/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4980882 8207250 8237474
 * @summary SSLEngine should enforce setUseClientMode
 * @library /javax/net/ssl/templates
 * @run main/othervm EngineEnforceUseClientMode
 * @author Brad R. Wetmore
 */

import javax.net.ssl.*;

public class EngineEnforceUseClientMode extends SSLEngineTemplate {

    private static boolean debug = false;

    private SSLEngine serverEngine2;    // server
    private SSLEngine serverEngine3;    // server
    private SSLEngine serverEngine4;    // server

    /*
     * Majority of the test case is here, setup is done below.
     */
    private void createAdditionalSSLEngines() throws Exception {
        SSLContext sslc = createServerSSLContext();
        /*
         * Note, these are not initialized to client/server
         */
        serverEngine2 = sslc.createSSLEngine();
        serverEngine3 = sslc.createSSLEngine();
        serverEngine4 = sslc.createSSLEngine();
        //Check default SSLEngine role.
        if (serverEngine4.getUseClientMode()) {
            throw new RuntimeException("Expected default role to be server");
        }

    }

    private void runTest() throws Exception {

        createAdditionalSSLEngines();

        /*
         * First try the engines with no client/server initialization
         * All should fail.
         */
        try {
            System.out.println("Testing wrap()");
            serverEngine2.wrap(clientOut, cTOs);
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
            serverEngine3.unwrap(cTOs, clientIn);
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
            serverEngine4.beginHandshake();
            throw new RuntimeException(
                "unwrap():  Didn't catch the exception properly");
        } catch (IllegalStateException e) {
            System.out.println("Caught the correct exception.");
        }

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

    public static void main(String args[]) throws Exception {

        EngineEnforceUseClientMode test = new EngineEnforceUseClientMode();
        test.createAdditionalSSLEngines();
        test.runTest();

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
