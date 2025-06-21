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

import javax.crypto.Cipher;
import javax.crypto.spec.HPKEParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.NamedParameterSpec;

import static javax.crypto.spec.HPKEParameterSpec.AEAD_AES_256_GCM;
import static javax.crypto.spec.HPKEParameterSpec.EXPORT_ONLY;
import static javax.crypto.spec.HPKEParameterSpec.KDF_HKDF_SHA256;
import static javax.crypto.spec.HPKEParameterSpec.KEM_DHKEM_X25519_HKDF_SHA256;

/*
 * @test
 * @bug 8325448
 * @library /test/lib
 * @summary HPKE compliance test
 */
public class Compliance {
    public static void main(String[] args) throws Exception {

        var emptyParams = HPKEParameterSpec.of();
        var defaultParams = HPKEParameterSpec.of(
                KEM_DHKEM_X25519_HKDF_SHA256,
                KDF_HKDF_SHA256,
                AEAD_AES_256_GCM);

        var kp = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        var info = "info".getBytes(StandardCharsets.UTF_8);
        var psk = new SecretKeySpec(new byte[32], "ONE");
        var psk_id = "psk_id".getBytes(StandardCharsets.UTF_8);

        // HPKEParameterSpec

        // Default values
        var spec = emptyParams;
        Asserts.assertEQ(spec.kem_id(), -1);
        Asserts.assertEQ(spec.kdf_id(), -1);
        Asserts.assertEQ(spec.aead_id(), -1);
        Asserts.assertEQ(spec.authKey(), null);
        Asserts.assertEQ(spec.encapsulation(), null);
        Asserts.assertEqualsByteArray(spec.info(), new byte[0]);
        Asserts.assertEQ(spec.psk(), null);
        Asserts.assertEqualsByteArray(spec.psk_id(), new byte[0]);

        // Specified values
        var spec2 = HPKEParameterSpec.of(0, 0, 0);
        Asserts.assertEQ(spec2.kem_id(), 0);
        Asserts.assertEQ(spec2.kdf_id(), 0);
        Asserts.assertEQ(spec2.aead_id(), 0);
        Asserts.assertEQ(spec2.authKey(), null);
        Asserts.assertEQ(spec2.encapsulation(), null);
        Asserts.assertEqualsByteArray(spec2.info(), new byte[0]);
        Asserts.assertEQ(spec2.psk(), null);
        Asserts.assertEqualsByteArray(spec2.psk_id(), new byte[0]);

        // identifiers must be in range
        HPKEParameterSpec.of(65535, 65535, 65535);
        Asserts.assertThrows(IllegalArgumentException.class,
                () -> HPKEParameterSpec.of(-1, 0, 0));
        Asserts.assertThrows(IllegalArgumentException.class,
                () -> HPKEParameterSpec.of(0, -1, 0));
        Asserts.assertThrows(IllegalArgumentException.class,
                () -> HPKEParameterSpec.of(0, 0, -1));
        Asserts.assertThrows(IllegalArgumentException.class,
                () -> HPKEParameterSpec.of(65536, 0, 0));
        Asserts.assertThrows(IllegalArgumentException.class,
                () -> HPKEParameterSpec.of(0, 65536, 0));
        Asserts.assertThrows(IllegalArgumentException.class,
                () -> HPKEParameterSpec.of(0, 0, 65536));

        Asserts.assertTrue(spec.authKey(null).authKey() == null);
        Asserts.assertTrue(spec.authKey(kp.getPrivate()).authKey() != null);
        Asserts.assertTrue(spec.authKey(kp.getPublic()).authKey() != null);
        Asserts.assertTrue(spec.authKey(kp.getPrivate()).authKey(null).authKey() == null);

        Asserts.assertTrue(defaultParams.toString().contains("kem_id=32, kdf_id=1, aead_id=2"));
        Asserts.assertTrue(defaultParams.info(new byte[3]).toString().contains("info=000000"));
        Asserts.assertTrue(defaultParams.toString().contains("mode_base}"));
        Asserts.assertTrue(defaultParams.psk(psk, psk_id).toString().contains("mode_psk}"));
        Asserts.assertTrue(defaultParams.authKey(kp.getPrivate()).toString().contains("mode_auth}"));
        Asserts.assertTrue(defaultParams.authKey(kp.getPrivate()).psk(psk, psk_id).toString().contains("mode_auth_psk}"));

        // Info can be empty but not null
        Asserts.assertThrows(NullPointerException.class, () -> spec.info(null));
        Asserts.assertEqualsByteArray(spec.info(info).info(), info);

        Asserts.assertTrue(spec.encapsulation(null).encapsulation() == null);
        Asserts.assertEqualsByteArray(spec.encapsulation(info).encapsulation(), info);
        Asserts.assertTrue(spec.encapsulation(info).encapsulation(null).encapsulation() == null);

        // psk_id can be empty but not null
        Asserts.assertThrows(NullPointerException.class, () -> spec.psk(psk, null));

        // psk and psk_id must match
        Asserts.assertThrows(InvalidAlgorithmParameterException.class, () -> spec.psk(psk, new byte[0]));
        Asserts.assertThrows(InvalidAlgorithmParameterException.class, () -> spec.psk(null, psk_id));

        Asserts.assertEqualsByteArray(spec.psk(psk, psk_id).psk().getEncoded(), psk.getEncoded());
        Asserts.assertEqualsByteArray(spec.psk(psk, psk_id).psk_id(), psk_id);
        Asserts.assertTrue(spec.psk(null, new byte[0]).psk() == null);
        Asserts.assertEqualsByteArray(spec.psk(null, new byte[0]).psk_id(), new byte[0]);

        // HPKE
        var c1 = Cipher.getInstance("HPKE");
        var c2 = Cipher.getInstance("HPKE");

        // Still at BEGIN, not initialized
        Asserts.assertEQ(c1.getIV(), null);
        Asserts.assertEQ(c1.getParameters(), null);
        Asserts.assertEquals(0, c1.getBlockSize());
        Asserts.assertThrows(IllegalStateException.class, () -> c1.getOutputSize(100));
        Asserts.assertThrows(IllegalStateException.class, () -> c1.update(new byte[1]));
        Asserts.assertThrows(IllegalStateException.class, () -> c1.update(new byte[1], 0, 1));
        Asserts.assertThrows(IllegalStateException.class, () -> c1.updateAAD(new byte[1]));
        Asserts.assertThrows(IllegalStateException.class, () -> c1.updateAAD(new byte[1], 0, 1));
        Asserts.assertThrows(IllegalStateException.class, () -> c1.doFinal());
        Asserts.assertThrows(IllegalStateException.class, () -> c1.doFinal(new byte[1]));
        Asserts.assertThrows(IllegalStateException.class, () -> c1.doFinal(new byte[1], 0, 1));
        Asserts.assertThrows(IllegalStateException.class, () -> c1.doFinal(new byte[1], 0, 1, new byte[1024], 0));

        // Simplest usages
        c1.init(Cipher.ENCRYPT_MODE, kp.getPublic());
        var encap = c1.getIV();
        c2.init(Cipher.DECRYPT_MODE, kp.getPrivate(),
                defaultParams.encapsulation(encap));

        var params = c1.getParameters().getParameterSpec(HPKEParameterSpec.class);
        Asserts.assertEqualsByteArray(encap, params.encapsulation());

        // Does not support WRAP and UNWRAP mode
        Asserts.assertThrows(UnsupportedOperationException.class,
                () -> c1.init(Cipher.WRAP_MODE, kp.getPublic()));
        Asserts.assertThrows(UnsupportedOperationException.class,
                () -> c1.init(Cipher.UNWRAP_MODE, kp.getPublic()));

        // Cannot init sender with private key
        Asserts.assertThrows(InvalidKeyException.class,
                () -> c1.init(Cipher.ENCRYPT_MODE, kp.getPrivate()));

        // Cannot provide key encap msg to sender
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> c1.init(Cipher.ENCRYPT_MODE, kp.getPublic(),
                        emptyParams.encapsulation(new byte[32])));

        // Cannot init recipient without algorithm identifiers
        Asserts.assertThrows(InvalidKeyException.class,
                () -> c2.init(Cipher.DECRYPT_MODE, kp.getPrivate()));
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> c2.init(Cipher.DECRYPT_MODE, kp.getPrivate(),
                        emptyParams));
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> c2.init(Cipher.DECRYPT_MODE, kp.getPrivate(),
                        emptyParams.encapsulation(encap)));

        // Cannot init recipient with public key
        Asserts.assertThrows(InvalidKeyException.class,
                () -> c2.init(Cipher.DECRYPT_MODE, kp.getPublic(),
                        defaultParams.encapsulation(encap)));
        // Must provide key encap msg to recipient
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> c2.init(Cipher.DECRYPT_MODE, kp.getPrivate(), defaultParams));

        // Unsupported identifiers
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> c1.init(Cipher.ENCRYPT_MODE, kp.getPublic(),
                        HPKEParameterSpec.of(0, KDF_HKDF_SHA256, AEAD_AES_256_GCM)));
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> c1.init(Cipher.ENCRYPT_MODE, kp.getPublic(),
                        HPKEParameterSpec.of(0x200, KDF_HKDF_SHA256, AEAD_AES_256_GCM)));
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> c1.init(Cipher.ENCRYPT_MODE, kp.getPublic(),
                        HPKEParameterSpec.of(KEM_DHKEM_X25519_HKDF_SHA256, 4, AEAD_AES_256_GCM)));
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> c1.init(Cipher.ENCRYPT_MODE, kp.getPublic(),
                        HPKEParameterSpec.of(KEM_DHKEM_X25519_HKDF_SHA256, KDF_HKDF_SHA256, 4)));

        // No key encap msg for recipient (export only)
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> c2.init(Cipher.DECRYPT_MODE, kp.getPrivate(),
                        HPKEParameterSpec.of(
                                KEM_DHKEM_X25519_HKDF_SHA256, KDF_HKDF_SHA256, EXPORT_ONLY)));

        var aad = "AAD".getBytes(StandardCharsets.UTF_8);

        // HPKE with encryption
        c1.init(Cipher.ENCRYPT_MODE, kp.getPublic());
        Asserts.assertEquals(16, c1.getBlockSize());
        Asserts.assertEquals(116, c1.getOutputSize(100));
        c1.updateAAD(aad);
        var ct = c1.doFinal(new byte[2]);

        c2.init(Cipher.DECRYPT_MODE, kp.getPrivate(),
                defaultParams.encapsulation(c1.getIV()));
        Asserts.assertEquals(16, c2.getBlockSize());
        Asserts.assertEquals(84, c2.getOutputSize(100));
        c2.updateAAD(aad);
        Asserts.assertEqualsByteArray(c2.doFinal(ct), new byte[2]);

        // info and psk
        checkEncryptDecrypt(kp,
                defaultParams.info(info).psk(psk, psk_id),
                defaultParams.info(info).psk(psk, psk_id));

        var kp2 = KeyPairGenerator.getInstance("X25519").generateKeyPair();

        // mod_auth, wrong key type
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> c1.init(Cipher.ENCRYPT_MODE, kp.getPublic(),
                        defaultParams.authKey(kp2.getPublic())));
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> c2.init(Cipher.DECRYPT_MODE, kp.getPrivate(),
                        defaultParams.authKey(kp2.getPrivate())));

        // mod_auth
        checkEncryptDecrypt(kp,
                defaultParams.authKey(kp2.getPrivate()),
                defaultParams.authKey(kp2.getPublic()));

        // check default values
        checkEncryptDecrypt(kp,
                emptyParams,
                defaultParams);

        checkEncryptDecrypt(kp,
                defaultParams,
                defaultParams.info(new byte[0]));

        checkEncryptDecrypt(kp,
                defaultParams,
                defaultParams.psk(null, new byte[0]));

        // HPKEParameters
        var ap = AlgorithmParameters.getInstance("HPKE");
        Asserts.assertThrows(IOException.class, () -> ap.init(new byte[100]));
        Asserts.assertThrows(InvalidParameterSpecException.class,
                () -> ap.init(NamedParameterSpec.X25519));
        Asserts.assertThrows(InvalidParameterSpecException.class,
                () -> ap.init(emptyParams));
        Asserts.assertTrue(ap.toString() == null);

        ap.init(defaultParams);
        var actual = ap.getParameterSpec(HPKEParameterSpec.class);
        Asserts.assertEquals(KEM_DHKEM_X25519_HKDF_SHA256, actual.kem_id());
        Asserts.assertEquals(KDF_HKDF_SHA256, actual.kdf_id());
        Asserts.assertEquals(AEAD_AES_256_GCM, actual.aead_id());
        Asserts.assertEquals(actual.toString(), ap.toString());
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
