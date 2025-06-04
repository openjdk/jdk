/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package test.java.lang.String;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test
 * @bug 8311906
 * @modules java.base/java.lang:open
 * @summary check String's racy constructors
 * @run junit/othervm -XX:+CompactStrings test.java.lang.String.StringRacyConstructor
 * @run junit/othervm -XX:-CompactStrings test.java.lang.String.StringRacyConstructor
 */

public class StringRacyConstructor {
    private static final byte LATIN1 = 0;
    private static final byte UTF16  = 1;

    private static final Field STRING_CODER_FIELD;
    private static final Field SB_CODER_FIELD;
    private static final boolean COMPACT_STRINGS;

    static {
        try {
            STRING_CODER_FIELD = String.class.getDeclaredField("coder");
            STRING_CODER_FIELD.setAccessible(true);
            SB_CODER_FIELD = Class.forName("java.lang.AbstractStringBuilder").getDeclaredField("coder");
            SB_CODER_FIELD.setAccessible(true);
            COMPACT_STRINGS = isCompactStrings();
        } catch (NoSuchFieldException ex ) {
            throw new ExceptionInInitializerError(ex);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /* {@return true iff CompactStrings are enabled}
     */
    public static boolean isCompactStrings() {
        try {
            Field compactStringField = String.class.getDeclaredField("COMPACT_STRINGS");
            compactStringField.setAccessible(true);
            return compactStringField.getBoolean(null);
        } catch (NoSuchFieldException ex) {
            throw new ExceptionInInitializerError(ex);
        } catch (IllegalAccessException iae) {
            throw new AssertionError(iae);
        }
    }

    // Return the coder for the String
    private static int coder(String s) {
        try {
            return STRING_CODER_FIELD.getByte(s);
        } catch (IllegalAccessException iae) {
            throw new AssertionError(iae);
        }
    }

    // Return the coder for the StringBuilder
    private static int sbCoder(StringBuilder sb) {
        try {
            return SB_CODER_FIELD.getByte(sb);
        } catch (IllegalAccessException iae) {
            throw new AssertionError(iae);
        }
    }

    // Return a summary of the internals of the String
    // The coder and indicate if the coder matches the string contents
    private static String inspectString(String s) {
        try {
            char[] chars = s.toCharArray();
            String r = new String(chars);

            boolean invalidCoder = coder(s) != coder(r);
            String coder = STRING_CODER_FIELD.getByte(s) == 0 ? "isLatin1" : "utf16";
            return (invalidCoder ? "INVALID CODER" : "" ) + " \"" + s + "\", coder: " + coder;
        } catch (IllegalAccessException ex ) {
            return "EXCEPTION: " + ex.getMessage();
        }
    }

    /**
     * {@return true if the coder matches the presence/lack of UTF16 characters}
     * If it returns false, the coder and the contents have failed the precondition for string.
     * @param orig a string
     */
    private static boolean validCoder(String orig) {
        if (!COMPACT_STRINGS) {
            assertEquals(UTF16, coder(orig), "Non-COMPACT STRINGS coder must be UTF16");
        }
        int accum = 0;
        for (int i = 0; i < orig.length(); i++)
            accum |= orig.charAt(i);
        byte expectedCoder = (accum < 256) ? LATIN1 : UTF16;
        return expectedCoder == coder(orig);
    }

    // Check a StringBuilder for consistency of coder and latin1 vs UTF16
    private static boolean validCoder(StringBuilder orig) {
        int accum = 0;
        for (int i = 0; i < orig.length(); i++)
            accum |= orig.charAt(i);
        byte expectedCoder = (accum < 256) ? LATIN1 : UTF16;
        return expectedCoder == sbCoder(orig);
    }

    @Test
    @EnabledIf("test.java.lang.String.StringRacyConstructor#isCompactStrings")
    public void checkStringRange() {
        char[] chars = {'a', 'b', 'c', 0xff21, 0xff22, 0xff23};
        String orig = new String(chars);
        char[] xx = orig.toCharArray();
        String stringFromChars = new String(xx);
        assertEquals(orig, stringFromChars, "mixed chars");
        assertTrue(validCoder(stringFromChars), "invalid coder"
                + ", invalid coder: " + inspectString(stringFromChars));
    }

    private static List<String> strings() {
        return List.of("01234", " ");
    }

    @ParameterizedTest
    @MethodSource("strings")
    @EnabledIf("test.java.lang.String.StringRacyConstructor#isCompactStrings")
    public void racyString(String orig) {
        String racyString = racyStringConstruction(orig);
        // The contents are indeterminate due to the race
        assertTrue(validCoder(racyString), orig + " string invalid"
                + ", racyString: " + inspectString(racyString));
    }

    @ParameterizedTest
    @MethodSource("strings")
    @EnabledIf("test.java.lang.String.StringRacyConstructor#isCompactStrings")
    public void racyCodePoint(String orig) {
        String iffyString = racyStringConstructionCodepoints(orig);
        // The contents are indeterminate due to the race
        assertTrue(validCoder(iffyString), "invalid coder in non-deterministic string"
                + ", orig:" + inspectString(orig)
                + ", iffyString: " + inspectString(iffyString));
    }

    @ParameterizedTest
    @MethodSource("strings")
    @EnabledIf("test.java.lang.String.StringRacyConstructor#isCompactStrings")
    public void racyCodePointSurrogates(String orig) {
        String iffyString = racyStringConstructionCodepointsSurrogates(orig);
        // The contents are indeterminate due to the race
        if (!orig.equals(iffyString))
            System.err.println("orig: " + orig + ", iffy: " + iffyString + Arrays.toString(iffyString.codePoints().toArray()));
        assertTrue(validCoder(iffyString), "invalid coder in non-deterministic string"
                + ", orig:" + inspectString(orig)
                + ", iffyString: " + inspectString(iffyString));
    }

    // Test the private methods of StringUTF16 that compress and copy COMPRESSED_STRING
    // encoded byte arrays.
    @Test
    public void verifyUTF16CopyBytes()
            throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<?> stringUTF16 = Class.forName("java.lang.StringUTF16");
        Method mCompressChars = stringUTF16.getDeclaredMethod("compress",
                char[].class, int.class, byte[].class, int.class, int.class);
        mCompressChars.setAccessible(true);

        // First warmup the intrinsic and check 1 case
        char[] chars = {'a', 'b', 'c', 0xff21, 0xff22, 0xff23};
        byte[] bytes = new byte[chars.length];
        int printWarningCount = 0;

        for (int i = 0; i < 1_000_000; i++) {   // repeat to get C2 to kick in
            // Copy only latin1 chars from UTF-16 converted prefix (3 chars -> 3 bytes)
            int intResult = (int) mCompressChars.invoke(null, chars, 0, bytes, 0, chars.length);
            if (intResult == 0) {
                if (printWarningCount == 0) {
                    printWarningCount = 1;
                    System.err.println("Intrinsic for StringUTF16.compress returned 0, may not have been updated.");
                }
            } else {
                assertEquals(3, intResult, "return length not-equal, iteration: " + i);
            }
        }

        // Exhaustively check compress returning the correct index of the non-latin1 char.
        final int SIZE = 48;
        final byte FILL_BYTE = 'R';
        chars = new char[SIZE];
        bytes = new byte[chars.length];
        for (int i = 0; i < SIZE; i++) { // Every starting index
            for (int j = i; j < SIZE; j++) {  // Every location of non-latin1
                Arrays.fill(chars, 'A');
                Arrays.fill(bytes, FILL_BYTE);
                chars[j] = 0xFF21;
                int intResult = (int) mCompressChars.invoke(null, chars, i, bytes, 0, chars.length - i);
                assertEquals(j - i, intResult, "compress found wrong index");
                assertEquals(FILL_BYTE, bytes[j], "extra character stored");
            }
        }

    }

    // Check that a concatenated "hello" has a valid coder
    @Test
    @EnabledIf("test.java.lang.String.StringRacyConstructor#isCompactStrings")
    public void checkConcatAndIntern() {
        var helloWorld = "hello world";
        String helloToo = racyStringConstruction("hell".concat("o"));
        String o = helloToo.intern();
        var hello = "hello";
        assertTrue(validCoder(helloToo), "startsWith: "
                + ", hell: " + inspectString(helloToo)
                + ", o: " + inspectString(o)
                + ", hello: " + inspectString(hello)
                + ", hello world: " + inspectString(helloWorld));
    }

    // Check that an empty string with racy construction has a valid coder
    @Test
    @EnabledIf("test.java.lang.String.StringRacyConstructor#isCompactStrings")
    public void racyEmptyString() {
        var space = racyStringConstruction(" ");
        var trimmed = space.trim();
        assertTrue(validCoder(trimmed), "empty string invalid coder"
                + ", trimmed: " + inspectString(trimmed));
    }

    // Check that an exception in a user implemented CharSequence doesn't result in
    // an invalid coder when appended to a StringBuilder
    @Test
    @EnabledIf("test.java.lang.String.StringRacyConstructor#isCompactStrings")
    void charSequenceException() {
        ThrowingCharSequence bs = new ThrowingCharSequence("A\u2030\uFFFD");
        var sb = new StringBuilder();
        try {
            sb.append(bs);
            fail("An IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ignore expected
        }
        assertTrue(validCoder(sb), "invalid coder in StringBuilder");
    }

    /**
     * Given a latin-1 String, attempt to create a copy that is
     * incorrectly encoded as UTF-16.
     */
    public static String racyStringConstruction(String original) throws ConcurrentModificationException {
        if (original.chars().max().getAsInt() >= 256) {
            throw new IllegalArgumentException(
                    "Only work with latin-1 Strings");
        }

        char[] chars = original.toCharArray();

        // In another thread, flip the first character back
        // and forth between being latin-1 or not
        Thread thread = new Thread(() -> {
            while (!Thread.interrupted()) {
                chars[0] ^= 256;
            }
        });
        thread.start();

        // at the same time call the String constructor,
        // until we hit the race condition
        int i = 0;
        while (true) {
            i++;
            String s = new String(chars);
            if ((s.charAt(0) < 256 && !original.equals(s)) || i > 1_000_000) {
                thread.interrupt();
                try {
                    thread.join();
                } catch (InterruptedException ie) {
                    // ignore interrupt
                }
                return s;
            }
        }
    }

    /**
     * Given a latin-1 String, creates a copy that is
     * incorrectly encoded as UTF-16 using the APIs for Codepoints.
     */
    public static String racyStringConstructionCodepoints(String original) throws ConcurrentModificationException {
        if (original.chars().max().getAsInt() >= 256) {
            throw new IllegalArgumentException(
                    "Can only work with latin-1 Strings");
        }

        int len = original.length();
        int[] codePoints = new int[len];
        for (int i = 0; i < len; i++) {
            codePoints[i] = original.charAt(i);
        }

        // In another thread, flip the first character back
        // and forth between being latin-1 or not
        Thread thread = new Thread(() -> {
            while (!Thread.interrupted()) {
                codePoints[0] ^= 256;
            }
        });
        thread.start();

        // at the same time call the String constructor,
        // until we hit the race condition
        int i = 0;
        while (true) {
            i++;
            String s = new String(codePoints, 0, len);
            if ((s.charAt(0) < 256 && !original.equals(s)) || i > 1_000_000) {
                thread.interrupt();
                try {
                    thread.join();
                } catch (InterruptedException ie) {
                    // ignore interrupt
                }
                return s;
            }
        }
    }

    /**
     * Returns a string created from a codepoint array that has been racily
     * modified to contain high and low surrogates. The string is a different length
     * than the original due to the surrogate encoding.
     */
    public static String racyStringConstructionCodepointsSurrogates(String original) throws ConcurrentModificationException {
        if (original.chars().max().getAsInt() >= 256) {
            throw new IllegalArgumentException(
                    "Can only work with latin-1 Strings");
        }

        int len = original.length();
        int[] codePoints = new int[len];
        for (int i = 0; i < len; i++) {
            codePoints[i] = original.charAt(i);
        }

        // In another thread, flip the first character back
        // and forth between being latin-1 or as a surrogate pair.
        Thread thread = new Thread(() -> {
            while (!Thread.interrupted()) {
                codePoints[0] ^= 0x10000;
            }
        });
        thread.start();

        // at the same time call the String constructor,
        // until we hit the race condition
        int i = 0;
        while (true) {
            i++;
            String s = new String(codePoints, 0, len);
            if ((s.length() != original.length()) || i > 1_000_000) {
                thread.interrupt();
                try {
                    thread.join();
                } catch (InterruptedException ie) {
                    // ignore interrupt
                }
                return s;
            }
        }
    }

    // A CharSequence that returns characters from a string and throws IllegalArgumentException
    // when the character requested is 0xFFFD (the replacement character)
    // The string contents determine when the exception is thrown.
    static class ThrowingCharSequence implements CharSequence {
        private final String aString;

        ThrowingCharSequence(String aString) {
            this.aString = aString;
        }

        @Override
        public int length() {
            return aString.length();
        }

        @Override
        public char charAt(int index) {
            char ch = aString.charAt(index);
            if (ch == 0xFFFD) {
                throw new IllegalArgumentException("Replacement character at index " + index);
            }
            return ch;
        }

        @Override
        // Not used; returns the entire string
        public CharSequence subSequence(int start, int end) {
            return this;
        }
    }
}
