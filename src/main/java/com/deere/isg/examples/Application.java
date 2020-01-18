package com.deere.isg.examples;

import com.google.common.base.Throwables;
import kong.unirest.Unirest;
import kong.unirest.json.JSONObject;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.Spark;
import spark.template.mustache.MustacheTemplateEngine;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.collect.ImmutableMap.of;

public class Application {
    private static final Logger logger = LoggerFactory.getLogger("oidc");
    private MustacheTemplateEngine stache = new MustacheTemplateEngine();
    private Settings settings = new Settings();
    private Map<String, JSONObject> metaInfo = new HashMap<>();

    /**
     * These are the routes for the application running on port 9090
     */
    public void start() {
        int port = 9090;
        Spark.port(port);
        Spark.staticFileLocation("assets");
        Spark.get("/", this::index);
        Spark.post("/", this::startOIDC);
        Spark.get("/callback", this::processCallback);
        Spark.get("/refresh-access-token", this::refreshAccessToken);
        Spark.post("/call-api", this::callTheApi);
        logger.info("Application Stated please navigate to http://localhost:"+port);
        Unirest.config().interceptor(new LoggingInterceptor());
    }

    public Object index(Request request, Response response) {
        return stache.render(new ModelAndView(settings, "main.mustache"));
    }

    /**
     * Updates the settings with the form an inits the OIDC login
     * @return
     */
    private Object startOIDC(Request request, Response response) {
        settings.populate(request);
        String redirect = getRedirectUrl();
        logger.info("Authorization Link Built. Redirecting to : " + redirect);
        response.redirect(redirect);
        return null;
    }

    private Object processCallback(Request request, Response response) {
        logger.info("Processing callback from authorization server.");
        if (request.queryParams("error") != null) {
            String description = request.queryParams("error_description");
            return renderError(description);
        }

        try {
            String code = request.queryParams("code");
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
        } catch (Exception e) {
            return renderError(Throwables.getStackTraceAsString(e));
        }

        return index(request, response);
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

    private Object refreshAccessToken(Request request, Response response) {
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
            return renderError(Throwables.getStackTraceAsString(e));
        }
        return index(request, response);
    }

    private Object callTheApi(Request request, Response response) {
        String path = request.queryParams("url");
        logger.info("Making api call");
        try {
        JSONObject apiResponse = Unirest.get(path)
                .header("authorization", "Bearer " + settings.accessToken)
                .accept("application/vnd.deere.axiom.v3+json")
                .asJson()
                .getBody()
                .getObject();
        settings.apiResponse = apiResponse.toString(3);
        } catch (Exception e) {
            return renderError(Throwables.getStackTraceAsString(e));
        }
        return index(request, response);
    }

    private Object renderError(String errorMessage) {
        logger.error(errorMessage);
        return stache.render(
                new ModelAndView(of("error", errorMessage), "error.mustache")
        );
    }
}
