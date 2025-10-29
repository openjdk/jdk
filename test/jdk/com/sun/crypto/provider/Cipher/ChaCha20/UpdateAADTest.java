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

/**
 * @test
 * @bug 8366833
 * @summary Poly1305 does not always correctly update position for array-backed
 *          ByteBuffers after processMultipleBlocks
 * @run main UpdateAADTest
 */

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;

public class UpdateAADTest {
    private static final SecureRandom RAND;
    private static final KeyGenerator CC20GEN;
    private static final HexFormat HEX = HexFormat.of();

    private static final byte[] TEST_KEY_BYTES = HEX.parseHex(
            "3cb1283912536e4108c3094dc2940d0d020afbd7701de267bbfb359bc7d54dd7");
    private static final byte[] TEST_NONCE_BYTES = HEX.parseHex(
            "9bd647a43b6fa7826e2cc26d");
    private static final byte[] TEST_AAD_BYTES =
            "This is a bunch of additional data to throw into the mix.".
                    getBytes(StandardCharsets.UTF_8);
    private static final byte[] TEST_INPUT_BYTES =
        "This is a plaintext message".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TEST_CT_BYTES = HEX.parseHex(
            "8536c999809f4b9d6a1594ee1084c751d1bd8a991e6a4b4ac26386f04b9a1303" +
            "f40cbe6788d72af2d0c617");
    private static final ByteBuffer EXPOUTBUF = ByteBuffer.wrap(TEST_CT_BYTES);

    static {
        try {
            RAND = new SecureRandom();
            CC20GEN = KeyGenerator.getInstance("ChaCha20");
        } catch (GeneralSecurityException gse) {
            throw new RuntimeException("Failed to init static JCE components",
                    gse);
        }
    }

    public interface TestAction {
        void runTest(ByteBuffer buffer) throws Exception;
    }

    public static void main(final String[] args) throws Exception {
        ByteBuffer twoKBuf = ByteBuffer.allocate(2048);
        ByteBuffer nonBABuf = ByteBuffer.allocate(1329);

        System.out.println("----- Test 1: Baseline test -----");
        System.out.println("Make an array backed buffer that is 16-byte " +
                           "aligned, treat all data as AAD and feed it to " +
                           " updateAAD.");
        aadUpdateTest.runTest(twoKBuf);

        System.out.println("----- Test 2: Non Block Aligned Offset -----");
        System.out.println("Use the same buffer, but place the offset such " +
                           "that the remaining data is not block aligned.");
        aadUpdateTest.runTest(twoKBuf.position(395));

        System.out.println("----- Test 3: Non Block Aligned Buf/Off -----");
        System.out.println("Make a buffer of non-block aligned size with an " +
                           "offset that keeps the remaining data non-block " +
                           "aligned.");
        aadUpdateTest.runTest(nonBABuf.position(602));

        System.out.println("----- Test 4: Aligned Buffer Slice -----");
        System.out.println("Use a buffer of block aligned size, but slice " +
                           "the buffer such that the slice offset is part " +
                           "way into the original buffer.");
        aadUpdateTest.runTest(twoKBuf.rewind().slice(1024,1024).position(42));

        System.out.println("----- Test 5: Non-Aligned Buffer Slice -----");
        System.out.println("Try the same test as #4, this time with " +
                           "non-block aligned buffers/slices.");
        aadUpdateTest.runTest(nonBABuf.rewind().slice(347, 347).position(86));

        System.out.println("----- Test 6: MemorySegment Buffer -----");
        System.out.println("Make a ByteBuffer from an array-backed " +
                           "MemorySegment, and try updating");
        MemorySegment mseg = MemorySegment.ofArray(new byte[2048]);
        ByteBuffer msegBuf = mseg.asByteBuffer();
        aadUpdateTest.runTest(msegBuf.position(55));

        System.out.println("----- Test 7: Buffer of MemorySegment Slice -----");
        System.out.println("Use a slice from the MemorySegment and create a " +
                           "buffer from that for testing");
        MemorySegment msegSlice = mseg.asSlice(1024);
        aadUpdateTest.runTest(msegSlice.asByteBuffer().position(55));

        System.out.println("----- Test 8: MemorySegment Buffer Slice -----");
        System.out.println("Create a slice from the ByteBuffer from the " +
                           "original MemorySegment.");
        aadUpdateTest.runTest(msegBuf.rewind().slice(1024, 1024));

        System.out.println("Test vector processing");
        System.out.println("----------------------");
        System.out.println("----- Test 9: AAD + Plaintext on buffer ------");
        System.out.println("Place the AAD, followed by plaintext and verify " +
                           "the ciphertext");
        // Create a ByteBuffer where the AAD and plaintext actually sit
        // somewhere in the middle of the underlying array, with non-test-vector
        // memory on either side of the data.
        ByteBuffer vectorBuf = ByteBuffer.allocate(1024).position(600).
                put(TEST_AAD_BYTES).put(TEST_INPUT_BYTES).flip().position(600);
        vectorTest.runTest(vectorBuf);

        System.out.println("----- Test 10: AAD + Plaintext on slice -----");
        System.out.println("Perform the same test, this time on a slice" +
                           "of the test vector buffer");
        ByteBuffer vectorSlice = vectorBuf.slice(600,
                TEST_AAD_BYTES.length + TEST_INPUT_BYTES.length);
        vectorTest.runTest(vectorSlice);
    }

    // Simple test callback for taking a ByteBuffer and throwing all
    // remaining bytes into an updateAAD call.
    public static TestAction aadUpdateTest = buffer -> {
        SecretKey key = CC20GEN.generateKey();
        byte[] nonce = new byte[12];
        RAND.nextBytes(nonce);

        Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(nonce));

        cipher.updateAAD(buffer);
        // Per the API the buffer's position and limit should be equal
        if (buffer.position() != buffer.limit()) {
            throw new RuntimeException("Buffer position and limit " +
                    "should be equal but are not: p = " +
                    buffer.position() + ", l = " + buffer.limit());
        }
    };

    // Test callback for making sure that the updateAAD method, when
    // put in with a complete encryption operation still gets the
    // expected answer.
    public static TestAction vectorTest = buffer -> {
        Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(TEST_KEY_BYTES, "ChaCha20"),
                new IvParameterSpec(TEST_NONCE_BYTES));
        ByteBuffer outbuf = ByteBuffer.allocate(cipher.getOutputSize(
                TEST_INPUT_BYTES.length));

        // Adjust the limit to be the end of the aad
        int origLim = buffer.limit();
        buffer.limit(buffer.position() + TEST_AAD_BYTES.length);
        cipher.updateAAD(buffer);
        buffer.limit(origLim);
        cipher.doFinal(buffer, outbuf);
        if (!outbuf.flip().equals(EXPOUTBUF)) {
            throw new RuntimeException("Output data mismatch");
        }
    };
}
