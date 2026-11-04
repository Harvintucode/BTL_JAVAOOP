package cafe;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Chuyển đổi dữ liệu nghiệp vụ <-> JSON dùng giữa máy chủ LAN và các máy POS/Admin. */
final class Wire {
    private Wire() {}

    // ---------- Data -> JSON ----------

    static Map<String, Object> state(Data.State s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("revision", s.revision);
        m.put("nextOrderId", s.nextOrderId);
        m.put("nextPromotionId", s.nextPromotionId);
        m.put("stock", new LinkedHashMap<>(s.stock));
        List<Object> orders = new ArrayList<>();
        for (Data.Order o : s.orders) orders.add(order(o));
        m.put("orders", orders);
        List<Object> promotions = new ArrayList<>();
        for (Data.Promotion p : s.promotions) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("id", p.id());
            pm.put("productId", p.productId());
            pm.put("date", p.date().toString());
            pm.put("percent", p.percent());
            promotions.add(pm);
        }
        m.put("promotions", promotions);
        return m;
    }

    static Map<String, Object> order(Data.Order o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.id);
        m.put("customer", o.customer);
        m.put("createdAt", o.createdAt.toString());
        m.put("paidAt", o.paidAt == null ? null : o.paidAt.toString());
        m.put("status", o.status.name());
        m.put("payment", o.payment.name());
        m.put("serving", o.serving.name());
        List<Object> lines = new ArrayList<>();
        for (Data.Line l : o.lines) {
            Map<String, Object> lm = new LinkedHashMap<>();
            lm.put("productId", l.productId());
            lm.put("name", l.name());
            lm.put("quantity", l.quantity());
            lm.put("unitPrice", l.unitPrice());
            lm.put("discountPercent", l.discountPercent());
            lines.add(lm);
        }
        m.put("lines", lines);
        // Các trường tính sẵn chỉ để client khác (ví dụ web) tiện dùng; Java client tự tính lại.
        m.put("subtotal", o.subtotal());
        m.put("discount", o.discount());
        m.put("total", o.total());
        return m;
    }

    // ---------- JSON -> Data ----------

    static Data.State state(Map<String, Object> m) {
        Data.State s = new Data.State();
        s.stock.clear();
        for (Map.Entry<String, Object> e : obj(m.get("stock")).entrySet())
            s.stock.put(e.getKey(), integer(e.getValue()));
        for (Object o : list(m.get("orders"))) s.orders.add(order(obj(o)));
        for (Object o : list(m.get("promotions"))) {
            Map<String, Object> p = obj(o);
            s.promotions.add(new Data.Promotion(num(p, "id"), str(p, "productId"),
                    LocalDate.parse(str(p, "date")), integer(p.get("percent"))));
        }
        s.nextOrderId = num(m, "nextOrderId");
        s.nextPromotionId = num(m, "nextPromotionId");
        s.revision = num(m, "revision");
        return s;
    }

    static Data.Order order(Map<String, Object> m) {
        List<Data.Line> lines = new ArrayList<>();
        for (Object o : list(m.get("lines"))) {
            Map<String, Object> l = obj(o);
            lines.add(new Data.Line(str(l, "productId"), str(l, "name"), integer(l.get("quantity")),
                    num(l, "unitPrice"), integer(l.get("discountPercent"))));
        }
        Data.Order order = new Data.Order(num(m, "id"), str(m, "customer"),
                LocalDateTime.parse(str(m, "createdAt")),
                enumOf(Data.Payment.class, str(m, "payment")),
                enumOf(Data.Serving.class, str(m, "serving")), lines);
        order.status = enumOf(Data.Status.class, str(m, "status"));
        Object paidAt = m.get("paidAt");
        order.paidAt = paidAt == null ? null : LocalDateTime.parse(asString(paidAt, "paidAt"));
        return order;
    }

    // ---------- Trợ giúp đọc JSON có kiểm tra kiểu ----------

    @SuppressWarnings("unchecked")
    static Map<String, Object> obj(Object v) {
        if (v instanceof Map<?, ?>) return (Map<String, Object>) v;
        throw new IllegalArgumentException("Dữ liệu không hợp lệ: cần một đối tượng.");
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Object v) {
        if (v instanceof List<?>) return (List<Object>) v;
        throw new IllegalArgumentException("Dữ liệu không hợp lệ: cần một danh sách.");
    }

    static String str(Map<String, Object> m, String key) {
        return asString(m.get(key), key);
    }

    /** Chuỗi tùy chọn: thiếu hoặc null thì trả về chuỗi rỗng. */
    static String optStr(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : asString(v, key);
    }

    private static String asString(Object v, String key) {
        if (v instanceof String s) return s;
        throw new IllegalArgumentException("Thiếu hoặc sai kiểu trường \"" + key + "\".");
    }

    static long num(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Long l) return l;
        throw new IllegalArgumentException("Thiếu hoặc sai kiểu trường số \"" + key + "\".");
    }

    static int integer(Object v) {
        if (v instanceof Long l && l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return l.intValue();
        throw new IllegalArgumentException("Giá trị số nguyên không hợp lệ.");
    }

    static <E extends Enum<E>> E enumOf(Class<E> type, String name) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Giá trị không hợp lệ: " + name);
        }
    }
}
