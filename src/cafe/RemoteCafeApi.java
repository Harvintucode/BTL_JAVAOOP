package cafe;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Gọi {@link CafeServer} qua mạng LAN bằng HTTP/JSON; dùng thay cho {@link CafeService} cục bộ. */
public final class RemoteCafeApi implements CafeApi {
    public static final int DEFAULT_PORT = 8080;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final HttpClient http;
    private final URI base;
    private final String token;
    private final User user;
    private Data.State cached;

    private RemoteCafeApi(HttpClient http, URI base, String token, User user) {
        this.http = http;
        this.base = base;
        this.token = token;
        this.user = user;
    }

    /** Đăng nhập vào máy chủ. Bên gọi tự xóa mảng password sau khi phương thức trả về. */
    public static RemoteCafeApi login(String server, String username, char[] password, Role role) {
        Objects.requireNonNull(role, "role");
        URI base = baseUri(server);
        HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", new String(password));
        body.put("role", role.name());
        Map<String, Object> res = call(http, base, "POST", "/api/login", null, body, true);
        try {
            User user = new User(Wire.str(res, "username"), Wire.str(res, "displayName"),
                    Wire.enumOf(Role.class, Wire.str(res, "role")));
            return new RemoteCafeApi(http, base, Wire.str(res, "token"), user);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Máy chủ trả về dữ liệu đăng nhập không hợp lệ.", ex);
        }
    }

    /** Chấp nhận "192.168.1.10", "192.168.1.10:9000" hoặc "http://192.168.1.10:9000". */
    static URI baseUri(String server) {
        if (server == null || server.isBlank())
            throw new IllegalArgumentException("Chưa nhập địa chỉ máy chủ.");
        String text = server.trim();
        if (!text.contains("://")) text = "http://" + text;
        URI uri;
        try {
            uri = URI.create(text);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Địa chỉ máy chủ không hợp lệ: " + server);
        }
        if (uri.getHost() == null || !"http".equalsIgnoreCase(uri.getScheme()))
            throw new IllegalArgumentException("Địa chỉ máy chủ không hợp lệ (dạng IP:cổng): " + server);
        int port = uri.getPort() == -1 ? DEFAULT_PORT : uri.getPort();
        return URI.create("http://" + uri.getHost() + ":" + port);
    }

    public User user() {
        return user;
    }

    public URI server() {
        return base;
    }

    @Override
    public synchronized Data.State snapshot() {
        String path = "/api/state" + (cached == null ? "" : "?rev=" + cached.revision);
        Map<String, Object> res = call("GET", path, null);
        if (cached != null && Boolean.TRUE.equals(res.get("unchanged"))) return cached;
        cached = parse(() -> Wire.state(res));
        return cached;
    }

    @Override
    public Data.Order createOrder(Map<String, Integer> cart, String customer,
                                  Data.Payment payment, Data.Serving serving) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cart", new LinkedHashMap<>(cart));
        body.put("customer", customer == null ? "" : customer);
        body.put("payment", payment.name());
        body.put("serving", serving.name());
        Map<String, Object> res = call("POST", "/api/orders", body);
        return parse(() -> Wire.order(res));
    }

    @Override
    public void completeOrder(long id) {
        call("POST", "/api/orders/pay", Map.of("id", id));
    }

    @Override
    public void cancelOrder(long id) {
        call("POST", "/api/orders/cancel", Map.of("id", id));
    }

    @Override
    public void adjustStock(String ingredient, int delta) {
        call("POST", "/api/stock", Map.of("ingredient", ingredient, "delta", delta));
    }

    @Override
    public void addPromotion(String productId, LocalDate date, int percent) {
        call("POST", "/api/promotions",
                Map.of("productId", productId, "date", date.toString(), "percent", percent));
    }

    @Override
    public void removePromotion(long id) {
        call("POST", "/api/promotions/delete", Map.of("id", id));
    }

    /** Đăng xuất khỏi máy chủ ở nền, không chờ kết quả. */
    @Override
    public void close() {
        HttpRequest request = request("POST", "/api/logout", token, Map.of());
        http.sendAsync(request, HttpResponse.BodyHandlers.discarding()).exceptionally(e -> null);
    }

    // ---------- Gửi/nhận ----------

    private Map<String, Object> call(String method, String path, Object body) {
        return call(http, base, method, path, token, body, false);
    }

    private HttpRequest request(String method, String path, String token, Object body) {
        return buildRequest(base, method, path, token, body);
    }

    private static HttpRequest buildRequest(URI base, String method, String path, String token, Object body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json; charset=utf-8");
            builder.method(method, HttpRequest.BodyPublishers.ofString(Json.write(body), StandardCharsets.UTF_8));
        }
        return builder.build();
    }

    private static Map<String, Object> call(HttpClient http, URI base, String method, String path,
                                            String token, Object body, boolean loginCall) {
        HttpResponse<String> response;
        try {
            response = http.send(buildRequest(base, method, path, token, body),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpConnectTimeoutException | ConnectException ex) {
            throw new IllegalStateException("Không kết nối được tới máy chủ " + base.getHost() + ":"
                    + base.getPort() + ". Hãy kiểm tra máy chủ đã chạy, cùng mạng LAN và tường lửa đã mở cổng.");
        } catch (IOException ex) {
            throw new IllegalStateException("Mất kết nối tới máy chủ: "
                    + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Yêu cầu tới máy chủ bị gián đoạn.");
        }

        Map<String, Object> json;
        try {
            json = Json.parseObject(response.body());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Máy chủ trả về dữ liệu không hợp lệ. Hãy kiểm tra địa chỉ và cổng.");
        }
        int status = response.statusCode();
        if (status == 200) return json;
        Object error = json.get("error");
        String message = error instanceof String s ? s : "Máy chủ báo lỗi (HTTP " + status + ").";
        if (status == 401 && !loginCall) throw new IllegalStateException(message);
        if (status >= 500) throw new IllegalStateException(message);
        throw new IllegalArgumentException(message);
    }

    private static <T> T parse(java.util.function.Supplier<T> reader) {
        try {
            return reader.get();
        } catch (IllegalArgumentException | java.time.DateTimeException ex) {
            throw new IllegalStateException("Dữ liệu từ máy chủ không hợp lệ (có thể khác phiên bản).", ex);
        }
    }

    @Override
    public String toString() {
        return "RemoteCafeApi[" + base + ", user=" + user.username() + ", token=" + (token == null ? "-" : "***") + "]";
    }
}
