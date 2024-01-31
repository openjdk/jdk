/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @author Eric Wang <yiming.wang@oracle.com>
 * @summary tests java.util.Base64
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm/timeout=600 -Xbatch -DcheckOutput=true
 *       -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *      compiler.intrinsics.base64.TestBase64
 */

package compiler.intrinsics.base64;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Random;
import java.util.Arrays;

import static java.lang.String.format;

import compiler.whitebox.CompilerWhiteBoxTest;
import jdk.test.whitebox.code.Compiler;
import jtreg.SkippedException;
import jdk.test.lib.Utils;

public class TestBase64 {
    static boolean checkOutput = Boolean.getBoolean("checkOutput");

    public static void main(String[] args) throws Exception {
        if (!Compiler.isIntrinsicAvailable(CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION, "java.util.Base64$Encoder", "encodeBlock", byte[].class, int.class, int.class, byte[].class, int.class, boolean.class)) {
            throw new SkippedException("Base64 intrinsic is not available");
        }
        int iters = (args.length > 0 ? Integer.valueOf(args[0]) : 5_000);
        System.out.println(iters + " iterations");

        initNonBase64Arrays();

        warmup();

        length_checks();

        test0(FileType.ASCII, Base64Type.BASIC, Base64.getEncoder(), Base64.getDecoder(),"plain.txt", "baseEncode.txt", iters);
        test0(FileType.ASCII, Base64Type.URLSAFE, Base64.getUrlEncoder(), Base64.getUrlDecoder(),"plain.txt", "urlEncode.txt", iters);
        test0(FileType.ASCII, Base64Type.MIME, Base64.getMimeEncoder(), Base64.getMimeDecoder(),"plain.txt", "mimeEncode.txt", iters);

        test0(FileType.HEXASCII, Base64Type.BASIC, Base64.getEncoder(), Base64.getDecoder(),"longLineHEX.txt", "longLineBaseEncode.txt", iters);
        test0(FileType.HEXASCII, Base64Type.URLSAFE, Base64.getUrlEncoder(), Base64.getUrlDecoder(),"longLineHEX.txt", "longLineUrlEncode.txt", iters);
        test0(FileType.HEXASCII, Base64Type.MIME, Base64.getMimeEncoder(), Base64.getMimeDecoder(),"longLineHEX.txt", "longLineMimeEncode.txt", iters);
    }

    private static void warmup() {
        final int warmupCount = 20_000;
        final int bufSize = 15308;
        byte[] srcBuf = new byte[bufSize];
        byte[] encBuf = new byte[((bufSize + 2) / 3) * 4];
        byte[] decBuf = new byte[bufSize];

        ran.nextBytes(srcBuf);

        // This should be enough to get both encode and decode compiled on
        // the highest tier.
        for (int i = 0; i < warmupCount; i++) {
            Base64.getEncoder().encode(srcBuf, encBuf);
            Base64.getDecoder().decode(encBuf, decBuf);
        }
    }

    public static void test0(FileType inputFileType, Base64Type type, Encoder encoder, Decoder decoder, String srcFile, String encodedFile, int numIterations) throws Exception {

        String[] srcLns = Files.readAllLines(Paths.get(SRCDIR, srcFile), DEF_CHARSET)
                               .toArray(new String[0]);
        String[] encodedLns = Files.readAllLines(Paths.get(SRCDIR, encodedFile), DEF_CHARSET)
                                   .toArray(new String[0]);

        for (int i = 0; i < numIterations; i++) {
            int lns = 0;
            for (String srcStr : srcLns) {
                String encodedStr = null;
                if (type != Base64Type.MIME) {
                    encodedStr = encodedLns[lns++];
                } else {
                    while (lns < encodedLns.length) {
                        String s = encodedLns[lns++];
                        if (s.length() == 0)
                            break;
                        if (encodedStr != null) {
                            encodedStr += DEFAULT_CRLF + s;
                        } else {
                            encodedStr = s;
                        }
                    }
                    if (encodedStr == null && srcStr.length() == 0) {
                        encodedStr = "";
                    }
                }

                byte[] srcArr;
                switch (inputFileType) {
                case ASCII:
                    srcArr = srcStr.getBytes(DEF_CHARSET);
                    break;
                case HEXASCII:
                    srcArr = Utils.toByteArray(srcStr);
                    break;
                default:
                    throw new IllegalStateException();
                }

                byte[] encodedArr = encodedStr.getBytes(DEF_CHARSET);

                ByteBuffer srcBuf = ByteBuffer.wrap(srcArr);
                ByteBuffer encodedBuf = ByteBuffer.wrap(encodedArr);
                byte[] resArr = new byte[encodedArr.length];

                // test int encode(byte[], byte[])
                int len = encoder.encode(srcArr, resArr);
                assertEqual(len, encodedArr.length);
                assertEqual(resArr, encodedArr);

                // test byte[] encode(byte[])
                resArr = encoder.encode(srcArr);
                assertEqual(resArr, encodedArr);

                // test ByteBuffer encode(ByteBuffer)
                int limit = srcBuf.limit();
                ByteBuffer resBuf = encoder.encode(srcBuf);
                assertEqual(srcBuf.position(), limit);
                assertEqual(srcBuf.limit(), limit);
                assertEqual(resBuf, encodedBuf);
                srcBuf.rewind(); // reset for next test

                // test String encodeToString(byte[])
                String resEncodeStr = encoder.encodeToString(srcArr);
                assertEqual(resEncodeStr, encodedStr);

                // test int decode(byte[], byte[])
                // JDK-8273108: Test for output buffer overrun
                resArr = new byte[srcArr.length + 2];
                resArr[srcArr.length + 1] = (byte) 167;
                len = decoder.decode(encodedArr, resArr);
                assertEqual(len, srcArr.length);
                assertEqual(Arrays.copyOfRange(resArr, 0, srcArr.length), srcArr);
                assertEqual(resArr[srcArr.length + 1], (byte) 167);

                // test byte[] decode(byte[])
                resArr = decoder.decode(encodedArr);
                assertEqual(resArr, srcArr);

                // test that an illegal Base64 character is detected
                if ((type != Base64Type.MIME) && (encodedArr.length > 0)) {
                    int bytePosToCorrupt = ran.nextInt(encodedArr.length);
                    byte orig = encodedArr[bytePosToCorrupt];
                    encodedArr[bytePosToCorrupt] = getBadBase64Char(type);
                    boolean caught = false;
                    try {
                        // resArr is already allocated
                        len = decoder.decode(encodedArr, resArr);
                    } catch (IllegalArgumentException e) {
                        caught = true;
                    }
                    if (!caught) {
                        throw new RuntimeException(String.format("Decoder did not catch an illegal base64 character: 0x%02x at position: %d in encoded buffer of length %d",
                             encodedArr[bytePosToCorrupt], bytePosToCorrupt, encodedArr.length));
                    }
                    encodedArr[bytePosToCorrupt] = orig;
                }

                // test ByteBuffer decode(ByteBuffer)
                limit = encodedBuf.limit();
                resBuf = decoder.decode(encodedBuf);
                assertEqual(encodedBuf.position(), limit);
                assertEqual(encodedBuf.limit(), limit);
                assertEqual(resBuf, srcBuf);
                encodedBuf.rewind(); // reset for next test

                // test byte[] decode(String)
                resArr = decoder.decode(encodedStr);
                assertEqual(resArr, srcArr);

            }
        }
    }

    // Data type in the input file
    enum FileType {
        ASCII, HEXASCII
    }

    // helper
    enum Base64Type {
        BASIC, URLSAFE, MIME
    }

    private static final String SRCDIR = System.getProperty("test.src", "compiler/intrinsics/base64/");
    private static final Charset DEF_CHARSET = StandardCharsets.US_ASCII;
    private static final String DEF_EXCEPTION_MSG =
        "Assertion failed! The result is not same as expected\n";
    private static final String DEFAULT_CRLF = "\r\n";
    private static final Random ran = new Random(1000); // Constant seed for repeatability

    private static void assertEqual(Object result, Object expect) {
        if (checkOutput) {
            if (!Objects.deepEquals(result, expect)) {
                String resultStr = result.toString();
                String expectStr = expect.toString();
                if (result instanceof byte[]) {
                    resultStr = new String((byte[]) result, DEF_CHARSET);
                }
                if (expect instanceof byte[]) {
                    expectStr = new String((byte[]) expect, DEF_CHARSET);
                }
                throw new RuntimeException(DEF_EXCEPTION_MSG +
                    " result: " + resultStr + " expected: " + expectStr);
            }
        }
    }

    // This array will contain all possible 8-bit values *except* those
    // that are legal Base64 characters: A-Z a-z 0-9 + / =
    private static final byte[] nonBase64 = new byte[256 - 65];

    // This array will contain all possible 8-bit values *except* those
    // that are legal URL-safe Base64 characters: A-Z a-z 0-9 - _ =
    private static final byte[] nonBase64URL = new byte[256 - 65];

    private static final byte[] legalBase64 = new byte[] {
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
        'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
        'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
        'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
        '+', '/', '=' };

    private static final byte[] legalBase64URL = new byte[] {
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
        'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
        'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
        'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
        '-', '_', '=' };

    private static final boolean contains(byte[] ary, byte b) {
        for (int i = 0; i < ary.length; i++) {
            if (ary[i] == b) {
                return true;
            }
        }
        return false;
    }

    private static final void initNonBase64Arrays() {
        int i0 = 0, i1 = 0;
        for (int val = 0; val < 256; val++) {
            if (! contains(legalBase64, (byte)val)) {
                nonBase64[i0++] = (byte)val;
            }
            if (! contains(legalBase64URL, (byte)val)) {
                nonBase64URL[i1++] = (byte)val;
            }
        }
    }

    private static final byte getBadBase64Char(Base64Type b64Type) {
        int ch = ran.nextInt(256 - 65); // 64 base64 characters, and one for the '=' padding character
        switch (b64Type) {
        case MIME:
        case BASIC:
            return nonBase64[ch];
        case URLSAFE:
            return nonBase64URL[ch];
        default:
            throw new InternalError("Internal test error: getBadBase64Char called with unknown Base64Type value");
        }
    }

    static final int POSITIONS = 30_000;
    static final int BASE_LENGTH = 256;
    static final HexFormat HEX_FORMAT = HexFormat.of().withUpperCase().withDelimiter(" ");

    static int[] plainOffsets = new int[POSITIONS + 1];
    static byte[] plainBytes;
    static int[] base64Offsets = new int[POSITIONS + 1];
    static byte[] base64Bytes;

    static {
        // Set up ByteBuffer with characters to be encoded
        int plainLength = 0;
        for (int i = 0; i < plainOffsets.length; i++) {
            plainOffsets[i] = plainLength;
            int positionLength = (BASE_LENGTH + i) % 2048;
            plainLength += positionLength;
        }
        // Put one of each possible byte value into ByteBuffer
        plainBytes = new byte[plainLength];
        for (int i = 0; i < plainBytes.length; i++) {
            plainBytes[i] = (byte) i;
        }

        // Grab various slices of the ByteBuffer and encode them
        ByteBuffer plainBuffer = ByteBuffer.wrap(plainBytes);
        int base64Length = 0;
        for (int i = 0; i < POSITIONS; i++) {
            base64Offsets[i] = base64Length;
            int offset = plainOffsets[i];
            int length = plainOffsets[i + 1] - offset;
            ByteBuffer plainSlice = plainBuffer.slice(offset, length);
            base64Length += Base64.getEncoder().encode(plainSlice).remaining();
        }

        // Decode the slices created above and ensure lengths match
        base64Offsets[base64Offsets.length - 1] = base64Length;
        base64Bytes = new byte[base64Length];
        for (int i = 0; i < POSITIONS; i++) {
            int plainOffset = plainOffsets[i];
            ByteBuffer plainSlice = plainBuffer.slice(plainOffset, plainOffsets[i + 1] - plainOffset);
            ByteBuffer encodedBytes = Base64.getEncoder().encode(plainSlice);
            int base64Offset = base64Offsets[i];
            int expectedLength = base64Offsets[i + 1] - base64Offset;
            if (expectedLength != encodedBytes.remaining()) {
                throw new IllegalStateException(format("Unexpected length: %s <> %s", encodedBytes.remaining(), expectedLength));
            }
            encodedBytes.get(base64Bytes, base64Offset, expectedLength);
        }
    }

    public static void length_checks() {
        decodeAndCheck();
        encodeDecode();
        System.out.println("Test complete, no invalid decodes detected");
    }

    // Use ByteBuffer to cause decode() to use the base + offset form of decode
    // Checks for bug reported in JDK-8321599 where padding characters appear
    // within the beginning of the ByteBuffer *before* the offset.  This caused
    // the decoded string length to be off by 1 or 2 bytes.
    static void decodeAndCheck() {
        for (int i = 0; i < POSITIONS; i++) {
            ByteBuffer encodedBytes = base64BytesAtPosition(i);
            ByteBuffer decodedBytes = Base64.getDecoder().decode(encodedBytes);

            if (!decodedBytes.equals(plainBytesAtPosition(i))) {
                String base64String = base64StringAtPosition(i);
                String plainHexString = plainHexStringAtPosition(i);
                String decodedHexString = HEX_FORMAT.formatHex(decodedBytes.array(), decodedBytes.arrayOffset() + decodedBytes.position(), decodedBytes.arrayOffset() + decodedBytes.limit());
                throw new IllegalStateException(format("Mismatch for %s\n\nExpected:\n%s\n\nActual:\n%s", base64String, plainHexString, decodedHexString));
            }
        }
    }

    // Encode strings of lengths 1-1K, decode, and ensure length and contents correct.
    // This checks that padding characters are properly handled by decode.
    static void encodeDecode() {
        String allAs = "A(=)".repeat(128);
        for (int i = 1; i <= 512; i++) {
            String encStr = Base64.getEncoder().encodeToString(allAs.substring(0, i).getBytes());
            String decStr = new String(Base64.getDecoder().decode(encStr));

            if ((decStr.length() != allAs.substring(0, i).length()) ||
                (!Objects.equals(decStr, allAs.substring(0, i)))
               ) {
                throw new IllegalStateException(format("Mismatch: Expected: %s\n          Actual: %s\n", allAs.substring(0, i), decStr));
            }
        }
    }

    static ByteBuffer plainBytesAtPosition(int position) {
        int offset = plainOffsets[position];
        int length = plainOffsets[position + 1] - offset;
        return ByteBuffer.wrap(plainBytes, offset, length);
    }

    static String plainHexStringAtPosition(int position) {
        int offset = plainOffsets[position];
        int length = plainOffsets[position + 1] - offset;
        return HEX_FORMAT.formatHex(plainBytes, offset, offset + length);
    }

    static String base64StringAtPosition(int position) {
        int offset = base64Offsets[position];
        int length = base64Offsets[position + 1] - offset;
        return new String(base64Bytes, offset, length, StandardCharsets.UTF_8);
    }

    static ByteBuffer base64BytesAtPosition(int position) {
        int offset = base64Offsets[position];
        int length = base64Offsets[position + 1] - offset;
        return ByteBuffer.wrap(base64Bytes, offset, length);
    }
}
