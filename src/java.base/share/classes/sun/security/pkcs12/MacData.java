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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.spec.AlgorithmParameterSpec;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.security.auth.DestroyFailedException;

import com.sun.crypto.provider.PBMAC1Parameters;
import sun.security.pkcs.ParsingException;
import sun.security.util.*;
import sun.security.x509.AlgorithmId;


/**
 * A MacData type, as defined in PKCS#12.
 *
 * @author Sharon Liu
 */

class MacData {

    private static final Debug debug = Debug.getInstance("pkcs12");
    private final String digestAlgorithmName;
    private String macAlgorithm = null;
    private final byte[] digest;
    private final byte[] macSalt;
    private final int iterations;
    private String kdfHmac;
    private String Hmac;
    private int keyLength;
    private boolean pbmac1Keystore = false;

    // the ASN.1 encoded contents of this class
    private byte[] encoded = null;

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
        this.digestAlgorithmName = digestAlgorithmId.getName();

        // Get the digest.
        this.digest = digestInfo[1].getOctetString();

        if (this.digestAlgorithmName.equals("PBMAC1")) {
            PBMAC1Parameters algParams;

            algParams = new PBMAC1Parameters();
            algParams.Init(digestAlgorithmId.getEncodedParams());

            this.iterations = algParams.getIterations();
            this.macSalt = algParams.getSalt();

            this.kdfHmac = algParams.getPrf();
            this.Hmac = algParams.getHmac();
            this.macAlgorithm = "PBEWith" + this.kdfHmac + "And" + this.Hmac;
        } else {
            this.macSalt = macData[1].getOctetString();
            if (macData.length > 2) {
                this.iterations = macData[2].getInteger();
            } else {
                this.iterations = 1;
            }
            this.macAlgorithm = "HmacPBE"
                    + this.digestAlgorithmName.replace("-", "");
        }
    }

    MacData(String algName, byte[] digest, AlgorithmParameterSpec params,
            String kdfHmac, String Hmac, int keyLength) throws NoSuchAlgorithmException {
        AlgorithmId algid;

        if (algName == null) {
           throw new NullPointerException("the algName parameter " +
                                               "must be non-null");
        }
        if (algName.equals("PBMAC1")) {
            this.pbmac1Keystore = true;
        }
        algid = AlgorithmId.get(algName);

        this.digestAlgorithmName = algid.getName();

        if (digest == null) {
            throw new NullPointerException("the digest " +
                                           "parameter must be non-null");
        } else if (digest.length == 0) {
            throw new IllegalArgumentException("the digest " +
                                                "parameter must not be empty");
        } else {
            this.digest = digest.clone();
        }

        if (!(params instanceof PBEParameterSpec p)) {
            throw new IllegalArgumentException("unsupported parameter spec");
        }

        if (this.pbmac1Keystore) {
            this.macSalt = p.getSalt();
            this.iterations = p.getIterationCount();
            this.kdfHmac = kdfHmac;
            this.Hmac = Hmac;
            this.keyLength = keyLength;
        } else {
            this.macSalt = p.getSalt();
            this.iterations = p.getIterationCount();
            this.kdfHmac = null;
            this.Hmac = null;
            this.keyLength = 0;
        }

        // delay the generation of ASN.1 encoding until
        // getEncoded() is called
        this.encoded = null;
    }

    /*
     * Destroy the key obtained from getPBEKey().
     */
    static void destroyPBEKey(SecretKey key) {
        try {
            key.destroy();
        } catch (DestroyFailedException e) {
            // Accept this
        }
    }

    void processMacData(AlgorithmParameterSpec params,
            MacData macData, char[] password, byte[] data, String macAlgorithm)
            throws  Exception {
        final String kdfHmac;
        final String Hmac;

        if (macAlgorithm.startsWith("PBEWith")) {
            kdfHmac = macData.getKdfHmac();
            Hmac = macData.getHmac();
        } else {
            kdfHmac = macAlgorithm;
            Hmac = macAlgorithm;
        }

        var skf = SecretKeyFactory.getInstance(
                kdfHmac.equals("HmacSHA512") ?
                "PBKDF2WithHmacSHA512" : "PBKDF2WithHmacSHA256");

        SecretKey pbeKey = skf.generateSecret(new PBEKeySpec(password,
                ((PBEParameterSpec)params).getSalt(),
                ((PBEParameterSpec)params).getIterationCount(),
                Hmac.equals("HmacSHA512") ? 64*8 : 32*8));
        Mac m = Mac.getInstance(Hmac);
        try {
            m.init(pbeKey);
        } finally {
            destroyPBEKey(pbeKey);
        }
        m.update(data);
        byte[] macResult = m.doFinal();

        if (debug != null) {
            debug.println("Checking keystore integrity " +
                    "(" + m.getAlgorithm() + " iterations: "
                    + macData.getIterations() + ")");
        }

        if (!MessageDigest.isEqual(macData.getDigest(), macResult)) {
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
    public static byte[] calculateMac(char[] passwd, byte[] data,
            boolean newKeystore, String macAlgorithm, int macIterationCount,
            byte[] salt)
        throws IOException, NoSuchAlgorithmException {
        final byte[] mData;
        final PBEParameterSpec params;
        final MacData macData;
        String algName = "PBMAC1";
        String kdfHmac = null;
        String Hmac = null;

        if (newKeystore) {
            if (macAlgorithm.startsWith("PBEWith")) {
                kdfHmac = MacData.parseKdfHmac(macAlgorithm);
                Hmac = MacData.parseHmac(macAlgorithm);
                if (Hmac == null) {
                    Hmac = kdfHmac;
                }
            }
        } else {
            // existing keystore
            String tmp = MacData.parseKdfHmac(macAlgorithm);
            if (tmp != null) {
                kdfHmac = tmp;
                Hmac = MacData.parseHmac(macAlgorithm);
            }
        }
        // Fall back to old way of computing MAC
        if (kdfHmac == null) {
            algName = macAlgorithm.substring(7);
            kdfHmac = macAlgorithm;
            Hmac = macAlgorithm;
        }

        params = new PBEParameterSpec(salt, macIterationCount);

        var skf = SecretKeyFactory.getInstance(kdfHmac.equals("HmacSHA512") ?
                "PBKDF2WithHmacSHA512" : "PBKDF2WithHmacSHA256");
        try {
            int keyLength = Hmac.equals("HmacSHA512") ? 64*8 : 32*8;

            SecretKey pbeKey = skf.generateSecret(new PBEKeySpec(passwd,
                    params.getSalt(), macIterationCount, keyLength));

            Mac m = Mac.getInstance(Hmac);
            try {
                m.init(pbeKey);
            } finally {
                destroyPBEKey(pbeKey);
            }
            m.update(data);
            byte[] macResult = m.doFinal();

            // encode as MacData
            macData = new MacData(algName, macResult, params,
                    kdfHmac, Hmac, keyLength);
            DerOutputStream bytes = new DerOutputStream();
            bytes.write(macData.getEncoded());
            mData = bytes.toByteArray();
        } catch (Exception e) {
            throw new IOException("calculateMac failed: " + e, e);
        }
        return mData;
    }

    String getDigestAlgName() {
        return this.digestAlgorithmName;
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

    byte[] getDigest() {
        return this.digest;
    }

    String getKdfHmac() {
        return this.kdfHmac;
    }

    String getHmac() {
        return this.Hmac;
    }

    /**
     * Returns the ASN.1 encoding of this object.
     * @return the ASN.1 encoding.
     * @exception IOException if error occurs when constructing its
     * ASN.1 encoding.
     */
    public byte[] getEncoded() throws NoSuchAlgorithmException, IOException {
        if (this.pbmac1Keystore) {
            ObjectIdentifier pkcs5PBKDF2_OID =
                    ObjectIdentifier.of(KnownOIDs.PBKDF2WithHmacSHA1);

            byte[] not_used = { 'N', 'O', 'T', ' ', 'U', 'S', 'E', 'D' };

            DerOutputStream out = new DerOutputStream();
            DerOutputStream tmp0 = new DerOutputStream();
            DerOutputStream tmp1 = new DerOutputStream();
            DerOutputStream tmp2 = new DerOutputStream();
            DerOutputStream tmp3 = new DerOutputStream();
            DerOutputStream tmp4 = new DerOutputStream();
            DerOutputStream Hmac = new DerOutputStream();
            DerOutputStream kdfHmac = new DerOutputStream();

            // encode kdfHmac algorithm
            kdfHmac.putOID(ObjectIdentifier.of(KnownOIDs
                    .findMatch(this.kdfHmac)));
            kdfHmac.putNull();

            // encode Hmac algorithm
            Hmac.putOID(ObjectIdentifier.of(KnownOIDs.findMatch(this.Hmac)));
            Hmac.putNull();

            DerOutputStream pBKDF2_params = new DerOutputStream();

            pBKDF2_params.putOctetString(this.macSalt); // choice: 'specified OCTET STRING'

            // encode iterations
            pBKDF2_params.putInteger(this.iterations);

            // encode derived key length
            if (this.keyLength > 0) {
                pBKDF2_params.putInteger(this.keyLength / 8); // derived key length (in octets)
            }
            pBKDF2_params.write(DerValue.tag_Sequence, kdfHmac);
            tmp3.putOID(pkcs5PBKDF2_OID);
            tmp3.write(DerValue.tag_Sequence, pBKDF2_params);
            tmp4.write(DerValue.tag_Sequence, tmp3);
            tmp4.write(DerValue.tag_Sequence, Hmac);

            tmp1.putOID(ObjectIdentifier.of(KnownOIDs .findMatch("PBMAC1")));

            tmp1.write(DerValue.tag_Sequence, tmp4);
            tmp2.write(DerValue.tag_Sequence, tmp1);
            tmp2.putOctetString(this.digest);
            tmp0.write(DerValue.tag_Sequence, tmp2);
            tmp0.putOctetString(not_used);
            tmp0.putInteger(1);
            out.write(DerValue.tag_Sequence, tmp0);
            encoded = out.toByteArray();

            return encoded.clone();
        }

        if (this.encoded != null)
            return this.encoded.clone();

        DerOutputStream out = new DerOutputStream();
        DerOutputStream tmp = new DerOutputStream();

        DerOutputStream tmp2 = new DerOutputStream();
        // encode encryption algorithm
        AlgorithmId algid = AlgorithmId.get(this.digestAlgorithmName);
        algid.encode(tmp2);

        // encode digest data
        tmp2.putOctetString(this.digest);

        tmp.write(DerValue.tag_Sequence, tmp2);

        // encode salt
        tmp.putOctetString(this.macSalt);

        // encode iterations
        tmp.putInteger(this.iterations);

        // wrap everything into a SEQUENCE
        out.write(DerValue.tag_Sequence, tmp);
        this.encoded = out.toByteArray();

        return this.encoded.clone();
    }

    public static String parseKdfHmac(String text) {
        int index1 = text.indexOf("With") + 4;
        int index2 = text.indexOf("And");
        if (index1 == 3) { // -1 + 4
            return null;
        } else if (index2 == -1) {
            return text.substring(index1);
        } else {
            return text.substring(index1, index2);
        }
    }

    public static String parseHmac(String text) {
        int index1 = text.indexOf("And") + 3;;
        if (index1 == 2) { // -1 + 3
            return null;
        } else {
            return text.substring(index1);
        }
    }
}
