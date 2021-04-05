package com.deere.isg.examples;

import com.github.mustachejava.DefaultMustacheFactory;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.plugin.rendering.template.JavalinMustache;
import kong.unirest.Unirest;
import kong.unirest.json.JSONObject;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.ImmutableMap.of;

public class Application {
    private static final Logger logger = LoggerFactory.getLogger("oidc");
    private Settings settings = new Settings();
    private Api api = new Api();
    private Map<String, JSONObject> metaInfo = new HashMap<>();

    /**
     * These are the routes for the application running on port 9090
     */
    public void start() {
        int port = 9090;
        Javalin app = Javalin.create(c -> {
            c.addStaticFiles("assets/");

        }).start(port);
        DefaultMustacheFactory stache = new DefaultMustacheFactory("templates");
        JavalinMustache.configure(stache);
        app.get("/", this::index);
        app.post("/", this::startOIDC);
        app.get("/callback", this::processCallback);
        app.get("/refresh-access-token", this::refreshAccessToken);
        app.post("/call-api", this::callTheApi);
        logger.info("Application Stated please navigate to http://localhost:"+port);
        Unirest.config().interceptor(new LoggingInterceptor());
    }

    public void index(Context contex) {
        contex.render("main.mustache", ImmutableMap.of("settings", settings));
    }

    /**
     * Updates the settings with the form an inits the OIDC login
     * @return
     */
    private Object startOIDC(Context contex) {
        settings.populate(contex);
        String redirect = getRedirectUrl();
        logger.info("Authorization Link Built. Redirecting to : " + redirect);
        contex.redirect(redirect);
        return null;
    }

    private void processCallback(Context contex) {
        logger.info("Processing callback from authorization server.");
        if (!Strings.isNullOrEmpty(contex.queryParam("error"))) {
            String description = contex.queryParam("error_description");
            renderError(contex, description);
            return;
        }

        try {
            String code = contex.queryParam("code");
            logger.info("Found access code ({}) will use this to exchange for a access token", code);
            JSONObject obj = Unirest.post(getLocationFromMeta("token_endpoint"))
                    .header("authorization", "Basic " + settings.getBasicAuthHeader())
                    .accept("application/json")
                    .field("grant_type", "authorization_code")
                    .field("redirect_uri", settings.callbackUrl)
                    .field("code", code)
                    .field("scope", settings.scopes)
                    .contentType("application/x-www-form-urlencoded")
                    .asJson()
                    .getBody()
                    .getObject();
            logger.info("Token exchange successful! \n{}", obj.toString(3));
            settings.updateTokenInfo(obj);

            String organizationAccessUrl = needsOrganizationAccess();
            if (organizationAccessUrl != null) {
                contex.redirect(organizationAccessUrl);
            }

        } catch (Exception e) {
            renderError(contex, Throwables.getStackTraceAsString(e));
            return;
        }

        index(contex);
    }

    /**
     * @return create a authorization URL
     */
    private String getRedirectUrl() {
        try {
            String authEndpoint = getLocationFromMeta("authorization_endpoint");
            return new URIBuilder(URI.create(authEndpoint))
                    .addParameter("client_id", settings.clientId)
                    .addParameter("response_type", "code")
                    .addParameter("scope", settings.scopes)
                    .addParameter("redirect_uri", settings.callbackUrl)
                    .addParameter("state", settings.state)
                    .build()
                    .toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private String getLocationFromMeta(String key) {
        // Meta  data can  be cached for a long time.
        String url = metaInfo.computeIfAbsent(settings.wellKnown, this::getMetaData).getString(key);
        logger.info("Using [{}] element value from meta: {}", key, url);
        return url;
    }

    private JSONObject getMetaData(String k) {
        logger.info("Making call to .well-known endpoint");
        return Unirest.get(k)
                .asJson()
                .getBody()
                .getObject();
    }

    private void refreshAccessToken(Context context) {
        try {
            logger.info("Posting to refresh access token");
            JSONObject refresh = Unirest.post(getLocationFromMeta("token_endpoint"))
                    .header("authorization", "Basic " + settings.getBasicAuthHeader())
                    .accept("application/json")
                    .field("grant_type", "refresh_token")
                    .field("redirect_uri", settings.callbackUrl)
                    .field("refresh_token", settings.refreshToken)
                    .field("scope", settings.scopes)
                    .contentType("application/x-www-form-urlencoded")
                    .asJson()
                    .getBody()
                    .getObject();
            logger.info("Refreshed token \n{}",refresh.toString(3));
            settings.updateTokenInfo(refresh);
        } catch (Exception e) {
            renderError(context, Throwables.getStackTraceAsString(e));
            return;
        }
        index(context);
    }

    /**
     * Check to see if the 'connections' rel is present for any organization.
     * If the rel is present it means the oauth application has not completed it's
     * access to an organization and must redirect the user to the uri provided
     * in the link.
     *
     * @return A redirect uri if 'connections' rel is present or <code>null</code>
     * if no redirect is required to finish the setup.
     */
    @SuppressWarnings("unchecked")
    private String needsOrganizationAccess() throws URISyntaxException {
        JSONObject apiResponse = api.get(settings.accessToken, settings.apiUrl + "/organizations");

        List<JSONObject> values = apiResponse.getJSONArray("values").toList();

        for (JSONObject org : values) {
            List<JSONObject> links = org.getJSONArray("links").toList();
            for (JSONObject link : links) {
                String linkType = link.getString("rel");
                if (linkType.equals("connections")) {
                    URIBuilder uriBuilder = new URIBuilder(link.getString("uri"));
                    uriBuilder.addParameter("redirect_uri", settings.orgConnectionCompletedUrl);
                    return uriBuilder.build().toString();
                }
            }
        }

        return null;
    }

    private void callTheApi(Context context) {
        String path = context.queryParam("url");
        logger.info("Making api call");
        try {
            JSONObject apiResponse = api.get(settings.accessToken, path);
            settings.apiResponse = apiResponse.toString(3);
        } catch (Exception e) {
             renderError(context, Throwables.getStackTraceAsString(e));
             return;
        }
        index(context);
    }

    private void renderError(Context context, String errorMessage) {
        logger.error(errorMessage);
        context.render("error.mustache",
                of("error", Strings.nullToEmpty(errorMessage))
        );
    }
}
