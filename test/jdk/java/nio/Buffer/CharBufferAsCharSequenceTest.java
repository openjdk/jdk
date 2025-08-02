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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.PrimitiveIterator.OfInt;
import java.util.Random;

import jdk.test.lib.RandomFactory;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8343110 8361299
 * @key randomness
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @summary tests the CharBuffer implementations behaving as CharSequence in various states (position, limit, offset)
 * @run junit CharBufferAsCharSequenceTest
 */
public class CharBufferAsCharSequenceTest {

    private static final Random RAND = RandomFactory.getRandom();
    private static final int SIZE = RAND.nextInt(128, 1153);

    private static char[] randomChars() {
        char[] chars = new char[SIZE];
        for (int i=0; i<SIZE; ++i) {
            chars[i] = (char) RAND.nextInt();
        }
        return chars;
    }

    private static CharBuffer randomizeRange(CharBuffer cb) {
        int mid = cb.capacity() >>> 1;
        int start = RAND.nextInt(mid - 3); // from 0 to mid
        int end = RAND.nextInt(mid + 3, cb.capacity()); // from mid to capacity
        cb.position(start);
        cb.limit(end);
        return cb;
    }

    private static void populateAndAddCases(String type, CharBuffer cb, List<Arguments> cases) {
        assert cb.position() == 0 && cb.limit() == cb.capacity();
        char[] buf = randomChars();
        cb.put(buf);
        cb.clear();
        addCases(type, buf, cb, cases);
    }

    private static void addCases(String type, char[] buf, CharBuffer cb, List<Arguments> cases) {
        assert cb.position() == 0 && cb.limit() == cb.capacity();
        cases.add(Arguments.of(cb, buf, 0, buf.length, type + " full"));

        CharBuffer rndRange = randomizeRange(cb.duplicate());
        cases.add(Arguments.of(rndRange, buf, rndRange.position(), rndRange.limit(), type + "  at " + rndRange.position() + " through " + rndRange.limit()));
        cases.add(Arguments.of(rndRange.slice(), buf, rndRange.position(), rndRange.limit(), type + " sliced at " + rndRange.position() + " through " + rndRange.limit()));

        CharBuffer rndSlicedRange = randomizeRange(rndRange.slice());
        cases.add(Arguments.of(rndSlicedRange, buf, rndRange.position() + rndSlicedRange.position(), rndRange.position() + rndSlicedRange.limit(), type + " sliced at " + rndRange.position() + " with position " + rndSlicedRange.position() + " and limit " + rndSlicedRange.limit()));
    }

    static List<Arguments> charBufferArguments() {
        List<Arguments> args = new ArrayList<>();

        populateAndAddCases("HeapCharBuffer", CharBuffer.allocate(SIZE), args);
        populateAndAddCases("BEHeapByteBuffer", ByteBuffer.allocate(SIZE*2).order(ByteOrder.BIG_ENDIAN).asCharBuffer(), args);
        populateAndAddCases("LEHeapByteBuffer", ByteBuffer.allocate(SIZE*2).order(ByteOrder.LITTLE_ENDIAN).asCharBuffer(), args);
        populateAndAddCases("BEDirectByteBuffer", ByteBuffer.allocateDirect(SIZE*2).order(ByteOrder.BIG_ENDIAN).asCharBuffer(), args);
        populateAndAddCases("LEDirectByteBuffer", ByteBuffer.allocateDirect(SIZE*2).order(ByteOrder.LITTLE_ENDIAN).asCharBuffer(), args);

        char[] randomChars = randomChars();
        CharBuffer cb = CharBuffer.wrap(randomChars);
        addCases("StringCharBuffer over CharBuffer", randomChars, CharBuffer.wrap(cb), args);

        addCases("StringCharBuffer over String", randomChars, CharBuffer.wrap(new String(randomChars)), args);

        // nothing magic about 1273, it is just larger than 1k and an odd number - eliminating any alignment assumptions
        char[] buf = new char[1273];
        for (int i = 0; i < buf.length; ++i) {
            buf[i] = (char) i;
        }
        String stringBuf = new String(buf);

        // nothing magic about 7, it is simply an odd number to advance - making sure no expectations of alignment
        // comparing to 29 results in 5 loops (0, 7, 14, 21, 28), giving decent coverage of offset and limits
        for (int i = 0; i < 29; i += 7) {
            CharBuffer buffer = CharBuffer.wrap(buf, i, buf.length - i);
            args.add(Arguments.of(buffer, buf, i, buf.length, "HeapCharBuffer index " + i + " to end"));
            args.add(Arguments.of(buffer.slice(), buf, i, buf.length, "HeapCharBuffer slice " + i + " to end"));

            args.add(Arguments.of(CharBuffer.wrap(new String(buf, i, buf.length - i)), buf, i, buf.length,
                    "StringCharBuffer index " + i + " to end"));
            buffer = CharBuffer.wrap(stringBuf);
            buffer.position(i);
            args.add(Arguments.of(buffer.slice(), buf, i, buf.length, "StringCharBuffer slice " + i + " to end"));

            CharBuffer lehbbAsCB = ByteBuffer.allocate(buf.length * 2)
                                             .order(ByteOrder.LITTLE_ENDIAN)
                                             .asCharBuffer()
                                             .put(buf)
                                             .position(i);
            args.add(Arguments.of(lehbbAsCB, buf, i, buf.length, "LE HeapByteBuffer as CharBuffer index " + i + " to end"));

            CharBuffer behbdAsCB = ByteBuffer.allocateDirect(buf.length * 2)
                                             .order(ByteOrder.BIG_ENDIAN)
                                             .asCharBuffer()
                                             .put(buf)
                                             .position(i);
            args.add(Arguments.of(behbdAsCB, buf, i, buf.length,
                    "BE DirectByteBuffer as CharBuffer index " + i + " to end"));

            if (i > 0) {
                buffer = CharBuffer.wrap(buf, 1, buf.length - 1).slice();
                buffer.position(i - 1);
                args.add(Arguments.of(buffer, buf, i, buf.length,
                        "HeapCharBuffer slice/offset 1 index " + (i - 1) + " to end"));

                int end = buf.length - i;

                buffer = CharBuffer.wrap(buf, i, buf.length - (2 * i));
                args.add(Arguments.of(buffer, buf, i, end, "HeapCharBuffer index " + i + " to " + end));
                args.add(Arguments.of(buffer.slice(), buf, i, end, "HeapCharBuffer slice " + i + " to " + end));

                args.add(Arguments.of(CharBuffer.wrap(new String(buf, i, buf.length - (2 * i))), buf, i, end,
                        "StringCharBuffer index " + i + " to " + end));
                buffer = CharBuffer.wrap(stringBuf);
                buffer.position(i);
                buffer.limit(end);
                args.add(Arguments.of(buffer.slice(), buf, i, end, "StringCharBuffer slice " + i + " to " + end));

                CharBuffer behbbAsCB = ByteBuffer.allocate(buf.length * 2)
                                                 .order(ByteOrder.BIG_ENDIAN)
                                                 .asCharBuffer()
                                                 .put(buf)
                                                 .position(1)
                                                 .slice()
                                                 .position(i - 1)
                                                 .limit(end - 1);
                args.add(Arguments.of(behbbAsCB, buf, i, buf.length - i, "BE HeapByteBuffer as CharBuffer index " + i + " to " + end));

                CharBuffer ledbbAsCB = ByteBuffer.allocateDirect(buf.length * 2)
                                                 .order(ByteOrder.LITTLE_ENDIAN)
                                                 .asCharBuffer()
                                                 .put(buf)
                                                 .position(1)
                                                 .slice()
                                                 .position(i - 1)
                                                 .limit(end - 1);
                args.add(Arguments.of(ledbbAsCB, buf, i, buf.length - i, "LE DirectByteBuffer as CharBuffer index " + i + " to " + end));
            }
        }
        return args;
    }

    @ParameterizedTest(name="{4}")
    @MethodSource("charBufferArguments")
    void testToString(CharSequence actual, char[] expected, int start, int stop, String description) {
        assertEquals(new String(expected, start, stop - start), actual.toString(), description);
    }

    @ParameterizedTest(name="{4}")
    @MethodSource("charBufferArguments")
    void testLength(CharSequence actual, char[] expected, int start, int stop, String description) {
        assertEquals(stop - start, actual.length(), description);
    }

    @ParameterizedTest(name="{4}")
    @MethodSource("charBufferArguments")
    void testGetCharsRange(CharSequence actual, char[] expected, int start, int stop, String description) {
        char[] val = new char[16];
        int length = Math.min(10, stop - start - 1);
        actual.getChars(1, length + 1, val, 3);

        for (int i = 0; i < length; ++i) {
            assertEquals(expected[i + start + 1], val[i + 3], "val at offset of " + i + " from " + description);
        }
        // test that calling getChars did not move the position
        assertEquals(expected[start], actual.charAt(0), "first char after calling getChars: " + description);
    }

    @ParameterizedTest(name="{4}")
    @MethodSource("charBufferArguments")
    void testGetCharsAll(CharSequence actual, char[] expected, int start, int stop, String description) {
        char[] val = new char[stop - start];
        actual.getChars(0, val.length, val, 0);

        for (int i = 0; i < val.length; ++i) {
            assertEquals(expected[i + start], val[i], "val at offset of " + i + " from " + description);
        }
        // test that calling getChars did not move the position
        assertEquals(expected[start], actual.charAt(0), "first char after calling getChars: " + description);
    }

    @ParameterizedTest(name="{4}")
    @MethodSource("charBufferArguments")
    void testGetCharsNegativeSourceBeg(CharSequence actual, char[] expected, int start, int stop, String description) {
        char[] val = new char[16];
        assertThrows(IndexOutOfBoundsException.class, () -> actual.getChars(-1, 4, val, 1));
    }

    @ParameterizedTest(name="{4}")
    @MethodSource("charBufferArguments")
    void testGetCharsNegativeSourceEnd(CharSequence actual, char[] expected, int start, int stop, String description) {
        char[] val = new char[16];
        assertThrows(IndexOutOfBoundsException.class, () -> actual.getChars(0, -4, val, 1));
    }

    @ParameterizedTest(name="{4}")
    @MethodSource("charBufferArguments")
    void testGetCharsSourceEndBeforeBeg(CharSequence actual, char[] expected, int start, int stop, String description) {
        char[] val = new char[16];
        assertThrows(IndexOutOfBoundsException.class, () -> actual.getChars(3, 2, val, 1));
    }

    @ParameterizedTest(name="{4}")
    @MethodSource("charBufferArguments")
    void testGetCharsNegativeDestBeg(CharSequence actual, char[] expected, int start, int stop, String description) {
        char[] val = new char[16];
        assertThrows(IndexOutOfBoundsException.class, () -> actual.getChars(1, 3, val, -1));
    }

    @ParameterizedTest(name="{4}")
    @MethodSource("charBufferArguments")
    void testGetCharsDestBegOOB(CharSequence actual, char[] expected, int start, int stop, String description) {
        char[] val = new char[16];
        assertThrows(IndexOutOfBoundsException.class, () -> actual.getChars(1, 4, val, val.length + 1));
    }

    @ParameterizedTest(name="{4}")
    @MethodSource("charBufferArguments")
    void testGetCharsDestLengthOOB(CharSequence actual, char[] expected, int start, int stop, String description) {
        char[] val = new char[16];
        assertThrows(IndexOutOfBoundsException.class, () -> actual.getChars(1, 4, val, val.length - 2));
    }

    @ParameterizedTest(name="{4}")
    @MethodSource("charBufferArguments")
    void testGetCharsNullDst(CharSequence actual, char[] expected, int start, int stop, String description) {
        assertThrows(NullPointerException.class, () -> actual.getChars(0, 1, null, 0));
    }

    @ParameterizedTest(name="{4}")
    @MethodSource("charBufferArguments")
    void testCharAt(CharSequence actual, char[] expected, int start, int stop, String description) {
        for (int i = 0, j = stop - start; i < j; ++i) {
            assertEquals(expected[start + i], actual.charAt(i), "chart at " + i + ": " + description);
        }
    }

    @ParameterizedTest(name="{4}")
    @MethodSource("charBufferArguments")
    void testCharAtNegativePos(CharSequence actual, char[] expected, int start, int stop, String description) {
        assertThrows(IndexOutOfBoundsException.class, () -> actual.charAt(-1));
    }

    @ParameterizedTest(name="{4}")
    @MethodSource("charBufferArguments")
    void testCharAtPosOOB(CharSequence actual, char[] expected, int start, int stop, String description) {
        assertThrows(IndexOutOfBoundsException.class, () -> actual.charAt(stop - start + 1));
    }

    @ParameterizedTest(name="{4}")
    @MethodSource("charBufferArguments")
    void testChars(CharSequence actual, char[] expected, int start, int stop, String description) {
        OfInt chars = actual.chars().iterator();
        for (int i = 0, j = stop - start; i < j; ++i) {
            assertEquals(expected[start + i], (char) chars.nextInt(), "chart at " + i + ": " + description);
        }
        assertFalse(chars.hasNext(), "chars has more elements than expected " + description);
    }

    @ParameterizedTest(name="{4}")
    @MethodSource("charBufferArguments")
    void testCodePoints(CharSequence actual, char[] expected, int start, int stop, String description) {
        OfInt codePoints = actual.codePoints().iterator();
        for (int i = 0, j = stop - start; i < j; ++i) {
            char c1 = expected[start + i];
            int expectedCodePoint = c1;
            if (Character.isHighSurrogate(c1) && (i + 1) < j) {
                char c2 = expected[start + i + 1];
                if (Character.isLowSurrogate(c2)) {
                    expectedCodePoint = Character.toCodePoint(c1, c2);
                    ++i;
                }
            }
            assertEquals(expectedCodePoint, codePoints.nextInt(), "code point at " + i + ": " + description);
        }
        assertFalse(codePoints.hasNext(), "codePoints has more elements than expected " + description);
    }

    @ParameterizedTest(name="{4}")
    @MethodSource("charBufferArguments")
    void testSubSequence(CharSequence actual, char[] expected, int start, int stop, String description) {
        int maxTests = Math.min(7,  ((stop - start) >> 1) - 1);
        for (int i = 0; i < maxTests; ++i) {
            assertEquals(new String(expected, start + i, stop - start - (2 * i)),
                    actual.subSequence(i, actual.length() - i).toString(),
                    "subsequence at index " + i + " for " + description);
        }
    }
}
