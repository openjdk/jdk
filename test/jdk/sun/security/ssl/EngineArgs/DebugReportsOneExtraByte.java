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

/*
 * @test
 * @bug 7126889
 * @summary Incorrect SSLEngine debug output
 * @library /test/lib /javax/net/ssl/templates
 * @run main DebugReportsOneExtraByte
 */
/*
 * Debug output was reporting n+1 bytes of data was written when it was
 * really was n.
 *
 *     SunJSSE does not support dynamic system properties, no way to re-use
 *     system properties in samevm/agentvm mode.
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
import java.nio.*;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.security.SecurityUtils;

public class DebugReportsOneExtraByte extends SSLEngineTemplate {

    /*
     * Enables logging of the SSLEngine operations.
     */
    private static boolean logging = true;

    /*
     * Main entry point for this test.
     */
    public static void main(String args[]) throws Exception {

        if (args.length == 0) {
            OutputAnalyzer output = ProcessTools.executeTestJava(
                "-Dtest.src=" + System.getProperty("test.src"),
                "-Djavax.net.debug=all", "DebugReportsOneExtraByte", "p");
            output.shouldContain("WRITE: TLSv1 application_data, length = 8");

            System.out.println("Test Passed.");
        } else {
            // Re-enable TLSv1 since test depends on it
            SecurityUtils.removeFromDisabledTlsAlgs("TLSv1");

            DebugReportsOneExtraByte test = new DebugReportsOneExtraByte();
            test.runTest();
        }
    }

    /*
     * Create an initialized SSLContext to use for these tests.
     */
    public DebugReportsOneExtraByte() throws Exception {
        super();
    }

    @Override
    protected SSLEngine configureServerEngine(SSLEngine serverEngine) {
        serverEngine.setUseClientMode(false);
        // Force a block-oriented ciphersuite.
        serverEngine.setEnabledCipherSuites(
                new String [] {"TLS_RSA_WITH_AES_128_CBC_SHA"});
        return serverEngine;
    }

    @Override
    protected ContextParameters getClientContextParameters() {
        return new ContextParameters("TLSv1", "PKIX", "NewSunX509");
    }

    @Override
    protected ContextParameters getServerContextParameters() {
        return new ContextParameters("TLSv1", "PKIX", "NewSunX509");
    }

    @Override
    protected ByteBuffer createClientOutputBuffer() {
        // No need to write anything on the client side, it will
        // just confuse the output.
        return ByteBuffer.wrap("".getBytes());
    }

    @Override
    protected ByteBuffer createServerOutputBuffer() {
        return ByteBuffer.wrap("Hi Client!".getBytes());
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

        SSLEngineResult clientResult;   // results from client's last operation
        SSLEngineResult serverResult;   // results from server's last operation

        /*
         * Examining the SSLEngineResults could be much more involved,
         * and may alter the overall flow of the application.
         *
         * For example, if we received a BUFFER_OVERFLOW when trying
         * to write to the output pipe, we could reallocate a larger
         * pipe, but instead we wait for the peer to drain it.
         */

        /*
         * Write one byte in first application packet, the rest
         * will come later.
         */
        serverOut.limit(1);

        while (!isEngineClosed(clientEngine) ||
                !isEngineClosed(serverEngine)) {

            log("================");

            clientResult = clientEngine.wrap(clientOut, cTOs);
            log("client wrap: ", clientResult);
            runDelegatedTasks(clientEngine);

            serverResult = serverEngine.wrap(serverOut, sTOc);
            log("server wrap: ", serverResult);
            runDelegatedTasks(serverEngine);

            // Next wrap will split.
            if (serverOut.position() == 1) {
                serverOut.limit(serverOut.capacity());
            }

            cTOs.flip();
            sTOc.flip();

            log("----");

            clientResult = clientEngine.unwrap(sTOc, clientIn);
            log("client unwrap: ", clientResult);
            runDelegatedTasks(clientEngine);

            serverResult = serverEngine.unwrap(cTOs, serverIn);
            log("server unwrap: ", serverResult);
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
                dataDone = true;
            }
        }
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
