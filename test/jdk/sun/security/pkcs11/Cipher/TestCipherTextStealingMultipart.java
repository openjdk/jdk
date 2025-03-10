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

    private static byte[] join(byte[][] inputChunks, int totalLength) {
        ByteBuffer outputBuf = ByteBuffer.allocate(totalLength);
        for (byte[] inputChunk : inputChunks) {
            outputBuf.put(inputChunk);
        }
        return outputBuf.array();
    }

    private static byte[][] split(byte[] input, int[] chunkSizes) {
        ByteBuffer inputBuf = ByteBuffer.wrap(input);
        byte[][] outputChunks = new byte[chunkSizes.length][];
        for (int chunkIdx = 0; chunkIdx < chunkSizes.length; chunkIdx++) {
            byte[] chunk = new byte[chunkSizes[chunkIdx]];
            inputBuf.get(chunk);
            outputChunks[chunkIdx] = chunk;
        }
        return outputChunks;
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

    private static ByteBuffer encryptOrDecryptMultipart(int operation,
            OutputType outputType, byte[][] inputChunks, int totalLength)
            throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM, sunPKCS11);
        cipher.init(operation, KEY, IV);
        ByteBuffer output = null;
        int outOfs = 1;
        switch (outputType) {
            case BYTE_ARRAY -> {
                output = ByteBuffer.allocate(totalLength);
                for (byte[] inputChunk : inputChunks) {
                    output.put(cipher.update(inputChunk));
                }
                // Check that the output array offset does not affect the
                // penultimate block length calculation.
                byte[] tmpOut = new byte[cipher.getOutputSize(0) + outOfs];
                cipher.doFinal(tmpOut, outOfs);
                output.put(tmpOut, outOfs, tmpOut.length - outOfs);
            }
            case DIRECT_BYTE_BUFFER -> {
                output = ByteBuffer.allocateDirect(totalLength);
                for (byte[] inputChunk : inputChunks) {
                    cipher.update(ByteBuffer.wrap(inputChunk), output);
                }
                // Check that the output array offset does not affect the
                // penultimate block length calculation.
                ByteBuffer tmpOut = ByteBuffer.allocateDirect(
                        cipher.getOutputSize(0) + outOfs);
                tmpOut.position(outOfs);
                cipher.doFinal(ByteBuffer.allocate(0), tmpOut);
                tmpOut.position(outOfs);
                output.put(tmpOut);
            }
        }
        return output;
    }

    private static void doMultipart(int... chunkSizes) throws Exception {
        int totalLength = IntStream.of(chunkSizes).sum();
        byte[][] plaintextChunks = generateChunks(totalLength, chunkSizes);
        byte[] jointPlaintext = join(plaintextChunks, totalLength);
        byte[] expectedCiphertext = computeExpected(jointPlaintext);
        byte[][] ciphertextChunks = split(expectedCiphertext, chunkSizes);

        for (OutputType outputType : OutputType.values()) {
            // Encryption test
            check(CheckType.CIPHERTEXT, outputType, expectedCiphertext,
                    encryptOrDecryptMultipart(Cipher.ENCRYPT_MODE, outputType,
                            plaintextChunks, totalLength));
            // Decryption test
            check(CheckType.PLAINTEXT, outputType, jointPlaintext,
                    encryptOrDecryptMultipart(Cipher.DECRYPT_MODE, outputType,
                            ciphertextChunks, totalLength));
        }
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
