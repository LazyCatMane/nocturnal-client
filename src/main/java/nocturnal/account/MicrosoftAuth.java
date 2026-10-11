package nocturnal.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Microsoft login with the device code flow: the user signs in on microsoft.com/link, so the client
 * never handles a password. Chain: Microsoft token -> Xbox Live -> XSTS -> Minecraft token -> profile.
 * All methods block; call them off the main thread.
 */
public final class MicrosoftAuth {
    /** The official Minecraft launcher's client id, which Minecraft services accept. */
    private static final String CLIENT_ID = "00000000402b5328";
    private static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public record DeviceCode(String userCode, String deviceCode, String verificationUri, int interval, int expiresIn) {}

    public record Profile(String name, UUID uuid, String minecraftToken, String refreshToken) {}

    private MicrosoftAuth() {}

    public static DeviceCode requestDeviceCode() throws IOException {
        JsonObject r = postForm("https://login.live.com/oauth20_connect.srf",
            Map.of("client_id", CLIENT_ID, "scope", SCOPE, "response_type", "device_code"));
        return new DeviceCode(r.get("user_code").getAsString(), r.get("device_code").getAsString(),
            r.get("verification_uri").getAsString(), r.get("interval").getAsInt(), r.get("expires_in").getAsInt());
    }

    /** Waits until the user finishes signing in, then logs into Minecraft. */
    public static Profile awaitDeviceLogin(DeviceCode code) throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + code.expiresIn() * 1000L;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(code.interval() * 1000L);
            JsonObject r = postForm("https://login.live.com/oauth20_token.srf", Map.of("client_id", CLIENT_ID,
                "device_code", code.deviceCode(), "grant_type", "urn:ietf:params:oauth:grant-type:device_code"));
            if (r.has("access_token")) return login(r.get("access_token").getAsString(), r.get("refresh_token").getAsString());
            String error = r.has("error") ? r.get("error").getAsString() : "unknown";
            if (!error.equals("authorization_pending") && !error.equals("slow_down")) throw new IOException("Microsoft login failed: " + error);
        }
        throw new IOException("Login code expired");
    }

    public static Profile refresh(String refreshToken) throws IOException {
        JsonObject r = postForm("https://login.live.com/oauth20_token.srf", Map.of("client_id", CLIENT_ID,
            "refresh_token", refreshToken, "grant_type", "refresh_token", "scope", SCOPE));
        if (!r.has("access_token")) throw new IOException("Saved login expired, add the account again");
        return login(r.get("access_token").getAsString(), r.get("refresh_token").getAsString());
    }

    private static Profile login(String microsoftToken, String refreshToken) throws IOException {
        JsonObject xblProps = new JsonObject();
        xblProps.addProperty("AuthMethod", "RPS");
        xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
        xblProps.addProperty("RpsTicket", microsoftToken);
        JsonObject xbl = postJson("https://user.auth.xboxlive.com/user/authenticate",
            body(xblProps, "http://auth.xboxlive.com"), null);
        String userHash = xbl.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();

        JsonObject xstsProps = new JsonObject();
        xstsProps.addProperty("SandboxId", "RETAIL");
        JsonArray userTokens = new JsonArray();
        userTokens.add(xbl.get("Token").getAsString());
        xstsProps.add("UserTokens", userTokens);
        JsonObject xsts = postJson("https://xsts.auth.xboxlive.com/xsts/authorize",
            body(xstsProps, "rp://api.minecraftservices.com/"), null);
        if (xsts.has("XErr")) throw new IOException(xstsError(xsts.get("XErr").getAsLong()));

        JsonObject mcBody = new JsonObject();
        mcBody.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xsts.get("Token").getAsString());
        String mcToken = postJson("https://api.minecraftservices.com/authentication/login_with_xbox", mcBody, null)
            .get("access_token").getAsString();

        JsonObject profile = get("https://api.minecraftservices.com/minecraft/profile", mcToken);
        if (!profile.has("id")) throw new IOException("This Microsoft account doesn't own Minecraft Java");
        String id = profile.get("id").getAsString().replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
        return new Profile(profile.get("name").getAsString(), UUID.fromString(id), mcToken, refreshToken);
    }

    private static JsonObject body(JsonObject properties, String relyingParty) {
        JsonObject o = new JsonObject();
        o.add("Properties", properties);
        o.addProperty("RelyingParty", relyingParty);
        o.addProperty("TokenType", "JWT");
        return o;
    }

    private static String xstsError(long code) {
        if (code == 2148916233L) return "This Microsoft account has no Xbox profile. Sign in at minecraft.net once first";
        if (code == 2148916238L) return "Child accounts must be added to a family by an adult first";
        return "Xbox login failed (" + code + ")";
    }

    private static JsonObject postForm(String url, Map<String, String> form) throws IOException {
        String encoded = form.entrySet().stream()
            .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
        return send(HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(encoded)));
    }

    private static JsonObject postJson(String url, JsonObject json, String bearer) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.toString()));
        if (bearer != null) b.header("Authorization", "Bearer " + bearer);
        return send(b);
    }

    private static JsonObject get(String url, String bearer) throws IOException {
        return send(HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer " + bearer).GET());
    }

    /** Returns the JSON body even for error statuses, since some endpoints report errors in it. */
    private static JsonObject send(HttpRequest.Builder request) throws IOException {
        try {
            HttpResponse<String> r = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (r.body().isBlank()) {
                if (r.statusCode() >= 400) throw new IOException("HTTP " + r.statusCode() + " from " + r.uri().getHost());
                return new JsonObject();
            }
            return JsonParser.parseString(r.body()).getAsJsonObject();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        } catch (RuntimeException e) {
            throw new IOException("Unexpected response", e);
        }
    }
}
