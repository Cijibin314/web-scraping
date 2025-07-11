import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class OllamaExtractor {

    private static final String OLLAMA_API_URL = "http://localhost:11434/api/generate";

    public static String extractInfo(String textContent, String schema, String url) throws IOException {
        try {
            // Prepare the request payload
            String prompt = "You are a data extraction machine. Your only function is to analyze the provided text and return a single, clean JSON object that strictly adheres to the provided schema. Do not include any additional text, explanations, or markdown. Do not invent new fields. Do not include any HTML tags in your output. If you cannot find a value for a field, return an empty string.\n\nText Content:\n" + textContent + "\n\nSchema:\n" + schema;
            OllamaRequest ollamaRequest = new OllamaRequest("llama3", prompt, false);

            // Create the HTTP connection
            URL apiUrl = new URL(OLLAMA_API_URL);
            HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            // Send the request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = new Gson().toJson(ollamaRequest).getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Read the response
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
            }

            // Parse the JSON response and extract the content
            JsonObject jsonResponse = new Gson().fromJson(response.toString(), JsonObject.class);
            return jsonResponse.get("response").getAsString();

        } catch (IOException e) {
            return "Error: Ollama service not available.";
        }
    }

    private static class OllamaRequest {
        private final String model;
        private final String prompt;
        private final boolean stream;

        public OllamaRequest(String model, String prompt, boolean stream) {
            this.model = model;
            this.prompt = prompt;
            this.stream = stream;
        }
    }
}

