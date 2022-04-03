/* Copyright (c) 2022 Emblasoft Test & Measurement AB. All Rights Reserved. */

package sanning.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.net.ssl.SSLContext;
import sanning.Util;

public class HTTPClient {

    private final HttpClient httpClient;

    public HTTPClient(SSLContext sslContext) {
        httpClient = HttpClient.newBuilder().version(Version.HTTP_1_1).sslContext(sslContext).build();
    }

    public String sendPost(String body, String contentType) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                                         .POST(HttpRequest.BodyPublishers.ofString(body))
                                         .uri(URI.create("https://appapi2.test.bankid.com/rp/v5.1/auth"))
                                         .setHeader("User-Agent", "Java 11 HttpClient Bot") // add request header
                                         .header("Content-Type", contentType)
                                         .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        return response.body();
    }

    public static void main(String[] args) throws Throwable {
        String keyStorePath = args[0];
        String keyStorePass = args[1];

        SSLContext sslContext = Util.createSSLContext(keyStorePath, keyStorePass, true);
        HTTPClient httpClient = new HTTPClient(sslContext);
        System.out.println(httpClient.sendPost("{\"personalNumber\":\"197110021834\",\"endUserIp\":\"82.196.112.52\"}", "application/json"));
    }

}
