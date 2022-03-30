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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import sanning.http.HTTPProcessor;
import sanning.http.HTTPRequest;
import sanning.http.HTTPResponse;
import sanning.http.HTTPServer;

final class SanningHTTP implements HTTPProcessor {

    final List<Sanning> sannings;
    final Map<String,Sanning> sanningMap;
    final Map<String,String> templateMap;

    public SanningHTTP() {
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
        loadTemplate("error");
        loadTemplate("list");
        loadTemplate("sanning");
        loadTemplate("sanning-answered");
        loadTemplate("test-login");
    }

    public void process(HTTPRequest request, HTTPResponse response) {
        // Parse method and path.
        int ix1 = request.requestLine.indexOf(' ');
        String method = request.requestLine.substring(0, ix1);
        String path = request.requestLine.substring(ix1 + 1, request.requestLine.indexOf(' ', ix1 + 1));

        // Parse sanning name.
        ix1 = path.indexOf('/', 2);
        String name = (ix1 != -1) ? path.substring(1, ix1) : path.substring(1);

        response.headers.setValue("Content-Type", "text/html");
        CharSequence responseBody = null;
        if ("GET".equals(method)) {
            if ("/".equals(path)) {
                // List sannings.
                responseBody = renderList();
            } else {
                if (path.endsWith("result")) {
                    // Download result.
                    response.headers.setValue("Content-Type", "text/plain");
                    try {
                        responseBody = sanningMap.get(name).persist();
                    } catch (IOException e) {
                        throw new RuntimeException("persist failed: " + e);
                    }
                } else {
                    // Show sanning.
                    responseBody = renderSanning(name, null);
                }
            }
        } else if ("POST".equals(method)) {
            ix1 = request.body.indexOf('=');
            int ix2 = request.body.indexOf('&');
            if (ix2 == -1) {
                // Send answer through login page.
                String option = request.body.substring(ix1 + 1);
                responseBody = renderTemplate("test-login",
                                              "SANNING", name,
                                              "OPTION", option);
            } else {
                // Submit answer to sanning.
                String ik = request.body.substring(ix1 + 1, ix2);
                ix1 = request.body.indexOf('=', ix2);
                String option = request.body.substring(ix1 + 1);

                try {
                    Sanning sanning = sanningMap.get(name);
                    Answer answer = sanning.doAnswer(ik, Integer.parseInt(option));
                    responseBody = renderSanning(name, answer);
                } catch (IOException e) {
                    response.headers.setValue("Content-Type", "text/plain");
                    responseBody = "ERROR: " + e.getMessage();
                }
            }
        }

        // No cache.
        response.headers.addValue("Pragma", "no-cache");
        response.headers.setValue("Cache-Control", "no-cache");
        response.headers.setValue("Expires", "Fri, 1 Jan 1971 00:00:00 GMT");

        // Create body content.
        byte[] content = toBytes(responseBody);
        response.headers.setValue("Content-Length", "" + content.length);
        response.body = content;
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
        String result = "<a href=" + name + "/result>" + name + "</a>";

        // LAST_UPDATED
        String lastUpdated = sanning.lastTS();
        lastUpdated = lastUpdated.isEmpty() ? "" : "Last Updated: " + lastUpdated;

        if (answer == null) {
            // Render unanswered sanning.
            StringBuilder options = new StringBuilder();
            int value = 0;
            for (String option : sanning.options) {
                options.append(String.format("    <li><button name=\"option\" value=\"%d\">%s</button></li>\n", value++, option));
            }
            return renderTemplate("sanning",
                                  "SANNING", sanning.name,
                                  "TITLE", sanning.title,
                                  "TEXT", sanning.text,
                                  "OPTIONS", options,
                                  "SUMMARY", summary,
                                  "RESULT", result,
                                  "LAST_UPDATED", lastUpdated);
        } else {
            return renderTemplate("sanning-answered",
                                  "SANNING", sanning.name,
                                  "TITLE", sanning.title,
                                  "TEXT", sanning.text,
                                  "MESSAGE", answer.isOld ? "You have already answered!" : "Answer submitted!",
                                  "OPTION", answer.option,
                                  "REF", answer.ak,
                                  "ANSWER_TIME", answer.ts,
                                  "SUMMARY", summary,
                                  "RESULT", result,
                                  "LAST_UPDATED", lastUpdated);
        }

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

    void loadSanning(String name) {
        Sanning sanning = sanningMap.get(name);
        if (sanning == null) {
            try { sanning = new Sanning(name, "sannings"); } catch (IOException e) { throw new RuntimeException(e); }
            sannings.add(sanning);
            sanningMap.put(name, sanning);
        }
    }

    void loadTemplate(String name) {
        //noinspection ConstantConditions
        String template = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/" + name + ".shtml"))).
            lines().parallel().collect(Collectors.joining("\n"));
        templateMap.put(name, template);
    }

    public static void main(String[] args) throws Throwable {
        // Usage.
        if ((args.length != 1) && (args.length != 3)) {
            System.out.println("sanning-http.sh <port> [<certificate> <cert password>]");
            System.exit(2);
        }

        int port = Integer.parseInt(args[0]);

        // TLS certificate.
        String certPath = null;
        String certPass = null;
        if (args.length == 3) {
            certPath = args[1];
            certPass = args[2];
        }

        // HTTP server.
        Executor executor = Executors.newFixedThreadPool(16);
        HTTPProcessor sannProcessor = new SanningHTTP();
        HTTPServer httpServer = new HTTPServer(port, sannProcessor, 20000, 60000, certPath, certPass, executor);
        executor.execute(httpServer);
    }

}
