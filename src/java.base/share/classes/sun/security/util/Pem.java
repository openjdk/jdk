/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.security.util;

import sun.security.x509.AlgorithmId;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.PEMRecord;
import java.security.Security;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A utility class for PEM format encoding.
 */
public class Pem {
    private static final char WS = 0x20;  // Whitespace

    // Default algorithm from jdk.epkcs8.defaultAlgorithm in java.security
    public static final String DEFAULT_ALGO;

    // Pattern matching for EKPI operations
    private static final Pattern pbePattern;

    // Lazy initialized PBES2 OID value
    private static ObjectIdentifier PBES2OID;

    // Lazy initialize singleton encoder.
    private static Base64.Encoder b64Encoder;

    static {
        DEFAULT_ALGO = Security.getProperty("jdk.epkcs8.defaultAlgorithm");
        pbePattern = Pattern.compile("^PBEWith.*And.*");
    }

    public static final String CERTIFICATE = "CERTIFICATE";
    public static final String X509_CERTIFICATE = "X509 CERTIFICATE";
    public static final String X509_CRL = "X509 CRL";
    public static final String PUBLIC_KEY = "PUBLIC KEY";
    public static final String RSA_PRIVATE_KEY = "RSA PRIVATE KEY";
    public static final String ENCRYPTED_PRIVATE_KEY = "ENCRYPTED PRIVATE KEY";
    public static final String PRIVATE_KEY = "PRIVATE KEY";

    /**
     * Decodes a PEM-encoded block.
     *
     * @param input the input string, according to RFC 1421, can only contain
     *              characters in the base-64 alphabet and whitespaces.
     * @return the decoded bytes
     */
    public static byte[] decode(String input) {
        byte[] src = input.replaceAll("\\s+", "").
            getBytes(StandardCharsets.ISO_8859_1);
            return Base64.getDecoder().decode(src);
    }

    /**
     * Return the OID for a given PBE algorithm.  PBES1 has an OID for each
     * algorithm, while PBES2 has one OID for everything that complies with
     * the formatting.  Therefore, if the algorithm is not PBES1, it will
     * return PBES2.  Cipher will determine if this is a valid PBE algorithm.
     * PBES2 specifies AES as the cipher algorithm, but any block cipher could
     * be supported.
     */
    public static ObjectIdentifier getPBEID(String algorithm) {

        // Verify pattern matches PBE Standard Name spec
        if (!pbePattern.matcher(algorithm).matches()) {
            throw new IllegalArgumentException("Invalid algorithm format.");
        }

        // Return the PBES1 OID if it matches
        try {
            return AlgorithmId.get(algorithm).getOID();
        } catch (NoSuchAlgorithmException e) {
            // fall-through
        }

        // Lazy initialize
        if (PBES2OID == null) {
            try {
                // Set to the hardcoded OID in KnownOID.java
                PBES2OID = AlgorithmId.get("PBES2").getOID();
            } catch (NoSuchAlgorithmException e) {
                // Should never fail.
                throw new IllegalArgumentException(e);
            }
        }
        return PBES2OID;
    }

    /**
     * Read the PEM text and return it in it's three components:  header,
     * base64, and footer.
     *
     * The method will leave the stream after reading the end of line of the
     * footer or end of file
     * @param is The pem data
     * @param shortHeader if true, the hyphen length is 4 because the first
     *                    hyphen is assumed to have been read.  This is needed
     *                    for the CertificateFactory X509 implementation.
     * @return A new Pem object containing the three components
     * @throws IOException on read errors
     * @throws EOFException when there is nothing to read
     */
    public static PEMRecord readPEM(InputStream is, boolean shortHeader)
        throws IOException {
        Objects.requireNonNull(is);

        int hyphen = (shortHeader ? 1 : 0);
        int eol = 0;

        ByteArrayOutputStream os = new ByteArrayOutputStream(6);
        // Find starting hyphens
        do {
            int d = is.read();
            switch (d) {
                case '-' -> hyphen++;
                case -1 -> {
                    if (os.size() == 0) {
                        throw new EOFException("No data available");
                    }
                    return new PEMRecord(null, null, os.toByteArray());
                }
                default -> hyphen = 0;
            }
            os.write(d);
        } while (hyphen != 5);

        StringBuilder sb = new StringBuilder(64);
        sb.append("-----");
        hyphen = 0;
        int c;

        // Get header definition until first hyphen
        do {
            switch (c = is.read()) {
                case '-' -> hyphen++;
                case -1 -> throw new IllegalArgumentException(
                    "Input ended prematurely");
                case '\n', '\r' -> throw new IllegalArgumentException(
                    "Incomplete header");
                default -> sb.append((char) c);
            }
        } while (hyphen == 0);

        // Verify header ending with 5 hyphens.
        do {
            switch (is.read()) {
                case '-' -> hyphen++;
                default ->
                    throw new IllegalArgumentException("Incomplete header");
            }
        } while (hyphen < 5);

        sb.append("-----");
        String header = sb.toString();
        if (header.length() < 16 || !header.startsWith("-----BEGIN ") ||
            !header.endsWith("-----")) {
            throw new IllegalArgumentException("Illegal header: " + header);
        }

        hyphen = 0;
        sb = new StringBuilder(1024);

        // Determine the line break using the char after the last hyphen
        switch (is.read()) {
            case WS -> {} // skip whitespace
            case '\r' -> {
                c = is.read();
                if (c == '\n') {
                    eol = '\n';
                } else {
                    eol = '\r';
                    sb.append((char) c);
                }
            }
            case '\n' -> eol = '\n';
            default ->
                throw new IllegalArgumentException("No EOL character found");
        }

        // Read data until we find the first footer hyphen.
        do {
            switch (c = is.read()) {
                case -1 ->
                    throw new IllegalArgumentException("Incomplete header");
                case '-' -> hyphen++;
                case WS, '\t', '\n', '\r' -> {} // skip whitespace, tab, etc
                default -> sb.append((char) c);
            }
        } while (hyphen == 0);

        String data = sb.toString();

        // Verify footer starts with 5 hyphens.
        do {
            switch (is.read()) {
                case '-' -> hyphen++;
                case -1 -> throw new IllegalArgumentException(
                    "Input ended prematurely");
                default -> throw new IllegalArgumentException(
                    "Incomplete footer");
            }
        } while (hyphen < 5);

        hyphen = 0;
        sb = new StringBuilder(64);
        sb.append("-----");

        // Look for Complete header by looking for the end of the hyphens
        do {
            switch (c = is.read()) {
                case '-' -> hyphen++;
                case -1 -> throw new IllegalArgumentException(
                    "Input ended prematurely");
                default -> sb.append((char) c);
            }
        } while (hyphen == 0);

        // Verify ending with 5 hyphens.
        do {
            switch (is.read()) {
                case '-' -> hyphen++;
                case -1 -> throw new IllegalArgumentException(
                    "Input ended prematurely");
                default -> throw new IllegalArgumentException(
                    "Incomplete footer");
            }
        } while (hyphen < 5);

        while ((c = is.read()) != eol && c != -1 && c != '\r' && c != WS) {
            throw new IllegalArgumentException("Invalid PEM format:  " +
                "No EOL char found in footer:  0x" +
                HexFormat.of().toHexDigits((byte) c));
        }

        sb.append("-----");
        String footer = sb.toString();
        if (footer.length() < 14 || !footer.startsWith("-----END ") ||
            !footer.endsWith("-----")) {
            throw new IllegalArgumentException("Illegal footer: " + footer);
        }

        // Verify the object type in the header and the footer are the same.
        String headerType = header.substring(11, header.length() - 5);
        String footerType = footer.substring(9, footer.length() - 5);
        if (!headerType.equals(footerType)) {
            throw new IllegalArgumentException("Header and footer do not " +
                "match: " + headerType + " " + footerType);
        }

        // If there was data before finding the 5 dashes of the PEM header,
        // backup 5 characters and save that data.
        byte[] preData = null;
        if (os.size() > 5) {
            preData = Arrays.copyOf(os.toByteArray(), os.size() - 5);
        }

        return new PEMRecord(headerType, data, preData);
    }

    public static PEMRecord readPEM(InputStream is) throws IOException {
        return readPEM(is, false);
    }

    /**
     * Construct a String-based encoding based off the type.  leadingData
     * is not used with this method.
     * @return the string
     */
    public static String pemEncoded(String type, byte[] data) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("-----BEGIN ").append(type).append("-----");
        sb.append(System.lineSeparator());
        if (b64Encoder == null) {
            b64Encoder = Base64.getMimeEncoder(64,
                System.lineSeparator().getBytes());
        }
        sb.append(b64Encoder.encodeToString(data));
        sb.append(System.lineSeparator());
        sb.append("-----END ").append(type).append("-----");
        sb.append(System.lineSeparator());
        return sb.toString();
    }

    public static String pemEncoded(PEMRecord pem) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("-----BEGIN ").append(pem.type()).append("-----");
        sb.append(System.lineSeparator());
        sb.append(pem.pem());
        sb.append(System.lineSeparator());
        sb.append("-----END ").append(pem.type()).append("-----");
        sb.append(System.lineSeparator());
        return sb.toString();
    }


}
