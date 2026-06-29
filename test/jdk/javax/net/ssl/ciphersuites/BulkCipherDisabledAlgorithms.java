/*
 * @test
 * @bug 8387124
 * @summary Test TLS cipher suite disabling via jdk.tls.disabledAlgorithms,
 *          including matching on bulk cipher components.
 * @library /test/lib
 *          /javax/net/ssl/TLSCommon
 *          /javax/net/ssl/templates
 */

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.*;

import jdk.test.lib.process.Proc;

import java.io.IOException;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

public class BulkCipherDisabledAlgorithms {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            List<String[]> tests = buildTests();

            for (String[] test : tests) {
                String suite = test[0];
                String disabled = test[1];
                String expected = test[2];

                System.out.println("=================================================");
                System.out.println("Testing: suite=" + suite +
                        ", disabled=" + disabled +
                        ", expected=" + expected);

                Proc p = Proc.create(
                        BulkCipherDisabledAlgorithms.class.getName())
                        .args(suite, expected)
                        .secprop("jdk.tls.disabledAlgorithms", disabled)
                        .inheritIO();

                p.start().waitFor(0);
            }

            System.out.println("TEST PASS - OK");
            return;
        }

        boolean expectedDisabled = args[1].equals("disabled");

        testCipherSuiteVisibility(args[0], expectedDisabled);

        testHandshake(args[0], expectedDisabled);
    }

    private static CipherSuite[] getCipherSuites(boolean enabled) throws NoSuchAlgorithmException {

        SSLEngine engine = SSLContext.getDefault().createSSLEngine();

        String[] suites = enabled
                ? engine.getEnabledCipherSuites()
                : engine.getSupportedCipherSuites();

        return Arrays.stream(suites)
                .map(CipherSuite::cipherSuite)
                .filter(cs -> cs != CipherSuite.TLS_EMPTY_RENEGOTIATION_INFO_SCSV)
                .toArray(CipherSuite[]::new);
    }

    private static List<String[]> buildTests() throws NoSuchAlgorithmException {
        List<String[]> tests = new ArrayList<>();
        CipherSuite[] suites = getCipherSuites(false);

        for (CipherSuite suite : suites) {
            String suiteName = suite.name();
            String bulk = extractBulkCipher(suiteName);

            tests.add(new String[] { suiteName, suiteName, "disabled" });
            tests.add(new String[] { suiteName, bulk, "disabled" });

            for (CipherSuite other : suites) {
                // Negative test case: disable a different bulk cipher than the one
                // used by the current suite. This ensures that the suite remains
                // enabled and a successful TLS handshake can still be negotiated.
                if (other == suite) {
                    continue;
                }

                String otherBulk = extractBulkCipher(other.name());

                if (!bulk.equals(otherBulk)
                        && !suiteName.contains(otherBulk)) {
                    tests.add(new String[] { suiteName, otherBulk, "enabled" });
                    break;
                }
            }
        }

        return tests;
    }

    /**
     * Separator used in TLS cipher suite names to mark the start of
     * the bulk cipher component (e.g. TLS_RSA_WITH_AES_128_CBC_SHA).
     */
    private static final String WITH = "_WITH_";

    private static String extractBulkCipher(String suite) {
        if (suite.contains(WITH)) {
            String after = suite.substring(suite.indexOf(WITH) + WITH.length());
            int last = after.lastIndexOf('_');
            return after.substring(0, last);
        } else {
            int first = suite.indexOf('_');
            int last = suite.lastIndexOf('_');
            return suite.substring(first + 1, last);
        }
    }

    private static void testCipherSuiteVisibility(String suite, boolean expectedDisabled)
            throws NoSuchAlgorithmException, KeyManagementException {
        boolean visible = Arrays.asList(getCipherSuites(true))
                .contains(CipherSuite.cipherSuite(suite));

        if (!expectedDisabled && !visible) {
            throw new RuntimeException(
                    "Cipher suite '" + suite + "' not visible but expected to be enabled");
        } else if (expectedDisabled && visible) {
            throw new RuntimeException(
                    "Cipher suite '" + suite + "' visible but expected to be disabled");
        }
    }

    private static void testHandshake(String suite, boolean expectedDisabled) throws Exception {
        try (SSLServer server = new SSLServer(new String[] { suite })) {
            Thread t = new Thread(server);
            t.setDaemon(true);
            t.start();

            while (!server.running) {
                Thread.sleep(50);
            }

            try (SSLClient client = new SSLClient(server.getPort(), suite)) {
                client.connect();
                if (expectedDisabled) {
                    throw new RuntimeException(
                            "Handshake succeeded but expected failure for suite=" + suite);
                }
            } catch (SSLHandshakeException e) {
                if (!expectedDisabled) {
                    throw new RuntimeException(
                            "Handshake failed unexpectedly for suite=" + suite);
                }
            }
        }
    }

    private static class SSLServer extends SSLContextTemplate implements Runnable, AutoCloseable {
        private final SSLServerSocket socket;
        volatile boolean running = false;

        public SSLServer(String[] suites) throws Exception {
            SSLContext ctx = createServerSSLContext();
            socket = (SSLServerSocket) ctx.getServerSocketFactory()
                    .createServerSocket(0, 0, InetAddress.getLoopbackAddress());

            socket.setEnabledCipherSuites(suites);
        }

        @Override
        public void run() {
            running = true;
            try (SSLSocket s = (SSLSocket) socket.accept()) {
                s.startHandshake();
            } catch (SSLHandshakeException ignored) {
                // expected in this test
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        int getPort() {
            return socket.getLocalPort();
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }

    private static class SSLClient extends SSLContextTemplate implements AutoCloseable {
        private final SSLSocket socket;

        public SSLClient(int port, String suite) throws Exception {
            SSLContext ctx = createClientSSLContext();
            socket = (SSLSocket) ctx.getSocketFactory()
                    .createSocket(InetAddress.getLoopbackAddress(), port);
            socket.setEnabledCipherSuites(new String[] { suite });
        }

        void connect() throws Exception {
            socket.startHandshake();
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }
}