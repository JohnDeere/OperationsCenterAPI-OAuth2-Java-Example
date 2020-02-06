package com.deere.isg.examples;

import com.google.common.base.Strings;
import kong.unirest.json.JSONObject;
import spark.Request;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

public class Settings {
    public String clientId = "";
    public String clientSecret = "";
    public String wellKnown = "https://signin.johndeere.com/oauth2/aus78tnlaysMraFhC1t7/.well-known/oauth-authorization-server";
    public String callbackUrl = "http://localhost:9090/callback";
    public String scopes = "openid profile offline_access ag1 eq1";
    public String state = "";
    public String idToken;
    public String accessToken;
    public String refreshToken;
    public String apiResponse;
    public Long exp;

    public void populate(Request request) {
        this.clientId = request.queryParams("clientId");
        this.clientSecret = request.queryParams("clientSecret");
        this.wellKnown = request.queryParams("wellKnown");
        this.callbackUrl = request.queryParams("callbackUrl");
        this.scopes = request.queryParams("scopes");
        this.state = request.queryParams("state");
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
