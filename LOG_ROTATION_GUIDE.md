# Hướng dẫn Log Rotation với Logback

## 1. Mục tiêu

Giới hạn dung lượng file log bằng `RollingFileAppender`: log hoạt động ghi vào `logs/application.log`, archive được chia theo ngày và kích thước, lưu tối đa 30 ngày và có giới hạn tổng dung lượng archive. Cấu hình giữ Console appender, Discord appender và MDC `requestId`.

## 2. Vì sao file log có thể tăng vô hạn?

`FileAppender` chỉ nối thêm log vào một file và không tự đổi tên, nén hoặc xóa file. Nếu ứng dụng ghi lâu dài, `application.log` có thể tăng đến khi hết dung lượng đĩa.

## 3–5. FileAppender và RollingFileAppender

- `FileAppender` ghi vào một file, thường không có cơ chế xoay vòng.
- `RollingFileAppender` ghi file hiện tại và kích hoạt rolling policy để archive file theo thời gian/kích thước.
- `RollingFileAppender` giữ nguyên log qua rollover bằng cách chuyển file đang ghi thành archive rồi tiếp tục ghi vào file hiện tại mới.

## 6. SizeAndTimeBasedRollingPolicy

Policy này xoay archive theo ngày và tạo nhiều archive trong cùng ngày khi vượt ngưỡng kích thước. Cấu hình trong `src/main/resources/logback-spring.xml`:

```xml
<appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/application.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
        <fileNamePattern>logs/application-%d{yyyy-MM-dd}.%i.log</fileNamePattern>
        <maxFileSize>10MB</maxFileSize>
        <maxHistory>30</maxHistory>
        <totalSizeCap>1GB</totalSizeCap>
    </rollingPolicy>
    <encoder>
        <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{requestId}] %logger{36} - %msg%n</pattern>
    </encoder>
</appender>
```

### Ý nghĩa các giới hạn

- `maxFileSize=10MB`: ngưỡng kích thước cho từng archive trong một chu kỳ ngày. Nó không phải giới hạn tổng tất cả log.
- `maxHistory=30`: giữ history tối đa 30 chu kỳ ngày; archive cũ hơn được dọn khi Logback chạy cleanup trong quá trình rollover (hoặc theo cơ chế cleanup của policy). Đây không phải timer chính xác đến từng phút.
- `totalSizeCap=1GB`: giới hạn bổ sung cho tổng archive được quản lý bởi policy; khi cleanup chạy, archive cũ hơn có thể bị xóa để đưa tổng dung lượng xuống mức cap. Giới hạn này khác `maxFileSize`: một cái giới hạn từng file, cái còn lại giới hạn tổng archive. File hiện hành `logs/application.log` không phải archive để cleanup xóa.

Không ghi file khác vào khu vực archive của pattern `logs/application-%d{yyyy-MM-dd}.%i.log`; Logback chỉ quản lý archive khớp pattern này, không chủ động xóa các file dữ liệu ngoài pattern. Giới hạn archive cũng không tính toàn bộ dữ liệu khác trên ổ đĩa.

### Ý nghĩa `fileNamePattern`, `%d` và `%i`

- `logs/application-%d{yyyy-MM-dd}.%i.log` đặt archive cạnh file hiện hành trong thư mục `logs`.
- `%d{yyyy-MM-dd}` là ngày rollover.
- `%i` là số thứ tự archive theo kích thước trong cùng ngày, thường bắt đầu từ 0.
- Ví dụ: `application-2026-11-03.0.log`, `application-2026-11-03.1.log`. File hiện tại là `application.log`.

Pattern console và file đều chứa timestamp, thread, log level, `%X{requestId}`, logger name và message. Discord appender vẫn được gắn một lần ở root; INFO/WARN không gửi Discord, ERROR tiếp tục đi qua appender bất đồng bộ hiện có.

## 7. Kiểm tra thư mục log

Khởi động tại thư mục gốc project:

```powershell
mvn spring-boot:run
```

Sau khi ứng dụng ghi log, kiểm tra:

```powershell
Get-ChildItem .\logs
Get-ChildItem .\logs | Select-Object Name, Length, LastWriteTime
```

Ban đầu mong đợi có `logs/application.log`. File log là tương đối với working directory của tiến trình; trên Production hãy đặt working directory/volume có dung lượng và quyền truy cập phù hợp.

## 8. Kiểm thử bằng Postman

### Log các mức cơ bản

- `GET http://localhost:8080/api/test-log-rotation`
- HTTP 200; API tạo log INFO, DEBUG, WARN, ERROR và trả:

```json
{"message":"Test logs emitted at INFO, DEBUG, WARN, and ERROR."}
```

Mặc định root ở INFO nên lời gọi DEBUG không hiện cho đến khi đổi level bằng Actuator. Request có request ID trong console và file.

### Tạo dữ liệu log có giới hạn

- `GET http://localhost:8080/api/test-log-rotation/bulk?count=1000`
- HTTP 200 với `requested` và `logged` bằng 1000.
- `count` hợp lệ là 1–10000; ngoài khoảng bị trả HTTP 400. Mỗi mục ghi một message cỡ nhỏ, không có API tạo lượng dữ liệu tùy ý.

Sau đó kiểm tra `logs/application.log`:

```powershell
Get-Item .\logs\application.log | Select-Object Name, Length
```

## 9. Mô phỏng rollover nhanh trong Development

1. Dừng ứng dụng.
2. Trong `logback-spring.xml`, tạm đổi `<maxFileSize>10MB</maxFileSize>` thành `<maxFileSize>10KB</maxFileSize>` (hoặc `1KB`).
3. Khởi động ứng dụng và gọi bulk endpoint một vài lần để vượt ngưỡng.
4. Kiểm tra archive như `application-2026-11-03.0.log`, `application-2026-11-03.1.log` và file đang ghi `application.log`.
5. Dừng ứng dụng, khôi phục **10MB**, rồi kiểm tra cấu hình cuối cùng trước khi chạy Production.

Không cần chờ 30 ngày để xác nhận XML/rolling policy được cấu hình. `maxHistory` chỉ xóa archive quá tuổi khi Logback thực hiện cleanup; tránh xóa thủ công các file production đang được dùng.

## 10. Bảo vệ dung lượng VPS và lưu ý Production

- Chọn `totalSizeCap` theo dung lượng volume và ngân sách lưu trữ; 1GB là giới hạn cho archive của policy này, không phải toàn bộ ổ đĩa hay toàn bộ log của nhiều instance.
- Dùng volume riêng hoặc giám sát dung lượng đĩa, inode và log growth; cảnh báo trước khi filesystem cạn.
- Cân nhắc shipping log tới hệ thống tập trung, log retention theo quy định, nén, backup và quyền truy cập.
- Nếu chạy nhiều replica cùng ghi chung một file/pattern, không chia sẻ thư mục log trừ khi dùng cơ chế multi-process an toàn. Ưu tiên file/volume riêng cho từng instance.
- Đặt `maxHistory`/`totalSizeCap` phù hợp với chính sách audit và yêu cầu lưu trữ.
- Cleanup xảy ra khi policy rollover/cleanup chạy, không phải một scheduler độc lập đảm bảo xóa đúng thời điểm nếu không có rollover.
- Không để log chứa bí mật; bảo vệ thư mục và archive như dữ liệu vận hành.

## 11. Kiểm thử

```powershell
mvn test
```

Test xác nhận API bulk ghi số lượng giới hạn, từ chối giá trị quá lớn, đồng thời các bài Actuator, MDC và Discord hiện có tiếp tục chạy.
