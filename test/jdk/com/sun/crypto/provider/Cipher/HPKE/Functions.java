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
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.List;

import static javax.crypto.spec.HPKEParameterSpec.AEAD_AES_128_GCM;
import static javax.crypto.spec.HPKEParameterSpec.AEAD_AES_256_GCM;
import static javax.crypto.spec.HPKEParameterSpec.AEAD_CHACHA20_POLY1305;
import static javax.crypto.spec.HPKEParameterSpec.KDF_HKDF_SHA256;
import static javax.crypto.spec.HPKEParameterSpec.KDF_HKDF_SHA384;
import static javax.crypto.spec.HPKEParameterSpec.KDF_HKDF_SHA512;
import static javax.crypto.spec.HPKEParameterSpec.KEM_DHKEM_P_256_HKDF_SHA256;
import static javax.crypto.spec.HPKEParameterSpec.KEM_DHKEM_P_384_HKDF_SHA384;
import static javax.crypto.spec.HPKEParameterSpec.KEM_DHKEM_P_521_HKDF_SHA512;
import static javax.crypto.spec.HPKEParameterSpec.KEM_DHKEM_X25519_HKDF_SHA256;
import static javax.crypto.spec.HPKEParameterSpec.KEM_DHKEM_X448_HKDF_SHA512;

/*
 * @test
 * @bug 8325448
 * @library /test/lib
 * @summary HPKE running with different keys
 */
public class Functions {

    record Params(String name, int kem) {}
    static List<Params> PARAMS = List.of(
            new Params("secp256r1", KEM_DHKEM_P_256_HKDF_SHA256),
            new Params("secp384r1", KEM_DHKEM_P_384_HKDF_SHA384),
            new Params("secp521r1", KEM_DHKEM_P_521_HKDF_SHA512),
            new Params("X25519", KEM_DHKEM_X25519_HKDF_SHA256),
            new Params("X448", KEM_DHKEM_X448_HKDF_SHA512)
    );

    public static void main(String[] args) throws Exception {

        var msg = "hello".getBytes(StandardCharsets.UTF_8);
        var msg2 = "goodbye".getBytes(StandardCharsets.UTF_8);
        var info = "info".getBytes(StandardCharsets.UTF_8);
        var psk = new SecretKeySpec("K".repeat(32).getBytes(StandardCharsets.UTF_8), "Generic");
        var psk_id = "psk1".getBytes(StandardCharsets.UTF_8);

        for (var param : PARAMS) {
            var c1 = Cipher.getInstance("HPKE");
            var c2 = Cipher.getInstance("HPKE");
            var kp = genKeyPair(param.name());
            var kp2 = genKeyPair(param.name());
            for (var kdf : List.of(KDF_HKDF_SHA256, KDF_HKDF_SHA384, KDF_HKDF_SHA512)) {
                for (var aead : List.of(AEAD_AES_256_GCM, AEAD_AES_128_GCM, AEAD_CHACHA20_POLY1305)) {

                    var params = HPKEParameterSpec.of(param.kem, kdf, aead);
                    System.out.println(params);

                    c1.init(Cipher.ENCRYPT_MODE, kp.getPublic(), params);
                    c2.init(Cipher.DECRYPT_MODE, kp.getPrivate(), params.withEncapsulation(c1.getIV()));
                    Asserts.assertEqualsByteArray(msg, c2.doFinal(c1.doFinal(msg)));
                    Asserts.assertEqualsByteArray(msg2, c2.doFinal(c1.doFinal(msg2)));

                    c1.init(Cipher.ENCRYPT_MODE, kp.getPublic(), params
                            .withAuthKey(kp2.getPrivate())
                            .withInfo(info)
                            .withPsk(psk, psk_id));
                    c2.init(Cipher.DECRYPT_MODE, kp.getPrivate(), params
                            .withAuthKey(kp2.getPublic())
                            .withInfo(info)
                            .withPsk(psk, psk_id)
                            .withEncapsulation(c1.getIV()));
                    Asserts.assertEqualsByteArray(msg, c2.doFinal(c1.doFinal(msg)));
                    Asserts.assertEqualsByteArray(msg2, c2.doFinal(c1.doFinal(msg2)));
                }
            }
        }
    }

    static KeyPair genKeyPair(String name) throws Exception {
        if (name.startsWith("secp")) {
            var g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec(name));
            return g.generateKeyPair();
        } else {
            return KeyPairGenerator.getInstance(name).generateKeyPair();
        }
    }
}
