/*
 * Copyright (c) 2025, Google LLC. All rights reserved.
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

/*
 * @test
 * @bug 8371864
 * @run main/othervm/timeout=600 TestGCMSplitBound
 * @requires (os.simpleArch == "x64" & (vm.cpu.features ~= ".*avx2.*" |
 *                                      vm.cpu.features ~= ".*avx512.*"))
 * @summary Test GaloisCounterMode.implGCMCrypt0 AVX512/AVX2 intrinsics.
 */

import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.time.Duration;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class TestGCMSplitBound {

    static final SecureRandom SECURE_RANDOM = newDefaultSecureRandom();

    private static SecureRandom newDefaultSecureRandom() {
        SecureRandom retval = new SecureRandom();
        retval.nextLong(); // force seeding
        return retval;
    }

    private static byte[] randBytes(int size) {
        byte[] rand = new byte[size];
        SECURE_RANDOM.nextBytes(rand);
        return rand;
    }

    private static final int IV_SIZE_IN_BYTES = 12;
    private static final int TAG_SIZE_IN_BYTES = 16;

    private Cipher getCipher(final byte[] key, final byte[] aad,
                             final byte[] nonce, int mode)
        throws Exception {
        SecretKey keySpec = new SecretKeySpec(key, "AES");
        AlgorithmParameterSpec params =
            new GCMParameterSpec(8 * TAG_SIZE_IN_BYTES, nonce, 0, nonce.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, keySpec, params);
        if (aad != null && aad.length != 0) {
            cipher.updateAAD(aad);
        }
        return cipher;
    }

    private byte[] gcmEncrypt(final byte[] key, final byte[] plaintext,
                              final byte[] aad)
        throws Exception {
        byte[] nonce = randBytes(IV_SIZE_IN_BYTES);
        Cipher cipher = getCipher(key, aad, nonce, Cipher.ENCRYPT_MODE);
        int outputSize = cipher.getOutputSize(plaintext.length);
        int len = IV_SIZE_IN_BYTES + outputSize;
        byte[] output = new byte[len];
        System.arraycopy(nonce, 0, output, 0, IV_SIZE_IN_BYTES);
        cipher.doFinal(plaintext, 0, plaintext.length, output,
                       IV_SIZE_IN_BYTES);
        return output;
    }

    private byte[] gcmDecrypt(final byte[] key, final byte[] ciphertext,
                              final byte[] aad)
        throws Exception {
        byte[] nonce = new byte[IV_SIZE_IN_BYTES];
        System.arraycopy(ciphertext, 0, nonce, 0, IV_SIZE_IN_BYTES);
        Cipher cipher = getCipher(key, aad, nonce, Cipher.DECRYPT_MODE);
        return cipher.doFinal(ciphertext, IV_SIZE_IN_BYTES,
                              ciphertext.length - IV_SIZE_IN_BYTES);
    }

    // x86-64 parallel intrinsic data size
    private static final int PARALLEL_LEN = 512;
    // max data size for x86-64 intrinsic
    private static final int SPLIT_LEN = 1048576; // 1MB

    private void encryptAndDecrypt(byte[] key, byte[] aad, byte[] message,
                                   int messageSize)
        throws Exception {
        byte[] ciphertext = gcmEncrypt(key, message, aad);
        byte[] decrypted = gcmDecrypt(key, ciphertext, aad);
        if (ciphertext == null) {
            throw new RuntimeException("ciphertext is null");
        }
        if (Arrays.compare(decrypted, 0, messageSize,
                           message, 0, messageSize) != 0) {
            throw new RuntimeException(
                 "Decrypted message is different from the original message");
        }
    }

    private void run() throws Exception {
        byte[] aad = randBytes(20);
        byte[] key = randBytes(16);
        // Force JIT.
        for (int i = 0; i < 100000; i++) {
            byte[] message = randBytes(PARALLEL_LEN);
            encryptAndDecrypt(key, aad, message, PARALLEL_LEN);
        }
        for (int messageSize = SPLIT_LEN - 300; messageSize <= SPLIT_LEN + 300;
                                                messageSize++) {
            byte[] message = randBytes(messageSize);
            try {
                encryptAndDecrypt(key, aad, message, messageSize);
            } catch (Exception e) {
                throw new RuntimeException("Failed for messageSize "
                        + Integer.toHexString(messageSize), e);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        TestGCMSplitBound test = new TestGCMSplitBound();
        for (int i = 0; i < 3; i++) {
            test.run();
        }
    }
}
