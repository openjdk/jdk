/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng/othervm -Djava.security.debug=provider,engine=kdf KDFDelayedProviderThreadingTest
 * @summary delayed provider selection threading test
 * @enablePreview
 */

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.crypto.KDF;
import javax.crypto.KDFParameters;
import javax.crypto.KDFSpi;
import javax.crypto.SecretKey;
import javax.crypto.spec.HKDFParameterSpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;

public class KDFDelayedProviderThreadingTest {

    KDF k;

    @BeforeClass
    public void setUp() throws NoSuchAlgorithmException {
        Security.insertProviderAt(new P(), 1);
        k = KDF.getInstance("HKDF-SHA256");
    }

    @Test(threadPoolSize = 50, invocationCount = 1000000, timeOut = 150)
    public void testThreading() throws Exception {
        var input = HKDFParameterSpec.ofExtract().extractOnly();
        new Thread(() -> {
            try {
                System.out.println(Arrays.toString(k.deriveData(input)));
            } catch (Exception e) {
                System.out.println(e);
                throw new RuntimeException(e);
            }
        }).start();
        new Thread(() -> k.getProviderName()).start();
        System.out.println(Arrays.toString(k.deriveData(input)));
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