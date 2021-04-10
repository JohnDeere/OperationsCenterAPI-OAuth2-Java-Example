package com.deere.isg.examples;

import com.google.common.base.Strings;
import io.javalin.http.Context;
import kong.unirest.json.JSONObject;


import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;


public class Settings {
    private static String SERVER_URL = "http://localhost:9090";
    public String clientId = "";
    public String clientSecret = "";
    public String wellKnown = "https://signin.johndeere.com/oauth2/aus78tnlaysMraFhC1t7/.well-known/oauth-authorization-server";
    public String apiUrl = "https://sandboxapi.deere.com/platform";
    public String callbackUrl = SERVER_URL + "/callback";
    public String orgConnectionCompletedUrl = SERVER_URL;
    public String scopes = "openid profile offline_access ag1 eq1";
    public String state = UUID.randomUUID().toString();
    public String idToken;
    public String accessToken;
    public String refreshToken;
    public String apiResponse;
    public Long exp;

    public void populate(Context request) {
        this.clientId = request.formParam("clientId");
        this.clientSecret = request.formParam("clientSecret");
        this.wellKnown = request.formParam("wellKnown");
        this.callbackUrl = request.formParam("callbackUrl");
        this.scopes = request.formParam("scopes");
        this.state = request.formParam("state");
    }

    public String getAccessTokenDetails() {
        if (Strings.isNullOrEmpty(accessToken)) {
            return null;
        }
        String body = accessToken.split("\\.")[1];
        return new JSONObject(new String(Base64.getDecoder().decode(body))).toString(3);
    }

    public String getExpiration(){
        if(exp == null){
            return null;
        }
        return LocalDateTime.now().plusSeconds(exp).toString();
    }

    public String getBasicAuthHeader() {
        String header = String.format("%s:%s", clientId, clientSecret);
        return Base64.getEncoder().encodeToString(header.getBytes());
    }

    public void updateTokenInfo(JSONObject obj) {
        idToken = obj.optString("id_token");
        accessToken = obj.optString("access_token");
        refreshToken = obj.optString("refresh_token");
        exp = obj.optLong("expires_in");
    }

}
