/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8253914
 * @library /test/lib
 * @summary Test the equals() and hashCode() of Argon2 derived keys
 */

import java.nio.charset.StandardCharsets;

import java.security.SecureRandom;
import javax.crypto.KDF;
import javax.crypto.SecretKey;
import javax.crypto.spec.Argon2ParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import jdk.test.lib.Asserts;

public class TestDerivedKey {

    private static final byte[] SALT =
            "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

    private static final Argon2ParameterSpec.Builder builder =
        Argon2ParameterSpec.newBuilder().memoryKiB(8).iterations(1)
                .parallelism(1).tagLen(16);

    private static SecretKey derive(String keyAlg, byte[] passwd)
            throws Exception {
        Argon2ParameterSpec params = builder.build(SALT, passwd);
        return KDF.getInstance("Argon2id").deriveKey(keyAlg, params);
    }

    private static void checkEQ(SecretKey k1, SecretKey k2, String desc) {
        Asserts.assertTrue(k1.equals(k2), desc + ": k1 equals k2");
        Asserts.assertTrue(k2.equals(k1), desc + ": k2 equals k1");
        Asserts.assertTrue(k1.hashCode() == k2.hashCode(), desc +
                ": hashCodes " + k1.hashCode() + " vs " + k2.hashCode());
    }

    public static void main(String[] args) throws Exception {
        byte[] passwd = new byte[16];
        SecureRandom sr = new SecureRandom();
        sr.nextBytes(passwd);
        SecretKey key1 = derive("AES", passwd);
        SecretKey key2 = derive("aes", passwd);
        SecretKey keySpec = new SecretKeySpec(key1.getEncoded(), "AeS");

        checkEQ(key1, key2, "key1 and key2");
        checkEQ(key1, keySpec, "key1 and keySpec");

        // use a different key algo
        key2 = derive("Generic", passwd);
        keySpec = new SecretKeySpec(key1.getEncoded(), "Generic");
        Asserts.assertFalse(key2.equals(key1));
        Asserts.assertFalse(keySpec.equals(key1));
        Asserts.assertFalse(key1.equals(null));

        // use a different key encoding
        byte[] passwd2 = passwd.clone();
        passwd2[0] = (byte) (passwd[0] ^ 1);
        key2 = derive("AES", passwd2);
        keySpec = new SecretKeySpec(key2.getEncoded(), "AES");
        Asserts.assertFalse(key2.equals(key1));
        Asserts.assertFalse(keySpec.equals(key1));

        System.out.println("Test passed");
    }
}
