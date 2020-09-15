/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
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
import java.util.Objects;
import java.util.Random;

import compiler.whitebox.CompilerWhiteBoxTest;
import sun.hotspot.code.Compiler;
import jtreg.SkippedException;

public class TestBase64 {
    static boolean checkOutput = Boolean.getBoolean("checkOutput");

    public static void main(String[] args) throws Exception {
        if (!Compiler.isIntrinsicAvailable(CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION, "java.util.Base64$Encoder", "encodeBlock", byte[].class, int.class, int.class, byte[].class, int.class, boolean.class)) {
            throw new SkippedException("Base64 intrinsic is not available");
        }
        int iters = (args.length > 0 ? Integer.valueOf(args[0]) : 5_000);
        System.out.println(iters + " iterations");

        warmup();

        test0(FileType.ASCII, Base64Type.BASIC, Base64.getEncoder(), Base64.getDecoder(),"plain.txt", "baseEncode.txt", iters);
        test0(FileType.ASCII, Base64Type.URLSAFE, Base64.getUrlEncoder(), Base64.getUrlDecoder(),"plain.txt", "urlEncode.txt", iters);
        test0(FileType.ASCII, Base64Type.MIME, Base64.getMimeEncoder(), Base64.getMimeDecoder(),"plain.txt", "mimeEncode.txt", iters);

        test0(FileType.HEXASCII, Base64Type.BASIC, Base64.getEncoder(), Base64.getDecoder(),"longLineHEX.txt", "longLineBaseEncode.txt", iters);
        test0(FileType.HEXASCII, Base64Type.URLSAFE, Base64.getUrlEncoder(), Base64.getUrlDecoder(),"longLineHEX.txt", "longLineUrlEncode.txt", iters);
        test0(FileType.HEXASCII, Base64Type.MIME, Base64.getMimeEncoder(), Base64.getMimeDecoder(),"longLineHEX.txt", "longLineMimeEncode.txt", iters);
    }

    private static void warmup() {
        final int warmupCount = 20_000;
        final int bufSize = 60;
        byte[] srcBuf = new byte[bufSize];
        byte[] encBuf = new byte[(bufSize / 3) * 4];
        byte[] decBuf = new byte[bufSize];

        ran.nextBytes(srcBuf);

        // This should be enough to get both the decoder and encoder intrinsic loaded up and running.
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
                    srcArr = hexStringToByteArray(srcStr);
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
                resArr = new byte[srcArr.length];
                len = decoder.decode(encodedArr, resArr);
                assertEqual(len, srcArr.length);
                assertEqual(resArr, srcArr);

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

    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        if (len % 2 != 0) {
                throw new InternalError("Internal test error: HEXASCII source file line doesn't contain an even number of characters: " + Integer.toString(len));
        }
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            byte upperNibble = (byte)Character.digit(s.charAt(i), 16);
            byte lowerNibble = (byte)Character.digit(s.charAt(i + 1), 16);
            if (upperNibble == -1) {
                throw new InternalError("Internal test error: HEXASCII source file contains non-hex character: " + s.charAt(i) + " at pos: " + Integer.toString(i));
            }
            if (lowerNibble == -1) {
                throw new InternalError("Internal test error: HEXASCII source file contains non-hex character: " + s.charAt(i) + " at pos: " + Integer.toString(i + 1));
            }
            data[i / 2] = (byte)((upperNibble << 4) + lowerNibble);
        }
        return data;
    }

    private static final byte[] nonBase64 = {
        (byte)0x00, (byte)0x01, (byte)0x02, (byte)0x03,
        (byte)0x04, (byte)0x05, (byte)0x06, (byte)0x07,
        (byte)0x08, (byte)0x09, (byte)0x0a, (byte)0x0b,
        (byte)0x0c, (byte)0x0d, (byte)0x0e, (byte)0x0f,
        (byte)0x10, (byte)0x11, (byte)0x12, (byte)0x13,
        (byte)0x14, (byte)0x15, (byte)0x16, (byte)0x17,
        (byte)0x18, (byte)0x19, (byte)0x1a, (byte)0x1b,
        (byte)0x1c, (byte)0x1d, (byte)0x1e, (byte)0x1f,
        (byte)0x20, (byte)0x21, (byte)0x22, (byte)0x23,
        (byte)0x24, (byte)0x25, (byte)0x26, (byte)0x27,
        (byte)0x28, (byte)0x29, (byte)0x2a, (byte)0x2c,
        (byte)0x2d, (byte)0x2e, (byte)0x3a, (byte)0x3b,
        (byte)0x3c, (byte)0x3e, (byte)0x3f, (byte)0x40,
        (byte)0x5b, (byte)0x5c, (byte)0x5d, (byte)0x5e,
        (byte)0x5f, (byte)0x60, (byte)0x7b, (byte)0x7c,
        (byte)0x7d, (byte)0x7e, (byte)0x7f, (byte)0x80,
        (byte)0x81, (byte)0x82, (byte)0x83, (byte)0x84,
        (byte)0x85, (byte)0x86, (byte)0x87, (byte)0x88,
        (byte)0x89, (byte)0x8a, (byte)0x8b, (byte)0x8c,
        (byte)0x8d, (byte)0x8e, (byte)0x8f, (byte)0x90,
        (byte)0x91, (byte)0x92, (byte)0x93, (byte)0x94,
        (byte)0x95, (byte)0x96, (byte)0x97, (byte)0x98,
        (byte)0x99, (byte)0x9a, (byte)0x9b, (byte)0x9c,
        (byte)0x9d, (byte)0x9e, (byte)0x9f, (byte)0xa0,
        (byte)0xa1, (byte)0xa2, (byte)0xa3, (byte)0xa4,
        (byte)0xa5, (byte)0xa6, (byte)0xa7, (byte)0xa8,
        (byte)0xa9, (byte)0xaa, (byte)0xab, (byte)0xac,
        (byte)0xad, (byte)0xae, (byte)0xaf, (byte)0xb0,
        (byte)0xb1, (byte)0xb2, (byte)0xb3, (byte)0xb4,
        (byte)0xb5, (byte)0xb6, (byte)0xb7, (byte)0xb8,
        (byte)0xb9, (byte)0xba, (byte)0xbb, (byte)0xbc,
        (byte)0xbd, (byte)0xbe, (byte)0xbf, (byte)0xc0,
        (byte)0xc1, (byte)0xc2, (byte)0xc3, (byte)0xc4,
        (byte)0xc5, (byte)0xc6, (byte)0xc7, (byte)0xc8,
        (byte)0xc9, (byte)0xca, (byte)0xcb, (byte)0xcc,
        (byte)0xcd, (byte)0xce, (byte)0xcf, (byte)0xd0,
        (byte)0xd1, (byte)0xd2, (byte)0xd3, (byte)0xd4,
        (byte)0xd5, (byte)0xd6, (byte)0xd7, (byte)0xd8,
        (byte)0xd9, (byte)0xda, (byte)0xdb, (byte)0xdc,
        (byte)0xdd, (byte)0xde, (byte)0xdf, (byte)0xe0,
        (byte)0xe1, (byte)0xe2, (byte)0xe3, (byte)0xe4,
        (byte)0xe5, (byte)0xe6, (byte)0xe7, (byte)0xe8,
        (byte)0xe9, (byte)0xea, (byte)0xeb, (byte)0xec,
        (byte)0xed, (byte)0xee, (byte)0xef, (byte)0xf0,
        (byte)0xf1, (byte)0xf2, (byte)0xf3, (byte)0xf4,
        (byte)0xf5, (byte)0xf6, (byte)0xf7, (byte)0xf8,
        (byte)0xf9, (byte)0xfa, (byte)0xfb, (byte)0xfc,
        (byte)0xfd, (byte)0xfe, (byte)0xff
    };

    private static final byte[] nonBase64URL = {
        (byte)0x00, (byte)0x01, (byte)0x02, (byte)0x03,
        (byte)0x04, (byte)0x05, (byte)0x06, (byte)0x07,
        (byte)0x08, (byte)0x09, (byte)0x0a, (byte)0x0b,
        (byte)0x0c, (byte)0x0d, (byte)0x0e, (byte)0x0f,
        (byte)0x10, (byte)0x11, (byte)0x12, (byte)0x13,
        (byte)0x14, (byte)0x15, (byte)0x16, (byte)0x17,
        (byte)0x18, (byte)0x19, (byte)0x1a, (byte)0x1b,
        (byte)0x1c, (byte)0x1d, (byte)0x1e, (byte)0x1f,
        (byte)0x20, (byte)0x21, (byte)0x22, (byte)0x23,
        (byte)0x24, (byte)0x25, (byte)0x26, (byte)0x27,
        (byte)0x28, (byte)0x29, (byte)0x2a, (byte)0x2b,
        (byte)0x2c, (byte)0x2e, (byte)0x2f, (byte)0x3a,
        (byte)0x3b, (byte)0x3c, (byte)0x3e, (byte)0x3f,
        (byte)0x40, (byte)0x5b, (byte)0x5c, (byte)0x5d,
        (byte)0x5e, (byte)0x60, (byte)0x7b, (byte)0x7c,
        (byte)0x7d, (byte)0x7e, (byte)0x7f, (byte)0x80,
        (byte)0x81, (byte)0x82, (byte)0x83, (byte)0x84,
        (byte)0x85, (byte)0x86, (byte)0x87, (byte)0x88,
        (byte)0x89, (byte)0x8a, (byte)0x8b, (byte)0x8c,
        (byte)0x8d, (byte)0x8e, (byte)0x8f, (byte)0x90,
        (byte)0x91, (byte)0x92, (byte)0x93, (byte)0x94,
        (byte)0x95, (byte)0x96, (byte)0x97, (byte)0x98,
        (byte)0x99, (byte)0x9a, (byte)0x9b, (byte)0x9c,
        (byte)0x9d, (byte)0x9e, (byte)0x9f, (byte)0xa0,
        (byte)0xa1, (byte)0xa2, (byte)0xa3, (byte)0xa4,
        (byte)0xa5, (byte)0xa6, (byte)0xa7, (byte)0xa8,
        (byte)0xa9, (byte)0xaa, (byte)0xab, (byte)0xac,
        (byte)0xad, (byte)0xae, (byte)0xaf, (byte)0xb0,
        (byte)0xb1, (byte)0xb2, (byte)0xb3, (byte)0xb4,
        (byte)0xb5, (byte)0xb6, (byte)0xb7, (byte)0xb8,
        (byte)0xb9, (byte)0xba, (byte)0xbb, (byte)0xbc,
        (byte)0xbd, (byte)0xbe, (byte)0xbf, (byte)0xc0,
        (byte)0xc1, (byte)0xc2, (byte)0xc3, (byte)0xc4,
        (byte)0xc5, (byte)0xc6, (byte)0xc7, (byte)0xc8,
        (byte)0xc9, (byte)0xca, (byte)0xcb, (byte)0xcc,
        (byte)0xcd, (byte)0xce, (byte)0xcf, (byte)0xd0,
        (byte)0xd1, (byte)0xd2, (byte)0xd3, (byte)0xd4,
        (byte)0xd5, (byte)0xd6, (byte)0xd7, (byte)0xd8,
        (byte)0xd9, (byte)0xda, (byte)0xdb, (byte)0xdc,
        (byte)0xdd, (byte)0xde, (byte)0xdf, (byte)0xe0,
        (byte)0xe1, (byte)0xe2, (byte)0xe3, (byte)0xe4,
        (byte)0xe5, (byte)0xe6, (byte)0xe7, (byte)0xe8,
        (byte)0xe9, (byte)0xea, (byte)0xeb, (byte)0xec,
        (byte)0xed, (byte)0xee, (byte)0xef, (byte)0xf0,
        (byte)0xf1, (byte)0xf2, (byte)0xf3, (byte)0xf4,
        (byte)0xf5, (byte)0xf6, (byte)0xf7, (byte)0xf8,
        (byte)0xf9, (byte)0xfa, (byte)0xfb, (byte)0xfc,
        (byte)0xfd, (byte)0xfe, (byte)0xff
    };

    private static byte getBadBase64Char(Base64Type b64Type) {
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
}
