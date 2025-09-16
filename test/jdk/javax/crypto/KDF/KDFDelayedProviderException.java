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
 * @bug 8353888
 * @library /test/lib /test/jdk/security/unsignedjce
 * @build java.base/javax.crypto.ProviderVerifier
 * @run main/othervm KDFDelayedProviderException
 * @summary check delayed provider selection exception messages
 */

import jdk.test.lib.Asserts;

import javax.crypto.KDF;
import javax.crypto.KDFParameters;
import javax.crypto.KDFSpi;
import javax.crypto.SecretKey;
import java.security.InvalidAlgorithmParameterException;
import java.security.Provider;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.NamedParameterSpec;

public class KDFDelayedProviderException {
    public static void main(String[] args) throws Exception {

        Security.addProvider(new P1()); // only accepts NamedParameterSpec.ED25519
        Security.addProvider(new P2()); // only accepts NamedParameterSpec.ED448

        checkMessage("No provider supports this input",
                () -> KDF.getInstance("K").deriveData(NamedParameterSpec.X25519));

        checkMessage("The specified P1 provider does not support this input",
                () -> KDF.getInstance("K", "P1").deriveData(NamedParameterSpec.ED448));

        // ED448 is supported by one provider
        KDF.getInstance("K").deriveData(NamedParameterSpec.ED448);

        // After P1 has been selected, ED448 is no longer supported
        var k = KDF.getInstance("K");
        k.deriveData(NamedParameterSpec.ED25519);
        checkMessage("The previously selected P1 provider does not support this input",
                () -> k.deriveData(NamedParameterSpec.ED448));

    }

    public static void checkMessage(String msg, Asserts.TestMethod testMethod) {
        var exc = Asserts.assertThrows(InvalidAlgorithmParameterException.class, testMethod);
        Asserts.assertEquals(msg, exc.getMessage());
    }

    public static class P1 extends Provider {
        public P1() {
            super("P1", "1", "");
            put("KDF.K", K1.class.getName());
        }
    }

    public static class P2 extends Provider {
        public P2() {
            super("P2", "1", "");
            put("KDF.K", K2.class.getName());
        }
    }

    public static class K1 extends KDFSpi {
        public K1(KDFParameters p) throws InvalidAlgorithmParameterException {
            super(p);
        }
        protected byte[] engineDeriveData(AlgorithmParameterSpec derivationSpec)
                throws InvalidAlgorithmParameterException {
            if (derivationSpec != NamedParameterSpec.ED25519) {
                throw new InvalidAlgorithmParameterException("Not Ed25519");
            }
            return new byte[0];
        }
        protected KDFParameters engineGetParameters() {
            return null;
        }
        protected SecretKey engineDeriveKey(String alg, AlgorithmParameterSpec derivationSpec) {
            return null;
        }
    }

    public static class K2 extends KDFSpi {
        public K2(KDFParameters p) throws InvalidAlgorithmParameterException {
            super(p);
        }
        protected byte[] engineDeriveData(AlgorithmParameterSpec derivationSpec)
                throws InvalidAlgorithmParameterException {
            if (derivationSpec != NamedParameterSpec.ED448) {
                throw new InvalidAlgorithmParameterException("Not Ed448");
            }
            return new byte[0];
        }
        protected KDFParameters engineGetParameters() {
            return null;
        }
        protected SecretKey engineDeriveKey(String alg, AlgorithmParameterSpec derivationSpec) {
            return null;
        }
    }
}
