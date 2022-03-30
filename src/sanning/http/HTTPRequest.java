package sanning.http;

public final class HTTPRequest {

   public final String line;
   public final Headers headers;
   public final String body;

   public HTTPRequest(String request, Headers headers, String body) {
      this.line = request;
      this.headers = headers;
      this.body = body;
   }

   /** Extract parameter value from request body. */
   public String extractBodyParameter(String name) {
       int ix1 = body.indexOf(name + "=");
       int ix2 = body.indexOf('&', ix1);
       return (ix1 != -1) ? body.substring(ix1 + 1 + name.length(), (ix2 > ix1) ? ix2 : body.length()) : null;
   }

}
