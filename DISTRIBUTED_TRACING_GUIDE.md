# Distributed Tracing: API Gateway → Order Service → User Service

## 1. Mục tiêu và cấu trúc hiện trạng

Project ban đầu là một Maven/Spring Boot application đơn (`Spring Boot 3.5.7`, Java 21), không có Gateway, Order Service hoặc User Service. Để không thay thế hay làm hỏng bài tập 1–4, bài tập này bổ sung một reactor Maven riêng trong cùng repository tại `distributed-tracing/`, gồm ba tiến trình độc lập:

```text
Client → API Gateway :8080 → Order Service :8081 → User Service :8082
```

Ba dịch vụ dùng Spring Cloud 2025.0.0 (release train tương thích Spring Boot 3.5.x), Micrometer Tracing và Brave bridge. Gateway là reactive WebFlux; Order và User là Spring MVC. Không dùng OpenFeign, Zipkin, Jaeger hay tracing backend.

## 2–7. Distributed Tracing, Logging, Trace, Span, Trace ID và Span ID

- **Logging** ghi sự kiện riêng lẻ; distributed tracing liên kết các thao tác liên quan thành một đường đi xuyên nhiều tiến trình.
- **Trace** là toàn bộ đường đi của một request.
- **Span** là một operation trong trace, có quan hệ cha/con và thời lượng.
- **Trace ID** định danh toàn bộ trace, giống nhau ở Gateway, Order và User.
- **Span ID** định danh một operation cụ thể; ID của server/client/service spans có thể khác nhau.
- **requestId** là ID tương quan ứng dụng riêng (header `X-Request-Id`), không thay thế Trace ID hay Span ID.

Các span inbound HTTP được tạo bởi Spring Boot/Micrometer instrumentation. Order và User còn tạo span nghiệp vụ con bằng Micrometer `Tracer`; HTTP client được instrument tạo span client và truyền context tiếp.

## 8–10. Micrometer, Brave và HTTP propagation

`micrometer-tracing-bridge-brave` nối API tracing của Micrometer với Brave. Cả ba dịch vụ bật sampling probability `1.0` để dễ kiểm thử. Cấu hình propagation chọn chuẩn W3C; request đầu vào có `traceparent` hợp lệ được extract, request không có context được tracing instrumentation khởi tạo trace mới. Outgoing request được instrument inject context mới phù hợp với cùng trace.

Không tự tạo trace ID hoặc span ID trong ứng dụng. Các span do `Tracer.nextSpan()` tạo là span con theo context hiện hành. Không cần gửi trace nào đến server ngoài; trace chỉ hiển thị trong log ứng dụng.

W3C `traceparent` thường có dạng:

```text
00-<32 hex trace id>-<16 hex parent span id>-01
```

Trace context là thông tin quan sát, không phải thông tin xác thực; không dùng nó để cấp quyền.

## 11–14. Cấu hình từng service và Logback

Build descriptor của các service ở `distributed-tracing/pom.xml` import Spring Cloud BOM; từng service khai báo `micrometer-tracing-bridge-brave` và Actuator, không khai báo version riêng cho dependency đã được BOM quản lý.

- Gateway: `spring.application.name=api-gateway`, cổng 8080, route `/api/orders/**` tới Order Service. `spring-cloud-starter-gateway-server-webflux` giữ Gateway reactive; không có servlet dependency. `spring.reactor.context-propagation: auto` giữ context Micrometer hiện hành xuyên suốt reactive routing chain.
- Order: `spring.application.name=order-service`, cổng 8081; `RestClient.Builder` do Spring Boot cấu hình được dùng để giữ HTTP client observation. Gọi User Service tại 8082 với timeout mặc định 3 giây.
- User: `spring.application.name=user-service`, cổng 8082; endpoint trả dữ liệu in-memory.
- Mỗi `application.yml` đặt `management.tracing.sampling.probability: 1.0` và `management.tracing.propagation.type: w3c`.

Logback ở mỗi module in timestamp, thread, level, service name, MDC `traceId`, `spanId`, `requestId`, logger và message. Ba service có file rolling riêng dưới `logs/<service-name>/`, giữ nguyên chính sách bài tập 4 (10MB/file, 30 ngày, 1GB archive). Root app hiện tại giữ Console, Discord và file rolling; Logback pattern của root app cũng hỗ trợ trace/span khi MDC có các giá trị này. DiscordAppender chỉ thêm các field trace/span nếu event MDC có chúng.

Gateway sinh request ID ứng dụng mới ở reactive WebFilter, đặt nó vào header cho downstream và trả lại một giá trị header duy nhất dù downstream cũng trả `X-Request-Id`. Gateway tracing filter chạy trước route execution để context W3C được inject trước khi tạo request upstream. Order/User giữ request ID nhận được trong MDC bằng servlet filter và chỉ xóa khóa `requestId` trong `finally`; không gọi `MDC.clear()` và không xóa `traceId`/`spanId`.

## 15–16. Chạy và kiểm thử bằng Postman

Chạy từng lệnh ở ba terminal, từ thư mục gốc repository, theo thứ tự:

```powershell
mvn -f distributed-tracing\pom.xml -pl user-service spring-boot:run
```

```powershell
mvn -f distributed-tracing\pom.xml -pl order-service spring-boot:run
```

```powershell
mvn -f distributed-tracing\pom.xml -pl api-gateway spring-boot:run
```

Sau đó gửi:

```http
GET http://localhost:8080/api/orders/test-tracing
```

Response dự kiến:

```json
{
  "message": "Distributed tracing successful",
  "orderService": "order-service",
  "user": {
    "userId": 1001,
    "username": "storex-user",
    "status": "ACTIVE"
  }
}
```

Kiểm tra console và file của từng tiến trình (`logs/api-gateway/application.log`, `logs/order-service/application.log`, `logs/user-service/application.log`). Tìm log cùng request bằng `traceId`: trace ID phải giống; span ID thay đổi theo server span, HTTP client span và span nghiệp vụ. Mỗi request mới không gửi `traceparent` phải có trace ID mới.

Để kiểm tra tiếp nhận trace từ client, gửi header `traceparent` hợp lệ, ví dụ:

```text
00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
```

Sau đó tìm trace ID `4bf92f3577b34da6a3ce929d0e0e4736` trong log ba service. Bỏ header này khi cần kiểm tra hai request độc lập.

## 17. Phân biệt các ID

| Giá trị | Nguồn | Phạm vi |
|---|---|---|
| `requestId` | Gateway WebFilter tạo UUID và truyền `X-Request-Id` | Correlation của ứng dụng/demo |
| `traceId` | Micrometer/Brave extract hoặc tạo trace | Toàn bộ distributed trace |
| `spanId` | Micrometer/Brave tạo cho từng operation | Một span cụ thể |

`RequestIdFilter` cũ trong app đơn vẫn giữ nguyên; nó là filter riêng và không được đưa vào Gateway reactive chain.

## 18–19. Lỗi thường gặp và chẩn đoán

1. **User Service không hoạt động:** Order sẽ log lỗi trong trace hiện hành; trả HTTP 502 (`User Service is unavailable`). Kiểm tra User Service ở cổng 8082 và `USER_SERVICE_URL`.
2. **User Service timeout:** Order HTTP client có timeout; kiểm tra `USER_SERVICE_TIMEOUT` (mặc định `3s`) và log lỗi cùng trace ID.
3. **Order Service không hoạt động:** Gateway không thể kết nối upstream; xem status lỗi và log Gateway. Kiểm tra `ORDER_SERVICE_URL` (mặc định `http://localhost:8081`).
4. **Thiếu trace context đầu vào:** Micrometer khởi tạo trace mới tại Gateway. Điều này là bình thường.
5. **Hai request độc lập:** không gửi cùng `traceparent`; mỗi request được tạo trace mới.

Nếu trace ID không truyền tiếp:

- xác nhận cả ba module có `micrometer-tracing-bridge-brave` và Actuator;
- kiểm tra `management.tracing.propagation.type: w3c`;
- xác nhận Order inject `RestClient.Builder` do Boot cung cấp, không tự dựng builder không instrumented;
- xác nhận Gateway observability được bật (`spring.cloud.gateway.server.webflux.observability.enabled`, mặc định true);
- tìm `traceparent` ở outbound call và kiểm tra không có code ghi đè header;
- kiểm tra log pattern dùng đúng MDC keys `traceId` và `spanId`;
- không dùng `MDC.clear()`; filter chỉ xóa khóa riêng của nó.

## 20. Không cần Zipkin/Jaeger

Sampling, tạo span, extract/inject trace context và ghi MDC là chức năng trong process do Micrometer/Brave thực hiện. Không cần backend để tạo hoặc truyền trace. Zipkin/Jaeger/OTel Collector chỉ cần khi muốn lưu, truy vấn, hiển thị waterfall hoặc phân tích trace tập trung.

## Bảo toàn bài tập 1–4 và Production

Ứng dụng gốc vẫn chạy riêng bằng `mvn spring-boot:run`; không bị chuyển thành Gateway hay đổi stack. Actuator Dynamic Log Level, MDC filter, Discord Appender và rolling log tiếp tục giữ nguyên. Với các module mới, tracing dependency và cấu hình được thêm riêng.

Chạy bài test của ba service:

```powershell
mvn -f distributed-tracing\pom.xml test
```

Chạy test của ứng dụng gốc:

```powershell
mvn test
```

Trong Production, đặt sampling rate theo tải/chi phí, giữ trace/request IDs khỏi chứa dữ liệu nhạy cảm, bảo vệ endpoints quản trị, cấu hình timeout và giới hạn tài nguyên, cân nhắc collector/backend để có retention và tra cứu tập trung. Không dùng sampling `1.0` mặc định cho hệ thống lưu lượng cao nếu chưa đánh giá overhead.
