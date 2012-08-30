/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ec;

import java.util.Map;

/**
 * Defines the entries of the SunEC provider.
 *
 * @since 1.7
 */
final class SunECEntries {

    private SunECEntries() {
        // empty
    }

    static void putEntries(Map<Object, Object> map,
        boolean useFullImplementation) {

        /*
         *  Key Factory engine
         */
        map.put("KeyFactory.EC", "sun.security.ec.ECKeyFactory");
        map.put("Alg.Alias.KeyFactory.EllipticCurve", "EC");

        map.put("KeyFactory.EC ImplementedIn", "Software");

        /*
         * Algorithm Parameter engine
         */
        map.put("AlgorithmParameters.EC", "sun.security.ec.ECParameters");
        map.put("Alg.Alias.AlgorithmParameters.EllipticCurve", "EC");
        map.put("Alg.Alias.AlgorithmParameters.1.2.840.10045.2.1", "EC");

        map.put("AlgorithmParameters.EC KeySize", "256");

        map.put("AlgorithmParameters.EC ImplementedIn", "Software");

        map.put("AlgorithmParameters.EC SupportedCurves",

            // A list comprising lists of curve names and object identifiers.
            // '[' ( <curve-name> ',' )+ <curve-object-identifier> ']' '|'

            // SEC 2 prime curves
            "[secp112r1,1.3.132.0.6]|" +
            "[secp112r2,1.3.132.0.7]|" +
            "[secp128r1,1.3.132.0.28]|" +
            "[secp128r2,1.3.132.0.29]|" +
            "[secp160k1,1.3.132.0.9]|" +
            "[secp160r1,1.3.132.0.8]|" +
            "[secp160r2,1.3.132.0.30]|" +
            "[secp192k1,1.3.132.0.31]|" +
            "[secp192r1,NIST P-192,X9.62 prime192v1,1.2.840.10045.3.1.1]|" +
            "[secp224k1,1.3.132.0.32]|" +
            "[secp224r1,NIST P-224,1.3.132.0.33]|" +
            "[secp256k1,1.3.132.0.10]|" +
            "[secp256r1,NIST P-256,X9.62 prime256v1,1.2.840.10045.3.1.7]|" +
            "[secp384r1,NIST P-384,1.3.132.0.34]|" +
            "[secp521r1,NIST P-521,1.3.132.0.35]|" +

            // ANSI X9.62 prime curves
            "[X9.62 prime192v2,1.2.840.10045.3.1.2]|" +
            "[X9.62 prime192v3,1.2.840.10045.3.1.3]|" +
            "[X9.62 prime239v1,1.2.840.10045.3.1.4]|" +
            "[X9.62 prime239v2,1.2.840.10045.3.1.5]|" +
            "[X9.62 prime239v3,1.2.840.10045.3.1.6]|" +

            // SEC 2 binary curves
            "[sect113r1,1.3.132.0.4]|" +
            "[sect113r2,1.3.132.0.5]|" +
            "[sect131r1,1.3.132.0.22]|" +
            "[sect131r2,1.3.132.0.23]|" +
            "[sect163k1,NIST K-163,1.3.132.0.1]|" +
            "[sect163r1,1.3.132.0.2]|" +
            "[sect163r2,NIST B-163,1.3.132.0.15]|" +
            "[sect193r1,1.3.132.0.24]|" +
            "[sect193r2,1.3.132.0.25]|" +
            "[sect233k1,NIST K-233,1.3.132.0.26]|" +
            "[sect233r1,NIST B-233,1.3.132.0.27]|" +
            "[sect239k1,1.3.132.0.3]|" +
            "[sect283k1,NIST K-283,1.3.132.0.16]|" +
            "[sect283r1,NIST B-283,1.3.132.0.17]|" +
            "[sect409k1,NIST K-409,1.3.132.0.36]|" +
            "[sect409r1,NIST B-409,1.3.132.0.37]|" +
            "[sect571k1,NIST K-571,1.3.132.0.38]|" +
            "[sect571r1,NIST B-571,1.3.132.0.39]|" +

            // ANSI X9.62 binary curves
            "[X9.62 c2tnb191v1,1.2.840.10045.3.0.5]|" +
            "[X9.62 c2tnb191v2,1.2.840.10045.3.0.6]|" +
            "[X9.62 c2tnb191v3,1.2.840.10045.3.0.7]|" +
            "[X9.62 c2tnb239v1,1.2.840.10045.3.0.11]|" +
            "[X9.62 c2tnb239v2,1.2.840.10045.3.0.12]|" +
            "[X9.62 c2tnb239v3,1.2.840.10045.3.0.13]|" +
            "[X9.62 c2tnb359v1,1.2.840.10045.3.0.18]|" +
            "[X9.62 c2tnb431r1,1.2.840.10045.3.0.20]");

        /*
         * Register the algorithms below only when the full ECC implementation
         * is available
         */
        if (!useFullImplementation) {
            return;
        }

        /*
         * Signature engines
         */
        map.put("Signature.NONEwithECDSA",
            "sun.security.ec.ECDSASignature$Raw");
        map.put("Signature.SHA1withECDSA",
            "sun.security.ec.ECDSASignature$SHA1");
        map.put("Signature.SHA224withECDSA",
            "sun.security.ec.ECDSASignature$SHA224");
        map.put("Alg.Alias.Signature.OID.1.2.840.10045.4.3.1", "SHA224withECDSA");
        map.put("Alg.Alias.Signature.1.2.840.10045.4.3.1", "SHA224withECDSA");

        map.put("Signature.SHA256withECDSA",
            "sun.security.ec.ECDSASignature$SHA256");
        map.put("Alg.Alias.Signature.OID.1.2.840.10045.4.3.2", "SHA256withECDSA");
        map.put("Alg.Alias.Signature.1.2.840.10045.4.3.2", "SHA256withECDSA");

        map.put("Signature.SHA384withECDSA",
            "sun.security.ec.ECDSASignature$SHA384");
        map.put("Alg.Alias.Signature.OID.1.2.840.10045.4.3.3", "SHA384withECDSA");
        map.put("Alg.Alias.Signature.1.2.840.10045.4.3.3", "SHA384withECDSA");

        map.put("Signature.SHA512withECDSA",
            "sun.security.ec.ECDSASignature$SHA512");
        map.put("Alg.Alias.Signature.OID.1.2.840.10045.4.3.4", "SHA512withECDSA");
        map.put("Alg.Alias.Signature.1.2.840.10045.4.3.4", "SHA512withECDSA");

        String ecKeyClasses = "java.security.interfaces.ECPublicKey" +
                "|java.security.interfaces.ECPrivateKey";
        map.put("Signature.NONEwithECDSA SupportedKeyClasses", ecKeyClasses);
        map.put("Signature.SHA1withECDSA SupportedKeyClasses", ecKeyClasses);
        map.put("Signature.SHA224withECDSA SupportedKeyClasses", ecKeyClasses);
        map.put("Signature.SHA256withECDSA SupportedKeyClasses", ecKeyClasses);
        map.put("Signature.SHA384withECDSA SupportedKeyClasses", ecKeyClasses);
        map.put("Signature.SHA512withECDSA SupportedKeyClasses", ecKeyClasses);

        map.put("Signature.SHA1withECDSA KeySize", "256");

        map.put("Signature.NONEwithECDSA ImplementedIn", "Software");
        map.put("Signature.SHA1withECDSA ImplementedIn", "Software");
        map.put("Signature.SHA224withECDSA ImplementedIn", "Software");
        map.put("Signature.SHA256withECDSA ImplementedIn", "Software");
        map.put("Signature.SHA384withECDSA ImplementedIn", "Software");
        map.put("Signature.SHA512withECDSA ImplementedIn", "Software");

        /*
         *  Key Pair Generator engine
         */
        map.put("KeyPairGenerator.EC", "sun.security.ec.ECKeyPairGenerator");
        map.put("Alg.Alias.KeyPairGenerator.EllipticCurve", "EC");

        map.put("KeyPairGenerator.EC KeySize", "256");

        map.put("KeyPairGenerator.EC ImplementedIn", "Software");

        /*
         * Key Agreement engine
         */
        map.put("KeyAgreement.ECDH", "sun.security.ec.ECDHKeyAgreement");

        map.put("KeyAgreement.ECDH SupportedKeyClasses", ecKeyClasses);

        map.put("KeyAgreement.ECDH ImplementedIn", "Software");
    }
}
