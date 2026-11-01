/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

import javax.naming.Context;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.StartTlsRequest;
import javax.naming.ldap.StartTlsResponse;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import com.sun.jndi.ldap.BerEncoder;
import com.sun.jndi.ldap.Connection;
import jdk.test.lib.net.URIBuilder;
import jdk.test.lib.security.CertUtils;
import jdk.test.lib.security.KeyEntry;
import jdk.test.lib.security.KeyStoreUtils;
import jdk.test.lib.security.SSLContextBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8261289
 * @summary Verify the behaviour of LDAPv3 Extended Response for StartTLS
 * @modules java.naming/com.sun.jndi.ldap
 *          java.naming/com.sun.jndi.ldap.ext:+open
 * @library /test/lib lib/
 * @build jdk.test.lib.net.URIBuilder
 *        jdk.test.lib.security.SSLContextBuilder
 *        jdk.test.lib.security.KeyStoreUtils
 *        BaseLdapServer LdapMessage
 * @run junit ${test.main.class}
 */
class LdapStartTlsTest {

    private static final byte BER_TYPE_LDAP_SEQUENCE = 0x30;
    private static final byte BER_TYPE_EXTENDED_RESPONSE = 0x78;
    private static final byte BER_TYPE_ENUM = 0x0a;
    private static final byte BER_TYPE_LDAP_SEARCH_RESULT_DONE_OP = 0x65;

    static List<Arguments> failedNegotiations() throws Exception {
        // a SSLContext which we expect to cause the TLS handshake to fail when
        // handshaking with a loopback address
        final SSLContext localhostOnlySSLCtx = onlyLocalHostSSLCtx();

        return List.of(
                Arguments.of(new Server(localhostOnlySSLCtx), null),
                Arguments.of(new Server(localhostOnlySSLCtx), new SimpleHostnameVerifier(false))
        );
    }

    static List<Arguments> successfulNegotiations() throws Exception {
        // a SSLContext constructed out of a keystore which has relevant
        // subject or subject alternate name and doesn't require a custom
        // hostname verifier to allow for TLS verification to succeed
        final String keyStoreFile = System.getProperty("test.src") + File.separator + "ksWithSAN";
        final String keyStorePass = "welcome1";
        final SSLContext sslContext = sslCtxFromKeyStoreFile(keyStoreFile, keyStorePass);

        // a different SSLContext which requires a custom hostname verifier to pass TLS verification
        final SSLContext localhostOnlySSLCtx = onlyLocalHostSSLCtx();

        return List.of(
                Arguments.of(new Server(sslContext), null),
                Arguments.of(new Server(localhostOnlySSLCtx), new SimpleHostnameVerifier(true))
        );
    }

    private static SSLContext onlyLocalHostSSLCtx() throws Exception {
        // a cert which is issued to "localhost" (and without any subject alternative name
        // for loopback IP address), which we expect the test to reject during TLS handshake
        final String cert = CertUtils.ECDSA_CERT;
        final KeyEntry ke = new KeyEntry("EC", CertUtils.ECDSA_KEY, new String[]{cert});
        return SSLContextBuilder.builder()
                .keyStore(KeyStoreUtils.createKeyStore(new KeyEntry[]{ke}))
                .trustStore(KeyStoreUtils.createTrustStore(new String[]{cert}))
                .build();
    }

    private static SSLContext sslCtxFromKeyStoreFile(final String keyStoreFile,
                                                     final String keyStorePass) throws Exception {
        final KeyStore keyStore = KeyStoreUtils.loadKeyStore(keyStoreFile, keyStorePass);
        return SSLContextBuilder.builder()
                .keyStore(keyStore)
                .trustStore(keyStore)
                .kmfPassphrase(keyStorePass)
                .build();
    }

    @ParameterizedTest
    @MethodSource(value = "failedNegotiations")
    void testFailedNegotiation(final Server server, final SimpleHostnameVerifier verifier)
            throws Exception {

        try (server) {
            server.start();
            System.err.println("server started at " + server.getAddress());

            final Hashtable<String, String> envProps = createEnvProps(server);
            final LdapContext ctx = new InitialLdapContext(envProps, null);

            StartTlsResponse startTlsResp = null;
            try {
                startTlsResp = (StartTlsResponse) ctx.extendedOperation(new StartTlsRequest());
                assertNotNull(startTlsResp, "StartTlsResponse is null");
                if (verifier != null) {
                    startTlsResp.setHostnameVerifier(verifier);
                }

                final Connection ldapConn = getConnection(startTlsResp);
                assertNotNull(ldapConn, "LDAP connection is null in " + startTlsResp);
                // capture the input/output stream on the LDAP connection
                final LdapConnStreams before = getStreams(ldapConn);

                // initiate the StartTLS negotiation/handshake
                final StartTlsResponse tlsRsp = startTlsResp;
                final SSLPeerUnverifiedException spue = assertThrows(
                        SSLPeerUnverifiedException.class,
                        () -> tlsRsp.negotiate(server.sslContext.getSocketFactory()));
                System.err.println("got expected exception: " + spue);

                if (verifier != null) {
                    // assert that the custom hostname verifier was invoked
                    assertTrue(verifier.invoked, "custom HostnameVerifier was not invoked");
                }
                // verify that after the failed StartTLS negotitation, the LDAP connection
                // streams are the ones that were prior to the negotiation
                final LdapConnStreams after = getStreams(ldapConn);
                assertSame(before.in, after.in,
                        "unexpected InputStream on LDAP connection after a failed StartTLS negotiation");
                assertSame(before.out, after.out,
                        "unexpected OutputStream on LDAP connection after a failed StartTLS negotiation");
            } finally {
                if (startTlsResp != null) {
                    startTlsResp.close();
                }
                ctx.close();
            }
        }
    }

    @ParameterizedTest
    @MethodSource(value = "successfulNegotiations")
    void testSuccessfulNegotiation(final Server server, final SimpleHostnameVerifier verifier)
            throws Exception {
        try (server) {
            server.start();
            System.err.println("server started at " + server.getAddress());

            final Hashtable<String, String> envProps = createEnvProps(server);
            final LdapContext ctx = new InitialLdapContext(envProps, null);

            StartTlsResponse startTlsResp = null;
            try {
                startTlsResp = (StartTlsResponse) ctx.extendedOperation(new StartTlsRequest());
                assertNotNull(startTlsResp, "StartTlsResponse is null");
                if (verifier != null) {
                    startTlsResp.setHostnameVerifier(verifier);
                }

                final Connection ldapConn = getConnection(startTlsResp);
                assertNotNull(ldapConn, "LDAP connection is null in " + startTlsResp);
                // capture the input/output stream on the LDAP connection
                final LdapConnStreams before = getStreams(ldapConn);
                // do the TLS negotiation. expected to complete successfully
                final SSLSession sess = startTlsResp.negotiate(server.sslContext.getSocketFactory());
                assertNotNull(sess, "SSLSession is null after StartTLS negotiation");
                assertTrue(sess.isValid(), "SSLSession is invalid after StartTLS negotiation");
                if (verifier != null) {
                    // assert that the custom hostname verifier was invoked
                    assertTrue(verifier.invoked, "custom HostnameVerifier was not invoked");
                }

                // TLS session established, now do a trivial LDAP search and expect it to
                // work fine
                final Object result = ctx.lookup("CN=foobar");
                System.err.println("got result " + result);
                assertNotNull(result, "Context.lookup() returned null");

                // TLS negotiation as well as a trivial LDAP search completed fine,
                // now close the StartTlsResponse
                startTlsResp.close();

                // verify that after the StartTlsResponse is closed, the streams
                // of the underlying LDAP connection are switched back to the ones that were there
                // before the TLS negotiation
                final LdapConnStreams after = getStreams(ldapConn);
                assertSame(before.in, after.in,
                        "unexpected InputStream on LDAP connection after StartTlsResponse was closed");
                assertSame(before.out, after.out,
                        "unexpected OutputStream on LDAP connection after StartTlsResponse was closed");
            } finally {
                if (startTlsResp != null) {
                    startTlsResp.close();
                }
                ctx.close();
            }
        }
    }

    private static Hashtable<String, String> createEnvProps(final Server server) throws Exception {
        final Hashtable<String, String> envProps = new Hashtable<>();
        envProps.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        final String providerUrl = URIBuilder.newBuilder()
                .scheme("ldap")
                .host(server.getInetAddress().getHostAddress())
                .port(server.getPort())
                .build().toString();
        envProps.put(Context.PROVIDER_URL, providerUrl);
        // explicitly set LDAP version to 3 to prevent LDAP BIND requests
        // during LdapCtx instantiation
        envProps.put("java.naming.ldap.version", "3");
        return envProps;
    }

    // using reflection, returns the LDAP connection instance associated with the StartTlsResponse
    private static Connection getConnection(final StartTlsResponse tlsResponse) throws Exception {
        final Field connField = tlsResponse.getClass().getDeclaredField("ldapConnection");
        connField.setAccessible(true);
        return (Connection) connField.get(tlsResponse);
    }

    // returns the input and output stream associated with the LDAP Connection
    private static LdapConnStreams getStreams(final Connection connection) {
        return new LdapConnStreams(connection.inStream, connection.outStream);
    }

    private record LdapConnStreams(InputStream in, OutputStream out) {
    }

    // a trivial HostnameVerifier which returns a given verification result from
    // its verify method
    private static final class SimpleHostnameVerifier implements HostnameVerifier {
        private final boolean verificationResult;
        private boolean invoked;

        private SimpleHostnameVerifier(final boolean verificationResult) {
            this.verificationResult = verificationResult;
        }

        @Override
        public boolean verify(final String hostname, final SSLSession session) {
            this.invoked = true;
            return this.verificationResult;
        }
    }

    // the LDAP server used in the test
    private static final class Server extends BaseLdapServer implements AutoCloseable {
        private final SSLContext sslContext;

        private Server(final SSLContext sslContext) throws IOException {
            super(new ServerSocket(0, 0, InetAddress.getLoopbackAddress()));
            this.sslContext = sslContext;
        }

        private InetSocketAddress getAddress() {
            return new InetSocketAddress(this.getInetAddress(), this.getPort());
        }

        // handles and responds to the incoming LDAP request
        @Override
        protected void handleRequestEx(final Socket socket,
                                       final LdapMessage request,
                                       final OutputStream out,
                                       final BaseLdapServer.ConnWrapper connWrapper)
                throws IOException {
            switch (request.getOperation()) {
                case EXTENDED_REQUEST: {
                    System.err.println("handling ExtendedRequest from " + socket);
                    // write out the ExtendedResponse
                    final byte[] resp = makeExtendedResponse((byte) request.getMessageID());
                    out.write(resp);
                    out.flush();
                    System.err.println("ExtendedResponse: " + Arrays.toString(resp));
                    // switch to using TLS over the server connection
                    switchToTLSConnection(socket, connWrapper);
                    return;
                }
                case SEARCH_REQUEST: {
                    System.err.println("handling SEARCH_REQUEST with id: "
                            + request.getMessageID() + " from " + socket);
                    // write out a search response
                    final byte[] resp = makeSearchResultDone((byte) request.getMessageID());
                    out.write(resp);
                    out.flush();
                    System.err.println("search response: " + Arrays.toString(resp));
                    break;
                }
                default: {
                    throw new IOException("unexpected operation type: " + request.getOperation()
                            + ", request: " + request);
                }
            }
        }

        @Override
        public void close() {
            System.err.println("stopping server " + this.getAddress());
            super.close();
        }

        // switch the underlying server implementation to expect TLS content
        // on the connection
        private void switchToTLSConnection(final Socket socket,
                                           final BaseLdapServer.ConnWrapper connWrapper)
                throws IOException {
            SSLSocket sslSocket;
            final SSLSocketFactory factory = this.sslContext.getSocketFactory();
            try {
                sslSocket = (SSLSocket) factory.createSocket(socket, null, socket.getLocalPort(), false);
            } catch (Exception e) {
                throw new IOException(e);
            }
            sslSocket.setUseClientMode(false);
            connWrapper.setWrapper(sslSocket);
        }

        // construct a ExtendedResponse LDAP message for the given message id
        private static byte[] makeExtendedResponse(final byte msgId) throws IOException {
            // LDAPResult ::= SEQUENCE {
            //    resultCode         ENUMERATED {
            //        success                      (0),
            //        ...
            //    },
            //    matchedDN          LDAPDN,
            //    diagnosticMessage  LDAPString,
            //    referral           [3] Referral OPTIONAL
            // }
            //
            // ExtendedResponse ::= [APPLICATION 24] SEQUENCE {
            //   COMPONENTS OF LDAPResult,
            //   responseName     [10] LDAPOID OPTIONAL,
            //   responseValue    [11] OCTET STRING OPTIONAL
            // }
            final BerEncoder ber = new BerEncoder();
            ber.beginSeq(BER_TYPE_LDAP_SEQUENCE);
            ber.encodeInt(msgId);
            {
                ber.beginSeq(BER_TYPE_EXTENDED_RESPONSE);
                ber.encodeInt(0, BER_TYPE_ENUM); // resultCode = 0 == success
                ber.encodeString("", false); // matchedDN == empty string
                ber.encodeString("", false); // diagnosticMessage == empty string
                ber.endSeq();
            }
            ber.endSeq();

            return ber.getTrimmedBuf();
        }

        // construct a SearchResultDone LDAP message for the given message id
        private static byte[] makeSearchResultDone(final byte msgId) throws IOException {
            // SearchResultDone ::= [APPLICATION 5] LDAPResult
            //
            // LDAPResult ::= SEQUENCE {
            //      resultCode         ENUMERATED {
            //           success                      (0),
            //           ...
            //      },
            //      matchedDN          LDAPDN,
            //      diagnosticMessage  LDAPString,
            //      referral           [3] Referral OPTIONAL
            // }
            final BerEncoder ber = new BerEncoder();
            ber.beginSeq(BER_TYPE_LDAP_SEQUENCE);
            ber.encodeInt(msgId);
            {
                ber.beginSeq(BER_TYPE_LDAP_SEARCH_RESULT_DONE_OP);
                ber.encodeInt(0, BER_TYPE_ENUM); // resultCode = 0 == success
                ber.encodeString("", false); // matchedDN == empty string
                ber.encodeString("", false); // diagnosticMessage == empty string
                ber.endSeq();
            }
            ber.endSeq();

            return ber.getTrimmedBuf();
        }
    }
}
