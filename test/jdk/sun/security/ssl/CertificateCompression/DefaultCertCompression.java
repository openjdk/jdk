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

import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/*
 * @test
 * @bug 8372526
 * @summary Add support for ZLIB TLS Certificate Compression.
 * @library /javax/net/ssl/templates
 *          /test/lib
 * @run main/othervm DefaultCertCompression
 * @run main/othervm DefaultCertCompression -Djdk.tls.enableCertificateCompression=false
 */

public class DefaultCertCompression extends SSLEngineTemplate {

    protected static final int CLI_HELLO_MSG = 1;
    protected static final int COMP_CERT_EXT = 27;
    // zlib(1), brotli(2), zstd(3)
    protected static final List<Integer> DEFAULT_COMP_ALGS = List.of(1);
    private final boolean certCompEnabled;

    protected DefaultCertCompression() throws Exception {
        certCompEnabled = Boolean.parseBoolean(System.getProperty(
                "jdk.tls.enableCertificateCompression", "true"));
        super();
    }

    public static void main(String[] args) throws Exception {
        new DefaultCertCompression().run();
    }

    protected void run() throws Exception {

        // Produce client_hello
        clientEngine.wrap(clientOut, cTOs);
        cTOs.flip();

        checkClientHello();
    }

    protected void checkClientHello() throws Exception {
        if (certCompEnabled) {
            assertTrue(DEFAULT_COMP_ALGS.equals(getCompAlgsCliHello(
                    extractHandshakeMsg(cTOs, CLI_HELLO_MSG, false))));
        } else {
            assertEquals(getCompAlgsCliHello(
                            extractHandshakeMsg(cTOs, CLI_HELLO_MSG, false)).size(), 0,
                    "compress_certificate extension present in ClientHello");
        }
    }

    /**
     * Parses the ClientHello message and extracts from it a list of
     * compression algorithm values. It is assumed that the provided
     * ByteBuffer has its position set at the first byte of the ClientHello
     * message body (AFTER the handshake header) and contains the entire
     * hello message.  Upon successful completion of this method the ByteBuffer
     * will have its position reset to the initial offset in the buffer.
     * If an exception is thrown the position at the time of the exception
     * will be preserved.
     *
     * @param data The ByteBuffer containing the ClientHello bytes.
     * @return A List of the compression algorithm values.
     */
    protected List<Integer> getCompAlgsCliHello(ByteBuffer data) {
        Objects.requireNonNull(data);
        data.mark();

        // Skip over the protocol version and client random
        data.position(data.position() + 34);

        // Jump past the session ID (if there is one)
        int sessLen = Byte.toUnsignedInt(data.get());
        if (sessLen != 0) {
            data.position(data.position() + sessLen);
        }

        // Jump past the cipher suites
        int csLen = Short.toUnsignedInt(data.getShort());
        if (csLen != 0) {
            data.position(data.position() + csLen);
        }

        // ...and the compression
        int compLen = Byte.toUnsignedInt(data.get());
        if (compLen != 0) {
            data.position(data.position() + compLen);
        }

        List<Integer> extSigAlgs = getCompAlgsFromExt(data);

        // We should be at the end of the ClientHello
        data.reset();
        return extSigAlgs;
    }

    /**
     * Gets compression algorithms from the given TLS extension.
     * The buffer should be positioned at the start of the extension.
     */
    protected List<Integer> getCompAlgsFromExt(ByteBuffer data) {

        List<Integer> extCompAlgs = new ArrayList<>();
        data.getShort(); // read length

        while (data.hasRemaining()) {
            int extType = Short.toUnsignedInt(data.getShort());
            int extLen = Short.toUnsignedInt(data.getShort());

            if (extType == COMP_CERT_EXT) {
                int sigSchemeLen = data.get();

                for (int ssOff = 0; ssOff < sigSchemeLen; ssOff += 2) {
                    Integer schemeName = Short.toUnsignedInt(data.getShort());
                    extCompAlgs.add(schemeName);
                }
            } else {
                // Not the extension we're looking for.  Skip past the
                // extension data
                data.position(data.position() + extLen);
            }
        }

        return extCompAlgs;
    }
}
