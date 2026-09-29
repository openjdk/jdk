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
import javax.crypto.SecretKey;
import javax.crypto.spec.HPKEParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.NamedParameterSpec;

import static javax.crypto.spec.HPKEParameterSpec.AEAD_AES_256_GCM;
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

        var kp = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        var info = "info".getBytes(StandardCharsets.UTF_8);
        var psk = new SecretKeySpec(new byte[32], "ONE");
        var shortKey = new SecretKeySpec(new byte[31], "ONE");
        var psk_id = "psk_id".getBytes(StandardCharsets.UTF_8);
        var emptyKey = new SecretKey() {
            public String getAlgorithm() { return "GENERIC"; }
            public String getFormat() { return "RAW"; }
            public byte[] getEncoded() { return new byte[0]; }
        };

        // HPKEParameterSpec

        // A typical spec
        var spec = HPKEParameterSpec.of(
                KEM_DHKEM_X25519_HKDF_SHA256,
                KDF_HKDF_SHA256,
                AEAD_AES_256_GCM);
        Asserts.assertEQ(spec.kem_id(), KEM_DHKEM_X25519_HKDF_SHA256);
        Asserts.assertEQ(spec.kdf_id(), KDF_HKDF_SHA256);
        Asserts.assertEQ(spec.aead_id(), AEAD_AES_256_GCM);
        Asserts.assertEQ(spec.authKey(), null);
        Asserts.assertEQ(spec.encapsulation(), null);
        Asserts.assertEqualsByteArray(spec.info(), new byte[0]);
        Asserts.assertEQ(spec.psk(), null);
        Asserts.assertEqualsByteArray(spec.psk_id(), new byte[0]);

        // A fake spec but still valid
        var specZero = HPKEParameterSpec.of(0, 0, 0);
        Asserts.assertEQ(specZero.kem_id(), 0);
        Asserts.assertEQ(specZero.kdf_id(), 0);
        Asserts.assertEQ(specZero.aead_id(), 0);
        Asserts.assertEQ(specZero.authKey(), null);
        Asserts.assertEQ(specZero.encapsulation(), null);
        Asserts.assertEqualsByteArray(specZero.info(), new byte[0]);
        Asserts.assertEQ(specZero.psk(), null);
        Asserts.assertEqualsByteArray(specZero.psk_id(), new byte[0]);

        // identifiers
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

        // auth key
        Asserts.assertTrue(spec.withAuthKey(kp.getPrivate()).authKey() != null);
        Asserts.assertTrue(spec.withAuthKey(kp.getPublic()).authKey() != null);
        Asserts.assertThrows(NullPointerException.class, () -> spec.withAuthKey(null));

        // info
        Asserts.assertEqualsByteArray(spec.withInfo(info).info(), info);
        Asserts.assertThrows(NullPointerException.class, () -> spec.withInfo(null));
        Asserts.assertThrows(IllegalArgumentException.class, () -> spec.withInfo(new byte[0]));

        // encapsulation
        Asserts.assertEqualsByteArray(spec.withEncapsulation(info).encapsulation(), info);
        Asserts.assertThrows(NullPointerException.class, () -> spec.withEncapsulation(null));
        Asserts.assertTrue(spec.withEncapsulation(new byte[0]).encapsulation().length == 0); // not emptiness check (yet)

        // psk_id and psk
        Asserts.assertEqualsByteArray(spec.withPsk(psk, psk_id).psk().getEncoded(), psk.getEncoded());
        Asserts.assertEqualsByteArray(spec.withPsk(psk, psk_id).psk_id(), psk_id);
        Asserts.assertThrows(NullPointerException.class, () -> spec.withPsk(psk, null));
        Asserts.assertThrows(NullPointerException.class, () -> spec.withPsk(null, psk_id));
        Asserts.assertThrows(NullPointerException.class, () -> spec.withPsk(null, null));
        Asserts.assertThrows(IllegalArgumentException.class, () -> spec.withPsk(psk, new byte[0]));
        Asserts.assertThrows(IllegalArgumentException.class, () -> spec.withPsk(emptyKey, psk_id));
        Asserts.assertThrows(IllegalArgumentException.class, () -> spec.withPsk(shortKey, psk_id));

        // toString
        Asserts.assertTrue(spec.toString().contains("kem_id=32, kdf_id=1, aead_id=2"));
        Asserts.assertTrue(spec.toString().contains("info=(empty),"));
        Asserts.assertTrue(spec.withInfo(new byte[3]).toString().contains("info=000000,"));
        Asserts.assertTrue(spec.withInfo("info".getBytes(StandardCharsets.UTF_8))
                .toString().contains("info=696e666f (\"info\"),"));
        Asserts.assertTrue(spec.withInfo("\"info\"".getBytes(StandardCharsets.UTF_8))
                .toString().contains("info=22696e666f22,"));
        Asserts.assertTrue(spec.withInfo("'info'".getBytes(StandardCharsets.UTF_8))
                .toString().contains("info=27696e666f27 (\"'info'\"),"));
        Asserts.assertTrue(spec.withInfo("i\\n\\f\\o".getBytes(StandardCharsets.UTF_8))
                .toString().contains("info=695c6e5c665c6f (\"i\\n\\f\\o\"),"));
        Asserts.assertTrue(spec.toString().contains("mode_base}"));
        Asserts.assertTrue(spec.withPsk(psk, psk_id).toString().contains("mode_psk}"));
        Asserts.assertTrue(spec.withAuthKey(kp.getPrivate()).toString().contains("mode_auth}"));
        Asserts.assertTrue(spec.withAuthKey(kp.getPrivate()).withPsk(psk, psk_id).toString().contains("mode_auth_psk}"));

        var c1 = Cipher.getInstance("HPKE");

        Asserts.assertThrows(NoSuchAlgorithmException.class, () -> Cipher.getInstance("HPKE/None/NoPadding"));

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

        c1.init(Cipher.ENCRYPT_MODE, kp.getPublic(), spec);
        var encap = c1.getIV();

        // Does not support WRAP and UNWRAP mode
        Asserts.assertThrows(UnsupportedOperationException.class,
                () -> c1.init(Cipher.WRAP_MODE, kp.getPublic(), spec));
        Asserts.assertThrows(UnsupportedOperationException.class,
                () -> c1.init(Cipher.UNWRAP_MODE, kp.getPublic(), spec));

        // Nulls
        Asserts.assertThrows(InvalidKeyException.class,
                () -> c1.init(Cipher.ENCRYPT_MODE, null, spec));
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> c1.init(Cipher.ENCRYPT_MODE, kp.getPublic(), (HPKEParameterSpec) null));

        // Cannot init sender with private key
        Asserts.assertThrows(InvalidKeyException.class,
                () -> c1.init(Cipher.ENCRYPT_MODE, kp.getPrivate(), spec));

        // Cannot provide key encap msg to sender
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> c1.init(Cipher.ENCRYPT_MODE, kp.getPublic(),
                        spec.withEncapsulation(encap)));

        // Cannot init without HPKEParameterSpec
        Asserts.assertThrows(InvalidKeyException.class,
                () -> c1.init(Cipher.ENCRYPT_MODE, kp.getPublic()));
        Asserts.assertThrows(InvalidKeyException.class,
                () -> c1.init(Cipher.DECRYPT_MODE, kp.getPrivate()));

        // Cannot init with a spec not HPKEParameterSpec
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> c1.init(Cipher.ENCRYPT_MODE, kp.getPublic(),
                        NamedParameterSpec.X25519));
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> c1.init(Cipher.DECRYPT_MODE, kp.getPrivate(),
                        NamedParameterSpec.X25519));

        // Cannot init recipient with public key
        Asserts.assertThrows(InvalidKeyException.class,
                () -> c1.init(Cipher.DECRYPT_MODE, kp.getPublic(),
                        spec.withEncapsulation(new byte[32])));
        // Cannot provide key encap msg to sender
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> c1.init(Cipher.ENCRYPT_MODE, kp.getPublic(), spec.withEncapsulation(encap)));
        // Must provide key encap msg to recipient
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> c1.init(Cipher.DECRYPT_MODE, kp.getPrivate(), spec));

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

        // HPKE
        checkEncryptDecrypt(kp, spec, spec);

        // extra features
        var kp2 = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        checkEncryptDecrypt(kp,
                spec.withInfo(info),
                spec.withInfo(info));
        checkEncryptDecrypt(kp,
                spec.withPsk(psk, psk_id),
                spec.withPsk(psk, psk_id));
        checkEncryptDecrypt(kp,
                spec.withAuthKey(kp2.getPrivate()),
                spec.withAuthKey(kp2.getPublic()));
        checkEncryptDecrypt(kp,
                spec.withInfo(info).withPsk(psk, psk_id).withAuthKey(kp2.getPrivate()),
                spec.withInfo(info).withPsk(psk, psk_id).withAuthKey(kp2.getPublic()));

        // wrong keys
        var kpRSA = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var kpEC = KeyPairGenerator.getInstance("EC").generateKeyPair();

        Asserts.assertThrows(InvalidKeyException.class,
                () -> c1.init(Cipher.ENCRYPT_MODE, kpRSA.getPublic(), spec));
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> c1.init(Cipher.ENCRYPT_MODE, kpEC.getPublic(), spec));

        // mod_auth, wrong key type
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> c1.init(Cipher.ENCRYPT_MODE, kp.getPublic(),
                        spec.withAuthKey(kp2.getPublic())));
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> c1.init(Cipher.DECRYPT_MODE, kp.getPrivate(),
                        spec.withAuthKey(kp2.getPrivate())));
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> c1.init(Cipher.ENCRYPT_MODE, kp.getPublic(),
                        spec.withAuthKey(kpRSA.getPrivate())));
        Asserts.assertThrows(InvalidAlgorithmParameterException.class,
                () -> c1.init(Cipher.ENCRYPT_MODE, kp.getPublic(),
                        spec.withAuthKey(kpEC.getPrivate())));
    }

    static void checkEncryptDecrypt(KeyPair kp, HPKEParameterSpec ps,
            HPKEParameterSpec pr) throws Exception {

        var c1 = Cipher.getInstance("HPKE");
        var c2 = Cipher.getInstance("HPKE");
        var aad = "AAD".getBytes(StandardCharsets.UTF_8);

        c1.init(Cipher.ENCRYPT_MODE, kp.getPublic(), ps);
        Asserts.assertEquals(16, c1.getBlockSize());
        Asserts.assertEquals(116, c1.getOutputSize(100));
        c1.updateAAD(aad);
        var ct = c1.doFinal(new byte[2]);

        c2.init(Cipher.DECRYPT_MODE, kp.getPrivate(),
                pr.withEncapsulation(c1.getIV()));
        Asserts.assertEquals(16, c2.getBlockSize());
        Asserts.assertEquals(84, c2.getOutputSize(100));
        c2.updateAAD(aad);
        Asserts.assertEqualsByteArray(c2.doFinal(ct), new byte[2]);
    }
}
