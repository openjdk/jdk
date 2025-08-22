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
import java.security.AlgorithmParameters;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.spec.PBEParameterSpec;

import sun.security.pkcs.ParsingException;
import sun.security.util.*;
import sun.security.x509.AlgorithmId;


/**
 * A MacData type, as defined in PKCS#12.
 *
 * @author Sharon Liu
 */

class MacData {

    private final String digestAlgorithmName;
    private final AlgorithmParameters digestAlgorithmParams;
    private final byte[] digest;
    private byte[] macSalt;
    private byte[] extraMacSalt;
    private int iterations;
    private int extraIterations;
    private String kdfHmac;
    private String Hmac;
    private int keyLength;

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
        this.digestAlgorithmParams = digestAlgorithmId.getParameters();

        // Get the digest.
        this.digest = digestInfo[1].getOctetString();

        if (digestInfo[0].tag != DerValue.tag_Sequence) {
            throw new IOException("algid parse error, not a sequence");
        }
        if (digestAlgorithmName.equals("PBMAC1")) {
            PBEParameterSpec pbeSpec;

            try {
                pbeSpec =
                        digestAlgorithmParams.getParameterSpec(
                        PBEParameterSpec.class);
            } catch (InvalidParameterSpecException ipse) {
                throw new IOException(
                        "Invalid PBE algorithm parameters");
            }
            iterations = pbeSpec.getIterationCount();
            macSalt = pbeSpec.getSalt();
            String ps = digestAlgorithmParams.toString();
            kdfHmac = getKdfHmac(ps);
            Hmac = getHmac(ps);
        }

        // Get the salt.
        extraMacSalt = macData[1].getOctetString();

        // Iterations are optional. The default value is 1.
        if (macData.length > 2) {
            extraIterations = macData[2].getInteger();
        } else {
            extraIterations = 1;
        }
        if (!digestAlgorithmName.equals("PBMAC1")) {
            macSalt = extraMacSalt;
            iterations = extraIterations;
        }
    }

    MacData(String algName, byte[] digest, AlgorithmParameterSpec params,
            String defaultKdfHmac, byte[] extraSalt, int extraIterationCount)
        throws NoSuchAlgorithmException
    {
        if (algName == null) {
           throw new NullPointerException("the algName parameter " +
                                               "must be non-null");
        }

        AlgorithmId algid = AlgorithmId.get(algName);
        this.digestAlgorithmName = algid.getName();
        this.digestAlgorithmParams = algid.getParameters();

        if (digest == null) {
            throw new NullPointerException("the digest " +
                                           "parameter must be non-null");
        } else if (digest.length == 0) {
            throw new IllegalArgumentException("the digest " +
                                                "parameter must not be empty");
        } else {
            this.digest = digest.clone();
        }

        if (params instanceof PBEParameterSpec p) {
            if (algName.equals("PBMAC1")) {
                macSalt = p.getSalt();
                iterations = p.getIterationCount();
                kdfHmac = defaultKdfHmac;
                Hmac = kdfHmac;

                if (defaultKdfHmac.equals("HmacSHA512")) {
                    keyLength = 512;
                } else if (defaultKdfHmac.equals("HmacSHA256")) {
                    keyLength = 256;
                } else {
                    throw new IllegalArgumentException("unsupported Hmac");
                }
                extraMacSalt = extraSalt;
                extraIterations = extraIterationCount;
            } else {
                macSalt = p.getSalt();
                iterations = p.getIterationCount();
                kdfHmac = null;
                Hmac = null;
                keyLength = 0;
            }
        } else {
            throw new IllegalArgumentException("unsupported parameter spec");
        }

        // delay the generation of ASN.1 encoding until
        // getEncoded() is called
        this.encoded = null;
    }

    String getDigestAlgName() {
        return digestAlgorithmName;
    }

    byte[] getSalt() {
        return macSalt;
    }

    int getIterations() {
        return iterations;
    }

    byte[] getDigest() {
        return digest;
    }

    String getKdfHmac() {
        return kdfHmac;
    }

    String getHmac() {
        return Hmac;
    }

    int getKeyLength() {
        return keyLength;
    }

    byte[] getExtraSalt() {
        return extraMacSalt;
    }

    int getExtraIterations() {
        return extraIterations;
    }

    /**
     * Returns the ASN.1 encoding of this object.
     * @return the ASN.1 encoding.
     * @exception IOException if error occurs when constructing its
     * ASN.1 encoding.
     */
    public byte[] getEncoded() throws NoSuchAlgorithmException,
            IOException
    {
        if (digestAlgorithmName.equals("PBMAC1")) {
            ObjectIdentifier pkcs5PBKDF2_OID =
                    ObjectIdentifier.of(KnownOIDs.PBKDF2WithHmacSHA1);

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

            pBKDF2_params.putOctetString(macSalt); // choice: 'specified OCTET STRING'

            // encode iterations
            pBKDF2_params.putInteger(iterations);

            // encode derived key length
            if (keyLength > 0) {
                pBKDF2_params.putInteger(keyLength / 8); // derived key length (in octets)
            }
            pBKDF2_params.write(DerValue.tag_Sequence, kdfHmac);
            tmp3.putOID(pkcs5PBKDF2_OID);
            tmp3.write(DerValue.tag_Sequence, pBKDF2_params);
            tmp4.write(DerValue.tag_Sequence, tmp3);
            tmp4.write(DerValue.tag_Sequence, Hmac);

            // PBMAC1
            tmp1.putOID(ObjectIdentifier.of(KnownOIDs
                    .findMatch(digestAlgorithmName)));

            tmp1.write(DerValue.tag_Sequence, tmp4);
            tmp2.write(DerValue.tag_Sequence, tmp1);
            tmp2.putOctetString(digest);
            tmp0.write(DerValue.tag_Sequence, tmp2);
            tmp0.putOctetString(extraMacSalt);
            tmp0.putInteger(extraIterations);

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
        AlgorithmId algid = AlgorithmId.get(digestAlgorithmName);
        algid.encode(tmp2);

        // encode digest data
        tmp2.putOctetString(digest);

        tmp.write(DerValue.tag_Sequence, tmp2);

        // encode salt
        tmp.putOctetString(macSalt);

        // encode iterations
        tmp.putInteger(iterations);

        // wrap everything into a SEQUENCE
        out.write(DerValue.tag_Sequence, tmp);
        this.encoded = out.toByteArray();

        return this.encoded.clone();
    }

    public String getKdfHmac(String text) {
        final String word1 = "With";
        final String word2 = "And";

        String regex = Pattern.quote(word1) + "(.*?)" + Pattern.quote(word2);
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }

    public String getHmac(String text) {
        final String word2 = "And";

        String regex = Pattern.quote(word2) + "(.*?)$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }
}
