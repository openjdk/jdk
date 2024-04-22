/*
 * Copyright (c) 2024, Red Hat, Inc.
 *
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

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.Provider;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.IntStream;

/*
 * @test
 * @bug 8330842
 * @summary test AES CTS multipart operations with SunPKCS11
 * @library /test/lib ..
 * @run main/othervm/timeout=120 TestCipherTextStealingMultipart
 */

public class TestCipherTextStealingMultipart extends PKCS11Test {
    private static final String LF = System.lineSeparator();
    private static final String ALGORITHM = "AES/CTS/NoPadding";
    private static final int BLOCK_SIZE = 16;
    private static final Key KEY =
            new SecretKeySpec("AbCdEfGhIjKlMnOp".getBytes(), "AES");
    private static final IvParameterSpec IV =
            new IvParameterSpec("1234567890aBcDeF".getBytes());

    private static final StringBuilder chunksDesc = new StringBuilder();
    private static Provider sunPKCS11;
    private static Cipher sunJCECipher;

    private static byte[][] generateChunks(int totalLength, int[] chunkSizes) {
        chunksDesc.setLength(0);
        chunksDesc.append("Testing with ").append(totalLength)
                .append(" bytes distributed in ").append(chunkSizes.length)
                .append(" multipart updates:").append(LF);
        int byteIdx = 0;
        byte[][] plaintextChunks = new byte[chunkSizes.length][];
        for (int chunkIdx = 0; chunkIdx < chunkSizes.length; chunkIdx++) {
            byte[] chunk = new byte[chunkSizes[chunkIdx]];
            for (int i = 0; i < chunk.length; i++) {
                chunk[i] = (byte) ('A' + byteIdx++ / BLOCK_SIZE);
            }
            chunksDesc.append("  ").append(repr(chunk)).append(LF);
            plaintextChunks[chunkIdx] = chunk;
        }
        return plaintextChunks;
    }

    private static byte[] computeExpected(byte[] jointPlaintext)
            throws Exception {
        byte[] ciphertext = sunJCECipher.doFinal(jointPlaintext);
        if (ciphertext.length != jointPlaintext.length) {
            throw new Exception("In CTS mode, ciphertext and plaintext should" +
                    " have the same length. However, SunJCE's CTS cipher " +
                    "returned a ciphertext of " + ciphertext.length + " bytes" +
                    " and plaintext has " + jointPlaintext.length + " bytes.");
        }
        return ciphertext;
    }

    private enum CheckType {CIPHERTEXT, PLAINTEXT}

    private enum OutputType {BYTE_ARRAY, DIRECT_BYTE_BUFFER}

    private static void check(CheckType checkType, OutputType outputType,
            byte[] expected, ByteBuffer actualBuf) throws Exception {
        byte[] actual;
        if (actualBuf.hasArray()) {
            actual = actualBuf.array();
        } else {
            actual = new byte[actualBuf.position()];
            actualBuf.position(0).get(actual);
        }
        if (!Arrays.equals(actual, expected)) {
            throw new Exception("After " + switch (checkType) {
                case CIPHERTEXT -> "encrypting";
                case PLAINTEXT -> "decrypting";
            } + " into a " + switch (outputType) {
                case BYTE_ARRAY -> "byte[]";
                case DIRECT_BYTE_BUFFER -> "direct ByteBuffer";
            } + ", " + checkType.name().toLowerCase() + "s don't match:" + LF +
                    "  Expected: " + repr(expected) + LF +
                    "    Actual: " + repr(actual));
        }
    }

    private static void doMultipart(int... chunkSizes) throws Exception {
        int totalLength = IntStream.of(chunkSizes).sum();
        byte[][] plaintextChunks = generateChunks(totalLength, chunkSizes);

        ByteBuffer jointPlaintextBuf = ByteBuffer.allocate(totalLength);
        for (byte[] plaintextChunk : plaintextChunks) {
            jointPlaintextBuf.put(plaintextChunk);
        }
        byte[] jointPlaintext = jointPlaintextBuf.array();
        byte[] expectedCiphertext = computeExpected(jointPlaintext);

        Cipher cipher = Cipher.getInstance(ALGORITHM, sunPKCS11);

        // Encryption test, with byte[]
        cipher.init(Cipher.ENCRYPT_MODE, KEY, IV);
        ByteBuffer actualCiphertextBuf = ByteBuffer.allocate(totalLength);
        for (byte[] plaintextChunk : plaintextChunks) {
            actualCiphertextBuf.put(cipher.update(plaintextChunk));
        }
        actualCiphertextBuf.put(cipher.doFinal());
        check(CheckType.CIPHERTEXT, OutputType.BYTE_ARRAY,
                expectedCiphertext, actualCiphertextBuf);

        // Encryption test, with direct output buffer
        cipher.init(Cipher.ENCRYPT_MODE, KEY, IV);
        ByteBuffer actualCiphertextDir = ByteBuffer.allocateDirect(totalLength);
        for (byte[] plaintextChunk : plaintextChunks) {
            cipher.update(ByteBuffer.wrap(plaintextChunk), actualCiphertextDir);
        }
        cipher.doFinal(ByteBuffer.allocate(0), actualCiphertextDir);
        check(CheckType.CIPHERTEXT, OutputType.DIRECT_BYTE_BUFFER,
                expectedCiphertext, actualCiphertextDir);

        // Decryption test, with byte[]
        cipher.init(Cipher.DECRYPT_MODE, KEY, IV);
        ByteBuffer actualPlaintextBuf = ByteBuffer.allocate(totalLength);
        actualCiphertextBuf.position(0);
        for (byte[] plaintextChunk : plaintextChunks) {
            // Use the same chunk sizes as the plaintext
            byte[] ciphertextChunk = new byte[plaintextChunk.length];
            actualCiphertextBuf.get(ciphertextChunk);
            actualPlaintextBuf.put(cipher.update(ciphertextChunk));
        }
        actualPlaintextBuf.put(cipher.doFinal());
        check(CheckType.PLAINTEXT, OutputType.BYTE_ARRAY,
                jointPlaintext, actualPlaintextBuf);

        // Decryption test, with direct output buffer
        cipher.init(Cipher.DECRYPT_MODE, KEY, IV);
        ByteBuffer actualPlaintextDir = ByteBuffer.allocateDirect(totalLength);
        actualCiphertextBuf.position(0);
        for (byte[] plaintextChunk : plaintextChunks) {
            // Use the same chunk sizes as the plaintext
            byte[] ciphertextChunk = new byte[plaintextChunk.length];
            actualCiphertextBuf.get(ciphertextChunk);
            cipher.update(ByteBuffer.wrap(ciphertextChunk), actualPlaintextDir);
        }
        cipher.doFinal(ByteBuffer.allocate(0), actualPlaintextDir);
        check(CheckType.PLAINTEXT, OutputType.DIRECT_BYTE_BUFFER,
                jointPlaintext, actualPlaintextDir);
    }

    private static String repr(byte[] data) {
        if (data == null) {
            return "<null>";
        }
        if (data.length == 0) {
            return "<empty []>";
        }
        String lenRepr = " (" + data.length + " bytes)";
        for (byte b : data) {
            if (b < 32 || b > 126) {
                return HexFormat.ofDelimiter(":").formatHex(data) + lenRepr;
            }
        }
        return new String(data, StandardCharsets.US_ASCII) + lenRepr;
    }

    private static void initialize() throws Exception {
        sunJCECipher = Cipher.getInstance(ALGORITHM, "SunJCE");
        sunJCECipher.init(Cipher.ENCRYPT_MODE, KEY, IV);
    }

    public static void main(String[] args) throws Exception {
        initialize();
        main(new TestCipherTextStealingMultipart(), args);
    }

    @Override
    public void main(Provider p) throws Exception {
        sunPKCS11 = p;
        try {
            // Test relevant combinations for 2, 3, and 4 update operations
            int aesBSize = 16;
            int[] points = new int[]{1, aesBSize - 1, aesBSize, aesBSize + 1};
            for (int size1 : points) {
                for (int size2 : points) {
                    if (size1 + size2 >= aesBSize) {
                        doMultipart(size1, size2);
                    }
                    for (int size3 : points) {
                        if (size1 + size2 + size3 >= aesBSize) {
                            doMultipart(size1, size2, size3);
                        }
                        for (int size4 : points) {
                            if (size1 + size2 + size3 + size4 >= aesBSize) {
                                doMultipart(size1, size2, size3, size4);
                            }
                        }
                    }
                }
            }
            doMultipart(17, 17, 17, 17, 17);
            doMultipart(4, 2, 7, 1, 6, 12);
            doMultipart(2, 15, 21, 26, 31, 26, 5, 30);
            doMultipart(7, 12, 26, 8, 15, 2, 17, 16, 21, 2, 32, 29);
            doMultipart(6, 7, 6, 1, 5, 16, 14, 1, 10, 16, 17, 8, 1, 13, 12);
            doMultipart(16, 125, 19, 32, 32, 16, 17,
                    31, 19, 13, 16, 16, 32, 16, 16);
            doMultipart(5, 30, 11, 9, 6, 14, 20, 6,
                    5, 18, 31, 33, 15, 29, 7, 9);
            doMultipart(105, 8, 21, 27, 30, 101, 15, 20,
                    23, 33, 26, 6, 8, 2, 13, 17);
        } catch (Exception e) {
            System.out.print(chunksDesc);
            throw e;
        }
        System.out.println("TEST PASS - OK");
    }
}
