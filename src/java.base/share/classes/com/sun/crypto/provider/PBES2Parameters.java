/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.crypto.provider;

import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.security.AlgorithmParametersSpi;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEParameterSpec;
import sun.security.util.*;

/**
 * This class implements the parameter set used with password-based
 * encryption scheme 2 (PBES2), which is defined in PKCS#5 as follows:
 *
 * <pre>
 * -- PBES2
 *
 * PBES2Algorithms ALGORITHM-IDENTIFIER ::=
 *   { {PBES2-params IDENTIFIED BY id-PBES2}, ...}
 *
 * id-PBES2 OBJECT IDENTIFIER ::= {pkcs-5 13}
 *
 * PBES2-params ::= SEQUENCE {
 *   keyDerivationFunc AlgorithmIdentifier {{PBES2-KDFs}},
 *   encryptionScheme AlgorithmIdentifier {{PBES2-Encs}} }
 *
 * PBES2-KDFs ALGORITHM-IDENTIFIER ::=
 *   { {PBKDF2-params IDENTIFIED BY id-PBKDF2}, ... }
 *
 * PBES2-Encs ALGORITHM-IDENTIFIER ::= { ... }
 *
 * -- PBKDF2
 *
 * PBKDF2Algorithms ALGORITHM-IDENTIFIER ::=
 *   { {PBKDF2-params IDENTIFIED BY id-PBKDF2}, ...}
 *
 * id-PBKDF2 OBJECT IDENTIFIER ::= {pkcs-5 12}
 *
 * PBKDF2-params ::= SEQUENCE {
 *     salt CHOICE {
 *       specified OCTET STRING,
 *       otherSource AlgorithmIdentifier {{PBKDF2-SaltSources}}
 *     },
 *     iterationCount INTEGER (1..MAX),
 *     keyLength INTEGER (1..MAX) OPTIONAL,
 *     prf AlgorithmIdentifier {{PBKDF2-PRFs}} DEFAULT algid-hmacWithSHA1
 * }
 *
 * PBKDF2-SaltSources ALGORITHM-IDENTIFIER ::= { ... }
 *
 * PBKDF2-PRFs ALGORITHM-IDENTIFIER ::= {
 *     {NULL IDENTIFIED BY id-hmacWithSHA1} |
 *     {NULL IDENTIFIED BY id-hmacWithSHA224} |
 *     {NULL IDENTIFIED BY id-hmacWithSHA256} |
 *     {NULL IDENTIFIED BY id-hmacWithSHA384} |
 *     {NULL IDENTIFIED BY id-hmacWithSHA512}, ... }
 *
 * algid-hmacWithSHA1 AlgorithmIdentifier {{PBKDF2-PRFs}} ::=
 *     {algorithm id-hmacWithSHA1, parameters NULL : NULL}
 *
 * id-hmacWithSHA1 OBJECT IDENTIFIER ::= {digestAlgorithm 7}
 *
 * PBES2-Encs ALGORITHM-IDENTIFIER ::= { ... }
 *
 * </pre>
 */
abstract class PBES2Parameters extends AlgorithmParametersSpi {

    private static final ObjectIdentifier pkcs5PBKDF2_OID =
            ObjectIdentifier.of(KnownOIDs.PBKDF2WithHmacSHA1);
    private static final ObjectIdentifier pkcs5PBES2_OID =
            ObjectIdentifier.of(KnownOIDs.PBES2);
    private static final ObjectIdentifier aes128CBC_OID =
            ObjectIdentifier.of(KnownOIDs.AES_128$CBC$NoPadding);
    private static final ObjectIdentifier aes192CBC_OID =
            ObjectIdentifier.of(KnownOIDs.AES_192$CBC$NoPadding);
    private static final ObjectIdentifier aes256CBC_OID =
            ObjectIdentifier.of(KnownOIDs.AES_256$CBC$NoPadding);

    // the PBES2 algorithm name
    private String pbes2AlgorithmName = null;

    // the salt
    private byte[] salt = null;

    // the iteration count
    private int iCount = 0;

    // the cipher parameter
    private AlgorithmParameterSpec cipherParam = null;

    // the key derivation function (default is HmacSHA1)
    private ObjectIdentifier kdfAlgo_OID =
            ObjectIdentifier.of(KnownOIDs.HmacSHA1);

    // the encryption function
    private ObjectIdentifier cipherAlgo_OID = null;

    // the cipher keysize (in bits)
    private int keysize = -1;

    PBES2Parameters() {
        // KDF, encryption & keysize values are set later, in engineInit(byte[])
    }

    PBES2Parameters(String pbes2AlgorithmName) throws NoSuchAlgorithmException {
        int and;
        String kdfAlgo;
        String cipherAlgo;

        // Extract the KDF and encryption algorithm names
        this.pbes2AlgorithmName = pbes2AlgorithmName;
        if (pbes2AlgorithmName.startsWith("PBEWith") &&
            (and = pbes2AlgorithmName.indexOf("And", 7 + 1)) > 0) {
            kdfAlgo = pbes2AlgorithmName.substring(7, and);
            cipherAlgo = pbes2AlgorithmName.substring(and + 3);

            // Check for keysize
            int underscore;
            if ((underscore = cipherAlgo.indexOf('_')) > 0) {
                int slash;
                if ((slash = cipherAlgo.indexOf('/', underscore + 1)) > 0) {
                    keysize =
                        Integer.parseInt(cipherAlgo.substring(underscore + 1,
                            slash));
                } else {
                    keysize =
                        Integer.parseInt(cipherAlgo.substring(underscore + 1));
                }
                cipherAlgo = cipherAlgo.substring(0, underscore);
            }
        } else {
            throw new NoSuchAlgorithmException("No crypto implementation for " +
                pbes2AlgorithmName);
        }

        switch (kdfAlgo) {
        case "HmacSHA1":
        case "HmacSHA224":
        case "HmacSHA256":
        case "HmacSHA384":
        case "HmacSHA512":
        case "HmacSHA512/224":
        case "HmacSHA512/256":
            kdfAlgo_OID = ObjectIdentifier.of(KnownOIDs.findMatch(kdfAlgo));
            break;
        default:
            throw new NoSuchAlgorithmException(
                "No crypto implementation for " + kdfAlgo);
        }

        if (cipherAlgo.equals("AES")) {
            switch (keysize) {
            case 128:
                cipherAlgo_OID = aes128CBC_OID;
                break;
            case 256:
                cipherAlgo_OID = aes256CBC_OID;
                break;
            default:
                throw new NoSuchAlgorithmException(
                    "No Cipher implementation for " + keysize + "-bit " +
                        cipherAlgo);
            }
        } else {
            throw new NoSuchAlgorithmException("No Cipher implementation for " +
                cipherAlgo);
        }
    }

    protected void engineInit(AlgorithmParameterSpec paramSpec)
        throws InvalidParameterSpecException
    {
       if (!(paramSpec instanceof PBEParameterSpec)) {
           throw new InvalidParameterSpecException
               ("Inappropriate parameter specification");
       }
       this.salt = ((PBEParameterSpec)paramSpec).getSalt().clone();
       this.iCount = ((PBEParameterSpec)paramSpec).getIterationCount();
       this.cipherParam = ((PBEParameterSpec)paramSpec).getParameterSpec();
    }

    protected void engineInit(byte[] encoded)
        throws IOException
    {
        DerValue pBES2_params = new DerValue(encoded);
        if (pBES2_params.tag != DerValue.tag_Sequence) {
            throw new IOException("PBE parameter parsing error: "
                + "not an ASN.1 SEQUENCE tag");
        }
        DerValue kdf = pBES2_params.data.getDerValue();

        // Before JDK-8202837, PBES2-params was mistakenly encoded like
        // an AlgorithmId which is a sequence of its own OID and the real
        // PBES2-params. If the first DerValue is an OID instead of a
        // PBES2-KDFs (which should be a SEQUENCE), we are likely to be
        // dealing with this buggy encoding. Skip the OID and treat the
        // next DerValue as the real PBES2-params.
        if (kdf.getTag() == DerValue.tag_ObjectId) {
            pBES2_params = pBES2_params.data.getDerValue();
            kdf = pBES2_params.data.getDerValue();
        }

        String kdfAlgo = parseKDF(kdf);

        if (pBES2_params.tag != DerValue.tag_Sequence) {
            throw new IOException("PBE parameter parsing error: "
                + "not an ASN.1 SEQUENCE tag");
        }
        String cipherAlgo = parseES(pBES2_params.data.getDerValue());

        this.pbes2AlgorithmName = "PBEWith" + kdfAlgo + "And" + cipherAlgo;
    }

    private String parseKDF(DerValue keyDerivationFunc) throws IOException {

        if (!pkcs5PBKDF2_OID.equals(keyDerivationFunc.data.getOID())) {
            throw new IOException("PBE parameter parsing error: "
                + "expecting the object identifier for PBKDF2");
        }
        if (keyDerivationFunc.tag != DerValue.tag_Sequence) {
            throw new IOException("PBE parameter parsing error: "
                + "not an ASN.1 SEQUENCE tag");
        }
        DerValue pBKDF2_params = keyDerivationFunc.data.getDerValue();
        if (pBKDF2_params.tag != DerValue.tag_Sequence) {
            throw new IOException("PBE parameter parsing error: "
                + "not an ASN.1 SEQUENCE tag");
        }
        DerValue specified = pBKDF2_params.data.getDerValue();
        // the 'specified' ASN.1 CHOICE for 'salt' is supported
        if (specified.tag == DerValue.tag_OctetString) {
            salt = specified.getOctetString();
        } else {
            // the 'otherSource' ASN.1 CHOICE for 'salt' is not supported
            throw new IOException("PBE parameter parsing error: "
                + "not an ASN.1 OCTET STRING tag");
        }
        iCount = pBKDF2_params.data.getInteger();

        // keyLength INTEGER (1..MAX) OPTIONAL,
        var ksDer = pBKDF2_params.data.getOptional(DerValue.tag_Integer);
        if (ksDer.isPresent()) {
            keysize = ksDer.get().getInteger() * 8; // keysize (in bits)
        }

        // prf AlgorithmIdentifier {{PBKDF2-PRFs}} DEFAULT algid-hmacWithSHA1
        String kdfAlgo;
        var prfDer = pBKDF2_params.data.getOptional(DerValue.tag_Sequence);
        if (prfDer.isPresent()) {
            DerValue prf = prfDer.get();
            kdfAlgo_OID = prf.data.getOID();
            KnownOIDs o = KnownOIDs.findMatch(kdfAlgo_OID.toString());
            if (o == null || (!o.stdName().equals("HmacSHA1") &&
                    !o.stdName().equals("HmacSHA224") &&
                    !o.stdName().equals("HmacSHA256") &&
                    !o.stdName().equals("HmacSHA384") &&
                    !o.stdName().equals("HmacSHA512") &&
                    !o.stdName().equals("HmacSHA512/224") &&
                    !o.stdName().equals("HmacSHA512/256"))) {
                throw new IOException("PBE parameter parsing error: "
                        + "expecting the object identifier for a HmacSHA key "
                        + "derivation function");
            }
            kdfAlgo = o.stdName();
            prf.data.getOptional(DerValue.tag_Null);
            prf.data.atEnd();
        } else {
            kdfAlgo = "HmacSHA1";
        }
        return kdfAlgo;
    }

    private String parseES(DerValue encryptionScheme) throws IOException {
        String cipherAlgo;

        cipherAlgo_OID = encryptionScheme.data.getOID();
        if (aes128CBC_OID.equals(cipherAlgo_OID)) {
            cipherAlgo = "AES_128";
            // parameter is AES-IV 'OCTET STRING (SIZE(16))'
            cipherParam =
                new IvParameterSpec(encryptionScheme.data.getOctetString());
            keysize = 128;
        } else if (aes256CBC_OID.equals(cipherAlgo_OID)) {
            cipherAlgo = "AES_256";
            // parameter is AES-IV 'OCTET STRING (SIZE(16))'
            cipherParam =
                new IvParameterSpec(encryptionScheme.data.getOctetString());
            keysize = 256;
        } else {
            throw new IOException("PBE parameter parsing error: "
                + "expecting the object identifier for AES cipher");
        }

        return cipherAlgo;
    }

    protected void engineInit(byte[] encoded, String decodingMethod)
        throws IOException
    {
        engineInit(encoded);
    }

    protected <T extends AlgorithmParameterSpec>
            T engineGetParameterSpec(Class<T> paramSpec)
        throws InvalidParameterSpecException
    {
        if (paramSpec.isAssignableFrom(PBEParameterSpec.class)) {
            return paramSpec.cast(
                new PBEParameterSpec(this.salt, this.iCount, this.cipherParam));
        } else {
            throw new InvalidParameterSpecException
                ("Inappropriate parameter specification");
        }
    }

    protected byte[] engineGetEncoded() throws IOException {
        DerOutputStream out = new DerOutputStream();

        DerOutputStream pBES2_params = new DerOutputStream();

        DerOutputStream keyDerivationFunc = new DerOutputStream();
        keyDerivationFunc.putOID(pkcs5PBKDF2_OID);

        DerOutputStream pBKDF2_params = new DerOutputStream();
        pBKDF2_params.putOctetString(salt); // choice: 'specified OCTET STRING'
        pBKDF2_params.putInteger(iCount);

        if (keysize > 0) {
            pBKDF2_params.putInteger(keysize / 8); // derived key length (in octets)
        }

        DerOutputStream prf = new DerOutputStream();
        // algorithm is id-hmacWith<MD>
        prf.putOID(kdfAlgo_OID);
        // parameters is 'NULL'
        prf.putNull();
        pBKDF2_params.write(DerValue.tag_Sequence, prf);

        keyDerivationFunc.write(DerValue.tag_Sequence, pBKDF2_params);
        pBES2_params.write(DerValue.tag_Sequence, keyDerivationFunc);

        DerOutputStream encryptionScheme = new DerOutputStream();
        // algorithm is id-aes128-CBC or id-aes256-CBC
        encryptionScheme.putOID(cipherAlgo_OID);
        // parameters is 'AES-IV ::= OCTET STRING (SIZE(16))'
        if (cipherParam != null && cipherParam instanceof IvParameterSpec) {
            encryptionScheme.putOctetString(
                ((IvParameterSpec)cipherParam).getIV());
        } else {
            throw new IOException("Wrong parameter type: IV expected");
        }
        pBES2_params.write(DerValue.tag_Sequence, encryptionScheme);

        out.write(DerValue.tag_Sequence, pBES2_params);

        return out.toByteArray();
    }

    protected byte[] engineGetEncoded(String encodingMethod)
        throws IOException
    {
        return engineGetEncoded();
    }

    /*
     * Returns a formatted string describing the parameters.
     *
     * The algorithm name pattern is: "PBEWith<prf>And<encryption>"
     * where <prf> is one of: HmacSHA1, HmacSHA224, HmacSHA256, HmacSHA384,
     * HmacSHA512, HmacSHA512/224, or HmacSHA512/256 and <encryption> is
     * AES with a keysize suffix.
     */
    protected String engineToString() {
        return pbes2AlgorithmName;
    }

    public static final class General extends PBES2Parameters {
        public General() throws NoSuchAlgorithmException {
            super();
        }
    }

    public static final class HmacSHA1AndAES_128 extends PBES2Parameters {
        public HmacSHA1AndAES_128() throws NoSuchAlgorithmException {
            super("PBEWithHmacSHA1AndAES_128");
        }
    }

    public static final class HmacSHA224AndAES_128 extends PBES2Parameters {
        public HmacSHA224AndAES_128() throws NoSuchAlgorithmException {
            super("PBEWithHmacSHA224AndAES_128");
        }
    }

    public static final class HmacSHA256AndAES_128 extends PBES2Parameters {
        public HmacSHA256AndAES_128() throws NoSuchAlgorithmException {
            super("PBEWithHmacSHA256AndAES_128");
        }
    }

    public static final class HmacSHA384AndAES_128 extends PBES2Parameters {
        public HmacSHA384AndAES_128() throws NoSuchAlgorithmException {
            super("PBEWithHmacSHA384AndAES_128");
        }
    }

    public static final class HmacSHA512AndAES_128 extends PBES2Parameters {
        public HmacSHA512AndAES_128() throws NoSuchAlgorithmException {
            super("PBEWithHmacSHA512AndAES_128");
        }
    }

    public static final class HmacSHA512_224AndAES_128 extends PBES2Parameters {
        public HmacSHA512_224AndAES_128() throws NoSuchAlgorithmException {
            super("PBEWithHmacSHA512/224AndAES_128");
        }
    }

    public static final class HmacSHA512_256AndAES_128 extends PBES2Parameters {
        public HmacSHA512_256AndAES_128() throws NoSuchAlgorithmException {
            super("PBEWithHmacSHA512/256AndAES_128");
        }
    }

    public static final class HmacSHA1AndAES_256 extends PBES2Parameters {
        public HmacSHA1AndAES_256() throws NoSuchAlgorithmException {
            super("PBEWithHmacSHA1AndAES_256");
        }
    }

    public static final class HmacSHA224AndAES_256 extends PBES2Parameters {
        public HmacSHA224AndAES_256() throws NoSuchAlgorithmException {
            super("PBEWithHmacSHA224AndAES_256");
        }
    }

    public static final class HmacSHA256AndAES_256 extends PBES2Parameters {
        public HmacSHA256AndAES_256() throws NoSuchAlgorithmException {
            super("PBEWithHmacSHA256AndAES_256");
        }
    }

    public static final class HmacSHA384AndAES_256 extends PBES2Parameters {
        public HmacSHA384AndAES_256() throws NoSuchAlgorithmException {
            super("PBEWithHmacSHA384AndAES_256");
        }
    }

    public static final class HmacSHA512AndAES_256 extends PBES2Parameters {
        public HmacSHA512AndAES_256() throws NoSuchAlgorithmException {
            super("PBEWithHmacSHA512AndAES_256");
        }
    }

    public static final class HmacSHA512_224AndAES_256 extends PBES2Parameters {
        public HmacSHA512_224AndAES_256() throws NoSuchAlgorithmException {
            super("PBEWithHmacSHA512/224AndAES_256");
        }
    }

    public static final class HmacSHA512_256AndAES_256 extends PBES2Parameters {
        public HmacSHA512_256AndAES_256() throws NoSuchAlgorithmException {
            super("PBEWithHmacSHA512/256AndAES_256");
        }
    }
}
