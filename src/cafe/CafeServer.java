package cafe;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Máy chủ HTTP/JSON bao quanh {@link CafeApi}. Chỉ máy chủ này đọc/ghi cafe.dat và users.dat;
 * các máy POS/Admin khác gọi qua mạng LAN bằng {@link RemoteCafeApi}.
 *
 * <p>Mọi thao tác dữ liệu được tuần tự hóa bằng một khóa duy nhất, vì FileLock không cho khóa
 * chồng nhau trong cùng một JVM.
 */
public final class CafeServer {
    private static final int MAX_BODY = 64 * 1024;
    private static final int MAX_CART_ITEMS = 50;
    private static final long IDLE_MS = TimeUnit.HOURS.toMillis(12);
    private static final int MAX_FAILURES = 5;
    private static final long LOCKOUT_MS = TimeUnit.MINUTES.toMillis(1);

    private final HttpServer http;
    private final ExecutorService executor;
    private final CafeApi api;
    private final AuthService auth;
    private final Object dataLock = new Object();
    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();
    private final Map<String, Failure> failures = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public CafeServer(int port) throws IOException {
        this(port, new CafeService(), new AuthService());
    }

    public CafeServer(int port, CafeApi api, AuthService auth) throws IOException {
        this.api = api;
        this.auth = auth;
        this.http = HttpServer.create(new InetSocketAddress(port), 0);
        this.executor = Executors.newFixedThreadPool(8, r -> {
            Thread t = new Thread(r, "cotafe-http");
            t.setDaemon(true);
            return t;
        });
        http.setExecutor(executor);
        http.createContext("/api/", this::handle);
    }

    public void start() {
        http.start();
    }

    public void stop() {
        http.stop(0);
        executor.shutdownNow();
    }

    public int port() {
        return http.getAddress().getPort();
    }

    // ---------- Xử lý yêu cầu ----------

    private void handle(HttpExchange ex) throws IOException {
        int status = 200;
        Object body;
        try {
            body = route(ex);
        } catch (HttpError e) {
            status = e.status;
            body = Map.of("error", e.getMessage());
        } catch (DateTimeParseException e) {
            status = 400;
            body = Map.of("error", "Ngày/giờ không hợp lệ (cần định dạng yyyy-MM-dd).");
        } catch (IllegalArgumentException e) {
            status = 400;
            body = Map.of("error", e.getMessage() == null ? "Yêu cầu không hợp lệ." : e.getMessage());
        } catch (RuntimeException e) {
            System.err.println("[server] Loi khi xu ly " + ex.getRequestMethod() + " "
                    + ex.getRequestURI().getPath() + ": " + e);
            status = 500;
            body = Map.of("error", "Lỗi máy chủ khi xử lý yêu cầu.");
        }
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        try {
            ex.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(bytes);
            }
        } finally {
            ex.close();
        }
    }

    private Object route(HttpExchange ex) {
        String key = ex.getRequestMethod() + " " + ex.getRequestURI().getPath();
        switch (key) {
            case "GET /api/ping":
                return Map.of("service", "cotafe", "ok", true);
            case "POST /api/login":
                return login(ex);
            default:
                break;
        }

        Entry entry = authenticate(ex);
        switch (key) {
            case "POST /api/logout": {
                sessions.remove(entry.token);
                entry.session.logout();
                return Map.of("ok", true);
            }
            case "GET /api/state": {
                require(entry, Role.STAFF);
                long known = queryLong(ex, "rev");
                Data.State state;
                synchronized (dataLock) {
                    state = api.snapshot();
                }
                if (state.revision == known) return Map.of("unchanged", true);
                return Wire.state(state);
            }
            case "POST /api/orders": {
                require(entry, Role.STAFF);
                Map<String, Object> b = readBody(ex);
                Map<String, Object> rawCart = Wire.obj(b.get("cart"));
                if (rawCart.size() > MAX_CART_ITEMS)
                    throw new IllegalArgumentException("Giỏ hàng có quá nhiều món.");
                Map<String, Integer> cart = new LinkedHashMap<>();
                for (Map.Entry<String, Object> item : rawCart.entrySet())
                    cart.put(item.getKey(), Wire.integer(item.getValue()));
                String customer = Wire.optStr(b, "customer");
                if (customer.length() > 100)
                    throw new IllegalArgumentException("Tên khách quá dài (tối đa 100 ký tự).");
                Data.Payment payment = Wire.enumOf(Data.Payment.class, Wire.str(b, "payment"));
                Data.Serving serving = Wire.enumOf(Data.Serving.class, Wire.str(b, "serving"));
                Data.Order order;
                synchronized (dataLock) {
                    order = api.createOrder(cart, customer, payment, serving);
                }
                return Wire.order(order);
            }
            case "POST /api/orders/pay": {
                require(entry, Role.STAFF);
                long id = Wire.num(readBody(ex), "id");
                synchronized (dataLock) {
                    api.completeOrder(id);
                }
                return Map.of("ok", true);
            }
            case "POST /api/orders/cancel": {
                require(entry, Role.STAFF);
                long id = Wire.num(readBody(ex), "id");
                synchronized (dataLock) {
                    api.cancelOrder(id);
                }
                return Map.of("ok", true);
            }
            case "POST /api/stock": {
                require(entry, Role.ADMIN);
                Map<String, Object> b = readBody(ex);
                String ingredient = Wire.str(b, "ingredient");
                int delta = Wire.integer(b.get("delta"));
                synchronized (dataLock) {
                    api.adjustStock(ingredient, delta);
                }
                return Map.of("ok", true);
            }
            case "POST /api/promotions": {
                require(entry, Role.ADMIN);
                Map<String, Object> b = readBody(ex);
                String productId = Wire.str(b, "productId");
                LocalDate date = LocalDate.parse(Wire.str(b, "date"));
                int percent = Wire.integer(b.get("percent"));
                synchronized (dataLock) {
                    api.addPromotion(productId, date, percent);
                }
                return Map.of("ok", true);
            }
            case "POST /api/promotions/delete": {
                require(entry, Role.ADMIN);
                long id = Wire.num(readBody(ex), "id");
                synchronized (dataLock) {
                    api.removePromotion(id);
                }
                return Map.of("ok", true);
            }
            default:
                throw new HttpError(404, "Không tìm thấy chức năng yêu cầu.");
        }
    }

    // ---------- Đăng nhập và phiên ----------

    private Object login(HttpExchange ex) {
        String ip = ex.getRemoteAddress().getAddress().getHostAddress();
        if (failures.size() > 1000) failures.clear();
        Failure failure = failures.computeIfAbsent(ip, k -> new Failure());
        if (failure.blocked())
            throw new HttpError(429, "Đăng nhập sai quá nhiều lần. Vui lòng thử lại sau 1 phút.");
        Map<String, Object> b = readBody(ex);
        Role role = Wire.enumOf(Role.class, Wire.str(b, "role"));
        String username = Wire.str(b, "username");
        char[] password = Wire.str(b, "password").toCharArray();
        AuthService.Session session;
        try {
            session = auth.login(username, password, role);
        } catch (IllegalArgumentException e) {
            failure.fail();
            throw new HttpError(401, e.getMessage());
        } finally {
            Arrays.fill(password, '\0');
        }
        failure.reset();
        purgeExpired();
        String token = newToken();
        sessions.put(token, new Entry(token, session));
        User user = session.user();
        return Map.of("token", token, "username", user.username(),
                "displayName", user.displayName(), "role", user.role().name());
    }

    private Entry authenticate(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) throw unauthorized();
        String token = header.substring(7).trim();
        Entry entry = sessions.get(token);
        long now = System.currentTimeMillis();
        if (entry == null || !entry.session.isActive() || now - entry.lastUsed > IDLE_MS) {
            if (entry != null) sessions.remove(token);
            throw unauthorized();
        }
        entry.lastUsed = now;
        return entry;
    }

    private static HttpError unauthorized() {
        return new HttpError(401, "Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại.");
    }

    private static void require(Entry entry, Role role) {
        if (!entry.session.user().role().canAccess(role))
            throw new HttpError(403, "Tài khoản không có quyền thực hiện thao tác này.");
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        sessions.values().removeIf(e -> !e.session.isActive() || now - e.lastUsed > IDLE_MS);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // ---------- Tiện ích HTTP ----------

    private static Map<String, Object> readBody(HttpExchange ex) {
        try (InputStream in = ex.getRequestBody()) {
            byte[] data = in.readNBytes(MAX_BODY + 1);
            if (data.length > MAX_BODY) throw new HttpError(413, "Yêu cầu quá lớn.");
            return Json.parseObject(new String(data, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new HttpError(400, "Không đọc được nội dung yêu cầu.");
        }
    }

    private static long queryLong(HttpExchange ex, String name) {
        String query = ex.getRequestURI().getRawQuery();
        if (query == null) return -1;
        for (String part : query.split("&")) {
            if (part.startsWith(name + "=")) {
                try {
                    return Long.parseLong(part.substring(name.length() + 1));
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private static final class HttpError extends RuntimeException {
        final int status;

        HttpError(int status, String message) {
            super(message, null, false, false);
            this.status = status;
        }
    }

    private static final class Entry {
        final String token;
        final AuthService.Session session;
        volatile long lastUsed = System.currentTimeMillis();

        Entry(String token, AuthService.Session session) {
            this.token = token;
            this.session = session;
        }
    }

    /** Chặn tạm một địa chỉ sau nhiều lần đăng nhập sai liên tiếp. */
    private static final class Failure {
        private int count;
        private long until;

        synchronized boolean blocked() {
            if (until == 0) return false;
            if (System.currentTimeMillis() < until) return true;
            until = 0;
            count = 0;
            return false;
        }

        synchronized void fail() {
            if (++count >= MAX_FAILURES) until = System.currentTimeMillis() + LOCKOUT_MS;
        }

        synchronized void reset() {
            count = 0;
            until = 0;
        }
    }
}
