/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4953553 8002277 8288050
 * @summary Ensure that InvalidKeyException is thrown when decrypting
 * without parameters as javadoc has stated.
 * @author Valerie Peng
 */

import java.io.*;
import java.util.*;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import javax.crypto.*;
import javax.crypto.spec.*;
import javax.crypto.interfaces.PBEKey;

public class DecryptWithoutParameters {

    private static String[] PBES1ALGOS = {
        "PBEWithMD5AndDES",
        "PBEWithSHA1AndDESede",
        "PBEWithSHA1AndRC2_40",
        "PBEWithSHA1AndRC2_128",
        "PBEWithSHA1AndRC4_40",
        "PBEWithSHA1AndRC4_128",
    };

    private static String[] PBES2ALGOS = {
        "PBEWithHmacSHA1AndAES_128",
        "PBEWithHmacSHA224AndAES_128",
        "PBEWithHmacSHA256AndAES_128",
        "PBEWithHmacSHA384AndAES_128",
        "PBEWithHmacSHA512AndAES_128",
        "PBEWithHmacSHA512/224AndAES_128",
        "PBEWithHmacSHA512/256AndAES_128",
    };

    // return true if Cipher.init() fails with
    // InvalidAlgorithmParameterException
    private static boolean test(Cipher c, SecretKey key,
        AlgorithmParameterSpec spec) throws InvalidKeyException {
        System.out.println("Testing " + c.getAlgorithm());
        try {
            c.init(Cipher.DECRYPT_MODE, key, spec);
            System.out.println("=> failed");
            return false;
        } catch (InvalidAlgorithmParameterException e) {
            System.out.println("=> ok, got expected IAPE " + e);
            return true;
        }
    }

    static final class MyPBEKey implements PBEKey {
        private String algo;
        private PBEKeySpec spec;

        MyPBEKey(String algo, PBEKeySpec spec) {
            this.algo = algo;
            this.spec = spec;
        }
        public int getIterationCount() {
            return spec.getIterationCount();
        }
        public char[] getPassword() {
            return spec.getPassword();
        }
        public byte[] getSalt() {
            return spec.getSalt();
        }
        public void destroy() {
            spec.clearPassword();
            spec = null;
        }
        public boolean isDestroyed() {
            return spec == null;
        }
        public String getAlgorithm() {
            return algo;
        }
        public byte[] getEncoded() {
            return new byte[5];
        }
        public String getFormat() {
            // not used
            return "Proprietary";
        }
    }

    public static void main(String argv[]) throws Exception {
        boolean status = true;

        for (String algo : PBES1ALGOS) {
            Cipher cipher = Cipher.getInstance(algo,
                                    System.getProperty("test.provider.name", "SunJCE"));
            SecretKey key = new SecretKeySpec(new byte[5], algo);
            status = status && test(cipher, key, null);
        }

        byte[] salt = "atleast8bytes".getBytes();
        int iterCount = 123456;
        PBEParameterSpec spec = new PBEParameterSpec(salt, iterCount);
        for (String algo : PBES2ALGOS) {
            Cipher cipher = Cipher.getInstance(algo,
                                    System.getProperty("test.provider.name", "SunJCE"));
            SecretKey key = new SecretKeySpec(new byte[5], algo);
            PBEKey key2 = new MyPBEKey(algo,
                new PBEKeySpec("phrase".toCharArray(), salt, iterCount));
            // null param
            status = status && test(cipher, key, null);
            status = status && test(cipher, key2, null);
            // param has salt and iterCount but missing iv
            status = status && test(cipher, key, spec);
            status = status && test(cipher, key2, spec);
        }
        if (!status) {
            throw new Exception("One or more test failed");
        }
        System.out.println("All tests passed");
    }
}
