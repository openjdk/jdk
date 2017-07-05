/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * @test
 * @bug 8087112
 * @library /lib/testlibrary/ /
 * @build jdk.testlibrary.SimpleSSLContext ProxyServer EchoHandler
 * @compile ../../../com/sun/net/httpserver/LogFilter.java
 * @compile ../../../com/sun/net/httpserver/FileServerHandler.java
 * @run main/othervm SmokeTest
 */

import com.sun.net.httpserver.*;
import java.net.*;
import java.net.http.*;
import java.io.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import java.nio.file.*;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import jdk.testlibrary.SimpleSSLContext;
import static java.net.http.HttpRequest.*;
import static java.net.http.HttpResponse.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * * Basic smoke test for Http/1.1 client
 * - basic request response
 * - request body POST
 * - response body GET
 * - redirect
 * - chunked request/response
 * - SSL
 * - proxies
 * - 100 continue
 * - check keep alive appears to be working
 * - cancel of long request
 *
 * Uses a FileServerHandler serving a couple of known files
 * in docs directory.
 */
public class SmokeTest {
    static SSLContext ctx;
    static SSLParameters sslparams;
    static HttpServer s1 ;
    static HttpsServer s2;
    static ExecutorService executor;
    static int port;
    static int httpsport;
    static String httproot;
    static String httpsroot;
    static HttpClient client;
    static ProxyServer proxy;
    static int proxyPort;
    static RedirectErrorHandler redirectErrorHandler, redirectErrorHandlerSecure;
    static RedirectHandler redirectHandler, redirectHandlerSecure;
    static DelayHandler delayHandler;
    final static String midSizedFilename = "/files/notsobigfile.txt";
    final static String smallFilename = "/files/smallfile.txt";
    static Path midSizedFile;
    static Path smallFile;
    static String fileroot;

    static String getFileContent(String path) throws IOException {
        FileInputStream fis = new FileInputStream(path);
        byte[] buf = new byte[2000];
        StringBuilder sb = new StringBuilder();
        int n;
        while ((n=fis.read(buf)) != -1) {
            sb.append(new String(buf, 0, n, "US-ASCII"));
        }
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        initServer();
        fileroot = System.getProperty ("test.src", ".")+ "/docs";
        midSizedFile = Paths.get(fileroot + midSizedFilename);
        smallFile = Paths.get(fileroot + smallFilename);

        client = HttpClient.create()
                           .sslContext(ctx)
                           .sslParameters(sslparams)
                           .followRedirects(HttpClient.Redirect.ALWAYS)
                           .executorService(Executors.newCachedThreadPool())
                           .build();

        try {
            test1(httproot + "files/foo.txt", true);

            test1(httproot + "files/foo.txt", false);
            test1(httpsroot + "files/foo.txt", true);
            test1(httpsroot + "files/foo.txt", false);
            test2(httproot + "echo/foo", "This is a short test");
            test2(httpsroot + "echo/foo", "This is a short test");

            test3(httproot + "redirect/foo.txt");
            test3(httpsroot + "redirect/foo.txt");
            test4(httproot + "files/foo.txt");
            test4(httpsroot + "files/foo.txt");
            test5(httproot + "echo/foo", true);
            test5(httpsroot + "echo/foo", true);
            test5(httproot + "echo/foo", false);
            test5(httpsroot + "echo/foo", false);

            test6(httproot + "echo/foo", true);
            test6(httpsroot + "echo/foo", true);
            test6(httproot + "echo/foo", false);
            test6(httpsroot + "echo/foo", false);

            test7(httproot + "keepalive/foo");

            test8(httproot + "files/foo.txt", true);
            test8(httproot + "files/foo.txt", false);
            test8(httpsroot + "files/foo.txt", true);
            test8(httpsroot + "files/foo.txt", false);
            // disabled test9();

            test10(httproot + "redirecterror/foo.txt");

            test10(httpsroot + "redirecterror/foo.txt");

            test11(httproot + "echo/foo");
            test11(httpsroot + "echo/foo");
            //test12(httproot + "delay/foo", delayHandler);

        } finally {
            s1.stop(0);
            s2.stop(0);
            proxy.close();
            executor.shutdownNow();
            client.executorService().shutdownNow();
        }
    }

    static class Auth extends java.net.Authenticator {
        volatile int count = 0;
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            if (count++ == 0) {
                return new PasswordAuthentication("user", "passwd".toCharArray());
            } else {
                return new PasswordAuthentication("user", "goober".toCharArray());
            }
        }
        int count() {
            return count;
        }
    }

    // Basic test
    static void test1(String target, boolean fixedLen) throws Exception {
        System.out.print("test1: " + target);
        URI uri = new URI(target);

        HttpRequest.Builder builder = client.request(uri)
                                             .body(noBody());

        if (fixedLen) {
            builder.header("XFixed", "yes");
        }

        HttpResponse response = builder.GET().response();

        String body = response.body(asString());
        if (!body.equals("This is foo.txt\r\n")) {
            throw new RuntimeException();
        }

        // repeat async
        response = builder.GET().responseAsync().join();

        body = response.body(asString());
        if (!body.equals("This is foo.txt\r\n")) {
            throw new RuntimeException();
        }
        System.out.println(" OK");
    }

    // POST use echo to check reply
    static void test2(String s, String body) throws Exception {
        System.out.print("test2: " + s);
        URI uri = new URI(s);

        HttpResponse response = client.request(uri)
                                       .body(fromString(body))
                                       .POST()
                                       .response();

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                "Expected 200, got [ " + response.statusCode() + " ]");
        }
        String reply = response.body(asString());
        if (!reply.equals(body)) {
            throw new RuntimeException(
                "Body mismatch: expected [" + body + "], got [" + reply + "]");
        }
        System.out.println(" OK");
    }

    // Redirect
    static void test3(String s) throws Exception {
        System.out.print("test3: " + s);
        URI uri = new URI(s);
        RedirectHandler handler = uri.getScheme().equals("https")
                ? redirectHandlerSecure : redirectHandler;

        HttpResponse response = client.request(uri)
                                      .body(noBody())
                                      .GET()
                                      .response();

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                "Expected 200, got [ " + response.statusCode() + " ]");
        } else {
            response.body(HttpResponse.asFile(Paths.get("redir1.txt")));
        }

        Path downloaded = Paths.get("redir1.txt");
        if (Files.size(downloaded) != Files.size(midSizedFile)) {
            throw new RuntimeException("Size mismatch");
        }

        System.out.printf(" (count: %d) ", handler.count());
        // repeat with async api

        handler.reset();

        response = client.request(uri)
                         .body(noBody())
                         .GET()
                         .responseAsync()
                         .join();

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Expected 200, got [ " + response.statusCode() + " ]");
        } else {
            response.body(HttpResponse.asFile(Paths.get("redir2.txt")));
        }

        downloaded = Paths.get("redir2.txt");
        if (Files.size(downloaded) != Files.size(midSizedFile)) {
            throw new RuntimeException("Size mismatch 2");
        }
        System.out.printf(" (count: %d) ", handler.count());
        System.out.println(" OK");
    }

    // Proxies
    static void test4(String s) throws Exception {
        System.out.print("test4: " + s);
        URI uri = new URI(s);
        InetSocketAddress proxyAddr = new InetSocketAddress("127.0.0.1", proxyPort);
        String filename = fileroot + uri.getPath();

        HttpClient cl = HttpClient.create()
                                  .proxy(ProxySelector.of(proxyAddr))
                                  .sslContext(ctx)
                                  .sslParameters(sslparams)
                                  .build();

        CompletableFuture<String> fut = cl.request(uri)
                                          .body(noBody())
                                          .GET()
                                          .responseAsync()
                                          .thenCompose((HttpResponse response) ->
                                                          response.bodyAsync(asString())
                                          );

        String body = fut.get(5, TimeUnit.HOURS);

        String fc = getFileContent(filename);

        if (!body.equals(fc)) {
            throw new RuntimeException(
                    "Body mismatch: expected [" + body + "], got [" + fc + "]");
        }
        cl.executorService().shutdownNow();
        System.out.println(" OK");
    }

    // 100 Continue: use echo target
    static void test5(String target, boolean fixedLen) throws Exception {
        System.out.print("test5: " + target);
        URI uri = new URI(target);
        String requestBody = generateString(12 * 1024 + 13);

        HttpRequest.Builder builder = client.request(uri)
                                            .expectContinue(true)
                                            .body(fromString(requestBody));

        if (fixedLen) {
            builder.header("XFixed", "yes");
        }

        HttpResponse response = builder.GET().response();

        String body = response.body(asString());

        if (!body.equals(requestBody)) {
            throw new RuntimeException(
                    "Body mismatch: expected [" + body + "], got [" + body + "]");
        }
        System.out.println(" OK");
    }

    // use echo
    static void test6(String target, boolean fixedLen) throws Exception {
        System.out.print("test6: " + target);
        URI uri = new URI(target);
        String requestBody = generateString(12 * 1024 + 3);

        HttpRequest.Builder builder = client.request(uri)
                                            .body(noBody());

        if (fixedLen) {
            builder.header("XFixed", "yes");
        }

        HttpResponse response = builder.GET().response();

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Expected 200, got [ " + response.statusCode() + " ]");
        }

        String responseBody = response.body(asString());

        if (responseBody.equals(requestBody)) {
            throw new RuntimeException(
                    "Body mismatch: expected [" + requestBody + "], got [" + responseBody + "]");
        }
        System.out.println(" OK");
    }

    @SuppressWarnings("rawtypes")
    static void test7(String target) throws Exception {
        System.out.print("test7: " + target);

        // First test
        URI uri = new URI(target);
        for (int i=0; i<4; i++) {
            HttpResponse r = client.request(uri)
                                   .body(noBody())
                                   .GET()
                                   .response();
            String body = r.body(asString());
            if (!body.equals("OK")) {
                throw new RuntimeException("Expected OK, got: " + body);
            }
        }

        // Second test: 4 x parallel
        List<CompletableFuture<HttpResponse>> futures = new LinkedList<>();
        for (int i=0; i<4; i++) {
            futures.add(client.request(uri)
                              .body(noBody())
                              .GET()
                              .responseAsync());
        }
        // all sent?
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                         .join();

        List<CompletableFuture<String>> futureBodies = new LinkedList<>();
        for (int i=0; i<4; i++) {
            futureBodies.add(futures.get(i)
                                    .join()
                                    .bodyAsync(asString()));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                         .join();

        for (CompletableFuture<String> future : futureBodies) {
            String body = future.get();
            if (!body.equals("OK")) {
                throw new RuntimeException("Expected OK, got: " + body);
            }
        }

        // Third test: Multiple of 4 parallel requests
        BlockingQueue<String> q = new LinkedBlockingQueue<>();
        for (int i=0; i<4; i++) {
            client.request(uri)
                  .body(noBody())
                  .GET()
                  .responseAsync()
                  .thenApply((HttpResponse resp) -> {
                      String body = resp.body(asString());
                      putQ(q, body);
                      return body;
                  });
        }
        // we've sent four requests. Now, just send another request
        // as each response is received. The idea is to ensure that
        // only four sockets ever get used.

        for (int i=0; i<100; i++) {
            // block until response received
            String body = takeQ(q);
            if (!body.equals("OK")) {
                throw new RuntimeException(body);
            }
            client.request(uri)
                  .body(noBody())
                  .GET()
                  .responseAsync()
                  .thenApply((HttpResponse resp) -> {
                      String body1 = resp.body(asString());
                      putQ(q, body1);
                      return body1;
                  });
        }
        // should be four left
        for (int i=0; i<4; i++) {
            takeQ(q);
        }
        System.out.println(" OK");
    }

    static String takeQ(BlockingQueue<String> q) {
        String r = null;
        try {
            r = q.take();
        } catch (InterruptedException e) {}

        return r;
    }

    static void putQ(BlockingQueue<String> q, String o) {
        try {
            q.put(o);
        } catch (InterruptedException e) {
            // can't happen
        }
    }

    static void test8(String target, boolean fixedLen) throws Exception {
        System.out.print("test8: " + target);
        URI uri = new URI(target);

        HttpRequest.Builder builder = client.request(uri)
                                            .body(noBody());

        if (fixedLen) {
            builder.header("XFixed", "yes");
        }

        HttpResponse response = builder.GET().response();

        StringBuilder sb = new StringBuilder();

        InputStream is = response.body(asInputStream());
        int c;
        byte[] buf = new byte[2048];
        while ((c = is.read(buf)) != -1) {
            for (int i=0; i<c; i++)
                sb.append((char)buf[i]);
        }
        is.close();
        String body = sb.toString();

        if (!body.equals("This is foo.txt\r\n")) {
            throw new RuntimeException("Expected \"This is foo.txt\", got: " + body);
        }
        System.out.println(" OK");
    }

    // Chunked output stream
    static void test11(String target) throws Exception {
        System.out.print("test11: " + target);
        URI uri = new URI(target);

        FileInputStream file = new FileInputStream(smallFile.toFile());

        HttpRequest.Builder builder = client.request(uri)
                                            .body(HttpRequest.fromInputStream(file));
        HttpResponse response = builder.POST().response();

        if (response.statusCode() != 200) {
            throw new RuntimeException("Wrong response code");
        }

        Path download = Paths.get("test11.txt");
        download.toFile().delete();
        response.body(HttpResponse.asFile(download));

        if (Files.size(download) != Files.size(smallFile)) {
            System.out.println("Original size: " + Files.size(smallFile));
            System.out.println("Downloaded size: " + Files.size(download));
            throw new RuntimeException("Size mismatch");
        }
        System.out.println(" OK");
    }

    // cancel
    /*
    static void test12(String target, DelayHandler h) throws Exception {
        System.out.print("test12: " + target);
        URI uri = new URI(target);

        HttpRequest.Builder builder = client
            .request(uri)
            .body(HttpRequest.fromString("Hello world"));

        HttpRequest request = builder
                .GET();
        request.sendAsync();
        h.barrier1().await();
        // request has been processed
        CompletableFuture<HttpResponse> cf = request.responseAsync();
        request.cancel();
        h.barrier2().await();
        try {
            HttpResponse r = cf.get();
            throw new RuntimeException("failed 2");
        } catch (Exception e) {
        }
        System.out.println(" OK");
    }
*/
    static void delay(int seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
        }
    }
/*
    // test won't work until sending fully decoupled from receiving in impl
    static void test9() throws Exception {
        System.out.print("test9: ");
        UploadServer up = new UploadServer(1000 * 1000);
        int size = up.size();
        String u = "http://127.0.0.1:" + up.port() + "/";
        URI uri = new URI(u);

        HttpRequest request = client
            .request(uri)
            .body(new HttpRequestBodyProcessor() {
                @Override
                public ByteBuffer onRequestBodyChunk(ByteBuffer b) throws IOException {
                    // slow things down
                    delay(1);
                    b.position(b.limit()); // fill it
                    return b;
                }
                @Override
                public long onRequestStart(HttpRequest req) throws IOException {
                    return size;
                }
             })
            .PUT();

        CompletableFuture<HttpRequest> cf1 = request.sendAsync();
        CompletableFuture<HttpResponse> cf = request.responseAsync();

        HttpResponse resp = cf.get(1, TimeUnit.MINUTES);
        if (resp.statusCode() != 201) {
            throw new RuntimeException("failed: wrong response code");
        }
        delay(2); // allow some data to be sent
        request.cancel();
        delay(1);
        if (up.failed()) {
            throw new RuntimeException("failed to cancel request");
        }
        System.out.println(" OK");
    }
  */
    // Redirect loop: return an error after a certain number of redirects
    static void test10(String s) throws Exception {
        System.out.print("test10: " + s);
        URI uri = new URI(s);
        RedirectErrorHandler handler = uri.getScheme().equals("https")
                ? redirectErrorHandlerSecure : redirectErrorHandler;

        CompletableFuture<HttpResponse> cf = client.request(uri)
                                                   .body(noBody())
                                                   .GET()
                                                   .responseAsync();

        try {
            HttpResponse response = cf.join();
            throw new RuntimeException("Exepected Completion Exception");
        } catch (CompletionException e) {
            //System.out.println(e);
        }

        System.out.printf(" (Calls %d) ", handler.count());
        System.out.println(" OK");
    }

    static final int NUM = 50;

    static Random random = new Random();
    static final String alphabet = "ABCDEFGHIJKLMNOPQRST";

    static char randomChar() {
        return alphabet.charAt(random.nextInt(alphabet.length()));
    }

    static String generateString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i=0; i<length; i++) {
            sb.append(randomChar());
        }
        return sb.toString();
    }

    static void initServer() throws Exception {
        Logger logger = Logger.getLogger("com.sun.net.httpserver");
        ConsoleHandler ch = new ConsoleHandler();
        logger.setLevel(Level.ALL);
        ch.setLevel(Level.ALL);
        logger.addHandler(ch);

        String root = System.getProperty ("test.src")+ "/docs";
        InetSocketAddress addr = new InetSocketAddress (0);
        s1 = HttpServer.create (addr, 0);
        if (s1 instanceof HttpsServer) {
            throw new RuntimeException ("should not be httpsserver");
        }
        s2 = HttpsServer.create (addr, 0);
        HttpHandler h = new FileServerHandler(root);

        HttpContext c1 = s1.createContext("/files", h);
        HttpContext c2 = s2.createContext("/files", h);
        HttpContext c3 = s1.createContext("/echo", new EchoHandler());
        redirectHandler = new RedirectHandler("/redirect");
        redirectHandlerSecure = new RedirectHandler("/redirect");
        HttpContext c4 = s1.createContext("/redirect", redirectHandler);
        HttpContext c41 = s2.createContext("/redirect", redirectHandlerSecure);
        HttpContext c5 = s2.createContext("/echo", new EchoHandler());
        HttpContext c6 = s1.createContext("/keepalive", new KeepAliveHandler());
        redirectErrorHandler = new RedirectErrorHandler("/redirecterror");
        redirectErrorHandlerSecure = new RedirectErrorHandler("/redirecterror");
        HttpContext c7 = s1.createContext("/redirecterror", redirectErrorHandler);
        HttpContext c71 = s2.createContext("/redirecterror", redirectErrorHandlerSecure);
        delayHandler = new DelayHandler();
        HttpContext c8 = s1.createContext("/delay", delayHandler);
        HttpContext c81 = s2.createContext("/delay", delayHandler);

        executor = Executors.newCachedThreadPool();
        s1.setExecutor(executor);
        s2.setExecutor(executor);
        ctx = new SimpleSSLContext().get();
        sslparams = ctx.getSupportedSSLParameters();
        s2.setHttpsConfigurator(new Configurator(ctx));
        s1.start();
        s2.start();

        port = s1.getAddress().getPort();
        System.out.println("HTTP server port = " + port);
        httpsport = s2.getAddress().getPort();
        System.out.println("HTTPS server port = " + httpsport);
        httproot = "http://127.0.0.1:" + port + "/";
        httpsroot = "https://127.0.0.1:" + httpsport + "/";

        proxy = new ProxyServer(0, false);
        proxyPort = proxy.getPort();
        System.out.println("Proxy port = " + proxyPort);
    }
}

class Configurator extends HttpsConfigurator {
    public Configurator(SSLContext ctx) {
        super(ctx);
    }

    public void configure (HttpsParameters params) {
        params.setSSLParameters (getSSLContext().getSupportedSSLParameters());
    }
}

class UploadServer extends Thread {
    int statusCode;
    ServerSocket ss;
    int port;
    int size;
    Object lock;
    boolean failed = false;

    UploadServer(int size) throws IOException {
        this.statusCode = statusCode;
        this.size = size;
        ss = new ServerSocket(0);
        port = ss.getLocalPort();
        lock = new Object();
    }

    int port() {
          return port;
    }

    int size() {
          return size;
    }

    // wait a sec before calling this
    boolean failed() {
        synchronized(lock) {
            return failed;
        }
    }

    @Override
    public void run () {
        int nbytes = 0;
        Socket s = null;

        synchronized(lock) {
            try {
                s = ss.accept();

                InputStream is = s.getInputStream();
                OutputStream os = s.getOutputStream();
                os.write("HTTP/1.1 201 OK\r\nContent-length: 0\r\n\r\n".getBytes());
                int n;
                byte[] buf = new byte[8000];
                while ((n=is.read(buf)) != -1) {
                    nbytes += n;
                }
            } catch (IOException e) {
                System.out.println ("read " + nbytes);
                System.out.println ("size " + size);
                failed = nbytes >= size;
            } finally {
                try {
                    ss.close();
                    if (s != null)
                        s.close();
                } catch (IOException e) {}
            }
        }
    }
}

class RedirectHandler implements HttpHandler {
    String root;
    volatile int count = 0;

    RedirectHandler(String root) {
        this.root = root;
    }

    @Override
    public synchronized void handle(HttpExchange t)
        throws IOException
    {
        byte[] buf = new byte[2048];
        try (InputStream is = t.getRequestBody()) {
            while (is.read(buf) != -1) ;
        }

        Headers responseHeaders = t.getResponseHeaders();

        if (count++ < 1) {
            responseHeaders.add("Location", root + "/foo/" + count);
        } else {
            responseHeaders.add("Location", SmokeTest.midSizedFilename);
        }
        t.sendResponseHeaders(301, -1);
        t.close();
    }

    int count() {
        return count;
    }

    void reset() {
        count = 0;
    }
}

class RedirectErrorHandler implements HttpHandler {
    String root;
    volatile int count = 1;

    RedirectErrorHandler(String root) {
        this.root = root;
    }

    synchronized int count() {
        return count;
    }

    synchronized void increment() {
        count++;
    }

    @Override
    public synchronized void handle (HttpExchange t)
        throws IOException
    {
        byte[] buf = new byte[2048];
        try (InputStream is = t.getRequestBody()) {
            while (is.read(buf) != -1) ;
        }

        Headers map = t.getResponseHeaders();
        String redirect = root + "/foo/" + Integer.toString(count);
        increment();
        map.add("Location", redirect);
        t.sendResponseHeaders(301, -1);
        t.close();
    }
}

class Util {
    static byte[] readAll(InputStream is) throws IOException {
        byte[] buf = new byte[1024];
        byte[] result = new byte[0];

        while (true) {
            int n = is.read(buf);
            if (n > 0) {
                byte[] b1 = new byte[result.length + n];
                System.arraycopy(result, 0, b1, 0, result.length);
                System.arraycopy(buf, 0, b1, result.length, n);
                result = b1;
            } else if (n == -1) {
                return result;
            }
        }
    }
}

class DelayHandler implements HttpHandler {

    CyclicBarrier bar1 = new CyclicBarrier(2);
    CyclicBarrier bar2 = new CyclicBarrier(2);
    CyclicBarrier bar3 = new CyclicBarrier(2);

    CyclicBarrier barrier1() {
        return bar1;
    }

    CyclicBarrier barrier2() {
        return bar2;
    }

    @Override
    public synchronized void handle(HttpExchange he) throws IOException {
        byte[] buf = Util.readAll(he.getRequestBody());
        try {
            bar1.await();
            bar2.await();
        } catch (Exception e) {}
        he.sendResponseHeaders(200, -1); // will probably fail
        he.close();
    }

}

// check for simple hardcoded sequence and use remote address
// to check.
// First 4 requests executed in sequence (should use same connection/address)
// Next 4 requests parallel (should use different addresses)
// Then send 4 requests in parallel x 100 times (same four addresses used all time)

class KeepAliveHandler implements HttpHandler {
    volatile int counter = 0;

    HashSet<Integer> portSet = new HashSet<>();

    volatile int[] ports = new int[4];

    void sleep(int n) {
        try {
            Thread.sleep(n);
        } catch (InterruptedException e) {}
    }

    @Override
    public synchronized void handle (HttpExchange t)
        throws IOException
    {
        int remotePort = t.getRemoteAddress().getPort();
        String result = "OK";

        int n = counter++;
        /// First test
        if (n < 4) {
            ports[n] = remotePort;
        }
        if (n == 3) {
            // check all values in ports[] are the same
            if (ports[0] != ports[1] || ports[2] != ports[3]
                    || ports[0] != ports[2]) {
                result = "Error " + Integer.toString(n);
                System.out.println(result);
            }
        }
        // Second test
        if (n >=4 && n < 8) {
            // delay to ensure ports are different
            sleep(500);
            ports[n-4] = remotePort;
        }
        if (n == 7) {
            // should be all different
            if (ports[0] == ports[1] || ports[2] == ports[3]
                    || ports[0] == ports[2]) {
                result = "Error " + Integer.toString(n);
                System.out.println(result);
                System.out.printf("Ports: %d, %d, %d, %d\n", ports[0], ports[1], ports[2], ports[3]);
            }
            // setup for third test
            for (int i=0; i<4; i++) {
                portSet.add(ports[i]);
            }
        }
        // Third test
        if (n > 7) {
            // just check that port is one of the ones in portSet
            if (!portSet.contains(remotePort)) {
                System.out.println ("UNEXPECTED REMOTE PORT " + remotePort);
                result = "Error " + Integer.toString(n);
                System.out.println(result);
            }
        }
        byte[] buf = new byte[2048];

        try (InputStream is = t.getRequestBody()) {
            while (is.read(buf) != -1) ;
        }
        t.sendResponseHeaders(200, result.length());
        OutputStream o = t.getResponseBody();
        o.write(result.getBytes("US-ASCII"));
        t.close();
    }
}
