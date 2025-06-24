/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8350830
 * @summary TLS 1.2 Client session resumption having ServerNameIndication
 * @modules java.base/sun.security.tools.keytool
 * @run main/othervm -Djavax.net.debug=all ResumeClientTLS12withSNI
 */

import javax.net.ssl.*;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.*;

public class ResumeClientTLS12withSNI {

    /*
     * Enables logging of the SSLEngine operations.
     */
    private static final boolean logging = true;

    private static SSLContext sslc;

    private SSLEngine clientEngine; // client Engine
    private ByteBuffer clientOut; // write side of clientEngine
    private ByteBuffer clientIn; // read side of clientEngine

    private SSLEngine serverEngine; // server Engine
    private ByteBuffer serverOut; // write side of serverEngine
    private ByteBuffer serverIn; // read side of serverEngine

    /*
     * For data transport, this example uses local ByteBuffers.
     */
    private ByteBuffer cTOs; // "reliable" transport client->server
    private ByteBuffer sTOc; // "reliable" transport server->client

    /*
     * The following is to set up the keystores.
     */
    private static final String keyFilename = "ks_san.p12";
    private static final String trustFilename = "ks_san.p12";
    private static final char[] passphrase = "123456".toCharArray();

    private static final String HOST_NAME = "arf.yak.foo.localhost123456.localhost123456.localhost123456.localhost123456.localhost123456.localhost123456."
            + "localhost123456.localhost123456.localhost123456.localhost123456.localhost123456.localhost123456";
    private static final SNIMatcher SNI_MATCHER = SNIHostName.createSNIMatcher("arf\\.yak\\.foo.*");

    /*
     * Main entry point for this test.
     */
    public static void main(String args[]) throws Exception {
        Files.deleteIfExists(Path.of(keyFilename));

        sun.security.tools.keytool.Main.main(
                ("-keystore " + keyFilename + " -storepass 123456 -keypass 123456 -dname"
                 + " CN=test" + " -alias ks_san -genkeypair -keyalg rsa -ext "
                 + "san=dns:localhost123.localhost123.localhost123.localhost123."
                 + "localhost123.localhost123.localhost123.localhost123.localhost123."
                 + "localhost123.localhost123.localhost123.localhost123.localhost123."
                 + "localhost123.localhost123.localhost123.localhost123.localhost123.com,"
                 + "dns:localhost456").split(" "));
        final ResumeClientTLS12withSNI clientSession = new ResumeClientTLS12withSNI("TLSv1.2");
        for (int i = 0; i < 2; i++) {
            clientSession.runTest();
        }

        Files.deleteIfExists(Path.of(keyFilename));
    }

    public ResumeClientTLS12withSNI(final String sslProtocol) throws Exception {

        KeyManagerFactory kmf = makeKeyManagerFactory(keyFilename,
                passphrase);
        TrustManagerFactory tmf = makeTrustManagerFactory(trustFilename,
                passphrase);

        SSLContext sslCtx = SSLContext.getInstance(sslProtocol);

        sslCtx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        sslc = sslCtx;
    }

    private static KeyManagerFactory makeKeyManagerFactory(String ksPath,
                                                           char[] pass) throws GeneralSecurityException, IOException {
        KeyManagerFactory kmf;
        KeyStore ks = KeyStore.getInstance("PKCS12");

        try (FileInputStream fsIn = new FileInputStream(ksPath)) {
            ks.load(fsIn, pass);
            kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, pass);
        }
        return kmf;
    }

    private static TrustManagerFactory makeTrustManagerFactory(String tsPath,
                                                               char[] pass) throws GeneralSecurityException, IOException {
        TrustManagerFactory tmf;
        KeyStore ts = KeyStore.getInstance("JKS");

        try (FileInputStream fsIn = new FileInputStream(tsPath)) {
            ts.load(fsIn, pass);
            tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(ts);
        }
        return tmf;
    }

    /*
     * Run the test.
     *
     * Sit in a tight loop, both engines calling wrap/unwrap regardless
     * of whether data is available or not. We do this until both engines
     * report back they are closed.
     *
     * The main loop handles all of the I/O phases of the SSLEngine's
     * lifetime:
     *
     * initial handshaking
     * application data transfer
     * engine closing
     *
     */
    private void runTest() throws Exception {
        boolean dataDone = false;

        createSSLEngines();
        createBuffers();

        SSLEngineResult clientResult; // results from client's last operation
        SSLEngineResult serverResult; // results from server's last operation

        /*
         * Examining the SSLEngineResults could be much more involved,
         * and may alter the overall flow of the application.
         *
         * For example, if we received a BUFFER_OVERFLOW when trying
         * to write to the output pipe, we could reallocate a larger
         * pipe, but instead we wait for the peer to drain it.
         */
        while (!isEngineClosed(clientEngine) ||
                !isEngineClosed(serverEngine)) {

            log("================");

            clientResult = clientEngine.wrap(clientOut, cTOs);
            log("client wrap: ", clientResult);
            runDelegatedTasks(clientResult, clientEngine);

            serverResult = serverEngine.wrap(serverOut, sTOc);
            log("server wrap: ", serverResult);
            runDelegatedTasks(serverResult, serverEngine);

            cTOs.flip();
            sTOc.flip();

            log("-------");

            clientResult = clientEngine.unwrap(sTOc, clientIn);
            log("client unwrap: ", clientResult);
            runDelegatedTasks(clientResult, clientEngine);

            serverResult = serverEngine.unwrap(cTOs, serverIn);
            log("server unwrap: ", serverResult);
            runDelegatedTasks(serverResult, serverEngine);

            cTOs.compact();
            sTOc.compact();

            /*
             * After we've transfered all application data between the client
             * and server, we close the clientEngine's outbound stream.
             * This generates a close_notify handshake message, which the
             * server engine receives and responds by closing itself.
             *
             * In normal operation, each SSLEngine should call
             * closeOutbound(). To protect against truncation attacks,
             * SSLEngine.closeInbound() should be called whenever it has
             * determined that no more input data will ever be
             * available (say a closed input stream).
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
                // serverEngine.closeOutbound();
                dataDone = true;
            }
        }
    }

    /*
     * Using the SSLContext created during object creation,
     * create/configure the SSLEngines we'll use for this test.
     */
    private void createSSLEngines() throws Exception {
        /*
         * Configure the serverEngine to act as a server in the SSL/TLS
         * handshake.
         */
        serverEngine = sslc.createSSLEngine();
        serverEngine.setUseClientMode(false);
        serverEngine.setNeedClientAuth(false);
        SSLParameters servSSLParams = serverEngine.getSSLParameters();
        servSSLParams.setSNIMatchers(List.of(SNI_MATCHER));
        serverEngine.setSSLParameters(servSSLParams);

        /*
         * Similar to above, but using client mode instead.
         */
        clientEngine = sslc.createSSLEngine(HOST_NAME, 80);
        clientEngine.setUseClientMode(true);
        SSLParameters cliSSLParams = clientEngine.getSSLParameters();
        clientEngine.setSSLParameters(cliSSLParams);
    }

    /*
     * Create and size the buffers appropriately.
     */
    private void createBuffers() {

        /*
         * We'll assume the buffer sizes are the same
         * between client and server.
         */
        SSLSession session = clientEngine.getSession();
        int appBufferMax = session.getApplicationBufferSize();
        int netBufferMax = session.getPacketBufferSize();

        /*
         * We'll make the input buffers a bit bigger than the max needed
         * size, so that unwrap()s following a successful data transfer
         * won't generate BUFFER_OVERFLOWS.
         *
         * We'll use a mix of direct and indirect ByteBuffers for
         * tutorial purposes only. In reality, only use direct
         * ByteBuffers when they give a clear performance enhancement.
         */
        clientIn = ByteBuffer.allocate(appBufferMax + 50);
        serverIn = ByteBuffer.allocate(appBufferMax + 50);

        cTOs = ByteBuffer.allocateDirect(netBufferMax);
        sTOc = ByteBuffer.allocateDirect(netBufferMax);

        clientOut = ByteBuffer.wrap("Hi Server, I'm Client".getBytes());
        serverOut = ByteBuffer.wrap("Hello Client, I'm Server".getBytes());
    }

    /*
     * If the result indicates that we have outstanding tasks to do,
     * go ahead and run them in this thread.
     */
    private static void runDelegatedTasks(SSLEngineResult result,
                                          SSLEngine engine) throws Exception {

        if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
            Runnable runnable;
            while ((runnable = engine.getDelegatedTask()) != null) {
                log("\trunning delegated task...");
                runnable.run();
            }
            HandshakeStatus hsStatus = engine.getHandshakeStatus();
            if (hsStatus == HandshakeStatus.NEED_TASK) {
                throw new Exception(
                        "handshake shouldn't need additional tasks");
            }
            log("\tnew HandshakeStatus: " + hsStatus);
        }
    }

    private static boolean isEngineClosed(SSLEngine engine) {
        return (engine.isOutboundDone() && engine.isInboundDone());
    }

    /*
     * Simple check to make sure everything came across as expected.
     */
    private static void checkTransfer(ByteBuffer a, ByteBuffer b)
            throws Exception {
        a.flip();
        b.flip();

        if (!a.equals(b)) {
            throw new Exception("Data didn't transfer cleanly");
        } else {
            log("\tData transferred cleanly");
        }

        a.position(a.limit());
        b.position(b.limit());
        a.limit(a.capacity());
        b.limit(b.capacity());
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
