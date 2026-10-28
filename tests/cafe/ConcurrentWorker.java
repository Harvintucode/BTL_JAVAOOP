package cafe;

/** Chạy nhiều JVM để kiểm tra transaction file-lock không mất cập nhật. */
public final class ConcurrentWorker {
    public static void main(String[] args) {
        CafeService s = new CafeService();
        for (int i = 0; i < Integer.parseInt(args[0]); i++)
            s.adjustStock("Siro (ml)", 1);
        System.out.println("worker finished");
    }
}
