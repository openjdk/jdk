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
 * @bug 4235519 8004212 8005394 8007298 8006295 8006315 8006530 8007379 8008925
 *      8014217 8025003 8026330 8028397 8129544 8165243 8176379 8222187 8331342
 * @summary tests java.util.Base64
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run junit TestBase64
 * @key randomness
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import jdk.test.lib.RandomFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestBase64 {

    private static final Random RND = RandomFactory.getRandom();
    private static final int ITERATIONS = 10;
    private static final int BYTES = 200;
    private static final Charset ASCII = StandardCharsets.US_ASCII;

    // Check encoded/decode behavior with associated encoder and decoder
    @ParameterizedTest
    @MethodSource("encodersAndDecoders")
    public void encodeDecodeRoundTripTest(Base64.Encoder enc, Base64.Decoder dec)
            throws Throwable {
        enc.encode(new byte[0]);
        dec.decode(new byte[0]);

        for (boolean withoutPadding : new boolean[] { false, true}) {
            if (withoutPadding) {
                 enc = enc.withoutPadding();
            }
            for (int i=0; i<ITERATIONS; i++) {
                for (int j=1; j<BYTES; j++) {
                    byte[] orig = new byte[j];
                    RND.nextBytes(orig);

                    // --------testing encode/decode(byte[])--------
                    byte[] encoded = enc.encode(orig);
                    byte[] decoded = dec.decode(encoded);

                    assertArrayEquals(orig, decoded,
                               "Base64 array encoding/decoding failed!");
                    if (withoutPadding) {
                        assertNotEquals('=', encoded[encoded.length - 1],
                                "Base64 enc.encode().withoutPadding() has padding!");
                    }
                    // --------testing encodetoString(byte[])/decode(String)--------
                    String str = enc.encodeToString(orig);
                    assertArrayEquals(encoded, str.getBytes(ASCII),
                            "Base64 encodingToString() failed!");

                    byte[] buf = dec.decode(new String(encoded, ASCII));
                    assertArrayEquals(buf, orig, "Base64 decoding(String) failed!");

                    //-------- testing encode/decode(Buffer)--------
                    testEncode(enc, ByteBuffer.wrap(orig), encoded);
                    ByteBuffer bin = ByteBuffer.allocateDirect(orig.length);
                    bin.put(orig).flip();
                    testEncode(enc, bin, encoded);

                    testDecode(dec, ByteBuffer.wrap(encoded), orig);
                    bin = ByteBuffer.allocateDirect(encoded.length);
                    bin.put(encoded).flip();
                    testDecode(dec, bin, orig);

                    // --------testing decode.wrap(input stream)--------
                    // 1) random buf length
                    ByteArrayInputStream bais = new ByteArrayInputStream(encoded);
                    InputStream is = dec.wrap(bais);
                    buf = new byte[orig.length + 10];
                    int len = orig.length;
                    int off = 0;
                    while (true) {
                        int n = RND.nextInt(len);
                        if (n == 0)
                            n = 1;
                        n = is.read(buf, off, n);
                        if (n == -1) {
                            assertEquals(off, orig.length, "Base64 stream decoding failed");
                            break;
                        }
                        off += n;
                        len -= n;
                        if (len == 0)
                            break;
                    }
                    buf = Arrays.copyOf(buf, off);
                    assertArrayEquals(buf, orig, "Base64 stream decoding failed!");

                    // 2) read one byte each
                    bais.reset();
                    is = dec.wrap(bais);
                    buf = new byte[orig.length + 10];
                    off = 0;
                    int b;
                    while ((b = is.read()) != -1) {
                        buf[off++] = (byte)b;
                    }
                    buf = Arrays.copyOf(buf, off);
                    assertArrayEquals(buf, orig, "Base64 stream decoding failed!");

                    // --------testing encode.wrap(output stream)--------
                    ByteArrayOutputStream baos = new ByteArrayOutputStream((orig.length + 2) / 3 * 4 + 10);
                    OutputStream os = enc.wrap(baos);
                    off = 0;
                    len = orig.length;
                    for (int k = 0; k < 5; k++) {
                        if (len == 0)
                            break;
                        int n = RND.nextInt(len);
                        if (n == 0)
                            n = 1;
                        os.write(orig, off, n);
                        off += n;
                        len -= n;
                    }
                    if (len != 0)
                        os.write(orig, off, len);
                    os.close();
                    buf = baos.toByteArray();
                    assertArrayEquals(buf, encoded, "Base64 stream encoding failed!");

                    // 2) write one byte each
                    baos.reset();
                    os = enc.wrap(baos);
                    off = 0;
                    while (off < orig.length) {
                        os.write(orig[off++]);
                    }
                    os.close();
                    buf = baos.toByteArray();
                    assertArrayEquals(buf, encoded, "Base64 stream encoding failed!");

                    // --------testing encode(in, out); -> bigger buf--------
                    buf = new byte[encoded.length + RND.nextInt(100)];
                    int ret = enc.encode(orig, buf);
                    assertEquals(ret, encoded.length,
                               "Base64 enc.encode(src, null) returns wrong size!");
                    buf = Arrays.copyOf(buf, ret);
                    assertArrayEquals(buf, encoded,
                               "Base64 enc.encode(src, dst) failed!");

                    // --------testing decode(in, out); -> bigger buf--------
                    buf = new byte[orig.length + RND.nextInt(100)];
                    ret = dec.decode(encoded, buf);
                    assertEquals(ret, orig.length,
                              "Base64 enc.encode(src, null) returns wrong size!");
                    buf = Arrays.copyOf(buf, ret);
                    assertArrayEquals(buf, orig,
                               "Base64 dec.decode(src, dst) failed!");

                }
            }
        }
    }

    private static final byte[] ba_null = null;
    private static final String str_null = null;
    private static final ByteBuffer bb_null = null;

    @ParameterizedTest
    @MethodSource("encoders")
    public void testNull(Base64.Encoder enc) {
        assertThrows(NullPointerException.class, () -> enc.encode(ba_null));
        assertThrows(NullPointerException.class, () -> enc.encodeToString(ba_null));
        assertThrows(NullPointerException.class, () -> enc.encode(ba_null, new byte[10]));
        assertThrows(NullPointerException.class, () -> enc.encode(new byte[10], ba_null));
        assertThrows(NullPointerException.class, () -> enc.encode(bb_null));
        assertThrows(NullPointerException.class, () -> enc.wrap((OutputStream)null));
    }

    // Ensure Mime throws IAE on null separator
    @Test
    public void getMimeNullSeparatorTest() {
        assertThrows(NullPointerException.class,
                () -> Base64.getMimeEncoder(10, null));
    }

    // Ensure Mime throws IAE on bad separator
    @Test
    public void getMimeIllegalSeparatorTest() {
        assertThrows(IllegalArgumentException.class,
                () -> Base64.getMimeEncoder(10, new byte[]{'\r', 'N'}));
    }

    // NPE tests
    @ParameterizedTest
    @MethodSource("decoders")
    public void testNull(Base64.Decoder dec) {
        assertThrows(NullPointerException.class, () -> dec.decode(ba_null));
        assertThrows(NullPointerException.class, () -> dec.decode(str_null));
        assertThrows(NullPointerException.class, () -> dec.decode(ba_null, new byte[10]));
        assertThrows(NullPointerException.class, () -> dec.decode(new byte[10], ba_null));
        assertThrows(NullPointerException.class, () -> dec.decode(bb_null));
        assertThrows(NullPointerException.class, () -> dec.wrap((InputStream)null));
    }

    // Ensure IAE thrown on illegal data
    @Test
    public void testIllegalDecoded() {
        byte[] src = new byte[1024];
        RND.nextBytes(src);
        final byte[] encoded = Base64.getEncoder().encode(src);
        encoded[2] = (byte)0xe0; // illegal char
        Base64.Decoder dec = Base64.getDecoder();
        assertThrows(IllegalArgumentException.class, () -> dec.decode(encoded));
        assertThrows(IllegalArgumentException.class, () -> dec.decode(encoded, new byte[1024]));
        assertThrows(IllegalArgumentException.class, () -> dec.decode(ByteBuffer.wrap(encoded)));
    }

    // General tests to ensure IOException is thrown for encoders when stream closed
    @ParameterizedTest
    @MethodSource("encoders")
    public void testIOE(Base64.Encoder enc) throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(8192);
        OutputStream os = enc.wrap(baos);
        os.write(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9});
        os.close();
        assertThrows(IOException.class, () -> os.write(10));
        assertThrows(IOException.class, () -> os.write(new byte[] {10}));
        assertThrows(IOException.class, () -> os.write(new byte[] {10}, 1, 4));
    }

    // General tests to ensure IOException is thrown for decoders when stream closed
    @ParameterizedTest
    @MethodSource("decodersWithEncoded")
    public void testIOE(Base64.Decoder dec, byte[] encoded) throws Throwable {
        ByteArrayInputStream bais = new ByteArrayInputStream(encoded);
        InputStream is = dec.wrap(bais);
        is.read(new byte[10]);
        is.close();
        assertThrows(IOException.class, () -> is.read());
        assertThrows(IOException.class, () -> is.read(new byte[] {10}));
        assertThrows(IOException.class, () -> is.read(new byte[] {10}, 1, 4));
        assertThrows(IOException.class, () -> is.available());
        assertThrows(IOException.class, () -> is.skip(20));
    }

    // test mime decoding with ignored character after padding
    @Test
    public void testDecodeIgnoredAfterPadding() {
        for (byte nonBase64 : new byte[] {'#', '(', '!', '\\', '-', '_', '\n', '\r'}) {
            byte[][] src = new byte[][]{
                    "A".getBytes(ASCII),
                    "AB".getBytes(ASCII),
                    "ABC".getBytes(ASCII),
                    "ABCD".getBytes(ASCII),
                    "ABCDE".getBytes(ASCII)
            };
            Base64.Encoder encM = Base64.getMimeEncoder();
            Base64.Decoder decM = Base64.getMimeDecoder();
            Base64.Encoder enc = Base64.getEncoder();
            Base64.Decoder dec = Base64.getDecoder();
            for (byte[] bytes : src) {
                // decode(byte[])
                byte[] encoded = encM.encode(bytes);
                encoded = Arrays.copyOf(encoded, encoded.length + 1);
                encoded[encoded.length - 1] = nonBase64;
                assertArrayEquals(decM.decode(encoded), bytes, "Non-base64 char is not ignored");
                byte[] decoded = new byte[bytes.length];
                decM.decode(encoded, decoded);
                assertArrayEquals(decoded, bytes, "Non-base64 char is not ignored");
                byte[] finalEncoded = encoded;
                assertThrows(IllegalArgumentException.class, () -> dec.decode(finalEncoded));
            }
        }
    }

    // malformed padding/ending
    @ParameterizedTest
    @MethodSource("decoders")
    public void testMalformedPadding(Base64.Decoder dec) {
        Object[] data = new Object[] {
            "$=#",       "",      0,      // illegal ending unit
            "A",         "",      0,      // dangling single byte
            "A=",        "",      0,
            "A==",       "",      0,
            "QUJDA",     "ABC",   4,
            "QUJDA=",    "ABC",   4,
            "QUJDA==",   "ABC",   4,

            "=",         "",      0,      // unnecessary padding
            "QUJD=",     "ABC",   4,      //"ABC".encode() -> "QUJD"

            "AA=",       "",      0,      // incomplete padding
            "QQ=",       "",      0,
            "QQ=N",      "",      0,      // incorrect padding
            "QQ=?",      "",      0,
            "QUJDQQ=",   "ABC",   4,
            "QUJDQQ=N",  "ABC",   4,
            "QUJDQQ=?",  "ABC",   4,
        };
        for (int i = 0; i < data.length; i += 3) {
            final String srcStr = (String) data[i];
            final byte[] srcBytes = srcStr.getBytes(ASCII);
            final ByteBuffer srcBB = ByteBuffer.wrap(srcBytes);

            // decode(byte[])
            assertThrows(IllegalArgumentException.class, () -> dec.decode(srcBytes));

            // decode(String)
            assertThrows(IllegalArgumentException.class, () -> dec.decode(srcStr));

            // decode(ByteBuffer)
            assertThrows(IllegalArgumentException.class, () -> dec.decode(srcBB));

            // wrap stream
            assertThrows(IOException.class, () -> {
                try (InputStream is = dec.wrap(new ByteArrayInputStream(srcBytes))) {
                    while (is.read() != -1) ;
                }
            });
        }

        // anything left after padding is "invalid"/IAE, if
        // not MIME. In case of MIME, non-base64 character(s) is ignored.
        assertThrows(IllegalArgumentException.class, () -> Base64.getDecoder().decode("AA==\u00D2"));
        assertThrows(IllegalArgumentException.class, () -> Base64.getUrlDecoder().decode("AA==\u00D2"));
        Base64.getMimeDecoder().decode("AA==\u00D2");
     }

    // test decoding of unpadded data
     @Test
     public void  testDecodeUnpadded() throws Throwable {
        byte[] srcA = new byte[] { 'Q', 'Q' };
        byte[] srcAA = new byte[] { 'Q', 'Q', 'E'};
        Base64.Decoder dec = Base64.getDecoder();
        byte[] ret = dec.decode(srcA);
        assertEquals('A', ret[0], "Decoding unpadding input A failed");
        ret = dec.decode(srcAA);
        assertFalse(ret[0] != 'A' && ret[1] != 'A', "Decoding unpadding input AA failed");
        ret = new byte[10];
        assertFalse(dec.wrap(new ByteArrayInputStream(srcA)).read(ret) != 1 &&
            ret[0] != 'A', "Decoding unpadding input A from stream failed");
        assertFalse(dec.wrap(new ByteArrayInputStream(srcA)).read(ret) != 2 &&
            ret[0] != 'A' && ret[1] != 'A', "Decoding unpadding input AA from stream failed");
    }

    // single-non-base64-char should be ignored for mime decoding, but
    // iae for basic decoding
    @Test
    public void testSingleNonBase64MimeDec() {
        for (String nonBase64 : new String[] {"#", "(", "!", "\\", "-", "_"}) {
            assertEquals(0, Base64.getMimeDecoder().decode(nonBase64).length,
                    "non-base64 char is not ignored");
            assertThrows(IllegalArgumentException.class,
                    () -> Base64.getDecoder().decode(nonBase64));
        }
    }

    // given invalid args, encoder should not produce output
    @ParameterizedTest
    @MethodSource("encoders")
    public void testEncoderKeepsSilence(Base64.Encoder enc) {
        List<Integer> vals = new ArrayList<>(List.of(Integer.MIN_VALUE,
                Integer.MIN_VALUE + 1, -1111, -2, -1, 0, 1, 2, 3, 1111,
                Integer.MAX_VALUE - 1, Integer.MAX_VALUE));
        vals.addAll(List.of(RND.nextInt(), RND.nextInt(), RND.nextInt(),
                RND.nextInt()));
        byte[] buf = new byte[] {1, 0, 91};
        for (int off : vals) {
            for (int len : vals) {
                if (off >= 0 && len >= 0 && off <= buf.length - len) {
                    // valid args, skip them
                    continue;
                }
                // current args are invalid, test them
                ByteArrayOutputStream baos = new ByteArrayOutputStream(100);
                assertThrows(IndexOutOfBoundsException.class, () -> {
                    try (OutputStream os = enc.wrap(baos)) {
                        os.write(buf, off, len);
                    }
                }, "Expected IOOBEx was not thrown");
                assertFalse(baos.size() > 0,
                        "No output was expected, but got " + baos.size() + " bytes");
            }
        }
    }

    // given invalid args, decoder should not consume input
    @ParameterizedTest
    @MethodSource("decoders")
    public void testDecoderKeepsAbstinence(Base64.Decoder dec)
            throws Throwable {
        List<Integer> vals = new ArrayList<>(List.of(Integer.MIN_VALUE,
                Integer.MIN_VALUE + 1, -1111, -2, -1, 0, 1, 2, 3, 1111,
                Integer.MAX_VALUE - 1, Integer.MAX_VALUE));
        vals.addAll(List.of(RND.nextInt(), RND.nextInt(), RND.nextInt(),
                RND.nextInt()));
        byte[] buf = new byte[3];
        for (int off : vals) {
            for (int len : vals) {
                if (off >= 0 && len >= 0 && off <= buf.length - len) {
                    // valid args, skip them
                    continue;
                }
                // current args are invalid, test them
                String input = "AAAAAAAAAAAAAAAAAAAAAA";
                ByteArrayInputStream bais =
                        new ByteArrayInputStream(input.getBytes("Latin1"));
                assertThrows(IndexOutOfBoundsException.class, () -> {
                    try (InputStream is = dec.wrap(bais)) {
                        is.read(buf, off, len);
                    }
                }, "Expected IOOBEx was not thrown");
                assertEquals(input.length(), bais.available(),
                        "No input should be consumed, but consumed "
                                + (input.length() - bais.available()) + " bytes");
            }
        }
    }

    // Tests patch addressing JDK-8222187
    // Ensure decoder stream does not add unexpected null bytes at end
    @Test
    public void unexpectedNullBytesTest() throws Throwable {
        byte[] orig = "12345678".getBytes(ASCII);
        byte[] encoded = Base64.getEncoder().encode(orig);
        // decode using different buffer sizes, up to a longer one than needed
        for (int bufferSize = 1; bufferSize <= encoded.length + 1; bufferSize++) {
            try (
                    InputStream in = Base64.getDecoder().wrap(
                            new ByteArrayInputStream(encoded));
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ) {
                byte[] buffer = new byte[bufferSize];
                int read;
                while ((read = in.read(buffer, 0, bufferSize)) >= 0) {
                    baos.write(buffer, 0, read);
                }
                // compare result, output info if lengths do not match
                byte[] decoded = baos.toByteArray();
                assertArrayEquals(decoded, orig, "Base64 stream decoding failed!");
            }
        }
    }

    // Utilities to test encoding and decoding correctness of Base64
    private static void testEncode(Base64.Encoder enc, ByteBuffer bin, byte[] expected) {
        ByteBuffer bout = enc.encode(bin);
        byte[] buf = new byte[bout.remaining()];
        bout.get(buf);
        assertFalse(bin.hasRemaining(), "Base64 enc.encode(ByteBuffer) failed!");
        assertArrayEquals(buf, expected, "Base64 enc.encode(bf, bf) failed!");
    }

    private static void testDecode(Base64.Decoder dec, ByteBuffer bin, byte[] expected) {
        ByteBuffer bout = dec.decode(bin);
        byte[] buf = new byte[bout.remaining()];
        bout.get(buf);
        assertArrayEquals(buf, expected, "Base64 dec.decode(bf) failed!");
    }

    // Data providers
    // Associated encoder and decoder
    // Additionally, provides further test cases for randomized Mime encoders
    private static List<Arguments> encodersAndDecoders() {
        List<Arguments> args = new ArrayList<>();
        args.add(Arguments.of(Base64.getEncoder(), Base64.getDecoder()));
        args.add(Arguments.of(Base64.getUrlEncoder(), Base64.getUrlDecoder()));
        args.add(Arguments.of(Base64.getMimeEncoder(), Base64.getMimeDecoder()));

        byte[] nl_1 = new byte[] {'\n'};
        byte[] nl_2 = new byte[] {'\n', '\r'};
        byte[] nl_3 = new byte[] {'\n', '\r', '\n'};
        for (int i = 0; i < ITERATIONS; i++) {
            int len = RND.nextInt(BYTES) + 4;
            args.add(Arguments.of(Base64.getMimeEncoder(len, nl_1),
                    Base64.getMimeDecoder()));
            args.add(Arguments.of(Base64.getMimeEncoder(len, nl_2),
                    Base64.getMimeDecoder()));
            args.add(Arguments.of(Base64.getMimeEncoder(len, nl_3),
                    Base64.getMimeDecoder()));
        }
        // test mime case with < 4 length
        for (int len = 0; len < 4; len++) {
            args.add(Arguments.of(Base64.getMimeEncoder(len, nl_1),
                    Base64.getMimeDecoder()));
            args.add(Arguments.of(Base64.getMimeEncoder(len, nl_2),
                    Base64.getMimeDecoder()));
            args.add(Arguments.of(Base64.getMimeEncoder(len, nl_3),
                    Base64.getMimeDecoder()));
        }
        return args;
    }

    // Basic encoders
    private static Stream<Base64.Encoder> encoders () {
        return Stream.of(Base64.getEncoder(), Base64.getMimeEncoder(),
                Base64.getUrlEncoder(), Base64.getMimeEncoder(10, new byte[]{'\n'}));
    }

    // Basic decoders
    private static Stream<Base64.Decoder> decoders () {
        return Stream.of(Base64.getDecoder(), Base64.getMimeDecoder(), Base64.getUrlDecoder());
    }

    // Decoders that also provide associated encoded data
    private static Stream<Arguments> decodersWithEncoded() {
        byte[] src = new byte[1024];
        RND.nextBytes(src);
        final byte[] decoded = Base64.getEncoder().encode(src);
        return Stream.of(Arguments.of(Base64.getDecoder(), decoded),
                Arguments.of(Base64.getMimeDecoder(), decoded),
                Arguments.of(Base64.getUrlDecoder(), Base64.getUrlEncoder().encode(src)));
    }
}
