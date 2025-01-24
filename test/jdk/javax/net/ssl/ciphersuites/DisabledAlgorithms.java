/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8076221 8211883 8163327 8279164 8245545
 * @summary Check if weak cipher suites are disabled
 * @library /javax/net/ssl/templates
 * @modules jdk.crypto.ec
 * @run main/othervm DisabledAlgorithms default
 * @run main/othervm DisabledAlgorithms empty
 */

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Security;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/*
 * This test verifies that setEnabledProtocols() does not override the
 * jdk.tls.disabledAlgorithms property. Both the client and server throw
 * an exception when creating a handshake context.
 *
 * In the TLSWontNegotiateDisabledCipherAlgoos test, one side of the connection
 * disables the cipher suites and the other side enables them and verifies
 * that the handshake cannot complete successfully.
 */
public class DisabledAlgorithms {

    public static final SSLContextTemplate.Cert[] CERTIFICATES = {
            SSLContextTemplate.Cert.EE_DSA_SHA1_1024,
            SSLContextTemplate.Cert.EE_DSA_SHA224_1024,
            SSLContextTemplate.Cert.EE_DSA_SHA256_1024,
            SSLContextTemplate.Cert.CA_ECDSA_SECP256R1,
            SSLContextTemplate.Cert.CA_RSA_2048
    };

    // disabled 3DES, DES, RC4, NULL, anon, and ECDH cipher suites
    public static final String[] DISABLED_CIPHERSUITES
            = new String[]{
            "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
            "TLS_ECDHE_RSA_WITH_RC4_128_SHA",
            "SSL_RSA_WITH_RC4_128_SHA",
            "TLS_ECDH_ECDSA_WITH_RC4_128_SHA",
            "TLS_ECDH_RSA_WITH_RC4_128_SHA",
            "SSL_RSA_WITH_RC4_128_MD5",
            "TLS_ECDH_anon_WITH_RC4_128_SHA",
            "SSL_DH_anon_WITH_RC4_128_MD5",
            "SSL_RSA_WITH_NULL_MD5",
            "SSL_RSA_WITH_NULL_SHA",
            "TLS_RSA_WITH_NULL_SHA256",
            "TLS_ECDH_ECDSA_WITH_NULL_SHA",
            "TLS_ECDHE_ECDSA_WITH_NULL_SHA",
            "TLS_ECDH_RSA_WITH_NULL_SHA",
            "TLS_ECDHE_RSA_WITH_NULL_SHA",
            "TLS_ECDH_anon_WITH_NULL_SHA",
            "SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA",
            "SSL_DH_anon_EXPORT_WITH_RC4_40_MD5",
            "SSL_DH_anon_WITH_3DES_EDE_CBC_SHA",
            "SSL_DH_anon_WITH_DES_CBC_SHA",
            "SSL_DH_anon_WITH_RC4_128_MD5",
            "TLS_DH_anon_WITH_AES_128_CBC_SHA",
            "TLS_DH_anon_WITH_AES_128_CBC_SHA256",
            "TLS_DH_anon_WITH_AES_128_GCM_SHA256",
            "TLS_DH_anon_WITH_AES_256_CBC_SHA",
            "TLS_DH_anon_WITH_AES_256_CBC_SHA256",
            "TLS_DH_anon_WITH_AES_256_GCM_SHA384",
            "SSL_RSA_WITH_DES_CBC_SHA",
            "SSL_DHE_RSA_WITH_DES_CBC_SHA",
            "SSL_DHE_DSS_WITH_DES_CBC_SHA",
            "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
            "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
            "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA",
            "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
            "TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDH_anon_WITH_AES_128_CBC_SHA",
            "TLS_ECDH_anon_WITH_AES_256_CBC_SHA",
            "TLS_ECDH_anon_WITH_NULL_SHA",
            "TLS_ECDH_anon_WITH_RC4_128_SHA",
            "TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",
            "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA",
            "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA",
            "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256",
            "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",
            "TLS_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_RSA_WITH_AES_256_CBC_SHA256",
            "TLS_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_RSA_WITH_AES_256_CBC_SHA",
            "TLS_RSA_WITH_AES_128_CBC_SHA"
    };

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new RuntimeException("No parameters specified");
        }

        switch (args[0]) {
            case "default":
                // use default jdk.tls.disabledAlgorithms
                System.out.println("jdk.tls.disabledAlgorithms = "
                        + Security.getProperty("jdk.tls.disabledAlgorithms"));

                // check that disabled cipher suites can't be used by default
                checkFailure(DISABLED_CIPHERSUITES);
                break;
            case "empty":
                // reset jdk.tls.disabledAlgorithms
                Security.setProperty("jdk.tls.disabledAlgorithms", "");
                System.out.println("jdk.tls.disabledAlgorithms = "
                        + Security.getProperty("jdk.tls.disabledAlgorithms"));
                // reset jdk.certpath.disabledAlgorithms. This is necessary
                // to allow the RSA_EXPORT suites to pass which use an RSA 512
                // bit key which violates the default certpath constraints.
                Security.setProperty("jdk.certpath.disabledAlgorithms", "");
                System.out.println("jdk.certpath.disabledAlgorithms = "
                        + Security.getProperty("jdk.certpath.disabledAlgorithms"));

                // check that disabled cipher suites can be used if
                // jdk.{tls,certpath}.disabledAlgorithms is empty
                checkSuccess(DISABLED_CIPHERSUITES);
                break;
            default:
                throw new RuntimeException("Wrong parameter: " + args[0]);
        }

        System.out.println("Test passed");
    }

    /*
     * Checks if that specified cipher suites cannot be used.
     */
    private static void checkFailure(String[] ciphersuites) throws Exception {
        try (SSLServer server = new SSLServer(ciphersuites)) {
            startNewThread(server);
            while (!server.isRunning()) {
                sleep();
            }

            int port = server.getPort();
            for (String ciphersuite : ciphersuites) {
                try (SSLClient client = new SSLClient(port, ciphersuite)) {
                    client.connect();
                    throw new RuntimeException("Expected SSLHandshakeException "
                            + "not thrown");
                } catch (SSLHandshakeException e) {
                    System.out.println("Got expected exception on client side: "
                            + e);
                }
            }

            server.stop();
            while (server.isRunning()) {
                sleep();
            }

            if (!server.sslError()) {
                throw new RuntimeException("Expected SSL exception "
                        + "not thrown on server side");
            }
        }

    }

    /*
     * Checks if specified cipher suites can be used.
     */
    private static void checkSuccess(String[] ciphersuites) throws Exception {
        try (SSLServer server = new SSLServer(ciphersuites)) {
            startNewThread(server);
            while (!server.isRunning()) {
                sleep();
            }

            int port = server.getPort();
            for (String ciphersuite : ciphersuites) {
                try (SSLClient client = new SSLClient(port, ciphersuite)) {
                    client.connect();
                    String negotiated = client.getNegotiatedCipherSuite();
                    System.out.println("Negotiated cipher suite: "
                            + negotiated);
                    if (!negotiated.equals(ciphersuite)) {
                        throw new RuntimeException("Unexpected cipher suite: "
                                + negotiated);
                    }
                }
            }

            server.stop();
            while (server.isRunning()) {
                sleep();
            }

            if (server.error()) {
                throw new RuntimeException("Unexpected error on server side");
            }
        }

    }

    private static Thread startNewThread(SSLServer server) {
        Thread serverThread = new Thread(server, "SSL server thread");
        serverThread.setDaemon(true);
        serverThread.start();
        return serverThread;
    }

    private static void sleep() {
        try {
            TimeUnit.MILLISECONDS.sleep(50);
        } catch (InterruptedException e) {
            // do nothing
        }
    }

    static class SSLServer extends SSLContextTemplate implements Runnable, AutoCloseable {


        private final SSLServerSocket ssocket;
        private volatile boolean stopped = false;
        private volatile boolean running = false;
        private volatile boolean sslError = false;
        private volatile boolean otherError = false;

        private SSLServer(String[] ciphersuites) throws Exception {
            SSLContext context = createSSLContext(null,
                    DisabledAlgorithms.CERTIFICATES, getServerContextParameters());
            SSLServerSocketFactory ssf = context.getServerSocketFactory();
            SSLServerSocket ssocket = (SSLServerSocket)
                    ssf.createServerSocket(0);

            if (ciphersuites != null) {
                System.out.println("Server: enable cipher suites: "
                        + java.util.Arrays.toString(ciphersuites));
                ssocket.setEnabledCipherSuites(ciphersuites);
            }

            this.ssocket = ssocket;
        }

        @Override
        public void run() {
            System.out.println("Server: started");
            running = true;
            while (!stopped) {
                try (SSLSocket socket = (SSLSocket) ssocket.accept()) {
                    System.out.println("Server: accepted client connection");
                    InputStream in = socket.getInputStream();
                    OutputStream out = socket.getOutputStream();
                    int b = in.read();
                    if (b < 0) {
                        throw new IOException("Unexpected EOF");
                    }
                    System.out.println("Server: send data: " + b);
                    out.write(b);
                    out.flush();
                    socket.getSession().invalidate();
                } catch (SSLHandshakeException e) {
                    System.out.println("Server: run: " + e);
                    sslError = true;
                } catch (IOException e) {
                    if (!stopped) {
                        System.out.println("Server: run: unexpected exception: "
                                + e);
                        e.printStackTrace();
                        otherError = true;
                        stopped = true;
                    } else {
                        System.out.println("Server: run: " + e);
                        System.out.println("The exception above occurred "
                                + "because socket was closed, "
                                + "please ignore it");
                    }
                }
            }

            System.out.println("Server: finished");
            running = false;
        }

        int getPort() {
            return ssocket.getLocalPort();
        }

        String[] getEnabledCiperSuites() {
            return ssocket.getEnabledCipherSuites();
        }

        boolean isRunning() {
            return running;
        }

        boolean sslError() {
            return sslError;
        }

        boolean error() {
            return sslError || otherError;
        }

        void stop() {
            stopped = true;
            if (!ssocket.isClosed()) {
                try {
                    System.out.println("Server: close socket");
                    ssocket.close();
                } catch (IOException e) {
                    System.out.println("Server: close: " + e);
                }
            }
        }

        @Override
        public void close() {
            stop();
        }
    }

    static class SSLClient extends SSLContextTemplate implements AutoCloseable {

        private final SSLSocket socket;

        private SSLClient(int port, String ciphersuite) throws Exception {
            SSLContext context = createSSLContext(DisabledAlgorithms.CERTIFICATES,
                    null, getClientContextParameters());
            SSLSocketFactory ssf = context.getSocketFactory();
            SSLSocket socket = (SSLSocket) ssf.createSocket("localhost", port);

            if (ciphersuite != null) {
                System.out.println("Client: enable cipher suite: "
                        + ciphersuite);
                socket.setEnabledCipherSuites(new String[]{ciphersuite});
            }
            this.socket = socket;
        }

        void connect() throws IOException {
            System.out.println("Client: connect to server");
            try (
                    BufferedInputStream bis = new BufferedInputStream(
                            socket.getInputStream());
                    BufferedOutputStream bos = new BufferedOutputStream(
                            socket.getOutputStream())) {
                bos.write('x');
                bos.flush();

                int read = bis.read();
                if (read < 0) {
                    throw new IOException("Client: couldn't read a response");
                }
                socket.getSession().invalidate();
            }
        }

        String[] getEnabledCiperSuites() {
            return socket.getEnabledCipherSuites();
        }

        String getNegotiatedCipherSuite() {
            return socket.getSession().getCipherSuite();
        }

        @Override
        public void close() throws Exception {
            if (!socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    System.out.println("Client: close: " + e);
                }
            }
        }
    }
}
