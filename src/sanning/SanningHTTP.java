package sanning;

import static sanning.Util.toBytes;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import sanning.http.HTTPProcessor;
import sanning.http.HTTPRequest;
import sanning.http.HTTPResponse;
import sanning.http.HTTPServer;

final class SanningHTTP implements HTTPProcessor {

    static final Pattern REQUEST_PATTERN = Pattern.compile("(?<method>GET|POST) /*(?<name>[^ /]*)(/?(?<op>[^ /]+)?)");

    final List<Sanning> sannings;
    final Map<String,Sanning> sanningMap;
    final Map<String,String> templateMap;
    final Map<String,byte[]> imageMap;
    final Authenticator authhenticator;

    SanningHTTP(String authTemplate, Authenticator authenticator) {
        this.authhenticator = authenticator;

        // Load sannings.
        sannings = new ArrayList<>();
        sanningMap = new HashMap<>();
        //noinspection ConstantConditions
        for (String fileName : Stream.of(new File("sannings").listFiles()).sorted().
                                     filter(file -> !file.isDirectory()).map(File::getName).
                                     collect(Collectors.toSet())) {
            loadSanning(fileName.substring(0, fileName.length() - 4));
        }

        // Load templates.
        templateMap = new HashMap<>();
        loadTemplate(authTemplate, "auth");
        loadTemplate("confirm-bankid", "confirm");
        loadTemplate("error");
        loadTemplate("list");
        loadTemplate("sanning");

        // Load images.
        imageMap = new HashMap<>();
        loadImage("BankID_logo.svg");
    }

    public void process(HTTPRequest request, HTTPResponse response) {
        // Parse method and path.
        Matcher m = REQUEST_PATTERN.matcher(request.line);
        if (!m.find()) {
            throw new IllegalArgumentException("invalid request: " + request);
        }
        String method = m.group("method");
        String name = m.group("name");
        String op = m.group("op");

        // Dispatch.
        if ((op != null) && op.endsWith(".svg")) {
            // Process SVG image.
            response.headers.setValue("Content-Type", "image/svg+xml");
            response.body = imageMap.get(op);
        } else {
            // Process Sanning application request.
            processAppRequest(request, response, method, name, op);
        }

        // No cache.
        response.headers.addValue("Pragma", "no-cache");
        response.headers.setValue("Cache-Control", "no-cache");
        response.headers.setValue("Expires", "Fri, 1 Jan 1971 00:00:00 GMT");
    }

    void processAppRequest(HTTPRequest request, HTTPResponse response, String method, String name, String op) {
        response.headers.setValue("Content-Type", "text/html; charset=UTF-8");
        CharSequence responseBody = null;
        String error = null;
        if ("GET".equals(method)) {
            if (name.isEmpty()) {
                // List sannings.
                responseBody = renderList();
            } else {
                if ("result".equals(op)) {
                    // Download result.
                    response.headers.setValue("Content-Type", "text/plain; charset=UTF-8");
                    try {
                        responseBody = sanningMap.get(name).persist();
                    } catch (IOException e) {
                        throw new RuntimeException("persist failed: " + e);
                    }
                } else {
                    // Show sanning.
                    responseBody = renderSanning(name, Answer.EMPTY);
                }
            }
        } else if ("POST".equals(method)) {
            Sanning sanning = sanningMap.get(name);
            if (sanning == null) {
                throw new IllegalArgumentException("invalid request (no such sanning): " + request.line);
            }

            String optionStr = request.extractBodyParameter("option");
            if (optionStr == null) {
                throw new IllegalArgumentException("invalid request (option not present): " + request.body);
            }
            int option = Integer.parseInt(optionStr);
            String prettyOption = sanning.options[option];

            String ik = request.extractBodyParameter("ik");

            if ("auth".equals(op)) {
                responseBody = renderTemplate("auth",
                                              "TITLE", sanning.title,
                                              "OPTION", optionStr);
            } else if ("confirm".equals(op)) {
                String orderRef = (authhenticator != null) ? authhenticator.initAuth(ik, request.remoteAddress.getAddress().getHostAddress()) : "";
                if (orderRef != null) {
                    responseBody = renderTemplate("confirm",
                                                  "TITLE", sanning.title,
                                                  "PRETTY_OPTION", prettyOption,
                                                  "IK", ik,
                                                  "OPTION", optionStr,
                                                  "ORDER_REF", orderRef);
                } else {
                    error = "Authentication failed!";
                }

            } else if ("answer".equals(op)) {
                if ((authhenticator != null) && !authhenticator.checkAuth(request.extractBodyParameter("orderRef"))) {
                    error = "Authentication failed!";
                } else {
                    String p = request.extractBodyParameter("p");

                    // Submit answer option to sanning.
                    try {
                        Answer answer = sanning.doAnswer(ik, Integer.parseInt(optionStr), p);
                        responseBody = renderSanning(name, answer);
                    } catch (IOException e) {
                        error = e.getMessage();
                    }
                }
            }
        }

        // Check error.
        if (error != null) {
            responseBody = renderTemplate("error", "MESSAGE", error);
        }

        // Body content.
        response.body = toBytes(responseBody);
    }

    String renderSanning(String name, Answer answer) {
        Sanning sanning = sanningMap.get(name);

        // SUMMARY html.
        int total = 0;
        for (int count : sanning.summary) {
            total += count;
        }
        StringBuilder summary = new StringBuilder();
        for (int ix = 0; ix < sanning.summary.length; ix++) {
            int count = sanning.summary[ix];
            float percentage = total > 0 ? (float)count * 100 / total : 0f;
            summary.append(String.format("<tr><td>%s</td><td class=\"right\">%d</td><td class=\"right\">%.2f%%</td></tr>\n",
                                         sanning.options[ix], count, percentage));
        }
        summary.append(String.format("<tr><td colspan=\"3\" class=\"fill\"/></td></tr>\n" +
                                     "<tr><td>Total:</td><td>%d</td></tr>\n", total));

        // RESULT data file href.
        String result = "<a href=/" + name + "/result>" + name + "</a>";

        // LAST_UPDATED
        String lastUpdated = sanning.lastTS();
        lastUpdated = lastUpdated.isEmpty() ? "" : "Last Updated: " + lastUpdated;

        StringBuilder options = new StringBuilder();
        int value = 0;
        for (String option : sanning.options) {
            options.append(String.format("    <li><button name=\"option\" value=\"%d\">%s</button></li>\n", value++, option));
        }

        // Render unanswered sanning.
        return renderTemplate("sanning",
                              "SANNING", sanning.name,
                              "TITLE", sanning.title,
                              "TEXT", sanning.text.replace("\n", "<br>"),
                              "OPTIONS_STATE", (answer == Answer.EMPTY) ? "enabled" : "disabled",
                              "OPTIONS", options,
                              "MESSAGE_STATE", (answer != Answer.EMPTY) ? "enabled" : "disabled",
                              "MESSAGE", answer.isOld ? "You have already answered!" : "Thank you!<br>Your answer has been recorded.",
                              "PRETTY_OPTION", (answer.o != null) ? answer.o : answer.po,
                              "REF", answer.ak,
                              "ANSWER_TIME", answer.ts,
                              "SUMMARY", summary,
                              "SUMMARY", summary,
                              "RESULT", result,
                              "LAST_UPDATED", lastUpdated);
    }

    String renderList() {
        StringBuilder list = new StringBuilder();
        for (Sanning sanning : sannings) {
            list.append(String.format("  <li><a href=\"%s\">%s</a></li>\n", sanning.name, sanning.title));
        }

        return renderTemplate("list",
                              "LIST", list);
    }

    String renderTemplate(String name, CharSequence... values) {
        String template = templateMap.get(name);
        for (int ix = 0; ix < values.length; ix += 2) {
            template = template.replace("${" + values[ix] + "}", values[ix + 1]);
        }
        return template;
    }

    /** Load sanning text file with specified name. */
    void loadSanning(String name) {
        Sanning sanning = sanningMap.get(name);
        if (sanning == null) {
            try { sanning = new Sanning(name, "sannings"); } catch (IOException e) { throw new RuntimeException(e); }
            sannings.add(sanning);
            sanningMap.put(name, sanning);
        }
    }

    /**
     * Load template with specified name. Optional alias can be used as actual name.
     * @param name template file name
     * @param alias optional alias name to use as actual name
     */
    void loadTemplate(String name, String... alias) {
        //noinspection ConstantConditions
        String template = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/shtml/" + name + ".shtml"))).
            lines().parallel().collect(Collectors.joining("\n"));
        templateMap.put((alias.length > 0) ? alias[0] : name, template);
    }

    /** Load image with specified file name. */
    @SuppressWarnings("SameParameterValue")
    void loadImage(String fileName) {
        try {
            //noinspection ConstantConditions
            byte[] imageBytes = getClass().getResourceAsStream("/images/" + fileName).readAllBytes();
            imageMap.put(fileName, imageBytes);
        } catch (IOException e) {
            throw new RuntimeException("error loading image: " + e.getMessage(), e);
        }
    }

    public static void main(String[] args) throws Throwable {
        // Usage.
        if ((args.length != 2) && (args.length != 4)) {
            System.out.println("sanning-http.sh <port> <authenticator URL> [<keystore path> <keystore pass>]");
            System.exit(2);
        }

        int port = Integer.parseInt(args[0]);

        // TLS certificate.
        String keyStorePath = null;
        String keyStorePass = null;
        if (args.length == 4) {
            keyStorePath = args[2];
            keyStorePass = args[3];
        }
        SSLContext sslContext = (keyStorePath != null) ? Util.createSSLContext(keyStorePath, keyStorePass, false) : null;

        // Authenticator.
        String authUrl = args[1];
        Authenticator authenticator = "test".equals(authUrl) ? null : new Authenticator(authUrl, sslContext);
        String authTemplate = (authenticator != null) ? "auth-bankid" : "auth-test";

        // HTTP server.
        Executor executor = Executors.newFixedThreadPool(16);
        HTTPProcessor sannProcessor = new SanningHTTP(authTemplate, authenticator);
        HTTPServer httpServer = new HTTPServer(port, sannProcessor, 20000, 60000, sslContext, executor);
        executor.execute(httpServer);
    }

}
