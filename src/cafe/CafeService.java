package cafe;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Quy tắc nghiệp vụ được dùng chung bởi POS và Admin. */
public final class CafeService {
    private final Store store = new Store();
    public Data.State snapshot() { return store.read(s -> s); }

    public static int available(Data.State s, Data.Product p) {
        int max = Integer.MAX_VALUE;
        for (var entry : p.recipe().entrySet()) {
            int have = s.stock.getOrDefault(entry.getKey(), 0);
            max = Math.min(max, have / entry.getValue());
        }
        return max == Integer.MAX_VALUE ? 0 : max;
    }

    public static int discount(Data.State s, String productId, LocalDate day) {
        return s.promotions.stream()
            .filter(p -> p.productId().equals(productId) && p.date().equals(day))
            .mapToInt(Data.Promotion::percent).max().orElse(0);
    }

    public Data.Order createOrder(Map<String, Integer> cart, String customer,
                                  Data.Payment payment, Data.Serving serving) {
        if (cart.isEmpty()) throw new IllegalArgumentException("Giỏ hàng đang trống.");
        return store.update(s -> {
            // Kiểm tra TỔNG nhu cầu nguyên liệu, tránh vượt kho khi 2 POS cùng đặt.
            Map<String, Integer> required = new LinkedHashMap<>();
            List<Data.Line> lines = new ArrayList<>();
            for (var item : cart.entrySet()) {
                if (item.getValue() <= 0) throw new IllegalArgumentException("Số lượng không hợp lệ.");
                Data.Product p = Catalog.find(item.getKey());
                for (var ingredient : p.recipe().entrySet())
                    required.merge(ingredient.getKey(), ingredient.getValue() * item.getValue(), Integer::sum);
                lines.add(new Data.Line(p.id(), p.name(), item.getValue(), p.price(),
                        discount(s, p.id(), LocalDate.now())));
            }
            for (var entry : required.entrySet())
                if (s.stock.getOrDefault(entry.getKey(), 0) < entry.getValue())
                    throw new IllegalArgumentException("Không đủ nguyên liệu: " + entry.getKey()
                            + ". Hãy giảm số lượng hoặc nhập kho.");
            required.forEach((ingredient, qty) -> s.stock.merge(ingredient, -qty, Integer::sum));
            Data.Order order = new Data.Order(s.nextOrderId++,
                    customer.isBlank() ? "Khách lẻ" : customer.trim(), payment, serving, lines);
            s.orders.add(order);
            return order;
        });
    }

    public void completeOrder(long id) {
        store.update(s -> {
            Data.Order order = locate(s, id);
            if (order.status != Data.Status.PENDING)
                throw new IllegalArgumentException("Chỉ có thể thanh toán đơn đang xử lý.");
            order.status = Data.Status.PAID;
            order.paidAt = LocalDateTime.now();
            return null;
        });
    }

    public void cancelOrder(long id) {
        store.update(s -> {
            Data.Order order = locate(s, id);
            if (order.status != Data.Status.PENDING)
                throw new IllegalArgumentException("Chỉ có thể hủy đơn đang xử lý.");
            for (Data.Line line : order.lines)
                for (var recipe : Catalog.find(line.productId()).recipe().entrySet())
                    s.stock.merge(recipe.getKey(), recipe.getValue() * line.quantity(), Integer::sum);
            order.status = Data.Status.CANCELLED;
            return null;
        });
    }

    public void adjustStock(String ingredient, int delta) {
        if (delta == 0) throw new IllegalArgumentException("Số lượng cần thay đổi phải khác 0.");
        store.update(s -> {
            if (!s.stock.containsKey(ingredient))
                throw new IllegalArgumentException("Nguyên liệu không tồn tại.");
            long next = (long) s.stock.get(ingredient) + delta;
            if (next < 0 || next > Integer.MAX_VALUE)
                throw new IllegalArgumentException("Không thể trừ quá số lượng tồn kho.");
            s.stock.put(ingredient, (int) next);
            return null;
        });
    }

    public void addPromotion(String productId, LocalDate date, int percent) {
        Catalog.find(productId);
        if (percent < 1 || percent > 100) throw new IllegalArgumentException("Giảm giá phải từ 1% đến 100%.");
        store.update(s -> {
            s.promotions.add(new Data.Promotion(s.nextPromotionId++, productId, date, percent));
            return null;
        });
    }

    public void removePromotion(long id) {
        store.update(s -> {
            if (!s.promotions.removeIf(p -> p.id() == id))
                throw new IllegalArgumentException("Không tìm thấy khuyến mãi.");
            return null;
        });
    }

    private static Data.Order locate(Data.State s, long id) {
        return s.orders.stream().filter(o -> o.id == id).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn #" + id));
    }

    public record Report(LocalDate date, long gross, long discount, long revenue,
                         long cash, long qr, int completed, int pending, int cancelled) {}

    public static Report report(Data.State s, LocalDate day) {
        long gross = 0, discount = 0, cash = 0, qr = 0;
        int completed = 0, pending = 0, cancelled = 0;
        for (Data.Order o : s.orders) {
            if (o.status == Data.Status.PAID && o.paidAt != null && o.paidAt.toLocalDate().equals(day)) {
                completed++; gross += o.subtotal(); discount += o.discount();
                if (o.payment == Data.Payment.CASH) cash += o.total();
                else qr += o.total();
            }
            if (o.createdAt.toLocalDate().equals(day)) {
                if (o.status == Data.Status.PENDING) pending++;
                if (o.status == Data.Status.CANCELLED) cancelled++;
            }
        }
        return new Report(day, gross, discount, gross - discount, cash, qr,
                          completed, pending, cancelled);
    }
}
