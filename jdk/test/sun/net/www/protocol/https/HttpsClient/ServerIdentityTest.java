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

/*
 * @test
 * @bug 4328195
 * @summary Need to include the alternate subject DN for certs,
 *          https should check for this
 * @library /javax/net/ssl/templates
 * @run main/othervm ServerIdentityTest dnsstore
 * @run main/othervm ServerIdentityTest ipstore
 *
 *     SunJSSE does not support dynamic system properties, no way to re-use
 *     system properties in samevm/agentvm mode.
 *
 * @author Yingxian Wang
 */

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;

public class ServerIdentityTest extends SSLSocketTemplate {

    private static final String PASSWORD = "changeit";

    public static void main(String[] args) throws Exception {
        final String keystore = args[0];
        String keystoreFilename = TEST_SRC + "/" + keystore;

        setup(keystoreFilename, keystoreFilename, PASSWORD);

        SSLContext context = SSLContext.getInstance("SSL");

        KeyManager[] kms = new KeyManager[1];
        KeyStore ks = loadJksKeyStore(keystoreFilename, PASSWORD);
        KeyManager km = new MyKeyManager(ks, PASSWORD.toCharArray());
        kms[0] = km;
        context.init(kms, null, null);
        HttpsURLConnection.setDefaultSSLSocketFactory(
                context.getSocketFactory());

        /*
         * Start the test.
         */
        System.out.println("Testing " + keystore);

        new SSLSocketTemplate()
            .setSSLContext(context)
            .setServerApplication((socket, test) -> {
                BufferedWriter bw = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream()));
                bw.write("HTTP/1.1 200 OK\r\n\r\n\r\n");
                bw.flush();
                Thread.sleep(2000);
                socket.getSession().invalidate();
                print("Server application is done");
            })
            .setClientPeer((test) -> {
                boolean serverIsReady = test.waitForServerSignal();
                if (!serverIsReady) {
                    print(
                            "The server is not ready, ignore on client side.");
                    return;
                }

                // Signal the server, the client is ready to communicate.
                test.signalClientReady();

                String host = keystore.equals("ipstore")
                        ? "127.0.0.1" : "localhost";
                URL url = new URL("https://" + host + ":" + test.getServerPort()
                        + "/index.html");

                ((HttpURLConnection) url.openConnection())
                        .getInputStream().close();

                print("Client is done");
            }).runTest();
    }
}
