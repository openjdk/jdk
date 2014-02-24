/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4931274
 * @summary closeInbound does not signal when a close_notify has not
 *              been received.
 * @run main/othervm CloseInboundException
 * @author Brad Wetmore
 */

import javax.net.ssl.*;
import javax.net.ssl.SSLEngineResult.*;
import java.io.*;
import java.security.*;
import java.nio.*;

public class CloseInboundException {

    private SSLEngine ssle1;    // client
    private SSLEngine ssle2;    // server

    SSLEngineResult result1;    // ssle1's results from last operation
    SSLEngineResult result2;    // ssle2's results from last operation

    private static String pathToStores = "../../../../../../../etc";
    private static String keyStoreFile = "keystore";
    private static String trustStoreFile = "truststore";
    private static String passwd = "passphrase";

    private static String keyFilename =
            System.getProperty("test.src", "./") + "/" + pathToStores +
                "/" + keyStoreFile;
    private static String trustFilename =
            System.getProperty("test.src", "./") + "/" + pathToStores +
                "/" + trustStoreFile;

    private ByteBuffer appOut1;         // write side of ssle1
    private ByteBuffer appIn1;          // read side of ssle1
    private ByteBuffer appOut2;         // write side of ssle2
    private ByteBuffer appIn2;          // read side of ssle2

    private ByteBuffer oneToTwo;        // "reliable" transport ssle1->ssle2
    private ByteBuffer twoToOne;        // "reliable" transport ssle2->ssle1

    /*
     * Majority of the test case is here, setup is done below.
     */
    private void runTest(boolean inboundClose) throws Exception {

        boolean done = false;

        while (!isEngineClosed(ssle1) || !isEngineClosed(ssle2)) {

            System.out.println("================");

            result1 = ssle1.wrap(appOut1, oneToTwo);
            result2 = ssle2.wrap(appOut2, twoToOne);

            System.out.println("wrap1 = " + result1);
            System.out.println("oneToTwo  = " + oneToTwo);

            System.out.println("wrap2 = " + result2);
            System.out.println("twoToOne  = " + twoToOne);

            runDelegatedTasks(result1, ssle1);
            runDelegatedTasks(result2, ssle2);

            oneToTwo.flip();
            twoToOne.flip();

            System.out.println("----");
            result1 = ssle1.unwrap(twoToOne, appIn1);

            if (done && inboundClose) {
                try {
                    result2 = ssle2.unwrap(oneToTwo, appIn2);
                    throw new Exception("Didn't throw Exception");
                } catch (SSLException e) {
                    System.out.println("Caught proper exception\n" + e);
                    return;
                }
            } else {
                result2 = ssle2.unwrap(oneToTwo, appIn2);
            }

            System.out.println("unwrap1 = " + result1);
            System.out.println("twoToOne  = " + twoToOne);

            System.out.println("unwrap2 = " + result2);
            System.out.println("oneToTwo  = " + oneToTwo);

            runDelegatedTasks(result1, ssle1);
            runDelegatedTasks(result2, ssle2);

            oneToTwo.compact();
            twoToOne.compact();

            /*
             * If we've transfered all the data between app1 and app2,
             * we try to close and see what that gets us.
             */
            if (!done && (appOut1.limit() == appIn2.position()) &&
                (appOut2.limit() == appIn1.position())) {

                if (inboundClose) {
                    try {
                        System.out.println("Closing ssle1's *INBOUND*...");
                        ssle1.closeInbound();
                        throw new Exception("closeInbound didn't throw");
                    } catch (SSLException e) {
                        System.out.println("Caught closeInbound exc properly");
                        checkStatus();
                        /*
                         * Let the message processing continue to
                         * handle the alert.
                         */
                    }
                    done = true;
                } else {
                    done = true;
                    System.out.println("Closing ssle1's *OUTBOUND*...");
                    ssle1.closeOutbound();
                }
            }
        }
    }

    /*
     * Check to see if the close generated a close_notify message,
     * that the result status is sane, and that close again doesn't
     * generate a new exception.
     *
     * We'll consume the wrapped data when we loop back around.
     */
    private void checkStatus() throws Exception {
        System.out.println("\nCalling last wrap");
        int pos = oneToTwo.position();

        result1 = ssle1.wrap(appOut1, oneToTwo);
        System.out.println("result1 = " + result1);

        if ((pos >= oneToTwo.position()) ||
                !result1.getStatus().equals(Status.CLOSED) ||
                !result1.getHandshakeStatus().equals(
                    HandshakeStatus.NOT_HANDSHAKING) ||
                !ssle1.isOutboundDone() ||
                !ssle1.isInboundDone()) {
            throw new Exception(result1.toString());
        }
        System.out.println("Make sure we don't throw a second SSLException.");
        ssle1.closeInbound();
    }

    public static void main(String args[]) throws Exception {

        CloseInboundException test;

        test = new CloseInboundException();
        test.runTest(false);

        test = new CloseInboundException();
        test.runTest(true);
        System.out.println("Test PASSED!!!");
    }

    /*
     * **********************************************************
     * Majority of the test case is above, below is just setup stuff
     * **********************************************************
     */

    public CloseInboundException() throws Exception {

        SSLContext sslc = getSSLContext(keyFilename, trustFilename);

        ssle1 = sslc.createSSLEngine("host1", 1);
        ssle1.setUseClientMode(true);

        ssle2 = sslc.createSSLEngine("host2", 2);
        ssle2.setUseClientMode(false);

        createBuffers();
    }

    /*
     * Create an initialized SSLContext to use for this test.
     */
    private SSLContext getSSLContext(String keyFile, String trustFile)
            throws Exception {

        KeyStore ks = KeyStore.getInstance("JKS");
        KeyStore ts = KeyStore.getInstance("JKS");

        char[] passphrase = "passphrase".toCharArray();

        ks.load(new FileInputStream(keyFile), passphrase);
        ts.load(new FileInputStream(trustFile), passphrase);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ts);

        SSLContext sslCtx = SSLContext.getInstance("TLS");

        sslCtx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return sslCtx;
    }

    private void createBuffers() {
        // Size the buffers as appropriate.
        SSLSession session = ssle1.getSession();
        int appBufferMax = session.getApplicationBufferSize();
        int netBufferMax = session.getPacketBufferSize();

        appIn1 = ByteBuffer.allocateDirect(appBufferMax + 50);
        appIn2 = ByteBuffer.allocateDirect(appBufferMax + 50);

        oneToTwo = ByteBuffer.allocateDirect(netBufferMax);
        twoToOne = ByteBuffer.allocateDirect(netBufferMax);

        appOut1 = ByteBuffer.wrap("Hi Engine2, I'm SSLEngine1".getBytes());
        appOut2 = ByteBuffer.wrap("Hello Engine1, I'm SSLEngine2".getBytes());

        System.out.println("AppOut1 = " + appOut1);
        System.out.println("AppOut2 = " + appOut2);
        System.out.println();
    }

    private static void runDelegatedTasks(SSLEngineResult result,
            SSLEngine engine) throws Exception {

        if (result.getHandshakeStatus().equals(HandshakeStatus.NEED_TASK)) {
            Runnable runnable;
            while ((runnable = engine.getDelegatedTask()) != null) {
                System.out.println("running delegated task...");
                runnable.run();
            }
        }
    }

    private static boolean isEngineClosed(SSLEngine engine) {
        return (engine.isOutboundDone() && engine.isInboundDone());
    }

}
