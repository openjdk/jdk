/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8331008
 * @library /test/lib /test/jdk/security/unsignedjce
 * @build java.base/javax.crypto.ProviderVerifier
 * @run main/othervm KDFDelayedProviderThreadingTest
 * @summary delayed provider selection threading test
 */

import jdk.test.lib.Asserts;

import javax.crypto.KDF;
import javax.crypto.KDFParameters;
import javax.crypto.KDFSpi;
import javax.crypto.SecretKey;
import javax.crypto.spec.HKDFParameterSpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.Provider;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;

public class KDFDelayedProviderThreadingTest {
    /// This number of iterations is enough to see a case where the threads
    /// arrange themselves such that both `deriveData` attempts cause "ERROR",
    /// which is still a passing case.
    static final int ITERATIONS = 10000;
    static int threadOrderReversalCounter = 0;
    static final String ERROR = "ERROR";
    static volatile String out;
    static final HKDFParameterSpec input
            = HKDFParameterSpec.ofExtract().extractOnly();

    static String derive(KDF kdf) {
        try {
            return Arrays.toString(kdf.deriveData(input));
        } catch (Exception e) {
            return ERROR;
        }
    }

    public static void main(String[] args) throws Exception {
        Security.insertProviderAt(new P(), 1);
        for (int i = 0; i < ITERATIONS; i++) {
            test();
        }

        // If the value of threadOrderReversalCounter is consistently zero,
        // then this test may need to be adjusted for newer hardware to ensure
        // a thorough test. This didn't seem fitting for a check, such as
        // `Asserts.assertTrue(threadOrderReversalCounter > 0);`, since we
        // may not want to start failing the test right away when running on
        // better hardware someday.
        System.out.println("Also tested atypical threading condition "
                           + threadOrderReversalCounter + "/" + ITERATIONS
                           + " iterations (depends on hardware specs/utilization).");
    }

    static void test() throws Exception {
        var k = KDF.getInstance("HKDF-SHA256");
        var t1 = new Thread(() -> out = derive(k));
        var t2 = new Thread(() -> k.getProviderName());
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        String out2 = derive(k);
        Asserts.assertEquals(out, out2);
        if (out.length() < 10) { // "error"
            threadOrderReversalCounter++;
        }
    }

    public static class P extends Provider {
        public P() {
            super("ME", "1", "ME");
            put("KDF.HKDF-SHA256", K.class.getName());
        }
    }

    public static class K extends KDFSpi {

        public K(KDFParameters p) throws InvalidAlgorithmParameterException {
            super(p);
        }

        @Override
        protected KDFParameters engineGetParameters() {
            return null;
        }

        @Override
        protected SecretKey engineDeriveKey(String alg,
                                            AlgorithmParameterSpec derivationSpec)
                throws InvalidAlgorithmParameterException {
            throw new InvalidAlgorithmParameterException();
        }

        @Override
        protected byte[] engineDeriveData(AlgorithmParameterSpec derivationSpec)
                throws InvalidAlgorithmParameterException {
            throw new InvalidAlgorithmParameterException();
        }
    }
}
