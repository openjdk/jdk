/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6956398 8301700
 * @summary make ephemeral DH key match the length of the certificate key
 * @library /javax/net/ssl/templates
 * @run main/othervm -Djdk.tls.client.enableSessionTicketExtension=false
 *      DHEKeySizing TLS_DHE_RSA_WITH_AES_128_CBC_SHA 1643 267 TLSv1
 * @run main/othervm -Djsse.enableFFDHE=false
 *      -Djdk.tls.client.enableSessionTicketExtension=false
 *      DHEKeySizing SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA 1259 75 TLSv1.1
 * @run main/othervm -Djsse.enableFFDHE=false
 *      -Djdk.tls.ephemeralDHKeySize=matched
 *      -Djdk.tls.client.enableSessionTicketExtension=false
 *      DHEKeySizing SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA 1259 75 TLSv1.2
 * @run main/othervm -Djsse.enableFFDHE=false
 *      -Djdk.tls.ephemeralDHKeySize=legacy
 *      -Djdk.tls.client.enableSessionTicketExtension=false
 *      DHEKeySizing SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA 1259 75 TLSv1
 * @run main/othervm -Djsse.enableFFDHE=false
 *      -Djdk.tls.ephemeralDHKeySize=1024
 *      -Djdk.tls.client.enableSessionTicketExtension=false
 *      DHEKeySizing SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA 1259 75 TLSv1.1
 * @run main/othervm -Djsse.enableFFDHE=false
 *      -Djdk.tls.client.enableSessionTicketExtension=false
 *      DHEKeySizing SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA 233 75 TLSv1.2
 * @run main/othervm -Djsse.enableFFDHE=false
 *      -Djdk.tls.client.enableSessionTicketExtension=false
 *      DHEKeySizing TLS_DHE_RSA_WITH_AES_128_CBC_SHA 1643 267 TLSv1
 * @run main/othervm -Djsse.enableFFDHE=false
 *      -Djdk.tls.ephemeralDHKeySize=legacy
 *      -Djdk.tls.client.enableSessionTicketExtension=false
 *      DHEKeySizing TLS_DHE_RSA_WITH_AES_128_CBC_SHA 1323 107 TLSv1.1
 * @run main/othervm -Djsse.enableFFDHE=false
 *      -Djdk.tls.ephemeralDHKeySize=matched
 *      -Djdk.tls.client.enableSessionTicketExtension=false
 *      DHEKeySizing TLS_DHE_RSA_WITH_AES_128_CBC_SHA 1645 267 TLSv1.2
 * @run main/othervm -Djsse.enableFFDHE=false
 *      -Djdk.tls.ephemeralDHKeySize=1024
 *      -Djdk.tls.client.enableSessionTicketExtension=false
 *      DHEKeySizing TLS_DHE_RSA_WITH_AES_128_CBC_SHA 1387 139 TLSv1
 * @run main/othervm -Djsse.enableFFDHE=false
 *      -Djdk.tls.client.enableSessionTicketExtension=false
 *      DHEKeySizing SSL_DH_anon_WITH_RC4_128_MD5 617 267 TLSv1.1
 * @run main/othervm -Djsse.enableFFDHE=false
 *      -Djdk.tls.client.enableSessionTicketExtension=false
 *      -Djdk.tls.ephemeralDHKeySize=legacy
 *      DHEKeySizing SSL_DH_anon_WITH_RC4_128_MD5 297 107 TLSv1.2
 * @run main/othervm -Djsse.enableFFDHE=false
 *      -Djdk.tls.client.enableSessionTicketExtension=false
 *      -Djdk.tls.ephemeralDHKeySize=matched
 *      DHEKeySizing SSL_DH_anon_WITH_RC4_128_MD5 617 267 TLSv1
 * @run main/othervm -Djsse.enableFFDHE=false
 *      -Djdk.tls.client.enableSessionTicketExtension=false
 *      -Djdk.tls.ephemeralDHKeySize=1024
 *      DHEKeySizing SSL_DH_anon_WITH_RC4_128_MD5 361 139 TLSv1.1
 */

/*
 * This is a simple hack to test key sizes of Diffie-Hellman key exchanging
 * during SSL/TLS handshaking.
 *
 * The record length of DH ServerKeyExchange and ClientKeyExchange.
 * ServerKeyExchange message are wrapped in ServerHello series messages, which
 * contains ServerHello, Certificate and ServerKeyExchange message.
 *
 *    struct {
 *        opaque dh_p<1..2^16-1>;
 *        opaque dh_g<1..2^16-1>;
 *        opaque dh_Ys<1..2^16-1>;
 *    } ServerDHParams;     // Ephemeral DH parameters
 *
 *    struct {
 *        select (PublicValueEncoding) {
 *            case implicit: struct { };
 *            case explicit: opaque dh_Yc<1..2^16-1>;
 *        } dh_public;
 *    } ClientDiffieHellmanPublic;
 *
 * From the above structures, it is clear that if the DH key size increases 128
 * bits (16 bytes), the ServerHello series messages increases 48 bytes
 * (becuase dh_p, dh_g and dh_Ys each increase 16 bytes) and ClientKeyExchange
 * increases 16 bytes (because of the size increasing of dh_Yc).
 *
 * Here is a summary of the record length in the test case.
 *
 *            |  ServerHello Series  |  ClientKeyExchange | ServerHello Anon
 *   512-bit  |          1259 bytes  |           75 bytes |        233 bytes
 *   768-bit  |          1323 bytes  |          107 bytes |        297 bytes
 *  1024-bit  |          1387 bytes  |          139 bytes |        361 bytes
 *  2048-bit  |          1643 bytes  |          267 bytes |        617 bytes
 */

import javax.net.ssl.*;
import javax.net.ssl.SSLEngineResult.*;
import java.nio.*;
import java.security.Security;

public class DHEKeySizing extends SSLEngineTemplate {

    private final static boolean debug = true;

    // key length bias because of the stripping of leading zero bytes of
    // negotiated DH keys.
    //
    // This is an effort to minimize intermittent failures when we cannot
    // estimate what's the exact number of leading zero bytes of
    // negotiated DH keys.
    private final static int KEY_LEN_BIAS = 6;

    private static String protocol;

    private void checkResult(ByteBuffer bbIn, ByteBuffer bbOut,
            SSLEngineResult result,
            Status status, HandshakeStatus hsStatus,
            int consumed, int produced)
            throws Exception {

        if ((status != null) && (result.getStatus() != status)) {
            throw new Exception("Unexpected Status: need = " + status +
                " got = " + result.getStatus());
        }

        if ((hsStatus != null) && (result.getHandshakeStatus() != hsStatus)) {
            throw new Exception("Unexpected hsStatus: need = " + hsStatus +
                " got = " + result.getHandshakeStatus());
        }

        if ((consumed != -1) && (consumed != result.bytesConsumed())) {
            throw new Exception("Unexpected consumed: need = " + consumed +
                " got = " + result.bytesConsumed());
        }

        if ((produced != -1) && (produced != result.bytesProduced())) {
            throw new Exception("Unexpected produced: need = " + produced +
                " got = " + result.bytesProduced());
        }

        if ((consumed != -1) && (bbIn.position() != result.bytesConsumed())) {
            throw new Exception("Consumed " + bbIn.position() +
                " != " + consumed);
        }

        if ((produced != -1) && (bbOut.position() != result.bytesProduced())) {
            throw new Exception("produced " + bbOut.position() +
                " != " + produced);
        }
    }

    private void test(String cipherSuite, int lenServerKeyEx,
            int lenClientKeyEx) throws Exception {

        SSLEngineResult result1;        // clientEngine's results from last operation
        SSLEngineResult result2;        // serverEngine's results from last operation

        String[] suites = new String [] {cipherSuite};

        clientEngine.setEnabledCipherSuites(suites);
        serverEngine.setEnabledCipherSuites(suites);

        log("======================================");
        log("===================");
        log("client hello");
        result1 = clientEngine.wrap(clientOut, cTOs);
        checkResult(clientOut, cTOs, result1,
            Status.OK, HandshakeStatus.NEED_UNWRAP, 0, -1);
        cTOs.flip();

        result2 = serverEngine.unwrap(cTOs, serverIn);
        checkResult(cTOs, serverIn, result2,
            Status.OK, HandshakeStatus.NEED_TASK, result1.bytesProduced(), 0);
        runDelegatedTasks(serverEngine);
        cTOs.compact();

        log("===================");
        log("ServerHello");
        result2 = serverEngine.wrap(serverOut, sTOc);
        checkResult(serverOut, sTOc, result2,
            Status.OK, HandshakeStatus.NEED_UNWRAP, 0, -1);
        sTOc.flip();

        log("Message length of ServerHello series: " + sTOc.remaining());
        if (sTOc.remaining() < (lenServerKeyEx - KEY_LEN_BIAS) ||
                sTOc.remaining() > lenServerKeyEx) {
            throw new Exception(
                "Expected to generate ServerHello series messages of " +
                lenServerKeyEx + " bytes, but not " + sTOc.remaining());
        }

        result1 = clientEngine.unwrap(sTOc, clientIn);
        checkResult(sTOc, clientIn, result1,
            Status.OK, HandshakeStatus.NEED_TASK, result2.bytesProduced(), 0);
        runDelegatedTasks(clientEngine);
        sTOc.compact();

        log("===================");
        log("Key Exchange");
        result1 = clientEngine.wrap(clientOut, cTOs);
        checkResult(clientOut, cTOs, result1,
            Status.OK, HandshakeStatus.NEED_WRAP, 0, -1);
        cTOs.flip();

        log("Message length of ClientKeyExchange: " + cTOs.remaining());
        if (cTOs.remaining() < (lenClientKeyEx - KEY_LEN_BIAS) ||
                cTOs.remaining() > lenClientKeyEx) {
            throw new Exception(
                "Expected to generate ClientKeyExchange message of " +
                lenClientKeyEx + " bytes, but not " + cTOs.remaining());
        }
        result2 = serverEngine.unwrap(cTOs, serverIn);
        checkResult(cTOs, serverIn, result2,
            Status.OK, HandshakeStatus.NEED_TASK, result1.bytesProduced(), 0);
        runDelegatedTasks(serverEngine);
        cTOs.compact();

        log("===================");
        log("Client CCS");
        result1 = clientEngine.wrap(clientOut, cTOs);
        checkResult(clientOut, cTOs, result1,
            Status.OK, HandshakeStatus.NEED_WRAP, 0, -1);
        cTOs.flip();

        result2 = serverEngine.unwrap(cTOs, serverIn);
        checkResult(cTOs, serverIn, result2,
            Status.OK, HandshakeStatus.NEED_UNWRAP,
            result1.bytesProduced(), 0);
        cTOs.compact();

        log("===================");
        log("Client Finished");
        result1 = clientEngine.wrap(clientOut, cTOs);
        checkResult(clientOut, cTOs, result1,
            Status.OK, HandshakeStatus.NEED_UNWRAP, 0, -1);
        cTOs.flip();

        result2 = serverEngine.unwrap(cTOs, serverIn);
        checkResult(cTOs, serverIn, result2,
            Status.OK, HandshakeStatus.NEED_WRAP,
            result1.bytesProduced(), 0);
        cTOs.compact();

        log("===================");
        log("Server CCS");
        result2 = serverEngine.wrap(serverOut, sTOc);
        checkResult(serverOut, sTOc, result2,
            Status.OK, HandshakeStatus.NEED_WRAP, 0, -1);
        sTOc.flip();

        result1 = clientEngine.unwrap(sTOc, clientIn);
        checkResult(sTOc, clientIn, result1,
            Status.OK, HandshakeStatus.NEED_UNWRAP, result2.bytesProduced(), 0);
        sTOc.compact();

        log("===================");
        log("Server Finished");
        result2 = serverEngine.wrap(serverOut, sTOc);
        checkResult(serverOut, sTOc, result2,
            Status.OK, HandshakeStatus.FINISHED, 0, -1);
        sTOc.flip();

        result1 = clientEngine.unwrap(sTOc, clientIn);
        checkResult(sTOc, clientIn, result1,
            Status.OK, HandshakeStatus.FINISHED, result2.bytesProduced(), 0);
        sTOc.compact();

        log("===================");
        log("Check Session/Ciphers");
        String cs = clientEngine.getSession().getCipherSuite();
        if (!cs.equals(suites[0])) {
            throw new Exception("suites not equal: " + cs + "/" + suites[0]);
        }

        cs = serverEngine.getSession().getCipherSuite();
        if (!cs.equals(suites[0])) {
            throw new Exception("suites not equal: " + cs + "/" + suites[0]);
        }

        log("===================");
        log("Done with SSL/TLS handshaking");
    }

    public static void main(String args[]) throws Exception {
        // reset security properties to make sure that the algorithms
        // and keys used in this test are not disabled.
        Security.setProperty("jdk.tls.disabledAlgorithms", "");
        Security.setProperty("jdk.certpath.disabledAlgorithms", "");

        if (args.length != 4) {
            System.out.println(
                "Usage: java DHEKeySizing cipher-suite " +
                "size-of-server-hello-record\n" +
                "    size-of-client-key-exchange protocol");
            throw new Exception("Incorrect usage!");
        }

        protocol = args[3];

        (new DHEKeySizing()).test(args[0],
                Integer.parseInt(args[1]),
                Integer.parseInt(args[2]));
        System.out.println("Test Passed.");
    }

    /*
     * **********************************************************
     * Majority of the test case is above, below is just setup stuff
     * **********************************************************
     */

    public DHEKeySizing() throws Exception {
        super();
    }

    @Override
    protected SSLEngine configureServerEngine(SSLEngine engine) {
        engine.setNeedClientAuth(false);
        engine.setUseClientMode(false);
        return engine;
    }

    @Override
    protected SSLContext createServerSSLContext() throws Exception {
        return createSSLContext(null, new Cert[]{Cert.CA_SHA1_RSA_2048},
                getServerContextParameters());
    }

    @Override
    protected SSLContext createClientSSLContext() throws Exception {
        return createSSLContext(new Cert[]{Cert.CA_SHA1_RSA_2048}, null,
                getClientContextParameters());
    }

    @Override
    protected ContextParameters getClientContextParameters() {
        return new ContextParameters(protocol, "PKIX", "NewSunX509");
    }

    @Override
    protected ContextParameters getServerContextParameters() {
        return new ContextParameters(protocol, "PKIX", "NewSunX509");
    }

    private static void log(String str) {
        if (debug) {
            System.out.println(str);
        }
    }
}
