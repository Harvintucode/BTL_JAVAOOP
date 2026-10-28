package cafe;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Bộ giao diện Swing dùng chung: màu, nút, thẻ, báo cáo. */
public final class Ui {
    private Ui() {
    }

    public static final Color BG = new Color(244, 247, 252);
    public static final Color WHITE = Color.WHITE;
    public static final Color BLUE = new Color(40, 111, 232);
    public static final Color PALE = new Color(231, 240, 255);
    public static final Color DARK = new Color(33, 43, 60);
    public static final Color MUTED = new Color(116, 126, 146);
    public static final Color RED = new Color(206, 66, 78);
    public static final Color GREEN = new Color(35, 145, 104);
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public static void theme() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        UIManager.put("Label.font", font(13, false));
        UIManager.put("ComboBox.font", font(13, false));
        UIManager.put("TextField.font", font(13, false));
    }

    public static Font font(int size, boolean bold) {
        return new Font("Segoe UI", bold ? Font.BOLD : Font.PLAIN, size);
    }

    public static JLabel label(String text, int size, boolean bold, Color color) {
        JLabel l = new JLabel(text);
        l.setForeground(color);
        l.setFont(font(size, bold));
        return l;
    }

    public static String money(long value) {
        return String.format(Locale.US, "%,d", value).replace(',', '.') + " ₫";
    }

    public static JPanel vertical(int gap) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    public static JPanel horizontal(int gap) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, gap, 0));
        p.setOpaque(false);
        return p;
    }

    public static JPanel panel(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        return p;
    }

    public static RoundPanel card(LayoutManager layout) {
        RoundPanel p = new RoundPanel(18, WHITE);
        p.setLayout(layout);
        return p;
    }

    public static void pad(JComponent component, int top, int left, int bottom, int right) {
        component.setBorder(new EmptyBorder(top, left, bottom, right));
    }

    public static JButton button(String caption, boolean primary, ActionListener onClick) {
        FlatButton b = new FlatButton(caption, primary ? BLUE : PALE,
                primary ? WHITE : BLUE);
        b.addActionListener(onClick);
        return b;
    }

    public static JButton light(String caption, ActionListener onClick) {
        return button(caption, false, onClick);
    }

    public static JScrollPane scroll(Component component) {
        JScrollPane pane = new JScrollPane(component);
        pane.setBorder(null);
        pane.getViewport().setBackground(BG);
        pane.setBackground(BG);
        pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        pane.getVerticalScrollBar().setUnitIncrement(16);
        return pane;
    }

    public static void message(Component parent, String text) {
        JOptionPane.showMessageDialog(parent, text, "CAFE", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void error(Component parent, Exception e) {
        JOptionPane.showMessageDialog(parent, e.getMessage(), "Không thể thực hiện", JOptionPane.ERROR_MESSAGE);
    }

    public static JTextField input(String initial) {
        JTextField field = new JTextField(initial);
        field.setFont(font(13, false));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(219, 226, 238)),
                new EmptyBorder(9, 10, 9, 10)));
        return field;
    }

    public static JPanel report(Data.State s, LocalDate day) {
        CafeService.Report r = CafeService.report(s, day);
        JPanel root = panel(new BorderLayout(0, 20));
        pad(root, 8, 3, 20, 3);
        JLabel heading = label("Báo cáo doanh thu • " + day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                22, true, DARK);
        root.add(heading, BorderLayout.NORTH);
        JPanel tiles = panel(new GridLayout(2, 2, 14, 14));
        tiles.add(metric("Doanh thu thực thu", money(r.revenue()), GREEN));
        tiles.add(metric("Đơn đã thanh toán", String.valueOf(r.completed()), BLUE));
        tiles.add(metric("Giá trị trước giảm", money(r.gross()), DARK));
        tiles.add(metric("Tổng khuyến mãi", "− " + money(r.discount()), RED));
        JPanel bottom = card(new GridLayout(4, 2, 12, 12));
        pad(bottom, 22, 24, 22, 24);
        bottom.add(label("Thanh toán tiền mặt", 14, false, MUTED));
        bottom.add(label(money(r.cash()), 15, true, DARK));
        bottom.add(label("Thanh toán QR", 14, false, MUTED));
        bottom.add(label(money(r.qr()), 15, true, DARK));
        bottom.add(label("Đơn đang xử lý (tạo trong ngày)", 13, false, MUTED));
        bottom.add(label(String.valueOf(r.pending()), 15, true, DARK));
        bottom.add(label("Đơn đã hủy (tạo trong ngày)", 13, false, MUTED));
        bottom.add(label(String.valueOf(r.cancelled()), 15, true, DARK));
        JPanel mid = panel(new BorderLayout(0, 14));
        mid.add(tiles, BorderLayout.NORTH);
        mid.add(bottom, BorderLayout.CENTER);
        root.add(mid, BorderLayout.CENTER);
        return root;
    }

    public static JPanel metric(String name, String value, Color accent) {
        JPanel tile = card(new BorderLayout(4, 9));
        pad(tile, 22, 22, 22, 22);
        tile.add(label(name, 13, false, MUTED), BorderLayout.NORTH);
        tile.add(label(value, 24, true, accent), BorderLayout.CENTER);
        return tile;
    }

    public static String status(Data.Status status) {
        return switch (status) {
            case PENDING -> "Đang xử lý";
            case PAID -> "Đã thanh toán";
            case CANCELLED -> "Đã hủy";
        };
    }

    public static String payment(Data.Payment method) {
        return method == Data.Payment.CASH ? "Tiền mặt" : "QR";
    }

    public static String serving(Data.Serving method) {
        return method == Data.Serving.DINE_IN ? "Tại chỗ" : "Mang đi";
    }

    public static final class RoundPanel extends JPanel {
        private final int arc;
        private final Color color;

        RoundPanel(int arc, Color color) {
            this.arc = arc;
            this.color = color;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    public static final class FlatButton extends JButton {
        private final Color fill;

        FlatButton(String text, Color bg, Color fg) {
            super(text);
            fill = bg;
            setForeground(fg);
            setFont(font(13, true));
            setBorder(new EmptyBorder(10, 14, 10, 14));
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(getModel().isPressed() ? fill.darker() : fill);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    public static final class Photo extends JComponent {
        private BufferedImage image;

        public Photo(String filename) {
            setPreferredSize(new Dimension(200, 140));
            try {
                image = ImageIO.read(Path.of(System.getProperty("cafe.home", "."),
                        "assets", filename).toFile());
            } catch (Exception ignored) {
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setClip(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 16, 16));
            if (image != null) {
                double scale = Math.max(getWidth() / (double) image.getWidth(),
                        getHeight() / (double) image.getHeight());
                int w = (int) Math.ceil(image.getWidth() * scale);
                int h = (int) Math.ceil(image.getHeight() * scale);
                g.drawImage(image, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
            } else {
                g.setColor(PALE);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            g.dispose();
        }
    }
}
