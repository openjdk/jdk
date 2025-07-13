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
 
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.PrimitiveIterator.OfInt;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/*
 * @test
 * @summary tests the CharBuffer implementations behaving as CharSequence in various states (postion, limit, offset)
 * @run junit CharBufferAsCharSequenceTest
 */
public class CharBufferAsCharSequenceTest {
    private static final List<Arguments> ARGS = new ArrayList<>();

    static {
        char[] buf = new char[1273];
        for (int i = 0; i < buf.length; ++i) {
            buf[i] = (char) i;
        }
        String stringBuf = new String(buf);

        for (int i = 0; i < 29; i += 7) {
            CharBuffer buffer = CharBuffer.wrap(buf, i, buf.length - i);
            ARGS.add(Arguments.of(buffer, buf, i, buf.length, "HeapCharBuffer index " + i + " to end"));
            ARGS.add(Arguments.of(buffer.slice(), buf, i, buf.length, "HeapCharBuffer slice " + i + " to end"));

            ARGS.add(Arguments.of(CharBuffer.wrap(new String(buf, i, buf.length - i)), buf, i, buf.length,
                    "StringCharBuffer index " + i + " to end"));
            buffer = CharBuffer.wrap(stringBuf);
            buffer.position(i);
            ARGS.add(Arguments.of(buffer.slice(), buf, i, buf.length, "StringCharBuffer slice " + i + " to end"));

            if (i > 0) {
                int end = buf.length - i;

                buffer = CharBuffer.wrap(buf, i, buf.length - (2 * i));
                ARGS.add(Arguments.of(buffer, buf, i, end, "HeapCharBuffer index " + i + " to " + end));
                ARGS.add(Arguments.of(buffer.slice(), buf, i, end, "HeapCharBuffer slice " + i + " to " + end));

                ARGS.add(Arguments.of(CharBuffer.wrap(new String(buf, i, buf.length - (2 * i))), buf, i, end,
                        "StringCharBuffer index " + i + " to " + end));
                buffer = CharBuffer.wrap(stringBuf);
                buffer.position(i);
                buffer.limit(end);
                ARGS.add(Arguments.of(buffer.slice(), buf, i, end, "StringCharBuffer slice " + i + " to " + end));
            }
        }
    }

    static List<Arguments> charBufferArguments() {
        return ARGS;
    }

    @ParameterizedTest
    @MethodSource("charBufferArguments")
    void testToString(CharSequence actual, char[] expected, int start, int stop, String description) {
        assertEquals(new String(expected, start, stop - start), actual.toString(), description);
    }

    @ParameterizedTest
    @MethodSource("charBufferArguments")
    void testLength(CharSequence actual, char[] expected, int start, int stop, String description) {
        assertEquals(stop - start, actual.length(), description);
    }

    @ParameterizedTest
    @MethodSource("charBufferArguments")
    void testGetChars_range(CharSequence actual, char[] expected, int start, int stop, String description) {
        char[] val = new char[16];
        actual.getChars(1, 5, val, 3);

        for (int i = 0; i < 4; ++i) {
            assertEquals(expected[i + start + 1], val[i + 3], "val at offset of " + i + " from " + description);
        }
    }

    @ParameterizedTest
    @MethodSource("charBufferArguments")
    void testCharAt(CharSequence actual, char[] expected, int start, int stop, String description) {
        for (int i = 0, j = stop - start; i < j; ++i) {
            assertEquals(expected[start + i], actual.charAt(i), "chart at " + i + ": " + description);
        }
    }

    @ParameterizedTest
    @MethodSource("charBufferArguments")
    void testChars(CharSequence actual, char[] expected, int start, int stop, String description) {
        OfInt chars = actual.chars().iterator();
        for (int i = 0, j = stop - start; i < j; ++i) {
            assertEquals(expected[start + i], (char) chars.nextInt(), "chart at " + i + ": " + description);
        }
    }
}
