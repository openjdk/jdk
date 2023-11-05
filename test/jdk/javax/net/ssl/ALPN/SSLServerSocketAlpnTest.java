/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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

// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.

/*
 * @test
 * @bug 8051498 8145849 8158978 8170282
 * @summary JEP 244: TLS Application-Layer Protocol Negotiation Extension
 * @library /javax/net/ssl/templates
 *
 * @run main/othervm SSLServerSocketAlpnTest h2          UNUSED   h2          h2
 * @run main/othervm SSLServerSocketAlpnTest h2          UNUSED   h2,http/1.1 h2
 * @run main/othervm SSLServerSocketAlpnTest h2,http/1.1 UNUSED   h2,http/1.1 h2
 * @run main/othervm SSLServerSocketAlpnTest http/1.1,h2 UNUSED   h2,http/1.1 http/1.1
 * @run main/othervm SSLServerSocketAlpnTest h4,h3,h2    UNUSED   h1,h2       h2
 * @run main/othervm SSLServerSocketAlpnTest EMPTY       UNUSED   h2,http/1.1 NONE
 * @run main/othervm SSLServerSocketAlpnTest h2          UNUSED   EMPTY       NONE
 * @run main/othervm SSLServerSocketAlpnTest H2          UNUSED   h2          ERROR
 * @run main/othervm SSLServerSocketAlpnTest h2          UNUSED   http/1.1    ERROR
 *
 * @run main/othervm SSLServerSocketAlpnTest UNUSED      h2       h2          h2
 * @run main/othervm SSLServerSocketAlpnTest UNUSED      h2       h2,http/1.1 h2
 * @run main/othervm SSLServerSocketAlpnTest UNUSED      h2       http/1.1,h2 h2
 * @run main/othervm SSLServerSocketAlpnTest UNUSED      http/1.1 h2,http/1.1 http/1.1
 * @run main/othervm SSLServerSocketAlpnTest UNUSED      EMPTY    h2,http/1.1 NONE
 * @run main/othervm SSLServerSocketAlpnTest UNUSED      h2       EMPTY       NONE
 * @run main/othervm SSLServerSocketAlpnTest UNUSED      H2       h2          ERROR
 * @run main/othervm SSLServerSocketAlpnTest UNUSED      h2       http/1.1    ERROR
 *
 * @run main/othervm SSLServerSocketAlpnTest h2          h2       h2          h2
 * @run main/othervm SSLServerSocketAlpnTest H2          h2       h2,http/1.1 h2
 * @run main/othervm SSLServerSocketAlpnTest h2,http/1.1 http/1.1 h2,http/1.1 http/1.1
 * @run main/othervm SSLServerSocketAlpnTest http/1.1,h2 h2       h2,http/1.1 h2
 * @run main/othervm SSLServerSocketAlpnTest EMPTY       h2       h2          h2
 * @run main/othervm SSLServerSocketAlpnTest h2,http/1.1 EMPTY    http/1.1    NONE
 * @run main/othervm SSLServerSocketAlpnTest h2,http/1.1 h2       EMPTY       NONE
 * @run main/othervm SSLServerSocketAlpnTest UNUSED      UNUSED   http/1.1,h2 NONE
 * @run main/othervm SSLServerSocketAlpnTest h2          h2       http/1.1    ERROR
 * @run main/othervm SSLServerSocketAlpnTest h2,http/1.1 H2       http/1.1    ERROR
 *
 * @author Brad Wetmore
 */
/**
 * A simple SSLSocket-based client/server that demonstrates the proposed API
 * changes for JEP 244 in support of the TLS ALPN extension (RFC 7301).
 *
 * Usage:
 *     java SSLServerSocketAlpnTest
 *             <server-APs> <callback-AP> <client-APs> <result>
 *
 * where:
 *      EMPTY  indicates that ALPN is disabled
 *      UNUSED indicates that no ALPN values are supplied (server-side only)
 *      ERROR  indicates that an exception is expected
 *      NONE   indicates that no ALPN is expected
 *
 * This example is based on our standard SSLSocketTemplate.
 */
import java.io.*;
import java.util.Arrays;

import javax.net.ssl.*;

public class SSLServerSocketAlpnTest extends SSLSocketTemplate {

    private static boolean hasCallback; // whether a callback is present

    /*
     * Turn on SSL debugging?
     */
    static boolean debug = Boolean.getBoolean("test.debug");

    static String[] serverAPs;
    static String callbackAP;
    static String[] clientAPs;
    static String expectedAP;

    /*
     * If the client or server is doing some kind of object creation
     * that the other side depends on, and that thread prematurely
     * exits, you may experience a hang. The test harness will
     * terminate all hung threads after its timeout has expired,
     * currently 3 minutes by default, but you might try to be
     * smart about it....
     */

    @Override
    protected void configureServerSocket(SSLServerSocket sslServerSocket) {
        sslServerSocket.setNeedClientAuth(true);

        SSLParameters sslp = sslServerSocket.getSSLParameters();

        // for both client/server to call into X509KM
        sslp.setNeedClientAuth(true);

        /*
         * The default ciphersuite ordering from the SSLContext may not
         * reflect "h2" ciphersuites as being preferred, additionally the
         * client may not send them in an appropriate order. We could resort
         * the suite list if so desired.
         */
        String[] suites = sslp.getCipherSuites();
        sslp.setCipherSuites(suites);
        sslp.setUseCipherSuitesOrder(true); // Set server side order

        // Set the ALPN selection.
        if (serverAPs != null) {
            sslp.setApplicationProtocols(serverAPs);
        }
        sslServerSocket.setSSLParameters(sslp);

        serverPort = sslServerSocket.getLocalPort();
    }

    /*
     * Define the server side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    @Override
    protected void runServerApplication(SSLSocket sslSocket) throws Exception {

        if (sslSocket.getHandshakeApplicationProtocol() != null) {
            throw new Exception ("getHandshakeApplicationProtocol() should "
                    + "return null before the handshake starts");
        }

        // check that no callback has been registered
        if (sslSocket.getHandshakeApplicationProtocolSelector() != null) {
            throw new Exception("getHandshakeApplicationProtocolSelector() " +
                "should return null");
        }

        if (hasCallback) {
            sslSocket.setHandshakeApplicationProtocolSelector(
                (serverSocket, clientProtocols) -> {
                    return callbackAP.equals("EMPTY") ? "" : callbackAP;
                });

            // check that the callback can be retrieved
            if (sslSocket.getHandshakeApplicationProtocolSelector() == null) {
                throw new Exception("getHandshakeApplicationProtocolSelector()"
                    + " should return non-null");
            }
        }

        sslSocket.startHandshake();

        if (sslSocket.getHandshakeApplicationProtocol() != null) {
            throw new Exception ("getHandshakeApplicationProtocol() should "
                    + "return null after the handshake is completed");
        }

        String ap = sslSocket.getApplicationProtocol();
        System.out.println("Application Protocol: \"" + ap + "\"");

        if (ap == null) {
            throw new Exception(
                    "Handshake was completed but null was received");
        }
        if (expectedAP.equals("NONE")) {
            if (!ap.isEmpty()) {
                throw new Exception("Expected no ALPN value");
            } else {
                System.out.println("No ALPN value negotiated, as expected");
            }
        } else if (!expectedAP.equals(ap)) {
            throw new Exception(expectedAP
                    + " ALPN value not available on negotiated connection");
        }

        InputStream sslIS = sslSocket.getInputStream();
        OutputStream sslOS = sslSocket.getOutputStream();

        sslIS.read();
        sslOS.write(85);
        sslOS.flush();

        sslSocket.close();
    }

    @Override
    protected void configureClientSocket(SSLSocket socket) {
        SSLParameters sslp = socket.getSSLParameters();

        /*
         * The default ciphersuite ordering from the SSLContext may not
         * reflect "h2" ciphersuites as being preferred, additionally the
         * client may not send them in an appropriate order. We could resort
         * the suite list if so desired.
         */
        String[] suites = sslp.getCipherSuites();
        sslp.setCipherSuites(suites);
        sslp.setUseCipherSuitesOrder(true); // Set server side order

        // Set the ALPN selection.
        sslp.setApplicationProtocols(clientAPs);
        socket.setSSLParameters(sslp);
    }

    /*
     * Define the client side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    @Override
    protected void runClientApplication(SSLSocket sslSocket) throws Exception {

        if (sslSocket.getHandshakeApplicationProtocol() != null) {
            throw new Exception ("getHandshakeApplicationProtocol() should "
                    + "return null before the handshake starts");
        }

        sslSocket.startHandshake();

        if (sslSocket.getHandshakeApplicationProtocol() != null) {
            throw new Exception ("getHandshakeApplicationProtocol() should "
                    + "return null after the handshake is completed");
        }

        /*
         * Check that the resulting connection meets our defined ALPN
         * criteria.  If we were connecting to a non-JSSE implementation,
         * the server might have negotiated something we shouldn't accept.
         */
        String ap = sslSocket.getApplicationProtocol();
        System.out.println("Application Protocol: \"" + ap + "\"");

        if (ap == null) {
            throw new Exception(
                    "Handshake was completed but null was received");
        }
        if (expectedAP.equals("NONE")) {
            if (!ap.isEmpty()) {
                throw new Exception("Expected no ALPN value");
            } else {
                System.out.println("No ALPN value negotiated, as expected");
            }
        } else if (!expectedAP.equals(ap)) {
            throw new Exception(expectedAP
                    + " ALPN value not available on negotiated connection");
        }

        InputStream sslIS = sslSocket.getInputStream();
        OutputStream sslOS = sslSocket.getOutputStream();

        sslOS.write(280);
        sslOS.flush();
        sslIS.read();

        sslSocket.close();
    }

    /*
     * =============================================================
     * The remainder is just support stuff
     */
    // use any free port by default
    volatile int serverPort = 0;

    public static void main(String[] args) throws Exception {

        if (debug) {
            System.setProperty("javax.net.debug", "all");
        }
        System.out.println("Test args: " + Arrays.toString(args));

        // Validate parameters
        if (args.length != 4) {
            throw new Exception("Invalid number of test parameters");
        }
        serverAPs = convert(args[0]);
        callbackAP = args[1];
        clientAPs = convert(args[2]);
        expectedAP = args[3];

        hasCallback = !callbackAP.equals("UNUSED"); // is callback being used?

        /*
         * Start the tests.
         */
        try {
            new SSLServerSocketAlpnTest().run();
        } catch (SSLHandshakeException she) {
            if (args[3].equals("ERROR")) {
                System.out.println("Caught the expected exception: " + she);
            } else {
                throw she;
            }
        }

        System.out.println("Test Passed.");
    }

    /*
     * Convert a comma-separated list into an array of strings.
     */
    private static String[] convert(String list) {
        if (list.equals("UNUSED")) {
            return null;
        }

        if (list.equals("EMPTY")) {
            return new String[0];
        }

        String[] strings;
        if (list.indexOf(',') > 0) {
            strings = list.split(",");
        } else {
            strings = new String[]{ list };
        }

        return strings;
    }
}
