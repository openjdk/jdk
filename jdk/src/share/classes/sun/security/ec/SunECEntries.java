/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
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

    static void putEntries(Map<Object, Object> map) {

        /*
         * Signature engines
         */
        map.put("Signature.NONEwithECDSA",
            "sun.security.ec.ECDSASignature$Raw");
        map.put("Signature.SHA1withECDSA",
            "sun.security.ec.ECDSASignature$SHA1");
        map.put("Signature.SHA256withECDSA",
            "sun.security.ec.ECDSASignature$SHA256");
        map.put("Signature.SHA384withECDSA",
            "sun.security.ec.ECDSASignature$SHA384");
        map.put("Signature.SHA512withECDSA",
            "sun.security.ec.ECDSASignature$SHA512");

        String ecKeyClasses = "java.security.interfaces.ECPublicKey" +
                "|java.security.interfaces.ECPrivateKey";
        map.put("Signature.NONEwithECDSA SupportedKeyClasses", ecKeyClasses);
        map.put("Signature.SHA1withECDSA SupportedKeyClasses", ecKeyClasses);
        map.put("Signature.SHA256withECDSA SupportedKeyClasses", ecKeyClasses);
        map.put("Signature.SHA384withECDSA SupportedKeyClasses", ecKeyClasses);
        map.put("Signature.SHA512withECDSA SupportedKeyClasses", ecKeyClasses);

        /*
         *  Key Pair Generator engine
         */
        map.put("KeyPairGenerator.EC", "sun.security.ec.ECKeyPairGenerator");
        map.put("Alg.Alias.KeyPairGenerator.EllipticCurve", "EC");

        /*
         *  Key Factory engine
         */
        map.put("KeyFactory.EC", "sun.security.ec.ECKeyFactory");
        map.put("Alg.Alias.KeyFactory.EllipticCurve", "EC");

        /*
         * Algorithm Parameter engine
         */
        map.put("AlgorithmParameters.EC", "sun.security.ec.ECParameters");
        map.put("Alg.Alias.AlgorithmParameters.EllipticCurve", "EC");

        /*
         * Key Agreement engine
         */
        map.put("KeyAgreement.ECDH", "sun.security.ec.ECDHKeyAgreement");
        map.put("KeyAgreement.ECDH SupportedKeyClasses", ecKeyClasses);

        /*
         * Key sizes
         */
        map.put("Signature.SHA1withECDSA KeySize", "256");
        map.put("KeyPairGenerator.EC KeySize", "256");
        map.put("AlgorithmParameterGenerator.ECDSA KeySize", "256");

        /*
         * Implementation type: software or hardware
         */
        map.put("Signature.NONEwithECDSA ImplementedIn", "Software");
        map.put("Signature.SHA1withECDSA ImplementedIn", "Software");
        map.put("Signature.SHA256withECDSA ImplementedIn", "Software");
        map.put("Signature.SHA384withECDSA ImplementedIn", "Software");
        map.put("Signature.SHA512withECDSA ImplementedIn", "Software");
        map.put("KeyPairGenerator.EC ImplementedIn", "Software");
        map.put("KeyFactory.EC ImplementedIn", "Software");
        map.put("KeyAgreement.ECDH ImplementedIn", "Software");
        map.put("AlgorithmParameters.EC ImplementedIn", "Software");
    }
}
