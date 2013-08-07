/*
 * Copyright (c) 2006, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6405536 6414980
 * @summary Basic consistency test for all curves using ECDSA and ECDH
 * @author Andreas Sterbenz
 * @library ..
 * @compile -XDignore.symbol.file TestCurves.java
 * @run main TestCurves
 */

import java.util.*;

import java.security.*;
import java.security.spec.*;

import javax.crypto.*;

public class TestCurves extends PKCS11Test {

    public static void main(String[] args) throws Exception {
        main(new TestCurves());
    }

    public void main(Provider p) throws Exception {
        if (p.getService("KeyAgreement", "ECDH") == null) {
            System.out.println("Not supported by provider, skipping");
            return;
        }

        if (isNSS(p) && getNSSVersion() >= 3.11 && getNSSVersion() < 3.12) {
            System.out.println("NSS 3.11 has a DER issue that recent " +
                    "version do not.");
            return;
        }

        Random random = new Random();
        byte[] data = new byte[2048];
        random.nextBytes(data);

        Vector<ECParameterSpec> curves = getKnownCurves(p);

        for (ECParameterSpec params : curves) {
            System.out.println("Testing " + params + "...");
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", p);
            kpg.initialize(params);
            KeyPair kp1, kp2;

            try {
                kp1 = kpg.generateKeyPair();
                kp2 = kpg.generateKeyPair();
            } catch (Exception e) {
                // The root cause of the exception might be NSS not having
                // "ECC Extended" support curves.  If so, we can ignore it.
                if (e instanceof java.security.ProviderException) {
                    Throwable t = e.getCause();
                    if (t instanceof
                            sun.security.pkcs11.wrapper.PKCS11Exception &&
                            t.getMessage().equals("CKR_DOMAIN_PARAMS_INVALID") &&
                            isNSS(p) && (getNSSECC() == ECCState.Basic) &&
                            (!params.toString().startsWith("secp256r1") &&
                            !params.toString().startsWith("secp384r1") &&
                            !params.toString().startsWith("secp521r1"))) {
                        System.out.println("NSS Basic ECC.  Failure expected");
                        continue;
                    }
                }

                throw e;
            }

            testSigning(p, "SHA1withECDSA", data, kp1, kp2);
            testSigning(p, "SHA224withECDSA", data, kp1, kp2);
            testSigning(p, "SHA256withECDSA", data, kp1, kp2);
            testSigning(p, "SHA384withECDSA", data, kp1, kp2);
            testSigning(p, "SHA512withECDSA", data, kp1, kp2);
            // System.out.println();

            KeyAgreement ka1 = KeyAgreement.getInstance("ECDH", p);
            ka1.init(kp1.getPrivate());
            ka1.doPhase(kp2.getPublic(), true);
            byte[] secret1 = ka1.generateSecret();

            KeyAgreement ka2 = KeyAgreement.getInstance("ECDH", p);
            ka2.init(kp2.getPrivate());
            ka2.doPhase(kp1.getPublic(), true);
            byte[] secret2 = ka2.generateSecret();

            if (Arrays.equals(secret1, secret2) == false) {
                throw new Exception("Secrets do not match");
            }
        }

        System.out.println("OK");
    }

    private static Vector<ECParameterSpec>
            getKnownCurves(Provider p) throws Exception {

        int index;
        int begin;
        int end;
        String curve;
        Vector<ECParameterSpec> results = new Vector<ECParameterSpec>();
        // Get Curves to test from SunEC.
        String kcProp = Security.getProvider("SunEC").
                getProperty("AlgorithmParameters.EC SupportedCurves");

        if (kcProp == null) {
            throw new RuntimeException(
            "\"AlgorithmParameters.EC SupportedCurves property\" not found");
        }

        index = 0;
        for (;;) {
            // Each set of curve names is enclosed with brackets.
            begin = kcProp.indexOf('[', index);
            end = kcProp.indexOf(']', index);
            if (begin == -1 || end == -1) {
                break;
            }

            /*
             * Each name is separated by a comma.
             * Just get the first name in the set.
             */
            index = end + 1;
            begin++;
            end = kcProp.indexOf(',', begin);
            if (end == -1) {
                // Only one name in the set.
                end = index -1;
            }

            curve = kcProp.substring(begin, end);

            results.add(getECParameterSpec(p, curve));
        }

        if (results.size() == 0) {
            throw new RuntimeException("No supported EC curves found");
        }

        return results;
    }

    private static ECParameterSpec getECParameterSpec(Provider p, String name)
            throws Exception {

        AlgorithmParameters parameters =
            AlgorithmParameters.getInstance("EC", p);

        parameters.init(new ECGenParameterSpec(name));

        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    private static void testSigning(Provider p, String algorithm,
            byte[] data, KeyPair kp1, KeyPair kp2) throws Exception {
        // System.out.print("  " + algorithm);
        Signature s = Signature.getInstance(algorithm, p);
        s.initSign(kp1.getPrivate());
        s.update(data);
        byte[] sig = s.sign();

        s = Signature.getInstance(algorithm, p);
        s.initVerify(kp1.getPublic());
        s.update(data);
        boolean r = s.verify(sig);
        if (r == false) {
            throw new Exception("Signature did not verify");
        }

        s.initVerify(kp2.getPublic());
        s.update(data);
        r = s.verify(sig);
        if (r) {
            throw new Exception("Signature should not verify");
        }
    }
}
