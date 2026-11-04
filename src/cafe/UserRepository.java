package cafe;

import java.util.Objects;
import java.util.Optional;

/** Hợp đồng lưu trữ giúp lớp xác thực không phụ thuộc cách đọc file. */
public interface UserRepository {
    Optional<Account> findByUsername(String username);

    /** Chỉ kho tài khoản giữ dữ liệu băm; giao diện chỉ nhận User. */
    final class Account {
        private final User user;
        private final String encodedPassword;

        public Account(User user, String encodedPassword) {
            this.user = Objects.requireNonNull(user, "user");
            PasswordHasher.validateEncoding(encodedPassword);
            this.encodedPassword = encodedPassword;
        }

        public User user() {
            return user;
        }

        public boolean matchesPassword(char[] password) {
            return PasswordHasher.verify(password, encodedPassword);
        }

        String encodedPassword() {
            return encodedPassword;
        }
    }
}
