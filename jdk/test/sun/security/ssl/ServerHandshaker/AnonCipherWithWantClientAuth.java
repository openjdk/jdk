/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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

//
// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.
//

/*
 * @test
 * @bug 4392475
 * @library /javax/net/ssl/templates
 * @summary Calling setWantClientAuth(true) disables anonymous suites
 * @run main/othervm/timeout=180 AnonCipherWithWantClientAuth
 */

import java.io.InputStream;
import java.io.OutputStream;
import java.security.Security;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

public class AnonCipherWithWantClientAuth extends SSLSocketTemplate {

    /*
     * Where do we find the keystores?
     */
    static String pathToStores = "../../../../javax/net/ssl/etc";
    static String keyStoreFile = "keystore";
    static String trustStoreFile = "truststore";
    static String passwd = "passphrase";

    public static void main(String[] args) throws Exception {
        Security.setProperty("jdk.tls.disabledAlgorithms", "");
        Security.setProperty("jdk.certpath.disabledAlgorithms", "");

        String keyFilename =
            System.getProperty("test.src", "./") + "/" + pathToStores +
                "/" + keyStoreFile;
        String trustFilename =
            System.getProperty("test.src", "./") + "/" + pathToStores +
                "/" + trustStoreFile;
        setup(keyFilename, trustFilename, passwd);

        new SSLSocketTemplate()
            .setServerPeer(test -> {
                SSLServerSocketFactory sslssf =
                        (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
                SSLServerSocket sslServerSocket =
                        (SSLServerSocket) sslssf.createServerSocket(FREE_PORT);
                test.setServerPort(sslServerSocket.getLocalPort());
                print("Server is listening on port "
                        + test.getServerPort());

                String ciphers[] = {
                        "SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA",
                        "SSL_DH_anon_EXPORT_WITH_RC4_40_MD5",
                        "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA" };
                sslServerSocket.setEnabledCipherSuites(ciphers);
                sslServerSocket.setWantClientAuth(true);

                // Signal the client, the server is ready to accept connection.
                test.signalServerReady();

                // Try to accept a connection in 30 seconds.
                SSLSocket sslSocket = accept(sslServerSocket);
                if (sslSocket == null) {
                    // Ignore the test case if no connection within 30 seconds.
                    print("No incoming client connection in 30 seconds."
                            + " Ignore in server side.");
                    return;
                }
                print("Server accepted connection");

                // handle the connection
                try {
                    // Is it the expected client connection?
                    //
                    // Naughty test cases or third party routines may try to
                    // connection to this server port unintentionally.  In
                    // order to mitigate the impact of unexpected client
                    // connections and avoid intermittent failure, it should
                    // be checked that the accepted connection is really linked
                    // to the expected client.
                    boolean clientIsReady = test.waitForClientSignal();

                    if (clientIsReady) {
                        // Run the application in server side.
                        print("Run server application");

                        InputStream sslIS = sslSocket.getInputStream();
                        OutputStream sslOS = sslSocket.getOutputStream();

                        sslIS.read();
                        sslOS.write(85);
                        sslOS.flush();
                    } else {
                        System.out.println(
                                "The client is not the expected one or timeout. "
                                        + "Ignore in server side.");
                    }
                } finally {
                    sslSocket.close();
                    sslServerSocket.close();
                }
            })
            .setClientApplication((socket, test) -> {
                String ciphers[] = {
                        "SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA",
                        "SSL_DH_anon_EXPORT_WITH_RC4_40_MD5" };
                socket.setEnabledCipherSuites(ciphers);
                socket.setUseClientMode(true);

                InputStream sslIS = socket.getInputStream();
                OutputStream sslOS = socket.getOutputStream();

                sslOS.write(280);
                sslOS.flush();
                sslIS.read();
            })
            .runTest();
    }
}
