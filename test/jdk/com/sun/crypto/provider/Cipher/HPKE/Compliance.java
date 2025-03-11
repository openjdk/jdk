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
import jdk.test.lib.Utils;

import javax.crypto.Cipher;
import javax.crypto.spec.HPKEParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

/*
 * @test
 * @bug 8325448
 * @library /test/lib
 * @summary HPKE compliance test
 */
public class Compliance {
    public static void main(String[] args) throws Exception {

        var kp = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        var info = "info".getBytes(StandardCharsets.UTF_8);
        var psk = new SecretKeySpec(new byte[32], "ONE");
        var psk_id = "psk_id".getBytes(StandardCharsets.UTF_8);

        // HPKEParameterSpec

        // Default values
        var spec = HPKEParameterSpec.of();
        Asserts.assertEQ(spec.kem_id(), -1);
        Asserts.assertEQ(spec.kdf_id(), -1);
        Asserts.assertEQ(spec.aead_id(), -1);
        Asserts.assertEQ(spec.authKey(), null);
        Asserts.assertEQ(spec.encapsulation(), null);
        Asserts.assertEqualsByteArray(spec.info(), new byte[0]);
        Asserts.assertEQ(spec.psk(), null);
        Asserts.assertEqualsByteArray(spec.psk_id(), new byte[0]);

        // Partial default values
        var spec2 = HPKEParameterSpec.of(0, 0, 0);
        Asserts.assertEQ(spec2.kem_id(), 0);
        Asserts.assertEQ(spec2.kdf_id(), 0);
        Asserts.assertEQ(spec2.aead_id(), 0);
        Asserts.assertEQ(spec2.authKey(), null);
        Asserts.assertEQ(spec2.encapsulation(), null);
        Asserts.assertEqualsByteArray(spec2.info(), new byte[0]);
        Asserts.assertEQ(spec2.psk(), null);
        Asserts.assertEqualsByteArray(spec2.psk_id(), new byte[0]);

        var spec3 = HPKEParameterSpec.of(0, 0);

        HPKEParameterSpec.of(65535, 65535, 65535);
        Asserts.assertEQ(spec3.kem_id(), -1);
        Asserts.assertEQ(spec3.kdf_id(), 0);
        Asserts.assertEQ(spec3.aead_id(), 0);
        Asserts.assertEQ(spec3.authKey(), null);
        Asserts.assertEQ(spec3.encapsulation(), null);
        Asserts.assertEqualsByteArray(spec3.info(), new byte[0]);
        Asserts.assertEQ(spec3.psk(), null);
        Asserts.assertEqualsByteArray(spec3.psk_id(), new byte[0]);

        // identifiers must be in range
        Utils.runAndCheckException(
                () -> HPKEParameterSpec.of(-1, 0),
                IllegalArgumentException.class);
        Utils.runAndCheckException(
                () -> HPKEParameterSpec.of(0, -1),
                IllegalArgumentException.class);
        Utils.runAndCheckException(
                () -> HPKEParameterSpec.of(65536, 0),
                IllegalArgumentException.class);
        Utils.runAndCheckException(
                () -> HPKEParameterSpec.of(0, 65536),
                IllegalArgumentException.class);
        Utils.runAndCheckException(
                () -> HPKEParameterSpec.of(-1, 0, 0),
                IllegalArgumentException.class);
        Utils.runAndCheckException(
                () -> HPKEParameterSpec.of(0, -1, 0),
                IllegalArgumentException.class);
        Utils.runAndCheckException(
                () -> HPKEParameterSpec.of(0, 0, -1),
                IllegalArgumentException.class);
        Utils.runAndCheckException(
                () -> HPKEParameterSpec.of(65536, 0, 0),
                IllegalArgumentException.class);
        Utils.runAndCheckException(
                () -> HPKEParameterSpec.of(0, 65536, 0),
                IllegalArgumentException.class);
        Utils.runAndCheckException(
                () -> HPKEParameterSpec.of(0, 0, 65536),
                IllegalArgumentException.class);

        Asserts.assertTrue(spec.authKey(null).authKey() == null);
        Asserts.assertTrue(spec.authKey(kp.getPrivate()).authKey() != null);
        Asserts.assertTrue(spec.authKey(kp.getPublic()).authKey() != null);
        Asserts.assertTrue(spec.authKey(kp.getPrivate()).authKey(null).authKey() == null);

        // Info can be empty but not null
        Utils.runAndCheckException(
                () -> spec.info(null),
                NullPointerException.class);
        Asserts.assertEqualsByteArray(spec.info(info).info(), info);

        Asserts.assertTrue(spec.encapsulation(null).encapsulation() == null);
        Asserts.assertEqualsByteArray(spec.encapsulation(info).encapsulation(), info);
        Asserts.assertTrue(spec.encapsulation(info).encapsulation(null).encapsulation() == null);

        // psk_id can be empty but not null
        Utils.runAndCheckException(
                () -> spec.psk(psk, null),
                NullPointerException.class);

        // psk and psk_id must match
        Utils.runAndCheckException(
                () -> spec.psk(psk, new byte[0]),
                InvalidAlgorithmParameterException.class);
        Utils.runAndCheckException(
                () -> spec.psk(null, psk_id),
                InvalidAlgorithmParameterException.class);

        Asserts.assertEqualsByteArray(spec.psk(psk, psk_id).psk().getEncoded(), psk.getEncoded());
        Asserts.assertEqualsByteArray(spec.psk(psk, psk_id).psk_id(), psk_id);
        Asserts.assertTrue(spec.psk(null, new byte[0]).psk() == null);
        Asserts.assertEqualsByteArray(spec.psk(null, new byte[0]).psk_id(), new byte[0]);

        // HPKE
        var c1 = Cipher.getInstance("HPKE");
        var c2 = Cipher.getInstance("HPKE");

        // Still at BEGIN, not initialized
        Asserts.assertEQ(c1.getIV(), null);
        Utils.runAndCheckException(() -> c1.getBlockSize(), IllegalStateException.class);
        Utils.runAndCheckException(() -> c1.getOutputSize(100), IllegalStateException.class);
        Utils.runAndCheckException(() -> c1.update(new byte[1]), IllegalStateException.class);
        Utils.runAndCheckException(() -> c1.update(new byte[1], 0, 1), IllegalStateException.class);
        Utils.runAndCheckException(() -> c1.updateAAD(new byte[1]), IllegalStateException.class);
        Utils.runAndCheckException(() -> c1.updateAAD(new byte[1], 0, 1), IllegalStateException.class);
        Utils.runAndCheckException(() -> c1.doFinal(), IllegalStateException.class);
        Utils.runAndCheckException(() -> c1.doFinal(new byte[1]), IllegalStateException.class);
        Utils.runAndCheckException(() -> c1.doFinal(new byte[1], 0, 1), IllegalStateException.class);
        Utils.runAndCheckException(() -> c1.doFinal(new byte[1], 0, 1, new byte[1024], 0), IllegalStateException.class);

        // Simplest usages
        c1.init(Cipher.ENCRYPT_MODE, kp.getPublic());
        c2.init(Cipher.DECRYPT_MODE, kp.getPrivate(), new IvParameterSpec(c1.getIV()));
        c2.init(Cipher.DECRYPT_MODE, kp.getPrivate(), HPKEParameterSpec.of().encapsulation(c1.getIV()));

        // Does not support WRAP and UNWRAP mode
        Utils.runAndCheckException(
                () -> c1.init(Cipher.WRAP_MODE, kp.getPublic()),
                UnsupportedOperationException.class);
        Utils.runAndCheckException(
                () -> c1.init(Cipher.UNWRAP_MODE, kp.getPublic()),
                UnsupportedOperationException.class);

        // Cannot init sender with private key
        Utils.runAndCheckException(
                () -> c1.init(Cipher.ENCRYPT_MODE, kp.getPrivate()),
                InvalidKeyException.class);
        // Cannot provide key encap msg to sender
        Utils.runAndCheckException(
                () -> c1.init(Cipher.ENCRYPT_MODE, kp.getPublic(),
                        HPKEParameterSpec.of().encapsulation(new byte[32])),
                InvalidAlgorithmParameterException.class);

        // Cannot init recipient with public key
        Utils.runAndCheckException(
                () -> c2.init(Cipher.DECRYPT_MODE, kp.getPublic()),
                InvalidKeyException.class);
        // Must provide key encap msg to recipient
        Utils.runAndCheckException(
                () -> c2.init(Cipher.DECRYPT_MODE, kp.getPrivate()),
                InvalidKeyException.class);

        // Unknown identifiers
        c1.init(Cipher.ENCRYPT_MODE, kp.getPublic(), HPKEParameterSpec.of(0x20, 1, 1));

        // Unknown identifiers
        Utils.runAndCheckException(
                () -> c1.init(Cipher.ENCRYPT_MODE, kp.getPublic(), HPKEParameterSpec.of(0, 1, 1)),
                InvalidAlgorithmParameterException.class);
        Utils.runAndCheckException(
                () -> c1.init(Cipher.ENCRYPT_MODE, kp.getPublic(), HPKEParameterSpec.of(0x200, 1, 1)),
                InvalidAlgorithmParameterException.class);
        Utils.runAndCheckException(
                () -> c1.init(Cipher.ENCRYPT_MODE, kp.getPublic(), HPKEParameterSpec.of(0x20, 4, 1)),
                InvalidAlgorithmParameterException.class);
        Utils.runAndCheckException(
                () -> c1.init(Cipher.ENCRYPT_MODE, kp.getPublic(), HPKEParameterSpec.of(0x20, 1, 4)),
                InvalidAlgorithmParameterException.class);

        // No key encap msg for recipient
        Utils.runAndCheckException(
                () -> c2.init(Cipher.DECRYPT_MODE, kp.getPrivate(), HPKEParameterSpec.of(0x20, 1, 1)),
                InvalidAlgorithmParameterException.class);

        // No key encap msg for recipient
        Utils.runAndCheckException(
                () -> c2.init(Cipher.DECRYPT_MODE, kp.getPrivate(), HPKEParameterSpec.of(0x20, 1, 65535)),
                InvalidAlgorithmParameterException.class);

        var aad = "AAD".getBytes(StandardCharsets.UTF_8);

        // HPKE with encryption
        c1.init(Cipher.ENCRYPT_MODE, kp.getPublic());
        c1.getBlockSize();
        c1.getOutputSize(100);
        c1.updateAAD(aad);
        var ct = c1.doFinal(new byte[2]);

        c2.init(Cipher.DECRYPT_MODE, kp.getPrivate(),
                HPKEParameterSpec.of().encapsulation(c1.getIV()));
        Asserts.assertEQ(c2.getIV(), null);
        c2.getBlockSize();
        c2.getOutputSize(100);
        c2.updateAAD(aad);
        Asserts.assertEqualsByteArray(c2.doFinal(ct), new byte[2]);

        c2.init(Cipher.DECRYPT_MODE, kp.getPrivate(), new IvParameterSpec(c1.getIV()));
        c2.updateAAD(aad);
        c2.update(ct);
        Asserts.assertEqualsByteArray(c2.doFinal(), new byte[2]);

        // info and psk
        checkEncryptDecrypt(kp,
                HPKEParameterSpec.of().info(info).psk(psk, psk_id),
                HPKEParameterSpec.of().info(info).psk(psk, psk_id));

        var kp2 = KeyPairGenerator.getInstance("X25519").generateKeyPair();

        // mod_auth, wrong key type
        Utils.runAndCheckException(
                () -> c1.init(Cipher.ENCRYPT_MODE, kp.getPublic(),
                        HPKEParameterSpec.of().authKey(kp2.getPublic())),
                InvalidAlgorithmParameterException.class);
        Utils.runAndCheckException(
                () -> c2.init(Cipher.DECRYPT_MODE, kp.getPrivate(),
                        HPKEParameterSpec.of().authKey(kp2.getPrivate())),
                InvalidAlgorithmParameterException.class);

        // mod_auth
        checkEncryptDecrypt(kp,
                HPKEParameterSpec.of().authKey(kp2.getPrivate()),
                HPKEParameterSpec.of().authKey(kp2.getPublic()));

        // check default values
        checkEncryptDecrypt(kp,
                HPKEParameterSpec.of(),
                HPKEParameterSpec.of(
                        HPKEParameterSpec.KEM_DHKEM_X25519_HKDF_SHA256,
                        HPKEParameterSpec.KDF_HKDF_SHA256,
                        HPKEParameterSpec.AEAD_AES_256_GCM));

        checkEncryptDecrypt(kp,
                HPKEParameterSpec.of(),
                HPKEParameterSpec.of(
                        HPKEParameterSpec.KDF_HKDF_SHA256,
                        HPKEParameterSpec.AEAD_AES_256_GCM));

        checkEncryptDecrypt(kp,
                HPKEParameterSpec.of(),
                HPKEParameterSpec.of().info(new byte[0]));

        checkEncryptDecrypt(kp,
                HPKEParameterSpec.of(),
                HPKEParameterSpec.of().psk(null, new byte[0]));
    }

    static void checkEncryptDecrypt(KeyPair kp, HPKEParameterSpec ps,
            HPKEParameterSpec pr) throws Exception {

        var c1 = Cipher.getInstance("HPKE");
        var c2 = Cipher.getInstance("HPKE");
        var aad = "AAD".getBytes(StandardCharsets.UTF_8);

        c1.init(Cipher.ENCRYPT_MODE, kp.getPublic(), ps);
        c1.updateAAD(aad);
        var ct = c1.doFinal(new byte[2]);

        c2.init(Cipher.DECRYPT_MODE, kp.getPrivate(),
                pr.encapsulation(c1.getIV()));
        c2.updateAAD(aad);
        Asserts.assertEqualsByteArray(c2.doFinal(ct), new byte[2]);
    }
}
