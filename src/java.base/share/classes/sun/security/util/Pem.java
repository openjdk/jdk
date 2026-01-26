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

import sun.security.pkcs.PKCS8Key;
import sun.security.x509.AlgorithmId;

import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
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
    private static final byte[] CRLF = new byte[] {'\r', '\n'};

    // Default algorithm from jdk.epkcs8.defaultAlgorithm in java.security
    public static final String DEFAULT_ALGO;

    // Pattern matching for EKPI operations
    private static final Pattern PBE_PATTERN;

    // Pattern matching for stripping whitespace.
    private static final Pattern STRIP_WHITESPACE_PATTERN;

    // Pattern matching for inserting line breaks.
    private static final Pattern LINE_WRAP_64_PATTERN;

    // Lazy initialized PBES2 OID value
    private static ObjectIdentifier PBES2OID;

    // Lazy initialized singleton encoder.
    private static Base64.Encoder b64Encoder;

    static {
        String algo = Security.getProperty("jdk.epkcs8.defaultAlgorithm");
        DEFAULT_ALGO = (algo == null || algo.isBlank()) ?
            "PBEWithHmacSHA256AndAES_128" : algo;
        PBE_PATTERN = Pattern.compile("^PBEWith.*And.*",
            Pattern.CASE_INSENSITIVE);
        STRIP_WHITESPACE_PATTERN = Pattern.compile("\\s+");
        LINE_WRAP_64_PATTERN = Pattern.compile("(.{64})");
    }

    public static final String CERTIFICATE = "CERTIFICATE";
    public static final String X509_CRL = "X509 CRL";
    public static final String ENCRYPTED_PRIVATE_KEY = "ENCRYPTED PRIVATE KEY";
    public static final String PRIVATE_KEY = "PRIVATE KEY";
    public static final String RSA_PRIVATE_KEY = "RSA PRIVATE KEY";
    public static final String PUBLIC_KEY = "PUBLIC KEY";
    // old PEM types per RFC 7468
    public static final String X509_CERTIFICATE = "X509 CERTIFICATE";
    public static final String X_509_CERTIFICATE = "X.509 CERTIFICATE";
    public static final String CRL = "CRL";

    /**
     * Decodes a PEM-encoded block.
     *
     * @param input the input string, according to RFC 1421, can only contain
     *              characters in the base-64 alphabet and whitespaces.
     * @return the decoded bytes
     */
    public static byte[] decode(String input) {
        byte[] src = STRIP_WHITESPACE_PATTERN.matcher(input).replaceAll("").
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
        if (!PBE_PATTERN.matcher(algorithm).matches()) {
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

    /*
     * RFC 7468 has some rules what generators should return given a historical
     * type name.  This converts read in PEM to the RFC.  Change the type to
     * be uniform is likely to help apps from not using all 3 certificate names.
     */
    private static String typeConverter(String type) {
        return switch (type) {
            case Pem.X509_CERTIFICATE, Pem.X_509_CERTIFICATE -> Pem.CERTIFICATE;
            case Pem.CRL -> Pem.X509_CRL;
            default -> type;
        };
    }

    /**
     * Read the PEM text and return it in it's three components:  header,
     * base64, and footer.
     *
     * The header begins processing when "-----B" is read.  At that point
     * exceptions will be thrown for syntax errors.
     *
     * The method will leave the stream after reading the end of line of the
     * footer or end of file
     * @param is an InputStream
     * @param shortHeader if true, the hyphen length is 4 because the first
     *                    hyphen is assumed to have been read.  This is needed
     *                    for the CertificateFactory X509 implementation.
     * @return a new PEMRecord
     * @throws IOException on IO errors or PEM syntax errors that leave
     * the read position not at the end of a PEM block
     * @throws EOFException when at the unexpected end of the stream
     * @throws IllegalArgumentException when a PEM syntax error occurs,
     * but the read position in the stream is at the end of the block, so
     * future reads can be successful.
     */
    public static PEM readPEM(InputStream is, boolean shortHeader)
        throws IOException {
        Objects.requireNonNull(is);

        int hyphen = (shortHeader ? 1 : 0);
        int eol = 0;
        ByteArrayOutputStream os = new ByteArrayOutputStream(6);

        // Find 5 hyphens followed by a 'B' to start processing the header.
        boolean headerStarted = false;
        do {
            int d = is.read();
            switch (d) {
                case '-' -> hyphen++;
                case -1 -> {
                    if (os.size() == 0) {
                        throw new EOFException("No data available");
                    }
                    throw new EOFException("No PEM data found");
                }
                case 'B' -> {
                    if (hyphen == 5) {
                        headerStarted = true;
                    } else {
                        hyphen = 0;
                    }
                }
                default -> hyphen = 0;
            }
            os.write(d);
        } while (!headerStarted);

        StringBuilder sb = new StringBuilder(64);
        sb.append("-----B");
        hyphen = 0;
        int c;

        // Get header definition until first hyphen
        do {
            switch (c = is.read()) {
                case '-' -> hyphen++;
                case -1 -> throw new EOFException("Input ended prematurely");
                case '\n', '\r' -> throw new IOException("Incomplete header");
                default -> sb.append((char) c);
            }
        } while (hyphen == 0);

        // Verify header ending with 5 hyphens.
        do {
            switch (is.read()) {
                case '-' -> hyphen++;
                default ->
                    throw new IOException("Incomplete header");
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
                throw new IOException("No EOL character found");
        }

        // Read data until we find the first footer hyphen.
        do {
            switch (c = is.read()) {
                case -1 ->
                    throw new EOFException("Incomplete header");
                case '-' -> hyphen++;
                case WS, '\t', '\r', '\n' -> {} // skip whitespace and tab
                default -> sb.append((char) c);
            }
        } while (hyphen == 0);

        String data = sb.toString();

        // Verify footer starts with 5 hyphens.
        do {
            switch (is.read()) {
                case '-' -> hyphen++;
                case -1 -> throw new EOFException("Input ended prematurely");
                default -> throw new IOException("Incomplete footer");
            }
        } while (hyphen < 5);

        hyphen = 0;
        sb = new StringBuilder(64);
        sb.append("-----");

        // Look for Complete header by looking for the end of the hyphens
        do {
            switch (c = is.read()) {
                case '-' -> hyphen++;
                case -1 -> throw new EOFException("Input ended prematurely");
                default -> sb.append((char) c);
            }
        } while (hyphen == 0);

        // Verify ending with 5 hyphens.
        do {
            switch (is.read()) {
                case '-' -> hyphen++;
                case -1 -> throw new EOFException("Input ended prematurely");
                default -> throw new IOException("Incomplete footer");
            }
        } while (hyphen < 5);

        while ((c = is.read()) != eol && c != -1 && c != WS) {
            // skip when eol is '\n', the line separator is likely "\r\n".
            if (c == '\r') {
                continue;
            }
            throw new IOException("Invalid PEM format:  " +
                "No EOL char found in footer:  0x" +
                HexFormat.of().toHexDigits((byte) c));
        }

        sb.append("-----");
        String footer = sb.toString();
        if (footer.length() < 14 || !footer.startsWith("-----END ") ||
            !footer.endsWith("-----")) {
            // Not an IOE because the read pointer is correctly at the end.
            throw new IOException("Illegal footer: " + footer);
        }

        // Verify the object type in the header and the footer are the same.
        String headerType = header.substring(11, header.length() - 5);
        String footerType = footer.substring(9, footer.length() - 5);
        if (!headerType.equals(footerType)) {
            throw new IOException("Header and footer do not " +
                "match: " + headerType + " " + footerType);
        }

        // If there was data before finding the 5 dashes of the PEM header,
        // backup 5 characters and save that data.
        byte[] preData = null;
        if (os.size() > 6) {
            preData = Arrays.copyOf(os.toByteArray(), os.size() - 6);
        }

        return new PEM(typeConverter(headerType), data, preData);
    }

    public static PEM readPEM(InputStream is) throws IOException {
        return readPEM(is, false);
    }

    private static String pemEncoded(String type, String base64) {
        return
            "-----BEGIN " + type + "-----\r\n" +
            base64 + (!base64.endsWith("\n") ? "\r\n" : "") +
            "-----END " + type + "-----\r\n";
    }

    /**
     * Construct a String-based encoding based off the type.  leadingData
     * is not used with this method.
     * @return PEM in a string
     */
    public static String pemEncoded(String type, byte[] der) {
        if (b64Encoder == null) {
            b64Encoder = Base64.getMimeEncoder(64, CRLF);
        }
        return pemEncoded(type, b64Encoder.encodeToString(der));
    }

    /**
     * Construct a String-based encoding based off the type.  leadingData
     * is not used with this method.
     * @return PEM in a string
     */
    public static String pemEncoded(PEM pem) {
        String p = LINE_WRAP_64_PATTERN.matcher(pem.content()).replaceAll("$1\r\n");
        return pemEncoded(pem.type(), p);
    }

    /*
     * Get PKCS8 encoding from an encrypted private key encoding.
     */
    public static byte[] decryptEncoding(byte[] encoded, char[] password)
        throws GeneralSecurityException {
        EncryptedPrivateKeyInfo ekpi;

        Objects.requireNonNull(password, "password cannot be null");
        PBEKeySpec keySpec = new PBEKeySpec(password);
        try {
            ekpi = new EncryptedPrivateKeyInfo(encoded);
            return decryptEncoding(ekpi, keySpec);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        } finally {
            keySpec.clearPassword();
        }
    }

    public static byte[] decryptEncoding(EncryptedPrivateKeyInfo ekpi, PBEKeySpec keySpec)
        throws NoSuchAlgorithmException, InvalidKeyException {

        PKCS8EncodedKeySpec p8KeySpec = null;
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ekpi.getAlgName());
            p8KeySpec = ekpi.getKeySpec(skf.generateSecret(keySpec));
            return p8KeySpec.getEncoded();
        } catch (InvalidKeySpecException e) {
            throw new InvalidKeyException(e);
        } finally {
            KeyUtil.clear(p8KeySpec);
        }
    }


    /**
     * With a given PKCS8 encoding, construct a PrivateKey or KeyPair.  A
     * KeyPair is returned if requested and the encoding has a public key;
     * otherwise, a PrivateKey is returned.
     *
     * @param encoded PKCS8 encoding
     * @param pair set to true for returning a KeyPair, if possible. Otherwise,
     *             return a PrivateKey
     * @param provider KeyFactory provider
     */
    public static DEREncodable toDEREncodable(byte[] encoded, boolean pair,
        Provider provider) throws InvalidKeyException {

        PrivateKey privKey;
        PublicKey pubKey = null;
        PKCS8EncodedKeySpec p8KeySpec;
        PKCS8Key p8key = new PKCS8Key(encoded);
        KeyFactory kf;

        try {
            p8KeySpec = new PKCS8EncodedKeySpec(encoded);
        } catch (NullPointerException e) {
            p8key.clear();
            throw new InvalidKeyException("No encoding found", e);
        }

        try {
            if (provider == null) {
                kf = KeyFactory.getInstance(p8key.getAlgorithm());
            } else {
                kf = KeyFactory.getInstance(p8key.getAlgorithm(), provider);
            }
        } catch (NoSuchAlgorithmException e) {
            KeyUtil.clear(p8KeySpec, p8key);
            throw new InvalidKeyException("Unable to find the algorithm: " +
                p8key.getAlgorithm(), e);
        }

        try {
            privKey = kf.generatePrivate(p8KeySpec);

            // Only want the PrivateKey? then return it.
            if (!pair) {
                return privKey;
            }

            if (p8key.hasPublicKey()) {
                // PKCS8Key.decode() has extracted the public key already
                pubKey = kf.generatePublic(
                    new X509EncodedKeySpec(p8key.getPubKeyEncoded()));
            } else {
                // In case decode() could not read the public key, the
                // KeyFactory can try.  Failure is ok as there may not
                // be a public key in the encoding.
                try {
                    pubKey = kf.generatePublic(p8KeySpec);
                } catch (InvalidKeySpecException e) {
                    // ignore
                }
            }
        } catch (InvalidKeySpecException e) {
            throw new InvalidKeyException(e);
        } finally {
            KeyUtil.clear(p8KeySpec, p8key);
        }
        if (pair && pubKey != null) {
            return new KeyPair(pubKey, privKey);
        }
        return privKey;
    }

}
