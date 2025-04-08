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
 * @run main/othervm KDFDelayedProviderTest
 * @summary delayed provider selection
 */

import jdk.test.lib.Asserts;

import javax.crypto.KDF;
import javax.crypto.KDFSpi;
import javax.crypto.SecretKey;
import java.security.InvalidAlgorithmParameterException;
import javax.crypto.KDFParameters;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Objects;

public class KDFDelayedProviderTest {
    public static void main(String[] args) throws Exception {
        Security.addProvider(new Provider1());
        Security.addProvider(new Provider2());
        Security.addProvider(new Provider3());
        KDF kdf;

        kdf = KDF.getInstance("X", new KDFParameters() {});
        kdf.deriveData(new AlgorithmParameterSpec() {});
        Asserts.assertEquals(kdf.getProviderName(), "P1");

        kdf = KDF.getInstance("X");
        kdf.deriveData(new MyDerivationSpec() {});
        Asserts.assertEquals(kdf.getProviderName(), "P2");

        kdf = KDF.getInstance("X");
        kdf.deriveData(new AlgorithmParameterSpec() {});
        Asserts.assertEquals(kdf.getProviderName(), "P3");

        boolean thrown = true;
        try {
            kdf = KDF.getInstance("Y");
            thrown = false;
        } catch(Exception nsae) {
            // Expected exception
            Asserts.assertTrue(nsae instanceof NoSuchAlgorithmException);
            System.out.println("Expected NoSuchAlgorithmException");
        }
        Asserts.assertTrue(thrown);

        thrown = true;
        try {
            kdf = KDF.getInstance("HKDF-SHA256", new MyKDFParameters());
            thrown = false;
        } catch (Exception iape) {
            // Expected exception
            Asserts.assertTrue(iape instanceof InvalidAlgorithmParameterException);
            System.out.println("Expected InvalidAlgorithmParameterException");
        }
        Asserts.assertTrue(thrown);

        thrown = true;
        try {
            kdf = KDF.getInstance("HKDF-SHA256");
            kdf.deriveData(new MyDerivationSpec());
            thrown = false;
        } catch (Exception iape) {
            // Expected exception
            Asserts.assertTrue(iape instanceof InvalidAlgorithmParameterException);
            System.out.println("Expected InvalidAlgorithmParameterException");
        }
        Asserts.assertTrue(thrown);
    }

    public static class Provider1 extends Provider {
        public Provider1() {
            super("P1", "1", "1");
            put("KDF.X", KDF1.class.getName());
        }
    }

    // KDF1 requires a params at getInstance()
    public static class KDF1 extends KDF0 {
        public KDF1(KDFParameters e) throws InvalidAlgorithmParameterException {
            super(Objects.requireNonNull(e));
        }
    }

    public static class Provider2 extends Provider {
        public Provider2() {
            super("P2", "1", "1");
            put("KDF.X", KDF2.class.getName());
        }
    }

    // KDF2 requires input to be a specific type
    public static class KDF2 extends KDF0 {
        public KDF2(KDFParameters e)
            throws InvalidAlgorithmParameterException {
            super(null);
        }

        @Override
        protected byte[] engineDeriveData(
            AlgorithmParameterSpec derivationSpec)
            throws InvalidAlgorithmParameterException {
            if (derivationSpec instanceof MyDerivationSpec) {
                return null;
            } else {
                throw new InvalidAlgorithmParameterException();
            }
        }
    }

    public static class Provider3 extends Provider {
        public Provider3() {
            super("P3", "1", "1");
            put("KDF.X", KDF3.class.getName());
        }
    }

    // KDF3 doesn't care about anything
    public static class KDF3 extends KDF0 {
        public KDF3(KDFParameters e) throws InvalidAlgorithmParameterException {
            super(null);
        }
    }

    public abstract static class KDF0 extends KDFSpi {
        public KDF0(KDFParameters a) throws InvalidAlgorithmParameterException {
            super(a);
        }

        protected SecretKey engineDeriveKey(String alg,
                                            AlgorithmParameterSpec derivationSpec)
            throws InvalidAlgorithmParameterException {
            return null;
        }

        protected byte[] engineDeriveData(
            AlgorithmParameterSpec derivationSpec)
            throws InvalidAlgorithmParameterException {
            return new byte[0];
        }

        protected KDFParameters engineGetParameters(){
            return null;
        }
    }

    static class MyDerivationSpec implements AlgorithmParameterSpec {}

    static class MyKDFParameters implements KDFParameters {}
}
