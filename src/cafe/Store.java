package cafe;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.function.Function;

/** Đọc/ghi an toàn giữa hai tiến trình JVM: khóa file + ghi tạm rồi thay thế. */
public final class Store {
    private final Path home = Path.of(System.getProperty("cafe.home", ".")).toAbsolutePath().normalize();
    private final Path data = home.resolve("cafe.dat");
    private final Path lock = home.resolve("cafe.lock");
    private final Path temp = home.resolve("cafe.tmp");

    public <T> T read(Function<Data.State, T> action) { return transaction(action, false); }
    public <T> T update(Function<Data.State, T> action) { return transaction(action, true); }

    private <T> T transaction(Function<Data.State, T> action, boolean write) {
        try {
            Files.createDirectories(home);
            try (FileChannel channel = FileChannel.open(lock,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                boolean firstRun = Files.notExists(data);
                Data.State state;
                if (firstRun) state = new Data.State();
                else try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(data))) {
                    state = (Data.State) in.readObject();
                }
                T result = action.apply(state);
                if (write || firstRun) {
                    if (write) state.revision++;
                    try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(temp))) {
                        out.writeObject(state);
                    }
                    try {
                        Files.move(temp, data, StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE);
                    } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                        Files.move(temp, data, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                return result;
            }
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) throw (IllegalArgumentException) e;
            if (e instanceof IllegalStateException) throw (IllegalStateException) e;
            throw new IllegalStateException("Không thể đọc/ghi " + data + ": " + e.getMessage(), e);
        }
    }
}
