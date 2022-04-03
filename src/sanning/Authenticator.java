package sanning;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;

class Authenticator {

    static final int TIMEOUT = 5000;
    static final Pattern JSON_PATTERN = Pattern.compile("(?s)\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"");

    final String authUrl;
    final HttpClient httpClient;

    Authenticator(String authUrl, SSLContext sslContext) {
        this.authUrl = authUrl;
        HttpClient.Builder builder = HttpClient.newBuilder().version(Version.HTTP_1_1).connectTimeout(Duration.ofMillis(TIMEOUT));
        builder = (sslContext != null) ? builder.sslContext(sslContext) : builder;
        httpClient = builder.build();
    }

    String initAuth(String ik, String endUserIp) {
        return call("/auth", "{\"personalNumber\":\"" + ik + "\",\"endUserIp\":\"" + endUserIp + "\"}", "orderRef");
    }

    boolean checkAuth(String orderRef) {
        String status = call("/collect", "{\"orderRef\":\"" + orderRef + "\"}", "status");
        return "complete".equals(status);
    }

    private String call(String operation, String requestBody, String returnParameter) {
        HttpRequest request = HttpRequest.newBuilder()
                                         .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                                         .uri(URI.create(authUrl + operation))
                                         .setHeader("User-Agent", "Sanning")
                                         .header("Content-Type", "application/json")
                                         .build();
        try {
            HttpResponse<String> response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).get(TIMEOUT, TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) {
                return null;
            }
            return extractJSONParameter(response.body(), returnParameter);
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            return null;
        }

    }

    private static String extractJSONParameter(String json, String paramName) {
        Matcher m = JSON_PATTERN.matcher(json);
        //noinspection StatementWithEmptyBody
        while (m.find() && !m.group(1).equals(paramName));
        return (m.regionStart() < 0) ? null : m.group(2);
    }

}
