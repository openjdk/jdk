/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.security.AlgorithmParametersSpi;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import javax.crypto.spec.PBMAC1ParameterSpec;

import sun.security.util.*;

/**
 * This class implements the parameter set used with password-based
 * mac scheme 1 (PBMAC1), which is defined in PKCS#5 as follows:
 *
 * <pre>
 * -- PBMAC1
 *
 * PBMAC1Algorithms ALGORITHM-IDENTIFIER ::=
 *   { {PBMAC1-params IDENTIFIED BY id-PBMAC1}, ...}
 *
 * id-PBMAC1 OBJECT IDENTIFIER ::= {pkcs-5 14}
 *
 * PBMAC1-params ::= SEQUENCE {
 *   keyDerivationFunc AlgorithmIdentifier {{PBMAC1-KDFs}},
 *   messageAuthScheme AlgorithmIdentifier {{PBMAC1-MACs}} }
 *
 * PBMAC1-KDFs ALGORITHM-IDENTIFIER ::=
 *   { {PBKDF2-params IDENTIFIED BY id-PBKDF2}, ... }
 *
 * PBMAC1-MACs ALGORITHM-IDENTIFIER ::= { ... }
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
 * </pre>
 */
abstract class PBMAC1Parameters extends AlgorithmParametersSpi {

    private static final ObjectIdentifier pkcs5PBKDF2_OID =
            ObjectIdentifier.of(KnownOIDs.PBKDF2WithHmacSHA1);
    private static final ObjectIdentifier pkcs5PBMAC1_OID =
            ObjectIdentifier.of(KnownOIDs.PBMAC1);

    // the PBMAC1 algorithm name
    private String pbmac1AlgorithmName = null;

    // the salt
    private byte[] salt = null;

    // the iteration count
    private int iCount = 0;

    // the Hmac function (first one)
    private String kdfHmac = null;

    // the Hmac function (second one)
    private String Hmac = null;

    // the key derivation function (default is HmacSHA1)
    private ObjectIdentifier kdfAlgo_OID =
            ObjectIdentifier.of(KnownOIDs.HmacSHA1);

    // the cipher keysize (in bits)
    private int keysize = -1;

    PBMAC1Parameters() {
        // KDF, encryption & keysize values are set later, in engineInit(byte[])
    }

    PBMAC1Parameters(String pbmac1AlgorithmName) {
        // TBD
    }

    protected void engineInit(AlgorithmParameterSpec paramSpec)
        throws InvalidParameterSpecException
    {
       if (!(paramSpec instanceof PBMAC1ParameterSpec)) {
           throw new InvalidParameterSpecException
               ("Inappropriate parameter specification");
       }
       this.salt = ((PBMAC1ParameterSpec)paramSpec).getSalt().clone();
       this.iCount = ((PBMAC1ParameterSpec)paramSpec).getIterationCount();
    }

    @SuppressWarnings("deprecation")
    protected void engineInit(byte[] encoded)
        throws IOException
    {
        DerValue pBMAC1_params = new DerValue(encoded);
        if (pBMAC1_params.tag != DerValue.tag_Sequence) {
            throw new IOException("PMAC1 parameter parsing error: "
                + "not an ASN.1 SEQUENCE tag");
        }
        DerValue[] Info = (new DerInputStream(pBMAC1_params.toByteArray()))
                .getSequence(2);
        if (Info.length != 2) {
            throw new IOException("PMAC1 parameter parsing error: "
                + "expected length not 2");
        }
        ObjectIdentifier OID = Info[1].data.getOID();
            KnownOIDs o = KnownOIDs.findMatch(OID.toString());
            if (o == null || (!o.stdName().equals("HmacSHA1") &&
                    !o.stdName().equals("HmacSHA224") &&
                    !o.stdName().equals("HmacSHA256") &&
                    !o.stdName().equals("HmacSHA384") &&
                    !o.stdName().equals("HmacSHA512") &&
                    !o.stdName().equals("HmacSHA512/224") &&
                    !o.stdName().equals("HmacSHA512/256"))) {
                throw new IOException("PBMAC1 parameter parsing error: "
                        + "expecting the object identifier for a HmacSHA key "
                        + "derivation function");
            }
            this.Hmac = o.stdName();

        DerValue kdf = pBMAC1_params.data.getDerValue();

        String kdfAlgo = parseKDF(kdf);
        this.kdfHmac = kdfAlgo;

        if (pBMAC1_params.tag != DerValue.tag_Sequence) {
            throw new IOException("PBMAC1 parameter parsing error: "
                + "not an ASN.1 SEQUENCE tag");
        }

        this.pbmac1AlgorithmName = "PBMAC1With" + kdfAlgo + "And" +Hmac;
    }

    @SuppressWarnings("deprecation")
    private String parseKDF(DerValue keyDerivationFunc) throws IOException {

        if (!pkcs5PBKDF2_OID.equals(keyDerivationFunc.data.getOID())) {
            throw new IOException("PBMAC1 parameter parsing error: "
                + "expecting the object identifier for PBKDF2");
        }
        if (keyDerivationFunc.tag != DerValue.tag_Sequence) {
            throw new IOException("PBMAC1 parameter parsing error: "
                + "not an ASN.1 SEQUENCE tag");
        }
        DerValue pBKDF2_params = keyDerivationFunc.data.getDerValue();
        if (pBKDF2_params.tag != DerValue.tag_Sequence) {
            throw new IOException("PBMAC1 parameter parsing error: "
                + "not an ASN.1 SEQUENCE tag");
        }
        DerValue specified = pBKDF2_params.data.getDerValue();
        // the 'specified' ASN.1 CHOICE for 'salt' is supported
        if (specified.tag == DerValue.tag_OctetString) {
            salt = specified.getOctetString();
        } else {
            // the 'otherSource' ASN.1 CHOICE for 'salt' is not supported
            throw new IOException("PBMAC1 parameter parsing error: "
                + "not an ASN.1 OCTET STRING tag");
        }
        iCount = pBKDF2_params.data.getInteger();

        // keyLength INTEGER (1..MAX) OPTIONAL,
        var ksDer = pBKDF2_params.data.getOptional(DerValue.tag_Integer);
        if (ksDer.isPresent()) {
            keysize = ksDer.get().getInteger() * 8; // keysize (in bits)
        } else {
            throw new IOException("PBMAC1 parameter parsing error: "
                + "missing keyLength");
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
                throw new IOException("PBMAC1 parameter parsing error: "
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

    protected void engineInit(byte[] encoded, String decodingMethod)
        throws IOException
    {
        engineInit(encoded);
    }

    protected <T extends AlgorithmParameterSpec>
            T engineGetParameterSpec(Class<T> paramSpec)
        throws InvalidParameterSpecException
    {
        if (paramSpec.isAssignableFrom(PBMAC1ParameterSpec.class)) {
            return paramSpec.cast(
                new PBMAC1ParameterSpec(this.salt, this.iCount, this.kdfHmac,
                        this.Hmac, this.keysize));
        } else {
            throw new InvalidParameterSpecException
                ("Inappropriate parameter specification");
        }
    }

    protected byte[] engineGetEncoded() throws IOException {
        DerOutputStream out = new DerOutputStream();

        DerOutputStream pBMAC1_params = new DerOutputStream();

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
        pBMAC1_params.write(DerValue.tag_Sequence, keyDerivationFunc);

        out.write(DerValue.tag_Sequence, pBMAC1_params);

        return out.toByteArray();
    }

    protected byte[] engineGetEncoded(String encodingMethod)
        throws IOException
    {
        return engineGetEncoded();
    }

    /*
     * Returns a formatted string describing the parameters.
     */
    protected String engineToString() {
        return pbmac1AlgorithmName;
    }

    public static final class General extends PBMAC1Parameters {
        public General() throws NoSuchAlgorithmException {
            super();
        }
    }
}
