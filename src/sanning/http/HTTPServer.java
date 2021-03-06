package sanning.http;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLContext;

public final class HTTPServer implements Runnable {

    private static final Pattern HEADER_PATTERN = Pattern.compile("(?i)([^:]*):\\s(.*?)\\s*");
    private static final Pattern CHARSET_PATTERN = Pattern.compile("[^;]*;\\scharset=(\\S*)\\s*$");

    private final int port;
    private final HTTPProcessor httpProcessor;
    private final Executor executor;
    private final int readTimeout;
    private final int idleTimeout;
    private final ServerSocketFactory sslSocketFactory;


    public HTTPServer(int port, HTTPProcessor httpProcessor, int readTimeout, int idleTimeout, SSLContext sslContext, Executor executor) {
        this.port = port;
        this.httpProcessor = httpProcessor;
        this.readTimeout = readTimeout;
        this.idleTimeout = idleTimeout;
        this.executor = executor;
        this.sslSocketFactory = (sslContext != null) ? sslContext.getServerSocketFactory() : null;
    }

    public void run() {
        try {
            ServerSocket ss = (sslSocketFactory != null) ? sslSocketFactory.createServerSocket(port, 50) : new ServerSocket(port);
            //noinspection InfiniteLoopStatement
            for (; ; ) {
                executor.execute(new RequestHandler(httpProcessor, ss.accept(), readTimeout, idleTimeout, null));
            }
        } catch (IOException e) {
            System.out.println("ERROR: listener I/O error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static class RequestHandler implements Runnable {

        private final HTTPProcessor httpProcessor;
        private final Socket socket;
        private final int readTimeout;
        private final int idleTimeout;
        private String requestLine;

        public RequestHandler(HTTPProcessor httpProcessor, Socket socket, int readTimeout, int idleTimeout,
                              String requestLine) {
            this.httpProcessor = httpProcessor;
            this.socket = socket;
            this.readTimeout = readTimeout;
            this.idleTimeout = idleTimeout;
            this.requestLine = requestLine;
        }

        public void run() {
            try {
                boolean shouldClose = false;
                socket.setSoTimeout(readTimeout); // use readTimeout for first request

                InputStream istream = socket.getInputStream();
                BufferedOutputStream ostream = new BufferedOutputStream(socket.getOutputStream());
                while (!shouldClose) {
                    if (requestLine == null) {
                        int firstChar;
                        try {
                            firstChar = istream.read();
                            if (firstChar == -1) {
                                shouldClose = true;
                                continue;
                            }
                        } catch (SocketTimeoutException e) {
                            shouldClose = true; // idle timeout - close down
                            continue;
                        }

                        socket.setSoTimeout(readTimeout);
                        requestLine = (char) firstChar + readLine(istream);
                    }
                    Headers requestHeaders = new Headers();
                    String headerLine = readLine(istream);
                    while (headerLine.length() > 0) {
                        Matcher m = HEADER_PATTERN.matcher(headerLine);
                        if (m.matches()) {
                            requestHeaders.addValue(m.group(1), m.group(2));
                        }
                        headerLine = readLine(istream);
                    }
                    shouldClose = "close".equals(requestHeaders.singleValue("Connection"));

                    ByteBuffer bodyBuffer = null;
                    String contentLengthStr = requestHeaders.singleValue("Content-Length");
                    if (contentLengthStr != null) {
                        int contentLength = Integer.parseInt(contentLengthStr);
                        byte[] bodyBytes = new byte[contentLength];
                        int offset = 0;
                        int numRead;
                        while (offset < contentLength && (numRead = istream.read(bodyBytes, offset, contentLength - offset)) >= 0) {
                            offset += numRead;
                        }
                        if (offset >= contentLength) {
                            bodyBuffer = ByteBuffer.wrap(bodyBytes);
                        }
                    }
                    String body = null;
                    if (bodyBuffer != null) {
                        Charset bodyCharset = StandardCharsets.ISO_8859_1;
                        String contentType = requestHeaders.singleValue("Content-Type");
                        if (contentType != null) {
                            Matcher m = CHARSET_PATTERN.matcher(contentType);
                            if (m.matches()) {
                                try { bodyCharset = Charset.forName(m.group(1)); } catch (IllegalArgumentException ignored) { }
                            }
                        }
                        body = bodyCharset.decode(bodyBuffer).toString();
                    }

                    HTTPRequest request = new HTTPRequest(requestLine, requestHeaders, body, (InetSocketAddress) socket.getRemoteSocketAddress());
                    requestLine = null;
                    HTTPResponse response = new HTTPResponse();
                    Headers responseHeaders = response.headers;
                    responseHeaders.setValue("Server", "HTTPServer");
                    try {
                        httpProcessor.process(request, response);
                    } catch (RuntimeException e) {
                        sendInternalServerError(response, e);
                    }

                    shouldClose = shouldClose || "close".equals(responseHeaders.singleValue("Connection"));

                    if (response.body != null) {
                        responseHeaders.setValue("Content-Length", String.valueOf(response.body.length));
                    } else {
                        responseHeaders.setValue("Content-Length", "0");
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append("HTTP/1.1 ").append(response.statusCode);
                    if (response.reasonPhrase != null) {
                        sb.append(' ').append(response.reasonPhrase);
                    }
                    sb.append("\r\n");
                    for (String headerName : responseHeaders.names()) {
                        for (String headerValue : responseHeaders.multiValue(headerName)) {
                            sb.append(headerName).append(": ");
                            sb.append(headerValue);
                            sb.append("\r\n");
                        }
                    }
                    sb.append("\r\n");
                    ostream.write(StandardCharsets.US_ASCII.encode(sb.toString()).array());

                    if (response.body != null) {
                        ostream.write(response.body);
                    }
                    ostream.flush();

                    if (!shouldClose) {
                        socket.setSoTimeout(idleTimeout);
                    }
                }
            } catch (Exception ignored) { } finally {
                if (socket != null) {
                    try { socket.close(); } catch (IOException ignored) { }
                }
            }
        }

        public static String readLine(InputStream istream) throws IOException {
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = istream.read()) != '\r') {
                if (c == -1) {
                    throw new IllegalStateException("unexpected EOF");
                }
                sb.append((char) c);
            }
            //noinspection ResultOfMethodCallIgnored
            istream.read(); // skip '\n'
            return sb.toString();
        }

        private static void sendInternalServerError(HTTPResponse response, Exception e) {
            response.statusCode = 500;
            response.reasonPhrase = "Internal Server Error";
            response.headers.setValue("Connection", "close");
            response.headers.setValue("Content-Type", "text/plain");
            StringBuilder errorMsg = new StringBuilder();
            errorMsg.append("500 INTERNAL SERVER ERROR").append("\n\n");
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            errorMsg.append(sw);
            response.body = StandardCharsets.US_ASCII.encode(errorMsg.toString()).array();
        }
    }

}
