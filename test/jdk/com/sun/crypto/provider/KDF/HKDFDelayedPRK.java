/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8347289
 * @summary make sure DPS works when non-extractable PRK is provided
 * @library /test/lib /test/jdk/security/unsignedjce
 * @build java.base/javax.crypto.ProviderVerifier
 * @run main/othervm HKDFDelayedPRK
 */

import jdk.test.lib.Asserts;

import javax.crypto.KDF;
import javax.crypto.KDFParameters;
import javax.crypto.KDFSpi;
import javax.crypto.SecretKey;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.Provider;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;

public class HKDFDelayedPRK {
    public static void main(String[] args) throws Exception {
        // This is a fake non-extractable key
        var prk = new SecretKey() {
            @Override
            public String getAlgorithm() {
                return "PRK";
            }

            @Override
            public String getFormat() {
                return null;
            }

            @Override
            public byte[] getEncoded() {
                return null;
            }
        };

        Security.addProvider(new ProviderImpl());
        var kdf = KDF.getInstance("HKDF-SHA256");
        kdf.deriveData(HKDFParameterSpec.expandOnly(prk, null, 32));

        // Confirms our own omnipotent impl is selected
        Asserts.assertEquals("P", kdf.getProviderName());
    }

    public static class ProviderImpl extends Provider {
        public ProviderImpl() {
            super("P", "1", "info");
            put("KDF.HKDF-SHA256", KDFImpl.class.getName());
        }
    }

    // This HKDF impl accepts everything
    public static class KDFImpl extends KDFSpi {

        public KDFImpl(KDFParameters params) throws InvalidAlgorithmParameterException {
            super(params);
        }

        @Override
        protected KDFParameters engineGetParameters() {
            return null;
        }

        @Override
        protected SecretKey engineDeriveKey(String alg, AlgorithmParameterSpec dummy) {
            return new SecretKeySpec(new byte[32], alg);
        }

        @Override
        protected byte[] engineDeriveData(AlgorithmParameterSpec dummy) {
            return new byte[32];
        }
    }
}
