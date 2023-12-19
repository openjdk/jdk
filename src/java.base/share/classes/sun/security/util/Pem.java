/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.security.Security;
import java.util.Base64;
import java.util.Objects;

/**
 * A utility class for PEM format encoding.
 */
public class Pem {

    /**
     * Public Key PEM header & footer
     */
    public static final String PUBHEADER = "-----BEGIN PUBLIC KEY-----";
    public static final String PUBFOOTER = "-----END PUBLIC KEY-----";

    /**
     * Private Key PEM header & footer
     */
    public static final String PKCS8HEADER = "-----BEGIN PRIVATE KEY-----";
    public static final String PKCS8FOOTER = "-----END PRIVATE KEY-----";

    /**
     * Encrypted Private Key PEM header & footer
     */
    public static final String PKCS8ENCHEADER = "-----BEGIN ENCRYPTED PRIVATE KEY-----";
    public static final String PKCS8ENCFOOTER = "-----END ENCRYPTED PRIVATE KEY-----";

    /**
     * Certificate PEM header & footer
     */
    public static final String CERTHEADER = "-----BEGIN CERTIFICATE-----";
    public static final String CERTFOOTER = "-----END CERTIFICATE-----";

    /**
     * CRL PEM header & footer
     */
    public static final String CRLHEADER = "-----BEGIN CRL-----";
    public static final String CRLFOOTER = "-----END CRL-----";

    /**
     * OpenSSL PKCS1 RSA Header
     */
    public static final String PKCS1HEADER = "-----BEGIN RSA PRIVATE KEY-----";
    public static final String PKCS1FOOTER = "-----END RSA PRIVATE KEY-----";

    public static final String LINESEPARATOR = "\r\n";

    private static final String STARTHEADER = "-----BEGIN ";
    private static final String ENDFOOTER = "-----END ";


    public enum KeyType {
        UNKNOWN, PRIVATE, PUBLIC, ENCRYPTED_PRIVATE, CERTIFICATE, CRL
    }

    public static final String DEFAULT_ALGO;

    static {
        DEFAULT_ALGO = Security.getProperty("jdk.epkcs8.defaultAlgorithm");
    }

    private String header, footer, data;

    private Pem(String header, String data, String footer) {
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
    public static byte[] decode(String input) throws IOException {
        byte[] src = input.replaceAll("\\s+", "")
            .getBytes(StandardCharsets.ISO_8859_1);
        try {
            return Base64.getDecoder().decode(src);
        } catch (IllegalArgumentException e) {
            throw new IOException(e);
        }
    }


    // Sorta hack to get the right OID for PBEKS2
    public static ObjectIdentifier getPBEID(String algorithm) throws IOException {
        try {
            if (algorithm.contains("AES")) {
                return AlgorithmId.get("PBES2").getOID();
            } else {
                return AlgorithmId.get(algorithm).getOID();
            }
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public static Pem readPEM(InputStream is) throws IOException {
        return readPEM(new InputStreamReader(is));
    }

    public static Pem readPEM(Reader reader) throws IOException {
        Objects.requireNonNull(reader);

        BufferedReader br = new BufferedReader(reader);
        int hyphen = 0;

        // Find starting hyphens
        do {
            switch (br.read()) {
                case '-' -> hyphen++;
                case -1 ->  throw new IOException("No PEM data found in input");
                default -> hyphen = 0;
            }
        } while (hyphen != 5);

        StringBuilder sb = new StringBuilder(64);
        sb.append("-----");
        hyphen = 0;
        int c;

        // Get header definition until first hyphen
        do {
            switch(c = br.read()) {
                case '-' -> hyphen++;
                case -1 -> throw new IOException("Input ended prematurely");
                case '\n', '\r' -> throw new IOException("Incomplete header");
                default -> sb.append((char)c);
            }
        } while (hyphen == 0);

        // Verify ending 5 hyphens of the header.
        do {
            switch (br.read()) {
                case '-' -> hyphen++;
                default -> throw new IOException("Incomplete header");
            }
        } while (hyphen < 5);

        sb.append("-----");
        String header = sb.toString();
        if (header.length() < 16 || !header.startsWith("-----BEGIN ") ||
            !header.endsWith("-----")) {
            throw new IOException("Illegal header: " + header);
        }

        hyphen = 0;
        sb = new StringBuilder(1024);

        // Read data until we find the hyphens for END
        do {
            switch (c = br.read()) {
                case -1 -> throw new IOException("Incomplete header");
                case '-' -> hyphen++;
                default -> sb.append((char)c);
            }
        } while (hyphen == 0);

        String data = sb.toString();

        // Verify beginning 5 hyphens of the footer.
        do {
            switch (br.read()) {
                case '-' -> hyphen++;
                case -1 -> throw new IOException("Input ended prematurely");
                default -> throw new IOException("Incomplete footer");
            }
        } while (hyphen < 5);

        hyphen = 0;
        sb = new StringBuilder(64);
        sb.append("-----");

        // Complete header by looking for the end of the hyphens
        do {
            switch(c = br.read()) {
                case '-' -> hyphen++;
                case -1 -> throw new IOException("Input ended prematurely");
                default -> sb.append((char)c);
            }
        } while (hyphen == 0);

        // Verify ending 5 hyphens of the header.
        do {
            switch (br.read()) {
                case '-' -> hyphen++;
                case -1 -> throw new IOException("Input ended prematurely");
                default -> throw new IOException("Incomplete header");
            }
        } while (hyphen < 5);

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

        return new Pem(header, data, footer);
    }

    public String getData() {
        return data;
    }

    public String getHeader() {
        return header;
    }

    public String getFooter() {
        return footer;
    }
}
