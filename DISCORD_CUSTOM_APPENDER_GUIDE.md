# Discord Custom Logback Appender

## 1. Mục tiêu

Gửi cảnh báo Discord dạng Embed cho log từ mức ERROR trở lên, đồng thời giữ log trên console và đưa `requestId` từ MDC vào Embed. Việc gửi chạy trên executor riêng có hàng đợi giới hạn để không chặn luồng xử lý request.

## 2. Custom Appender và Logback

Logback nhận logging event từ SLF4J rồi chuyển event đến một hoặc nhiều appender. `DiscordAppender` kế thừa `AppenderBase<ILoggingEvent>`, được đăng ký cùng Console appender trong `logback-spring.xml`. `append()` bỏ qua event thấp hơn ERROR và chỉ chụp dữ liệu event, tạo payload rồi enqueue việc gửi.

`AppenderBase<ILoggingEvent>` là lớp nền Logback cho appender nhận event kiểu `ILoggingEvent`; event chứa level, logger, message, timestamp, MDC snapshot và thông tin exception.

## 3. Phát hiện ERROR và tạo Embed

Appender so sánh `eventObject.getLevel().toInt()` với `Level.ERROR_INT`. Payload được serialize thành JSON bằng Jackson, không nối chuỗi JSON thủ công. Embed có title `APPLICATION ERROR`, màu đỏ decimal `15158332`, nội dung log, level, logger, request ID, timestamp ISO-8601 và stack trace nếu event có exception.

Message null được thay bằng `(no message)`. Nội dung, field và stack trace bị giới hạn để tôn trọng giới hạn Discord Embed; các chuỗi được cắt theo code point để không làm hỏng cặp surrogate. Payload tắt mention parsing để tránh log kích hoạt `@everyone`/`@here`. Các giá trị thông thường của password, token, secret, authorization, credentials, bearer/basic token và JDBC/database connection URL được che trước khi gửi. Không nên log secret ngay từ đầu; sanitizer không thể nhận diện mọi dạng dữ liệu nhạy cảm.

## 4. HTTP, timeout và cô lập lỗi

Appender dùng Java `HttpClient` gửi HTTP POST với `Content-Type: application/json; charset=utf-8`, timeout kết nối 2 giây và timeout request 4 giây. Gửi đồng bộ ngay trong `append()` có thể chặn thread ghi log, làm tăng độ trễ request hoặc nghẽn ứng dụng khi Discord chậm. Vì vậy appender dùng một worker daemon và `ArrayBlockingQueue` tối đa 128 event; khi queue đầy, event bị bỏ và cảnh báo nội bộ chỉ in một lần ra `System.err`.

Lỗi mạng, HTTP status ngoài 2xx hoặc lỗi serialize không được gửi qua SLF4J để tránh vòng lặp ERROR → appender → ERROR. Appender báo sự cố qua `System.err` một lần, không in webhook URL, body response hay log event. Khi shutdown, executor được đóng và chờ tối đa 2 giây trước khi hủy task còn lại.

## 5. MDC và requestId

Appender lấy request ID từ `eventObject.getMDCPropertyMap().get("requestId")`, không đọc `MDC` của worker. Event giữ snapshot MDC tại thời điểm ghi log; worker chạy trên thread khác nên MDC của thread đó không phải context của request ban đầu.

`RequestIdFilter` hiện có vẫn sinh UUID, đặt MDC trước khi xử lý, thêm response header `X-Request-Id` và gọi `MDC.remove("requestId")` trong `finally`. Log của cùng request có cùng request ID; mỗi request mới nhận UUID mới.

## 6. Tạo Discord Webhook

1. Mở Discord và chọn server nhận cảnh báo.
2. Chọn channel dành cho Dev Team, mở **Edit Channel**.
3. Vào **Integrations** → **Webhooks** → **New Webhook**.
4. Chọn channel phù hợp và **Copy Webhook URL**.
5. Không paste URL vào source code, tài liệu đã commit, log hoặc Git.

Chưa cấu hình URL thì ứng dụng vẫn khởi động bình thường; appender ghi một cảnh báo trên console, không tạo executor/network task và không gửi HTTP.

## 7. Cấu hình biến môi trường

`application.yml` đọc `DISCORD_WEBHOOK_URL` qua placeholder:

```yaml
discord:
  webhook:
    url: ${DISCORD_WEBHOOK_URL:}
```

PowerShell, trong terminal chạy ứng dụng:

```powershell
$env:DISCORD_WEBHOOK_URL = "PASTE_WEBHOOK_URL_HERE"
mvn spring-boot:run
```

Hoặc cấu hình biến `DISCORD_WEBHOOK_URL` trong Run Configuration/Environment của VS Code. Không commit giá trị thật vào Git. `logback-spring.xml` dùng `springProperty` để chuyển property sang appender khi Logback khởi tạo; custom appender không phụ thuộc `@Value` hay Spring bean.

## 8. Kiểm thử bằng Postman

1. Đặt biến môi trường và khởi động ứng dụng:

   ```powershell
   $env:DISCORD_WEBHOOK_URL = "PASTE_WEBHOOK_URL_HERE"
   mvn spring-boot:run
   ```

2. Gửi `GET http://localhost:8080/api/test-discord-error`.
3. API trả HTTP 200, header `X-Request-Id` và JSON chứa cùng `requestId`.
4. Console hiển thị INFO, WARN và ERROR với cùng request ID.
5. Discord nhận một Embed cho duy nhất log ERROR: `TEST DISCORD WEBHOOK ALERT`, logger, request ID, timestamp và exception mô phỏng.

INFO và WARN vẫn xuất hiện trên console nhưng appender loại chúng khỏi Discord. ERROR được enqueue bất đồng bộ. Endpoint trả về trước khi đảm bảo Discord đã nhận alert; webhook có thể thất bại hoặc hàng đợi đầy, và các trường hợp này được báo riêng trên `System.err`.

Không có webhook thật trong project này, do đó xác minh gửi Discord thực tế cần URL được cung cấp an toàn trong môi trường chạy. Không commit URL vào repository.

## 9. Ví dụ Embed

```text
🚨 APPLICATION ERROR
TEST DISCORD WEBHOOK ALERT
Level: ERROR
Logger: com.storex.payment.DiscordAlertTestService
Request ID: <UUID từ request thực tế>
Time: <timestamp của logging event>
Exception: java.lang.IllegalStateException: Simulated alert-test exception
```

## 10. Tương thích và lưu ý Production

Custom appender không thay thế Console appender, `%X{requestId}`, RequestIdFilter hoặc Actuator dynamic log level từ các bài trước. Thử log DEBUG bằng cách POST `{"configuredLevel":"DEBUG"}` tới `/actuator/loggers/com.storex.payment`; DEBUG tiếp tục xuất hiện trên console nhưng không gửi Discord.

Trong Production, giữ URL trong secret manager/environment bảo mật, giới hạn quyền đọc biến môi trường và quyền quản lý Actuator, giám sát cảnh báo queue-full/failure, cân nhắc retry có giới hạn và metrics nếu cần độ tin cậy cao hơn. Bounded queue chủ ý có thể drop alert khi Discord chậm hoặc quá tải để bảo vệ luồng nghiệp vụ. Redaction là lớp giảm rủi ro, không thay thế chính sách không ghi secret vào log. Webhook cần HTTPS và URL không bao giờ được in ra log.

Chạy kiểm thử:

```powershell
mvn test
```
