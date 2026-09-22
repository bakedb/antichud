// This is an example report.

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class LogSender {
    public static void main(String[] args) {
        String url = "http://localhost:8642/log";

        String jsonPayload = """
                {
                    "uuid": "0417789f-5035-3059-abd1-88f7101f5c92",
                    "username": "thatbakedbeans",
                    "event_type": "BANNED_MOD",
                    "details": "'Wurst' mod detected."
                }
                """;

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Response Status Code: " + response.statusCode());
            System.out.println("Response Body: " + response.body());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}