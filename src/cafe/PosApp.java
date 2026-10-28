package cafe;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ứng dụng nhân viên/POS. */
public final class PosApp extends JFrame {
    private final CafeService service = new CafeService();
    private Data.State state = service.snapshot();
    private final Map<String, Integer> cart = new LinkedHashMap<>();
    private final JPanel contentHost = Ui.panel(new BorderLayout());
    private final JPanel cartHost = Ui.panel(new BorderLayout());
    private final JTextField searchField = Ui.input("");
    private JTextField customerField;
    private JComboBox<String> paymentBox;
    private JComboBox<String> servingBox;
    private String customer = "";
    private int paymentIndex = 0, servingIndex = 0;
    private String page = "MENU", category = "Tất cả";
    private LocalDate reportDay = LocalDate.now();

    public PosApp() {
        super("CAFE • Nhân viên POS");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1310, 800);
        setMinimumSize(new Dimension(1080, 650));
        setLocationRelativeTo(null);
        getContentPane().setBackground(Ui.BG);
        setLayout(new BorderLayout(12, 0));
        add(sidebar(), BorderLayout.WEST);
        JPanel middle = Ui.panel(new BorderLayout(0, 18));
        Ui.pad(middle, 24, 3, 20, 3);
        middle.add(header(), BorderLayout.NORTH);
        middle.add(contentHost, BorderLayout.CENTER);
        add(middle, BorderLayout.CENTER);
        add(cartHost, BorderLayout.EAST);
        showPage();
        new Timer(2300, e -> poll()).start();
    }

    private JPanel sidebar() {
        JPanel side = Ui.card(new BorderLayout(0, 28));
        side.setPreferredSize(new Dimension(181, 0));
        Ui.pad(side, 28, 15, 18, 15);
        JPanel menu = Ui.vertical(0);
        menu.add(Ui.label("CAFE", 23, true, Ui.BLUE));
        menu.add(Box.createVerticalStrut(10));
        menu.add(Ui.label("  QUẢN LÝ BÁN HÀNG", 10, true, Ui.MUTED));
        menu.add(Box.createVerticalStrut(38));
        addNav(menu, "Thực đơn", "MENU");
        addNav(menu, "Đơn hàng", "ORDERS");
        addNav(menu, "Doanh thu", "REPORT");
        side.add(menu, BorderLayout.NORTH);
        JPanel bottom = Ui.vertical(0);
        bottom.add(Ui.label("Đang kết nối dữ liệu", 11, true, Ui.GREEN));
        bottom.add(Box.createVerticalStrut(5));
        bottom.add(Ui.label("Đồng bộ mỗi 2,3 giây", 11, false, Ui.MUTED));
        side.add(bottom, BorderLayout.SOUTH);
        return side;
    }

    private void addNav(JPanel side, String text, String target) {
        JButton btn = Ui.light(text, e -> {
            page = target;
            showPage();
        });
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(btn);
        side.add(Box.createVerticalStrut(12));
    }

    private JPanel header() {
        JPanel h = Ui.panel(new BorderLayout(8, 0));
        JPanel name = Ui.vertical(0);
        name.add(Ui.label("Xin chào, nhân viên!", 25, true, Ui.DARK));
        name.add(Box.createVerticalStrut(5));
        name.add(Ui.label("Chọn món và quản lý đơn hàng tại đây", 12, false, Ui.MUTED));
        h.add(name, BorderLayout.WEST);
        JPanel search = Ui.panel(new BorderLayout(0, 5));
        search.setPreferredSize(new Dimension(230, 54));
        search.add(Ui.label("TÌM MÓN", 10, true, Ui.MUTED), BorderLayout.NORTH);
        searchField.setToolTipText("Nhập tên món để lọc thực đơn");
        search.add(searchField, BorderLayout.CENTER);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                if ("MENU".equals(page))
                    showPage();
            }

            public void removeUpdate(DocumentEvent e) {
                if ("MENU".equals(page))
                    showPage();
            }

            public void changedUpdate(DocumentEvent e) {
            }
        });
        h.add(search, BorderLayout.EAST);
        return h;
    }

    private void showPage() {
        contentHost.removeAll();
        switch (page) {
            case "ORDERS" -> contentHost.add(ordersPage(), BorderLayout.CENTER);
            case "REPORT" -> contentHost.add(reportPage(), BorderLayout.CENTER);
            default -> contentHost.add(menuPage(), BorderLayout.CENTER);
        }
        renderCart();
        contentHost.revalidate();
        contentHost.repaint();
    }

    private JPanel menuPage() {
        JPanel root = Ui.panel(new BorderLayout(0, 14));
        JPanel cats = Ui.horizontal(10);
        for (String c : Catalog.CATEGORIES) {
            JButton b = new Ui.FlatButton(c,
                    c.equals(category) ? Ui.BLUE : Ui.WHITE,
                    c.equals(category) ? Ui.WHITE : Ui.DARK);
            b.addActionListener(e -> {
                category = c;
                showPage();
            });
            cats.add(b);
        }
        root.add(cats, BorderLayout.NORTH);
        JPanel grid = Ui.panel(new GridLayout(0, 3, 14, 14));
        String query = searchField.getText().trim().toLowerCase();
        int count = 0;
        for (Data.Product product : Catalog.PRODUCTS) {
            if (!category.equals("Tất cả") && !product.category().equals(category))
                continue;
            if (!product.name().toLowerCase().contains(query))
                continue;
            grid.add(productCard(product));
            count++;
        }
        if (count == 0)
            grid.add(Ui.label("Không tìm thấy món phù hợp.", 15, false, Ui.MUTED));
        JPanel top = Ui.panel(new BorderLayout());
        Ui.pad(top, 5, 2, 24, 5);
        top.add(grid, BorderLayout.NORTH);
        root.add(Ui.scroll(top), BorderLayout.CENTER);
        return root;
    }

    private JPanel productCard(Data.Product product) {
        JPanel card = Ui.card(new BorderLayout(0, 8));
        card.setPreferredSize(new Dimension(170, 263));
        Ui.pad(card, 8, 8, 12, 8);
        Ui.Photo photo = new Ui.Photo(product.image());
        photo.setPreferredSize(new Dimension(170, 123));
        card.add(photo, BorderLayout.NORTH);
        JPanel details = Ui.panel(new BorderLayout(0, 6));
        JPanel title = Ui.panel(new BorderLayout());
        title.add(Ui.label(product.name(), 15, true, Ui.DARK), BorderLayout.NORTH);
        title.add(Ui.label(product.category(), 11, false, Ui.MUTED), BorderLayout.SOUTH);
        details.add(title, BorderLayout.NORTH);
        JPanel foot = Ui.panel(new BorderLayout());
        int canMake = CafeService.available(state, product);
        int discount = CafeService.discount(state, product.id(), LocalDate.now());
        JPanel info = Ui.vertical(0);
        info.add(Ui.label(Ui.money(product.price()), 14, true, Ui.DARK));
        info.add(Box.createVerticalStrut(3));
        info.add(Ui.label(
                canMake == 0 ? "HẾT MÓN" : discount > 0 ? "Ưu đãi −" + discount + "%" : "Còn " + canMake + " suất",
                11, discount > 0 || canMake == 0, canMake == 0 ? Ui.RED : discount > 0 ? Ui.GREEN : Ui.MUTED));
        foot.add(info, BorderLayout.WEST);
        JButton plus = Ui.button("+", true, e -> addCart(product));
        plus.setEnabled(canMake > cart.getOrDefault(product.id(), 0));
        foot.add(plus, BorderLayout.EAST);
        details.add(foot, BorderLayout.SOUTH);
        card.add(details, BorderLayout.CENTER);
        return card;
    }

    private void addCart(Data.Product p) {
        int next = cart.getOrDefault(p.id(), 0) + 1;
        if (next > CafeService.available(state, p)) {
            Ui.message(this, "Không đủ nguyên liệu để thêm " + p.name());
            return;
        }
        cart.put(p.id(), next);
        showPage();
    }

    private void renderCart() {
        if (customerField != null)
            customer = customerField.getText();
        if (paymentBox != null)
            paymentIndex = paymentBox.getSelectedIndex();
        if (servingBox != null)
            servingIndex = servingBox.getSelectedIndex();
        cartHost.removeAll();
        JPanel panel = Ui.card(new BorderLayout(0, 12));
        panel.setPreferredSize(new Dimension(307, 0));
        Ui.pad(panel, 23, 16, 17, 16);
        JPanel top = Ui.vertical(0);
        top.add(Ui.label("Đơn hàng mới", 22, true, Ui.DARK));
        top.add(Box.createVerticalStrut(5));
        top.add(Ui.label("Xác nhận nhận tiền trong Đơn hàng", 11, false, Ui.MUTED));
        top.add(Box.createVerticalStrut(19));
        customerField = Ui.input(customer);
        top.add(Ui.label("Tên khách (không bắt buộc)", 11, false, Ui.MUTED));
        top.add(Box.createVerticalStrut(6));
        top.add(customerField);
        top.add(Box.createVerticalStrut(13));
        JPanel choices = Ui.panel(new GridLayout(1, 2, 8, 0));
        servingBox = new JComboBox<>(new String[] { "Tại chỗ", "Mang đi" });
        servingBox.setSelectedIndex(servingIndex);
        paymentBox = new JComboBox<>(new String[] { "Tiền mặt", "QR" });
        paymentBox.setSelectedIndex(paymentIndex);
        choices.add(servingBox);
        choices.add(paymentBox);
        top.add(choices);
        panel.add(top, BorderLayout.NORTH);

        JPanel rows = Ui.vertical(0);
        if (cart.isEmpty()) {
            rows.add(Box.createVerticalStrut(30));
            rows.add(Ui.label("Giỏ hàng đang trống", 14, false, Ui.MUTED));
            rows.add(Box.createVerticalStrut(8));
            rows.add(Ui.label("Chọn dấu + để thêm món", 12, false, Ui.MUTED));
        }
        for (var entry : cart.entrySet()) {
            Data.Product p = Catalog.find(entry.getKey());
            rows.add(cartRow(p, entry.getValue()));
            rows.add(Box.createVerticalStrut(10));
        }
        JScrollPane scroll = Ui.scroll(rows);
        scroll.getViewport().setBackground(Ui.WHITE);
        panel.add(scroll, BorderLayout.CENTER);

        long subtotal = 0, discount = 0;
        for (var entry : cart.entrySet()) {
            Data.Product p = Catalog.find(entry.getKey());
            long value = p.price() * entry.getValue();
            subtotal += value;
            discount += Math.round(value * CafeService.discount(state, p.id(), LocalDate.now()) / 100.0);
        }
        JPanel foot = Ui.vertical(0);
        JPanel totals = Ui.panel(new GridLayout(3, 2, 4, 9));
        totals.add(Ui.label("Tạm tính", 12, false, Ui.MUTED));
        totals.add(Ui.label(Ui.money(subtotal), 12, true, Ui.DARK));
        totals.add(Ui.label("Giảm giá", 12, false, Ui.MUTED));
        totals.add(Ui.label("−" + Ui.money(discount), 12, true, Ui.GREEN));
        totals.add(Ui.label("THÀNH TIỀN", 13, true, Ui.DARK));
        totals.add(Ui.label(Ui.money(subtotal - discount), 17, true, Ui.BLUE));
        foot.add(totals);
        foot.add(Box.createVerticalStrut(16));
        JButton order = Ui.button("Đặt hàng", true, e -> checkout());
        order.setAlignmentX(Component.LEFT_ALIGNMENT);
        order.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        order.setEnabled(!cart.isEmpty());
        foot.add(order);
        foot.add(Box.createVerticalStrut(9));
        foot.add(Ui.label("QR được ghi nhận, không tự thu tiền.",
                10, false, Ui.MUTED));
        panel.add(foot, BorderLayout.SOUTH);
        cartHost.add(panel, BorderLayout.CENTER);
        cartHost.revalidate();
        cartHost.repaint();
    }

    private JPanel cartRow(Data.Product p, int qty) {
        JPanel row = Ui.card(new BorderLayout(0, 9));
        row.setBackground(Ui.WHITE);
        Ui.pad(row, 10, 8, 10, 8);
        JPanel top = Ui.panel(new BorderLayout(6, 4));
        top.add(Ui.label(p.name(), 13, true, Ui.DARK), BorderLayout.WEST);
        top.add(Ui.label(Ui.money(p.price() * qty), 12, true, Ui.DARK), BorderLayout.EAST);
        row.add(top, BorderLayout.NORTH);
        JPanel count = Ui.horizontal(6);
        JButton minus = Ui.light("−", e -> {
            if (qty == 1)
                cart.remove(p.id());
            else
                cart.put(p.id(), qty - 1);
            showPage();
        });
        JButton plus = Ui.light("+", e -> addCart(p));
        plus.setEnabled(qty < CafeService.available(state, p));
        count.add(minus);
        count.add(Ui.label("  " + qty + "  ", 13, true, Ui.DARK));
        count.add(plus);
        row.add(count, BorderLayout.SOUTH);
        return row;
    }

    private void checkout() {
        try {
            Data.Order newOrder = service.createOrder(cart, customerField.getText(),
                    paymentBox.getSelectedIndex() == 0 ? Data.Payment.CASH : Data.Payment.QR,
                    servingBox.getSelectedIndex() == 0 ? Data.Serving.DINE_IN : Data.Serving.TAKE_AWAY);
            cart.clear();
            state = service.snapshot();
            page = "ORDERS";
            showPage();
            Ui.message(this, "Đã tạo đơn #" + newOrder.id + " (ĐANG XỬ LÝ).\n"
                    + "Vào Đơn hàng → Xác nhận thanh toán khi đã nhận tiền.");
        } catch (Exception ex) {
            Ui.error(this, ex);
            refresh();
        }
    }

    private JPanel ordersPage() {
        JPanel body = Ui.vertical(0);
        Ui.pad(body, 8, 3, 18, 3);
        body.add(Ui.label("Lịch sử và xử lý đơn hàng", 23, true, Ui.DARK));
        body.add(Box.createVerticalStrut(6));
        body.add(Ui.label("Chỉ đơn ĐÃ THANH TOÁN mới được tính doanh thu.", 12, false, Ui.MUTED));
        body.add(Box.createVerticalStrut(18));
        if (state.orders.isEmpty())
            body.add(Ui.label("Chưa có đơn nào.", 14, false, Ui.MUTED));
        for (int i = state.orders.size() - 1; i >= 0; i--) {
            body.add(orderCard(state.orders.get(i)));
            body.add(Box.createVerticalStrut(12));
        }
        JPanel wrapper = Ui.panel(new BorderLayout());
        wrapper.add(body, BorderLayout.NORTH);
        JPanel scrolled = Ui.panel(new BorderLayout());
        scrolled.add(Ui.scroll(wrapper), BorderLayout.CENTER);
        return scrolled;
    }

    private JPanel orderCard(Data.Order o) {
        JPanel card = Ui.card(new BorderLayout(0, 12));
        Ui.pad(card, 18, 20, 16, 20);
        JPanel header = Ui.panel(new BorderLayout());
        header.add(Ui.label("#" + o.id + "  ·  " + o.customer, 16, true, Ui.DARK), BorderLayout.WEST);
        header.add(Ui.label(Ui.status(o.status), 13, true,
                o.status == Data.Status.PAID ? Ui.GREEN : o.status == Data.Status.CANCELLED ? Ui.RED : Ui.BLUE),
                BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);
        StringBuilder names = new StringBuilder();
        for (Data.Line line : o.lines) {
            if (!names.isEmpty())
                names.append("  •  ");
            names.append(line.name()).append(" x").append(line.quantity());
        }
        JPanel description = Ui.vertical(0);
        description.add(Ui.label(names.toString(), 12, false, Ui.DARK));
        description.add(Box.createVerticalStrut(5));
        description.add(Ui.label(o.createdAt.format(Ui.DATE_TIME) + "  |  " +
                Ui.serving(o.serving) + "  |  " + Ui.payment(o.payment), 11, false, Ui.MUTED));
        card.add(description, BorderLayout.CENTER);
        JPanel bottom = Ui.panel(new BorderLayout());
        bottom.add(Ui.label(Ui.money(o.total()), 18, true, Ui.BLUE), BorderLayout.WEST);
        if (o.status == Data.Status.PENDING) {
            JPanel actions = Ui.horizontal(8);
            actions.add(Ui.light("Hủy đơn", e -> {
                if (JOptionPane.showConfirmDialog(this, "Hủy đơn #" + o.id + " và hoàn nguyên liệu?",
                        "Xác nhận", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
                    perform(() -> service.cancelOrder(o.id));
            }));
            actions.add(Ui.button("Xác nhận thanh toán", true, e -> {
                if (JOptionPane.showConfirmDialog(this,
                        "Xác nhận đã thu " + Ui.money(o.total()) + " qua " + Ui.payment(o.payment) + "?",
                        "Xác nhận thanh toán", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
                    perform(() -> service.completeOrder(o.id));
            }));
            bottom.add(actions, BorderLayout.EAST);
        }
        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    private JPanel reportPage() {
        JPanel pageRoot = Ui.panel(new BorderLayout(0, 18));
        JPanel controls = Ui.horizontal(8);
        JTextField date = Ui.input(reportDay.toString());
        date.setPreferredSize(new Dimension(132, 37));
        controls.add(Ui.label("Ngày (yyyy-MM-dd):", 12, false, Ui.MUTED));
        controls.add(date);
        controls.add(Ui.button("Xem báo cáo", true, e -> {
            try {
                reportDay = LocalDate.parse(date.getText().trim());
                showPage();
            } catch (Exception ex) {
                Ui.message(this, "Ngày phải có định dạng yyyy-MM-dd.");
            }
        }));
        controls.add(Ui.light("Hôm nay", e -> {
            reportDay = LocalDate.now();
            showPage();
        }));
        pageRoot.add(controls, BorderLayout.NORTH);
        JPanel body = Ui.panel(new BorderLayout());
        body.add(Ui.report(state, reportDay), BorderLayout.NORTH);
        pageRoot.add(Ui.scroll(body), BorderLayout.CENTER);
        return pageRoot;
    }

    private void perform(Runnable task) {
        try {
            task.run();
            refresh();
        } catch (Exception ex) {
            Ui.error(this, ex);
            refresh();
        }
    }

    private void refresh() {
        state = service.snapshot();
        showPage();
    }

    private void poll() {
        try {
            Data.State latest = service.snapshot();
            if (latest.revision != state.revision) {
                state = latest;
                showPage();
            }
        } catch (Exception ex) {
            /* Thao tác kế tiếp sẽ hiển thị lỗi. */ }
    }

    public static void main(String[] args) {
        Ui.theme();
        SwingUtilities.invokeLater(() -> new PosApp().setVisible(true));
    }
}
