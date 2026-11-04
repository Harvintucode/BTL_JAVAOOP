package cafe;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/** Giao diện đăng nhập dùng chung; xác thực được giao cho AuthService. */
public final class LoginFrame extends JFrame {
    private final Role requiredRole;
    private final JTextField usernameField = Ui.input("");
    private final JPasswordField passwordField = new JPasswordField();
    private final JCheckBox showPassword = new JCheckBox("Hiện mật khẩu");
    private final JButton loginButton = Ui.button("Đăng nhập", true, e -> login());
    private final JTextArea status = new JTextArea(3, 28);
    private boolean busy;

    public LoginFrame(Role requiredRole) {
        super("CAFE • Đăng nhập " + Objects.requireNonNull(requiredRole).label()
                + (Connection.serverAddress() == null ? "" : " • Máy chủ " + Connection.serverAddress()));
        this.requiredRole = requiredRole;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(520, 590);
        setResizable(false);
        setLocationRelativeTo(null);
        getContentPane().setBackground(Ui.BG);
        setLayout(new GridBagLayout());

        JPanel card = Ui.card(new BorderLayout(0, 24));
        Ui.pad(card, 30, 32, 24, 32);
        card.setPreferredSize(new Dimension(440, 490));
        JPanel heading = Ui.vertical(0);
        heading.add(Ui.label("CAFE", 28, true, Ui.BLUE));
        heading.add(Box.createVerticalStrut(10));
        heading.add(Ui.label("Đăng nhập " + requiredRole.label(), 22, true, Ui.DARK));
        heading.add(Box.createVerticalStrut(8));
        heading.add(Ui.label(requiredRole == Role.ADMIN
                ? "Dành cho tài khoản quản trị viên."
                : "Dành cho nhân viên và quản trị viên.", 12, false, Ui.MUTED));
        card.add(heading, BorderLayout.NORTH);

        JPanel form = Ui.vertical(0);
        JLabel usernameLabel = Ui.label("Tên đăng nhập", 13, true, Ui.DARK);
        usernameLabel.setLabelFor(usernameField);
        form.add(usernameLabel);
        form.add(Box.createVerticalStrut(7));
        usernameField.setName("username");
        usernameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        usernameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(usernameField);
        form.add(Box.createVerticalStrut(16));
        JLabel passwordLabel = Ui.label("Mật khẩu", 13, true, Ui.DARK);
        passwordLabel.setLabelFor(passwordField);
        form.add(passwordLabel);
        form.add(Box.createVerticalStrut(7));
        passwordField.setName("password");
        passwordField.setFont(Ui.font(13, false));
        passwordField.setBorder(usernameField.getBorder());
        passwordField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        passwordField.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(passwordField);
        form.add(Box.createVerticalStrut(8));
        char echo = passwordField.getEchoChar();
        showPassword.setOpaque(false);
        showPassword.setFont(Ui.font(12, false));
        showPassword.setForeground(Ui.MUTED);
        showPassword.setAlignmentX(Component.LEFT_ALIGNMENT);
        showPassword.addActionListener(e -> passwordField.setEchoChar(showPassword.isSelected() ? 0 : echo));
        form.add(showPassword);
        form.add(Box.createVerticalStrut(18));
        loginButton.setName("login");
        loginButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        loginButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(loginButton);
        card.add(form, BorderLayout.CENTER);

        status.setName("status");
        status.setEditable(false);
        status.setFocusable(false);
        status.setOpaque(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        status.setFont(Ui.font(12, false));
        status.setForeground(Ui.RED);
        status.getAccessibleContext().setAccessibleName("Trạng thái đăng nhập");
        card.add(status, BorderLayout.SOUTH);
        add(card);
        getRootPane().setDefaultButton(loginButton);
    }

    private void login() {
        if (busy) return;
        String username = usernameField.getText();
        char[] password = passwordField.getPassword();
        if (username.isBlank() || password.length == 0) {
            Arrays.fill(password, '\0');
            status.setText("Vui lòng nhập tên đăng nhập và mật khẩu.");
            return;
        }
        setBusy(true);
        status.setForeground(Ui.MUTED);
        status.setText("Đang đăng nhập…");
        // Băm mật khẩu và truy cập file ở luồng nền để giao diện vẫn phản hồi.
        new SwingWorker<Connection, Void>() {
            @Override
            protected Connection doInBackground() {
                try {
                    return Connection.open(username, password, requiredRole);
                } finally {
                    Arrays.fill(password, '\0');
                }
            }

            @Override
            protected void done() {
                Connection connection = null;
                try {
                    connection = get();
                    if (!isDisplayable()) {
                        connection.close();
                        return;
                    }
                    JFrame app = requiredRole == Role.ADMIN
                            ? new AdminApp(connection.session(), connection.api())
                            : new PosApp(connection.session(), connection.api());
                    app.setVisible(true);
                    dispose();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showFailure("Đăng nhập bị gián đoạn. Vui lòng thử lại.");
                } catch (ExecutionException ex) {
                    showFailure(ex.getCause().getMessage());
                } catch (RuntimeException ex) {
                    if (connection != null) connection.close();
                    showFailure(ex.getMessage());
                } finally {
                    passwordField.setText("");
                    setBusy(false);
                    if (isDisplayable()) passwordField.requestFocusInWindow();
                }
            }
        }.execute();
    }

    private void showFailure(String message) {
        status.setForeground(Ui.RED);
        status.setText(message == null ? "Không thể đăng nhập. Vui lòng thử lại." : message);
    }

    private void setBusy(boolean value) {
        busy = value;
        usernameField.setEnabled(!value);
        passwordField.setEnabled(!value);
        showPassword.setEnabled(!value);
        loginButton.setEnabled(!value);
        loginButton.setText(value ? "Đang đăng nhập…" : "Đăng nhập");
    }
}
