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

package sun.security.util;

import java.io.IOException;
import java.security.AlgorithmParametersSpi;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;

/**
 * This class implements the parameter set used with password-based
 * mac scheme 1 (PBKDF2), which is defined in PKCS#5 as follows:
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
 */
public class PBKDF2Parameters {

    private static final ObjectIdentifier pkcs5PBKDF2_OID =
            ObjectIdentifier.of(KnownOIDs.PBKDF2WithHmacSHA1);

    // the PBMAC1 algorithm name
    private String pbmac1AlgorithmName = null;

    // the salt
    private byte[] salt = null;

    // the iteration count
    private int iCount = 0;

    // the key derivation function (default is HmacSHA1)
    private ObjectIdentifier kdfAlgo_OID =
            ObjectIdentifier.of(KnownOIDs.HmacSHA1);

    // the keysize (in bits)
    private int keysize = -1;

    @SuppressWarnings("deprecation")
    public String parseKDF(DerValue keyDerivationFunc) throws IOException {

        if (!pkcs5PBKDF2_OID.equals(keyDerivationFunc.data.getOID())) {
            throw new IOException("PBKDF2 parameter parsing error: "
                + "expecting the object identifier for PBKDF2");
        }
        if (keyDerivationFunc.tag != DerValue.tag_Sequence) {
            throw new IOException("PBKDF2 parameter parsing error: "
                + "not an ASN.1 SEQUENCE tag");
        }
        DerValue pBKDF2_params = keyDerivationFunc.data.getDerValue();
        if (pBKDF2_params.tag != DerValue.tag_Sequence) {
            throw new IOException("PBKDF2 parameter parsing error: "
                + "not an ASN.1 SEQUENCE tag");
        }
        DerValue specified = pBKDF2_params.data.getDerValue();
        // the 'specified' ASN.1 CHOICE for 'salt' is supported
        if (specified.tag == DerValue.tag_OctetString) {
            salt = specified.getOctetString();
        } else {
            // the 'otherSource' ASN.1 CHOICE for 'salt' is not supported
            throw new IOException("PBKDF2 parameter parsing error: "
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
                throw new IOException("PBKDF2 parameter parsing error: "
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

    /**
     * Returns the salt.
     *
     * @return the salt. Returns a new array
     * each time this method is called.
     */
    public byte[] getSalt() {
        return this.salt.clone(); 
    }
     
    /**
     * Returns the iteration count.
     *
     * @return the iteration count
     */
    public int getIterationCount() {
        return this.iCount;
    }

    /**
     * Returns size of key generated by PBKDF2.
     *
     * @return size of key generated by PBKDF2.
     */
    public int getKeyLength() {
        return this.keysize;
    }
}
