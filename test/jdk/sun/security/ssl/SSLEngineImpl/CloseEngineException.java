/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4969799
 * @summary javax.net.ssl.SSLSocket.SSLSocket(InetAddress,int) shouldn't
 *              throw exception
 * @library /javax/net/ssl/templates
 * @run main/othervm CloseEngineException
 */

//
// This is making sure that starting a new handshake throws the right
// exception.  There is a similar test for SSLSocket.
//

import javax.net.ssl.*;

// Note that this test case depends on JSSE provider implementation details.
public class CloseEngineException extends SSLEngineTemplate {

    private static boolean debug = true;

    private void runTest() throws Exception {
        boolean dataDone = false;

        SSLEngineResult result1;        // clientEngine's results from last operation
        SSLEngineResult result2;        // ssle2's results from last operation

        while (!isEngineClosed(clientEngine) && !isEngineClosed(serverEngine)) {

            log("================");

            if (!isEngineClosed(clientEngine)) {
                result1 = clientEngine.wrap(clientOut, cTOs);
                runDelegatedTasks(clientEngine);

                log("wrap1:  " + result1);
                log("oneToTwo  = " + cTOs);
                log("");

                cTOs.flip();
            }
            if (!isEngineClosed(serverEngine)) {
                result2 = serverEngine.wrap(serverOut, sTOc);
                runDelegatedTasks(serverEngine);

                log("wrap2:  " + result2);
                log("twoToOne  = " + sTOc);

                sTOc.flip();
            }

            log("----");

            if (!isEngineClosed(clientEngine) && !dataDone) {
            log("--");
                result1 = clientEngine.unwrap(sTOc, clientIn);
                runDelegatedTasks(clientEngine);

                log("unwrap1: " + result1);
                log("twoToOne  = " + sTOc);
                log("");

                sTOc.compact();
            }
            if (!isEngineClosed(serverEngine)) {
            log("---");
                result2 = serverEngine.unwrap(cTOs, serverIn);
                runDelegatedTasks(serverEngine);

                log("unwrap2: " + result2);
                log("oneToTwo  = " + cTOs);

                cTOs.compact();
            }

            /*
             * If we've transfered all the data between app1 and app2,
             * we try to close and see what that gets us.
             */
            if (!dataDone && (clientOut.limit() == serverIn.position()) &&
                    (serverOut.limit() == clientIn.position())) {

                checkTransfer(clientOut, serverIn);
                checkTransfer(serverOut, clientIn);

                log("Closing clientEngine's *OUTBOUND*...");
                clientEngine.closeOutbound();
                dataDone = true;

                try {
                    /*
                     * Check that closed Outbound generates.
                     */
                    clientEngine.beginHandshake();
                    throw new Exception(
                        "TEST FAILED:  didn't throw Exception");
                } catch (SSLException e) {
                    System.err.println("PARTIAL PASS");
                }
            }
        }

        try {
            /*
             * Check that closed Inbound generates.
             */
            serverEngine.beginHandshake();
            throw new Exception(
                "TEST FAILED:  didn't throw Exception");
        } catch (SSLException e) {
            System.err.println("TEST PASSED");
        }
    }

    public static void main(String args[]) throws Exception {

        CloseEngineException test = new CloseEngineException();
        test.runTest();

        System.err.println("Test Passed.");
    }

    /*
     * **********************************************************
     * Majority of the test case is above, below is just setup stuff
     * **********************************************************
     */

    public CloseEngineException() throws Exception {
        super();
    }

    @Override
    protected SSLEngine configureServerEngine(SSLEngine engine) {
        engine.setUseClientMode(false);
        engine.setNeedClientAuth(true);
        return engine;
    }

    private static boolean isEngineClosed(SSLEngine engine) {
        return (engine.isOutboundDone() && engine.isInboundDone());
    }

    private static void log(String str) {
        if (debug) {
            System.err.println(str);
        }
    }
}
