/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import jdk.httpclient.test.lib.common.DynamicKeyStoreUtil;
import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.common.ServerNameMatcher;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import jdk.httpclient.test.lib.http3.Http3TestServer;
import jdk.httpclient.test.lib.quic.QuicServer;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.Test;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.ALT_SVC;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static jdk.httpclient.test.lib.common.DynamicKeyStoreUtil.generateCert;
import static jdk.httpclient.test.lib.common.DynamicKeyStoreUtil.generateKeyStore;
import static jdk.httpclient.test.lib.common.DynamicKeyStoreUtil.generateRSAKeyPair;
import static jdk.httpclient.test.lib.http3.Http3TestServer.quicServerBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @summary verifies the HttpClient's usage of alternate services
 * @comment The goal of this test class is to run various tests to verify that the HttpClient
 * (and the underlying layers) use an alternate server for HTTP request(s) IF AND ONLY IF such an
 * advertised alternate server satisfies "reasonable assurance" expectations as noted in the
 * alternate service RFC-7838. Reasonable assurance can be summarized as:
 *  - The origin server which advertised the alternate service, MUST be running on TLS
 *  - The certificate presented by origin server during TLS handshake must be valid (and trusted)
 *    for the origin server
 *  - The certificate presented by alternate server (when subsequently a connection attempt is
 *    made to it) MUST be valid (and trusted) for the ORIGIN server
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.httpclient.test.lib.common.DynamicKeyStoreUtil
 *        jdk.test.lib.net.URIBuilder
 * @modules java.base/sun.net.www.http
 *          java.net.http/jdk.internal.net.http.common
 *          java.net.http/jdk.internal.net.http.frame
 *          java.net.http/jdk.internal.net.http.hpack
 *          java.base/jdk.internal.net.quic
 *          java.net.http/jdk.internal.net.http.quic
 *          java.net.http/jdk.internal.net.http.quic.packets
 *          java.net.http/jdk.internal.net.http.quic.frames
 *          java.net.http/jdk.internal.net.http.quic.streams
 *          java.net.http/jdk.internal.net.http.http3.streams
 *          java.net.http/jdk.internal.net.http.http3.frames
 *          java.net.http/jdk.internal.net.http.http3
 *          java.net.http/jdk.internal.net.http.qpack
 *          java.net.http/jdk.internal.net.http.qpack.readers
 *          java.net.http/jdk.internal.net.http.qpack.writers
 * @modules java.base/sun.security.x509
 *          java.base/jdk.internal.util
 * @run junit/othervm  -Djdk.net.hosts.file=${test.src}/altsvc-dns-hosts.txt
 *                     -Djdk.internal.httpclient.debug=true -Djavax.net.debug=all
 *                     -Djdk.httpclient.HttpClient.log=requests,responses,errors
 *                      AltServiceReasonableAssurance
 */
public class AltServiceReasonableAssurance implements HttpServerAdapters {

    private static final String ORIGIN_SERVER_HOSTNAME = "origin.server";
    private static final String ALT_SERVER_HOSTNAME = "altservice.server";

    private static final String ALT_SERVER_RESPONSE_MESSAGE = "Hello from an alt server";
    private static final String ORIGIN_SERVER_RESPONSE_MESSAGE = "Hello from an origin server";

    private record TestInput(HttpTestServer originServer, HttpTestServer altServer,
                             URI requestURI, String expectedAltSvcHeader) {
    }

    /**
     * Creates and starts a origin server and an alternate server. The passed (same) SSLContext
     * is used by both the origin server and the alternate server.
     */
    private static TestInput startOriginAndAltServer(final SSLContext sslContext)
            throws Exception {
        Objects.requireNonNull(sslContext);
        return startOriginAndAltServer(sslContext, sslContext);
    }

    /**
     * Creates and starts a origin server and an alternate server. The origin server will use
     * the {@code originSrvSSLCtx} and the alternate server will use the {@code altSrvSSLCtx}
     */
    private static TestInput startOriginAndAltServer(final SSLContext originSrvSSLCtx,
                                                     final SSLContext altSrvSSLCtx)
            throws Exception {
        Objects.requireNonNull(originSrvSSLCtx);
        Objects.requireNonNull(altSrvSSLCtx);
        final String requestPath = "/hello";
        final QuicServer quicServer = quicServerBuilder()
                .sslContext(altSrvSSLCtx)
                // the client sends a SNI for origin server. this alt server should be capable
                // of matching/accepting that SNI name of the origin
                .sniMatcher(new ServerNameMatcher(ORIGIN_SERVER_HOSTNAME))
                .build();
        // Alt server only supports H3
        final HttpTestServer altServer = HttpTestServer.of(new Http3TestServer(quicServer));
        altServer.addHandler(new Handler(ALT_SERVER_RESPONSE_MESSAGE), requestPath);
        altServer.start();
        System.out.println("Alt server started at " + altServer.getAddress());

        // H2 server which has a (application level) handler which advertises H3 alt service
        final HttpTestServer originServer = HttpTestServer.of(
                new Http2TestServer(ORIGIN_SERVER_HOSTNAME, true, originSrvSSLCtx));
        final int altServerPort = altServer.getAddress().getPort();
        final String altSvcHeaderVal = "h3=\"" + ALT_SERVER_HOSTNAME + ":" + altServerPort + "\"";
        originServer.addHandler(new Handler(ORIGIN_SERVER_RESPONSE_MESSAGE, altSvcHeaderVal),
                requestPath);
        originServer.start();
        System.out.println("Origin server started at " + originServer.getAddress());
        // request URI should be directed to the origin server
        final URI requestURI = URIBuilder.newBuilder()
                .scheme("https")
                .host(ORIGIN_SERVER_HOSTNAME)
                .port(originServer.getAddress().getPort())
                .path(requestPath)
                .build();
        return new TestInput(originServer, altServer, requestURI, altSvcHeaderVal);
    }

    private TestInput startHttpOriginHttpsAltServer(final SSLContext altServerSSLCtx)
            throws Exception {
        Objects.requireNonNull(altServerSSLCtx);
        final String requestPath = "/foo";
        // Alt server only supports H3
        final HttpTestServer altServer = HttpTestServer.create(HTTP_3_URI_ONLY, altServerSSLCtx);
        altServer.addHandler(new Handler(ALT_SERVER_RESPONSE_MESSAGE), requestPath);
        altServer.start();
        System.out.println("Alt server (HTTPS) started at " + altServer.getAddress());

        // supports only HTTP server and uses a (application level) handler which advertises a H3
        // alternate service
        final HttpTestServer originServer = HttpTestServer.create(HTTP_2);
        final int altServerPort = altServer.getAddress().getPort();
        final String altSvcHeaderVal = "h3=\"" + ALT_SERVER_HOSTNAME + ":" + altServerPort + "\"";
        originServer.addHandler(new Handler(ORIGIN_SERVER_RESPONSE_MESSAGE, altSvcHeaderVal),
                requestPath);
        originServer.start();
        System.out.println("Origin server (HTTP) started at " + originServer.getAddress());
        // request URI should be against (HTTP) origin server
        final URI requestURI = URIBuilder.newBuilder()
                .scheme("http")
                .host(ORIGIN_SERVER_HOSTNAME)
                .port(originServer.getAddress().getPort())
                .path(requestPath)
                .build();
        return new TestInput(originServer, altServer, requestURI, altSvcHeaderVal);
    }

    /**
     * Stop the server (and ignore any exception)
     */
    private static void safeStop(final HttpTestServer server) {
        if (server == null) {
            return;
        }
        final InetSocketAddress serverAddr = server.getAddress();
        try {
            System.out.println("Stopping server " + serverAddr);
            server.stop();
        } catch (Exception e) {
            System.err.println("Ignoring exception: " + e.getMessage() + " that occurred " +
                    "during stop of server: " + serverAddr);
        }
    }

    /**
     * Returns back a 200 HTTP response with a response body containing a response message
     * that was used to construct the Handler instance. Additionally, if the Handler was constructed
     * with a non-null {@code altSvcHeaderVal} then that value is sent back as a header value. in
     * the response, for the {@code alt-svc} header
     */
    private static final class Handler implements HttpTestHandler {
        private final String responseMessage;
        private final byte[] responseBytes;
        private final String altSvcHeaderVal;

        private Handler(final String responseMessage) {
            this(responseMessage, null);
        }

        private Handler(final String responseMessage, final String altSvcHeaderVal) {
            Objects.requireNonNull(responseMessage);
            this.responseMessage = responseMessage;
            this.responseBytes = responseMessage.getBytes(StandardCharsets.UTF_8);
            this.altSvcHeaderVal = altSvcHeaderVal;
        }

        @Override
        public void handle(final HttpTestExchange exchange) throws IOException {
            System.out.println("Handling request " + exchange.getRequestURI());
            if (this.altSvcHeaderVal != null) {
                System.out.println("Responding with alt-svc header: " + this.altSvcHeaderVal);
                exchange.getResponseHeaders().addHeader("alt-svc", this.altSvcHeaderVal);
            }
            System.out.println("Responding with body: " + this.responseMessage);
            exchange.sendResponseHeaders(200, this.responseBytes.length);
            try (final OutputStream os = exchange.getResponseBody()) {
                os.write(this.responseBytes);
            }
        }
    }

    /**
     * - Keystore K1 is constructed with a certificate whose subject is origin server hostname and
     * subject alternative name is alternate server hostname
     * - K1 is used to construct a SSLContext and thus the SSLContext uses the keys and trusted
     * certificate from this keystore
     * - The constructed SSLContext instance is used by the HttpClient, the origin server and the
     * alternate server
     * - During TLS handshake with origin server, the origin server is expected to present the
     * certificate from this K1 keystore.
     * - During TLS handshake with alternate server, the alternate server is expected to present
     * this same certificate from K1 keystore.
     * - Since the certificate is valid (and trusted by the client) for both origin server
     * and alternate server (because of the valid subject name and subject alternate name),
     * the TLS handshake between the HttpClient and the origin and alternate server is expected
     * to pass
     * <p>
     * Once the servers are started, this test method does the following:
     * <p>
     * - Client constructs a HTTP_3 request addressed to origin server
     * - Origin server responds with a 200 response and also with alt-svc header pointing to
     * an alternate server
     * - Client verifies the response as well as presence of the alt-svc header value
     * - Client issues the *same* request again
     * - The request is expected to be handled by the alternate server
     */
    @Test
    public void testOriginAltSameCert() throws Exception {
        // create a keystore which contains a PrivateKey entry and a certificate associated with
        // that key. the certificate's subject will be origin server's hostname and will
        // additionally have the alt server hostname as a subject alternate name. Thus, the
        // certificate is valid for both origin server and alternate server
        final KeyStore keyStore = generateKeyStore(ORIGIN_SERVER_HOSTNAME, ALT_SERVER_HOSTNAME);
        System.out.println("Generated a keystore with certificate: " +
                keyStore.getCertificate(DynamicKeyStoreUtil.DEFAULT_ALIAS));
        // create a SSLContext that will be used by the servers and the HttpClient and will be
        // backed by the keystore we just created. Thus, the HttpClient will trust the certificate
        // belonging to that keystore
        final SSLContext sslContext = DynamicKeyStoreUtil.createSSLContext(keyStore);
        // start the servers
        final TestInput testInput = startOriginAndAltServer(sslContext);
        try {
            final HttpClient client = newClientBuilderForH3()
                    .proxy(NO_PROXY)
                    .sslContext(sslContext)
                    .version(HTTP_3)
                    .build();
            // send a HTTP3 request to a server which is expected to respond back
            // with a 200 response and an alt-svc header pointing to another/different H3 server
            final URI requestURI = testInput.requestURI;
            final HttpRequest request = HttpRequest.newBuilder()
                    .GET().uri(requestURI)
                    .setOption(H3_DISCOVERY, ALT_SVC)
                    .build();
            System.out.println("Issuing request " + requestURI);
            final HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, response.statusCode(), "Unexpected response code");
            // the origin server is expected to respond
            assertEquals(ORIGIN_SERVER_RESPONSE_MESSAGE, response.body(), "Unexpected response" +
                    " body");
            assertEquals(HTTP_2, response.version(), "Unexpected HTTP version in response");

            // verify the origin server sent back a alt-svc header
            final Optional<String> altSvcHeader = response.headers().firstValue("alt-svc");
            assertTrue(altSvcHeader.isPresent(), "alt-svc header is missing in response");
            final String actualAltSvcHeader = altSvcHeader.get();
            System.out.println("Received alt-svc header value: " + actualAltSvcHeader);
            assertTrue(actualAltSvcHeader.contains(testInput.expectedAltSvcHeader),
                    "Unexpected alt-svc header value: " + actualAltSvcHeader
                            + ", was expected to contain: " + testInput.expectedAltSvcHeader);

            // now issue the same request again and this time expect it to be handled
            // by the alt-service
            System.out.println("Again issuing request " + requestURI);
            final HttpResponse<String> secondResponse = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, secondResponse.statusCode(), "Unexpected response code");
            // expect the alt service to respond
            assertEquals(ALT_SERVER_RESPONSE_MESSAGE, secondResponse.body(), "Unexpected response" +
                    " body");
            assertEquals(HTTP_3, secondResponse.version(), "Unexpected HTTP version in response");
        } finally {
            safeStop(testInput.originServer);
            safeStop(testInput.altServer);
        }
    }

    /**
     * - Keystore K1 is constructed with a PrivateKey PK1 and certificate whose subject is
     * origin server hostname
     * - Keystore K2 is constructed with the same PrivateKey PK1 and certificate whose subject is
     * alternate server hostname AND has a subject alternate name of origin server hostname
     * - K1 is used to construct a SSLContext S1 and that S1 is used by origin server
     * - K2 is used to construct a SSLContext S2 and that S2 is used by alternate server
     * - SSLContext S3 is constructed with both the certificate of origin server and
     * the certificate of alternate server as trusted certificates. HttpClient uses S3
     * - During TLS handshake with origin server, the origin server is expected to present the
     * certificate from this K1 keystore, with subject as origin server hostname
     * - During TLS handshake with alternate server, the alternate server is expected to present
     * the certificate from K2 keystore, with subject as alternate server hostname AND a subject
     * alternate name of origin server
     * - HttpClient (through S3 SSLContext) trusts both these certs. The cert presented
     * by alt server, is valid (even) for origin server (since its subject alternate name is
     * origin server hostname). Thus, the client must consider the alternate service as valid and
     * use it.
     * <p>
     * Once the servers are started, this test method does the following:
     * <p>
     * - Client constructs a HTTP_3 request addressed to origin server
     * - Origin server responds with a 200 response and also with alt-svc header pointing to
     * an alternate server
     * - Client verifies the response as well as presence of the alt-svc header value
     * - Client issues the *same* request again
     * - The request is expected to be handled by the alternate server
     */
    @Test
    public void testOriginAltDifferentCert() throws Exception {
        final SecureRandom secureRandom = new SecureRandom();
        final KeyPair keyPair = generateRSAKeyPair(secureRandom);

        // generate a certificate for origin server, with origin server hostname as the subject
        final X509Certificate originServerCert = generateCert(keyPair, secureRandom,
                ORIGIN_SERVER_HOSTNAME);
        // create a keystore with the private key and the cert. this keystore will then be
        // used by the SSLContext of origin server
        final KeyStore originServerKeyStore = generateKeyStore(keyPair.getPrivate(),
                new Certificate[]{originServerCert});
        System.out.println("Generated a keystore, for origin server, with certificate: " +
                originServerKeyStore.getCertificate(DynamicKeyStoreUtil.DEFAULT_ALIAS));
        // create the SSLContext for the origin server
        final SSLContext originServerSSLCtx = DynamicKeyStoreUtil.createSSLContext(
                originServerKeyStore);

        // create a cert for the alternate server, with alternate server hostname as the subject
        // AND origin server hostname as a subject alternate name
        final X509Certificate altServerCert = generateCert(keyPair, secureRandom,
                ALT_SERVER_HOSTNAME, ORIGIN_SERVER_HOSTNAME);
        // create keystore with the private key and the alt server's cert. this keystore will then
        // be used by the SSLContext of alternate server
        final KeyStore altServerKeyStore = generateKeyStore(keyPair.getPrivate(),
                new Certificate[]{altServerCert});
        System.out.println("Generated a keystore, for alt server, with certificate: " +
                altServerKeyStore.getCertificate(DynamicKeyStoreUtil.DEFAULT_ALIAS));
        // create SSLContext of alternate server
        final SSLContext altServerSSLCtx = DynamicKeyStoreUtil.createSSLContext(altServerKeyStore);

        // now create a SSLContext for the HttpClient. This SSLContext will contain no key manager
        // and will have a trust manager which trusts origin server certificate and the alternate
        // server certificate
        final SSLContext clientSSLCtx = sslCtxWithTrustedCerts(List.of(originServerCert,
                altServerCert));
        // start the servers
        final TestInput testInput = startOriginAndAltServer(originServerSSLCtx, altServerSSLCtx);
        try {
            final HttpClient client = newClientBuilderForH3()
                    .proxy(NO_PROXY)
                    .sslContext(clientSSLCtx)
                    .version(HTTP_3)
                    .build();
            // send a HTTP3 request to a server which is expected to respond back
            // with a 200 response and an alt-svc header pointing to another/different H3 server
            final URI requestURI = testInput.requestURI;
            final HttpRequest request = HttpRequest.newBuilder()
                    .GET().uri(requestURI)
                    .setOption(H3_DISCOVERY, ALT_SVC)
                    .build();
            System.out.println("Issuing request " + requestURI);
            final HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, response.statusCode(), "Unexpected response code");
            // the origin server is expected to respond
            assertEquals(ORIGIN_SERVER_RESPONSE_MESSAGE, response.body(), "Unexpected response" +
                    " body");
            assertEquals(HTTP_2, response.version(), "Unexpected HTTP version in response");

            // verify the origin server sent back a alt-svc header
            final Optional<String> altSvcHeader = response.headers().firstValue("alt-svc");
            assertTrue(altSvcHeader.isPresent(), "alt-svc header is missing in response");
            final String actualAltSvcHeader = altSvcHeader.get();
            System.out.println("Received alt-svc header value: " + actualAltSvcHeader);
            assertTrue(actualAltSvcHeader.contains(testInput.expectedAltSvcHeader),
                    "Unexpected alt-svc header value: " + actualAltSvcHeader
                            + ", was expected to contain: " + testInput.expectedAltSvcHeader);

            // now issue the same request again and this time expect it to be handled
            // by the alt-service
            System.out.println("Again issuing request " + requestURI);
            final HttpResponse<String> secondResponse = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, secondResponse.statusCode(), "Unexpected response code");
            // expect the alt service to respond
            assertEquals(ALT_SERVER_RESPONSE_MESSAGE, secondResponse.body(), "Unexpected response" +
                    " body");
            assertEquals(HTTP_3, secondResponse.version(), "Unexpected HTTP version in response");
        } finally {
            safeStop(testInput.originServer);
            safeStop(testInput.altServer);
        }
    }


    /**
     * - Keystore K1 is constructed with a PrivateKey PK1 and certificate whose subject is
     * origin server hostname
     * - Keystore K2 is constructed with the same PrivateKey PK1 and certificate whose subject is
     * alternate server hostname
     * - K1 is used to construct a SSLContext S1 and that S1 is used by origin server
     * - K2 is used to construct a SSLContext S2 and that S2 is used by alternate server
     * - SSLContext S3 is constructed with both the certificate of origin server and
     * the certificate of alternate server as trusted certificates. HttpClient uses S3
     * - During TLS handshake with origin server, the origin server is expected to present the
     * certificate from this K1 keystore, with subject as origin server hostname
     * - During TLS handshake with alternate server, the alternate server is expected to present
     * the certificate from K2 keystore, with subject as alternate server hostname
     * - HttpClient (through S3 SSLContext) trusts both these certs, but the cert presented
     * by alt server, although valid for the alt server, CANNOT/MUST NOT be valid for origin
     * server (since it's subject nor subject alternate name is origin server hostname).
     * Reasonable assurance expects that the alt server present a certificate that is valid for
     * origin server host and since it doesn't, the alt server must not be used by the HttpClient.
     * <p>
     * Once the servers are started, this test method does the following:
     * <p>
     * - Client constructs a HTTP_3 request addressed to origin server
     * - Origin server responds with a 200 response and also with alt-svc header pointing to
     * an alternate server
     * - Client verifies the response as well as presence of the alt-svc header value
     * - Client issues the *same* request again
     * - The request is expected to be handled by the origin server again and the advertised
     * alternate service MUST NOT be used (due to reasons noted above)
     */
    @Test
    public void testAltServerWrongCert() throws Exception {
        final SecureRandom secureRandom = new SecureRandom();
        final KeyPair keyPair = generateRSAKeyPair(secureRandom);

        // generate a certificate for origin server, with origin server hostname as the subject
        final X509Certificate originServerCert = generateCert(keyPair, secureRandom,
                ORIGIN_SERVER_HOSTNAME);
        // create a keystore with the private key and the cert. this keystore will then be
        // used by the SSLContext of origin server
        final KeyStore originServerKeyStore = generateKeyStore(keyPair.getPrivate(),
                new Certificate[]{originServerCert});
        System.out.println("Generated a keystore, for origin server, with certificate: " +
                originServerKeyStore.getCertificate(DynamicKeyStoreUtil.DEFAULT_ALIAS));
        // create the SSLContext for the origin server
        final SSLContext originServerSSLCtx = DynamicKeyStoreUtil.createSSLContext(
                originServerKeyStore);

        // create a cert for the alternate server, with alternate server hostname as the subject
        final X509Certificate altServerCert = generateCert(keyPair, secureRandom,
                ALT_SERVER_HOSTNAME);
        // create keystore with the private key and the alt server's cert. this keystore will then
        // be used by the SSLContext of alternate server
        final KeyStore altServerKeyStore = generateKeyStore(keyPair.getPrivate(),
                new Certificate[]{altServerCert});
        System.out.println("Generated a keystore, for alt server, with certificate: " +
                altServerKeyStore.getCertificate(DynamicKeyStoreUtil.DEFAULT_ALIAS));
        // create SSLContext of alternate server
        final SSLContext altServerSSLCtx = DynamicKeyStoreUtil.createSSLContext(altServerKeyStore);

        // now create a SSLContext for the HttpClient. This SSLContext will contain no key manager
        // and will have a trust manager which trusts origin server certificate and the alternate
        // server certificate
        final SSLContext clientSSLCtx = sslCtxWithTrustedCerts(List.of(originServerCert,
                altServerCert));
        // start the servers
        final TestInput testInput = startOriginAndAltServer(originServerSSLCtx, altServerSSLCtx);
        try {
            final HttpClient client = newClientBuilderForH3()
                    .proxy(NO_PROXY)
                    .sslContext(clientSSLCtx)
                    .version(HTTP_3)
                    .build();
            // send a HTTP3 request to a server which is expected to respond back
            // with a 200 response and an alt-svc header pointing to another/different H3 server
            final URI requestURI = testInput.requestURI;
            final HttpRequest request = HttpRequest.newBuilder()
                    .GET().uri(requestURI)
                    .setOption(H3_DISCOVERY, ALT_SVC)
                    .build();
            System.out.println("Issuing request " + requestURI);
            final HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, response.statusCode(), "Unexpected response code");
            // the origin server is expected to respond
            assertEquals(ORIGIN_SERVER_RESPONSE_MESSAGE, response.body(), "Unexpected response" +
                    " body");
            assertEquals(HTTP_2, response.version(), "Unexpected HTTP version in response");

            // verify the origin server sent back a alt-svc header
            final Optional<String> altSvcHeader = response.headers().firstValue("alt-svc");
            assertTrue(altSvcHeader.isPresent(), "alt-svc header is missing in response");
            final String actualAltSvcHeader = altSvcHeader.get();
            System.out.println("Received alt-svc header value: " + actualAltSvcHeader);
            assertTrue(actualAltSvcHeader.contains(testInput.expectedAltSvcHeader),
                    "Unexpected alt-svc header value: " + actualAltSvcHeader
                            + ", was expected to contain: " + testInput.expectedAltSvcHeader);

            // now issue the same request again (a few times). Expect each of these requests too,
            // to be handled by the origin server (since the advertised alt server isn't expected
            // to satisfy the "reasonable assurances" expectations
            for (int i = 1; i <= 3; i++) {
                System.out.println("Again(" + i + ") issuing request " + requestURI);
                final HttpResponse<String> rsp = client.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                assertEquals(200, rsp.statusCode(), "Unexpected response code");
                // expect the alt service to respond
                assertEquals(ORIGIN_SERVER_RESPONSE_MESSAGE, rsp.body(),
                        "Unexpected response body");
                assertEquals(HTTP_2, rsp.version(), "Unexpected HTTP version in response");
            }
        } finally {
            safeStop(testInput.originServer);
            safeStop(testInput.altServer);
        }
    }


    /**
     * - Keystore K1 is constructed with a PrivateKey PK1 and certificate whose subject is
     * alternate server hostname
     * - K1 is used to construct a SSLContext S1 and that S1 is used by alternate server
     * - The same SSLContext S1 is used by the HttpClient (and thus will trust the alternate
     * server's certificate)
     * - Origin server runs only on HTTP
     * - Any alt-svc advertised by origin server MUST NOT be used by the client, because origin
     * server runs on HTTP and as a result the "reasonable assurance" for the origin server
     * cannot be satisfied.
     * <p>
     * Once the servers are started, this test method does the following:
     * <p>
     * - Client constructs a HTTP2 request addressed to origin server
     * - Origin server responds with a 200 response and also with alt-svc header pointing to
     * an alternate server
     * - Client verifies the response as well as presence of the alt-svc header value
     * - Client issues the request again, to the origin server, this time with HTTP3 as the request
     * version
     * - The request is expected to be handled by the origin server again and the advertised
     * alternate service MUST NOT be used (due to reasons noted above)
     */
    @Test
    public void testAltServiceAdvertisedByHTTPOrigin() throws Exception {
        // create a keystore which contains a PrivateKey entry and a certificate associated with
        // that key. the certificate's subject will be alternate server's hostname. Thus, the
        // certificate is valid for alternate server
        final KeyStore keyStore = generateKeyStore(ALT_SERVER_HOSTNAME);
        System.out.println("Generated a keystore with certificate: " +
                keyStore.getCertificate(DynamicKeyStoreUtil.DEFAULT_ALIAS));
        // create a SSLContext that will be used by the alternate server and the HttpClient and
        // will be backed by the keystore we just created. Thus, the HttpClient will trust the
        // certificate belonging to that keystore
        final SSLContext sslContext = DynamicKeyStoreUtil.createSSLContext(keyStore);

        // start the servers
        final TestInput testInput = startHttpOriginHttpsAltServer(sslContext);
        try {
            final HttpClient client = newClientBuilderForH3()
                    .proxy(NO_PROXY)
                    .sslContext(sslContext)
                    .version(HTTP_3)
                    .build();
            // send a HTTP2 request to a server which is expected to respond back
            // with a 200 response and an alt-svc header pointing to another/different H3 server
            final URI requestURI = testInput.requestURI;
            final HttpRequest request = HttpRequest.newBuilder()
                    .version(HTTP_2).GET()
                    .uri(requestURI).build();
            System.out.println("Issuing " + request.version() + " request to " + requestURI);
            final HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, response.statusCode(), "Unexpected response code");
            assertEquals(HTTP_2, response.version(), "Unexpected HTTP version in response");
            // the origin server is expected to respond
            assertEquals(ORIGIN_SERVER_RESPONSE_MESSAGE, response.body(), "Unexpected response" +
                    " body");

            // verify the origin server sent back a alt-svc header
            final Optional<String> altSvcHeader = response.headers().firstValue("alt-svc");
            assertTrue(altSvcHeader.isPresent(), "alt-svc header is missing in response");
            final String actualAltSvcHeader = altSvcHeader.get();
            System.out.println("Received alt-svc header value: " + actualAltSvcHeader);
            assertTrue(actualAltSvcHeader.contains(testInput.expectedAltSvcHeader),
                    "Unexpected alt-svc header value: " + actualAltSvcHeader
                            + ", was expected to contain: " + testInput.expectedAltSvcHeader);

            // now issue few more requests to the same address, but as a HTTP3 version. Expect each
            // of these requests too, to be handled by the origin server (since the previously
            // advertised alt server isn't expected to satisfy the "reasonable assurances"
            // expectations)
            for (int i = 1; i <= 3; i++) {
                final HttpRequest h3Request = HttpRequest.newBuilder()
                        .version(HTTP_3).GET().uri(requestURI)
                        .setOption(H3_DISCOVERY, ALT_SVC)
                        .build();
                System.out.println("Again(" + i + ") issuing " + h3Request.version()
                        + " request to " + requestURI);
                final HttpResponse<String> rsp = client.send(h3Request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                assertEquals(200, rsp.statusCode(), "Unexpected response code");
                // even though the request is HTTP_3 version, the client will fall back to HTTP_2
                // since the origin server does run on TLS and thus cannot use HTTP_3 (which
                // requires TLS)
                assertEquals(HTTP_2, rsp.version(), "Unexpected HTTP version in response");
                // expect the alt service to respond
                assertEquals(ORIGIN_SERVER_RESPONSE_MESSAGE, rsp.body(),
                        "Unexpected response body");
            }
        } finally {
            safeStop(testInput.originServer);
            safeStop(testInput.altServer);
        }
    }

    private static SSLContext sslCtxWithTrustedCerts(final List<Certificate> trustedCerts)
            throws Exception {
        Objects.requireNonNull(trustedCerts);
        // start with a blank keystore
        final KeyStore keyStore = DynamicKeyStoreUtil.generateBlankKeyStore();
        final String aliasPrefix = "trusted-certs-alias-";
        int i = 1;
        for (final Certificate cert : trustedCerts) {
            // add the cert as a trusted certificate to the keystore
            keyStore.setCertificateEntry(aliasPrefix + i, cert);
            i++;
        }
        System.out.println("Generated a keystore with (only) trusted certs: ");
        for (--i; i > 0; i--) {
            System.out.println(keyStore.getCertificate(aliasPrefix + i));
        }
        final TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        // use the generated keystore for this trust manager
        tmf.init(keyStore);

        final String protocol = "TLS";
        final SSLContext ctx = SSLContext.getInstance(protocol);
        // initialize the SSLContext with the trust manager which trusts the passed certificates
        ctx.init(null, tmf.getTrustManagers(), null);
        return ctx;
    }
}
