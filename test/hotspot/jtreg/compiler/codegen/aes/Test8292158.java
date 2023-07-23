/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @key randomness
 * @bug 8292158
 * @summary AES-CTR cipher state corruption with AVX-512
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xbatch
 * -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 * compiler.codegen.aes.Test8292158
 */

package compiler.codegen.aes;

import java.util.Arrays;
import java.util.Random;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import compiler.whitebox.CompilerWhiteBoxTest;
import jdk.test.lib.Utils;
import jdk.test.whitebox.code.Compiler;
import jtreg.SkippedException;

public class Test8292158 {
    private static final String ALGO = "AES/CTR/NoPadding";
    private static final int LOOPS = 100000;
    private static final int LEN = 15;

    public static void main(String[] args) throws Exception {
        if (!Compiler.isIntrinsicAvailable(CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION, "com.sun.crypto.provider.CounterMode", "implCrypt", byte[].class, int.class, int.class, byte[].class, int.class)) {
            throw new SkippedException("AES-CTR intrinsic is not available");
        }

        Random random = Utils.getRandomInstance();

        byte[] keyBytes = new byte[32];
        random.nextBytes(keyBytes);
        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");

        byte[] ivBytes = new byte[16];
        random.nextBytes(ivBytes);
        IvParameterSpec iv = new IvParameterSpec(ivBytes);

        Cipher encryptCipher = Cipher.getInstance(ALGO);
        Cipher decryptCipher = Cipher.getInstance(ALGO);

        encryptCipher.init(Cipher.ENCRYPT_MODE, key, iv);
        decryptCipher.init(Cipher.DECRYPT_MODE, key, iv);

        byte[] original = new byte[LEN];
        byte[] encrypted = new byte[LEN];
        byte[] decrypted = new byte[LEN];

        for (int i = 0; i < LOOPS; i++) {
            random.nextBytes(original);
            encryptCipher.doFinal(original, 0, LEN, encrypted);

            // Cipher must be used at least 3 times
            decryptCipher.update(encrypted, 0, 1, decrypted, 0);
            decryptCipher.update(encrypted, 1, 1, decrypted, 1);
            decryptCipher.doFinal(encrypted, 2, LEN - 2, decrypted, 2);

            if (!Arrays.equals(original, decrypted)) {
                throw new Exception("array mismatch");
            }
        }
    }
}
