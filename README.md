# Cotafe — 2 ứng dụng Java chạy song song, dùng chung file `.dat`

Hai ứng dụng **Java Swing thuần (không cần Maven, Gradle, JavaFX hoặc thư viện ngoài)**:

- **POS (nhân viên):** danh sách món, tìm kiếm/lọc, giỏ hàng, đặt món; chọn **tiền mặt / QR**, **tại chỗ / mang đi**; đơn **Đang xử lý → Đã thanh toán**, có nút **Xác nhận thanh toán** hoặc **Hủy đơn**; xem lịch sử và báo cáo doanh thu.
- **Admin:** xem tồn kho, **nhập/xuất nguyên liệu**; thêm/xóa **khuyến mãi theo món + ngày + %**; báo cáo doanh thu giống POS.
- **Đăng nhập:** mỗi app yêu cầu tài khoản phù hợp trước khi mở giao diện; hiển thị người đang đăng nhập và hỗ trợ đăng xuất.

> Giao diện lấy cảm hứng bố cục và hình minh họa món từ ảnh tham khảo do người dùng cung cấp. Không tích hợp cổng thanh toán thực tế: QR là lựa chọn ghi nhận phương thức thanh toán; người dùng cần xác nhận đã nhận tiền.

## Cách chạy trên Windows

1. Cài **JDK 17 hoặc mới hơn**, đặt `JAVA_HOME` trỏ tới thư mục JDK hoặc thêm thư mục `JDK\bin` (có `java`, `javaw`, `javac`) vào `PATH`. Các script ưu tiên JDK hợp lệ trong `JAVA_HOME`, sau đó mới dùng `PATH`.
2. Giải nén toàn bộ project vào cùng **một** thư mục (giữ nguyên `src` và `assets`).
3. **Nhấp đúp `run.bat`**. Script biên dịch và mở **hai cửa sổ đăng nhập** cho POS và Admin. Nhập tài khoản trong bảng bên dưới để mở app tương ứng.
4. Hai app đọc/ghi file **`cafe.dat` nằm cùng cấp với `run.bat`**; file được tạo tự động trong lần đầu chạy. File `cafe.lock` được dùng để khóa ghi liên tiến trình.
5. Có thể chạy riêng `run-pos.bat` hoặc `run-admin.bat` sau khi đã biên dịch bằng `run.bat`.

### Tài khoản đăng nhập demo

| Tên đăng nhập | Mật khẩu | Vai trò | App được phép truy cập |
| --- | --- | --- | --- |
| `staff` | `Staff@123` | Nhân viên | POS |
| `admin` | `Admin@123` | Quản trị viên | POS và Admin |

Hai tài khoản demo được tạo tự động trong lần đăng nhập đầu tiên khi chưa có `users.dat`. Tên đăng nhập được bỏ khoảng trắng ở hai đầu và không phân biệt chữ hoa/chữ thường; mật khẩu phân biệt chữ hoa/chữ thường và giữ nguyên khoảng trắng. Không cần đăng ký tài khoản để chạy thử.

- Tài khoản được lưu trong **`users.dat`**, cùng thư mục với `cafe.dat`; chỉ lưu giá trị băm **PBKDF2-HMAC-SHA256** và salt riêng cho từng tài khoản, không lưu mật khẩu dạng rõ. Đây là file nhị phân có phiên bản định dạng riêng, không phải file văn bản.
- Hai app dùng chung kho tài khoản. `users.lock` bảo vệ việc khởi tạo/đọc dữ liệu giữa các tiến trình; dữ liệu được ghi qua file tạm trước khi thay thế.
- Nút **Đăng xuất** kết thúc phiên hiện tại, đóng cửa sổ nghiệp vụ và đưa app trở lại màn hình đăng nhập. Mỗi app có phiên riêng: đăng xuất POS không đăng xuất cửa sổ Admin đang mở.
- Thêm đăng nhập không thay đổi cấu trúc hay xóa dữ liệu đơn hàng, tồn kho và khuyến mãi trong `cafe.dat`.

### Thử đăng nhập và phân quyền

1. Chạy `run.bat`. Trước khi đăng nhập, chỉ thấy màn hình đăng nhập của từng app.
2. Ở POS, nhập `staff` / `Staff@123`: giao diện bán hàng mở và hiển thị tên người dùng.
3. Ở Admin, thử `staff` / `Staff@123`: app từ chối vì tài khoản không có quyền quản trị. Dùng `admin` / `Admin@123` để vào Admin.
4. Thử bỏ trống tên/mật khẩu hoặc nhập sai mật khẩu: màn hình thông báo lỗi và không mở app.
5. Đăng xuất rồi đăng nhập lại; cũng có thể dùng tài khoản `admin` để vào POS.

### Thử nghiệm nhanh

1. Ở POS, thêm `Americano` → chọn `QR`, `Mang đi` → **Đặt hàng**. Đơn sẽ ở trạng thái **Đang xử lý**, nguyên liệu bị trừ ngay để giữ chỗ.
2. Ở POS vào **Đơn hàng**, chọn **Xác nhận thanh toán** → trạng thái chuyển sang **Đã thanh toán**, lúc này báo cáo mới cộng doanh thu. Nếu **Hủy đơn** khi vẫn đang xử lý, tồn kho được **hoàn lại** và đơn hiển thị **Đã hủy**.
3. Ở Admin vào **Kho nguyên liệu**, thử nhập/xuất hoặc trừ hết nguyên liệu có trong công thức. Sau khoảng 2,3 giây, POS tự báo **HẾT MÓN** với các món không thể chế biến.
4. Ở Admin vào **Khuyến mãi**, thêm giảm 20% cho `Americano` vào ngày hiện tại. Đơn POS **tạo mới** trong ngày sẽ áp dụng giảm giá đó; đơn cũ giữ nguyên mức giá đã chốt.
5. Cả hai app vào **Doanh thu**, xem số liệu trên ngày đã chọn (mặc định hôm nay).

**Định nghĩa doanh thu:** tổng tiền của đơn có trạng thái `PAID`, ghi nhận theo **thời điểm xác nhận thanh toán**, không phải thời điểm tạo đơn. Không tính đơn pending/cancelled. Không có phí thuế và phí dịch vụ. Nếu trùng khuyến mãi cho cùng món/cùng ngày, lấy mức giảm **cao nhất** (không cộng dồn).

## Chạy trên mạng LAN (nhiều máy)

Khi dùng nhiều máy, **chỉ một máy (máy quầy) giữ dữ liệu** `cafe.dat`/`users.dat` và chạy máy chủ; các máy POS/Admin khác gọi qua mạng. **Không** chia sẻ thư mục `.dat` qua ổ đĩa mạng, vì khóa file trên SMB không đáng tin.

```
[Máy quầy] run-server.bat ──► giữ cafe.dat + users.dat
     ▲  ▲        HTTP + JSON (cổng 8080)
     │  └── Máy 2: run-pos-lan.bat
     └───── Máy 3: run-admin-lan.bat
```

1. **Máy chủ:** nhấp đúp `run-server.bat`. Cửa sổ hiện địa chỉ dạng `192.168.1.10:8080`. Lần đầu Windows hỏi tường lửa, chọn **Allow** cho *Private networks* (hoặc mở cổng TCP 8080 vào). Đổi cổng: `run-server.bat 9000`. Giữ cửa sổ này mở.
2. **Máy POS/Admin khác:** chép **toàn bộ project** sang máy đó (giữ `src` và `assets`), nhấp đúp `run-pos-lan.bat` hoặc `run-admin-lan.bat`. Lần đầu nhập địa chỉ máy chủ; địa chỉ được lưu vào `server.txt` (xóa file này để đổi máy chủ).
3. Đăng nhập bằng tài khoản như bình thường. Việc xác thực và phân quyền do **máy chủ** thực hiện.
4. Tất cả máy nên dùng **cùng một phiên bản mã nguồn** (menu và công thức nằm trong `Catalog.java`). Máy quầy vẫn có thể chạy `run.bat` (chế độ một máy) song song với server, hai bên dùng chung file qua khóa file.

Chạy thủ công: `java -Dcafe.server=192.168.1.10:8080 -cp build cafe.PosApp` (thiếu `-Dcafe.server` thì app chạy ở chế độ một máy như cũ).

**Giao thức** (JSON qua HTTP, token `Authorization: Bearer ...` sau khi đăng nhập): `POST /api/login`, `POST /api/logout`, `GET /api/state?rev=N` (trả `{"unchanged":true}` nếu chưa đổi), `POST /api/orders`, `/api/orders/pay`, `/api/orders/cancel`, `/api/stock` *(Admin)*, `/api/promotions` và `/api/promotions/delete` *(Admin)*, `GET /api/ping`.

**Lưu ý bảo mật:** dùng HTTP thường, mật khẩu đi dạng rõ trong mạng nội bộ; chỉ nên dùng trên mạng LAN tin cậy, không mở cổng ra Internet. Máy chủ chặn tạm một địa chỉ sau 5 lần đăng nhập sai và phiên hết hạn sau 12 giờ không dùng.

## Cấu trúc

```text
cotafe-java-demo/
├── run.bat                # Biên dịch + chạy 2 app trên Windows
├── run-pos.bat            # Chỉ chạy POS (sau khi build)
├── run-admin.bat          # Chỉ chạy Admin (sau khi build)
├── run-server.bat         # Chạy máy chủ LAN (máy giữ dữ liệu)
├── run-pos-lan.bat        # POS kết nối máy chủ LAN
├── run-admin-lan.bat      # Admin kết nối máy chủ LAN
├── lan-client.bat         # Script dùng chung của hai file *-lan.bat
├── test.bat               # Kiểm thử nghiệp vụ, đăng nhập và máy chủ LAN
├── README.md
├── assets/                # 6 hình món đã crop từ ảnh tham khảo
├── src/cafe/
│   ├── Data.java          # Model Order, Promotion, Product, State
│   ├── Catalog.java       # Menu, giá, định lượng công thức
│   ├── Store.java         # File .dat + FileLock + ghi atomic
│   ├── CafeApi.java       # Interface thao tác nghiệp vụ (cục bộ hoặc từ xa)
│   ├── CafeService.java   # Đặt/hủy/thanh toán, kho, giảm giá, doanh thu (cục bộ)
│   ├── CafeServer.java    # Máy chủ HTTP/JSON bao quanh CafeService + AuthService
│   ├── ServerMain.java    # Điểm chạy của máy chủ LAN
│   ├── RemoteCafeApi.java # Client gọi máy chủ qua LAN
│   ├── Connection.java    # Chọn chế độ cục bộ hoặc LAN khi đăng nhập
│   ├── Json.java          # JSON tối giản, không thư viện ngoài
│   ├── Wire.java          # Dữ liệu nghiệp vụ <-> JSON
│   ├── Role.java          # Vai trò và quyền truy cập app
│   ├── User.java          # Thông tin người dùng bất biến
│   ├── UserRepository.java # Interface truy xuất tài khoản
│   ├── FileUserRepository.java # Kho tài khoản dùng chung trên file
│   ├── PasswordHasher.java # Băm và kiểm tra mật khẩu
│   ├── AuthService.java   # Xác thực, kiểm tra quyền và quản lý phiên
│   ├── LoginFrame.java    # Giao diện đăng nhập dùng chung
│   ├── Ui.java            # Theme, button, card, report
│   ├── PosApp.java        # Giao diện POS
│   └── AdminApp.java      # Giao diện Admin
└── tests/cafe/            # Kiểm thử nghiệp vụ, xác thực và đa tiến trình

# TỰ TẠO KHI CHẠY:
├── build/                 # File .class
├── cafe.dat               # Dữ liệu chia sẻ
├── cafe.lock              # Khóa dữ liệu nghiệp vụ
├── users.dat              # Tài khoản và mật khẩu đã băm
└── users.lock             # Khóa kho tài khoản
```

## Thiết kế đăng nhập theo hướng đối tượng

`User` đóng gói thông tin người dùng; `Role` biểu diễn vai trò và quy tắc truy cập. `PasswordHasher` chịu trách nhiệm băm/kiểm tra mật khẩu, tách biệt với giao diện.

`AuthService` phụ thuộc vào interface `UserRepository`, còn `FileUserRepository` cung cấp cách lưu tài khoản trên file. Có thể thay cách lưu trữ bằng một lớp triển khai interface khác mà không phải viết lại giao diện đăng nhập. Khi xác thực thành công, `AuthService` cấp một `Session`; phiên kiểm tra quyền trước khi mở `PosApp` hoặc `AdminApp` và bị vô hiệu hóa khi đăng xuất.

`LoginFrame` thu thập tên đăng nhập/mật khẩu và hiển thị kết quả từ `AuthService`. Hai app dùng lại cùng màn hình đăng nhập, mỗi app yêu cầu vai trò tương ứng. Việc xác thực không trộn vào các quy tắc đặt món, thanh toán hoặc tồn kho của `CafeService`.

## Kiểm thử tự động

Chạy **`test.bat`** để biên dịch mã nguồn và thực hiện `SmokeTest`, `AuthTest` và `ServerTest` (JSON, máy chủ LAN, phân quyền, 30 đơn đặt đồng thời qua mạng). Có thể dùng `test.bat --no-pause` khi chạy từ terminal hoặc công cụ tự động; script trả mã lỗi khác 0 nếu biên dịch/kiểm thử thất bại.

Các bài kiểm thử dùng thư mục dữ liệu tạm: kiểm tra nghiệp vụ bán hàng hiện có, đăng nhập đúng/sai, quyền truy cập POS/Admin, đăng xuất, lưu tài khoản, từ chối dữ liệu tài khoản hỏng và khởi tạo tài khoản từ nhiều tiến trình.

Để kiểm tra giao diện tùy chọn, sau khi chạy `test.bat`, dùng `java -cp build cafe.LoginUiTest` trong thư mục dự án. Bài kiểm thử này cần desktop đồ họa, tự mở/đóng các cửa sổ thử nghiệm và dùng thư mục dữ liệu tạm; không chạy mặc định trong `test.bat`. Chạy bằng cùng JDK đã dùng để biên dịch; nếu terminal chưa đặt `PATH` đúng, dùng `"%JAVA_HOME%\bin\java" -cp build cafe.LoginUiTest` trong Command Prompt.

## Thay đổi / reset

- Muốn đổi **menu/giá/định mức nguyên liệu**: sửa `src/cafe/Catalog.java`.
- Muốn đổi **số tồn kho khởi tạo**: sửa constructor `Data.State()` trong `src/cafe/Data.java` (**chỉ ảnh hưởng file mới**).
- Muốn đổi **quy tắc đơn hàng/giảm giá**: sửa `src/cafe/CafeService.java`.
- Muốn sửa **giao diện**: xem `src/cafe/Ui.java`, `LoginFrame.java`, `PosApp.java`, `AdminApp.java`.
- Muốn đổi **tài khoản demo khởi tạo**: xem `src/cafe/FileUserRepository.java` (chỉ ảnh hưởng kho tài khoản mới).
- Khi sửa Java, đóng cửa sổ cũ và chạy lại `run.bat` để biên dịch.
- **Reset dữ liệu demo**: đóng cả hai app, sau đó xóa `cafe.dat` (và tùy ý `cafe.lock`), mở lại. **Sao lưu `cafe.dat`** trước khi xóa nếu cần giữ lịch sử.
- Không sửa trực tiếp `cafe.dat` bằng text editor: đây là file nhị phân Java Serialization.
- **Reset tài khoản demo**: đóng cả hai app, sao lưu rồi xóa `users.dat`, sau đó đăng nhập lại để tạo lại tài khoản mặc định. Không cần xóa `cafe.dat`.
- Nếu `users.dat` hỏng, đăng nhập sẽ báo lỗi; app không tự ghi đè kho tài khoản. Khôi phục bản sao lưu hoặc reset tài khoản theo hướng dẫn trên.

## Giới hạn của bản demo

- Chế độ một máy (`run.bat`) chỉ dùng trên **cùng một máy / một thư mục**. Muốn dùng nhiều máy hãy chạy theo mục **Chạy trên mạng LAN** (không chia sẻ file `.dat` qua ổ đĩa mạng).
- Chức năng tài khoản hiện gồm đăng nhập, phân quyền truy cập app và đăng xuất; chưa có đăng ký, quản lý tài khoản hoặc đổi/quên mật khẩu.
- Tài khoản mặc định dùng để minh họa. Phân quyền áp dụng trong luồng mở app, không bảo vệ dữ liệu trước người có quyền sửa file hoặc mã nguồn trên máy.
- Chưa có in hóa đơn, tích hợp API ngân hàng hay QR thanh toán thật.
- Ảnh món dùng bản crop từ ảnh tham khảo nên độ phân giải không cao.
- Giao tiếp LAN dùng HTTP không mã hóa; chưa có HTTPS.
- Với nghiệp vụ thật hoặc nhiều quầy tải cao, nên chuyển lưu trữ sang SQLite/server DB và bổ sung quản lý tài khoản cùng phân quyền ở tầng dịch vụ.
