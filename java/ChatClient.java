import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Minimal chat client for the OpenAI-compatible API in Open WebUI on the DGX Spark.
 * No libraries: the JDK has no JSON parser, so the few fields needed are picked out by hand.
 *
 * Run: java java/ChatClient.java llama3.1:8b "Say hello in one sentence."
 *
 * SPARK_URL           base URL (default http://spark-d66c.local:8080)
 * SPARK_API_KEY       the API key, or
 * SPARK_API_KEY_FILE  path to a file containing it
 */
public class ChatClient {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java ChatClient.java <model> <prompt>");
            System.exit(2);
        }
        var baseUrl = System.getenv().getOrDefault("SPARK_URL", "http://spark-d66c.local:8080");
        var body = "{\"model\":\"" + jsonEscape(args[0]) + "\","
                + "\"messages\":[{\"role\":\"user\",\"content\":\"" + jsonEscape(args[1]) + "\"}]}";

        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat/completions"))
                .header("Authorization", "Bearer " + readApiKey())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(10)) // a cold start of a large model can take over a minute
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        var client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            System.err.println("HTTP " + response.statusCode() + ": " + response.body());
            System.exit(1);
        }

        var json = response.body();
        var content = stringField(json, "content");
        System.out.println(content != null ? content : "(no content in response)");
        System.err.printf("%n[%s tokens, %s tokens/s, %s s in total]%n",
                numberField(json, "completion_tokens"),
                numberField(json, "response_token/s"),
                nanosToSeconds(numberField(json, "total_duration")));
    }

    static String readApiKey() throws Exception {
        var key = System.getenv("SPARK_API_KEY");
        if (key != null && !key.isBlank()) {
            return key.strip();
        }
        var file = System.getenv("SPARK_API_KEY_FILE");
        if (file != null) {
            return Files.readString(Path.of(file)).strip();
        }
        System.err.println("Set SPARK_API_KEY or SPARK_API_KEY_FILE");
        System.exit(2);
        return null;
    }

    /** The first string value for "key", unescaped, or null. Matches only the exact key name. */
    static String stringField(String json, String key) {
        var matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"").matcher(json);
        return matcher.find() ? readJsonString(json, matcher.end()) : null;
    }

    /** The first numeric value for "key" as text, or "?" when absent. */
    static String numberField(String json, String key) {
        var matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?[0-9.eE+-]+)").matcher(json);
        return matcher.find() ? matcher.group(1) : "?";
    }

    static String nanosToSeconds(String nanos) {
        try {
            return "%.1f".formatted(Double.parseDouble(nanos) / 1e9);
        } catch (NumberFormatException e) {
            return "?";
        }
    }

    /** Reads a JSON string starting just after its opening quote, resolving escapes. */
    static String readJsonString(String json, int start) {
        var out = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char escaped = json.charAt(++i);
            switch (escaped) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    out.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> out.append(escaped); // \" \\ \/
            }
        }
        throw new IllegalStateException("Unterminated JSON string");
    }

    static String jsonEscape(String text) {
        var out = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u%04x".formatted((int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
