/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.PrivateKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.interfaces.DHPrivateKey;
import sun.security.util.SecurityProviderConstants;
import sun.security.provider.ParameterCache;

/**
 * @test
 * @bug 8295425
 * @modules java.base/sun.security.provider java.base/sun.security.util
 * @library /test/lib ..
 * @run main TestDefaultDHPrivateExpSize
 * @summary This test verifies the DH private exponent size for SunPKCS11
 *         provider.
 */

public class TestDefaultDHPrivateExpSize extends PKCS11Test {

    @Override
    public void main(Provider p) throws Exception {
        System.out.println("Testing " + p.getName());

        if (p.getService("KeyPairGenerator", "DH") == null) {
            System.out.println("Skip, no support for DH KeyPairGenerator");
            return;
        }

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("DH", p);
        // try common DH key sizes with built-in primes
        int[] cachedSizes = { 2048, 3072, 4096, 6144, 8192 };
        for (int ks : cachedSizes) {
            // use keysize which uses JDK default parameters w/ JDK
            // default lSize
            kpg.initialize(ks);
            int expectedL = SecurityProviderConstants.getDefDHPrivateExpSize
                    (ParameterCache.getCachedDHParameterSpec(ks));
            System.out.println("Test against built-in DH " + ks +
                    "-bit parameters, expectedL = " + expectedL);
            DHParameterSpec spec = generateAndCheck(kpg, ks, expectedL);

            // use custom DH parameters w/o lSize
            DHParameterSpec spec2 = new DHParameterSpec(spec.getP(),
                    spec.getG());
            kpg.initialize(spec2);
            System.out.println("Test against user DH " + ks +
                    "-bit parameters, expectedL = " + spec2.getL());

            generateAndCheck(kpg, ks, spec2.getL());

            // use custom DH parameters w/ lSize
            expectedL += 2;
            spec2 = new DHParameterSpec(spec.getP(), spec.getG(), expectedL);
            kpg.initialize(spec2);
            System.out.println("Test against user DH " + ks +
                    "-bit parameters, expectedL = " + spec2.getL());
            generateAndCheck(kpg, ks, expectedL);
        }
    }

    // initialize the specified 'kpg' with 'initParam', then check
    // the parameters associated with the generated key against 'initParam'
    // and return the actual private exponent length.
    private static DHParameterSpec generateAndCheck(KeyPairGenerator kpg,
            int expKeySize, int expL) {

        DHPrivateKey dhPriv = (DHPrivateKey) kpg.generateKeyPair().getPrivate();
        DHParameterSpec generated = dhPriv.getParams();
        // check the params associated with the key as that's what we
        // have control over
        if ((generated.getP().bitLength() != expKeySize) ||
                generated.getL()!= expL) {
            new RuntimeException("Error: size check failed, got " +
                    generated.getP().bitLength() + " and " + generated.getL());
        }

        // Known NSS Issue/limitation: NSS ignores the supplied L value when
        // generating the DH private key
        int actualL = dhPriv.getX().bitLength();
        System.out.println("INFO: actual L = " + actualL);
        /*
        if (expLSize != 0 && actualL != expLSize) {
            throw new RuntimeException("ERROR: actual L mismatches, got "
                    + actualL + " vs expect " + expLSize);
        }
        */
        return generated;
    }

    public static void main(String[] args) throws Exception {
        main(new TestDefaultDHPrivateExpSize(), args);
    }
}
