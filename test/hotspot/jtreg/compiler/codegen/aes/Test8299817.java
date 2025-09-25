/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023 SAP SE. All rights reserved.
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
 *
 */

/**
 * @test
 * @key randomness
 * @bug 8299817
 * @summary AES-CTR cipher failure with multiple short (< 16 bytes) update calls.
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xbatch
 * -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 * compiler.codegen.aes.Test8299817
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

public class Test8299817 {
    private static final String ALGO = "AES/CTR/NoPadding";
    private static final int LOOPS        = 20000;
    private static final int WARMUP_LOOPS = 10000;
    private static final int LEN_INC   = 5;
    private static final int LEN_STEPS = 13;
    private static final int LEN_MAX   = LEN_INC*LEN_STEPS;
    private static final int SEG_INC   = 3;
    private static final int SEG_MAX   = 11;
    private static final int SHOW_ARRAY_LIMIT = 72;
    private static final boolean DEBUG_MODE = false;

    public static void main(String[] args) throws Exception {
        if (!DEBUG_MODE) {
            if (!Compiler.isIntrinsicAvailable(CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION,
                                               "com.sun.crypto.provider.CounterMode", "implCrypt",
                                                byte[].class, int.class, int.class, byte[].class, int.class)
               ) {
                throw new SkippedException("AES-CTR intrinsic is not available");
            }
        }

        Random random = Utils.getRandomInstance();

        // Create secret key
        byte[] keyBytes = new byte[32];
        random.nextBytes(keyBytes);
        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");

        // Create initial counter
        byte[] ivBytes = new byte[16];
        random.nextBytes(ivBytes);
        if (DEBUG_MODE) {
            for (int i = 0; i < 16; i++) {
                ivBytes[i] = (byte)0;
            }
            ivBytes[15] = (byte)1;
        }
        IvParameterSpec iv = new IvParameterSpec(ivBytes);

        // Create cipher objects and initialize
        Cipher encryptCipher = Cipher.getInstance(ALGO);
        Cipher decryptCipher = Cipher.getInstance(ALGO);

        encryptCipher.init(Cipher.ENCRYPT_MODE, key, iv);
        decryptCipher.init(Cipher.DECRYPT_MODE, key, iv);

        // Create plaintext, ciphertext, and encrypted counter (reference copy)
        byte[] original           = new byte[LEN_MAX];
        byte[] original_encrypted = new byte[LEN_MAX];
        byte[] counter_encrypted  = new byte[LEN_MAX];
        // Retrieve the encrypted counter
        if (DEBUG_MODE) {
            for (int i = 0; i < LEN_MAX; i++) {
                original[i] = (byte)0;
            }
            encryptCipher.doFinal(original, 0, LEN_MAX, counter_encrypted);
        }
        // Create the encrypted message reference (no JIT, no intrinsic involved)
        if (DEBUG_MODE) {
            for (int i = 0; i < LEN_MAX; i++) {
                original[i] = (byte)i;
            }
            encryptCipher.doFinal(original, 0, LEN_MAX, original_encrypted);
        }
        if (DEBUG_MODE) {
            showArray(original,           original.length,           "original:           ");
            showArray(original_encrypted, original_encrypted.length, "original_encrypted: ");
            showArray(counter_encrypted,  counter_encrypted.length,  "counter_encrypted:  ");
        }

        // Warmup to have everything compiled
        System.out.println("Warming up, " + WARMUP_LOOPS + " iterations...");
        byte[] work_encrypted = new byte[LEN_MAX];
        byte[] work_decrypted = new byte[LEN_MAX];
        byte[] varlen         = new byte[LEN_MAX*2];

        for (int i = 0; i < WARMUP_LOOPS; i++) {
            boolean failed = false;
            if (!DEBUG_MODE) {
                random.nextBytes(original);
            }
            encryptCipher.doFinal(original, 0, LEN_MAX, work_encrypted);

            random.nextBytes(varlen);
            for (int j = 0; j < LEN_MAX; j++) {
                int len1 = (varlen[2*j] & 0x0f) + 1;
                decryptCipher.update(work_encrypted,   0, len1,         work_decrypted,  0);
                for (int k = 0; k < len1; k++) {
                    if (original[k] != work_decrypted[k]) {
                        if (!failed) {
                            failed = true;
                            System.out.println("-------------------");
                        }
                        System.out.println("Decrypt failure (warmup, update): LEN(" +
                                           LEN_MAX + "), iteration (" + i + "), k = " + k);
                    }
                }
                int len2 = (varlen[2*j+1] & 0x0f) + 1;
                decryptCipher.update(work_encrypted, len1, len2,         work_decrypted, len1);
                for (int k = len1; k < len1+len2; k++) {
                    if (original[k] != work_decrypted[k]) {
                        if (!failed) {
                            failed = true;
                            System.out.println("-------------------");
                        }
                        System.out.println("Decrypt failure (warmup, update): LEN(" +
                                           LEN_MAX + "), iteration (" + i + "), k = " + k);
                    }
                }
                decryptCipher.doFinal(work_encrypted, len1+len2, LEN_MAX-len1-len2, work_decrypted, len1+len2);
                for (int k = len1+len2; k < LEN_MAX; k++) {
                    if (original[k] != work_decrypted[k]) {
                        if (!failed) {
                            failed = true;
                            System.out.println("-------------------");
                        }
                        System.out.println("Decrypt failure (warmup, doFinal): LEN(" +
                                           LEN_MAX + "), iteration (" + i + "), k = " + k);
                    }
                }
            }
            if (!compareArrays(work_decrypted, original, false)) {
                System.out.println("Warmup encrypt/decrypt failure during iteration " + i + " of LEN " + LEN_MAX);
                compareArrays(work_decrypted, original, true);
                showArray(work_encrypted,    work_encrypted.length,    "encrypted:");
                showArray(counter_encrypted, counter_encrypted.length, "ctr_enc:  ");
                if (!DEBUG_MODE) {
                    System.exit(1);
                }
            }
        }

        System.out.println("Testing, " + LOOPS + " iterations...");
        for (int LEN = 1; LEN < LEN_MAX; LEN += LEN_INC) {
            work_encrypted = new byte[LEN];
            work_decrypted = new byte[LEN];

            for (int i = 0; i < LOOPS; i++) {
                boolean failed = false;
                random.nextBytes(original);
                encryptCipher.doFinal(original, 0, LEN, work_encrypted);

                int ix = 0;
                for (int SEG = 0; (SEG < SEG_MAX) && (ix + SEG_INC < LEN); SEG++) {
                    decryptCipher.update(work_encrypted, ix, SEG_INC, work_decrypted, ix);
                    for (int k = ix; k < ix + SEG_INC; k++) {
                        if (original[k] != work_decrypted[k]) {
                            if (!failed) {
                                failed = true;
                                System.out.println("-------------------");
                            }
                            System.out.println("Decrypt failure (update): LEN(" + LEN + "), iteration " +
                                               i + ", SEG(" + SEG + "), SEG_INC(" + SEG_INC + "), k = " + k);
                        }
                    }
                    ix += SEG_INC;
                }

                decryptCipher.doFinal(work_encrypted, ix, LEN - ix, work_decrypted, ix);
                if (!compareArrays(work_decrypted, original, false)) {
                    if (!failed) {
                        failed = true;
                        System.out.println("-------------------");
                    }
                    System.out.println("While decrypting the remaining " + (LEN - ix) +
                                       "(" + LEN + ") bytes of CT, iteration " + i);
                    System.out.println("Decrypt failure (doFinal): LEN(" + LEN +
                                       "), SEG_INC(" + SEG_INC + "), SEG_MAX(" + SEG_MAX + ")");
                    showArray(work_encrypted, work_encrypted.length, "encrypted:");
                    compareArrays(work_decrypted, original, true);
                    if (!DEBUG_MODE) {
                        System.exit(1);
                    }
                }
            }
        }
    }

    static void showArray(byte b[], int len, String name) {
        System.out.format("%s [%d]: ", name, b.length);
        for (int i = 0; i < Math.min(len, SHOW_ARRAY_LIMIT); i++) {
            System.out.format("%02x ", b[i] & 0xff);
        }
        System.out.println();
    }

    static boolean compareArrays(byte b[], byte exp[], boolean print) {
        boolean equal = true;
        int len = (b.length <= exp.length) ? b.length : exp.length;
        for (int i = 0; i < len; i++) {
            equal &= b[i] == exp[i];
            if (!equal) {
                if (print) {
                    System.out.format("encrypt/decrypt error at index %d: got %02x, expected %02x\n",
                                      i, b[i] & 0xff, exp[i] & 0xff);
                    showArray(b,   len, "result:   ");
                    showArray(exp, len, "expected: ");
                }
                return equal;
            }
        }
        return equal;
    }
}
