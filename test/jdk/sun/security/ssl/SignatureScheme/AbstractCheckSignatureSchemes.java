/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;

/**
 * This is not a test. Actual tests are implemented by concrete subclasses.
 * The abstract class AbstractCheckSignatureSchemes provides a base framework
 * for checking TLS signature schemes.
 */

public abstract class AbstractCheckSignatureSchemes extends SSLEngineTemplate {

    // Helper map to correlate integral SignatureScheme identifiers to
    // their IANA string name counterparts.
    protected static final Map<Integer, String> sigSchemeMap = Map.ofEntries(
            new SimpleImmutableEntry(0x0401, "rsa_pkcs1_sha256"),
            new SimpleImmutableEntry(0x0501, "rsa_pkcs1_sha384"),
            new SimpleImmutableEntry(0x0601, "rsa_pkcs1_sha512"),
            new SimpleImmutableEntry(0x0403, "ecdsa_secp256r1_sha256"),
            new SimpleImmutableEntry(0x0503, "ecdsa_secp384r1_sha384"),
            new SimpleImmutableEntry(0x0603, "ecdsa_secp521r1_sha512"),
            new SimpleImmutableEntry(0x0804, "rsa_pss_rsae_sha256"),
            new SimpleImmutableEntry(0x0805, "rsa_pss_rsae_sha384"),
            new SimpleImmutableEntry(0x0806, "rsa_pss_rsae_sha512"),
            new SimpleImmutableEntry(0x0807, "ed25519"),
            new SimpleImmutableEntry(0x0808, "ed448"),
            new SimpleImmutableEntry(0x0809, "rsa_pss_pss_sha256"),
            new SimpleImmutableEntry(0x080a, "rsa_pss_pss_sha384"),
            new SimpleImmutableEntry(0x080b, "rsa_pss_pss_sha512"),
            new SimpleImmutableEntry(0x0101, "rsa_md5"),
            new SimpleImmutableEntry(0x0201, "rsa_pkcs1_sha1"),
            new SimpleImmutableEntry(0x0202, "dsa_sha1"),
            new SimpleImmutableEntry(0x0203, "ecdsa_sha1"),
            new SimpleImmutableEntry(0x0301, "rsa_sha224"),
            new SimpleImmutableEntry(0x0302, "dsa_sha224"),
            new SimpleImmutableEntry(0x0303, "ecdsa_sha224"),
            new SimpleImmutableEntry(0x0402, "rsa_pkcs1_sha256"));

    // Other useful TLS definitions for these tests
    protected static final int TLS_HS_CLI_HELLO = 1;
    protected static final int TLS_HS_CERT_REQ = 13;
    protected static final int SIG_ALGS_EXT = 13;
    protected static final int SIG_ALGS_CERT_EXT = 50;

    protected AbstractCheckSignatureSchemes() throws Exception {
        super();
    }

    // Returns the protocol for test to use.
    abstract String getProtocol();

    protected boolean isDtls() {
        return getProtocol().startsWith("DTLS");
    }

    @Override
    protected SSLEngine configureClientEngine(SSLEngine clientEngine) {
        clientEngine.setUseClientMode(true);
        clientEngine.setEnabledProtocols(new String[]{getProtocol()});
        return clientEngine;
    }

    @Override
    protected SSLEngine configureServerEngine(SSLEngine serverEngine) {
        serverEngine.setUseClientMode(false);
        serverEngine.setWantClientAuth(true);
        serverEngine.setEnabledProtocols(new String[]{getProtocol()});
        return serverEngine;
    }

    @Override
    protected ContextParameters getServerContextParameters() {
        return new ContextParameters(getProtocol(), "PKIX", "NewSunX509");
    }

    @Override
    protected ContextParameters getClientContextParameters() {
        return new ContextParameters(getProtocol(), "PKIX", "NewSunX509");
    }

    protected ByteBuffer extractHandshakeMsg(ByteBuffer tlsRecord, int hsMsgId)
            throws SSLException {
        return extractHandshakeMsg(tlsRecord, hsMsgId, isDtls());
    }

    /**
     * Parses the ClientHello message and extracts from it a list of
     * SignatureScheme values in string form.  It is assumed that the provided
     * ByteBuffer has its position set at the first byte of the ClientHello
     * message body (AFTER the handshake header) and contains the entire
     * hello message.  Upon successful completion of this method the ByteBuffer
     * will have its position reset to the initial offset in the buffer.
     * If an exception is thrown the position at the time of the exception
     * will be preserved.
     *
     * @param data    The ByteBuffer containing the ClientHello bytes.
     * @param extCode Code of the TLS extension from which to extract
     *                signature schemes.
     * @return        A List of the signature schemes in string form.
     */
    protected List<String> getSigSchemesCliHello(
            ByteBuffer data, int extCode) {
        Objects.requireNonNull(data);
        data.mark();

        // Skip over the protocol version and client random
        data.position(data.position() + 34);

        // Jump past the session ID (if there is one)
        int sessLen = Byte.toUnsignedInt(data.get());
        if (sessLen != 0) {
            data.position(data.position() + sessLen);
        }

        // Skip DTLS-specific opaque cookie if any
        if (isDtls()) {
            int cookieLen = Byte.toUnsignedInt(data.get());
            if (cookieLen != 0) {
                data.position(data.position() + cookieLen);
            }
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

        // Now for the fun part.  Go through the extensions and look
        // for the two status request exts.
        List<String> extSigAlgs = getSigSchemesFromExt(data, extCode);

        // We should be at the end of the ClientHello
        data.reset();
        return extSigAlgs;
    }

    /**
     * Parses the CertificateRequest message and extracts from it a list of
     * SignatureScheme values in string form.  It is assumed that the provided
     * ByteBuffer has its position set at the first byte of the
     * CertificateRequest message body (AFTER the handshake header) and
     * contains the entire CR message.  Upon successful completion of this
     * method the ByteBuffer will have its position reset to the initial
     * offset in the buffer.
     * If an exception is thrown the position at the time of the exception
     * will be preserved.
     *
     * @param data The ByteBuffer containing the CertificateRequest bytes
     *
     * @return A List of the signature schemes in string form.  If no
     * signature_algorithms extension is present in the CertificateRequest
     * then an empty list will be returned.
     */
    protected List<String> getSigSchemesCertReq(ByteBuffer data) {
        Objects.requireNonNull(data);
        data.mark();

        // Jump past the certificate types
        int certTypeLen = Byte.toUnsignedInt(data.get());
        if (certTypeLen != 0) {
            data.position(data.position() + certTypeLen);
        }

        // Collect the SignatureAndHashAlgorithms
        List<String> extSigAlgs = new ArrayList();
        int sigSchemeLen = Short.toUnsignedInt(data.getShort());
        for (int ssOff = 0; ssOff < sigSchemeLen; ssOff += 2) {
            String schemeName = sigSchemeMap.get(
                    Short.toUnsignedInt(data.getShort()));
            if (schemeName != null) {
                extSigAlgs.add(schemeName);
            }
        }

        data.reset();
        return extSigAlgs;
    }

    /**
     * Gets signatures schemes from the given TLS extension.
     * The buffer should be positioned at the start of the extension.
     */
    protected List<String> getSigSchemesFromExt(
            ByteBuffer data, int extCode) {

        List<String> extSigAlgs = new ArrayList<>();
        data.getShort(); // read length

        while (data.hasRemaining()) {
            int extType = Short.toUnsignedInt(data.getShort());
            int extLen = Short.toUnsignedInt(data.getShort());
            if (extType == extCode) {
                // Start processing signature algorithms
                int sigSchemeLen = Short.toUnsignedInt(data.getShort());
                for (int ssOff = 0; ssOff < sigSchemeLen; ssOff += 2) {
                    String schemeName = sigSchemeMap.get(
                            Short.toUnsignedInt(data.getShort()));
                    if (schemeName != null) {
                        extSigAlgs.add(schemeName);
                    }
                }
            } else {
                // Not the extension we're looking for.  Skip past the
                // extension data
                data.position(data.position() + extLen);
            }
        }

        return extSigAlgs;
    }
}
