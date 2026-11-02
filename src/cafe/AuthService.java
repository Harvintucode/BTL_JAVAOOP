package cafe;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Xác thực tài khoản, kiểm tra vai trò và quản lý vòng đời phiên đăng nhập. */
public final class AuthService {
    // Vẫn thực hiện phép băm khi tên tài khoản không tồn tại.
    private static final String DUMMY_HASH = "pbkdf2-sha256$600000$"
            + "AAAAAAAAAAAAAAAAAAAAAA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private final UserRepository users;

    public AuthService() {
        this(new FileUserRepository());
    }

    public AuthService(UserRepository users) {
        this.users = Objects.requireNonNull(users, "users");
    }

    /** Bên gọi sở hữu và xóa mảng password sau khi phương thức hoàn tất. */
    public Session login(String username, char[] password, Role requiredRole) {
        if (username == null || username.isBlank() || password == null || password.length == 0)
            throw new IllegalArgumentException("Vui lòng nhập tên đăng nhập và mật khẩu.");
        if (username.length() > 128 || password.length > 1024)
            throw new IllegalArgumentException("Tên đăng nhập hoặc mật khẩu quá dài.");
        if (requiredRole == null)
            throw new IllegalArgumentException("Chưa xác định ứng dụng cần đăng nhập.");
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        Optional<UserRepository.Account> found = users.findByUsername(normalized);
        boolean matches = found.isPresent()
                ? found.get().matchesPassword(password)
                : PasswordHasher.verify(password, DUMMY_HASH);
        if (found.isEmpty() || !matches)
            throw new IllegalArgumentException("Tên đăng nhập hoặc mật khẩu không đúng.");
        User user = found.get().user();
        if (!user.role().canAccess(requiredRole))
            throw new IllegalArgumentException("Tài khoản không có quyền truy cập ứng dụng Admin.");
        return new Session(user);
    }

    /** Chỉ AuthService tạo được phiên sau khi xác thực thành công. */
    public static final class Session {
        private final User user;
        private volatile boolean active = true;

        private Session(User user) {
            this.user = user;
        }

        public User user() {
            requireActive();
            return user;
        }

        public void requireRole(Role role) {
            requireActive();
            if (!user.role().canAccess(role))
                throw new IllegalArgumentException("Tài khoản không có quyền truy cập chức năng này.");
        }

        public void logout() {
            active = false;
        }

        public boolean isActive() {
            return active;
        }

        private void requireActive() {
            if (!active)
                throw new IllegalStateException("Phiên đăng nhập đã kết thúc. Vui lòng đăng nhập lại.");
        }
    }
}
