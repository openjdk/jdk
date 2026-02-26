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


import jdk.test.lib.Asserts;
import sun.security.util.SliceableSecretKey;

import javax.crypto.KDF;
import javax.crypto.KDFParameters;
import javax.crypto.KDFSpi;
import javax.crypto.KEM;
import javax.crypto.SecretKey;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;

/*
 * @test
 * @bug 8325448
 * @library /test/lib /test/jdk/security/unsignedjce
 * @build java.base/javax.crypto.ProviderVerifier
 * @modules java.base/sun.security.util
 * @run main/othervm SoftSliceable
 * @summary Showcase how Sliceable can be used in DHKEM
 */
public class SoftSliceable {

    public static void main(String[] args) throws Exception {

        // Put an HKDF-SHA256 impl that is preferred to the SunJCE one
        Security.insertProviderAt(new ProviderImpl(), 1);

        // Just plain KEM calls
        var kp = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        var k = KEM.getInstance("DHKEM");
        var e = k.newEncapsulator(kp.getPublic());
        var d = k.newDecapsulator(kp.getPrivate());
        var enc = e.encapsulate(3, 9, "Generic");
        var k2 = d.decapsulate(enc.encapsulation(), 3, 9, "Generic");
        var k2full = d.decapsulate(enc.encapsulation());

        if (enc.key() instanceof KeyImpl ki1
                && k2 instanceof KeyImpl ki2
                && k2full instanceof KeyImpl ki2full) {
            // So the keys do come from the new provider, and
            // 1. It has the correct length
            Asserts.assertEquals(6, ki1.bytes.length);
            // 2. encaps and decaps result in same keys
            Asserts.assertEqualsByteArray(ki1.bytes, ki2.bytes);
            // 3. The key is the correct slice from the full shared secret
            Asserts.assertEqualsByteArray(
                    Arrays.copyOfRange(ki2full.bytes, 3, 9), ki2.bytes);
        } else {
            throw new Exception("Unexpected key types");
        }
    }

    // A trivial SliceableSecretKey that is non-extractable with getBytes()
    public static class KeyImpl implements SecretKey, SliceableSecretKey {

        private final byte[] bytes;
        private final String algorithm;

        public KeyImpl(byte[] bytes, String algorithm) {
            this.bytes = bytes.clone();
            this.algorithm = algorithm;
        }

        @Override
        public String getAlgorithm() {
            return algorithm;
        }

        @Override
        public String getFormat() {
            return null;
        }

        @Override
        public byte[] getEncoded() {
            return null;
        }

        @Override
        public SecretKey slice(String alg, int from, int to) {
            return new KeyImpl(Arrays.copyOfRange(bytes, from, to), algorithm);
        }
    }

    // Our new provider
    public static class ProviderImpl extends Provider {
        public ProviderImpl() {
            super("A", "A", "A");
            put("KDF.HKDF-SHA256", KDFImpl.class.getName());
        }
    }

    // Our new HKDF-SHA256 impl that always returns a KeyImpl object
    public static class KDFImpl extends KDFSpi {

        public KDFImpl(KDFParameters p)
                throws InvalidAlgorithmParameterException {
            super(p);
        }

        @Override
        protected KDFParameters engineGetParameters() {
            return null;
        }

        @Override
        protected SecretKey engineDeriveKey(String alg, AlgorithmParameterSpec spec)
                throws InvalidAlgorithmParameterException {
            try {
                var kdf = KDF.getInstance("HKDF-SHA256", "SunJCE");
                var bytes = kdf.deriveData(spec);
                return new KeyImpl(bytes, alg);
            } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
                throw new AssertionError("Cannot happen", e);
            }
        }

        @Override
        protected byte[] engineDeriveData(AlgorithmParameterSpec spec) {
            throw new UnsupportedOperationException("Cannot derive data");
        }
    }
}
