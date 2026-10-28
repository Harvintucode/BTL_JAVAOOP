package cafe;

import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Map;

/** Kiểm thử nghiệp vụ không cần mở giao diện. */
public final class SmokeTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("cafe.home", Files.createTempDirectory("cotafe-test-").toString());
        CafeService service = new CafeService();
        Data.State seed = service.snapshot();
        check(seed.stock.get("Cà phê (g)") == 650, "Initial stock");
        check(CafeService.available(seed, Catalog.find("americano")) == 36, "Can make");
        service.addPromotion("americano", LocalDate.now(), 20);
        Data.Order pending = service.createOrder(Map.of("americano", 2, "lemon", 1),
                "Khách thử", Data.Payment.QR, Data.Serving.TAKE_AWAY);
        check(pending.status == Data.Status.PENDING, "Pending after create");
        check(pending.total() == 69000, "Discount applied");
        check(service.snapshot().stock.get("Cà phê (g)") == 614, "Stock reserved");
        check(CafeService.report(service.snapshot(), LocalDate.now()).revenue() == 0,
                "Unpaid not revenue");
        service.completeOrder(pending.id);
        CafeService.Report report = CafeService.report(service.snapshot(), LocalDate.now());
        check(report.completed() == 1 && report.revenue() == 69000 && report.qr() == 69000,
                "Revenue after pay");
        Data.Order cancelled = service.createOrder(Map.of("tuna", 1), "",
                Data.Payment.CASH, Data.Serving.DINE_IN);
        int bread = service.snapshot().stock.get("Bánh mì (cái)");
        service.cancelOrder(cancelled.id);
        check(service.snapshot().stock.get("Bánh mì (cái)") == bread + 1,
                "Restock cancelled order");
        check(CafeService.report(service.snapshot(), LocalDate.now()).revenue() == 69000,
                "Cancelled order not revenue");
        service.adjustStock("Chanh (quả)", 8);
        check(service.snapshot().stock.get("Chanh (quả)") == 37, "Add stock");
        boolean rejected = false;
        try { service.adjustStock("Chanh (quả)", -10000); }
        catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, "Cannot make negative stock");
        rejected = false;
        try { service.createOrder(Map.of("americano", 999), "",
                    Data.Payment.CASH, Data.Serving.DINE_IN); }
        catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, "Cannot oversell stock");
        rejected = false;
        try { service.cancelOrder(pending.id); }
        catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, "Paid order cannot cancel");
        System.out.println("PASS - 13 business checks: inventory, payment, revenue, cancellation, promotions.");
    }
}
