package cafe;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Path;
import java.util.Collections;

/** Chạy máy chủ LAN: nơi duy nhất giữ cafe.dat/users.dat khi dùng nhiều máy POS/Admin. */
public final class ServerMain {
    private ServerMain() {}

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 && !args[0].isBlank()
                ? Integer.parseInt(args[0].trim())
                : Integer.getInteger("cafe.port", RemoteCafeApi.DEFAULT_PORT);
        CafeServer server = new CafeServer(port);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        Path home = Path.of(System.getProperty("cafe.home", ".")).toAbsolutePath().normalize();
        System.out.println("=== COTAFE SERVER dang chay (cong " + server.port() + ") ===");
        System.out.println("Du lieu: " + home);
        System.out.println("Tren may POS/Admin, nhap mot trong cac dia chi sau:");
        boolean found = false;
        for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) continue;
            for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                if (address instanceof Inet4Address && !address.isLinkLocalAddress()) {
                    System.out.println("  " + address.getHostAddress() + ":" + server.port()
                            + "   (" + nic.getDisplayName() + ")");
                    found = true;
                }
            }
        }
        if (!found) System.out.println("  (khong tim thay dia chi LAN - kiem tra ket noi mang)");
        System.out.println("Neu Windows hoi tuong lua, chon 'Allow' cho Private networks.");
        System.out.println("Dong cua so nay (hoac Ctrl+C) de dung may chu.");
        Thread.currentThread().join();
    }
}
