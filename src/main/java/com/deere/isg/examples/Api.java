// Unpublished Work (c) 2020 Deere & Company
package com.deere.isg.examples;


import kong.unirest.core.Unirest;
import kong.unirest.core.json.JSONObject;

public class Api {
    public JSONObject get(String accessToken, String resourceUrl) {
        return Unirest.get(resourceUrl)
                .header("authorization", "Bearer " + accessToken)
                .accept("application/vnd.deere.axiom.v3+json")
                .asJson()
                .getBody()
                .getObject();
    }
}
