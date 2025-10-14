/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.pkcs12;

import java.io.IOException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import static java.util.Locale.ENGLISH;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

import sun.security.pkcs.ParsingException;
import sun.security.util.*;
import sun.security.x509.AlgorithmId;


/**
 * The MacData type, as defined in PKCS#12.
 *
 * @author Sharon Liu
 *
 * The ASN.1 definition is as follows:
 *
 * <pre>
 *
 * MacData ::= SEQUENCE {
 *     mac        DigestInfo,
 *     macSalt    OCTET STRING,
 *     iterations INTEGER DEFAULT 1
 *      -- Note: The default is for historical reasons and its use is
 *      -- deprecated.
 * }
 *
 * DigestInfo ::= SEQUENCE {
 *     digestAlgorithm DigestAlgorithmIdentifier,
 *     digest OCTET STRING
 * }
 *
 * </pre>
 */

class MacData {

    private static final Debug debug = Debug.getInstance("pkcs12");
    private final String macAlgorithm;
    private final byte[] digest;
    private final byte[] macSalt;
    private final int iterations;
    private final int keyLength;
    private final String kdfHmac;
    private final String hmac;

    /**
     * Parses a PKCS#12 MAC data.
     */
    MacData(DerInputStream derin) throws IOException {
        DerValue[] macData = derin.getSequence(2);
        if (macData.length < 2 || macData.length > 3) {
            throw new ParsingException("Invalid length for MacData");
        }

        // Parse the digest info
        DerInputStream digestIn = new DerInputStream(macData[0].toByteArray());
        DerValue[] digestInfo = digestIn.getSequence(2);
        if (digestInfo.length != 2) {
            throw new ParsingException("Invalid length for DigestInfo");
        }

        // Parse the DigestAlgorithmIdentifier.
        AlgorithmId digestAlgorithmId = AlgorithmId.parse(digestInfo[0]);
        String digestAlgorithmName = digestAlgorithmId.getName();

        // Get the digest.
        this.digest = digestInfo[1].getOctetString();

        if (digestAlgorithmName.equals("PBMAC1")) {
            PBMAC1Parameters algParams;

            algParams = new PBMAC1Parameters(digestAlgorithmId
                    .getEncodedParams());

            this.iterations = algParams.getKdfParams().getIterationCount();
            this.macSalt = algParams.getKdfParams().getSalt();
            this.kdfHmac = algParams.getKdfParams().getPrfAlgo();
            this.keyLength = algParams.getKdfParams().getKeyLength();

            // Implementations MUST NOT accept params that omit keyLength.
            if (this.keyLength == -1) {
                throw new IOException("error: missing keyLength field");
            }
            this.hmac = algParams.getHmac();
            this.macAlgorithm = "pbewith" + this.kdfHmac + "and" + this.hmac;
        } else {
            this.kdfHmac = null;
            this.hmac = null;
            this.keyLength = -1;
            this.macSalt = macData[1].getOctetString();
            if (macData.length > 2) {
                this.iterations = macData[2].getInteger();
            } else {
                this.iterations = 1;
            }
            // Remove "-" from digest algorithm names
            this.macAlgorithm = "hmacpbe"
                    + digestAlgorithmName.replace("-", "");
        }
    }

    private static Mac getMac(String macAlgorithm, char[] password,
            PBEParameterSpec params, byte[] data,
            String kdfHmac, String hmac, int keyLength)
            throws NoSuchAlgorithmException, InvalidKeySpecException,
            InvalidKeyException, InvalidAlgorithmParameterException {
        SecretKeyFactory skf;
        SecretKey pbeKey;
        Mac m;

        PBEKeySpec keySpec;
        if (macAlgorithm.startsWith("pbewith")) {
            m = Mac.getInstance(hmac);
            int len = keyLength == 0 ? m.getMacLength()*8 : keyLength;
            skf = SecretKeyFactory.getInstance("PBKDF2With" +kdfHmac);
            keySpec = new PBEKeySpec(password, params.getSalt(),
                    params.getIterationCount(), len);
            pbeKey = skf.generateSecret(new PBEKeySpec(password,
                    params.getSalt(), params.getIterationCount(), len));
        } else {
            hmac = macAlgorithm;
            m = Mac.getInstance(hmac);
            keySpec = new PBEKeySpec(password);
            skf = SecretKeyFactory.getInstance("PBE");
            pbeKey = skf.generateSecret(keySpec);
        }
        keySpec.clearPassword();

        try {
            if (macAlgorithm.startsWith("pbewith")) {
                m.init(pbeKey);
            } else {
                m.init(pbeKey, params);
            }
        } finally {
            sun.security.util.KeyUtil.destroySecretKeys(pbeKey);
        }
        m.update(data);
        return m;
    }

    void processMacData(char[] password, byte[] data)
            throws InvalidKeySpecException,
            NoSuchAlgorithmException, UnrecoverableKeyException,
            InvalidKeyException, InvalidAlgorithmParameterException {
        Mac m;
        byte[] macResult;

        m = getMac(this.macAlgorithm, password,
                new PBEParameterSpec(this.macSalt, this.iterations),
                data, this.kdfHmac, this.hmac, this.keyLength);
        macResult = m.doFinal();

        if (debug != null) {
            debug.println("Checking keystore integrity " +
                    "(" + m.getAlgorithm() + " iterations: "
                    + this.iterations + ")");
        }

        if (!MessageDigest.isEqual(this.digest, macResult)) {
            throw new UnrecoverableKeyException("Failed PKCS12" +
                    " integrity checking");
        }
    }

    /*
     * Calculate MAC using HMAC algorithm (required for password integrity)
     *
     * Hash-based MAC algorithm combines secret key with message digest to
     * create a message authentication code (MAC)
     */
    static byte[] calculateMac(char[] passwd, byte[] data,
            String macAlgorithm, int macIterationCount, byte[] salt)
            throws IOException, NoSuchAlgorithmException {
        final byte[] mData;
        final PBEParameterSpec params;
        String algName = "PBMAC1";
        String kdfHmac;
        String hmac;
        Mac m;
        int keyLength;

        macAlgorithm = macAlgorithm.toLowerCase(ENGLISH);
        if (macAlgorithm.startsWith("pbewith")) {
            kdfHmac = MacData.parseKdfHmac(macAlgorithm);
            hmac = MacData.parseHmac(macAlgorithm);
            if (hmac == null) {
                hmac = kdfHmac;
            }
        } else if (macAlgorithm.startsWith("hmacpbe")) {
            algName = macAlgorithm.substring(7);
            kdfHmac = macAlgorithm;
            hmac = macAlgorithm;
        } else {
            throw new ParsingException("unexpected algorithm '"
                    +macAlgorithm+ "'");
        }

        params = new PBEParameterSpec(salt, macIterationCount);

        try {
            m = getMac(macAlgorithm, passwd, params, data, kdfHmac, hmac, 0);
            byte[] macResult = m.doFinal();

            DerOutputStream bytes = new DerOutputStream();
            bytes.write(encode(algName, macResult, params, kdfHmac, hmac,
                    m.getMacLength()));
            mData = bytes.toByteArray();
        } catch (InvalidKeySpecException | InvalidKeyException |
                    InvalidAlgorithmParameterException e) {
            throw new IOException("calculateMac failed: " + e, e);
        }
        return mData;
    }

    String getMacAlgorithm() {
        return this.macAlgorithm;
    }

    byte[] getSalt() {
        return this.macSalt;
    }

    int getIterations() {
        return this.iterations;
    }

    /**
     * Returns the ASN.1 encoding of this object.
     * @return the ASN.1 encoding.
     * @exception NoSuchAlgorithmException if error occurs when constructing its
     * ASN.1 encoding.
     */
    static byte[] encode(String algName, byte[] digest, PBEParameterSpec p,
            String kdfHmac, String hmac, int keyLength)
            throws NoSuchAlgorithmException {
        return PBMAC1Parameters.encode(algName, p.getSalt(),
                p.getIterationCount(), keyLength, kdfHmac, hmac, digest);
    }

    private static String parseKdfHmac(String text) {
        int index1 = text.indexOf("with") + 4;
        int index2 = text.indexOf("and");
        if (index1 == 3) { // -1 + 4
            return null;
        } else if (index2 == -1) {
            return text.substring(index1);
        } else {
            return text.substring(index1, index2);
        }
    }

    private static String parseHmac(String text) {
        int index1 = text.indexOf("and") + 3;
        if (index1 == 2) { // -1 + 3
            return null;
        } else {
            return text.substring(index1);
        }
    }
}
