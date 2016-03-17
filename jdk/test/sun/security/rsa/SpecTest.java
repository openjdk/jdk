/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAKeyGenParameterSpec;

/**
 * @test
 * @bug 8044199
 * @key intermittent
 * @summary Check same KeyPair's private key and public key have same modulus.
 *  also check public key's public exponent equals to given spec's public
 *  exponent.
 * @run main SpecTest 512
 * @run main SpecTest 768
 * @run main SpecTest 1024
 * @run main SpecTest 2048
 * @run main/timeout=240 SpecTest 4096
 * @run main/timeout=240 SpecTest 5120
 */
public class SpecTest {
    /**
     * ALGORITHM name, fixed as RSA.
     */
    private static final String KEYALG = "RSA";

    /**
     * JDK default RSA Provider.
     */
    private static final String PROVIDER = "SunRsaSign";

    /**
     *
     * @param kpair test key pair
     * @param pubExponent expected public exponent.
     * @return true if test passed. false if test failed.
     */
    private static boolean specTest(KeyPair kpair, BigInteger pubExponent) {
        boolean passed = true;
        RSAPrivateKey priv = (RSAPrivateKey) kpair.getPrivate();
        RSAPublicKey pub = (RSAPublicKey) kpair.getPublic();

        // test the getModulus method
        if ((priv instanceof RSAKey) && (pub instanceof RSAKey)) {
            if (!priv.getModulus().equals(pub.getModulus())) {
                System.err.println("priv.getModulus() = " + priv.getModulus());
                System.err.println("pub.getModulus() = " + pub.getModulus());
                passed = false;
            }

            if (!pubExponent.equals(pub.getPublicExponent())) {
                System.err.println("pubExponent = " + pubExponent);
                System.err.println("pub.getPublicExponent() = "
                        + pub.getPublicExponent());
                passed = false;
            }
        }
        return passed;
    }

    public static void main(String[] args) {
        int failCount = 0;

        // Test key size.
        int size = Integer.parseInt(args[0]);

        try {
            KeyPairGenerator kpg1 = KeyPairGenerator.getInstance(KEYALG, PROVIDER);
            kpg1.initialize(new RSAKeyGenParameterSpec(size,
                    RSAKeyGenParameterSpec.F4));
            if (!specTest(kpg1.generateKeyPair(),
                    RSAKeyGenParameterSpec.F4)) {
                failCount++;
            }

            KeyPairGenerator kpg2 = KeyPairGenerator.getInstance(KEYALG, PROVIDER);
            kpg2.initialize(new RSAKeyGenParameterSpec(size,
                    RSAKeyGenParameterSpec.F0));
            if (!specTest(kpg2.generateKeyPair(), RSAKeyGenParameterSpec.F0)) {
                failCount++;
            }
        } catch (NoSuchAlgorithmException | NoSuchProviderException
                | InvalidAlgorithmParameterException ex) {
            ex.printStackTrace(System.err);
            failCount++;
        }

        if (failCount != 0) {
            throw new RuntimeException("There are " + failCount
                    + " tests failed.");
        }
    }
}
