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
 * @bug 4323990 4413069 8160838
 * @summary HttpsURLConnection doesn't send Proxy-Authorization on CONNECT
 *     Incorrect checking of proxy server response
 * @modules java.base/sun.net.www
 * @library /javax/net/ssl/templates
 * @run main/othervm ProxyAuthTest fail
 * @run main/othervm -Djdk.http.auth.tunneling.disabledSchemes=Basic ProxyAuthTest fail
 * @run main/othervm -Djdk.http.auth.tunneling.disabledSchemes=Basic, ProxyAuthTest fail
 * @run main/othervm -Djdk.http.auth.tunneling.disabledSchemes=BAsIc ProxyAuthTest fail
 * @run main/othervm -Djdk.http.auth.tunneling.disabledSchemes=Basic,Digest ProxyAuthTest fail
 * @run main/othervm -Djdk.http.auth.tunneling.disabledSchemes=Unknown,bAsIc ProxyAuthTest fail
 * @run main/othervm -Djdk.http.auth.tunneling.disabledSchemes= ProxyAuthTest succeed
 * @run main/othervm -Djdk.http.auth.tunneling.disabledSchemes=Digest,NTLM,Negotiate ProxyAuthTest succeed
 * @run main/othervm -Djdk.http.auth.tunneling.disabledSchemes=UNKNOWN,notKnown ProxyAuthTest succeed
 */

// No way to reserve and restore java.lang.Authenticator, as well as read-once
// system properties, so this tests needs to run in othervm mode.

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URL;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import static java.nio.charset.StandardCharsets.US_ASCII;

/*
 * ProxyAuthTest.java -- includes a simple server that can serve
 * Http get request in both clear and secure channel, and a client
 * that makes https requests behind the firewall through an
 * authentication proxy
 */

public class ProxyAuthTest {
    /*
     * Where do we find the keystores?
     */
    static String pathToStores = "../../../../../../javax/net/ssl/etc";
    static String keyStoreFile = "keystore";
    static String trustStoreFile = "truststore";
    static String passwd = "passphrase";

    /**
     * read the response, don't care for the syntax of the request-line
     * for this testing
     */
    private static void readRequest(BufferedReader in) throws IOException {
        String line = null;
        System.out.println("Server received: ");
        do {
            if (line != null) {
                System.out.println(line);
            }
            line = in.readLine();
        } while ((line.length() != 0) &&
                (line.charAt(0) != '\r') && (line.charAt(0) != '\n'));
    }

    /*
     * Main method to create the server and the client
     */
    public static void main(String args[]) throws Exception {
        boolean expectSuccess;
        expectSuccess = args[0].equals("succeed");

        String keyFilename =
            SSLTest.TEST_SRC + "/" + pathToStores + "/" + keyStoreFile;
        String trustFilename =
            SSLTest.TEST_SRC + "/" + pathToStores + "/" + trustStoreFile;

        SSLTest.setup(keyFilename, trustFilename, passwd);

        new SSLTest()
            .setServerApplication((socket, test) -> {
                DataOutputStream out = new DataOutputStream(
                        socket.getOutputStream());

                try {
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream()));

                    // read the request
                    readRequest(in);

                    // retrieve bytecodes
                    byte[] bytecodes =
                            "Proxy authentication for tunneling succeeded .."
                                    .getBytes(US_ASCII);

                    // send bytecodes in response (assumes HTTP/1.0 or later)
                    out.writeBytes("HTTP/1.0 200 OK\r\n");
                    out.writeBytes("Content-Length: " + bytecodes.length +
                                   "\r\n");
                    out.writeBytes("Content-Type: text/html\r\n\r\n");
                    out.write(bytecodes);
                    out.flush();
                } catch (IOException e) {
                    // write out error response
                    out.writeBytes("HTTP/1.0 400 " + e.getMessage() + "\r\n");
                    out.writeBytes("Content-Type: text/html\r\n\r\n");
                    out.flush();
                }
            })
            .setClientPeer(test -> {
                try {
                    doClientSide(test);
                    if (!expectSuccess) {
                        throw new RuntimeException("Expected exception/failure "
                                + "to connect, but succeeded.");
                    }
                } catch (IOException e) {
                    if (expectSuccess) {
                        System.out.println("Client side failed: "
                                + e.getMessage());
                        throw e;
                    }

                    if (! (e.getMessage().contains(
                                "Unable to tunnel through proxy") &&
                           e.getMessage().contains("407")) ) {

                        throw new RuntimeException(
                                "Expected exception about cannot tunnel, "
                                        + "407, etc, but got", e);
                    } else {
                        // Informative
                        System.out.println("Caught expected exception: "
                                + e.getMessage());
                    }
                }
            })
            .runTest();
    }

    static void doClientSide(SSLTest test) throws IOException {

        // Wait for server to get started.
        //
        // The server side takes care of the issue if the server cannot
        // get started in 90 seconds.  The client side would just ignore
        // the test case if the serer is not ready.
        try {
            if (!test.waitForServerSignal()) {
                System.out.print("The server is not ready yet in 90 seconds. "
                        + "Ignore in client side.");
                return;
            }
        } catch (InterruptedException e) {
            System.out.print("InterruptedException occured. "
                    + "Ignore in client side.");
            return;
        }

        /*
         * setup up a proxy with authentication information
         */
        ProxyTunnelServer ps = setupProxy();

        /*
         * we want to avoid URLspoofCheck failures in cases where the cert
         * DN name does not match the hostname in the URL.
         */
        HttpsURLConnection.setDefaultHostnameVerifier(new NameVerifier());

        InetSocketAddress paddr = new InetSocketAddress(
                "localhost", ps.getPort());
        Proxy proxy = new Proxy(Proxy.Type.HTTP, paddr);

        URL url = new URL("https://" + "localhost:" + test.getServerPort()
                + "/index.html");

        // Signal the server, the client is ready to communicate.
        test.signalClientReady();

        HttpsURLConnection uc = (HttpsURLConnection) url.openConnection(proxy);
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(uc.getInputStream()))) {

            String inputLine;
            System.out.print("Client recieved from the server: ");
            while ((inputLine = in.readLine()) != null) {
                System.out.println(inputLine);
            }
        } catch (IOException e) {
            // Assert that the error stream is not accessible from the failed
            // tunnel setup.
            if (uc.getErrorStream() != null) {
                throw new RuntimeException("Unexpected error stream.");
            }

            throw e;
        }
    }

    static class NameVerifier implements HostnameVerifier {

        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }

    static ProxyTunnelServer setupProxy() throws IOException {
        ProxyTunnelServer pserver = new ProxyTunnelServer();
        /*
         * register a system wide authenticator and setup the proxy for
         * authentication
         */
        Authenticator.setDefault(new TestAuthenticator());

        // register with the username and password
        pserver.needUserAuth(true);
        pserver.setUserAuth("Test", "test123");

        pserver.start();
        return pserver;
    }

    public static class TestAuthenticator extends Authenticator {

        @Override
        public PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication("Test", "test123".toCharArray());
        }
    }
}
