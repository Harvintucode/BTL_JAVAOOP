package cafe;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Kiểm thử JSON, máy chủ LAN và client từ xa (không cần giao diện). */
public final class ServerTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        checks++;
    }

    private static boolean throwsType(Runnable action, Class<? extends RuntimeException> type) {
        try {
            action.run();
            return false;
        } catch (RuntimeException ex) {
            return type.isInstance(ex);
        }
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("cafe.home", Files.createTempDirectory("cotafe-server-test-").toString());
        testJson();
        CafeServer server = new CafeServer(0);
        server.start();
        try {
            testServer("127.0.0.1:" + server.port());
        } finally {
            server.stop();
        }
        System.out.println("PASS - " + checks + " checks: JSON, dang nhap qua LAN, phan quyen, nghiep vu, dong thoi.");
    }

    private static void testJson() {
        String tricky = "Cà phê (g) \"x\" \\ \n\t \u0001 / 😀";
        Map<String, Object> source = new java.util.LinkedHashMap<>();
        source.put("s", tricky);
        source.put("n", 42L);
        source.put("neg", -7L);
        source.put("f", 2.5);
        source.put("t", true);
        source.put("nil", null);
        source.put("list", List.of(1L, "a", List.of()));
        source.put("obj", Map.of("k", "v"));
        Map<String, Object> back = Json.parseObject(Json.write(source));
        check(tricky.equals(back.get("s")), "JSON round trip giu nguyen chuoi Unicode/escape");
        check(Long.valueOf(42).equals(back.get("n")) && Long.valueOf(-7).equals(back.get("neg")), "JSON so nguyen");
        check(Double.valueOf(2.5).equals(back.get("f")), "JSON so thuc");
        check(Boolean.TRUE.equals(back.get("t")) && back.containsKey("nil") && back.get("nil") == null, "JSON bool/null");
        check(Json.parse("  [1, 2.5e2, \"\\u00e0\" ]  ").equals(List.of(1L, 250.0, "à")), "JSON parse mang va \\u");
        for (String bad : new String[] {"", "{", "[1,]", "{\"a\":}", "tru", "{\"a\" 1}", "\"abc", "[1] x",
                "\"\\q\"", "\"\\u12G4\"", "01x", "[" .repeat(100)}) {
            check(throwsType(() -> Json.parse(bad), IllegalArgumentException.class), "JSON tu choi: " + bad);
        }
    }

    private static void testServer(String address) throws Exception {
        // Kết nối tới địa chỉ sai / chưa đăng nhập
        check(throwsType(() -> RemoteCafeApi.login("127.0.0.1:1", "staff", "Staff@123".toCharArray(), Role.STAFF),
                IllegalStateException.class), "Khong ket noi duoc -> IllegalStateException");
        check(throwsType(() -> RemoteCafeApi.baseUri("http://"), IllegalArgumentException.class), "Dia chi sai bi tu choi");
        check(RemoteCafeApi.baseUri("192.168.1.5").toString().equals("http://192.168.1.5:8080"), "Cong mac dinh 8080");
        check(RemoteCafeApi.baseUri("10.0.0.2:9000").toString().equals("http://10.0.0.2:9000"), "Cong tuy chinh");

        HttpClient raw = HttpClient.newHttpClient();
        HttpResponse<String> ping = raw.send(HttpRequest.newBuilder(URI.create("http://" + address + "/api/ping")).build(),
                HttpResponse.BodyHandlers.ofString());
        check(ping.statusCode() == 200 && ping.body().contains("cotafe"), "ping khong can dang nhap");
        HttpResponse<String> anon = raw.send(HttpRequest.newBuilder(URI.create("http://" + address + "/api/state")).build(),
                HttpResponse.BodyHandlers.ofString());
        check(anon.statusCode() == 401, "Khong co token -> 401");
        HttpResponse<String> forged = raw.send(HttpRequest.newBuilder(URI.create("http://" + address + "/api/state"))
                .header("Authorization", "Bearer abc").build(), HttpResponse.BodyHandlers.ofString());
        check(forged.statusCode() == 401, "Token gia -> 401");
        HttpResponse<String> garbage = raw.send(HttpRequest.newBuilder(URI.create("http://" + address + "/api/login"))
                .POST(HttpRequest.BodyPublishers.ofString("{not json")).build(), HttpResponse.BodyHandlers.ofString());
        check(garbage.statusCode() == 400, "JSON hong -> 400");

        // Đăng nhập
        check(throwsType(() -> RemoteCafeApi.login(address, "staff", "sai".toCharArray(), Role.STAFF),
                IllegalArgumentException.class), "Sai mat khau bi tu choi");
        check(throwsType(() -> RemoteCafeApi.login(address, "staff", "Staff@123".toCharArray(), Role.ADMIN),
                IllegalArgumentException.class), "Staff khong dang nhap duoc Admin");
        RemoteCafeApi staff = RemoteCafeApi.login(address, "staff", "Staff@123".toCharArray(), Role.STAFF);
        RemoteCafeApi admin = RemoteCafeApi.login(address, "admin", "Admin@123".toCharArray(), Role.ADMIN);
        check(staff.user().role() == Role.STAFF && admin.user().role() == Role.ADMIN, "Vai tro tu may chu");

        // Trạng thái và bộ nhớ đệm theo revision
        Data.State seed = staff.snapshot();
        check(seed.stock.get("Cà phê (g)") == 650 && seed.stock.size() == 12, "Ton kho ban dau qua mang");
        check(staff.snapshot() == seed, "Khong doi revision -> dung ban cache");

        // Phân quyền
        check(throwsType(() -> staff.adjustStock("Chanh (quả)", 1), IllegalArgumentException.class), "Staff khong chinh kho");
        check(throwsType(() -> staff.addPromotion("americano", LocalDate.now(), 10), IllegalArgumentException.class),
                "Staff khong tao khuyen mai");

        // Nghiệp vụ như SmokeTest nhưng đi qua mạng
        admin.addPromotion("americano", LocalDate.now(), 20);
        Data.Order pending = staff.createOrder(Map.of("americano", 2, "lemon", 1), "Khách thử",
                Data.Payment.QR, Data.Serving.TAKE_AWAY);
        check(pending.status == Data.Status.PENDING && pending.total() == 69000, "Don moi + giam gia 20%");
        check(pending.customer.equals("Khách thử"), "Ten khach tieng Viet qua JSON");
        Data.State afterOrder = staff.snapshot();
        check(afterOrder != seed && afterOrder.stock.get("Cà phê (g)") == 614, "Ton kho bi tru, state moi");
        check(afterOrder.orders.size() == 1 && afterOrder.promotions.size() == 1, "Don va khuyen mai trong state");
        check(CafeService.report(afterOrder, LocalDate.now()).revenue() == 0, "Don chua thanh toan chua tinh doanh thu");
        admin.snapshot(); // Admin cũng đọc được trạng thái
        staff.completeOrder(pending.id);
        Data.State paid = admin.snapshot();
        check(paid.orders.get(0).status == Data.Status.PAID && paid.orders.get(0).paidAt != null, "Thanh toan hien o Admin");
        check(CafeService.report(paid, LocalDate.now()).revenue() == 69000, "Doanh thu sau thanh toan");
        check(throwsType(() -> staff.cancelOrder(pending.id), IllegalArgumentException.class), "Don da tra tien khong huy duoc");
        check(throwsType(() -> staff.createOrder(Map.of("americano", 999), "", Data.Payment.CASH, Data.Serving.DINE_IN),
                IllegalArgumentException.class), "Khong ban vuot ton kho");
        check(throwsType(() -> staff.createOrder(Map.of("khong-co", 1), "", Data.Payment.CASH, Data.Serving.DINE_IN),
                IllegalArgumentException.class), "Mon khong ton tai bi tu choi");
        check(throwsType(() -> staff.createOrder(Map.of(), "", Data.Payment.CASH, Data.Serving.DINE_IN),
                IllegalArgumentException.class), "Gio hang rong bi tu choi");
        admin.adjustStock("Chanh (quả)", 8);
        check(admin.snapshot().stock.get("Chanh (quả)") == 37, "Admin nhap kho");
        check(throwsType(() -> admin.adjustStock("Chanh (quả)", -10000), IllegalArgumentException.class), "Khong xuat qua ton");
        long promoId = admin.snapshot().promotions.get(0).id();
        admin.removePromotion(promoId);
        check(admin.snapshot().promotions.isEmpty(), "Xoa khuyen mai");

        // Dữ liệu qua mạng giống hệt đọc trực tiếp từ file
        Data.State local = new CafeService().snapshot();
        Data.State remote = staff.snapshot();
        check(local.stock.equals(remote.stock) && local.revision == remote.revision
                && local.orders.size() == remote.orders.size(), "Remote == file tren may chu");

        // Hai máy POS cùng đặt đơn một lúc: không hỏng dữ liệu, ID không trùng
        long before = remote.stock.get("Cà phê (g)");
        int orders = remote.orders.size();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<Long>> futures = new ArrayList<>();
        for (int t = 0; t < 6; t++) {
            RemoteCafeApi pos = (t % 2 == 0) ? staff : admin;
            futures.add(pool.submit((Callable<Long>) () -> {
                long last = 0;
                for (int i = 0; i < 5; i++)
                    last = pos.createOrder(Map.of("americano", 1), "", Data.Payment.CASH, Data.Serving.DINE_IN).id;
                return last;
            }));
        }
        for (Future<Long> f : futures) f.get();
        pool.shutdown();
        Data.State end = staff.snapshot();
        check(end.orders.size() == orders + 30, "30 don dong thoi deu duoc ghi");
        check(end.stock.get("Cà phê (g)") == before - 30 * 18, "Ton kho dung sau 30 don dong thoi");
        Set<Long> ids = new HashSet<>();
        end.orders.forEach(o -> ids.add(o.id));
        check(ids.size() == end.orders.size(), "ID don khong trung");

        // Đăng xuất: token không còn hiệu lực
        staff.close();
        Thread.sleep(300);
        check(throwsType(staff::snapshot, IllegalStateException.class), "Sau dang xuat token het hieu luc");
        check(admin.snapshot().orders.size() == end.orders.size(), "Admin van dung duoc");
    }
}
