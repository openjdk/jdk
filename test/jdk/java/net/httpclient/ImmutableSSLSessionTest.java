/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpOption.Http3DiscoveryMode;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.internal.net.http.common.ImmutableExtendedSSLSession;
import jdk.internal.net.http.common.ImmutableSSLSession;
import jdk.internal.net.http.common.ImmutableSSLSessionAccess;
import jdk.test.lib.net.SimpleSSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.net.http.HttpOption.Http3DiscoveryMode.ALT_SVC;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @summary Verify that the request/response headers of HTTP/2 and HTTP/3
 *          are sent and received in lower case
 * @library /test/lib /test/jdk/java/net/httpclient/lib /test/jdk/java/net/httpclient/access
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 *        java.net.http/jdk.internal.net.http.common.ImmutableSSLSessionAccess
 * @run junit/othervm -Djdk.httpclient.HttpClient.log=request,response,headers,errors
 *                    ImmutableSSLSessionTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ImmutableSSLSessionTest implements HttpServerAdapters {

    private HttpTestServer h1server;
    private HttpTestServer h2server;
    private HttpTestServer h3server;
    private String h1ReqURIBase;
    private String h2ReqURIBase;
    private String h3ReqURIBase;
    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    private final AtomicInteger counter = new AtomicInteger();

    @BeforeAll
    public void beforeAll() throws Exception {
        h1server = HttpTestServer.create(HTTP_1_1, sslContext);
        h1server.start();
        h1ReqURIBase = "https://" + h1server.serverAuthority() + "/h1ImmutableSSLSessionTest/";
        h1server.addHandler(new HttpHeadOrGetHandler(), "/h1ImmutableSSLSessionTest/");
        System.out.println("HTTP/1.1 server listening on " + h1server.getAddress());

        h2server = HttpTestServer.create(HTTP_2, sslContext);
        h2server.start();
        h2ReqURIBase = "https://" + h2server.serverAuthority() + "/h2ImmutableSSLSessionTest/";
        h2server.addHandler(new HttpHeadOrGetHandler(), "/h2ImmutableSSLSessionTest/");
        System.out.println("HTTP/2 server listening on " + h2server.getAddress());


        h3server = HttpTestServer.create(HTTP_3_URI_ONLY, sslContext);
        h3server.start();
        h3ReqURIBase = "https://" + h3server.serverAuthority() + "/h3ImmutableSSLSessionTest/";
        h3server.addHandler(new HttpHeadOrGetHandler(), "/h3ImmutableSSLSessionTest/");
        System.out.println("HTTP/3 server listening on " + h3server.getAddress());

    }

    @AfterAll
    public void afterAll() throws Exception {
        if (h2server != null) {
            h2server.stop();
        }
        if (h3server != null) {
            h3server.stop();
        }
    }

    private Stream<Arguments> params() throws Exception {
        return Stream.of(
                Arguments.of(HTTP_1_1, new URI(h1ReqURIBase)),
                Arguments.of(HTTP_2, new URI(h2ReqURIBase)),
                Arguments.of(HTTP_3, new URI(h3ReqURIBase)));
    }

    private Stream<Arguments> sessions() throws Exception {
        return Stream.of(
                Arguments.of(ImmutableSSLSessionAccess.immutableSSLSession(new DummySession())),
                Arguments.of(ImmutableSSLSessionAccess.immutableExtendedSSLSession(new DummySession())));
    }

    /**
     * Issues an HTTPS request and verifies that the SSLSession
     * is immutable.
     */
    @ParameterizedTest
    @MethodSource("params")
    public void testImmutableSSLSession(final Version version, final URI requestURI) throws Exception {
        Http3DiscoveryMode config = switch (version) {
            case HTTP_3 -> HTTP_3_URI_ONLY;
            default -> null;
        };

        final HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .setOption(H3_DISCOVERY, config)
                .version(version);
        final HttpClient.Builder clientBuilder = (version == HTTP_3
                ? newClientBuilderForH3()
                : HttpClient.newBuilder())
                .version(version)
                .sslContext(sslContext)
                .proxy(HttpClient.Builder.NO_PROXY);

        try (HttpClient client = clientBuilder.build()) {
            final URI uriSync = URI.create(requestURI.toString() + "?sync=true,req=" + counter.incrementAndGet());
            final HttpRequest reqSync = reqBuilder.uri(uriSync).build();
            System.out.println("Issuing " + version + " request to " + uriSync);
            final HttpResponse<Void> resp = client.send(reqSync, BodyHandlers.discarding());
            final Optional<SSLSession> syncSession = resp.sslSession();
            assertEquals(resp.version(), version, "Unexpected HTTP version in response");
            assertEquals(resp.statusCode(), 200, "Unexpected response code");
            checkImmutableSession(resp.sslSession());
        }

        // now try with async
        try (HttpClient client = clientBuilder.build()) {
            final URI uriAsync = URI.create(requestURI.toString() + "?sync=false,req=" + counter.incrementAndGet());
            final HttpRequest reqAsync = reqBuilder.copy().uri(uriAsync).build();
            System.out.println("Issuing (async) request to " + uriAsync);
            final CompletableFuture<HttpResponse<Void>> futureResp = client.sendAsync(reqAsync,
                    BodyHandlers.discarding());
            final HttpResponse<Void> asyncResp = futureResp.get();
            assertEquals(asyncResp.version(), version, "Unexpected HTTP version in response");
            assertEquals(asyncResp.statusCode(), 200, "Unexpected response code");
            checkImmutableSession(asyncResp.sslSession());
        }
    }

    @ParameterizedTest
    @MethodSource("sessions")
    public void testImmutableSSLSessionClass(SSLSession session) throws Exception {
        System.out.println("Checking session class: " + session.getClass());
        checkDummySession(session);
    }


    private void checkImmutableSession(Optional<SSLSession> session) {
        assertNotNull(session);
        assertTrue(session.isPresent());
        SSLSession sess = session.get();
        assertNotNull(sess);
        checkImmutableSession(sess);
    }

    private void checkImmutableSession(SSLSession session) {
        if (session instanceof ExtendedSSLSession) {
            assertEquals(ImmutableExtendedSSLSession.class, session.getClass());
        } else {
            assertEquals(ImmutableSSLSession.class, session.getClass());
        }
        assertThrows(UnsupportedOperationException.class, session::invalidate);
        assertThrows(UnsupportedOperationException.class,
                () -> session.putValue("foo", "bar"));
        for (String name : session.getValueNames()) {
            assertThrows(UnsupportedOperationException.class,
                    () -> session.removeValue(name));
        }
    }

    private void checkDummySession(SSLSession session) throws Exception {
        checkImmutableSession(session);
        assertEquals("abcd", new String(session.getId(), US_ASCII));
        assertEquals(sslContext.getClientSessionContext(), session.getSessionContext());
        assertEquals(42, session.getCreationTime());
        assertEquals(4242, session.getLastAccessedTime());
        assertFalse(session.isValid());
        assertEquals("bar", session.getValue("foo"));
        assertEquals(List.of("foo"), Arrays.asList(session.getValueNames()));
        assertEquals(0, session.getPeerCertificates().length);
        assertEquals(0, session.getLocalCertificates().length);
        assertNull(session.getPeerPrincipal());
        assertNull(session.getLocalPrincipal());
        assertEquals("MyCipherSuite", session.getCipherSuite());
        assertEquals("TLSv1.3", session.getProtocol());
        assertEquals("dummy", session.getPeerHost());
        assertEquals(42, session.getPeerPort());
        assertEquals(42, session.getPacketBufferSize());
        assertEquals(42, session.getApplicationBufferSize());
        if (session instanceof ExtendedSSLSession ext) {
            assertEquals(List.of("bar", "foo"),
                    Arrays.asList(ext.getPeerSupportedSignatureAlgorithms()));
            assertEquals(List.of("foo", "bar"),
                    Arrays.asList(ext.getLocalSupportedSignatureAlgorithms()));
            assertEquals(List.of(new SNIHostName("localhost")), ((ExtendedSSLSession) session).getRequestedServerNames());
            List<byte[]> status = ext.getStatusResponses();
            assertEquals(1, status.size());
            assertEquals("42", new String(status.get(0), US_ASCII));
            assertThrows(UnsupportedOperationException.class,
                    () -> ext.exportKeyingMaterialData("foo", new byte[] {1,2,3,4}, 4));
            assertThrows(UnsupportedOperationException.class,
                    () -> ext.exportKeyingMaterialKey("foo", "foo", new byte[] {1,2,3,4}, 4));
        }
    }

    static class DummySession extends ExtendedSSLSession {

        @Override
        public byte[] getId() {
            return new byte[] {'a', 'b', 'c', 'd'};
        }

        @Override
        public SSLSessionContext getSessionContext() {
            return sslContext.getClientSessionContext();
        }

        @Override
        public long getCreationTime() {
            return 42;
        }

        @Override
        public long getLastAccessedTime() {
            return 4242;
        }

        @Override
        public void invalidate() {}

        @Override
        public boolean isValid() {
            return false;
        }

        @Override
        public void putValue(String name, Object value) {

        }

        @Override
        public Object getValue(String name) {
            if (name.equals("foo")) return "bar";
            return null;
        }

        @Override
        public void removeValue(String name) {

        }

        @Override
        public String[] getValueNames() {
            return new String[] {"foo"};
        }

        @Override
        public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
            return new Certificate[0];
        }

        @Override
        public Certificate[] getLocalCertificates() {
            return new Certificate[0];
        }

        @Override
        public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
            return null;
        }

        @Override
        public Principal getLocalPrincipal() {
            return null;
        }

        @Override
        public String getCipherSuite() {
            return "MyCipherSuite";
        }

        @Override
        public String getProtocol() {
            return "TLSv1.3";
        }

        @Override
        public String getPeerHost() {
            return "dummy";
        }

        @Override
        public int getPeerPort() {
            return 42;
        }

        @Override
        public int getPacketBufferSize() {
            return 42;
        }

        @Override
        public int getApplicationBufferSize() {
            return 42;
        }

        @Override
        public String[] getLocalSupportedSignatureAlgorithms() {
            return new String[] {"foo", "bar"};
        }

        @Override
        public String[] getPeerSupportedSignatureAlgorithms() {
            return new String[] {"bar", "foo"};
        }

        @Override
        public List<SNIServerName> getRequestedServerNames() {
            return List.of(new SNIHostName("localhost"));
        }

        @Override
        public List<byte[]> getStatusResponses() {
            return List.of(new byte[] {'4', '2'});
        }
    }

}
