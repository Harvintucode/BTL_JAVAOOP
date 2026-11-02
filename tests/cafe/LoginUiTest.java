package cafe;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Kiểm thử Swing tùy chọn: chạy trên máy có giao diện, không dùng dữ liệu thật. */
public final class LoginUiTest {
    private static int checks;

    private LoginUiTest() {}

    public static void main(String[] args) throws Exception {
        Path home = Files.createTempDirectory("cotafe-ui-test-");
        System.setProperty("cafe.home", home.toString());
        try {
            edt(() -> { Ui.theme(); return null; });
            LoginFrame posLogin = openLogin(Role.STAFF);
            if (args.length > 0) render(posLogin, Path.of(args[0]));
            check(edt(() -> visible(PosApp.class) == null && visible(AdminApp.class) == null),
                    "Main applications must not appear before login");
            check(edt(() -> password(posLogin).getEchoChar() != 0), "Password is masked");

            submit(posLogin, "", "");
            check(edt(() -> posLogin.isShowing()
                    && status(posLogin).getText().contains("Vui lòng nhập")),
                    "Empty fields keep the login visible with a validation message");
            check(edt(() -> visible(PosApp.class) == null), "Empty fields cannot open POS");

            submit(posLogin, "staff", "incorrect-password");
            waitIdle(posLogin);
            check(edt(() -> posLogin.isShowing()
                    && status(posLogin).getText().contains("không đúng")),
                    "Incorrect password shows an error and keeps login visible");
            check(edt(() -> password(posLogin).getPassword().length == 0),
                    "Incorrect password is cleared after authentication completes");
            check(edt(() -> visible(PosApp.class) == null), "Incorrect password cannot open POS");

            submit(posLogin, "staff", "Staff@123");
            PosApp staffPos = awaitFrame(PosApp.class);
            check(edt(() -> !posLogin.isDisplayable()), "Successful login disposes the login frame");
            check(edt(() -> hasLabel(staffPos, "Xin chào, staff!")),
                    "Staff login shows the authenticated identity in POS");
            LoginFrame returnedPosLogin = logout(staffPos);
            check(edt(() -> !staffPos.isDisplayable()), "Logout disposes POS");
            check(edt(() -> username(returnedPosLogin).getText().isEmpty()
                    && password(returnedPosLogin).getPassword().length == 0),
                    "Logout opens a fresh login with empty credentials");

            submit(returnedPosLogin, "admin", "Admin@123");
            PosApp adminPos = awaitFrame(PosApp.class);
            check(edt(() -> hasLabel(adminPos, "Xin chào, admin!")),
                    "Admin can sign in to POS");
            LoginFrame unusedPosLogin = logout(adminPos);
            edt(() -> { unusedPosLogin.dispose(); return null; });

            LoginFrame adminLogin = openLogin(Role.ADMIN);
            submit(adminLogin, "staff", "Staff@123");
            waitIdle(adminLogin);
            check(edt(() -> adminLogin.isShowing()
                    && status(adminLogin).getText().contains("không có quyền")),
                    "Staff cannot sign in to Admin");
            check(edt(() -> visible(AdminApp.class) == null), "Denied staff login cannot open Admin");
            check(edt(() -> password(adminLogin).getPassword().length == 0),
                    "Denied role clears the entered password");

            submit(adminLogin, "admin", "Admin@123");
            AdminApp admin = awaitFrame(AdminApp.class);
            check(edt(() -> hasLabel(admin, "Quản trị viên") && !adminLogin.isDisplayable()),
                    "Admin login opens the management application");
            LoginFrame returnedAdminLogin = logout(admin);
            check(edt(() -> returnedAdminLogin.getTitle().contains(Role.ADMIN.label())),
                    "Admin logout returns to the Admin login");
            submit(returnedAdminLogin, "admin", "Admin@123");
            AdminApp adminAgain = awaitFrame(AdminApp.class);
            check(adminAgain != admin, "A new Admin session can log in after logout");
            LoginFrame lastAdminLogin = logout(adminAgain);
            edt(() -> { lastAdminLogin.dispose(); return null; });

            AuthService.Session staffSession = new AuthService().login(
                    "staff", "Staff@123".toCharArray(), Role.STAFF);
            check(edt(() -> {
                try { new AdminApp(staffSession); return false; }
                catch (IllegalArgumentException expected) { return true; }
            }), "Admin constructor rejects a valid staff session");
            staffSession.logout();
            check(edt(() -> {
                try { new PosApp(staffSession); return false; }
                catch (IllegalStateException expected) { return true; }
            }), "POS constructor rejects a logged-out session");

            LoginFrame closedLogin = openLogin(Role.STAFF);
            edt(() -> {
                username(closedLogin).setText("staff");
                password(closedLogin).setText("Staff@123");
                loginButton(closedLogin).doClick();
                check(!loginButton(closedLogin).isEnabled(), "Authentication runs asynchronously");
                // done() cannot execute until this EDT callback returns.
                closedLogin.dispose();
                return null;
            });
            waitIdle(closedLogin);
            check(edt(() -> !closedLogin.isDisplayable()
                    && visible(PosApp.class) == null && visible(AdminApp.class) == null),
                    "Closing login during authentication cannot open an application afterwards");
            System.out.println("PASS - " + checks
                    + " Swing login checks: validation, roles, logout, re-login, disposal race.");
        } finally {
            edt(() -> {
                for (Window window : Window.getWindows()) window.dispose();
                return null;
            });
        }
    }

    private static LoginFrame openLogin(Role role) throws Exception {
        return edt(() -> {
            LoginFrame frame = new LoginFrame(role);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setVisible(true);
            return frame;
        });
    }

    private static void submit(LoginFrame frame, String username, String password) throws Exception {
        edt(() -> {
            username(frame).setText(username);
            password(frame).setText(password);
            loginButton(frame).doClick();
            return null;
        });
    }

    private static LoginFrame logout(JFrame frame) throws Exception {
        edt(() -> {
            JButton logout = (JButton) find(frame,
                    c -> c instanceof JButton b && "Đăng xuất".equals(b.getText()));
            if (logout == null) throw new AssertionError("Missing logout button");
            logout.doClick();
            return null;
        });
        return awaitFrame(LoginFrame.class);
    }

    private static void waitIdle(LoginFrame frame) throws Exception {
        await(() -> loginButton(frame).isEnabled(), "Authentication did not finish");
    }

    private static <T extends JFrame> T awaitFrame(Class<T> type) throws Exception {
        await(() -> visible(type) != null, "Frame did not appear: " + type.getSimpleName());
        return edt(() -> visible(type));
    }

    private static void await(Callable<Boolean> condition, String message) throws Exception {
        long deadline = System.nanoTime() + 15_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (edt(condition)) return;
            Thread.sleep(25); // Chờ ngoài EDT để SwingWorker.done vẫn chạy được.
        }
        throw new AssertionError(message);
    }

    private static <T> T edt(Callable<T> action) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) return action.call();
        FutureTask<T> task = new FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        try {
            return task.get();
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof Exception cause) throw cause;
            if (ex.getCause() instanceof Error cause) throw cause;
            throw new RuntimeException(ex.getCause());
        }
    }

    private static <T extends JFrame> T visible(Class<T> type) {
        for (Window window : Window.getWindows())
            if (type.isInstance(window) && window.isShowing()) return type.cast(window);
        return null;
    }

    private static Component find(Component component, Predicate<Component> matches) {
        if (matches.test(component)) return component;
        if (component instanceof Container container)
            for (Component child : container.getComponents()) {
                Component result = find(child, matches);
                if (result != null) return result;
            }
        return null;
    }

    private static Component named(LoginFrame frame, String name) {
        Component found = find(frame, c -> name.equals(c.getName()));
        if (found == null) throw new AssertionError("Missing login component: " + name);
        return found;
    }

    private static JTextField username(LoginFrame frame) {
        return (JTextField) named(frame, "username");
    }

    private static JPasswordField password(LoginFrame frame) {
        return (JPasswordField) named(frame, "password");
    }

    private static JButton loginButton(LoginFrame frame) {
        return (JButton) named(frame, "login");
    }

    private static JTextArea status(LoginFrame frame) {
        return (JTextArea) named(frame, "status");
    }

    private static boolean hasLabel(JFrame frame, String text) {
        return find(frame, c -> c instanceof JLabel label && text.equals(label.getText())) != null;
    }

    private static void render(LoginFrame frame, Path output) throws Exception {
        BufferedImage image = edt(() -> {
            BufferedImage result = new BufferedImage(frame.getWidth(), frame.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = result.createGraphics();
            try { frame.printAll(graphics); }
            finally { graphics.dispose(); }
            return result;
        });
        ImageIO.write(image, "png", output.toFile());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
