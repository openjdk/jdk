/*
 *  Copyright (c) 2026, Google LLC. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

import static org.testng.Assert.*;

import org.testng.annotations.*;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/*
 * @test
 * @modules java.base/jdk.internal.foreign
 * @run testng TestStringView
 */
public class TestStringView {

    @Test(dataProvider = "strings")
    public void testStringView(String string) {
        MemorySegment.StringView view = MemorySegment.ofString(string);
        MemorySegment segment = view.segment();
        Charset charset = view.charset();
        if (charset == StandardCharsets.ISO_8859_1) {
            assertEquals(segment.byteSize(), string.length());
            for (int i = 0; i < string.length(); i++) {
                assertEquals(
                        string.charAt(i),
                        Byte.toUnsignedInt(segment.get(ValueLayout.JAVA_BYTE, i)));
            }
        } else if (charset == StandardCharsets.UTF_16LE || charset == StandardCharsets.UTF_16BE) {
            assertEquals(segment.byteSize(), string.length() * 2);
            for (int i = 0; i < string.length(); i++) {
                assertEquals(string.charAt(i), segment.get(ValueLayout.JAVA_CHAR_UNALIGNED, i * 2));
            }
        } else {
            throw new AssertionError(charset);
        }
    }

    @Test(dataProvider = "strings")
    public void testStringViewSubstring(String string) {
        for (int srcIndex = 0; srcIndex <= string.length(); srcIndex++) {
            for (int numChars = 0; numChars <= string.length() - srcIndex; numChars++) {
                MemorySegment.StringView view = MemorySegment.ofString(string, srcIndex, numChars);
                MemorySegment segment = view.segment();
                Charset charset = view.charset();
                if (charset == StandardCharsets.ISO_8859_1) {
                    assertEquals(segment.byteSize(), numChars);
                    for (int i = 0; i < numChars; i++) {
                        assertEquals(
                                string.charAt(srcIndex + i),
                                Byte.toUnsignedInt(segment.get(ValueLayout.JAVA_BYTE, i)));
                    }
                } else if (charset == StandardCharsets.UTF_16LE
                        || charset == StandardCharsets.UTF_16BE) {
                    assertEquals(segment.byteSize(), numChars * 2);
                    for (int i = 0; i < numChars; i++) {
                        assertEquals(
                                string.charAt(srcIndex + i),
                                segment.get(ValueLayout.JAVA_CHAR_UNALIGNED, i * 2));
                    }
                } else {
                    throw new AssertionError(charset);
                }
            }
        }
    }

    @Test(dataProvider = "strings")
    public void testStringViewRoundtrip(String string) {
        MemorySegment.StringView view = MemorySegment.ofString(string);
        MemorySegment segment = view.segment();
        Charset charset = view.charset();
        String roundtrip = segment.getString(0, charset, segment.byteSize());
        if (charset.newEncoder().canEncode(string)) {
            assertEquals(roundtrip, string);
        } else {
            assertEquals(roundtrip, new String(string.getBytes(charset), charset));
        }
    }

    @Test(dataProvider = "strings")
    public void testStringViewSubstringRoundtrip(String string) {
        for (int srcIndex = 0; srcIndex <= string.length(); srcIndex++) {
            for (int numChars = 0; numChars <= string.length() - srcIndex; numChars++) {
                MemorySegment.StringView view = MemorySegment.ofString(string, srcIndex, numChars);
                MemorySegment segment = view.segment();
                Charset charset = view.charset();
                String roundtrip = segment.getString(0, charset, segment.byteSize());
                String substring = string.substring(srcIndex, srcIndex + numChars);
                if (charset.newEncoder().canEncode(substring)) {
                    assertEquals(roundtrip, substring);
                } else {
                    assertEquals(roundtrip, new String(substring.getBytes(charset), charset));
                }
            }
        }
    }

    @Test
    public void testStringViewThrows() {
        assertThrows(NullPointerException.class, () -> MemorySegment.ofString(null));

        String testString = "abc";
        MemorySegment segment = MemorySegment.ofString(testString).segment();

        assertTrue(segment.isReadOnly());
        assertThrows(
                IllegalArgumentException.class,
                () -> segment.set(ValueLayout.JAVA_BYTE, 0, (byte) 0));
    }

    @Test
    public void testEmptyStringViewSubstring() {
        String testString = "abc";
        assertEquals(
                MemorySegment.ofString(testString, testString.length(), 0).segment().byteSize(), 0);
    }

    @Test
    public void testStringViewSubstringThrows() {
        assertThrows(NullPointerException.class, () -> MemorySegment.ofString(null, 0, 3));

        String testString = "abc";
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> MemorySegment.ofString(testString, -1, testString.length()));
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> MemorySegment.ofString(testString, 1, testString.length()));
        assertThrows(
                IndexOutOfBoundsException.class, () -> MemorySegment.ofString(testString, 0, -1));
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> MemorySegment.ofString(testString, 0, Integer.MAX_VALUE));

        MemorySegment segment =
                MemorySegment.ofString(testString, 0, testString.length()).segment();
        assertTrue(segment.isReadOnly());
        assertThrows(
                IllegalArgumentException.class,
                () -> segment.set(ValueLayout.JAVA_BYTE, 0, (byte) 0));
    }

    @DataProvider
    public static Object[][] strings() {
        return new Object[][] {
            {""},
            {"hello world"},
            {"123"},
            {"section \u00A7"},
            {"\u00E9"},
            {"cartwheel \uD83E\uDD38"},
            {"snowman \u26C4"},
            {"cjk \u4E00\u4E8C"},
            {"rainbow \uD83C\uDF08"},
            {"\uD83D\uDE00"},
            {"unpaired surrogate \uD83C"},
            {"\uD83D"},
            {"\uDC00"},
            {"\uDC00\uD83C"},
        };
    }
}
