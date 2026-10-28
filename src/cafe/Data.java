package cafe;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Các kiểu dữ liệu được lưu trong cafe.dat. */
public final class Data {
    private Data() {}

    public enum Status { PENDING, PAID, CANCELLED }
    public enum Payment { CASH, QR }
    public enum Serving { DINE_IN, TAKE_AWAY }

    public record Product(String id, String name, String category, long price,
                          String image, Map<String, Integer> recipe) {}

    public record Line(String productId, String name, int quantity,
                       long unitPrice, int discountPercent) implements Serializable {
        private static final long serialVersionUID = 1L;
        public long subtotal() { return unitPrice * quantity; }
        public long discount() { return Math.round(subtotal() * discountPercent / 100.0); }
        public long total() { return subtotal() - discount(); }
    }

    public record Promotion(long id, String productId, LocalDate date,
                            int percent) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    public static final class Order implements Serializable {
        private static final long serialVersionUID = 1L;
        public final long id;
        public final String customer;
        public final LocalDateTime createdAt;
        public LocalDateTime paidAt;
        public Status status = Status.PENDING;
        public final Payment payment;
        public final Serving serving;
        public final List<Line> lines;

        public Order(long id, String customer, Payment payment, Serving serving, List<Line> lines) {
            this.id = id;
            this.customer = customer;
            this.payment = payment;
            this.serving = serving;
            this.lines = new ArrayList<>(lines);
            this.createdAt = LocalDateTime.now();
        }
        public long subtotal() { return lines.stream().mapToLong(Line::subtotal).sum(); }
        public long discount() { return lines.stream().mapToLong(Line::discount).sum(); }
        public long total() { return lines.stream().mapToLong(Line::total).sum(); }
    }

    public static final class State implements Serializable {
        private static final long serialVersionUID = 1L;
        public final Map<String, Integer> stock = new LinkedHashMap<>();
        public final List<Order> orders = new ArrayList<>();
        public final List<Promotion> promotions = new ArrayList<>();
        public long nextOrderId = 1001;
        public long nextPromotionId = 1;
        public long revision = 1;

        public State() {
            stock.put("Cà phê (g)", 650);
            stock.put("Sữa (ml)", 1500);
            stock.put("Trà (g)", 500);
            stock.put("Bánh mì (cái)", 20);
            stock.put("Cá ngừ (g)", 1400);
            stock.put("Rau (g)", 1200);
            stock.put("Matcha (g)", 180);
            stock.put("Thịt viên (viên)", 60);
            stock.put("Chanh (quả)", 30);
            stock.put("Siro (ml)", 900);
            stock.put("Soda (ml)", 2500);
            stock.put("Đá (g)", 4000);
        }
    }
}
