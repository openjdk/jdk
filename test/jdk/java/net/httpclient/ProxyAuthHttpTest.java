/*
 * @test
 * @bug 8328894
 * @summary HttpResponse.body() returns null with HTTPS target and failed proxy authentication
 * @library /test/lib
 * @run main/othervm ProxyAuthHttpTest
 */

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.util.concurrent.*;
import java.util.stream.*;
import com.sun.net.httpserver.HttpServer;

/**
 * This test reproduces JDK-8328894:
 * When a proxy requires authentication, HTTPS requests receive a 407 response
 * but HttpResponse.body() returns null, while HTTP requests return a proper body.
 *
 * This test verifies the fix by testing:
 * 1. Basic HTTP and HTTPS 407 responses with body
 * 2. Various BodyHandlers (ofString, ofByteArray, ofInputStream, ofLines)
 * 3. Response headers (Content-Type, Content-Length, status code)
 * 4. Body content correctness
 */
public class ProxyAuthHttpTest {

    static HttpServer targetServer;
    static ServerSocket proxySocket;
    static volatile boolean stop = false;

    static final String EXPECTED_BODY = "<html><body>Proxy Authentication Required</body></html>";
    static int testsPassed = 0;
    static int testsFailed = 0;

    public static void main(String[] args) throws Exception {
        // 1. Start dummy target HTTP server (never actually reached)
        targetServer = HttpServer.create(new InetSocketAddress(0), 0);
        targetServer.createContext("/", exchange -> {
            byte[] body = "OK".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        targetServer.start();

        // 2. Start simple proxy that always responds 407
        proxySocket = new ServerSocket(0);
        Thread proxyThread = new Thread(ProxyAuthHttpTest::runProxy, "proxy-thread");
        proxyThread.setDaemon(true);
        proxyThread.start();

        int proxyPort = proxySocket.getLocalPort();
        System.out.println("Proxy running on port " + proxyPort);

        // 3. Build HttpClient using this proxy
        InetSocketAddress proxyAddr = new InetSocketAddress("127.0.0.1", proxyPort);
        HttpClient client = HttpClient.newBuilder()
                .proxy(ProxySelector.of(proxyAddr))
                .build();

        try {
            // 4. Run comprehensive tests
            System.out.println("\n=== Testing HTTP 407 responses ===");
            testBasicResponse(client, "http://example.invalid/");
            testBodyHandlerString(client, "http://example.invalid/");
            testBodyHandlerByteArray(client, "http://example.invalid/");
            testBodyHandlerInputStream(client, "http://example.invalid/");
            testBodyHandlerLines(client, "http://example.invalid/");
            testResponseHeaders(client, "http://example.invalid/");

            System.out.println("\n=== Testing HTTPS 407 responses ===");
            testBasicResponse(client, "https://example.invalid/");
            testBodyHandlerString(client, "https://example.invalid/");
            testBodyHandlerByteArray(client, "https://example.invalid/");
            testBodyHandlerInputStream(client, "https://example.invalid/");
            testBodyHandlerLines(client, "https://example.invalid/");
            testResponseHeaders(client, "https://example.invalid/");

            // 5. Print summary
            System.out.println("\n=== Test Summary ===");
            System.out.println("Passed: " + testsPassed);
            System.out.println("Failed: " + testsFailed);

            if (testsFailed > 0) {
                throw new RuntimeException(testsFailed + " test(s) failed");
            }
        } finally {
            stop = true;
            proxySocket.close();
            targetServer.stop(0);
        }
    }

    private static void runProxy() {
        try {
            while (!stop) {
                Socket s = proxySocket.accept();
                new Thread(() -> handleProxyConnection(s)).start();
            }
        } catch (IOException ignored) {}
    }

    private static void handleProxyConnection(Socket s) {
        try (Socket socket = s;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             OutputStream out = socket.getOutputStream()) {

            String firstLine = in.readLine();
            if (firstLine == null) return;

            System.out.println("Proxy received: " + firstLine);

            String body = "<html><body>Proxy Authentication Required</body></html>";
            byte[] bytes = body.getBytes();

            String resp = "HTTP/1.1 407 Proxy Authentication Required\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "Proxy-Authenticate: Basic realm=\"Proxy\"\r\n" +
                    "Content-Length: " + bytes.length + "\r\n" +
                    "\r\n";

            out.write(resp.getBytes());
            out.write(bytes);
            out.flush();
        } catch (IOException ignored) {}
    }

    // Test helper
    private static void test(String testName, boolean passed, String message) {
        if (passed) {
            System.out.println("  [PASS] " + testName);
            testsPassed++;
        } else {
            System.out.println("  [FAIL] " + testName + ": " + message);
            testsFailed++;
        }
    }

    // Test 1: Basic response body not null
    private static void testBasicResponse(HttpClient client, String url) throws Exception {
        System.out.println("\nTest: Basic response (" + url + ")");
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

        test("Status code is 407", res.statusCode() == 407,
             "Expected 407, got " + res.statusCode());
        test("Body is not null", res.body() != null,
             "Body is null");
        test("Body matches expected", EXPECTED_BODY.equals(res.body()),
             "Expected '" + EXPECTED_BODY + "', got '" + res.body() + "'");
    }

    // Test 2: BodyHandlers.ofString()
    private static void testBodyHandlerString(HttpClient client, String url) throws Exception {
        System.out.println("\nTest: BodyHandler.ofString() (" + url + ")");
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

        test("Body is not null", res.body() != null, "Body is null");
        test("Body is String type", res.body() instanceof String, "Wrong type");
        test("Body content correct", EXPECTED_BODY.equals(res.body()),
             "Content mismatch");
    }

    // Test 3: BodyHandlers.ofByteArray()
    private static void testBodyHandlerByteArray(HttpClient client, String url) throws Exception {
        System.out.println("\nTest: BodyHandler.ofByteArray() (" + url + ")");
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<byte[]> res = client.send(req, HttpResponse.BodyHandlers.ofByteArray());

        test("Body is not null", res.body() != null, "Body is null");
        test("Body length correct", res.body().length == EXPECTED_BODY.length(),
             "Expected length " + EXPECTED_BODY.length() + ", got " + res.body().length);
        test("Body content correct", EXPECTED_BODY.equals(new String(res.body())),
             "Content mismatch");
    }

    // Test 4: BodyHandlers.ofInputStream()
    private static void testBodyHandlerInputStream(HttpClient client, String url) throws Exception {
        System.out.println("\nTest: BodyHandler.ofInputStream() (" + url + ")");
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());

        test("Body is not null", res.body() != null, "Body is null");

        try (InputStream is = res.body()) {
            String content = new String(is.readAllBytes());
            test("Body content correct", EXPECTED_BODY.equals(content),
                 "Content mismatch");
        }
    }

    // Test 5: BodyHandlers.ofLines()
    private static void testBodyHandlerLines(HttpClient client, String url) throws Exception {
        System.out.println("\nTest: BodyHandler.ofLines() (" + url + ")");
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<Stream<String>> res = client.send(req, HttpResponse.BodyHandlers.ofLines());

        test("Body is not null", res.body() != null, "Body is null");

        String content = res.body().collect(Collectors.joining());
        test("Body content correct", EXPECTED_BODY.equals(content),
             "Content mismatch");
    }

    // Test 6: Response headers
    private static void testResponseHeaders(HttpClient client, String url) throws Exception {
        System.out.println("\nTest: Response headers (" + url + ")");
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

        test("Status code is 407", res.statusCode() == 407,
             "Expected 407, got " + res.statusCode());

        var headers = res.headers();
        test("Has Content-Type header",
             headers.firstValue("Content-Type").isPresent(),
             "Content-Type header missing");
        test("Content-Type is text/html",
             headers.firstValue("Content-Type").orElse("").contains("text/html"),
             "Wrong Content-Type: " + headers.firstValue("Content-Type").orElse(""));
        test("Has Content-Length header",
             headers.firstValue("Content-Length").isPresent(),
             "Content-Length header missing");
        test("Content-Length is correct",
             headers.firstValue("Content-Length").orElse("").equals(String.valueOf(EXPECTED_BODY.length())),
             "Wrong Content-Length: " + headers.firstValue("Content-Length").orElse(""));
        test("Has Proxy-Authenticate header",
             headers.firstValue("Proxy-Authenticate").isPresent(),
             "Proxy-Authenticate header missing");
    }
}
