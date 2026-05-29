/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8329548
 * @library ../../
 *          /test/lib
 *          /javax/net/ssl/templates
 * @summary Verify KeyUpdate messages skipped after first one sent.
 *
 * @run main KeyUpdateOnce server TLS_AES_256_GCM_SHA384 200000
 * @run main KeyUpdateOnce client TLS_AES_256_GCM_SHA384 200000
 */

/*
 * This test runs in another process so we can monitor the debug
 * results.  The OutputAnalyzer must see correct debug output to return a
 * success.
 */

import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * This server/client TLS test will force side A to stop reading as it
 * continuously writes out.  These write ops will trigger the side B to
 * request a KeyUpdate.  With side A not reading, side B must skip
 * sending more KeyUpdate messages.  Only one KeyUpdate message will be
 * sent by side B.
 *
 * This test depends on debug messages string match.  Changing the KeyUpdate-
 * related messages may cause a failure.
 */

public class KeyUpdateOnce extends SSLContextTemplate {

    private static final int DATALEN = 10240;
    private static final int BUF_DATALEN = 4 * DATALEN;
    private static final int MAXLOOPS = 150;
    private static final int COUNTDOWNLIMIT = 5;

    private static final boolean DEBUG = true;

    private static ByteBuffer cTos;
    private static ByteBuffer sToc;
    private static ByteBuffer outData;
    private final ByteBuffer inData;

    // thread flags
    private static boolean ready = false;
    private static boolean sc = true;
    private static boolean readDone = false;
    private static boolean serverWrites = true;

    private static long newLimit;

    // Reflection handle captured on read side once handshake completes
    private static Object readSideInputRecord = null;

    protected SSLEngine engine;
    private final int delay = 1;
    private int totalDataLen = 0;

    KeyUpdateOnce() {
        this.inData = ByteBuffer.allocate(BUF_DATALEN);
    }

    /**
     * args should have:
     *   server|client, cipherSuite, <limit size>
     *
     * Prepending 'p' is for internal use only (test harness relaunch).
     */
    public static void main(String[] args) throws Exception {
        for (String arg : args) {
            System.out.print(" " + arg);
        }
        System.out.println();

        // Harness mode: relaunch self with 'p' to force add-opens + debugging flags
        if (!"p".equals(args[0])) {
            // args[]: 0 = client/server, 1 = cipher suite, 2 = newLimit
            System.setProperty("test.java.opts",
                System.getProperty("test.java.opts") +
                    " -Dtest.src=" + System.getProperty("test.src") +
                    " -Dtest.jdk=" + System.getProperty("test.jdk") +
                    " -Djavax.net.debug=ssl,handshake" +
                    " -Djavatest.maxOutputSize=99999999" +
                    " --add-opens java.base/sun.security.ssl=ALL-UNNAMED");

            System.out.println("test.java.opts: " +
                System.getProperty("test.java.opts"));

            ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                Utils.addTestJavaOpts("KeyUpdateOnce", "p", args[0],
                    args[1], args[2]));

            OutputAnalyzer output = ProcessTools.executeProcess(pb);
            try {
                output.shouldContain(String.format(
                    "\"cipher suite\"        : \"%s", args[1]));
                System.err.println("Output logs should show KeyUpdate has" +
                    " been sent and skipped");
                List<String> producedList = output.asLines().stream()
                    .filter(s -> s.contains("Produced KeyUpdate"))
                    .toList();
                List<String> skippingList = output.asLines().stream()
                    .filter(s -> s.contains("KeyUpdate already sent, skipping"))
                    .toList();
                producedList.forEach(System.err::println);
                skippingList.forEach(System.err::println);
                System.err.println("\"Produced KeyUpdate\" count = " + producedList.size());
                System.err.println("\"KeyUpdate already send, skipping\" count = " + skippingList.size());

                /*
                 * Sometimes debug messages may not be consistent.  The below
                 * checks verify that at least 1 of each message were received.
                 */
                // Ideally there should be 2 "Produced KeyUpdate"
                if (producedList.isEmpty()) {
                    throw new AssertionError("No \"Produced KeyUpdate\"");
                }
                // Ideally there should be 5 "KeyUpdate already send, skipping"
                if (skippingList.isEmpty()) {
                    throw new AssertionError("No \"KeyUpdate already send, skipping\"");
                }

            } finally {
                System.out.println("-- BEGIN Stdout:");
                System.out.println(output.getStdout());
                System.out.println("-- END Stdout");
                System.out.println("-- BEGIN Stderr:");
                System.out.println(output.getStderr());
                System.out.println("-- END Stderr");
            }
            return;
        }

        // Worker mode:
        // args[]: 0 = p, 1 = client/server, 2 = cipher suite, 3 = newLimit
        serverWrites = !"client".equals(args[1]);
        newLimit = Long.parseLong(args[3]);

        cTos = ByteBuffer.allocateDirect(BUF_DATALEN);
        sToc = ByteBuffer.allocateDirect(BUF_DATALEN);
        outData = ByteBuffer.allocateDirect(DATALEN);

        byte[] data = new byte[DATALEN];
        Arrays.fill(data, (byte) 0x0A);
        outData.put(data).flip();

        cTos.clear();
        sToc.clear();

        Thread peer = new Thread(serverWrites ? new Client() :
            new Server(args[2]));
        peer.start();

        (serverWrites ? new Server(args[2]) : new Client()).run();

        peer.interrupt();
        peer.join();
    }

    private static void doTask(SSLEngineResult result, SSLEngine engine)
        throws Exception {
        if (result.getHandshakeStatus() ==
            SSLEngineResult.HandshakeStatus.NEED_TASK) {
            Runnable runnable;
            while ((runnable = engine.getDelegatedTask()) != null) {
                print("\trunning delegated task...");
                runnable.run();
            }
            SSLEngineResult.HandshakeStatus hsStatus =
                engine.getHandshakeStatus();
            if (hsStatus == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                throw new Exception("handshake shouldn't need additional tasks");
            }
            print("\tnew HandshakeStatus: " + hsStatus);
        }
    }

    private static void print(String s) {
        if (DEBUG) {
            System.err.println(s);
        }
    }

    private static void log(String s, SSLEngineResult r) {
        if (DEBUG) {
            System.err.println(s + ": " +
                r.getStatus() + "/" + r.getHandshakeStatus() + " " +
                r.bytesConsumed() + "/" + r.bytesProduced());
        }
    }

    private static void dumpBuffers(String aName, ByteBuffer a) {
        if (DEBUG) {
            System.err.println(aName + " pos=" + a.position() +
                " rem=" + a.remaining() +
                " lim=" + a.limit() + " cap=" + a.capacity());
        }
    }

    void writeLoop() throws Exception {
        int i = 0;
        SSLEngineResult r;
        int countdown = COUNTDOWNLIMIT;

        while (!ready) {
            Thread.sleep(delay);
        }

        print("Write-side begins");

        while (i++ < MAXLOOPS) {
            while (sc) {
                if (readDone) {
                    return;
                }
                Thread.sleep(delay);
            }

            outData.rewind();

            while (true) {
                r = engine.wrap(outData, getWriteBuf());
                log("write wrap", r);

                if (DEBUG && r.getStatus() != SSLEngineResult.Status.OK) {
                    dumpBuffers("outData", outData);
                    dumpBuffers("writeBuf", getWriteBuf());
                }

                if (r.getStatus() == SSLEngineResult.Status.OK &&
                    r.getHandshakeStatus() ==
                        SSLEngineResult.HandshakeStatus.NEED_WRAP) {
                    continue;
                }
                break;
            }

            doTask(r, engine);

            getWriteBuf().flip();
            sc = true;

            while (sc) {
                if (readDone) {
                    return;
                }
                Thread.sleep(delay);
            }

            long rlimit = Long.MAX_VALUE;
            if (readSideInputRecord != null) {
                rlimit = getReadLimit(readSideInputRecord);
            }
            if (rlimit <= 0) {
                countdown--;
            }
            System.err.println("Write side readLimit = " + rlimit);

            if (countdown == COUNTDOWNLIMIT || countdown <= 0) {
                inData.clear();
                r = engine.unwrap(getReadBuf(), inData);
                log("write unwrap", r);

                if (DEBUG && r.getStatus() != SSLEngineResult.Status.OK) {
                    dumpBuffers("inData", inData);
                    dumpBuffers("readBuf", getReadBuf());
                }
            } else {
                print("write side unwrap skipped");
            }

            doTask(r, engine);
            getReadBuf().compact();
            dumpBuffers("compacted getReadBuf()", getReadBuf());
            sc = true;
        }
    }

    void readLoop() throws Exception {
        byte b = 0x0B;
        ByteBuffer buf = ByteBuffer.allocateDirect(DATALEN);

        SSLEngineResult r = null;
        boolean again = true;
        boolean firstNotHandshake = false;

        while (engine == null) {
            Thread.sleep(delay);
        }

        try {
            System.out.println("connected");
            print("entering read loop");
            ready = true;

            while (true) {
                while (!sc) {
                    Thread.sleep(delay);
                }

                boolean exit = false;
                while (!exit) {
                    buf.put(b);
                    buf.flip();

                    r = engine.wrap(buf, getWriteBuf());
                    log("read wrap", r);

                    if (DEBUG) {
                        dumpBuffers("buf", buf);
                        dumpBuffers( "writeBuf", getWriteBuf());
                    }

                    if (again && r.getStatus() == SSLEngineResult.Status.OK &&
                        r.getHandshakeStatus() ==
                            SSLEngineResult.HandshakeStatus.NEED_WRAP) {
                        buf.compact();
                        again = false;
                        continue;
                    }
                    exit = true;
                }

                doTask(r, engine);

                buf.clear();
                getWriteBuf().flip();
                sc = false;

                while (!sc) {
                    Thread.sleep(delay);
                }

                while (true) {
                    inData.clear();
                    r = engine.unwrap(getReadBuf(), inData);
                    log("read unwrap", r);

                    if (DEBUG && r.getStatus() != SSLEngineResult.Status.OK) {
                        dumpBuffers("inData", inData);
                        dumpBuffers("readBuf", getReadBuf());

                        doTask(r, engine);
                    }

                    if (again && r.getStatus() == SSLEngineResult.Status.OK &&
                        r.getHandshakeStatus() ==
                            SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
                        inData.clear();
                        print("again");
                        again = false;
                        continue;
                    }
                    break;
                }

                inData.clear();
                getReadBuf().compact();

                totalDataLen += r.bytesProduced();
                sc = false;

                if (!firstNotHandshake &&
                    r.getHandshakeStatus() ==
                        SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {

                    try {
                        readSideInputRecord = getInputRecord(engine);
                        setReadLimit(readSideInputRecord, newLimit);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    System.err.println("Resetting readside");
                    firstNotHandshake = true;
                }
            }
        } catch (Exception e) {
            sc = false;
            readDone = true;

            System.out.println(e.getMessage());
            e.printStackTrace();
            System.out.println("Total data read = " + totalDataLen);
        }
    }

    // Overridden in Server/Client
    ByteBuffer getReadBuf() {
        return null;
    }
    ByteBuffer getWriteBuf() {
        return null;
    }

    SSLContext initContext() throws Exception {
        return createServerSSLContext();
    }

    @Override
    protected SSLContextTemplate.ContextParameters getServerContextParameters() {
        return new SSLContextTemplate.ContextParameters("TLSv1.3", "PKIX", "NewSunX509");
    }

    static Object getInputRecord(SSLEngine eng) throws Exception {
        Class<?> engineImplCls = Class.forName("sun.security.ssl.SSLEngineImpl");
        Object conContext = getPrivate(eng, engineImplCls, "conContext");

        Class<?> transportCtxCls = Class.forName("sun.security.ssl.TransportContext");
        return getPrivate(conContext, transportCtxCls, "inputRecord");
    }

    static void setReadLimit(Object inputRecord, long newCountdown) throws Exception {
        Class<?> inputRecordCls = Class.forName("sun.security.ssl.InputRecord");
        Object readCipher = getPrivate(inputRecord, inputRecordCls, "readCipher");
        Class<?> sslReadCipher = readCipher.getClass().getSuperclass();

        Field f = getField(sslReadCipher, "keyLimitCountdown");
        f.setLong(readCipher, newCountdown);
    }

    static long getReadLimit(Object inputRecord) throws Exception {
        Class<?> inputRecordCls = Class.forName("sun.security.ssl.InputRecord");
        Object readCipher = getPrivate(inputRecord, inputRecordCls, "readCipher");
        Class<?> sslReadCipher = readCipher.getClass().getSuperclass();

        Field f = getField(sslReadCipher, "keyLimitCountdown");
        return f.getLong(readCipher);
    }

    private static Field getField(Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true); // requires --add-opens for sun.security.ssl
        return f;
    }

    private static Object getPrivate(Object target, Class<?> owner, String name) throws Exception {
        return getField(owner, name).get(target);
    }

    static class Server extends KeyUpdateOnce implements Runnable {
        Server(String cipherSuite) throws Exception {
            super();
            engine = initContext().createSSLEngine();
            engine.setUseClientMode(false);
            engine.setNeedClientAuth(true);

            if (cipherSuite != null && !cipherSuite.isEmpty()) {
                engine.setEnabledCipherSuites(new String[] { cipherSuite });
            }
        }

        @Override
        public void run() {
            try {
                if (serverWrites) {
                    writeLoop();
                } else {
                    readLoop();
                }
            } catch (Exception e) {
                System.out.println("server: " + e.getMessage());
                e.printStackTrace();
            }
            System.out.println("Server closed");
        }

        @Override
        ByteBuffer getWriteBuf() {
            return sToc;
        }

        @Override
        ByteBuffer getReadBuf() {
            return cTos;
        }
    }

    static class Client extends KeyUpdateOnce implements Runnable {
        Client() throws Exception {
            super();
            engine = initContext().createSSLEngine();
            engine.setUseClientMode(true);
        }

        @Override
        public void run() {
            try {
                if (!serverWrites) {
                    writeLoop();
                } else {
                    readLoop();
                }
            } catch (Exception e) {
                System.out.println("client: " + e.getMessage());
                e.printStackTrace();
            }
            System.out.println("Client closed");
        }

        @Override
        ByteBuffer getWriteBuf() {
            return cTos;
        }

        @Override
        ByteBuffer getReadBuf() {
            return sToc;
        }
    }
}
