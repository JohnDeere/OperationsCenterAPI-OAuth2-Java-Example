package com.deere.isg.examples;

import com.google.common.base.Strings;
import kong.unirest.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingInterceptor implements Interceptor {
    private static final Logger logger = LoggerFactory.getLogger("oidc");

    @Override
    public void onResponse(HttpResponse<?> response, HttpRequestSummary request, Config config) {
        if(response.isSuccess()){
            logger.info("Successful call To: {} {}", pad(request.getHttpMethod()), request.getUrl());
        }  else  {
            logger.error("Failed! {} {}", request.getHttpMethod(), request.getUrl());
            logger.error("Reason: {} {}", response.getStatus(), response.getStatusText());
            throw new RequestException((HttpResponse<JsonNode>) response);
        }
    }

    private String pad(HttpMethod httpMethod) {
        return Strings.padEnd(httpMethod.toString(), 4, ' ');
    }
}
