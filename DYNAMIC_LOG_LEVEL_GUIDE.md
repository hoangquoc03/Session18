# Hướng dẫn Dynamic Log Level với Spring Boot Actuator

## 1. Mục tiêu

Ứng dụng minh họa cách xem và thay đổi log level của package `com.storex.payment` tại runtime bằng Spring Boot Actuator, không cần khởi động lại ứng dụng.

## 2. File được tạo

- `pom.xml`: khai báo Spring Boot 3.5.7, Java 21, Spring Web, Actuator và test starter.
- `src/main/java/com/storex/payment/DynamicLogLevelApplication.java`: điểm khởi chạy ứng dụng.
- `src/main/java/com/storex/payment/LogTestController.java`: API phát sinh log TRACE, DEBUG, INFO, WARN và ERROR.
- `src/main/resources/application.yml`: expose endpoint Actuator cần thiết.
- `src/test/java/com/storex/payment/LogTestControllerTest.java`: kiểm thử API log.
- `DYNAMIC_LOG_LEVEL_GUIDE.md`: tài liệu hướng dẫn này.

## 3. Spring Boot Actuator là gì?

Spring Boot Actuator cung cấp các endpoint giám sát và quản trị ứng dụng, chẳng hạn trạng thái health, thông tin ứng dụng và cấu hình logger. Endpoint `loggers` cho phép xem hoặc đổi log level khi ứng dụng đang chạy.

## 4. Dynamic Log Level hoạt động như thế nào?

Ứng dụng dùng SLF4J và Logback mặc định của Spring Boot. Actuator nhận yêu cầu POST tới `/actuator/loggers/{logger-name}` và cập nhật mức log cho logger trong bộ nhớ của tiến trình hiện tại. Thay đổi có hiệu lực ngay lập tức, không sửa file cấu hình và không restart; khi ứng dụng được khởi động lại, log level động trở về cấu hình ban đầu (trừ khi có cơ chế cấu hình bền vững riêng).

## 5. Giải thích `application.yml`

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,loggers
  endpoint:
    loggers:
      enabled: true
```

- `exposure.include` công khai các endpoint `health`, `info` và `loggers` qua HTTP.
- `endpoint.loggers.enabled` bật endpoint quản lý logger.
- Khi chưa cấu hình log level riêng cho `com.storex.payment`, mức hiệu lực mặc định là `INFO`.

> Bài tập này expose endpoint quản trị để kiểm thử trên máy local. Trong môi trường thật, cần giới hạn truy cập mạng và bảo vệ endpoint bằng Spring Security hoặc cơ chế xác thực phù hợp.

## 6. Chạy ứng dụng

Yêu cầu JDK 21 và Maven 3.9 trở lên. Từ thư mục gốc project, chạy:

```powershell
mvn spring-boot:run
```

Ứng dụng chạy tại `http://localhost:8080`.

## 7–8. Kiểm thử bằng Postman và kết quả mong đợi

### Request 1: Kiểm tra Actuator

- Method: `GET`
- URL: `http://localhost:8080/actuator/health`
- Kết quả: HTTP 200, JSON có trạng thái `UP`, ví dụ:

```json
{"status":"UP"}
```

### Request 2: Xem log level hiện tại

- Method: `GET`
- URL: `http://localhost:8080/actuator/loggers/com.storex.payment`
- Kết quả ban đầu thường có `configuredLevel: null` và `effectiveLevel: "INFO"`:

```json
{
  "configuredLevel": null,
  "effectiveLevel": "INFO"
}
```

### Request 3: Đổi log level sang DEBUG

- Method: `POST`
- URL: `http://localhost:8080/actuator/loggers/com.storex.payment`
- Header: `Content-Type: application/json`
- Body chọn **raw** và **JSON**:

```json
{
  "configuredLevel": "DEBUG"
}
```

- Kết quả: HTTP 204 No Content.

### Request 4: Phát sinh log ở các cấp

- Method: `GET`
- URL: `http://localhost:8080/api/test-logs`
- Kết quả: HTTP 200:

```json
{
  "message": "Logs emitted at TRACE, DEBUG, INFO, WARN, and ERROR levels."
}
```

Quan sát console nơi chạy ứng dụng: DEBUG, INFO, WARN và ERROR xuất hiện. TRACE không xuất hiện với cấu hình DEBUG vì TRACE có độ chi tiết thấp hơn DEBUG; gửi request này vẫn thực hiện lời gọi logger ở đủ cả năm cấp.

### Request 5: Xác nhận log level đã đổi

- Method: `GET`
- URL: `http://localhost:8080/actuator/loggers/com.storex.payment`
- Kết quả:

```json
{
  "configuredLevel": "DEBUG",
  "effectiveLevel": "DEBUG"
}
```

## 9. Đổi DEBUG về INFO

Gửi lại POST tới `http://localhost:8080/actuator/loggers/com.storex.payment`, giữ header `Content-Type: application/json`, đổi body thành:

```json
{
  "configuredLevel": "INFO"
}
```

Sau đó GET endpoint logger để xác nhận `configuredLevel` và `effectiveLevel` là `INFO`.

## 10. Vì sao không cần restart?

Actuator gọi trực tiếp logging system của ứng dụng và cập nhật logger đang tồn tại trong bộ nhớ. Các log phát sinh sau yêu cầu POST sử dụng mức mới ngay lập tức. Đây là thay đổi runtime, không phải chỉnh sửa `application.yml`; vì vậy nó không yêu cầu restart và cũng không tự được lưu bền qua lần restart tiếp theo.
