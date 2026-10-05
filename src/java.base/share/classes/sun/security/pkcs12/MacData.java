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
 *
 * @author Sharon Liu
 */

class MacData {

    private static final Debug debug = Debug.getInstance("pkcs12");
    private final String macAlgorithm;
    private final byte[] digest;
    private final byte[] macSalt;
    private final int iterations;

    // The following three fields are for PBMAC1.
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

    /**
     * Computes a MAC on the data.
     *
     * This is a two-step process: first generate a key and then use the
     * key to generate the MAC. PBMAC1 and non-PBMAC1 keys use different
     * key factories. PBMAC1 uses a pseudorandom function (kdfHmac)
     * to generate keys while non-PBMAC1 does not. The MAC is computed
     * according to the specified hmac algorithm.
     *
     * @param macAlgorithm the algorithm used to compute the MAC
     * @param password the password used to generate the key
     * @param params a PBEParameterSpec object
     * @param data the data on which the MAC is computed
     * @param kdfHmac the pseudorandom function used to compute the key
     * for PBMAC1
     * @param hmac the algorithm used to compute the MAC
     * @param keyLength the length of the key generated by the pseudorandom
     * function
     *
     * @return the computed MAC as a byte array
     *
     * @exception NoSuchAlgorithmException if either kdfHmac or hmac is
     * unknown to the Mac or SecretKeyFactory
     */
    private static byte[] calculateMac(String macAlgorithm, char[] password,
            PBEParameterSpec params, byte[] data,
            String kdfHmac, String hmac, int keyLength)
            throws InvalidAlgorithmParameterException, InvalidKeyException,
            InvalidKeySpecException, NoSuchAlgorithmException {
        SecretKeyFactory skf;
        SecretKey pbeKey = null;
        Mac m;

        PBEKeySpec keySpec;

        /*
         * The Hmac has to be extracted from the algorithm name for
         * PBMAC1 algorithms. For non-PBMAC1 macAlgorithms, the name
         * and Hmac are the same.
         *
         * The prefix used in Algorithm names is guaranteed to be lowercase.
         */
        if (macAlgorithm.startsWith("pbewith")) {
            m = Mac.getInstance(hmac);
            int len = keyLength == -1 ? m.getMacLength()*8 : keyLength;
            skf = SecretKeyFactory.getInstance("PBKDF2With" +kdfHmac);
            keySpec = new PBEKeySpec(password, params.getSalt(),
                    params.getIterationCount(), len);
        } else {
            m = Mac.getInstance(macAlgorithm);
            skf = SecretKeyFactory.getInstance("PBE");
            keySpec = new PBEKeySpec(password);
        }

        try {
            pbeKey = skf.generateSecret(keySpec);
            if (macAlgorithm.startsWith("pbewith")) {
                m.init(pbeKey);
            } else {
                m.init(pbeKey, params);
            }
            m.update(data);
            return m.doFinal();
        } finally {
            keySpec.clearPassword();
            KeyUtil.destroySecretKeys(pbeKey);
        }
    }

    /**
     * Verify Mac on the data.
     *
     * Calculate Mac on the data and compare with Mac found in input stream.
     *
     * @param password the password used to generate the key
     * @param data the data on which the MAC is computed
     *
     * @exception UnrecoverableKeyException if calculated Mac and
     * Mac found in input stream are different
     */
    void verifyMac(char[] password, byte[] data)
            throws InvalidAlgorithmParameterException, InvalidKeyException,
            InvalidKeySpecException, NoSuchAlgorithmException,
            UnrecoverableKeyException {

        byte[] macResult = calculateMac(this.macAlgorithm, password,
                new PBEParameterSpec(this.macSalt, this.iterations),
                data, this.kdfHmac, this.hmac, this.keyLength);

        if (debug != null) {
            debug.println("Checking keystore integrity " +
                    "(" + this.macAlgorithm + " iterations: "
                    + this.iterations + ")");
        }

        if (!MessageDigest.isEqual(this.digest, macResult)) {
            throw new UnrecoverableKeyException("Failed PKCS12" +
                    " integrity checking");
        }
    }

    /*
     * Gathers parameters and generates a MAC of the data
     *
     * @param password the password used to generate the key
     * @param data the data on which the MAC is computed
     * @param macAlgorithm the algorithm used to compute the MAC
     * @param macIterationCount the iteration count
     * @param salt the salt
     *
     * @exception IOException if the MAC cannot be calculated
     *
     * @return the computed MAC as a byte array
     */
    static byte[] generateMac(char[] passwd, byte[] data,
            String macAlgorithm, int macIterationCount, byte[] salt)
            throws IOException, NoSuchAlgorithmException {
        final PBEParameterSpec params;
        String algName;
        String kdfHmac;
        String hmac;

        macAlgorithm = macAlgorithm.toLowerCase(ENGLISH);
        // The prefix used in Algorithm names is guaranteed to be lowercase.
        if (macAlgorithm.startsWith("pbewith")) {
            algName = "PBMAC1";
            kdfHmac = MacData.parseKdfHmac(macAlgorithm);
            hmac = MacData.parseHmac(macAlgorithm);
            if (hmac == null) {
                hmac = kdfHmac;
            }
        } else if (macAlgorithm.startsWith("hmacpbe")) {
            algName = macAlgorithm.substring(7);
            kdfHmac = null;
            hmac = macAlgorithm;
        } else {
            throw new ParsingException("unexpected algorithm '"
                    + macAlgorithm + "'");
        }

        params = new PBEParameterSpec(salt, macIterationCount);

        try {
            byte[] macResult = calculateMac(macAlgorithm, passwd, params, data,
                    kdfHmac, hmac, -1);

            DerOutputStream bytes = new DerOutputStream();
            bytes.write(encode(algName, macResult, params, kdfHmac, hmac,
                    macResult.length));
            return bytes.toByteArray();
        } catch (InvalidKeySpecException | InvalidKeyException |
                    InvalidAlgorithmParameterException e) {
            throw new IOException("calculateMac failed: " + e, e);
        }
    }

    String getMacAlgorithm() {
        return this.macAlgorithm;
    }

    int getIterations() {
        return this.iterations;
    }

    /**
     * Returns the ASN.1 encoding.
     * @return the ASN.1 encoding
     * @exception NoSuchAlgorithmException if error occurs when constructing its
     * ASN.1 encoding.
     */
    static byte[] encode(String algName, byte[] digest, PBEParameterSpec p,
            String kdfHmac, String hmac, int keyLength)
            throws IOException, NoSuchAlgorithmException {

        final int iterations = p.getIterationCount();
        final byte[] macSalt = p.getSalt();

        DerOutputStream tmp = new DerOutputStream();
        DerOutputStream out = new DerOutputStream();

        if (algName.equals("PBMAC1")) {
            DerOutputStream tmp1 = new DerOutputStream();
            DerOutputStream tmp2 = new DerOutputStream();

            // id-PBMAC1 OBJECT IDENTIFIER ::= { pkcs-5 14 }
            tmp2.putOID(ObjectIdentifier.of(KnownOIDs.PBMAC1));
            tmp2.writeBytes(PBMAC1Parameters.encode(macSalt, iterations,
                    keyLength, kdfHmac, hmac));

            tmp1.write(DerValue.tag_Sequence, tmp2);
            tmp1.putOctetString(digest);

            tmp.write(DerValue.tag_Sequence, tmp1);
            tmp.putOctetString(
                    new byte[]{ 'N', 'O', 'T', ' ', 'U', 'S', 'E', 'D' });
            // Unused, but must have non-zero positive value.
            tmp.putInteger(1);
        } else {
            final AlgorithmId digestAlgorithm = AlgorithmId.get(algName);
            DerOutputStream tmp2 = new DerOutputStream();

            tmp2.write(digestAlgorithm);
            tmp2.putOctetString(digest);

            // wrap into a SEQUENCE
            tmp.write(DerValue.tag_Sequence, tmp2);
            tmp.putOctetString(macSalt);
            tmp.putInteger(iterations);
        }
        // wrap everything into a SEQUENCE
        out.write(DerValue.tag_Sequence, tmp);
        return out.toByteArray();
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
