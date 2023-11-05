/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import static java.lang.System.out;

import java.lang.Integer;
import java.lang.String;
import java.lang.System;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/*
 * @test
 * @bug 8264849
 * @summary Tests for key wrap and unwrap operations
 * @library /test/lib ../..
 * @run main/othervm TestCipherKeyWrapperTest
 */
// adapted from
// com/sun/crypto/provider/Cipher/KeyWrap/TestCipherKeyWrapperTest.java
public class TestCipherKeyWrapperTest extends PKCS11Test {
    private static final int LINIMITED_KEYSIZE = 128;

    private static final byte[] DATA_32 =
            Arrays.copyOf("1234567890123456789012345678901234".getBytes(), 32);

    private enum TestVector {
        AESWrap("AES", "AESWrap", -1),
        AESWrap_128("AES", "AESWrap_128", 128),
        AESWrap_192("AES", "AESWrap_192", 192),
        AESWrap_256("AES", "AESWrap_256", 256),
        AESWrapPad("AES", "AESWrapPad", -1),
        AESWrapPad_128("AES", "AESWrapPad_128", 128),
        AESWrapPad_192("AES", "AESWrapPad_192", 192),
        AESWrapPad_256("AES", "AESWrapPad_256", 256);

        final String algo;
        final String wrapperAlgo;
        final int keySize; // -1 means no restriction
        final SecretKey wrapperKey;

        private TestVector(String algo, String wrapperAlgo, int kSize) {
            this.algo = algo;
            this.wrapperAlgo = wrapperAlgo;
            this.keySize = kSize;
            if (kSize == -1) {
                this.wrapperKey = new SecretKeySpec(DATA_32, "AES");
            } else {
                this.wrapperKey = new SecretKeySpec(DATA_32, 0, kSize >> 3,
                        "AES");
            }
        }
    };

    public static void main(String[] args) throws Exception {
        main(new TestCipherKeyWrapperTest(), args);
    }

    @Override
    public void main(Provider p) throws Exception {
        for (TestVector tv : TestVector.values()) {
            if (p.getService("Cipher", tv.wrapperAlgo) == null) {
                System.out.println("Skip, due to no support:  " +
                        tv.wrapperAlgo);
                continue;
            }

            // only run the tests on longer key lengths if unlimited
            // version of JCE jurisdiction policy files are installed
            if (!(Cipher.getMaxAllowedKeyLength(tv.algo) == Integer.MAX_VALUE)
                    && tv.keySize > LINIMITED_KEYSIZE) {
                out.println(tv.algo + " will not run if unlimited version of"
                        + " JCE jurisdiction policy files are installed");
                continue;
            }
            wrapperSecretKeyTest(p, tv.wrapperAlgo, tv.wrapperKey, tv.algo);
            wrapperPrivateKeyTest(p, tv.wrapperAlgo, tv.wrapperKey, "RSA");
        }
    }

    private void wrapperSecretKeyTest(Provider p, String wrapAlgo,
            SecretKey key, String algo)
              throws Exception {
        // Initialization
        KeyGenerator kg = KeyGenerator.getInstance(algo, p);
        SecretKey skey = kg.generateKey();
        wrapTest(p, wrapAlgo, key, skey, Cipher.SECRET_KEY);
    }

    private void wrapperPrivateKeyTest(Provider p, String wrapAlgo,
            SecretKey key, String algo)
              throws Exception {
        // Key pair generated
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(algo, p);
        kpg.initialize(2048);
        System.out.println("Generate key pair (algorithm: " + algo
                + ", provider: " + kpg.getProvider().getName() + ")");

        KeyPair kp = kpg.genKeyPair();
        // key generated
        wrapTest(p, wrapAlgo, key, kp.getPrivate(), Cipher.PRIVATE_KEY);
    }

    private void wrapTest(Provider p, String wrapAlgo, SecretKey initKey,
            Key tbwKey, int keyType)
            throws Exception {
        AlgorithmParameters aps = null;

        out.println("Testing " + wrapAlgo + " cipher wrap/unwrap");

        Cipher wrapCI = Cipher.getInstance(wrapAlgo, p);

        byte[] keyEncoding = tbwKey.getEncoded();
        if (wrapAlgo.indexOf("Pad") == -1 &&
                (keyEncoding.length % wrapCI.getBlockSize() != 0)) {
            System.out.println("Skip due to key length: " +
                    keyEncoding.length);
            return;
        }
        // Wrap & Unwrap operation
        System.out.println("calling wrap()");
        wrapCI.init(Cipher.WRAP_MODE, initKey);
        aps = wrapCI.getParameters();
        byte[] keyWrapped = wrapCI.wrap(tbwKey);

        wrapCI.init(Cipher.UNWRAP_MODE, initKey, aps);
        Key unwrappedKey = wrapCI.unwrap(keyWrapped, tbwKey.getAlgorithm(),
                keyType);
        // Comparison
        if (!Arrays.equals(tbwKey.getEncoded(), unwrappedKey.getEncoded())) {
            out.println("key encoding len : " + tbwKey.getEncoded().length);
            throw new RuntimeException("Comparation failed testing "
                    + wrapAlgo + ":" + keyType);
        }
    }
}
