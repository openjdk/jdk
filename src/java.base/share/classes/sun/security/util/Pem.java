/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.security.Security;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/**
 * A utility class for PEM format encoding.
 */
public class Pem {

    /**
     * Public Key PEM header & footer
     */
    public static final byte[] PUBHEADER = "-----BEGIN PUBLIC KEY-----"
        .getBytes(StandardCharsets.UTF_8);
    public static final byte[] PUBFOOTER = "-----END PUBLIC KEY-----"
        .getBytes(StandardCharsets.UTF_8);

    /**
     * Private Key PEM header & footer
     */
    public static final byte[] PKCS8HEADER = "-----BEGIN PRIVATE KEY-----"
        .getBytes(StandardCharsets.UTF_8);
    public static final byte[] PKCS8FOOTER = "-----END PRIVATE KEY-----"
        .getBytes(StandardCharsets.UTF_8);

    /**
     * Encrypted Private Key PEM header & footer
     */
    public static final byte[] PKCS8ENCHEADER = "-----BEGIN ENCRYPTED PRIVATE KEY-----"
        .getBytes(StandardCharsets.UTF_8);
    public static final byte[] PKCS8ENCFOOTER = "-----END ENCRYPTED PRIVATE KEY-----"
        .getBytes(StandardCharsets.UTF_8);

    /**
     * Certificate PEM header & footer
     */
    public static final byte[] CERTHEADER = "-----BEGIN CERTIFICATE-----"
        .getBytes(StandardCharsets.UTF_8);
    public static final byte[] CERTFOOTER = "-----END CERTIFICATE-----"
        .getBytes(StandardCharsets.UTF_8);

    /**
     * CRL PEM header & footer
     */
    public static final byte[] CRLHEADER = "-----BEGIN CRL-----"
        .getBytes(StandardCharsets.UTF_8);
    public static final byte[] CRLFOOTER = "-----END CRL-----"
        .getBytes(StandardCharsets.UTF_8);

    /**
     * PKCS#1/slleay/OpenSSL RSA PEM header & footer
     */
    public static final byte[] PKCS1HEADER = "-----BEGIN RSA PRIVATE KEY-----"
        .getBytes(StandardCharsets.UTF_8);
    public static final byte[] PKCS1FOOTER = "-----END RSA PRIVATE KEY-----"
        .getBytes(StandardCharsets.UTF_8);

    public static final byte[] LINESEPARATOR = "\r\n"
        .getBytes(StandardCharsets.UTF_8);

    private static final Pem NULLPEM = new Pem(null, null, null);

    public enum KeyType {
        UNKNOWN, PRIVATE, PUBLIC, ENCRYPTED_PRIVATE, CERTIFICATE, CRL, PKCS1
    }

    public static final String DEFAULT_ALGO;

    static {
        DEFAULT_ALGO = Security.getProperty("jdk.epkcs8.defaultAlgorithm");
    }

    private byte[] header, footer;
    private byte[] data;

    private Pem(byte[] header, byte[] data, byte[] footer) {
        this.header = header;
        this.data = data;
        this.footer = footer;
    }
    /**
     * Decodes a PEM-encoded block.
     *
     * @param input the input string, according to RFC 1421, can only contain
     *              characters in the base-64 alphabet and whitespaces.
     * @return the decoded bytes
     * @throws java.io.IOException if input is invalid
     */
    public static byte[] decode(String input) {
        byte[] src = input.replaceAll("\\s+", "")
            .getBytes(StandardCharsets.UTF_8);
            return Base64.getDecoder().decode(src);
    }


    // Extract the OID from the PBE algorithm.  PBEKS2, which are all AES-based,
    // has uses one OID for all the standard algorithm, while PBEKS1 uses
    // individual ones.
    public static ObjectIdentifier getPBEID(String algorithm) {
        try {
            if (algorithm.contains("AES")) {
                return AlgorithmId.get("PBES2").getOID();
            } else {
                return AlgorithmId.get(algorithm).getOID();
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static Pem readPEM(InputStream is) throws IOException {
        return readPEM(is, false);
    }

    /**
     * Read the PEM text and return it in it's three components:  header,
     * base64, and footer
     * @param is The pem data
     * @param shortHeader if true, the hyphen length is 4 because the first
     *                    hyphen is assumed to have been read.
     * @return A new Pem object containing the three components
     * @throws IOException on read errors
     */
    public static Pem readPEM(InputStream is, boolean shortHeader) throws IOException{
        Objects.requireNonNull(is);

        int hyphen = (shortHeader ? 1 : 0);
        int endchar = 0;

        // Find starting hyphens
        do {
            switch (is.read()) {
                case '-' -> hyphen++;
                case -1 -> {
                    return null;
                }
                default -> hyphen = 0;
            }
        } while (hyphen != 5);

        StringBuilder sb = new StringBuilder(64);
        sb.append("-----");
        hyphen = 0;
        int c;

        // Get header definition until first hyphen
        do {
            switch (c = is.read()) {
                case '-' -> hyphen++;
                case -1 -> throw new IllegalArgumentException("Input ended prematurely");
                case '\n', '\r' -> throw new IllegalArgumentException("Incomplete header");
                default -> sb.append((char) c);
            }
        } while (hyphen == 0);

        // Verify header ending with 5 hyphens.
        do {
            switch (is.read()) {
                case '-' -> hyphen++;
                default -> throw new IllegalArgumentException("Incomplete header");
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
        switch (c = is.read()) {
            case 32 -> {
            }
            case '\r' -> {
                c = is.read();
                if (c == '\n') {
                    endchar = '\n';
                } else {
                    endchar = '\r';
                    sb.append((char) c);
                }
            }
            case '\n' -> endchar = '\n';
            default -> sb.append((char) c);
        }

        // Read data until we find the first footer hyphen.
        do {
            switch (c = is.read()) {
                case -1 -> throw new IllegalArgumentException("Incomplete header");
                case '-' -> hyphen++;
                case 9, '\n', '\r', 32 -> {
                } // skip for data reading
                default -> sb.append((char) c);
            }
        } while (hyphen == 0);

        String data = sb.toString();

        // Verify footer starts with 5 hyphens.
        do {
            switch (is.read()) {
                case '-' -> hyphen++;
                case -1 -> throw new IllegalArgumentException("Input ended prematurely");
                default -> throw new IllegalArgumentException("Incomplete footer");
            }
        } while (hyphen < 5);

        hyphen = 0;
        sb = new StringBuilder(64);
        sb.append("-----");

        // Look for Complete header by looking for the end of the hyphens
        do {
            switch (c = is.read()) {
                case '-' -> hyphen++;
                case -1 -> throw new IllegalArgumentException("Input ended prematurely");
                default -> sb.append((char) c);
            }
        } while (hyphen == 0);

        // Verify ending with 5 hyphens.
        do {
            switch (is.read()) {
                case '-' -> hyphen++;
                case -1 -> throw new IllegalArgumentException("Input ended prematurely");
                default -> throw new IllegalArgumentException("Incomplete footer");
            }
        } while (hyphen < 5);

        if (endchar != 0) {
            while ((c = is.read()) != endchar && c != -1 && c != '\r' &&
                c != 32) {
                throw new IllegalArgumentException("Invalid PEM format:  " +
                    "No end of line char found in footer:  0x" +
                    HexFormat.of().toHexDigits((byte) c));
            }
        }

        sb.append("-----");
        String footer = sb.toString();
        if (footer.length() < 14 || !footer.startsWith("-----END ") ||
            !footer.endsWith("-----")) {
            throw new IOException("Illegal footer: " + footer);
        }

        String headerType = header.substring(11, header.length() - 5);
        String footerType = footer.substring(9, footer.length() - 5);
        if (!headerType.equals(footerType)) {
            throw new IOException("Header and footer do not match: " +
                header + " " + footer);
        }

        return new Pem(header.getBytes(StandardCharsets.UTF_8),
            data.getBytes(StandardCharsets.UTF_8),
            footer.getBytes(StandardCharsets.UTF_8));
    }

    public byte[] getData() {
        return data;
    }

    public byte[] getHeader() {
        return header;
    }

    public byte[] getFooter() {
        return footer;
    }

    public void clean() {
        Arrays.fill(data, (byte)0);
    }
}
