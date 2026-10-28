# Cotafe — 2 ứng dụng Java chạy song song, dùng chung file `.dat`

Hai ứng dụng **Java Swing thuần (không cần Maven, Gradle, JavaFX hoặc thư viện ngoài)**:

- **POS (nhân viên):** danh sách món, tìm kiếm/lọc, giỏ hàng, đặt món; chọn **tiền mặt / QR**, **tại chỗ / mang đi**; đơn **Đang xử lý → Đã thanh toán**, có nút **Xác nhận thanh toán** hoặc **Hủy đơn**; xem lịch sử và báo cáo doanh thu.
- **Admin:** xem tồn kho, **nhập/xuất nguyên liệu**; thêm/xóa **khuyến mãi theo món + ngày + %**; báo cáo doanh thu giống POS.

> Giao diện lấy cảm hứng bố cục và hình minh họa món từ ảnh tham khảo do người dùng cung cấp. Không tích hợp cổng thanh toán thực tế: QR là lựa chọn ghi nhận phương thức thanh toán; người dùng cần xác nhận đã nhận tiền.

## Cách chạy trên Windows

1. Cài **JDK 17 hoặc mới hơn** (phải có cả `java`, `javaw`, `javac` trong `PATH`). Gõ `javac -version` trong Command Prompt để kiểm tra.
2. Giải nén toàn bộ project vào cùng **một** thư mục (giữ nguyên `src` và `assets`).
3. **Nhấp đúp `run.bat`**. Script biên dịch và mở **hai cửa sổ cùng lúc**: `Cotafe • Nhân viên POS` và `Cotafe • Admin`.
4. Hai app đọc/ghi file **`cafe.dat` nằm cùng cấp với `run.bat`**; file được tạo tự động trong lần đầu chạy. File `cafe.lock` được dùng để khóa ghi liên tiến trình.
5. Có thể chạy riêng `run-pos.bat` hoặc `run-admin.bat` sau khi đã biên dịch bằng `run.bat`.

### Thử nghiệm nhanh

1. Ở POS, thêm `Americano` → chọn `QR`, `Mang đi` → **Đặt hàng**. Đơn sẽ ở trạng thái **Đang xử lý**, nguyên liệu bị trừ ngay để giữ chỗ.
2. Ở POS vào **Đơn hàng**, chọn **Xác nhận thanh toán** → trạng thái chuyển sang **Đã thanh toán**, lúc này báo cáo mới cộng doanh thu. Nếu **Hủy đơn** khi vẫn đang xử lý, tồn kho được **hoàn lại** và đơn hiển thị **Đã hủy**.
3. Ở Admin vào **Kho nguyên liệu**, thử nhập/xuất hoặc trừ hết nguyên liệu có trong công thức. Sau khoảng 2,3 giây, POS tự báo **HẾT MÓN** với các món không thể chế biến.
4. Ở Admin vào **Khuyến mãi**, thêm giảm 20% cho `Americano` vào ngày hiện tại. Đơn POS **tạo mới** trong ngày sẽ áp dụng giảm giá đó; đơn cũ giữ nguyên mức giá đã chốt.
5. Cả hai app vào **Doanh thu**, xem số liệu trên ngày đã chọn (mặc định hôm nay).

**Định nghĩa doanh thu:** tổng tiền của đơn có trạng thái `PAID`, ghi nhận theo **thời điểm xác nhận thanh toán**, không phải thời điểm tạo đơn. Không tính đơn pending/cancelled. Không có phí thuế và phí dịch vụ. Nếu trùng khuyến mãi cho cùng món/cùng ngày, lấy mức giảm **cao nhất** (không cộng dồn).

## Cấu trúc

```text
cotafe-java-demo/
├── run.bat                # Biên dịch + chạy 2 app trên Windows
├── run-pos.bat            # Chỉ chạy POS (sau khi build)
├── run-admin.bat          # Chỉ chạy Admin (sau khi build)
├── test.bat               # Kiểm thử nghiệp vụ
├── README.md
├── assets/                # 6 hình món đã crop từ ảnh tham khảo
├── src/cafe/
│   ├── Data.java          # Model Order, Promotion, Product, State
│   ├── Catalog.java       # Menu, giá, định lượng công thức
│   ├── Store.java         # File .dat + FileLock + ghi atomic
│   ├── CafeService.java   # Đặt/hủy/thanh toán, kho, giảm giá, doanh thu
│   ├── Ui.java            # Theme, button, card, report
│   ├── PosApp.java        # Giao diện POS
│   └── AdminApp.java      # Giao diện Admin
└── tests/cafe/            # Smoke tests và test đa tiến trình

# TỰ TẠO KHI CHẠY:
├── build/                 # File .class
├── cafe.dat               # Dữ liệu chia sẻ
└── cafe.lock              # Tệp khóa, KHÔNG phải dữ liệu nghiệp vụ
```

## Thay đổi / reset

- Muốn đổi **menu/giá/định mức nguyên liệu**: sửa `src/cafe/Catalog.java`.
- Muốn đổi **số tồn kho khởi tạo**: sửa constructor `Data.State()` trong `src/cafe/Data.java` (**chỉ ảnh hưởng file mới**).
- Muốn đổi **quy tắc đơn hàng/giảm giá**: sửa `src/cafe/CafeService.java`.
- Muốn sửa **giao diện**: xem `src/cafe/Ui.java`, `PosApp.java`, `AdminApp.java`.
- Khi sửa Java, đóng cửa sổ cũ và chạy lại `run.bat` để biên dịch.
- **Reset dữ liệu demo**: đóng cả hai app, sau đó xóa `cafe.dat` (và tùy ý `cafe.lock`), mở lại. **Sao lưu `cafe.dat`** trước khi xóa nếu cần giữ lịch sử.
- Không sửa trực tiếp `cafe.dat` bằng text editor: đây là file nhị phân Java Serialization.

## Giới hạn của bản demo

- Chạy offline trên **cùng một máy / một thư mục**; không dùng để chia sẻ qua nhiều máy hoặc ổ đĩa mạng.
- Không có đăng nhập, phân quyền, in hóa đơn, tích hợp API ngân hàng hay QR thanh toán thật.
- Ảnh món dùng bản crop từ ảnh tham khảo nên độ phân giải không cao.
- Với nhiều quầy/máy hoặc nghiệp vụ thật, nên chuyển lưu trữ sang SQLite/server DB, thêm kiểm thử tích hợp và xác thực người dùng.
