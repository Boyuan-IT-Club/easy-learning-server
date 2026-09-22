package com.earlylearning.early_learning_server.storage;

import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.aliyun.oss.OSSException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 使用真实 SDK 和本地 HTTP 服务检查请求及响应兼容性，不访问阿里云。 */
class OssSdkIntegrationTests {

    @Test
    void realSdkUploadsDownloadsDeletesAndParsesAccessDenied() throws Exception {
        var stored = new AtomicReference<byte[]>();
        var contentType = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                exchange.getResponseHeaders().set("x-oss-request-id", "test-request");
                if (exchange.getRequestURI().getPath().endsWith("/denied")) {
                    byte[] error = ("<Error><Code>AccessDenied</Code><Message>Access denied</Message>"
                            + "<RequestId>test-request</RequestId></Error>").getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/xml");
                    exchange.getRequestBody().readAllBytes();
                    exchange.sendResponseHeaders(403, error.length);
                    exchange.getResponseBody().write(error);
                    return;
                }
                switch (exchange.getRequestMethod()) {
                    case "PUT" -> {
                        stored.set(exchange.getRequestBody().readAllBytes());
                        contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                        exchange.sendResponseHeaders(200, -1);
                    }
                    case "GET" -> {
                        byte[] data = stored.get();
                        if (data == null) {
                            exchange.sendResponseHeaders(404, -1);
                        } else {
                            exchange.sendResponseHeaders(200, data.length);
                            exchange.getResponseBody().write(data);
                        }
                    }
                    case "DELETE" -> {
                        stored.set(null);
                        exchange.sendResponseHeaders(204, -1);
                    }
                    default -> exchange.sendResponseHeaders(405, -1);
                }
            }
        });
        server.start();
        try {
            var properties = new OssProperties("http://127.0.0.1:" + server.getAddress().getPort(),
                    "cn-hangzhou", "test-bucket", "test-id", "test-secret", 900);
            var client = new OssConfig().ossClient(properties);
            try {
                ObjectStorageService service = new OssObjectStorageService(client, properties);
                byte[] content = "测试文件内容".getBytes(StandardCharsets.UTF_8);
                try (var input = new ByteArrayInputStream(content)) {
                    service.upload("图片/a + b.png", input, content.length, "image/png");
                }
                assertArrayEquals(content, stored.get());
                assertEquals("image/png", contentType.get());
                var connection = service.generateDownloadUrl("图片/a + b.png").toURL().openConnection();
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                try (var downloaded = connection.getInputStream()) {
                    assertArrayEquals(content, downloaded.readAllBytes());
                }
                service.delete("图片/a + b.png");
                service.delete("图片/a + b.png");
                assertNull(stored.get());

                var failure = assertThrows(IllegalStateException.class, () -> service.delete("denied"));
                var cause = assertInstanceOf(OSSException.class, failure.getCause());
                assertEquals("AccessDenied", cause.getErrorCode());
            } finally {
                client.shutdown();
            }
        } finally {
            server.stop(0);
        }
    }
}
