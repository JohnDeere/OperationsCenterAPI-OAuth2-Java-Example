package com.deere.isg.examples;

import kong.unirest.core.HttpResponse;
import kong.unirest.core.JsonNode;

public class RequestException extends RuntimeException {
    public RequestException(HttpResponse<JsonNode> r) {
        super("Request Error! \n" +
                "Status: " + r.getStatus() + " " + r.getStatusText() + "\n" +
                "Body: " + r.getBody().toString()
        );
    }
}
