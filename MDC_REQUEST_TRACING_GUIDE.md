# Hướng dẫn theo vết request bằng MDC

## 1. Mục tiêu

Mỗi HTTP request nhận một UUID mới. UUID được đặt trong SLF4J MDC, xuất hiện trên các dòng log của request trong Controller và Service, đồng thời được trả về trong header `X-Request-Id`. Filter xóa MDC sau khi xử lý để tránh dữ liệu sót lại khi servlet thread được tái sử dụng.

## 2. MDC là gì?

MDC (Mapped Diagnostic Context) là một map theo thread do logging framework cung cấp. Ứng dụng có thể đặt các thông tin chẩn đoán như request ID vào đó để logging pattern tự chèn thông tin vào mỗi dòng log mà không phải truyền ID thủ công qua mọi lời gọi logger.

## 3. Tại sao cần requestId?

Khi có nhiều request và hàng nghìn dòng log đan xen, request ID giúp ghép chính xác các log thuộc cùng một request, kể cả khi xử lý qua nhiều lớp. Có thể dùng ID này để tìm kiếm, điều tra lỗi và đối chiếu phản hồi với log.

## 4. RequestIdFilter hoạt động như thế nào?

`RequestIdFilter` là Spring `@Component` và kế thừa `OncePerRequestFilter`, nên Spring Web tự đăng ký filter và gọi nó một lần trên mỗi request trong dispatch thông thường. Filter luôn tạo `UUID.randomUUID()` mới, không dùng lại header `X-Request-Id` từ client, đặt ID vào MDC và response header trước khi chuyển tiếp request tới các filter/controller tiếp theo.

Logic cốt lõi:

```java
String requestId = UUID.randomUUID().toString();
try {
    MDC.put("requestId", requestId);
    response.setHeader("X-Request-Id", requestId);
    filterChain.doFilter(request, response);
} finally {
    MDC.remove("requestId");
}
```

## 5. `MDC.put()` và 6. `MDC.remove()`

- `MDC.put("requestId", requestId)` đặt ID vào context của thread hiện tại để Logback có thể đọc trong lúc xử lý request.
- `MDC.remove("requestId")` xóa khóa sau khi xử lý. Servlet container dùng lại thread cho request khác; không xóa context có thể làm log request tiếp theo mang nhầm ID.

## 7. Tại sao cần `try-finally`?

Controller hoặc code phía sau có thể ném exception. `finally` vẫn chạy khi `filterChain.doFilter()` kết thúc hoặc lỗi, đảm bảo MDC luôn được dọn. Không nên chỉ xóa sau lời gọi filter chain vì nhánh exception có thể bỏ qua thao tác đó.

## 8. `%X{requestId}` trong Logback

`%X{requestId}` đọc giá trị khóa `requestId` từ MDC và in vào dòng log. `logback-spring.xml` đặt pattern gồm thời gian, thread, level, request ID, logger và nội dung. Log không nằm trong HTTP request (chẳng hạn log lúc khởi động) có thể hiển thị trường request ID trống.

## 9. Controller và Service dùng chung UUID

Filter thiết lập MDC trước khi gọi `filterChain.doFilter()`. Controller và Service chạy đồng bộ trên cùng request thread nên cả hai logger đều đọc cùng một giá trị MDC. `GET /api/test-mdc` ghi log ở Controller và gọi `MdcTestService`, nơi có log DEBUG, INFO, WARN và ERROR mẫu. ERROR ở đây chỉ là thông điệp minh họa, không ném exception hay làm API thất bại.

## 10. Chạy ứng dụng

Yêu cầu JDK 21 và Maven:

```powershell
mvn spring-boot:run
```

Mặc định ứng dụng ở `http://localhost:8080`. Nếu cổng đó đã được tiến trình khác sử dụng, dừng tiến trình phù hợp hoặc chạy với cổng khác, ví dụ:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

## 11. Kiểm thử bằng Postman

Gửi hai lần:

- Method: `GET`
- URL: `http://localhost:8080/api/test-mdc`

Mỗi lần cần nhận HTTP 200, response header `X-Request-Id` với UUID mới và JSON chứa cùng ID:

```json
{
  "requestId": "e8b3cfa9-7ba8-4ed1-8d35-f516a9ce9214",
  "message": "Request processed; see application logs for the shared request ID."
}
```

So sánh header giữa hai lần gọi: hai UUID phải khác nhau. Mọi dòng log từ Controller và Service của cùng một request phải có cùng ID. Client gửi sẵn `X-Request-Id` cũng không thay đổi hành vi; server luôn sinh UUID mới theo yêu cầu bài tập.

Để thấy log DEBUG, dùng Actuator (không cần restart):

```http
POST http://localhost:8080/actuator/loggers/com.storex.payment
Content-Type: application/json
```

```json
{"configuredLevel":"DEBUG"}
```

Gọi lại `/api/test-mdc`, xác nhận log DEBUG có cùng request ID. Đưa package về INFO sau khi kiểm thử:

```json
{"configuredLevel":"INFO"}
```

Các endpoint Actuator `health` và `loggers` từ Bài tập 1 vẫn được giữ nguyên.

## 12. Ví dụ log trước và sau MDC

Trước:

```text
INFO  c.storex.payment.MdcTestController - Starting MDC test request
INFO  c.storex.payment.MdcTestService    - Processing request in MDC test service
```

Sau:

```text
2026-11-03 10:00:00.001 [http-nio-8080-exec-1] INFO  [e8b3cfa9-7ba8-4ed1-8d35-f516a9ce9214] c.storex.payment.MdcTestController - Starting MDC test request
2026-11-03 10:00:00.002 [http-nio-8080-exec-1] INFO  [e8b3cfa9-7ba8-4ed1-8d35-f516a9ce9214] c.storex.payment.MdcTestService - Processing request in MDC test service
```

## 13. Tìm log trên Kibana

Nếu log được thu thập dưới dạng text, tìm theo UUID đầy đủ, ví dụ:

```text
"e8b3cfa9-7ba8-4ed1-8d35-f516a9ce9214"
```

Nếu pipeline parse log thành field, lọc theo field tương ứng, ví dụ `requestId:"e8b3cfa9-7ba8-4ed1-8d35-f516a9ce9214"`. Tên field cụ thể phụ thuộc cấu hình ingest và mapping của Elasticsearch.

## 14. MDC với Async hoặc `@Async`

MDC gắn với thread; context không tự truyền sang thread khác. Với `@Async`, executor hoặc reactive pipeline, log trong thread làm việc có thể thiếu request ID. Cấu hình `TaskDecorator` để chụp và phục hồi MDC cho task, hoặc truyền context bằng cơ chế tương thích của framework đang dùng. Luôn dọn context trong `finally` ở cả thread nhận và thread xử lý, đặc biệt khi dùng thread pool. Không giả định `ThreadLocal` MDC tự được truyền qua ranh giới bất đồng bộ.

## Kiểm thử và tương thích

Chạy:

```powershell
mvn test
```

Test xác nhận ID là UUID, có trong response header/body, hai request khác ID, MDC được xóa sau mỗi request, và Actuator vẫn đổi được log level DEBUG rồi trả về INFO.
