package cafe;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Kiểm thử đăng nhập, phiên làm việc và dữ liệu tài khoản giữa nhiều JVM. */
public final class AuthTest {
    private static int checks;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static void expect(Class<? extends RuntimeException> type, Runnable action, String message) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            check(type.isInstance(ex), message + ": unexpected " + ex.getClass().getSimpleName());
            return;
        }
        throw new AssertionError(message + ": did not reject");
    }

    private static AuthService.Session login(AuthService auth, String username, String password, Role role) {
        char[] chars = password.toCharArray();
        try {
            return auth.login(username, chars, role);
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--worker".equals(args[0])) {
            concurrentWorker(Path.of(args[1]), args[2]);
            return;
        }
        String previousHome = System.getProperty("cafe.home");
        try {
            authenticationAndStorage();
            concurrentThreads();
            concurrentFirstRun();
            System.out.println("PASS - " + checks
                    + " authentication checks: roles, passwords, sessions, persistence, corrupt data, concurrent JVMs.");
        } finally {
            if (previousHome == null) System.clearProperty("cafe.home");
            else System.setProperty("cafe.home", previousHome);
        }
    }

    private static void authenticationAndStorage() throws Exception {
        Path home = Files.createTempDirectory("cotafe-auth-test-");
        System.setProperty("cafe.home", home.toString());
        // File nghiệp vụ phải được giữ nguyên, kể cả khi khởi tạo tài khoản lần đầu.
        byte[] businessData = { 1, 5, 9, 12, 25, 50 };
        Files.write(home.resolve("cafe.dat"), businessData);
        AuthService auth = new AuthService();
        AuthService.Session admin = login(auth, "admin", "Admin@123", Role.ADMIN);
        check(admin.isActive(), "Successful login creates active session");
        check("admin".equals(admin.user().username()), "Admin username");
        check(admin.user().role() == Role.ADMIN, "Admin role");
        check(!admin.user().displayName().isBlank(), "Admin display name");
        admin.requireRole(Role.ADMIN);
        admin.requireRole(Role.STAFF);
        check(Role.ADMIN.canAccess(Role.ADMIN) && Role.ADMIN.canAccess(Role.STAFF), "Admin role permissions");
        check(Role.STAFF.canAccess(Role.STAFF) && !Role.STAFF.canAccess(Role.ADMIN), "Staff role permissions");

        AuthService.Session staff = login(auth, "staff", "Staff@123", Role.STAFF);
        check(staff.user().role() == Role.STAFF && "staff".equals(staff.user().username()), "Staff login");
        staff.requireRole(Role.STAFF);
        expect(IllegalArgumentException.class, () -> staff.requireRole(Role.ADMIN),
                "Staff session cannot access admin functionality");
        check(login(auth, "admin", "Admin@123", Role.STAFF).user().role() == Role.ADMIN, "Admin can open POS");
        check("admin".equals(login(auth, "  AdMiN  ", "Admin@123", Role.ADMIN).user().username()),
                "Username trimmed and case-insensitive");
        expect(IllegalArgumentException.class, () -> login(auth, "staff", "Staff@123", Role.ADMIN),
                "Staff cannot open admin application");
        expect(IllegalArgumentException.class, () -> login(auth, "admin", "wrong", Role.ADMIN), "Wrong password");
        expect(IllegalArgumentException.class, () -> login(auth, "admin", "admin@123", Role.ADMIN),
                "Password remains case-sensitive");
        expect(IllegalArgumentException.class, () -> login(auth, "admin", " Admin@123 ", Role.ADMIN),
                "Password whitespace is not trimmed");
        expect(IllegalArgumentException.class, () -> login(auth, "nobody", "Admin@123", Role.STAFF), "Unknown user");
        expect(IllegalArgumentException.class, () -> login(auth, "  ", "Admin@123", Role.ADMIN), "Blank username");
        expect(IllegalArgumentException.class, () -> login(auth, "admin", "", Role.ADMIN), "Empty password");

        byte[] saved = Files.readAllBytes(home.resolve("users.dat"));
        String storedText = new String(saved, StandardCharsets.ISO_8859_1);
        check(!storedText.contains("Admin@123") && !storedText.contains("Staff@123"),
                "Account store contains no plaintext demo passwords");
        AuthService reopened = new AuthService();
        check(login(reopened, "staff", "Staff@123", Role.STAFF).isActive(), "Accounts persist after reopen");
        check(Arrays.equals(saved, Files.readAllBytes(home.resolve("users.dat"))),
                "Reopening and login do not regenerate account data");
        check(Arrays.equals(businessData, Files.readAllBytes(home.resolve("cafe.dat"))), "Existing cafe.dat unchanged");

        admin.logout();
        check(!admin.isActive(), "Logout deactivates session");
        expect(IllegalStateException.class, () -> admin.requireRole(Role.STAFF), "Logged-out session cannot access POS");
        expect(IllegalStateException.class, () -> admin.requireRole(Role.ADMIN), "Logged-out session cannot access admin");
        expect(IllegalStateException.class, admin::user, "Logged-out session cannot expose active user");
        admin.logout();
        check(!admin.isActive(), "Repeated logout remains safe");
        check(staff.isActive(), "Logging out one session does not invalidate another");
        check(login(auth, "admin", "Admin@123", Role.ADMIN).isActive(), "Login again after logout");

        Path secondHome = Files.createTempDirectory("cotafe-auth-salt-");
        System.setProperty("cafe.home", secondHome.toString());
        login(new AuthService(), "admin", "Admin@123", Role.ADMIN).logout();
        check(!Arrays.equals(saved, Files.readAllBytes(secondHome.resolve("users.dat"))),
                "Independent stores use different random password salts");

        Path corruptHome = Files.createTempDirectory("cotafe-auth-corrupt-");
        System.setProperty("cafe.home", corruptHome.toString());
        byte[] corrupt = "invalid account data".getBytes(StandardCharsets.UTF_8);
        Files.write(corruptHome.resolve("users.dat"), corrupt);
        expect(IllegalStateException.class, () -> login(new AuthService(), "admin", "Admin@123", Role.ADMIN),
                "Corrupt store must fail closed");
        check(Arrays.equals(corrupt, Files.readAllBytes(corruptHome.resolve("users.dat"))),
                "Corrupt store is not overwritten with default credentials");
        Files.write(corruptHome.resolve("users.dat"), new byte[0]);
        expect(IllegalStateException.class, () -> login(new AuthService(), "staff", "Staff@123", Role.STAFF),
                "Empty existing store must fail closed");
        check(Files.size(corruptHome.resolve("users.dat")) == 0, "Empty existing store is not reseeded");
    }

    private static void concurrentThreads() throws Exception {
        Path home = Files.createTempDirectory("cotafe-auth-threads-");
        System.setProperty("cafe.home", home.toString());
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            AuthService auth = new AuthService();
            if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Thread start timeout");
            AuthService.Session session = login(auth, "admin", "Admin@123", Role.ADMIN);
            boolean active = session.isActive();
            session.logout();
            return active;
        };
        try {
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            start.countDown();
            check(first.get(30, TimeUnit.SECONDS), "First simultaneous same-JVM login");
            check(second.get(30, TimeUnit.SECONDS), "Second simultaneous same-JVM login");
        } finally {
            executor.shutdownNow();
            check(executor.awaitTermination(5, TimeUnit.SECONDS), "Authentication test threads stopped");
        }
    }

    private static void concurrentFirstRun() throws Exception {
        Path home = Files.createTempDirectory("cotafe-auth-race-");
        String javaName = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", javaName);
        List<Process> workers = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                workers.add(new ProcessBuilder(java.toString(), "-Djava.awt.headless=true", "-cp",
                        System.getProperty("java.class.path"), AuthTest.class.getName(), "--worker",
                        home.toString(), Integer.toString(i))
                        .redirectErrorStream(true).redirectOutput(home.resolve("worker-" + i + ".log").toFile())
                        .start());
            }
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (!Files.exists(home.resolve("ready-0")) || !Files.exists(home.resolve("ready-1"))) {
                if (System.nanoTime() > deadline) throw new AssertionError("Auth JVM workers did not reach start barrier");
                for (Process worker : workers)
                    if (!worker.isAlive()) throw new AssertionError("Auth JVM worker exited before start barrier");
                Thread.sleep(20);
            }
            Files.writeString(home.resolve("start"), "start");
            for (int i = 0; i < workers.size(); i++) {
                Process worker = workers.get(i);
                check(worker.waitFor(60, TimeUnit.SECONDS), "Auth JVM worker completed within timeout");
                check(worker.exitValue() == 0, "Auth JVM worker " + i + ": "
                        + Files.readString(home.resolve("worker-" + i + ".log")));
            }
            System.setProperty("cafe.home", home.toString());
            AuthService auth = new AuthService();
            check(login(auth, "admin", "Admin@123", Role.ADMIN).isActive(), "Admin valid after concurrent initialization");
            check(login(auth, "staff", "Staff@123", Role.STAFF).isActive(), "Staff valid after concurrent initialization");
        } finally {
            for (Process worker : workers) {
                if (worker.isAlive()) {
                    worker.destroyForcibly();
                    worker.waitFor(5, TimeUnit.SECONDS);
                }
            }
        }
    }

    private static void concurrentWorker(Path home, String id) throws Exception {
        System.setProperty("cafe.home", home.toString());
        AuthService auth = new AuthService();
        Files.writeString(home.resolve("ready-" + id), "ready");
        long deadline = System.nanoTime() + Duration.ofSeconds(40).toNanos();
        while (!Files.exists(home.resolve("start"))) {
            if (System.nanoTime() > deadline) throw new AssertionError("Start barrier timeout");
            Thread.sleep(20);
        }
        login(auth, "admin", "Admin@123", Role.ADMIN).logout();
        login(auth, "staff", "Staff@123", Role.STAFF).logout();
        System.out.println("Auth worker " + id + " passed");
    }
}
