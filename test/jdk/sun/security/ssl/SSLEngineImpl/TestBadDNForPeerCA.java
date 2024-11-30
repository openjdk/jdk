/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8294985
 * @library /test/lib
 * @summary SSLEngine throws IAE during parsing of X500Principal
 * @run main/othervm TestBadDNForPeerCA
 * @run main/othervm -Djavax.net.debug=all TestBadDNForPeerCA
 */

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.util.Base64;


public class TestBadDNForPeerCA {

    private static final String proto = "TLSv1.3";

    private final SSLContext sslc;

    private SSLEngine serverEngine;     // server Engine
    private ByteBuffer serverIn;        // read side of serverEngine

    private ByteBuffer cTOs;            // "reliable" transport client->server

    private static final String keyStoreFile =
        System.getProperty("test.src", "./")
        + "/../../../../javax/net/ssl/etc/keystore";

    // the following ClientHello contains a certificate with an
    // invalid/unparseable distinguished name
    private static final byte[] payload = Base64.getDecoder().decode(
        "FgMDAcsBAAHHAwPbDfeUCIStPzVIfXuGgCu56dSJOJ6xeus1W44frG5tciDEcBfYt"
            + "/PN/6MFCGojEVcmPw21mVyjYInMo0UozIn4NwBiEwITARMDwCzAK8ypwDDMqMAvA"
            + "J/MqgCjAJ4AosAkwCjAI8AnAGsAagBnAEDALsAywC3AMcAmCgAFKsApJcDAFMAJw"
            + "BMAOQA4ADMAMsAFwA/ABMAOAJ0AnAA9ADwANgAvAP8BAAEcAAUABQEAAAAAAAoAF"
            + "gAUAB0AFwAYABkAHgEAAQEBAgEDAQQACwACAQAAEQAJAAcCAAQAAAAAABcAAAAjA"
            + "AAADQAsACoEAwUDBgMIBwgICAQIBQgGCAkICggLBAEFAQYBBAIDAwMBAwICAwIBA"
            + "gIAKwAFBAMEAwMALQACAQEAMgAsACoEAwUDBgMIBwgICAQIBQgGCAkICggLBAEFA"
            + "QYBBAIDAwMBAwICAwIBAgIALwBrAGkAHQAAAAARACAAZMUAADkwsiaOwcsWAwAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAtAAAAAAAAAAEAADAAAAA=");

    /*
     * The following is to set up the keystores.
     */
    private static final String passwd = "passphrase";

    /*
     * Main entry point for this demo.
     */
    public static void main(String[] args) throws Exception {

        TestBadDNForPeerCA test = new TestBadDNForPeerCA();

        try {
            test.runTest();
            throw new Exception(
                "TEST FAILED:  Didn't generate any exception");
        } catch (SSLHandshakeException she) {
            System.out.println("TEST PASSED:  Caught expected exception");
        }
    }

    /*
     * Create an initialized SSLContext to use for this demo.
     */

    public TestBadDNForPeerCA() throws Exception {

        char[] passphrase = passwd.toCharArray();

        KeyStore ks = KeyStore.getInstance(new File(keyStoreFile), passphrase);
        KeyStore ts = KeyStore.getInstance(new File(keyStoreFile), passphrase);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ts);

        SSLContext sslCtx = SSLContext.getInstance(proto);

        sslCtx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        sslc = sslCtx;
    }


    private void runTest() throws Exception {

        createSSLEngines();
        createBuffers();

        cTOs = ByteBuffer.wrap(payload);

        System.out.println("injecting client hello");

        for (int i = 0; i < 10; i++) { //retry if survived
            SSLEngineResult serverResult = serverEngine.unwrap(cTOs, serverIn);
            System.out.println("server unwrap: " + serverResult);
            runDelegatedTasks(serverResult, serverEngine);
        }
    }

    private void createSSLEngines() throws Exception {

        serverEngine = sslc.createSSLEngine();
        serverEngine.setUseClientMode(false);
        serverEngine.setNeedClientAuth(true);

    }


    private void createBuffers() {

        serverIn = ByteBuffer.allocateDirect(65536);

        cTOs = ByteBuffer.allocateDirect(65536);

    }

    private static void runDelegatedTasks(SSLEngineResult result,
                                          SSLEngine engine) throws Exception {

        if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
            Runnable runnable;
            while ((runnable = engine.getDelegatedTask()) != null) {
                System.out.println("\trunning delegated task...");
                runnable.run();
            }

            HandshakeStatus hsStatus = engine.getHandshakeStatus();
            if (hsStatus == HandshakeStatus.NEED_TASK) {
                throw new Exception("handshake shouldn't need additional " +
                    "tasks");
            }
            System.out.println("\tnew HandshakeStatus: " + hsStatus);
        }
    }


}
