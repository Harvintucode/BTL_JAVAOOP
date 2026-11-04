package cafe;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Kho tài khoản riêng, không thay đổi định dạng dữ liệu bán hàng cafe.dat. */
public final class FileUserRepository implements UserRepository {
    private static final int MAGIC = 0x43545553; // CTUS
    private static final int VERSION = 1;
    // FileLock bảo vệ giữa JVM; monitor tránh khóa chồng nhau trong cùng JVM.
    private static final Object JVM_LOCK = new Object();
    private final Path home;
    private final Path data;
    private final Path lock;

    public FileUserRepository() {
        this(Path.of(System.getProperty("cafe.home", ".")));
    }

    public FileUserRepository(Path home) {
        this.home = Objects.requireNonNull(home, "home").toAbsolutePath().normalize();
        this.data = this.home.resolve("users.dat");
        this.lock = this.home.resolve("users.lock");
    }

    @Override
    public Optional<Account> findByUsername(String username) {
        synchronized (JVM_LOCK) {
            try {
                Files.createDirectories(home);
                try (FileChannel channel = FileChannel.open(lock,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                     FileLock ignored = channel.lock()) {
                    if (Files.notExists(data)) writeInitialAccounts();
                    return Optional.ofNullable(readAccounts().get(username));
                }
            } catch (IOException | IllegalArgumentException ex) {
                // Không tự tạo lại tài khoản khi file tồn tại nhưng hỏng.
                throw new IllegalStateException("Không thể đọc kho tài khoản " + data
                        + ". Hãy kiểm tra quyền truy cập hoặc bản sao lưu.", ex);
            }
        }
    }

    private Map<String, Account> readAccounts() throws IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(data)))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION)
                throw new IOException("Định dạng kho tài khoản không hợp lệ.");
            int count = in.readInt();
            if (count < 1 || count > 1000)
                throw new IOException("Số tài khoản không hợp lệ.");
            Map<String, Account> accounts = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                User user = new User(in.readUTF(), in.readUTF(), Role.valueOf(in.readUTF()));
                Account account = new Account(user, in.readUTF());
                if (accounts.putIfAbsent(user.username(), account) != null)
                    throw new IOException("Trùng tên đăng nhập.");
            }
            if (in.read() != -1)
                throw new IOException("Kho tài khoản có dữ liệu dư.");
            return accounts;
        }
    }

    private void writeInitialAccounts() throws IOException {
        List<Account> initial = List.of(
                demoAccount("admin", "Quản trị viên", Role.ADMIN, "Admin@123".toCharArray()),
                demoAccount("staff", "Nhân viên", Role.STAFF, "Staff@123".toCharArray()));
        Path temp = Files.createTempFile(home, "users-", ".tmp");
        try {
            try (DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(temp)))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(initial.size());
                for (Account account : initial) {
                    out.writeUTF(account.user().username());
                    out.writeUTF(account.user().displayName());
                    out.writeUTF(account.user().role().name());
                    out.writeUTF(account.encodedPassword());
                }
            }
            try {
                Files.move(temp, data, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, data, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static Account demoAccount(String username, String name, Role role, char[] password) {
        try {
            return new Account(new User(username, name, role), PasswordHasher.hash(password));
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
