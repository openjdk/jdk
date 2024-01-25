/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8206925
 * @library /javax/net/ssl/templates
 * @summary Support the certificate_authorities extension
 * @run main/othervm TooManyCAs
 * @run main/othervm -Djdk.tls.client.enableCAExtension=true TooManyCAs
 */
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;
import java.io.*;
import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.CyclicBarrier;

/**
 * Check if the connection can be established if the client or server trusts
 * more CAs such that it exceeds the size limit of the certificate_authorities
 * extension (2^16).
 */
public class TooManyCAs extends SSLSocketTemplate {

    private static final String[][][] protocols = {
            {{"TLSv1.3"}, {"TLSv1.3"}},
            {{"TLSv1.3", "TLSv1.2"}, {"TLSv1.3"}},
            {{"TLSv1.3"}, {"TLSv1.3", "TLSv1.2"}},
    };

    private final String[] clientProtocols;
    private final String[] serverProtocols;
    private final boolean needClientAuth;

    /*
     * Used to synchronize client and server; there were intermittent
     * failures on Windows due to the connection being killed.
     */
    private final CyclicBarrier barrier = new CyclicBarrier(2);

    TooManyCAs(int index, boolean needClientAuth) {
        this.clientProtocols = protocols[index][0];
        this.serverProtocols = protocols[index][1];
        this.needClientAuth = needClientAuth;

        System.out.printf("Testing%n\tclient protocols: %s%n\t" +
                "server protocols: %s%n\tneed client auth: %s%n",
                String.join(", ", clientProtocols),
                String.join(", ", serverProtocols),
                needClientAuth);
    }

    @Override
    protected void configureClientSocket(SSLSocket clientSocket) {
        System.out.println("Setting client protocol(s): "
                + String.join(",", clientProtocols));

        clientSocket.setEnabledProtocols(clientProtocols);
    }

    @Override
    protected void configureServerSocket(SSLServerSocket serverSocket) {
        serverSocket.setNeedClientAuth(needClientAuth);
        serverSocket.setEnableSessionCreation(true);
        serverSocket.setUseClientMode(false);

        System.out.println("Setting server protocol(s): "
                + String.join(",", serverProtocols));

        serverSocket.setEnabledProtocols(serverProtocols);
    }

    @Override
    protected TrustManager createClientTrustManager() throws Exception {
        TrustManager trustManager = super.createClientTrustManager();
        return new BogusX509TrustManager(
                (X509TrustManager)trustManager);
    }

    @Override
    protected TrustManager createServerTrustManager() throws Exception {
        TrustManager trustManager = super.createServerTrustManager();
        return new BogusX509TrustManager(
                (X509TrustManager)trustManager);
    }

    /*
     * Run the test case.
     */
    public static void main(String[] args) throws Exception {
        for (int i = 0; i < protocols.length; i++) {
            (new TooManyCAs(i, false)).run();
            (new TooManyCAs(i, true)).run();
        }
    }

    @Override
    protected void runServerApplication(SSLSocket socket) throws Exception {
        try {
            System.out.println("Sending data to client ...");
            String serverData = "Hi, I am server";
            BufferedWriter os = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream()));
            os.write(serverData, 0, serverData.length());
            os.newLine();
            os.flush();
        } finally {
            barrier.await();
            System.out.println("Server done");
        }
    }

    @Override
    protected void runClientApplication(SSLSocket socket) throws Exception {
        try {
            String clientData = "Hi, I am client";
            System.out.println("Sending data to server ...");

            BufferedWriter os = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream()));
            os.write(clientData, 0, clientData.length());
            os.newLine();
            os.flush();

            System.out.println("Reading data from server");
            BufferedReader is = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            String data = is.readLine();
            System.out.println("Received Data from server: " + data);
        } finally {
            barrier.await();
            System.out.println("client done.");
        }
    }

    // Construct a bogus trust manager which has more CAs such that exceed
    // the size limit of the certificate_authorities extension (2^16).
    private static final class BogusX509TrustManager
            extends X509ExtendedTrustManager implements X509TrustManager {
        private final X509ExtendedTrustManager tm;

        private BogusX509TrustManager(X509TrustManager trustManager) {
            this.tm = (X509ExtendedTrustManager)trustManager;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain,
               String authType, Socket socket) throws CertificateException {
            tm.checkClientTrusted(chain, authType, socket);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain,
               String authType, Socket socket) throws CertificateException {
            tm.checkServerTrusted(chain, authType, socket);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain,
            String authType, SSLEngine sslEngine) throws CertificateException {

            tm.checkClientTrusted(chain, authType, sslEngine);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain,
            String authType, SSLEngine sslEngine) throws CertificateException {

            tm.checkServerTrusted(chain, authType, sslEngine);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain,
               String authType) throws CertificateException {
            tm.checkServerTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain,
               String authType) throws CertificateException {
            tm.checkServerTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            X509Certificate[] trustedCerts = tm.getAcceptedIssuers();
            int sizeAccount = 0;
            for (X509Certificate cert: trustedCerts) {
                X500Principal x500Principal = cert.getSubjectX500Principal();
                byte[] encodedPrincipal = x500Principal.getEncoded();
                sizeAccount += encodedPrincipal.length;
            }

            // 0xFFFF: the size limit of the certificate_authorities extension
            int duplicated = (0xFFFF + sizeAccount) / sizeAccount;
            X509Certificate[] returnedCAs =
                    new X509Certificate[trustedCerts.length * duplicated];
            for (int i = 0; i < duplicated; i++) {
                System.arraycopy(trustedCerts, 0,
                    returnedCAs,
                    i * trustedCerts.length, trustedCerts.length);
            }

            return returnedCAs;
        }
    }
}
