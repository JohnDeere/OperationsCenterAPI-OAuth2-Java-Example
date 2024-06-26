package com.deere.isg.examples;


import com.github.mustachejava.DefaultMustacheFactory;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;

import io.javalin.rendering.template.JavalinMustache;
import kong.unirest.core.Path;
import kong.unirest.core.Unirest;
import kong.unirest.core.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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
            c.fileRenderer(new JavalinMustache(new DefaultMustacheFactory("templates")));
            c.staticFiles.add(s -> {
                s.directory = "assets/";
                s.location = Location.CLASSPATH;
            });

        }).start(port);
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
    private void startOIDC(Context context) {
        settings.populate(context);
        String redirect = getRedirectUrl();
        logger.info("Authorization Link Built. Redirecting to : " + redirect);
        context.redirect(redirect);
    }

    private void processCallback(Context context) {
        logger.info("Processing callback from authorization server.");
        if (!Strings.isNullOrEmpty(context.queryParam("error"))) {
            String description = context.queryParam("error_description");
            renderError(context, description);
            return;
        }

        try {
            String code = context.queryParam("code");
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
                context.redirect(organizationAccessUrl);
            }
            index(context);
            
        } catch (Exception e) {
            renderError(context, Throwables.getStackTraceAsString(e));
        }
    }

    /**
     * @return create a authorization URL
     */
    private String getRedirectUrl() {
        var authEndpoint = getLocationFromMeta("authorization_endpoint");
        var path = new Path(authEndpoint);
        path.queryString("client_id", settings.clientId);
        path.queryString("response_type", "code");
        path.queryString("scope", settings.scopes);
        path.queryString("redirect_uri", settings.callbackUrl);
        path.queryString("state", settings.state);
        return path.toString();
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
    private String needsOrganizationAccess() {
        JSONObject apiResponse = api.get(settings.accessToken, settings.apiUrl + "/organizations");

        List<JSONObject> values = apiResponse.getJSONArray("values").toList();

        for (JSONObject org : values) {
            List<JSONObject> links = org.getJSONArray("links").toList();
            for (JSONObject link : links) {
                String linkType = link.getString("rel");
                if (linkType.equals("connections")) {
                    Path uriBuilder = new Path(link.getString("uri"));
                    uriBuilder.queryString("redirect_uri", Settings.SERVER_URL);
                    return uriBuilder.toString();
                }
            }
        }

        return null;
    }

    private void callTheApi(Context context) {
        String path = context.req().getParameter("url");
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
