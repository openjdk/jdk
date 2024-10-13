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

// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.

/*
 * @test
 * @bug 8207317
 * @summary SSLEngine negotiation fail Exception behavior changed from
 *          fail-fast to fail-lazy
 * @library /javax/net/ssl/templates
 * @run main/othervm SSLEngineFailedALPN
 */
/**
 * A SSLEngine usage example which simplifies the presentation
 * by removing the I/O and multi-threading concerns.
 *
 * The test creates two SSLEngines, simulating a client and server.
 * The "transport" layer consists two byte buffers:  think of them
 * as directly connected pipes.
 *
 * Note, this is a *very* simple example: real code will be much more
 * involved.  For example, different threading and I/O models could be
 * used, transport mechanisms could close unexpectedly, and so on.
 *
 * When this application runs, notice that several messages
 * (wrap/unwrap) pass before any application data is consumed or
 * produced.  (For more information, please see the SSL/TLS
 * specifications.)  There may several steps for a successful handshake,
 * so it's typical to see the following series of operations:
 *
 *      client          server          message
 *      ======          ======          =======
 *      wrap()          ...             ClientHello
 *      ...             unwrap()        ClientHello
 *      ...             wrap()          ServerHello/Certificate
 *      unwrap()        ...             ServerHello/Certificate
 *      wrap()          ...             ClientKeyExchange
 *      wrap()          ...             ChangeCipherSpec
 *      wrap()          ...             Finished
 *      ...             unwrap()        ClientKeyExchange
 *      ...             unwrap()        ChangeCipherSpec
 *      ...             unwrap()        Finished
 *      ...             wrap()          ChangeCipherSpec
 *      ...             wrap()          Finished
 *      unwrap()        ...             ChangeCipherSpec
 *      unwrap()        ...             Finished
 */
import javax.net.ssl.*;
import javax.net.ssl.SSLEngineResult.*;

public class SSLEngineFailedALPN extends SSLEngineTemplate {

    /*
     * Enables logging of the SSLEngine operations.
     */
    private static final boolean logging = true;

    /*
     * Enables the JSSE system debugging system property:
     *
     *     -Djavax.net.debug=all
     *
     * This gives a lot of low-level information about operations underway,
     * including specific handshake messages, and might be best examined
     * after gaining some familiarity with this application.
     */
    private static final boolean debug = false;

    /*
     * Main entry point for this test.
     */
    public static void main(String args[]) throws Exception {
        if (debug) {
            System.setProperty("javax.net.debug", "all");
        }

        SSLEngineFailedALPN test = new SSLEngineFailedALPN();
        test.runTest();

        System.out.println("Test Passed.");
    }

    /*
     * Create an initialized SSLContext to use for these tests.
     */
    public SSLEngineFailedALPN() throws Exception {
        super();
    }

    /*
     * Run the test.
     *
     * Sit in a tight loop, both engines calling wrap/unwrap regardless
     * of whether data is available or not.  We do this until both engines
     * report back they are closed.
     *
     * The main loop handles all of the I/O phases of the SSLEngine's
     * lifetime:
     *
     *     initial handshaking
     *     application data transfer
     *     engine closing
     *
     * One could easily separate these phases into separate
     * sections of code.
     */
    private void runTest() throws Exception {
        boolean dataDone = false;

        // results from client's last operation
        SSLEngineResult clientResult;

        // results from server's last operation
        SSLEngineResult serverResult;

        /*
         * Examining the SSLEngineResults could be much more involved,
         * and may alter the overall flow of the application.
         *
         * For example, if we received a BUFFER_OVERFLOW when trying
         * to write to the output pipe, we could reallocate a larger
         * pipe, but instead we wait for the peer to drain it.
         */
        Exception clientException = null;
        Exception serverException = null;

        while (!isEngineClosed(clientEngine)
                || !isEngineClosed(serverEngine)) {

            log("================");

            try {
                clientResult = clientEngine.wrap(clientOut, cTOs);
                log("client wrap: ", clientResult);
            } catch (Exception e) {
                clientException = e;
                System.out.println("Client wrap() threw: " + e.getMessage());
            }
            logEngineStatus(clientEngine);
            runDelegatedTasks(clientEngine);

            log("----");

            try {
                serverResult = serverEngine.wrap(serverOut, sTOc);
                log("server wrap: ", serverResult);
            } catch (Exception e) {
                serverException = e;
                System.out.println("Server wrap() threw: " + e.getMessage());
            }
            logEngineStatus(serverEngine);
            runDelegatedTasks(serverEngine);

            cTOs.flip();
            sTOc.flip();

            log("--------");

            try {
                clientResult = clientEngine.unwrap(sTOc, clientIn);
                log("client unwrap: ", clientResult);
            } catch (Exception e) {
                clientException = e;
                System.out.println("Client unwrap() threw: " + e.getMessage());
            }
            logEngineStatus(clientEngine);
            runDelegatedTasks(clientEngine);

            log("----");

            try {
                serverResult = serverEngine.unwrap(cTOs, serverIn);
                log("server unwrap: ", serverResult);
            } catch (Exception e) {
                serverException = e;
                System.out.println("Server unwrap() threw: " + e.getMessage());
            }
            logEngineStatus(serverEngine);
            runDelegatedTasks(serverEngine);

            cTOs.compact();
            sTOc.compact();

            /*
             * After we've transfered all application data between the client
             * and server, we close the clientEngine's outbound stream.
             * This generates a close_notify handshake message, which the
             * server engine receives and responds by closing itself.
             */
            if (!dataDone && (clientOut.limit() == serverIn.position()) &&
                    (serverOut.limit() == clientIn.position())) {

                /*
                 * A sanity check to ensure we got what was sent.
                 */
                checkTransfer(serverOut, clientIn);
                checkTransfer(clientOut, serverIn);

                log("\tClosing clientEngine's *OUTBOUND*...");
                clientEngine.closeOutbound();
                logEngineStatus(clientEngine);

                dataDone = true;
                log("\tClosing serverEngine's *OUTBOUND*...");
                serverEngine.closeOutbound();
                logEngineStatus(serverEngine);
            }
        }

        log("================");

        if ((clientException != null) &&
                (clientException instanceof SSLHandshakeException)) {
            log("Client threw proper exception");
            clientException.printStackTrace(System.out);
        } else {
            throw new Exception("Client Exception not seen");
        }

        if ((serverException != null) &&
                (serverException instanceof SSLHandshakeException)) {
            log("Server threw proper exception:");
            serverException.printStackTrace(System.out);
        } else {
            throw new Exception("Server Exception not seen");
        }
    }

    private static void logEngineStatus(SSLEngine engine) {
        log("\tCurrent HS State  " + engine.getHandshakeStatus().toString());
        log("\tisInboundDone():  " + engine.isInboundDone());
        log("\tisOutboundDone(): " + engine.isOutboundDone());
    }

    @Override
    protected SSLEngine configureServerEngine(SSLEngine engine) {
        engine.setUseClientMode(false);
        engine.setNeedClientAuth(true);

        // Get/set parameters if needed
        SSLParameters paramsServer = engine.getSSLParameters();
        paramsServer.setApplicationProtocols(new String[]{"one"});
        engine.setSSLParameters(paramsServer);
        return engine;
    }

    @Override
    protected SSLEngine configureClientEngine(SSLEngine engine) {
        engine.setUseClientMode(true);

        // Get/set parameters if needed
        SSLParameters paramsClient = engine.getSSLParameters();
        paramsClient.setApplicationProtocols(new String[]{"two"});
        engine.setSSLParameters(paramsClient);
        return engine;
    }

    private static boolean isEngineClosed(SSLEngine engine) {
        return (engine.isOutboundDone() && engine.isInboundDone());
    }

    /*
     * Logging code
     */
    private static boolean resultOnce = true;

    private static void log(String str, SSLEngineResult result) {
        if (!logging) {
            return;
        }
        if (resultOnce) {
            resultOnce = false;
            System.out.println("The format of the SSLEngineResult is: \n" +
                    "\t\"getStatus() / getHandshakeStatus()\" +\n" +
                    "\t\"bytesConsumed() / bytesProduced()\"\n");
        }
        HandshakeStatus hsStatus = result.getHandshakeStatus();
        log(str +
                result.getStatus() + "/" + hsStatus + ", " +
                result.bytesConsumed() + "/" + result.bytesProduced() +
                " bytes");
        if (hsStatus == HandshakeStatus.FINISHED) {
            log("\t...ready for application data");
        }
    }

    private static void log(String str) {
        if (logging) {
            System.out.println(str);
        }
    }
}
