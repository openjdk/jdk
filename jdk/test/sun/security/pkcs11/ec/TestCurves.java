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

        // Check if this is sparc for later failure avoidance.
        boolean sparc = false;
        if (System.getProperty("os.arch").equals("sparcv9")) {
            sparc = true;
            System.out.println("This is a sparcv9");
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

            kp1 = kpg.generateKeyPair();
            kp2 = kpg.generateKeyPair();

            testSigning(p, "SHA1withECDSA", data, kp1, kp2);
            // Check because Solaris ncp driver does not support these but
            // Solaris metaslot causes them to be run.
            try {
                testSigning(p, "SHA224withECDSA", data, kp1, kp2);
                testSigning(p, "SHA256withECDSA", data, kp1, kp2);
                testSigning(p, "SHA384withECDSA", data, kp1, kp2);
                testSigning(p, "SHA512withECDSA", data, kp1, kp2);
            } catch (ProviderException e) {
                if (sparc) {
                    Throwable t = e.getCause();
                    if (t instanceof sun.security.pkcs11.wrapper.PKCS11Exception &&
                        t.getMessage().equals("CKR_ATTRIBUTE_VALUE_INVALID")) {
                        System.out.print("-Failure not uncommon.  Probably pre-T4.");
                    } else {
                        throw e;
                    }
                } else {
                    throw e;
                }
            }
            System.out.println();

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

    private static void testSigning(Provider p, String algorithm,
            byte[] data, KeyPair kp1, KeyPair kp2) throws Exception {
        System.out.print("  " + algorithm);
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
