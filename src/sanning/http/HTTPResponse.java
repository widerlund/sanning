package sanning.http;

public final class HTTPResponse {

   public int statusCode;
   public String reasonPhrase;
   public final Headers headers;
   public byte[] body;

   public HTTPResponse() {
      headers = new Headers();
      statusCode = 200;
      reasonPhrase = "OK";
   }

}
