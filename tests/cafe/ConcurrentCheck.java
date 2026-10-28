package cafe;

/** Kiểm tra kết quả sau khi chạy 2 ConcurrentWorker cùng thư mục dữ liệu. */
public final class ConcurrentCheck {
    public static void main(String[] args) {
        int expected = 900 + Integer.parseInt(args[0]) * 2;
        int actual = new CafeService().snapshot().stock.get("Siro (ml)");
        if (actual != expected)
            throw new AssertionError("File concurrency lost updates: " + actual + " != " + expected);
        System.out.println("PASS - two processes, shared .dat, Siro (ml) = " + actual);
    }
}
