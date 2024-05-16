/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4235519 8331342
 * @summary Tests java.util.Base64.Encoder/Decoder encode/decode methods.
 * @run junit TestBase64Golden
 */

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestBase64Golden {

    private static final String SRCDIR = System.getProperty("test.src", ".");
    private static final Charset DEF_CHARSET = StandardCharsets.US_ASCII;
    private static final String DEFAULT_CRLF = "\r\n";
    private static final List<Arguments> encoderTestData = new ArrayList<>();
    private static final List<Arguments> decoderTestData = new ArrayList<>();

    enum Base64Type {
        BASIC("baseEncode.txt", Base64.getEncoder(), Base64.getDecoder()),
        URLSAFE("urlEncode.txt", Base64.getUrlEncoder(), Base64.getUrlDecoder()),
        MIME("mimeEncode.txt", Base64.getMimeEncoder(), Base64.getMimeDecoder());

        Base64Type(String s, Encoder e, Decoder d) {
            fileName = s;
            encoder = e;
            decoder = d;
        }
        private final String fileName;
        private final Base64.Encoder encoder;
        private final Base64.Decoder decoder;
    }

    // Build the test and expected data from the txt files based off the Base64Type
    @BeforeAll
    public static void generateTestData() throws IOException {
        for (Base64Type type : Base64Type.values()) {
            String[] srcLns = Files.readAllLines(Paths.get(SRCDIR, "plain.txt"), DEF_CHARSET)
                    .toArray(new String[0]);
            String[] encodedLns = Files.readAllLines(Paths.get(SRCDIR, type.fileName),
                    DEF_CHARSET).toArray(new String[0]);
            int lns = 0;
            for (String srcStr : srcLns) {
                StringBuilder encodedStr = null;
                if (type != Base64Type.MIME) {
                    encodedStr = new StringBuilder(encodedLns[lns++]);
                } else {
                    while (lns < encodedLns.length) {
                        String s = encodedLns[lns++];
                        if (s.length() == 0)
                            break;
                        if (encodedStr != null) {
                            encodedStr.append(DEFAULT_CRLF).append(s);
                        } else {
                            encodedStr = new StringBuilder(s);
                        }
                    }
                    if (encodedStr == null && srcStr.length() == 0) {
                        encodedStr = new StringBuilder();
                    }
                }
                byte[] srcArr = srcStr.getBytes(DEF_CHARSET);
                byte[] encodedArr = encodedStr.toString().getBytes(DEF_CHARSET);
                // Add the encoder/decoder type with pre/post encoded data
                encoderTestData.add(Arguments.of(type.encoder, encodedArr, srcArr));
                decoderTestData.add(Arguments.of(type.decoder, encodedArr, srcArr));
            }
        }
    }

    // Data providers for encode and decode
    private static List<Arguments> encoderData() {
        return encoderTestData;
    }

    private static List<Arguments> decoderData() {
        return decoderTestData;
    }

    // Test all the encode* methods for Base64.Encoder
    // test int encode(byte[], byte[])
    @ParameterizedTest
    @MethodSource("encoderData")
    public void encodeNewByteArrayTest(Base64.Encoder encoder,
                                       byte[] encodedArr, byte[] srcArr) {
        byte[] resArr = new byte[encodedArr.length];
        int len = encoder.encode(srcArr, resArr);
        assertEquals(len, encodedArr.length);
        assertArrayEquals(resArr, encodedArr);
    }

    // test byte[] encode(byte[])
    @ParameterizedTest
    @MethodSource("encoderData")
    public void encodeToByteArrayTest(Base64.Encoder encoder,
                                      byte[] encodedArr, byte[] srcArr) {
        assertArrayEquals(encoder.encode(srcArr), encodedArr);
    }

    // test ByteBuffer encode(ByteBuffer)
    @ParameterizedTest
    @MethodSource("encoderData")
    public void encoderByteBufferTest(Base64.Encoder encoder,
                                      byte[] encodedArr, byte[] srcArr) {
        ByteBuffer srcBuf = ByteBuffer.wrap(srcArr);
        ByteBuffer encodedBuf = ByteBuffer.wrap(encodedArr);
        int limit = srcBuf.limit();
        ByteBuffer resBuf = encoder.encode(srcBuf);
        assertEquals(srcBuf.position(), limit);
        assertEquals(srcBuf.limit(), limit);
        assertEquals(resBuf, encodedBuf);
    }

    // test String encodeToString(byte[])
    @ParameterizedTest
    @MethodSource("encoderData")
    public void encoderToStringTest(Base64.Encoder encoder,
                                    byte[] encodedArr, byte[] srcArr) {
        String resEncodeStr = encoder.encodeToString(srcArr);
        assertEquals(resEncodeStr, new String(encodedArr, DEF_CHARSET));
    }

    // Test all the decode* methods for Base64.Decoder
    // test byte[] decode(byte[])
    @ParameterizedTest
    @MethodSource("decoderData")
    public void decodeNewByteArrayTest(Base64.Decoder decoder,
                                       byte[] encodedArr, byte[] srcArr) {
        byte[] resArr = decoder.decode(encodedArr);
        assertArrayEquals(resArr, srcArr);
    }

    // test int decode(byte[], byte[])
    @ParameterizedTest
    @MethodSource("decoderData")
    public void decodeToByteArrayTest(Base64.Decoder decoder,
                                      byte[] encodedArr, byte[] srcArr) {
        byte[] resArr = new byte[srcArr.length];
        int len = decoder.decode(encodedArr, resArr);
        assertEquals(len, srcArr.length);
        assertArrayEquals(resArr, srcArr);
    }

    // test ByteBuffer decode(ByteBuffer)
    @ParameterizedTest
    @MethodSource("decoderData")
    public void decodeByteBufferTest(Base64.Decoder decoder,
                                     byte[] encodedArr, byte[] srcArr) {
        ByteBuffer srcBuf = ByteBuffer.wrap(srcArr);
        ByteBuffer encodedBuf = ByteBuffer.wrap(encodedArr);
        int limit = encodedBuf.limit();
        ByteBuffer resBuf = decoder.decode(encodedBuf);
        assertEquals(encodedBuf.position(), limit);
        assertEquals(encodedBuf.limit(), limit);
        assertEquals(resBuf, srcBuf);
    }

    // test byte[] decode(String)
    @ParameterizedTest
    @MethodSource("decoderData")
    public void decodeEncodedStringTest(Base64.Decoder decoder,
                                        byte[] encodedArr, byte[] srcArr) {
        byte[] resArr = decoder.decode(new String(encodedArr, DEF_CHARSET));
        assertArrayEquals(resArr, srcArr);
    }

    // Standalone test for MIME. Not dependent on the golden data.
    @Test
    public void mimeRoundTripTest()  {
        byte[] src = new byte[] {
                46, -97, -35, -44, 127, -60, -39, -4, -112, 34, -57, 47, -14, 67,
                40, 18, 90, -59, 68, 112, 23, 121, -91, 94, 35, 49, 104, 17, 30,
                -80, -104, -3, -53, 27, 38, -72, -47, 113, -52, 18, 5, -126 };
        Encoder encoder = Base64.getMimeEncoder(49, new byte[] { 0x7e });
        byte[] encoded = encoder.encode(src);
        Decoder decoder = Base64.getMimeDecoder();
        byte[] decoded = decoder.decode(encoded);
        assertArrayEquals(src, decoded);
    }
}
