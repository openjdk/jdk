
/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8217408
 * @summary Verify that setting multiple instances of the same cipher suite does
 * not result in extra reported suites
 * @run main/othervm CheckDuplicateCipherSuites
 */

import javax.net.ssl.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.io.IOException;

public class CheckDuplicateCipherSuites {
    enum CipherSuite {
        TLS_AES_256_GCM_SHA384
                (0x1302,    "TLS_AES_256_GCM_SHA384"),
        TLS_AES_128_GCM_SHA256
                (0x1301,    "TLS_AES_128_GCM_SHA256"),
        TLS_CHACHA20_POLY1305_SHA256
                (0x1303,    "TLS_CHACHA20_POLY1305_SHA256"),
        TLS_DHE_RSA_WITH_AES_128_CBC_SHA
                (0x0033,    "TLS_DHE_RSA_WITH_AES_128_CBC_SHA"),
        TLS_DHE_RSA_WITH_AES_128_CBC_SHA256
                (0x0067,    "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256"),
        TLS_DHE_RSA_WITH_AES_128_GCM_SHA256
                (0x009E,    "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256"),
        TLS_DHE_RSA_WITH_AES_256_CBC_SHA
                (0x0039,    "TLS_DHE_RSA_WITH_AES_256_CBC_SHA"),
        TLS_DHE_RSA_WITH_AES_256_CBC_SHA256
                (0x006B,    "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256"),
        TLS_DHE_RSA_WITH_AES_256_GCM_SHA384
                (0x009F,    "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384");

        final int id;
        final String name;

        CipherSuite(int id, String name) {
            this.id = id;
            this.name = name;
        }

        /**
         * Get a list of the ciphersuite names only
         *
         * @param orig a list of CipherSuite enums
         *
         * @return a list of ciphersuite String names
         */
        private static List<String> names(List<CipherSuite> orig) {
            List<String> names = new ArrayList<>();
            orig.forEach(cs -> names.add(cs.name));
            return names;
        }

        /**
         * Get a list of the ciphersuite ids only
         *
         * @param orig a list of CipherSuite enums
         *
         * @return a list of ciphersuite hex IDs
         */
        private static List<Integer> ids(List<CipherSuite> orig) {
            List<Integer> ids = new ArrayList<>();
            orig.forEach(cs -> ids.add(cs.id));
            return ids;
        }
    }

    enum ProtocolVersion {
        TLS13(0x0304, "TLSv1.3"),
        TLS12(0x0303, "TLSv1.2"),
        TLS11(0x0302, "TLSv1.1"),
        TLS10(0x0301, "TLSv1"),
        SSL30(0x0300, "SSLv3"),
        SSL20Hello(0x0002, "SSLv2Hello"),

        DTLS12(0xFEFD, "DTLSv1.2"),
        DTLS10(0xFEFF, "DTLSv1.0");

        final int id;
        final String name;

        ProtocolVersion(int id, String name) {
            this.id = id;
            this.name = name;
        }

        /**
         * Get a list of the protocol version names only
         *
         * @param orig a list of ProtocolVersion enums
         *
         * @return a list of protocol version String names
         */
        private static List<String> names(List<ProtocolVersion> orig) {
            List<String> names = new ArrayList<>();
            orig.forEach(cs -> names.add(cs.name));
            return names;
        }

        /**
         * Get a list of the protocol version ids only
         *
         * @param orig a list of ProtocolVersion enums
         *
         * @return a list of protocol version hex IDs
         */
        private static List<Integer> ids(List<ProtocolVersion> orig) {
            List<Integer> ids = new ArrayList<>();
            orig.forEach(cs -> ids.add(cs.id));
            return ids;
        }
    }

    static final int TLS_RECORD_HANDSHAKE = 22;
    static final int TLS_HANDSHAKE_CLIHELLO = 1;
    static final int HELLO_EXT_SUPP_VERS = 43;
    static final List<CipherSuite> malfCS = List.of(
            CipherSuite.TLS_AES_256_GCM_SHA384,
            CipherSuite.TLS_AES_128_GCM_SHA256,
            CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA256,
            CipherSuite.TLS_DHE_RSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA,
            CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA256,
            CipherSuite.TLS_DHE_RSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA256,
            CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA256,
            CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA256);
    static final List<ProtocolVersion> malfPV = List.of(
            ProtocolVersion.TLS11,
            ProtocolVersion.SSL20Hello,
            ProtocolVersion.DTLS12,
            ProtocolVersion.TLS13,
            ProtocolVersion.DTLS10,
            ProtocolVersion.TLS12,
            ProtocolVersion.SSL30,
            ProtocolVersion.TLS10,
            ProtocolVersion.TLS13,
            ProtocolVersion.TLS13,
            ProtocolVersion.TLS13);
    static final String[] malfCSNameArr =
            CipherSuite.names(malfCS).toArray(new String[0]);
    static final String[] cleanCSNameArr =
            CipherSuite.names(clearDups(malfCS)).toArray(new String[0]);
    static final String[] malfPVNameArr =
            ProtocolVersion.names(malfPV).toArray(new String[0]);
    static final String[] cleanPVNamerArr =
            ProtocolVersion.names(clearDups(malfPV)).toArray(new String[0]);
    static SSLContext defaultCtx;

    public static void main(String[] args) throws Exception {
        defaultCtx = SSLContext.getDefault();
        /*
        testEngine();
        testSocket();
        testParam();
        */
        ByteBuffer transitData = clientHelloEnv();
        checkClientHello(transitData);
    }

    /**
     * Creates an SSLEngine from the default context, sets its cipher suites
     * and protocol versions to the malformed arrays, then queries the
     * engine for its ciphersuites and protocol versions.
     *
     * @throws RuntimeException if the engine's cipher suites or protocol
     * versions do not match the expected flattened array.
     */
    static void testEngine() throws IOException{
        SSLEngine sslEng  = defaultCtx.createSSLEngine();
        sslEng.setEnabledCipherSuites(malfCSNameArr);
        sslEng.setEnabledProtocols(malfPVNameArr);

        if (!Arrays.equals(sslEng.getEnabledCipherSuites(), cleanCSNameArr)) {
            throw new RuntimeException("SSLEngine: getEnabledCipherSuites " +
                    "does not return expected output");
        }

        if (!Arrays.equals(sslEng.getEnabledProtocols(), cleanPVNamerArr)) {
            throw new RuntimeException("SSLEngine: getEnabledProtocols " +
                    "does not return expected output");
        }
    }

    /**
     * Functions the same as testEngine but uses an SSLSocket
     *
     * @throws IOException if the socket's cipher suites or protocol versions
     * do not match the expected flattened array
     */
    static void testSocket() throws IOException {
        SSLSocketFactory sslSF = defaultCtx.getSocketFactory();
        SSLSocket sslSoc = (SSLSocket) sslSF.createSocket();
        sslSoc.setEnabledCipherSuites(malfCSNameArr);
        sslSoc.setEnabledProtocols(malfPVNameArr);

        if (!Arrays.equals(sslSoc.getEnabledCipherSuites(), cleanCSNameArr)) {
            throw new RuntimeException("SSLSocket: getEnabledCipherSuites " +
                    "does not return expected output");
        }

        if (!Arrays.equals(sslSoc.getEnabledProtocols(), cleanPVNamerArr)) {
            throw new RuntimeException("SSLSocket: getEnabledProtocols " +
                    "does not return expected output");
        }
    }

    /**
     * Functions the same as testEngine but uses a socket that has been
     * modified via the SSLParamaters
     *
     * @throws RuntimeException if the socket's cipher suites or protocol
     * versions do not match the expected flattened array.
     */
    static void testParam() throws IOException {
        SSLParameters modParams =
                new SSLParameters(malfCSNameArr, malfPVNameArr);
        SSLSocketFactory sslSF = defaultCtx.getSocketFactory();
        SSLSocket modSSLS = (SSLSocket) sslSF.createSocket();
        modSSLS.setSSLParameters(modParams);

        if (!Arrays.equals(modSSLS.getEnabledCipherSuites(), cleanCSNameArr)) {
            throw new RuntimeException("SSLSocket modified via parameters: " +
                    "getEnabledCipherSuites does not return expected output");
        }

        if (!Arrays.equals(modSSLS.getEnabledProtocols(), cleanPVNamerArr)) {
            throw new RuntimeException("SSLSocket: getEnabledProtocols " +
                    "does not return expected output");
        }
    }

    /**
     * Create an SSLEngine from the default context and initiate a
     * ClientHello to be evaluated.
     */
    private static ByteBuffer clientHelloEnv() throws Exception {
        SSLEngine eng = defaultCtx.createSSLEngine();
        eng.setEnabledCipherSuites(malfCSNameArr);
        eng.setEnabledProtocols(malfPVNameArr);
        eng.setUseClientMode(true);
        SSLSession session = eng.getSession();
        ByteBuffer clientOut = ByteBuffer.wrap("Client".getBytes());
        ByteBuffer cTOs =
                ByteBuffer.allocateDirect(session.getPacketBufferSize());

        // Create and check the ClientHello message
        SSLEngineResult clientResult = eng.wrap(clientOut, cTOs);
        if (clientResult.getStatus() != SSLEngineResult.Status.OK) {
            throw new RuntimeException("Client wrap status: "
                    + clientResult.getStatus());
        }

        cTOs.flip();
        return cTOs;
    }

    /**
     * Examine the ClientHello to check the agreed upon cipher suites
     * for handling of duplicate entries.
     *
     * @param data the ByteBuffer containing the ClientHello bytes
     */
    private static void checkClientHello(ByteBuffer data) {
        Objects.requireNonNull(data);
        data.mark();

        // Process the TLS record header
        int type = Byte.toUnsignedInt(data.get());
        int ver = Short.toUnsignedInt(data.getShort());
        int recLen = Short.toUnsignedInt(data.getShort());

        // Sanity checks on the record header
        if (type != TLS_RECORD_HANDSHAKE) {
            throw new RuntimeException("Not a handshake, type = " + type);
        } else if (recLen > data.remaining()) {
            throw new RuntimeException("Buffer record is incomplete. " +
                    "Record length = " + recLen + ", remaining = " +
                    data.remaining());
        }

        // Extract the handshake message header
        int msgHdr = data.getInt();
        int msgType = msgHdr >>> 24;
        int msgLen = msgHdr & 0x00FFFFFF;

        // Sanity check on the message type
        if (msgType != TLS_HANDSHAKE_CLIHELLO) {
            throw new RuntimeException("Not a ClientHello, type = " + msgType);
        }

        // Skip protocol version (2b) and client random (32b)
        data.position(data.position() + 34);

        // Check session length (variable length vector) and then jump past it
        int sessLen = Byte.toUnsignedInt(data.get());
        if (sessLen != 0) {
            data.position(data.position() + sessLen);
        }

        // Extract the cipher suites and put them in a separate List
        List<Integer> transitCSs = new ArrayList<>();
        int csLen = Short.toUnsignedInt(data.getShort());
        for (int i=0; i < csLen; i+=2) {
            transitCSs.add(Short.toUnsignedInt(data.getShort()));
        }

        // Jump past compression algorithms
        int compLen = Byte.toUnsignedInt(data.get());
        if (compLen != 0) {
            data.position(data.position() + compLen);
        }

        // Go through the extensions and look for supported_versions, then add
        // each version to a list to be checked later
        List<Integer> transitPVs = new ArrayList<>();
        while (data.hasRemaining()) {
            int extType = Short.toUnsignedInt(data.getShort());
            int extLen = Short.toUnsignedInt(data.getShort());
            if (extType == HELLO_EXT_SUPP_VERS) {
                int supVerLen = Byte.toUnsignedInt(data.get());
                for (int i=0; i < supVerLen; i+=2) {
                    int pv = Short.toUnsignedInt(data.getShort());
                    transitPVs.add(pv);
                }
                break;
            }
        }

        List<Integer> expectedCS = CipherSuite.ids(clearDups(malfCS));
        List<Integer> expectedPV = clearDups(transitPVs);

        System.out.println("Cipher suite parameters transmitted in ClientHello: "
                + transitCSs.toString());
        System.out.println("Cipher suite expected output: " +
                expectedCS.toString());

        System.out.println("Protocol version parameters transmitted in "
                + "ClientHello: " + transitPVs.toString());
        System.out.println("Protocol versions expected output: "
                + expectedPV.toString());

        if (!transitCSs.equals(expectedCS)) {
            throw new RuntimeException("Expected and actual " +
                    " ciphersuites differ");
        }
        /*
        if (!transitPVs.equals(expectedPV)) {
            throw new RuntimeException("Expected and actual protcol version"
                    + " lists differ");
        }
        */
        // move ByteBuffer location back to the beginning point saved earlier
        data.reset();
    }

    /**
     * Eliminate duplicates from a generic List while preserving order.
     * Underlying changes to SSL implementation being tested here are done
     * with a non-destructive for loop implementation, so here we use a Set
     * in case there are any bugs inherent to the List/for-loop implemenation.
     *
     * @param src an unsanitized array
     *
     * @return a list with the same elements and order, but without duplicates
     */
    private static <T> List<T> clearDups(List<T> src) {
        Set<T> setVers = new LinkedHashSet<>();
        setVers.addAll(src);
        List<T> clean = new ArrayList<>(setVers);

        return clean;
    }
}
