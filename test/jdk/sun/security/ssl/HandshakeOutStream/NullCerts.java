/*
 * Copyright (c) 2001, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4453053
 * @summary If a server shuts down correctly during handshaking, the client
 *     doesn't see it.
 * @library /javax/net/ssl/templates
 * @run main/othervm NullCerts
 *
 *     SunJSSE does not support dynamic system properties, no way to re-use
 *     system properties in samevm/agentvm mode.
 * @author Brad Wetmore
 */

import java.io.*;
import javax.net.ssl.*;

public class NullCerts extends SSLSocketTemplate {

     /*
     * Turn on SSL debugging?
     */
    private final static boolean DEBUG = false;

    /*
     * If the client or server is doing some kind of object creation
     * that the other side depends on, and that thread prematurely
     * exits, you may experience a hang.  The test harness will
     * terminate all hung threads after its timeout has expired,
     * currently 3 minutes by default, but you might try to be
     * smart about it....
     */

    @Override
    protected void configureServerSocket(SSLServerSocket socket) {
        socket.setNeedClientAuth(true);
        socket.setUseClientMode(false);
    }

    /*
     * Define the server side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    @Override
    protected void runServerApplication(SSLSocket socket) throws Exception {
        InputStream sslIS = socket.getInputStream();
        OutputStream sslOS = socket.getOutputStream();

        try {
            sslIS.read();
            sslOS.write(85);
            sslOS.flush();
        } catch (SSLHandshakeException e) {
            System.out.println(
                "Should see a null cert chain exception for server: "
                + e.toString());
        }

        System.out.println("Server done and exiting!");
    }

    /*
     * Define the client side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    @Override
    protected void runClientApplication(SSLSocket sslSocket) throws Exception {

        System.out.println("Starting test");

        InputStream sslIS = sslSocket.getInputStream();
        OutputStream sslOS = sslSocket.getOutputStream();

        try {
            sslOS.write(280);
            sslOS.flush();
            sslIS.read();

        } catch (IOException e) {
            String str =
                "\nYou will either see a bad_certificate SSLException\n" +
                "or an IOException if the server shutdown while the\n" +
                "client was still sending the remainder of its \n" +
                "handshake data.";
            System.out.println(str + e.toString());
        }
    }

    @Override
    protected KeyManager createClientKeyManager() throws Exception {
        return createKeyManager(new Cert[]{Cert.DSA_SHA1_1024_EXPIRED},
                getClientContextParameters());
    }

   // Used for running test standalone
    public static void main(String[] args) throws Exception {

        if (DEBUG)
            System.setProperty("javax.net.debug", "all");

        /*
         * Start the tests.
         */
        new NullCerts().run();
    }
}
