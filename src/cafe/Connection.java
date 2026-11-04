package cafe;

import java.util.Objects;

/**
 * Kết nối đã xác thực của một app: phiên đăng nhập + nguồn dữ liệu.
 * Nếu đặt {@code -Dcafe.server=IP[:cổng]} thì đăng nhập và dữ liệu đều đi qua máy chủ LAN;
 * nếu không, chạy cục bộ trên cùng máy như trước.
 */
public record Connection(AuthService.Session session, CafeApi api) {
    public static final String SERVER_PROPERTY = "cafe.server";

    public Connection {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(api, "api");
    }

    /** Địa chỉ máy chủ LAN, hoặc null nếu đang chạy cục bộ. */
    public static String serverAddress() {
        String value = System.getProperty(SERVER_PROPERTY, "").trim();
        return value.isEmpty() ? null : value;
    }

    /** Bên gọi sở hữu và xóa mảng password sau khi phương thức hoàn tất. */
    public static Connection open(String username, char[] password, Role requiredRole) {
        String server = serverAddress();
        if (server == null)
            return new Connection(new AuthService().login(username, password, requiredRole), new CafeService());
        RemoteCafeApi remote = RemoteCafeApi.login(server, username, password, requiredRole);
        return new Connection(AuthService.remoteSession(remote.user()), remote);
    }

    public void close() {
        session.logout();
        api.close();
    }
}
