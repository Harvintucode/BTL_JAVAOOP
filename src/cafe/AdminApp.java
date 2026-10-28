package cafe;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Ứng dụng quản trị: nhập/xuất kho, khuyến mãi, báo cáo. */
public final class AdminApp extends JFrame {
    private final CafeService service = new CafeService();
    private Data.State state = service.snapshot();
    private final JPanel host = Ui.panel(new BorderLayout());
    private String page = "STOCK";
    private LocalDate reportDay = LocalDate.now();

    public AdminApp() {
        super("CAFE • Admin");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1040, 780);
        setMinimumSize(new Dimension(850, 620));
        setLocationByPlatform(true);
        getContentPane().setBackground(Ui.BG);
        setLayout(new BorderLayout(14, 0));
        add(sidebar(), BorderLayout.WEST);
        JPanel main = Ui.panel(new BorderLayout(0, 16));
        Ui.pad(main, 25, 4, 22, 20);
        main.add(Ui.label("CAFE / Bảng điều khiển Admin", 25, true, Ui.DARK), BorderLayout.NORTH);
        main.add(host, BorderLayout.CENTER);
        add(main, BorderLayout.CENTER);
        showPage();
        new Timer(2300, e -> poll()).start();
    }

    private JPanel sidebar() {
        JPanel side = Ui.card(new BorderLayout());
        side.setPreferredSize(new Dimension(192, 0));
        Ui.pad(side, 26, 15, 20, 15);
        JPanel nav = Ui.vertical(0);
        nav.add(Ui.label("CAFE", 23, true, Ui.BLUE));
        nav.add(Box.createVerticalStrut(8));
        nav.add(Ui.label("  TRANG QUẢN TRỊ", 10, true, Ui.MUTED));
        nav.add(Box.createVerticalStrut(38));
        link(nav, "Kho nguyên liệu", "STOCK");
        link(nav, "Khuyến mãi", "PROMOS");
        link(nav, "Doanh thu", "REPORT");
        side.add(nav, BorderLayout.NORTH);
        JPanel foot = Ui.vertical(0);
        foot.add(Ui.label("Dữ liệu liên thông POS", 11, true, Ui.GREEN));
        foot.add(Box.createVerticalStrut(5));
        foot.add(Ui.label("File: cafe.dat", 11, false, Ui.MUTED));
        side.add(foot, BorderLayout.SOUTH);
        return side;
    }

    private void link(JPanel nav, String caption, String target) {
        JButton b = Ui.light(caption, e -> {
            page = target;
            showPage();
        });
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        nav.add(b);
        nav.add(Box.createVerticalStrut(12));
    }

    private void showPage() {
        host.removeAll();
        switch (page) {
            case "PROMOS" -> host.add(promotionsPage(), BorderLayout.CENTER);
            case "REPORT" -> host.add(reportPage(), BorderLayout.CENTER);
            default -> host.add(stockPage(), BorderLayout.CENTER);
        }
        host.revalidate();
        host.repaint();
    }

    private JPanel stockPage() {
        JPanel root = Ui.panel(new BorderLayout(0, 14));
        JPanel head = Ui.vertical(0);
        head.add(Ui.label("Quản lý kho nguyên liệu", 22, true, Ui.DARK));
        head.add(Box.createVerticalStrut(5));
        head.add(Ui.label("Nhập (+) hoặc xuất (−) kho. Không thể xuất quá tồn kho.", 12, false, Ui.MUTED));
        root.add(head, BorderLayout.NORTH);
        JPanel grid = Ui.panel(new GridLayout(0, 2, 13, 13));
        state.stock.forEach((ingredient, amount) -> grid.add(stockCard(ingredient, amount)));
        JPanel wrapper = Ui.panel(new BorderLayout());
        Ui.pad(wrapper, 5, 1, 17, 1);
        wrapper.add(grid, BorderLayout.NORTH);
        root.add(Ui.scroll(wrapper), BorderLayout.CENTER);
        return root;
    }

    private JPanel stockCard(String ingredient, int amount) {
        JPanel tile = Ui.card(new BorderLayout(0, 15));
        Ui.pad(tile, 18, 20, 17, 20);
        JPanel top = Ui.panel(new BorderLayout());
        top.add(Ui.label(ingredient, 14, true, Ui.DARK), BorderLayout.WEST);
        top.add(Ui.label("Tồn: " + amount, 13, true,
                amount <= 5 ? Ui.RED : Ui.GREEN), BorderLayout.EAST);
        tile.add(top, BorderLayout.NORTH);
        JPanel bottom = Ui.horizontal(8);
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(10, 1, 100000, 1));
        spinner.setPreferredSize(new Dimension(92, 36));
        bottom.add(spinner);
        bottom.add(Ui.button("+ Nhập", true, e -> changeStock(ingredient, (Integer) spinner.getValue())));
        bottom.add(Ui.light("− Xuất", e -> changeStock(ingredient, -(Integer) spinner.getValue())));
        tile.add(bottom, BorderLayout.SOUTH);
        return tile;
    }

    private void changeStock(String key, int delta) {
        try {
            service.adjustStock(key, delta);
            refresh();
        } catch (Exception ex) {
            Ui.error(this, ex);
            refresh();
        }
    }

    private JPanel promotionsPage() {
        JPanel root = Ui.panel(new BorderLayout(0, 15));
        JPanel head = Ui.vertical(0);
        head.add(Ui.label("Thiết lập khuyến mãi", 22, true, Ui.DARK));
        head.add(Box.createVerticalStrut(5));
        head.add(Ui.label("Giảm giá cho một món vào đúng ngày chỉ định.", 12, false, Ui.MUTED));
        root.add(head, BorderLayout.NORTH);
        JPanel body = Ui.vertical(0);
        JPanel creator = Ui.card(new BorderLayout(0, 13));
        Ui.pad(creator, 18, 20, 18, 20);
        creator.add(Ui.label("Tạo chương trình giảm giá", 16, true, Ui.DARK), BorderLayout.NORTH);
        JPanel fields = Ui.panel(new GridLayout(2, 3, 10, 7));
        fields.add(Ui.label("Món áp dụng", 12, false, Ui.MUTED));
        fields.add(Ui.label("Ngày (yyyy-MM-dd)", 12, false, Ui.MUTED));
        fields.add(Ui.label("Giảm (%)", 12, false, Ui.MUTED));
        JComboBox<String> products = new JComboBox<>(Catalog.PRODUCTS.stream()
                .map(Data.Product::name).toArray(String[]::new));
        JTextField day = Ui.input(LocalDate.now().toString());
        JSpinner percent = new JSpinner(new SpinnerNumberModel(10, 1, 100, 1));
        fields.add(products);
        fields.add(day);
        fields.add(percent);
        creator.add(fields, BorderLayout.CENTER);
        JButton create = Ui.button("+ Thêm khuyến mãi", true, e -> {
            try {
                service.addPromotion(Catalog.PRODUCTS.get(products.getSelectedIndex()).id(),
                        LocalDate.parse(day.getText().trim()), (Integer) percent.getValue());
                refresh();
            } catch (Exception ex) {
                Ui.error(this, ex);
            }
        });
        JPanel createAlign = Ui.horizontal(0);
        createAlign.add(create);
        creator.add(createAlign, BorderLayout.SOUTH);
        body.add(creator);
        body.add(Box.createVerticalStrut(23));
        body.add(Ui.label("Danh sách khuyến mãi đã tạo", 17, true, Ui.DARK));
        body.add(Box.createVerticalStrut(13));
        if (state.promotions.isEmpty())
            body.add(Ui.label("Chưa có chương trình nào.", 13, false, Ui.MUTED));
        List<Data.Promotion> reversed = new ArrayList<>(state.promotions);
        java.util.Collections.reverse(reversed);
        for (Data.Promotion p : reversed) {
            JPanel tile = Ui.card(new BorderLayout());
            Ui.pad(tile, 14, 16, 14, 16);
            String product = Catalog.find(p.productId()).name();
            JPanel info = Ui.vertical(0);
            info.add(Ui.label(product + "  •  Giảm " + p.percent() + "%", 14, true, Ui.DARK));
            info.add(Box.createVerticalStrut(4));
            info.add(Ui.label("Ngày " + p.date() + "  |  " +
                    (p.date().isBefore(LocalDate.now()) ? "Đã qua"
                            : p.date().isEqual(LocalDate.now()) ? "Đang áp dụng" : "Sắp tới"),
                    11, false, Ui.MUTED));
            tile.add(info, BorderLayout.WEST);
            tile.add(Ui.light("Xóa", e -> {
                try {
                    service.removePromotion(p.id());
                    refresh();
                } catch (Exception ex) {
                    Ui.error(this, ex);
                }
            }), BorderLayout.EAST);
            body.add(tile);
            body.add(Box.createVerticalStrut(10));
        }
        JPanel wrapper = Ui.panel(new BorderLayout());
        Ui.pad(wrapper, 3, 1, 20, 1);
        wrapper.add(body, BorderLayout.NORTH);
        root.add(Ui.scroll(wrapper), BorderLayout.CENTER);
        return root;
    }

    private JPanel reportPage() {
        JPanel root = Ui.panel(new BorderLayout(0, 18));
        JPanel controls = Ui.horizontal(8);
        JTextField date = Ui.input(reportDay.toString());
        date.setPreferredSize(new Dimension(132, 37));
        controls.add(Ui.label("Ngày (yyyy-MM-dd):", 12, false, Ui.MUTED));
        controls.add(date);
        controls.add(Ui.button("Xem", true, e -> {
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
        root.add(controls, BorderLayout.NORTH);
        JPanel body = Ui.panel(new BorderLayout());
        body.add(Ui.report(state, reportDay), BorderLayout.NORTH);
        root.add(Ui.scroll(body), BorderLayout.CENTER);
        return root;
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
            /* Hành động tiếp theo sẽ báo lỗi. */ }
    }

    public static void main(String[] args) {
        Ui.theme();
        SwingUtilities.invokeLater(() -> new AdminApp().setVisible(true));
    }
}
