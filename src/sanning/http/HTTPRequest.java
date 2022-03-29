package sanning.http;

public final class HTTPRequest {

   public final String requestLine;
   public final Headers headers;
   public final String body;

   public HTTPRequest(String request, Headers headers, String body) {
      this.requestLine = request;
      this.headers = headers;
      this.body = body;
   }

}
