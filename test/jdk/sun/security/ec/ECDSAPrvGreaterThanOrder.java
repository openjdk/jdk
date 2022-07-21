/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


/**
 * @test
 * @bug 8272385
 * @summary Enforce ECPrivateKey d value to be in the range [1, n-1] for SunEC provider
 * @run main ECDSAPrvGreaterThanOrder
 */

import javax.crypto.KeyAgreement;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.util.List;

public class ECDSAPrvGreaterThanOrder {

    private static final List<String> CURVE_NAMES =
            List.of("secp256r1", "secp384r1", "secp521r1");

    public static void main(String[] args) throws Exception {
        for (String curveName : CURVE_NAMES) {
            ECPrivateKey ecPrivKey = makePrivateKey(curveName);

            // Check using the private key for creating a digital signature
            Signature sig = null;
            KeyAgreement ka = null;
            try {
                sig = Signature.getInstance("SHA256withECDSA",
                        "SunEC");
                sig.initSign(ecPrivKey);
                throw new RuntimeException("Expected exception for " +
                        "ECDSA/" + sig.getAlgorithm() + "/" + curveName +
                        " not thrown.");
            } catch (InvalidKeyException ike) {
                // We are expecting this to be caught
                System.out.println("Caught expected exception for " +
                        "ECDSA/" + sig.getAlgorithm() + "/" + curveName +
                        ": " + ike);
            }

            // Next, try starting a ECDH operation
            try {
                ka = KeyAgreement.getInstance("ECDH", "SunEC");
                ka.init(ecPrivKey);
                throw new RuntimeException("Expected exception for ECDH/" +
                        curveName + " not thrown.");
            } catch (InvalidKeyException ike) {
                // We are expecting this to be caught
                System.out.println("Caught expected exception for ECDH/" +
                        curveName + ": " + ike);
            }
        }
    }

    private static ECPrivateKey makePrivateKey(String curveName) {
        try {
            System.out.println("Creating private key for curve " + curveName);

            AlgorithmParameters params = AlgorithmParameters.getInstance(
                    "EC", "SunEC");
            params.init(new ECGenParameterSpec(curveName));
            ECParameterSpec ecParameters = params.getParameterSpec(
                    ECParameterSpec.class);
            BigInteger order = ecParameters.getOrder(); // the N value
            System.out.println("Order is: " + order);

            // Create a private key value (d) that is outside the range
            // [1, N-1]
            BigInteger dVal = order.add(BigInteger.TWO);
            System.out.println("Modified d Value is: " + dVal);

            // Create the private key
            KeyFactory kf = KeyFactory.getInstance("EC", "SunEC");
            return (ECPrivateKey)kf.generatePrivate(
                    new ECPrivateKeySpec(dVal, ecParameters));
        } catch (GeneralSecurityException gse) {
            throw new RuntimeException("Unexpected error creating private key",
                    gse);
        }
    }
}
