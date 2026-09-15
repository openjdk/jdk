/*
 * Copyright (c) 2004, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4980882 8207250 8237474 8382270
 * @summary SSLEngine should enforce setUseClientMode
 * @library /javax/net/ssl/templates
 *          /test/lib
 * @run main EngineEnforceUseClientMode
 * @author Brad R. Wetmore
 */

import static jdk.test.lib.Asserts.assertFalse;
import static jdk.test.lib.Asserts.assertTrue;

import javax.net.ssl.*;

public class EngineEnforceUseClientMode extends SSLEngineTemplate {

    private static final boolean debug = false;

    private void runTest() throws Exception {
        SSLContext sslc = createServerSSLContext();

        SSLEngine engine1 = sslc.createSSLEngine();
        SSLEngine engine2 = sslc.createSSLEngine();
        SSLEngine engine3 = sslc.createSSLEngine();
        SSLEngine engine4 = sslc.createSSLEngine();

        /*
         * First try the engines with no client/server initialization
         * All should fail.
         */
        try {
            System.out.println("Testing wrap()");
            engine1.wrap(clientOut, cTOs);
            throw new RuntimeException(
                "wrap():  Didn't catch the exception properly");
        } catch (IllegalStateException e) {
            System.out.println("Caught the correct exception.");
            cTOs.flip();
            if (cTOs.hasRemaining()) {
                throw new Exception("wrap generated data");
            }
            cTOs.clear();
        }

        try {
            System.out.println("Testing unwrap()");
            engine2.unwrap(cTOs, clientIn);
            throw new RuntimeException(
                "unwrap():  Didn't catch the exception properly");
        } catch (IllegalStateException e) {
            System.out.println("Caught the correct exception.");
            clientIn.flip();
            if (clientIn.hasRemaining()) {
                throw new Exception("unwrap generated data");
            }
            clientIn.clear();
        }

        try {
            System.out.println("Testing beginHandshake()");
            engine3.beginHandshake();
            throw new RuntimeException(
                "unwrap():  Didn't catch the exception properly");
        } catch (IllegalStateException e) {
            System.out.println("Caught the correct exception.");
        }

        try {
            System.out.println("Testing getUseClientMode()");
            engine4.getUseClientMode();
            throw new RuntimeException(
                    "unwrap():  Didn't catch the exception properly");
        } catch (IllegalStateException e) {
            System.out.println("Caught the correct exception.");
        }

        // Now set the mode and verify that we can get it.
        engine4.setUseClientMode(true);
        assertTrue(engine4.getUseClientMode());

        engine1.setUseClientMode(false);
        assertFalse(engine1.getUseClientMode());

        boolean dataDone = false;

        SSLEngineResult result1;        // ssle1's results from last operation
        SSLEngineResult result2;        // ssle2's results from last operation

        while (!isEngineClosed(clientEngine) || !isEngineClosed(serverEngine)) {

            log("================");

            result1 = clientEngine.wrap(clientOut, cTOs);
            result2 = serverEngine.wrap(serverOut, sTOc);

            log("wrap1:  " + result1);
            log("oneToTwo  = " + cTOs);
            log("");

            log("wrap2:  " + result2);
            log("twoToOne  = " + sTOc);

            runDelegatedTasks(clientEngine);
            runDelegatedTasks(serverEngine);

            cTOs.flip();
            sTOc.flip();

            log("----");

            result1 = clientEngine.unwrap(sTOc, clientIn);
            result2 = serverEngine.unwrap(cTOs, serverIn);

            log("unwrap1: " + result1);
            log("twoToOne  = " + sTOc);
            log("");

            log("unwrap2: " + result2);
            log("oneToTwo  = " + cTOs);

            runDelegatedTasks(clientEngine);
            runDelegatedTasks(serverEngine);

            cTOs.compact();
            sTOc.compact();

            /*
             * If we've transfered all the data between app1 and app2,
             * we try to close and see what that gets us.
             */
            if (!dataDone && (clientOut.limit() == serverIn.position()) &&
                    (serverOut.limit() == clientIn.position())) {

                checkTransfer(clientOut, serverIn);
                checkTransfer(serverOut, clientIn);

                // Should not be able to set mode now, no matter if
                // it is the same of different.
                System.out.println("Try changing modes...");
                for (boolean b : new Boolean[] {true, false}) {
                    try {
                        serverEngine.setUseClientMode(b);
                        throw new RuntimeException(
                                "setUseClientMode(" + b + "):  " +
                                        "Didn't catch the exception properly");
                    } catch (IllegalArgumentException e) {
                        System.out.println("Caught the correct exception.");
                    }
                }

                return;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new EngineEnforceUseClientMode().runTest();
        System.out.println("Test Passed.");
    }

    /*
     * **********************************************************
     * Majority of the test case is above, below is just setup stuff
     * **********************************************************
     */

    public EngineEnforceUseClientMode() throws Exception {
        super();
    }

    private static boolean isEngineClosed(SSLEngine engine) {
        return (engine.isOutboundDone() && engine.isInboundDone());
    }


    private static void log(String str) {
        if (debug) {
            System.out.println(str);
        }
    }
}
