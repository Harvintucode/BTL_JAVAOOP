package cafe;

import java.util.Objects;

/** Thông tin tài khoản bất biến; không mang mật khẩu vào lớp giao diện. */
public record User(String username, String displayName, Role role) {
    public User {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(role, "role");
        if (!username.matches("[a-z0-9._-]{1,64}"))
            throw new IllegalArgumentException("Tên đăng nhập không hợp lệ.");
        if (displayName.isBlank() || displayName.length() > 100)
            throw new IllegalArgumentException("Tên hiển thị không hợp lệ.");
    }
}
