/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4495742
 * @summary Add non-blocking SSL/TLS functionality, usable with any
 *      I/O abstraction
 * This is intended to test many of the basic API calls to the SSLEngine
 * interface.  This doesn't really exercise much of the SSL code.
 *
 * @library /test/lib
 * @author Brad Wetmore
 * @run main/othervm Basics
 */

import java.security.*;
import java.io.*;
import java.nio.*;
import java.util.Arrays;
import javax.net.ssl.*;
import javax.net.ssl.SSLEngineResult.*;

import jdk.test.lib.security.SecurityUtils;

public class Basics {

    private static final String PATH_TO_STORES = "../etc";
    private static final String KEY_STORE_FILE = "keystore";
    private static final String TRUSTSTORE_FILE = "truststore";

    private static final String KEYSTORE_PATH =
            System.getProperty("test.src", "./") + "/" + PATH_TO_STORES +
                "/" + KEY_STORE_FILE;
    private static final String TRUSTSTORE_PATH =
            System.getProperty("test.src", "./") + "/" + PATH_TO_STORES +
                "/" + TRUSTSTORE_FILE;

    public static void main(String[] args) throws Exception {
        SecurityUtils.removeFromDisabledTlsAlgs("TLSv1.1");

        runTest("TLSv1.3", "TLS_AES_256_GCM_SHA384");
        runTest("TLSv1.2", "TLS_RSA_WITH_AES_256_GCM_SHA384");
        runTest("TLSv1.1", "TLS_DHE_DSS_WITH_AES_128_CBC_SHA");
    }

    private static void runTest(String protocol, String cipherSuite) throws Exception {
        System.out.printf("Testing %s with %s%n", protocol, cipherSuite);

        KeyStore ks = KeyStore.getInstance("JKS");
        KeyStore ts = KeyStore.getInstance("JKS");
        char[] passphrase = "passphrase".toCharArray();

        ks.load(new FileInputStream(KEYSTORE_PATH), passphrase);
        ts.load(new FileInputStream(TRUSTSTORE_PATH), passphrase);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);

        SSLContext sslCtx = SSLContext.getInstance("TLS");

        sslCtx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        SSLEngine ssle = sslCtx.createSSLEngine();

        System.out.println(ssle);

        String [] suites = ssle.getSupportedCipherSuites();
        // sanity check that the ciphersuite we want to use is still supported
        Arrays.stream(suites)
                .filter(s -> s.equals(cipherSuite))
                .findFirst()
                .orElseThrow((() ->
                        new RuntimeException(cipherSuite +
                                " is not a supported ciphersuite.")));

        printStrings("Supported Ciphersuites", suites);
        printStrings("Enabled Ciphersuites", ssle.getEnabledCipherSuites());
        ssle.setEnabledCipherSuites(new String [] { cipherSuite });
        printStrings("Set Ciphersuites", ssle.getEnabledCipherSuites());

        suites = ssle.getEnabledCipherSuites();
        if ((ssle.getEnabledCipherSuites().length != 1) ||
                !(suites[0].equals(cipherSuite))) {
            throw new RuntimeException("set ciphers not what was expected");
        }

        System.out.println();

        String [] protocols = ssle.getSupportedProtocols();
        // sanity check that the protocol we want is still supported
        Arrays.stream(protocols)
                .filter(p -> p.equals(protocol))
                .findFirst()
                .orElseThrow(() ->
                        new RuntimeException(protocol +
                                " is not a supported TLS protocol."));

        printStrings("Supported Protocols", protocols);
        printStrings("Enabled Protocols", ssle.getEnabledProtocols());
        ssle.setEnabledProtocols(new String[]{ protocol });
        printStrings("Set Protocols", ssle.getEnabledProtocols());

        protocols = ssle.getEnabledProtocols();
        if ((ssle.getEnabledProtocols().length != 1) ||
                !(protocols[0].equals(protocol))) {
            throw new RuntimeException("set protocols not what was expected");
        }

        System.out.println("Checking get/setUseClientMode");

        ssle.setUseClientMode(true);
        if (!ssle.getUseClientMode()) {
            throw new RuntimeException("set/getUseClientMode false");
        }

        ssle.setUseClientMode(false);
        if (ssle.getUseClientMode()) {
            throw new RuntimeException("set/getUseClientMode true");
        }


        System.out.println("Checking get/setClientAuth");

        ssle.setNeedClientAuth(false);
        if (ssle.getNeedClientAuth()) {
            throw new RuntimeException("set/getNeedClientAuth true");
        }

        ssle.setNeedClientAuth(true);
        if (!ssle.getNeedClientAuth()) {
            throw new RuntimeException("set/getNeedClientAuth false");
        }

        ssle.setWantClientAuth(true);

        if (ssle.getNeedClientAuth()) {
            throw new RuntimeException("set/getWantClientAuth need = true");
        }

        if (!ssle.getWantClientAuth()) {
            throw new RuntimeException("set/getNeedClientAuth false");
        }

        ssle.setWantClientAuth(false);
        if (ssle.getWantClientAuth()) {
            throw new RuntimeException("set/getNeedClientAuth true");
        }

        /*
         * Reset back to client mode
         */
        ssle.setUseClientMode(true);

        System.out.println("checking session creation");

        ssle.setEnableSessionCreation(false);
        if (ssle.getEnableSessionCreation()) {
            throw new RuntimeException("set/getSessionCreation true");
        }

        ssle.setEnableSessionCreation(true);
        if (!ssle.getEnableSessionCreation()) {
            throw new RuntimeException("set/getSessionCreation false");
        }

        /* Checking for overflow wrap/unwrap() */
        ByteBuffer smallBB = ByteBuffer.allocate(10);

        if (ssle.wrap(smallBB, smallBB).getStatus() !=
                Status.BUFFER_OVERFLOW) {
            throw new RuntimeException("wrap should have overflowed");
        }

        // For unwrap(), the BUFFER_OVERFLOW will not be generated
        // until received SSL/TLS application data.
        // Test test/jdk/javax/net/ssl/SSLEngine/LargePacket.java will check
        // BUFFER_OVERFLOW/UNDERFLOW for both wrap() and unwrap().

        SSLSession ssls = ssle.getSession();

        ByteBuffer appBB =
            ByteBuffer.allocate(ssls.getApplicationBufferSize());
        ByteBuffer netBB =
            ByteBuffer.allocate(ssls.getPacketBufferSize());
        appBB.position(10);

        /*
         * start handshake, drain buffer
         */
        if (ssle.wrap(appBB, netBB).getHandshakeStatus() !=
                HandshakeStatus.NEED_UNWRAP) {
            throw new RuntimeException("initial client hello needs unwrap");
        }

        /*
         * After the first call to wrap(), the handshake status is
         * NEED_UNWRAP and we need to receive data before doing anymore
         * handshaking.
         */
        SSLEngineResult result = ssle.wrap(appBB, netBB);
        if (result.getStatus() != Status.OK
            && result.bytesConsumed() != 0 && result.bytesProduced() != 0) {
            throw new RuntimeException("wrap should have returned without doing anything");
        }

        ByteBuffer ro = appBB.asReadOnlyBuffer();

        System.out.println("checking for wrap/unwrap on RO Buffers");
        try {
            ssle.wrap(netBB, ro);
            throw new Exception("wrap wasn't ReadOnlyBufferException");
        } catch (ReadOnlyBufferException e) {
            System.out.println("Caught the ReadOnlyBuffer: " + e);
        }

        try {
            ssle.unwrap(netBB, ro);
            throw new RuntimeException("unwrap wasn't ReadOnlyBufferException");
        } catch (ReadOnlyBufferException e) {
            System.out.println("Caught the ReadOnlyBuffer: " + e);
        }

        appBB.position(0);
        System.out.println("Check various UNDERFLOW conditions");

        SSLEngineResult sslER;

        if ((sslER =
                ssle.unwrap(ByteBuffer.wrap(smallSSLHeader),
                appBB)).getStatus() !=
                Status.BUFFER_UNDERFLOW) {
            System.out.println(sslER);
            throw new RuntimeException("unwrap should underflow");
        }

        if ((sslER =
                ssle.unwrap(ByteBuffer.wrap(incompleteSSLHeader),
                appBB)).getStatus() !=
                Status.BUFFER_UNDERFLOW) {
            System.out.println(sslER);
            throw new RuntimeException("unwrap should underflow");
        }

        if ((sslER =
                ssle.unwrap(ByteBuffer.wrap(smallv2Header),
                appBB)).getStatus() !=
                Status.BUFFER_UNDERFLOW) {
            System.out.println(sslER);
            throw new RuntimeException("unwrap should underflow");
        }

        // junk inbound message
        try {
            /*
             * Exceptions are thrown when:
             *    - the length field is correct but the data can't be decoded.
             *    - the length field is larger than max allowed.
             */
            ssle.unwrap(ByteBuffer.wrap(gobblydegook), appBB);
            throw new RuntimeException("Expected SSLProtocolException was not thrown "
                    + "for bad input");
        } catch (SSLProtocolException e) {
            System.out.println("caught the SSLProtocolException for bad decoding: "
                    + e);
        }

        System.out.println("Test PASSED");

    }

    static byte [] smallSSLHeader = new byte [] {
        (byte) 0x16, (byte) 0x03, (byte) 0x01,
        (byte) 0x05 };

    static byte [] incompleteSSLHeader = new byte [] {
        (byte) 0x16, (byte) 0x03, (byte) 0x01,
        (byte) 0x00, (byte) 0x5,  // 5 bytes
        (byte) 0x16, (byte) 0x03, (byte) 0x01, (byte) 0x00 };

    static byte [] smallv2Header = new byte [] {
        (byte) 0x80, (byte) 0x03, (byte) 0x01,
        (byte) 0x00 };

    static byte [] gobblydegook = new byte [] {
        // bad data but correct record length to cause decryption error
        (byte) 0x48, (byte) 0x45, (byte) 0x4C, (byte) 0x00, (byte) 0x04,
        (byte) 0x48, (byte) 0x45, (byte) 0x4C, (byte) 0x4C };

    static void printStrings(String label, String [] strs) {
        System.out.println(label);

        for (int i = 0; i < strs.length; i++) {
            System.out.println("    " + strs[i]);
        }
    }
}
