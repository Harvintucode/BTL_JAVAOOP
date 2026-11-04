package cafe;

import java.time.LocalDate;
import java.util.Map;

/**
 * Các thao tác nghiệp vụ mà POS và Admin cần. Có hai cách triển khai:
 * {@link CafeService} (đọc/ghi file trên cùng máy) và {@link RemoteCafeApi} (gọi máy chủ qua LAN).
 */
public interface CafeApi {
    Data.State snapshot();

    Data.Order createOrder(Map<String, Integer> cart, String customer,
                           Data.Payment payment, Data.Serving serving);

    void completeOrder(long id);

    void cancelOrder(long id);

    void adjustStock(String ingredient, int delta);

    void addPromotion(String productId, LocalDate date, int percent);

    void removePromotion(long id);

    /** Giải phóng tài nguyên (ví dụ đăng xuất khỏi máy chủ). Mặc định không làm gì. */
    default void close() {}
}
