/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Check for 128-bit AES/CTR wraparound
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xbatch
 * -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 * compiler.codegen.aes.CTR_Wraparound 32
 * @run main/othervm -Xbatch
 * -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 * compiler.codegen.aes.CTR_Wraparound 1009
 * @run main/othervm -Xbatch
 * -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 * compiler.codegen.aes.CTR_Wraparound 2048
 */

package compiler.codegen.aes;

import java.util.Arrays;
import java.util.Random;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import compiler.whitebox.CompilerWhiteBoxTest;
import jdk.test.whitebox.code.Compiler;
import jdk.test.lib.Utils;
import jtreg.SkippedException;

public class CTR_Wraparound {
    private static final String ALGO = "AES/CTR/NoPadding";
    private static final int LOOPS = 100000;

    public static void main(String[] args) throws Exception {
        int length = Integer.parseInt(args[0]);
        int maxOffset = 60;
        if (args.length > 1) {
            maxOffset = Integer.parseInt(args[1]);
            System.out.println("InitialOffset = " + maxOffset);
        }

        if (!Compiler.isIntrinsicAvailable(CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION, "com.sun.crypto.provider.CounterMode", "implCrypt", byte[].class, int.class, int.class, byte[].class, int.class)) {
            throw new SkippedException("AES-CTR intrinsic is not available");
        }

        Random random = Utils.getRandomInstance();

        byte[] keyBytes = new byte[32];
        Arrays.fill(keyBytes, (byte)0xff);
        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");

        byte[] ivBytes = new byte[16];

        Arrays.fill(ivBytes, (byte)0xff);

        byte[][] plaintext = new byte[maxOffset][];
        byte[][] ciphertext = new byte[maxOffset][];

        for (int offset = 0; offset < maxOffset; offset++) {
            ivBytes[ivBytes.length - 1] = (byte)-offset;
            IvParameterSpec iv = new IvParameterSpec(ivBytes);

            Cipher encryptCipher = Cipher.getInstance(ALGO);
            Cipher decryptCipher = Cipher.getInstance(ALGO);

            encryptCipher.init(Cipher.ENCRYPT_MODE, key, iv);
            decryptCipher.init(Cipher.DECRYPT_MODE, key, iv);

            plaintext[offset] = new byte[length];
            ciphertext[offset] = new byte[length];
            random.nextBytes(plaintext[offset]);

            byte[] decrypted = new byte[length];

            encryptCipher.doFinal(plaintext[offset], 0, length, ciphertext[offset]);
            decryptCipher.doFinal(ciphertext[offset], 0, length, decrypted);

            if (!Arrays.equals(plaintext[offset], decrypted)) {
                throw new Exception("mismatch in setup at offset " + offset);
            }
        }

        for (int offset = 0; offset < maxOffset; offset++) {
            ivBytes[ivBytes.length - 1] = (byte)-offset;
            IvParameterSpec iv = new IvParameterSpec(ivBytes);

            Cipher encryptCipher = Cipher.getInstance(ALGO);

            encryptCipher.init(Cipher.ENCRYPT_MODE, key, iv);

            byte[] encrypted = new byte[length];

            for (int i = 0; i < LOOPS; i++) {
                encryptCipher.doFinal(plaintext[offset], 0, length, encrypted);
                if (!Arrays.equals(ciphertext[offset], encrypted)) {
                    throw new Exception("array mismatch at offset " + offset
                                        + " with length " + length);
                }
            }
        }
    }
}
