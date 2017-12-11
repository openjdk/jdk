/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.util.List;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import jdk.incubator.http.HttpClient;
import jdk.incubator.http.HttpClient.Redirect;
import jdk.incubator.http.HttpClient.Version;
import jdk.testlibrary.SimpleSSLContext;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/*
 * @test
 * @summary HttpClient[.Builder] API and behaviour checks
 * @library /lib/testlibrary/
 * @build jdk.testlibrary.SimpleSSLContext
 * @run testng HttpClientBuilderTest
 */

public class HttpClientBuilderTest {

    @Test
    public void testDefaults() throws Exception {
        List<HttpClient> clients = List.of(HttpClient.newHttpClient(),
                                           HttpClient.newBuilder().build());

        for (HttpClient client : clients) {
            // Empty optionals and defaults
            assertFalse(client.authenticator().isPresent());
            assertFalse(client.cookieHandler().isPresent());
            assertFalse(client.executor().isPresent());
            assertFalse(client.proxy().isPresent());
            assertTrue(client.sslParameters() != null);
            assertTrue(client.followRedirects().equals(HttpClient.Redirect.NEVER));
            assertTrue(client.sslContext() == SSLContext.getDefault());
            assertTrue(client.version().equals(HttpClient.Version.HTTP_2));
        }
    }

    @Test
    public void testNull() throws Exception {
        HttpClient.Builder builder = HttpClient.newBuilder();
        assertThrows(NullPointerException.class, () -> builder.authenticator(null));
        assertThrows(NullPointerException.class, () -> builder.cookieHandler(null));
        assertThrows(NullPointerException.class, () -> builder.executor(null));
        assertThrows(NullPointerException.class, () -> builder.proxy(null));
        assertThrows(NullPointerException.class, () -> builder.sslParameters(null));
        assertThrows(NullPointerException.class, () -> builder.followRedirects(null));
        assertThrows(NullPointerException.class, () -> builder.sslContext(null));
        assertThrows(NullPointerException.class, () -> builder.version(null));
    }

    static class TestAuthenticator extends Authenticator { }

    @Test
    public void testAuthenticator() {
        HttpClient.Builder builder = HttpClient.newBuilder();
        Authenticator a = new TestAuthenticator();
        builder.authenticator(a);
        assertTrue(builder.build().authenticator().get() == a);
        Authenticator b = new TestAuthenticator();
        builder.authenticator(b);
        assertTrue(builder.build().authenticator().get() == b);
        assertThrows(NullPointerException.class, () -> builder.authenticator(null));
        Authenticator c = new TestAuthenticator();
        builder.authenticator(c);
        assertTrue(builder.build().authenticator().get() == c);
    }

    @Test
    public void testCookieHandler() {
        HttpClient.Builder builder = HttpClient.newBuilder();
        CookieHandler a = new CookieManager();
        builder.cookieHandler(a);
        assertTrue(builder.build().cookieHandler().get() == a);
        CookieHandler b = new CookieManager();
        builder.cookieHandler(b);
        assertTrue(builder.build().cookieHandler().get() == b);
        assertThrows(NullPointerException.class, () -> builder.cookieHandler(null));
        CookieManager c = new CookieManager();
        builder.cookieHandler(c);
        assertTrue(builder.build().cookieHandler().get() == c);
    }

    static class TestExecutor implements Executor {
        public void execute(Runnable r) { }
    }

    @Test
    public void testExecutor() {
        HttpClient.Builder builder = HttpClient.newBuilder();
        TestExecutor a = new TestExecutor();
        builder.executor(a);
        assertTrue(builder.build().executor().get() == a);
        TestExecutor b = new TestExecutor();
        builder.executor(b);
        assertTrue(builder.build().executor().get() == b);
        assertThrows(NullPointerException.class, () -> builder.executor(null));
        TestExecutor c = new TestExecutor();
        builder.executor(c);
        assertTrue(builder.build().executor().get() == c);
    }

    @Test
    public void testProxySelector() {
        HttpClient.Builder builder = HttpClient.newBuilder();
        ProxySelector a = ProxySelector.of(null);
        builder.proxy(a);
        assertTrue(builder.build().proxy().get() == a);
        ProxySelector b = ProxySelector.of(InetSocketAddress.createUnresolved("foo", 80));
        builder.proxy(b);
        assertTrue(builder.build().proxy().get() == b);
        assertThrows(NullPointerException.class, () -> builder.proxy(null));
        ProxySelector c = ProxySelector.of(InetSocketAddress.createUnresolved("bar", 80));
        builder.proxy(c);
        assertTrue(builder.build().proxy().get() == c);
    }

    @Test
    public void testSSLParameters() {
        HttpClient.Builder builder = HttpClient.newBuilder();
        SSLParameters a = new SSLParameters();
        a.setCipherSuites(new String[] { "A" });
        builder.sslParameters(a);
        a.setCipherSuites(new String[] { "Z" });
        assertTrue(builder.build().sslParameters() != (a));
        assertTrue(builder.build().sslParameters().getCipherSuites()[0].equals("A"));
        SSLParameters b = new SSLParameters();
        b.setEnableRetransmissions(true);
        builder.sslParameters(b);
        assertTrue(builder.build().sslParameters() != b);
        assertTrue(builder.build().sslParameters().getEnableRetransmissions());
        assertThrows(NullPointerException.class, () -> builder.sslParameters(null));
        SSLParameters c = new SSLParameters();
        c.setProtocols(new String[] { "C" });
        builder.sslParameters(c);
        c.setProtocols(new String[] { "D" });
        assertTrue(builder.build().sslParameters().getProtocols()[0].equals("C"));
    }

    @Test
    public void testSSLContext() throws Exception {
        HttpClient.Builder builder = HttpClient.newBuilder();
        SSLContext a = (new SimpleSSLContext()).get();
        builder.sslContext(a);
        assertTrue(builder.build().sslContext() == a);
        SSLContext b = (new SimpleSSLContext()).get();
        builder.sslContext(b);
        assertTrue(builder.build().sslContext() == b);
        assertThrows(NullPointerException.class, () -> builder.sslContext(null));
        SSLContext c = (new SimpleSSLContext()).get();
        builder.sslContext(c);
        assertTrue(builder.build().sslContext() == c);
    }

    @Test
    public void testFollowRedirects() {
        HttpClient.Builder builder = HttpClient.newBuilder();
        builder.followRedirects(Redirect.ALWAYS);
        assertTrue(builder.build().followRedirects() == Redirect.ALWAYS);
        builder.followRedirects(Redirect.NEVER);
        assertTrue(builder.build().followRedirects() == Redirect.NEVER);
        assertThrows(NullPointerException.class, () -> builder.followRedirects(null));
        builder.followRedirects(Redirect.SAME_PROTOCOL);
        assertTrue(builder.build().followRedirects() == Redirect.SAME_PROTOCOL);
        builder.followRedirects(Redirect.SECURE);
        assertTrue(builder.build().followRedirects() == Redirect.SECURE);
    }

    @Test
    public void testVersion() {
        HttpClient.Builder builder = HttpClient.newBuilder();
        builder.version(Version.HTTP_2);
        assertTrue(builder.build().version() == Version.HTTP_2);
        builder.version(Version.HTTP_1_1);
        assertTrue(builder.build().version() == Version.HTTP_1_1);
        assertThrows(NullPointerException.class, () -> builder.version(null));
        builder.version(Version.HTTP_2);
        assertTrue(builder.build().version() == Version.HTTP_2);
        builder.version(Version.HTTP_1_1);
        assertTrue(builder.build().version() == Version.HTTP_1_1);
    }

    @Test
    static void testPriority() throws Exception {
        HttpClient.Builder builder = HttpClient.newBuilder();
        assertThrows(IllegalArgumentException.class, () -> builder.priority(-1));
        assertThrows(IllegalArgumentException.class, () -> builder.priority(0));
        assertThrows(IllegalArgumentException.class, () -> builder.priority(257));
        assertThrows(IllegalArgumentException.class, () -> builder.priority(500));

        builder.priority(1);
        builder.build();
        builder.priority(256);
        builder.build();
    }


    /* ---- standalone entry point ---- */

    public static void main(String[] args) throws Exception {
        HttpClientBuilderTest test = new HttpClientBuilderTest();
        for (Method m : HttpClientBuilderTest.class.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Test.class)) {
                try {
                    m.invoke(test);
                    System.out.printf("test %s: success%n", m.getName());
                } catch (Throwable t ) {
                    System.out.printf("test %s: failed%n", m.getName());
                    t.printStackTrace();
                }
            }
        }
    }
}
