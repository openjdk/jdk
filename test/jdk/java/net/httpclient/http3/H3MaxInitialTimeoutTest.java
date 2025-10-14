/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.channels.DatagramChannel;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import org.testng.ITestContext;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.lang.System.out;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static org.testng.Assert.assertEquals;


/*
 * @test
 * @bug 8342954
 * @summary Verify jdk.httpclient.quic.maxInitialTimeout is taken into account.
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.httpclient.test.lib.quic.QuicStandaloneServer
 * @run testng/othervm -Djdk.internal.httpclient.debug=true
 *                     -Djdk.httpclient.HttpClient.log=requests,responses,errors,quic:controls
 *                     -Djdk.httpclient.quic.maxInitialTimeout=1
 *                     H3MaxInitialTimeoutTest
 * @run testng/othervm -Djdk.internal.httpclient.debug=true
 *                     -Djdk.httpclient.HttpClient.log=requests,responses,errors,quic:controls
 *                     -Djdk.httpclient.quic.maxInitialTimeout=2
 *                     H3MaxInitialTimeoutTest
 * @run testng/othervm -Djdk.internal.httpclient.debug=true
 *                     -Djdk.httpclient.HttpClient.log=requests,responses,errors,quic:controls
 *                     -Djdk.httpclient.quic.maxInitialTimeout=2147483647
 *                     H3MaxInitialTimeoutTest
 */
public class H3MaxInitialTimeoutTest implements HttpServerAdapters {

    SSLContext sslContext;
    DatagramChannel receiver;
    String h3URI;

    static final Executor executor = new TestExecutor(Executors.newVirtualThreadPerTaskExecutor());
    static final ConcurrentMap<String, Throwable> FAILURES = new ConcurrentHashMap<>();
    static volatile boolean tasksFailed;
    static final AtomicLong serverCount = new AtomicLong();
    static final AtomicLong clientCount = new AtomicLong();
    static final long start = System.nanoTime();
    public static String now() {
        long now = System.nanoTime() - start;
        long secs = now / 1000_000_000;
        long mill = (now % 1000_000_000) / 1000_000;
        long nan = now % 1000_000;
        return String.format("[%d s, %d ms, %d ns] ", secs, mill, nan);
    }

    static class TestExecutor implements Executor {
        final AtomicLong tasks = new AtomicLong();
        Executor executor;
        TestExecutor(Executor executor) {
            this.executor = executor;
        }

        @Override
        public void execute(Runnable command) {
            long id = tasks.incrementAndGet();
            executor.execute(() -> {
                try {
                    command.run();
                } catch (Throwable t) {
                    tasksFailed = true;
                    System.out.printf(now() + "Task %s failed: %s%n", id, t);
                    System.err.printf(now() + "Task %s failed: %s%n", id, t);
                    FAILURES.putIfAbsent("Task " + id, t);
                    throw t;
                }
            });
        }
    }

    protected boolean stopAfterFirstFailure() {
        return Boolean.getBoolean("jdk.internal.httpclient.debug");
    }

    @BeforeMethod
    void beforeMethod(ITestContext context) {
        if (stopAfterFirstFailure() && context.getFailedTests().size() > 0) {
            var x = new SkipException("Skipping: some test failed");
            x.setStackTrace(new StackTraceElement[0]);
            throw x;
        }
    }

    @AfterClass
    static void printFailedTests() {
        out.println("\n=========================");
        try {
            out.printf("%n%sCreated %d servers and %d clients%n",
                    now(), serverCount.get(), clientCount.get());
            if (FAILURES.isEmpty()) return;
            out.println("Failed tests: ");
            FAILURES.forEach((key, value) -> {
                out.printf("\t%s: %s%n", key, value);
                value.printStackTrace(out);
                value.printStackTrace();
            });
            if (tasksFailed) {
                System.out.println("WARNING: Some tasks failed");
            }
        } finally {
            out.println("\n=========================\n");
        }
    }

    @DataProvider(name = "h3URIs")
    public Object[][] versions(ITestContext context) {
        if (stopAfterFirstFailure() && context.getFailedTests().size() > 0) {
            return new Object[0][];
        }
        Object[][] result = {{h3URI}};
        return result;
    }

    private HttpClient makeNewClient(long connectionTimeout) {
        clientCount.incrementAndGet();
        HttpClient client = newClientBuilderForH3()
                .version(Version.HTTP_3)
                .proxy(HttpClient.Builder.NO_PROXY)
                .executor(executor)
                .sslContext(sslContext)
                .connectTimeout(Duration.ofSeconds(connectionTimeout))
                .build();
        return client;
    }

    @Test(dataProvider = "h3URIs")
    public void testTimeout(final String h3URI) throws Exception {
        long timeout = Long.getLong("jdk.httpclient.quic.maxInitialTimeout", 30);
        long connectionTimeout = timeout == Integer.MAX_VALUE ? 2 : 10 * timeout;

        try (HttpClient client = makeNewClient(connectionTimeout)) {
            URI uri = URI.create(h3URI);
            Builder builder = HttpRequest.newBuilder(uri)
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .GET();
            HttpRequest request = builder.build();
            try {
                HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
                out.println("Response #1: " + response);
                out.println("Version  #1: " + response.version());
                assertEquals(response.statusCode(), 200, "first response status");
                assertEquals(response.version(), HTTP_3, "first response version");
                throw new AssertionError("Expected ConnectException not thrown");
            } catch (ConnectException c) {
                String msg = c.getMessage();
                if (timeout != Integer.MAX_VALUE) {
                    if (msg != null && msg.contains("No response from peer")) {
                        out.println("Got expected exception: " + c);
                    } else throw c;
                } else throw c;
            } catch (HttpConnectTimeoutException hc) {
                String msg = hc.getMessage();
                if (timeout == Integer.MAX_VALUE) {
                    if (msg != null && msg.contains("No response from peer")) {
                        throw new AssertionError("Unexpected message: " + msg, hc);
                    } else {
                        out.println("Got expected exception: " + hc);
                        return;
                    }
                } else throw hc;
            }
        }

    }


    @BeforeTest
    public void setup() throws Exception {
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null) {
            throw new AssertionError("Unexpected null sslContext");
        }
        receiver = DatagramChannel.open();
        receiver.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        h3URI = URIBuilder.newBuilder()
                .scheme("https")
                .loopback()
                .port(((InetSocketAddress)receiver.getLocalAddress()).getPort())
                .path("/")
                .build()
                .toString();
    }

    @AfterTest
    public void teardown() throws Exception {
        System.err.println("=======================================================");
        System.err.println("               Tearing down test");
        System.err.println("=======================================================");
        receiver.close();
    }

}
