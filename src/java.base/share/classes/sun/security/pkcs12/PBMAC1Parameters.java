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

package sun.security.pkcs12;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import sun.security.util.*;
import sun.security.x509.AlgorithmId;

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
 * See sun.security.util.PBKDF2Parameters.
 *
 * </pre>
 *
 * @since 26
 */
final class PBMAC1Parameters {

    static final ObjectIdentifier pkcs5PBKDF2_OID =
            ObjectIdentifier.of(KnownOIDs.PBKDF2);

    private final String hmacAlgo;
    private final PBKDF2Parameters kdfParams;

    PBMAC1Parameters(byte[] encoded) throws IOException {
        DerValue pBMAC1_params = new DerValue(encoded);
        if (pBMAC1_params.tag != DerValue.tag_Sequence) {
            throw new IOException("PBMAC1 parameter parsing error: "
                    + "not an ASN.1 SEQUENCE tag");
        }
        DerValue[] info = new DerInputStream(pBMAC1_params.toByteArray())
                .getSequence(2);
        if (info.length != 2) {
            throw new IOException("PBMAC1 parameter parsing error: "
                + "expected length not 2");
        }
        ObjectIdentifier OID = info[1].data.getOID();
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
        // Hmac function used to compute the MAC
        this.hmacAlgo = o.stdName();

        //DerValue kdf = pBMAC1_params.data.getDerValue();
        DerValue kdf = info[0];

        if (!pkcs5PBKDF2_OID.equals(kdf.data.getOID())) {
            throw new IOException("PBKDF2 parameter parsing error: "
                + "expecting the object identifier for PBKDF2");
        }
        if (kdf.tag != DerValue.tag_Sequence) {
            throw new IOException("PBKDF2 parameter parsing error: "
                + "not an ASN.1 SEQUENCE tag");
        }
        DerValue pBKDF2_params = kdf.data.getDerValue();

        this.kdfParams = new PBKDF2Parameters(pBKDF2_params);
    }

    /*
     * Encode PBMAC1 parameters from components.
     */
    static byte[] encode(byte[] salt, int iterationCount, int keyLength,
            String kdfHmac, String hmac) throws NoSuchAlgorithmException {

        DerOutputStream out = new DerOutputStream();

        // keyDerivationFunc AlgorithmIdentifier {{PBMAC1-KDFs}}
        out.writeBytes(PBKDF2Parameters.encode(salt,
                iterationCount, keyLength, kdfHmac));

        // messageAuthScheme AlgorithmIdentifier {{PBMAC1-MACs}}
        out.write(AlgorithmId.get(hmac));
        return new DerOutputStream().write(DerValue.tag_Sequence, out)
                .toByteArray();
    }

    PBKDF2Parameters getKdfParams() {
        return this.kdfParams;
    }

    String getHmac() {
        return this.hmacAlgo;
    }
}
