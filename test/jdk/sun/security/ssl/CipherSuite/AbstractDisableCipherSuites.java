/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;

/**
 * This is not a test. Actual tests are implemented by concrete subclasses.
 * The abstract class AbstractDisableCipherSuites provides a base framework
 * for testing cipher suite disablement.
 */
public abstract class AbstractDisableCipherSuites {

    private static final byte RECTYPE_HS = 0x16;
    private static final byte HSMSG_CLIHELLO = 0x01;
    private static final ByteBuffer CLIOUTBUF =
            ByteBuffer.wrap("Client Side".getBytes());

    /**
     * Create an engine with the default set of cipher suites enabled and make
     * sure none of the disabled suites are present in the client hello.
     *
     * @param disabledSuiteIds the {@code List} of disabled cipher suite IDs
     *      to be checked for.
     *
     * @return true if the test passed (No disabled suites), false otherwise
     */
    protected boolean testDefaultCase(List<Integer> disabledSuiteIds)
            throws Exception {
        System.err.println("\nTest: Default SSLEngine suite set");
        SSLEngine ssle = makeEngine();
        if (getDebug()) {
            listCiphers("Suite set upon creation", ssle);
        }
        SSLEngineResult clientResult;
        ByteBuffer cTOs = makeClientBuf(ssle);
        clientResult = ssle.wrap(CLIOUTBUF, cTOs);
        if (getDebug()) {
            dumpResult("ClientHello: ", clientResult);
        }
        cTOs.flip();
        boolean foundSuite = areSuitesPresentCH(cTOs, disabledSuiteIds);
        if (foundSuite) {
            System.err.println("FAIL: Found disabled suites!");
            return false;
        } else {
            System.err.println("PASS: No disabled suites found.");
            return true;
        }
    }

    /**
     * Create an engine and set only disabled cipher suites.
     * The engine should not create the client hello message since the only
     * available suites to assert in the client hello are disabled ones.
     *
     * @param disabledSuiteNames an array of cipher suite names that
     *      should be disabled cipher suites.
     *
     * @return true if the engine throws SSLHandshakeException during client
     *      hello creation, false otherwise.
     */
    protected boolean testEngOnlyDisabled(String[] disabledSuiteNames)
            throws Exception {
        System.err.println(
                "\nTest: SSLEngine configured with only disabled suites");
        try {
            SSLEngine ssle = makeEngine();
            ssle.setEnabledCipherSuites(disabledSuiteNames);
            if (getDebug()) {
                listCiphers("Suite set upon creation", ssle);
            }
            SSLEngineResult clientResult;
            ByteBuffer cTOs = makeClientBuf(ssle);
            clientResult = ssle.wrap(CLIOUTBUF, cTOs);
            if (getDebug()) {
                dumpResult("ClientHello: ", clientResult);
            }
            cTOs.flip();
        } catch (SSLHandshakeException shse) {
            System.err.println("PASS: Caught expected exception: " + shse);
            return true;
        }
        System.err.println("FAIL: Expected SSLHandshakeException not thrown");
        return false;
    }

    /**
     * Create an engine and add some disabled suites to the default
     * set of cipher suites.  Make sure none of the disabled suites show up
     * in the client hello even though they were explicitly added.
     *
     * @param disabledNames an array of cipher suite names that
     *      should be disabled cipher suites.
     * @param disabledIds the {@code List} of disabled cipher suite IDs
     *      to be checked for.
     *
     * @return true if the test passed (No disabled suites), false otherwise
     */
    protected boolean testEngAddDisabled(String[] disabledNames,
                                         List<Integer> disabledIds) throws Exception {
        System.err.println("\nTest: SSLEngine with disabled suites added");
        SSLEngine ssle = makeEngine();

        // Add disabled suites to the existing engine's set of enabled suites
        String[] initialSuites = ssle.getEnabledCipherSuites();
        String[] plusDisSuites = Arrays.copyOf(initialSuites,
                                               initialSuites.length + disabledNames.length);
        System.arraycopy(disabledNames, 0, plusDisSuites,
                         initialSuites.length, disabledNames.length);
        ssle.setEnabledCipherSuites(plusDisSuites);

        if (getDebug()) {
            listCiphers("Suite set upon creation", ssle);
        }
        SSLEngineResult clientResult;
        ByteBuffer cTOs = makeClientBuf(ssle);
        clientResult = ssle.wrap(CLIOUTBUF, cTOs);
        if (getDebug()) {
            dumpResult("ClientHello: ", clientResult);
        }
        cTOs.flip();
        boolean foundDisabled = areSuitesPresentCH(cTOs, disabledIds);
        if (foundDisabled) {
            System.err.println("FAIL: Found disabled suites!");
            return false;
        } else {
            System.err.println("PASS: No disabled suites found.");
            return true;
        }
    }

    protected String getProtocol() {
        return "TLSv1.2";
    }

    private SSLEngine makeEngine() throws GeneralSecurityException {
        SSLContext ctx = SSLContext.getInstance(getProtocol());
        ctx.init(null, null, null);
        return ctx.createSSLEngine();
    }

    private static ByteBuffer makeClientBuf(SSLEngine ssle) {
        ssle.setUseClientMode(true);
        ssle.setNeedClientAuth(false);
        SSLSession sess = ssle.getSession();
        ByteBuffer cTOs = ByteBuffer.allocateDirect(sess.getPacketBufferSize());
        return cTOs;
    }

    private static void listCiphers(String prefix, SSLEngine ssle) {
        System.err.println(prefix + "\n---------------");
        String[] suites = ssle.getEnabledCipherSuites();
        for (String suite : suites) {
            System.err.println(suite);
        }
        System.err.println("---------------");
    }

    /**
     * Walk a TLS 1.2 or earlier ClientHello looking for any of the suites
     * in the suiteIdList.
     *
     * @param clientHello a ByteBuffer containing the ClientHello message as
     *      a complete TLS record.  The position of the buffer should be
     *      at the first byte of the TLS record header.
     * @param suiteIdList a List of integer values corresponding to
     *      TLS cipher suite identifiers.
     *
     * @return true if at least one of the suites in {@code suiteIdList}
     * is found in the ClientHello's cipher suite list
     *
     * @throws IOException if the data in the {@code clientHello}
     *      buffer is not a TLS handshake message or is not a client hello.
     */
    private boolean areSuitesPresentCH(ByteBuffer clientHello,
                                       List<Integer> suiteIdList) throws IOException {
        byte val;

        // Process the TLS Record
        val = clientHello.get();
        if (val != RECTYPE_HS) {
            throw new IOException(
                    "Not a handshake record, type = " + val);
        }

        // Just skip over the version and length
        clientHello.position(clientHello.position() + 4);

        // Check the handshake message type
        val = clientHello.get();
        if (val != HSMSG_CLIHELLO) {
            throw new IOException(
                    "Not a ClientHello handshake message, type = " + val);
        }

        // Skip over the length
        clientHello.position(clientHello.position() + 3);

        // Skip over the protocol version (2) and random (32);
        clientHello.position(clientHello.position() + 34);

        // Skip past the session ID (variable length <= 32)
        int len = Byte.toUnsignedInt(clientHello.get());
        if (len > 32) {
            throw new IOException("Session ID is too large, len = " + len);
        }
        clientHello.position(clientHello.position() + len);

        // Finally, we are at the cipher suites.  Walk the list and place them
        // into a List.
        int csLen = Short.toUnsignedInt(clientHello.getShort());
        if (csLen % 2 != 0) {
            throw new IOException("CipherSuite length is invalid, len = " +
                                          csLen);
        }
        int csCount = csLen / 2;
        List<Integer> csSuiteList = new ArrayList<>(csCount);
        log("Found following suite IDs in hello:");
        for (int i = 0; i < csCount; i++) {
            int curSuite = Short.toUnsignedInt(clientHello.getShort());
            log(String.format("Suite ID: 0x%04x", curSuite));
            csSuiteList.add(curSuite);
        }

        // Now check to see if any of the suites passed in match what is in
        // the suite list.
        boolean foundMatch = false;
        for (Integer cs : suiteIdList) {
            if (csSuiteList.contains(cs)) {
                System.err.format("Found match for suite ID 0x%04x\n", cs);
                foundMatch = true;
                break;
            }
        }

        // We don't care about the rest of the ClientHello message.
        // Rewind and return whether we found a match or not.
        clientHello.rewind();
        return foundMatch;
    }

    private static void dumpResult(String str, SSLEngineResult result) {
        System.err.println("The format of the SSLEngineResult is: \n" +
                                   "\t\"getStatus() / getHandshakeStatus()\" +\n" +
                                   "\t\"bytesConsumed() / bytesProduced()\"\n");
        HandshakeStatus hsStatus = result.getHandshakeStatus();
        System.err.println(str + result.getStatus() + "/" + hsStatus + ", " +
                                   result.bytesConsumed() + "/" + result.bytesProduced() + " bytes");
        if (hsStatus == HandshakeStatus.FINISHED) {
            System.err.println("\t...ready for application data");
        }
    }

    private void log(String str) {
        if (getDebug()) {
            System.err.println(str);
        }
    }

    protected boolean getDebug() {
        return false;
    }
}
