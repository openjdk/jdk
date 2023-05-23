// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 1996-2015, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */
package jdk.internal.icu.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Pattern;

import jdk.internal.icu.lang.UCharacter;
import jdk.internal.icu.text.Replaceable;
import jdk.internal.icu.text.UTF16;
import jdk.internal.icu.text.UnicodeMatcher;

public final class Utility {

    private static final char APOSTROPHE = '\'';
    private static final char BACKSLASH  = '\\';
    private static final int MAGIC_UNSIGNED = 0x80000000;

    /**
     * Convenience utility to compare two Object[]s.
     * Ought to be in System
     */
    public final static boolean arrayEquals(Object[] source, Object target) {
        if (source == null) return (target == null);
        if (!(target instanceof Object[])) return false;
        Object[] targ = (Object[]) target;
        return (source.length == targ.length
                && arrayRegionMatches(source, 0, targ, 0, source.length));
    }

    /**
     * Convenience utility to compare two int[]s
     * Ought to be in System
     */
    public final static boolean arrayEquals(int[] source, Object target) {
        if (source == null) return (target == null);
        if (!(target instanceof int[])) return false;
        int[] targ = (int[]) target;
        return (source.length == targ.length
                && arrayRegionMatches(source, 0, targ, 0, source.length));
    }

    /**
     * Convenience utility to compare two double[]s
     * Ought to be in System
     */
    public final static boolean arrayEquals(double[] source, Object target) {
        if (source == null) return (target == null);
        if (!(target instanceof double[])) return false;
        double[] targ = (double[]) target;
        return (source.length == targ.length
                && arrayRegionMatches(source, 0, targ, 0, source.length));
    }
    public final static boolean arrayEquals(byte[] source, Object target) {
        if (source == null) return (target == null);
        if (!(target instanceof byte[])) return false;
        byte[] targ = (byte[]) target;
        return (source.length == targ.length
                && arrayRegionMatches(source, 0, targ, 0, source.length));
    }

    /**
     * Convenience utility to compare two Object[]s
     * Ought to be in System
     */
    public final static boolean arrayEquals(Object source, Object target) {
        if (source == null) return (target == null);
        // for some reason, the correct arrayEquals is not being called
        // so do it by hand for now.
        if (source instanceof Object[])
            return(arrayEquals((Object[]) source,target));
        if (source instanceof int[])
            return(arrayEquals((int[]) source,target));
        if (source instanceof double[])
            return(arrayEquals((double[]) source, target));
        if (source instanceof byte[])
            return(arrayEquals((byte[]) source,target));
        return source.equals(target);
    }

    /**
     * Convenience utility to compare two Object[]s
     * Ought to be in System.
     * @param len the length to compare.
     * The start indices and start+len must be valid.
     */
    public final static boolean arrayRegionMatches(Object[] source, int sourceStart,
            Object[] target, int targetStart,
            int len)
    {
        int sourceEnd = sourceStart + len;
        int delta = targetStart - sourceStart;
        for (int i = sourceStart; i < sourceEnd; i++) {
            if (!arrayEquals(source[i],target[i + delta]))
                return false;
        }
        return true;
    }

    /**
     * Convenience utility to compare two Object[]s
     * Ought to be in System.
     * @param len the length to compare.
     * The start indices and start+len must be valid.
     */
    public final static boolean arrayRegionMatches(char[] source, int sourceStart,
            char[] target, int targetStart,
            int len)
    {
        int sourceEnd = sourceStart + len;
        int delta = targetStart - sourceStart;
        for (int i = sourceStart; i < sourceEnd; i++) {
            if (source[i]!=target[i + delta])
                return false;
        }
        return true;
    }

    /**
     * Convenience utility to compare two int[]s.
     * @param len the length to compare.
     * The start indices and start+len must be valid.
     * Ought to be in System
     */
    public final static boolean arrayRegionMatches(int[] source, int sourceStart,
            int[] target, int targetStart,
            int len)
    {
        int sourceEnd = sourceStart + len;
        int delta = targetStart - sourceStart;
        for (int i = sourceStart; i < sourceEnd; i++) {
            if (source[i] != target[i + delta])
                return false;
        }
        return true;
    }

    /**
     * Convenience utility to compare two arrays of doubles.
     * @param len the length to compare.
     * The start indices and start+len must be valid.
     * Ought to be in System
     */
    public final static boolean arrayRegionMatches(double[] source, int sourceStart,
            double[] target, int targetStart,
            int len)
    {
        int sourceEnd = sourceStart + len;
        int delta = targetStart - sourceStart;
        for (int i = sourceStart; i < sourceEnd; i++) {
            if (source[i] != target[i + delta])
                return false;
        }
        return true;
    }
    public final static boolean arrayRegionMatches(byte[] source, int sourceStart,
            byte[] target, int targetStart, int len){
        int sourceEnd = sourceStart + len;
        int delta = targetStart - sourceStart;
        for (int i = sourceStart; i < sourceEnd; i++) {
            if (source[i] != target[i + delta])
                return false;
        }
        return true;
    }

    /**
     * Trivial reference equality.
     * This method should help document that we really want == not equals(),
     * and to have a single place to suppress warnings from static analysis tools.
     */
    public static final boolean sameObjects(Object a, Object b) {
        return a == b;
    }

    /**
     * Convenience utility. Does null checks on objects, then calls compare.
     */
    public static <T extends Comparable<T>> int checkCompare(T a, T b) {
        return a == null ?
                b == null ? 0 : -1 :
                    b == null ? 1 : a.compareTo(b);
      }

    /**
     * Convenience utility. Does null checks on object, then calls hashCode.
     */
    public static int checkHash(Object a) {
        return a == null ? 0 : a.hashCode();
      }

    /**
     * The ESCAPE character is used during run-length encoding.  It signals
     * a run of identical chars.
     */
    private static final char ESCAPE = '\uA5A5';

    /**
     * The ESCAPE_BYTE character is used during run-length encoding.  It signals
     * a run of identical bytes.
     */
    static final byte ESCAPE_BYTE = (byte)0xA5;

    /**
     * Construct a string representing an int array.  Use run-length encoding.
     * A character represents itself, unless it is the ESCAPE character.  Then
     * the following notations are possible:
     *   ESCAPE ESCAPE   ESCAPE literal
     *   ESCAPE n c      n instances of character c
     * Since an encoded run occupies 3 characters, we only encode runs of 4 or
     * more characters.  Thus we have n > 0 and n != ESCAPE and n <= 0xFFFF.
     * If we encounter a run where n == ESCAPE, we represent this as:
     *   c ESCAPE n-1 c
     * The ESCAPE value is chosen so as not to collide with commonly
     * seen values.
     */
    static public final String arrayToRLEString(int[] a) {
        StringBuilder buffer = new StringBuilder();

        appendInt(buffer, a.length);
        int runValue = a[0];
        int runLength = 1;
        for (int i=1; i<a.length; ++i) {
            int s = a[i];
            if (s == runValue && runLength < 0xFFFF) {
                ++runLength;
            } else {
                encodeRun(buffer, runValue, runLength);
                runValue = s;
                runLength = 1;
            }
        }
        encodeRun(buffer, runValue, runLength);
        return buffer.toString();
    }

    /**
     * Construct a string representing a short array.  Use run-length encoding.
     * A character represents itself, unless it is the ESCAPE character.  Then
     * the following notations are possible:
     *   ESCAPE ESCAPE   ESCAPE literal
     *   ESCAPE n c      n instances of character c
     * Since an encoded run occupies 3 characters, we only encode runs of 4 or
     * more characters.  Thus we have n > 0 and n != ESCAPE and n <= 0xFFFF.
     * If we encounter a run where n == ESCAPE, we represent this as:
     *   c ESCAPE n-1 c
     * The ESCAPE value is chosen so as not to collide with commonly
     * seen values.
     */
    static public final String arrayToRLEString(short[] a) {
        StringBuilder buffer = new StringBuilder();
        // for (int i=0; i<a.length; ++i) buffer.append((char) a[i]);
        buffer.append((char) (a.length >> 16));
        buffer.append((char) a.length);
        short runValue = a[0];
        int runLength = 1;
        for (int i=1; i<a.length; ++i) {
            short s = a[i];
            if (s == runValue && runLength < 0xFFFF) ++runLength;
            else {
                encodeRun(buffer, runValue, runLength);
                runValue = s;
                runLength = 1;
            }
        }
        encodeRun(buffer, runValue, runLength);
        return buffer.toString();
    }

    /**
     * Construct a string representing a char array.  Use run-length encoding.
     * A character represents itself, unless it is the ESCAPE character.  Then
     * the following notations are possible:
     *   ESCAPE ESCAPE   ESCAPE literal
     *   ESCAPE n c      n instances of character c
     * Since an encoded run occupies 3 characters, we only encode runs of 4 or
     * more characters.  Thus we have n > 0 and n != ESCAPE and n <= 0xFFFF.
     * If we encounter a run where n == ESCAPE, we represent this as:
     *   c ESCAPE n-1 c
     * The ESCAPE value is chosen so as not to collide with commonly
     * seen values.
     */
    static public final String arrayToRLEString(char[] a) {
        StringBuilder buffer = new StringBuilder();
        buffer.append((char) (a.length >> 16));
        buffer.append((char) a.length);
        char runValue = a[0];
        int runLength = 1;
        for (int i=1; i<a.length; ++i) {
            char s = a[i];
            if (s == runValue && runLength < 0xFFFF) ++runLength;
            else {
                encodeRun(buffer, (short)runValue, runLength);
                runValue = s;
                runLength = 1;
            }
        }
        encodeRun(buffer, (short)runValue, runLength);
        return buffer.toString();
    }

    /**
     * Construct a string representing a byte array.  Use run-length encoding.
     * Two bytes are packed into a single char, with a single extra zero byte at
     * the end if needed.  A byte represents itself, unless it is the
     * ESCAPE_BYTE.  Then the following notations are possible:
     *   ESCAPE_BYTE ESCAPE_BYTE   ESCAPE_BYTE literal
     *   ESCAPE_BYTE n b           n instances of byte b
     * Since an encoded run occupies 3 bytes, we only encode runs of 4 or
     * more bytes.  Thus we have n > 0 and n != ESCAPE_BYTE and n <= 0xFF.
     * If we encounter a run where n == ESCAPE_BYTE, we represent this as:
     *   b ESCAPE_BYTE n-1 b
     * The ESCAPE_BYTE value is chosen so as not to collide with commonly
     * seen values.
     */
    static public final String arrayToRLEString(byte[] a) {
        StringBuilder buffer = new StringBuilder();
        buffer.append((char) (a.length >> 16));
        buffer.append((char) a.length);
        byte runValue = a[0];
        int runLength = 1;
        byte[] state = new byte[2];
        for (int i=1; i<a.length; ++i) {
            byte b = a[i];
            if (b == runValue && runLength < 0xFF) ++runLength;
            else {
                encodeRun(buffer, runValue, runLength, state);
                runValue = b;
                runLength = 1;
            }
        }
        encodeRun(buffer, runValue, runLength, state);

        // We must save the final byte, if there is one, by padding
        // an extra zero.
        if (state[0] != 0) appendEncodedByte(buffer, (byte)0, state);

        return buffer.toString();
    }

    /**
     * Encode a run, possibly a degenerate run (of < 4 values).
     * @param length The length of the run; must be > 0 && <= 0xFFFF.
     */
    private static final <T extends Appendable> void encodeRun(T buffer, int value, int length) {
        if (length < 4) {
            for (int j=0; j<length; ++j) {
                if (value == ESCAPE) {
                    appendInt(buffer, value);
                }
                appendInt(buffer, value);
            }
        }
        else {
            if (length == ESCAPE) {
                if (value == ESCAPE) {
                    appendInt(buffer, ESCAPE);
                }
                appendInt(buffer, value);
                --length;
            }
            appendInt(buffer, ESCAPE);
            appendInt(buffer, length);
            appendInt(buffer, value); // Don't need to escape this value
        }
    }

    private static final <T extends Appendable> void appendInt(T buffer, int value) {
        try {
            buffer.append((char)(value >>> 16));
            buffer.append((char)(value & 0xFFFF));
        } catch (IOException e) {
            throw new IllegalIcuArgumentException(e);
        }
    }

    /**
     * Encode a run, possibly a degenerate run (of < 4 values).
     * @param length The length of the run; must be > 0 && <= 0xFFFF.
     */
    private static final <T extends Appendable> void encodeRun(T buffer, short value, int length) {
        try {
            char valueChar = (char) value;
            if (length < 4) {
                for (int j=0; j<length; ++j) {
                    if (valueChar == ESCAPE) {
                        buffer.append(ESCAPE);
                    }
                    buffer.append(valueChar);
                }
            }
            else {
                if (length == ESCAPE) {
                    if (valueChar == ESCAPE) {
                        buffer.append(ESCAPE);
                    }
                    buffer.append(valueChar);
                    --length;
                }
                buffer.append(ESCAPE);
                buffer.append((char) length);
                buffer.append(valueChar); // Don't need to escape this value
            }
        } catch (IOException e) {
            throw new IllegalIcuArgumentException(e);
        }
    }

    /**
     * Encode a run, possibly a degenerate run (of < 4 values).
     * @param length The length of the run; must be > 0 && <= 0xFF.
     */
    private static final <T extends Appendable> void encodeRun(T buffer, byte value, int length,
            byte[] state) {
        if (length < 4) {
            for (int j=0; j<length; ++j) {
                if (value == ESCAPE_BYTE) appendEncodedByte(buffer, ESCAPE_BYTE, state);
                appendEncodedByte(buffer, value, state);
            }
        }
        else {
            if ((byte)length == ESCAPE_BYTE) {
                if (value == ESCAPE_BYTE) appendEncodedByte(buffer, ESCAPE_BYTE, state);
                appendEncodedByte(buffer, value, state);
                --length;
            }
            appendEncodedByte(buffer, ESCAPE_BYTE, state);
            appendEncodedByte(buffer, (byte)length, state);
            appendEncodedByte(buffer, value, state); // Don't need to escape this value
        }
    }

    /**
     * Append a byte to the given Appendable, packing two bytes into each
     * character.  The state parameter maintains intermediary data between
     * calls.
     * @param state A two-element array, with state[0] == 0 if this is the
     * first byte of a pair, or state[0] != 0 if this is the second byte
     * of a pair, in which case state[1] is the first byte.
     */
    private static final <T extends Appendable> void appendEncodedByte(T buffer, byte value,
            byte[] state) {
        try {
            if (state[0] != 0) {
                char c = (char) ((state[1] << 8) | ((value) & 0xFF));
                buffer.append(c);
                state[0] = 0;
            }
            else {
                state[0] = 1;
                state[1] = value;
            }
        } catch (IOException e) {
            throw new IllegalIcuArgumentException(e);
        }
    }

    /**
     * Construct an array of ints from a run-length encoded string.
     */
    static public final int[] RLEStringToIntArray(String s) {
        int length = getInt(s, 0);
        int[] array = new int[length];
        int ai = 0, i = 1;

        int maxI = s.length() / 2;
        while (ai < length && i < maxI) {
            int c = getInt(s, i++);

            if (c == ESCAPE) {
                c = getInt(s, i++);
                if (c == ESCAPE) {
                    array[ai++] = c;
                } else {
                    int runLength = c;
                    int runValue = getInt(s, i++);
                    for (int j=0; j<runLength; ++j) {
                        array[ai++] = runValue;
                    }
                }
            }
            else {
                array[ai++] = c;
            }
        }

        if (ai != length || i != maxI) {
            throw new IllegalStateException("Bad run-length encoded int array");
        }

        return array;
    }
    static final int getInt(String s, int i) {
        return ((s.charAt(2*i)) << 16) | s.charAt(2*i+1);
    }

    /**
     * Construct an array of shorts from a run-length encoded string.
     */
    static public final short[] RLEStringToShortArray(String s) {
        int length = ((s.charAt(0)) << 16) | (s.charAt(1));
        short[] array = new short[length];
        int ai = 0;
        for (int i=2; i<s.length(); ++i) {
            char c = s.charAt(i);
            if (c == ESCAPE) {
                c = s.charAt(++i);
                if (c == ESCAPE) {
                    array[ai++] = (short) c;
                } else {
                    int runLength = c;
                    short runValue = (short) s.charAt(++i);
                    for (int j=0; j<runLength; ++j) array[ai++] = runValue;
                }
            }
            else {
                array[ai++] = (short) c;
            }
        }

        if (ai != length)
            throw new IllegalStateException("Bad run-length encoded short array");

        return array;
    }

    /**
     * Construct an array of shorts from a run-length encoded string.
     */
    static public final char[] RLEStringToCharArray(String s) {
        int length = ((s.charAt(0)) << 16) | (s.charAt(1));
        char[] array = new char[length];
        int ai = 0;
        for (int i=2; i<s.length(); ++i) {
            char c = s.charAt(i);
            if (c == ESCAPE) {
                c = s.charAt(++i);
                if (c == ESCAPE) {
                    array[ai++] = c;
                } else {
                    int runLength = c;
                    char runValue = s.charAt(++i);
                    for (int j=0; j<runLength; ++j) array[ai++] = runValue;
                }
            }
            else {
                array[ai++] = c;
            }
        }

        if (ai != length)
            throw new IllegalStateException("Bad run-length encoded short array");

        return array;
    }

    /**
     * Construct an array of bytes from a run-length encoded string.
     */
    static public final byte[] RLEStringToByteArray(String s) {
        int length = ((s.charAt(0)) << 16) | (s.charAt(1));
        byte[] array = new byte[length];
        boolean nextChar = true;
        char c = 0;
        int node = 0;
        int runLength = 0;
        int i = 2;
        for (int ai=0; ai<length; ) {
            // This part of the loop places the next byte into the local
            // variable 'b' each time through the loop.  It keeps the
            // current character in 'c' and uses the boolean 'nextChar'
            // to see if we've taken both bytes out of 'c' yet.
            byte b;
            if (nextChar) {
                c = s.charAt(i++);
                b = (byte) (c >> 8);
                nextChar = false;
            }
            else {
                b = (byte) (c & 0xFF);
                nextChar = true;
            }

            // This part of the loop is a tiny state machine which handles
            // the parsing of the run-length encoding.  This would be simpler
            // if we could look ahead, but we can't, so we use 'node' to
            // move between three nodes in the state machine.
            switch (node) {
            case 0:
                // Normal idle node
                if (b == ESCAPE_BYTE) {
                    node = 1;
                }
                else {
                    array[ai++] = b;
                }
                break;
            case 1:
                // We have seen one ESCAPE_BYTE; we expect either a second
                // one, or a run length and value.
                if (b == ESCAPE_BYTE) {
                    array[ai++] = ESCAPE_BYTE;
                    node = 0;
                }
                else {
                    runLength = b;
                    // Interpret signed byte as unsigned
                    if (runLength < 0) runLength += 0x100;
                    node = 2;
                }
                break;
            case 2:
                // We have seen an ESCAPE_BYTE and length byte.  We interpret
                // the next byte as the value to be repeated.
                for (int j=0; j<runLength; ++j) array[ai++] = b;
                node = 0;
                break;
            }
        }

        if (node != 0)
            throw new IllegalStateException("Bad run-length encoded byte array");

        if (i != s.length())
            throw new IllegalStateException("Excess data in RLE byte array string");

        return array;
    }

    static public String LINE_SEPARATOR = System.getProperty("line.separator");

    /**
     * Format a String for representation in a source file.  This includes
     * breaking it into lines and escaping characters using octal notation
     * when necessary (control characters and double quotes).
     */
    static public final String formatForSource(String s) {
        StringBuilder buffer = new StringBuilder();
        for (int i=0; i<s.length();) {
            if (i > 0) buffer.append('+').append(LINE_SEPARATOR);
            buffer.append("        \"");
            int count = 11;
            while (i<s.length() && count<80) {
                char c = s.charAt(i++);
                if (c < '\u0020' || c == '"' || c == '\\') {
                    if (c == '\n') {
                        buffer.append("\\n");
                        count += 2;
                    } else if (c == '\t') {
                        buffer.append("\\t");
                        count += 2;
                    } else if (c == '\r') {
                        buffer.append("\\r");
                        count += 2;
                    } else {
                        // Represent control characters, backslash and double quote
                        // using octal notation; otherwise the string we form
                        // won't compile, since Unicode escape sequences are
                        // processed before tokenization.
                        buffer.append('\\');
                        buffer.append(HEX_DIGIT[(c & 0700) >> 6]); // HEX_DIGIT works for octal
                        buffer.append(HEX_DIGIT[(c & 0070) >> 3]);
                        buffer.append(HEX_DIGIT[(c & 0007)]);
                        count += 4;
                    }
                }
                else if (c <= '\u007E') {
                    buffer.append(c);
                    count += 1;
                }
                else {
                    buffer.append("\\u");
                    buffer.append(HEX_DIGIT[(c & 0xF000) >> 12]);
                    buffer.append(HEX_DIGIT[(c & 0x0F00) >> 8]);
                    buffer.append(HEX_DIGIT[(c & 0x00F0) >> 4]);
                    buffer.append(HEX_DIGIT[(c & 0x000F)]);
                    count += 6;
                }
            }
            buffer.append('"');
        }
        return buffer.toString();
    }

    static final char[] HEX_DIGIT = {'0','1','2','3','4','5','6','7',
        '8','9','A','B','C','D','E','F'};

    /**
     * Format a String for representation in a source file.  Like
     * formatForSource but does not do line breaking.
     */
    static public final String format1ForSource(String s) {
        StringBuilder buffer = new StringBuilder();
        buffer.append("\"");
        for (int i=0; i<s.length();) {
            char c = s.charAt(i++);
            if (c < '\u0020' || c == '"' || c == '\\') {
                if (c == '\n') {
                    buffer.append("\\n");
                } else if (c == '\t') {
                    buffer.append("\\t");
                } else if (c == '\r') {
                    buffer.append("\\r");
                } else {
                    // Represent control characters, backslash and double quote
                    // using octal notation; otherwise the string we form
                    // won't compile, since Unicode escape sequences are
                    // processed before tokenization.
                    buffer.append('\\');
                    buffer.append(HEX_DIGIT[(c & 0700) >> 6]); // HEX_DIGIT works for octal
                    buffer.append(HEX_DIGIT[(c & 0070) >> 3]);
                    buffer.append(HEX_DIGIT[(c & 0007)]);
                }
            }
            else if (c <= '\u007E') {
                buffer.append(c);
            }
            else {
                buffer.append("\\u");
                buffer.append(HEX_DIGIT[(c & 0xF000) >> 12]);
                buffer.append(HEX_DIGIT[(c & 0x0F00) >> 8]);
                buffer.append(HEX_DIGIT[(c & 0x00F0) >> 4]);
                buffer.append(HEX_DIGIT[(c & 0x000F)]);
            }
        }
        buffer.append('"');
        return buffer.toString();
    }

    /**
     * Convert characters outside the range U+0020 to U+007F to
     * Unicode escapes, and convert backslash to a double backslash.
     */
    public static final String escape(String s) {
        StringBuilder buf = new StringBuilder();
        for (int i=0; i<s.length(); ) {
            int c = Character.codePointAt(s, i);
            i += UTF16.getCharCount(c);
            if (c >= ' ' && c <= 0x007F) {
                if (c == '\\') {
                    buf.append("\\\\"); // That is, "\\"
                } else {
                    buf.append((char)c);
                }
            } else {
                boolean four = c <= 0xFFFF;
                buf.append(four ? "\\u" : "\\U");
                buf.append(hex(c, four ? 4 : 8));
            }
        }
        return buf.toString();
    }

    /* This map must be in ASCENDING ORDER OF THE ESCAPE CODE */
    static private final char[] UNESCAPE_MAP = {
        /*"   0x22, 0x22 */
        /*'   0x27, 0x27 */
        /*?   0x3F, 0x3F */
        /*\   0x5C, 0x5C */
        /*a*/ 0x61, 0x07,
        /*b*/ 0x62, 0x08,
        /*e*/ 0x65, 0x1b,
        /*f*/ 0x66, 0x0c,
        /*n*/ 0x6E, 0x0a,
        /*r*/ 0x72, 0x0d,
        /*t*/ 0x74, 0x09,
        /*v*/ 0x76, 0x0b
    };

    /* Convert one octal digit to a numeric value 0..7, or -1 on failure */
    private static final int _digit8(int c) {
        if (c >= '0' && c <= '7') {
            return c - '0';
        }
        return -1;
    }

    /* Convert one hex digit to a numeric value 0..F, or -1 on failure */
    private static final int _digit16(int c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'F') {
            return c - ('A' - 10);
        }
        if (c >= 'a' && c <= 'f') {
            return c - ('a' - 10);
        }
        return -1;
    }

    /**
     * Converts an escape to a code point value. We attempt
     * to parallel the icu4c unescapeAt() function.
     * This function returns an integer with
     * both the code point (bits 28..8) and the length of the escape sequence (bits 7..0).
     * offset+length is the index after the escape sequence.
     *
     * @param offset the offset to the character <em>after</em> the backslash.
     * @return the code point and length, or -1 on error.
     */
    public static int unescapeAndLengthAt(CharSequence s, int offset) {
        return unescapeAndLengthAt(s, offset, s.length());
    }

    private static int unescapeAndLengthAt(CharSequence s, int offset, int length) {
        int result = 0;
        int n = 0;
        int minDig = 0;
        int maxDig = 0;
        int bitsPerDigit = 4;
        int dig;
        boolean braces = false;

        /* Check that offset is in range */
        if (offset < 0 || offset >= length) {
            return -1;
        }
        int start = offset;

        /* Fetch first UChar after '\\' */
        int c = s.charAt(offset++);

        /* Convert hexadecimal and octal escapes */
        switch (c) {
        case 'u':
            minDig = maxDig = 4;
            break;
        case 'U':
            minDig = maxDig = 8;
            break;
        case 'x':
            minDig = 1;
            if (offset < length && s.charAt(offset) == '{') {
                ++offset;
                braces = true;
                maxDig = 8;
            } else {
                maxDig = 2;
            }
            break;
        default:
            dig = _digit8(c);
            if (dig >= 0) {
                minDig = 1;
                maxDig = 3;
                n = 1; /* Already have first octal digit */
                bitsPerDigit = 3;
                result = dig;
            }
            break;
        }
        if (minDig != 0) {
            while (offset < length && n < maxDig) {
                c = s.charAt(offset);
                dig = (bitsPerDigit == 3) ? _digit8(c) : _digit16(c);
                if (dig < 0) {
                    break;
                }
                result = (result << bitsPerDigit) | dig;
                ++offset;
                ++n;
            }
            if (n < minDig) {
                return -1;
            }
            if (braces) {
                if (c != '}') {
                    return -1;
                }
                ++offset;
            }
            if (result < 0 || result >= 0x110000) {
                return -1;
            }
            // If an escape sequence specifies a lead surrogate, see
            // if there is a trail surrogate after it, either as an
            // escape or as a literal.  If so, join them up into a
            // supplementary.
            if (offset < length && UTF16.isLeadSurrogate(result)) {
                int ahead = offset+1;
                c = s.charAt(offset);
                if (c == '\\' && ahead < length) {
                    // Calling ourselves recursively may cause a stack overflow if
                    // we have repeated escaped lead surrogates.
                    // Limit the length to 11 ("x{0000DFFF}") after ahead.
                    int tailLimit = ahead + 11;
                    if (tailLimit > length) {
                        tailLimit = length;
                    }
                    int cpAndLength = unescapeAndLengthAt(s, ahead, tailLimit);
                    if (cpAndLength >= 0) {
                        c = cpAndLength >> 8;
                        ahead += cpAndLength & 0xff;
                    }
                }
                if (UTF16.isTrailSurrogate(c)) {
                    offset = ahead;
                    result = UCharacter.toCodePoint(result, c);
                }
            }
            return codePointAndLength(result, start, offset);
        }

        /* Convert C-style escapes in table */
        for (int i=0; i<UNESCAPE_MAP.length; i+=2) {
            if (c == UNESCAPE_MAP[i]) {
                return codePointAndLength(UNESCAPE_MAP[i+1], start, offset);
            } else if (c < UNESCAPE_MAP[i]) {
                break;
            }
        }

        /* Map \cX to control-X: X & 0x1F */
        if (c == 'c' && offset < length) {
            c = Character.codePointAt(s, offset);
            return codePointAndLength(c & 0x1F, start, offset + Character.charCount(c));
        }

        /* If no special forms are recognized, then consider
         * the backslash to generically escape the next character.
         * Deal with surrogate pairs. */
        if (UTF16.isLeadSurrogate(c) && offset < length) {
            int c2 = s.charAt(offset);
            if (UTF16.isTrailSurrogate(c2)) {
                ++offset;
                c = UCharacter.toCodePoint(c, c2);
            }
        }
        return codePointAndLength(c, start, offset);
    }

    private static int codePointAndLength(int c, int length) {
        assert 0 <= c && c <= 0x10ffff;
        assert 0 <= length && length <= 0xff;
        return c << 8 | length;
    }

    private static int codePointAndLength(int c, int start, int limit) {
        return codePointAndLength(c, limit - start);
    }

    public static int cpFromCodePointAndLength(int cpAndLength) {
        assert cpAndLength >= 0;
        return cpAndLength >> 8;
    }

    public static int lengthFromCodePointAndLength(int cpAndLength) {
        assert cpAndLength >= 0;
        return cpAndLength & 0xff;
    }

    /**
     * Convert all escapes in a given string using unescapeAndLengthAt().
     * @exception IllegalArgumentException if an invalid escape is
     * seen.
     */
    public static String unescape(CharSequence s) {
        StringBuilder buf = null;
        for (int i=0; i<s.length(); ) {
            char c = s.charAt(i++);
            if (c == '\\') {
                if (buf == null) {
                    buf = new StringBuilder(s.length()).append(s, 0, i - 1);
                }
                int cpAndLength = unescapeAndLengthAt(s, i);
                if (cpAndLength < 0) {
                    throw new IllegalArgumentException("Invalid escape sequence " +
                            s.subSequence(i-1, Math.min(i+9, s.length())));
                }
                buf.appendCodePoint(cpAndLength >> 8);
                i += cpAndLength & 0xff;
            } else if (buf != null) {
                // We could optimize this further by appending whole substrings between escapes.
                buf.append(c);
            }
        }
        if (buf == null) {
            // No escapes in s.
            return s.toString();
        }
        return buf.toString();
    }

    /**
     * Convert all escapes in a given string using unescapeAndLengthAt().
     * Leave invalid escape sequences unchanged.
     */
    public static String unescapeLeniently(CharSequence s) {
        StringBuilder buf = null;
        for (int i=0; i<s.length(); ) {
            char c = s.charAt(i++);
            if (c == '\\') {
                if (buf == null) {
                    buf = new StringBuilder(s.length()).append(s, 0, i - 1);
                }
                int cpAndLength = unescapeAndLengthAt(s, i);
                if (cpAndLength < 0) {
                    buf.append(c);
                } else {
                    buf.appendCodePoint(cpAndLength >> 8);
                    i += cpAndLength & 0xff;
                }
            } else if (buf != null) {
                // We could optimize this further by appending whole substrings between escapes.
                buf.append(c);
            }
        }
        if (buf == null) {
            // No escapes in s.
            return s.toString();
        }
        return buf.toString();
    }

    /**
     * Convert a char to 4 hex uppercase digits.  E.g., hex('a') =>
     * "0041".
     */
    public static String hex(long ch) {
        return hex(ch, 4);
    }

    /**
     * Supplies a zero-padded hex representation of an integer (without 0x)
     */
    static public String hex(long i, int places) {
        if (i == Long.MIN_VALUE) return "-8000000000000000";
        boolean negative = i < 0;
        if (negative) {
            i = -i;
        }
        String result = Long.toString(i, 16).toUpperCase(Locale.ENGLISH);
        if (result.length() < places) {
            result = "0000000000000000".substring(result.length(),places) + result;
        }
        if (negative) {
            return '-' + result;
        }
        return result;
    }

    /**
     * Convert a string to comma-separated groups of 4 hex uppercase
     * digits.  E.g., hex('ab') => "0041,0042".
     */
    public static String hex(CharSequence s) {
        return hex(s, 4, ",", true, new StringBuilder()).toString();
    }

    /**
     * Convert a string to separated groups of hex uppercase
     * digits.  E.g., hex('ab'...) => "0041,0042".  Append the output
     * to the given Appendable.
     */
    public static <S extends CharSequence, U extends CharSequence, T extends Appendable> T hex(S s, int width, U separator, boolean useCodePoints, T result) {
        try {
            if (useCodePoints) {
                int cp;
                for (int i = 0; i < s.length(); i += UTF16.getCharCount(cp)) {
                    cp = Character.codePointAt(s, i);
                    if (i != 0) {
                        result.append(separator);
                    }
                    result.append(hex(cp,width));
                }
            } else {
                for (int i = 0; i < s.length(); ++i) {
                    if (i != 0) {
                        result.append(separator);
                    }
                    result.append(hex(s.charAt(i),width));
                }
            }
            return result;
        } catch (IOException e) {
            throw new IllegalIcuArgumentException(e);
        }
    }

    public static String hex(byte[] o, int start, int end, String separator) {
        StringBuilder result = new StringBuilder();
        //int ch;
        for (int i = start; i < end; ++i) {
          if (i != 0) result.append(separator);
          result.append(hex(o[i]));
        }
        return result.toString();
      }

    /**
     * Convert a string to comma-separated groups of 4 hex uppercase
     * digits.  E.g., hex('ab') => "0041,0042".
     */
    public static <S extends CharSequence> String hex(S s, int width, S separator) {
        return hex(s, width, separator, true, new StringBuilder()).toString();
    }

    /**
     * Split a string into pieces based on the given divider character
     * @param s the string to split
     * @param divider the character on which to split.  Occurrences of
     * this character are not included in the output
     * @param output an array to receive the substrings between
     * instances of divider.  It must be large enough on entry to
     * accommodate all output.  Adjacent instances of the divider
     * character will place empty strings into output.  Before
     * returning, output is padded out with empty strings.
     */
    public static void split(String s, char divider, String[] output) {
        int last = 0;
        int current = 0;
        int i;
        for (i = 0; i < s.length(); ++i) {
            if (s.charAt(i) == divider) {
                output[current++] = s.substring(last,i);
                last = i+1;
            }
        }
        output[current++] = s.substring(last,i);
        while (current < output.length) {
            output[current++] = "";
        }
    }

    /**
     * Split a string into pieces based on the given divider character
     * @param s the string to split
     * @param divider the character on which to split.  Occurrences of
     * this character are not included in the output
     * @return output an array to receive the substrings between
     * instances of divider. Adjacent instances of the divider
     * character will place empty strings into output.
     */
    public static String[] split(String s, char divider) {
        int last = 0;
        int i;
        ArrayList<String> output = new ArrayList<>();
        for (i = 0; i < s.length(); ++i) {
            if (s.charAt(i) == divider) {
                output.add(s.substring(last,i));
                last = i+1;
            }
        }
        output.add( s.substring(last,i));
        return output.toArray(new String[output.size()]);
    }

    /**
     * Look up a given string in a string array.  Returns the index at
     * which the first occurrence of the string was found in the
     * array, or -1 if it was not found.
     * @param source the string to search for
     * @param target the array of zero or more strings in which to
     * look for source
     * @return the index of target at which source first occurs, or -1
     * if not found
     */
    public static int lookup(String source, String[] target) {
        for (int i = 0; i < target.length; ++i) {
            if (source.equals(target[i])) return i;
        }
        return -1;
    }

    /**
     * Parse a single non-whitespace character 'ch', optionally
     * preceded by whitespace.
     * @param id the string to be parsed
     * @param pos INPUT-OUTPUT parameter.  On input, pos[0] is the
     * offset of the first character to be parsed.  On output, pos[0]
     * is the index after the last parsed character.  If the parse
     * fails, pos[0] will be unchanged.
     * @param ch the non-whitespace character to be parsed.
     * @return true if 'ch' is seen preceded by zero or more
     * whitespace characters.
     */
    public static boolean parseChar(String id, int[] pos, char ch) {
        int start = pos[0];
        pos[0] = PatternProps.skipWhiteSpace(id, pos[0]);
        if (pos[0] == id.length() ||
                id.charAt(pos[0]) != ch) {
            pos[0] = start;
            return false;
        }
        ++pos[0];
        return true;
    }

    /**
     * Parse a pattern string starting at offset pos.  Keywords are
     * matched case-insensitively.  Spaces may be skipped and may be
     * optional or required.  Integer values may be parsed, and if
     * they are, they will be returned in the given array.  If
     * successful, the offset of the next non-space character is
     * returned.  On failure, -1 is returned.
     * @param pattern must only contain lowercase characters, which
     * will match their uppercase equivalents as well.  A space
     * character matches one or more required spaces.  A '~' character
     * matches zero or more optional spaces.  A '#' character matches
     * an integer and stores it in parsedInts, which the caller must
     * ensure has enough capacity.
     * @param parsedInts array to receive parsed integers.  Caller
     * must ensure that parsedInts.length is >= the number of '#'
     * signs in 'pattern'.
     * @return the position after the last character parsed, or -1 if
     * the parse failed
     */
    @SuppressWarnings("fallthrough")
    public static int parsePattern(String rule, int pos, int limit,
            String pattern, int[] parsedInts) {
        // TODO Update this to handle surrogates
        int[] p = new int[1];
        int intCount = 0; // number of integers parsed
        for (int i=0; i<pattern.length(); ++i) {
            char cpat = pattern.charAt(i);
            char c;
            switch (cpat) {
            case ' ':
                if (pos >= limit) {
                    return -1;
                }
                c = rule.charAt(pos++);
                if (!PatternProps.isWhiteSpace(c)) {
                    return -1;
                }
                // FALL THROUGH to skipWhitespace
            case '~':
                pos = PatternProps.skipWhiteSpace(rule, pos);
                break;
            case '#':
                p[0] = pos;
                parsedInts[intCount++] = parseInteger(rule, p, limit);
                if (p[0] == pos) {
                    // Syntax error; failed to parse integer
                    return -1;
                }
                pos = p[0];
                break;
            default:
                if (pos >= limit) {
                    return -1;
                }
                c = (char) UCharacter.toLowerCase(rule.charAt(pos++));
                if (c != cpat) {
                    return -1;
                }
                break;
            }
        }
        return pos;
    }

    /**
     * Parse a pattern string within the given Replaceable and a parsing
     * pattern.  Characters are matched literally and case-sensitively
     * except for the following special characters:
     *
     * ~  zero or more Pattern_White_Space chars
     *
     * If end of pattern is reached with all matches along the way,
     * pos is advanced to the first unparsed index and returned.
     * Otherwise -1 is returned.
     * @param pat pattern that controls parsing
     * @param text text to be parsed, starting at index
     * @param index offset to first character to parse
     * @param limit offset after last character to parse
     * @return index after last parsed character, or -1 on parse failure.
     */
    public static int parsePattern(String pat,
            Replaceable text,
            int index,
            int limit) {
        int ipat = 0;

        // empty pattern matches immediately
        if (ipat == pat.length()) {
            return index;
        }

        int cpat = Character.codePointAt(pat, ipat);

        while (index < limit) {
            int c = text.char32At(index);

            // parse \s*
            if (cpat == '~') {
                if (PatternProps.isWhiteSpace(c)) {
                    index += UTF16.getCharCount(c);
                    continue;
                } else {
                    if (++ipat == pat.length()) {
                        return index; // success; c unparsed
                    }
                    // fall thru; process c again with next cpat
                }
            }

            // parse literal
            else if (c == cpat) {
                int n = UTF16.getCharCount(c);
                index += n;
                ipat += n;
                if (ipat == pat.length()) {
                    return index; // success; c parsed
                }
                // fall thru; get next cpat
            }

            // match failure of literal
            else {
                return -1;
            }

            cpat = UTF16.charAt(pat, ipat);
        }

        return -1; // text ended before end of pat
    }

    /**
     * Parse an integer at pos, either of the form \d+ or of the form
     * 0x[0-9A-Fa-f]+ or 0[0-7]+, that is, in standard decimal, hex,
     * or octal format.
     * @param pos INPUT-OUTPUT parameter.  On input, the first
     * character to parse.  On output, the character after the last
     * parsed character.
     */
    public static int parseInteger(String rule, int[] pos, int limit) {
        int count = 0;
        int value = 0;
        int p = pos[0];
        int radix = 10;

        if (rule.regionMatches(true, p, "0x", 0, 2)) {
            p += 2;
            radix = 16;
        } else if (p < limit && rule.charAt(p) == '0') {
            p++;
            count = 1;
            radix = 8;
        }

        while (p < limit) {
            int d = UCharacter.digit(rule.charAt(p++), radix);
            if (d < 0) {
                --p;
                break;
            }
            ++count;
            int v = (value * radix) + d;
            if (v <= value) {
                // If there are too many input digits, at some point
                // the value will go negative, e.g., if we have seen
                // "0x8000000" already and there is another '0', when
                // we parse the next 0 the value will go negative.
                return 0;
            }
            value = v;
        }
        if (count > 0) {
            pos[0] = p;
        }
        return value;
    }

    /**
     * Parse a Unicode identifier from the given string at the given
     * position.  Return the identifier, or null if there is no
     * identifier.
     * @param str the string to parse
     * @param pos INPUT-OUTPUT parameter.  On INPUT, pos[0] is the
     * first character to examine.  It must be less than str.length(),
     * and it must not point to a whitespace character.  That is, must
     * have pos[0] < str.length().  On
     * OUTPUT, the position after the last parsed character.
     * @return the Unicode identifier, or null if there is no valid
     * identifier at pos[0].
     */
    public static String parseUnicodeIdentifier(String str, int[] pos) {
        // assert(pos[0] < str.length());
        StringBuilder buf = new StringBuilder();
        int p = pos[0];
        while (p < str.length()) {
            int ch = Character.codePointAt(str, p);
            if (buf.length() == 0) {
                if (UCharacter.isUnicodeIdentifierStart(ch)) {
                    buf.appendCodePoint(ch);
                } else {
                    return null;
                }
            } else {
                if (UCharacter.isUnicodeIdentifierPart(ch)) {
                    buf.appendCodePoint(ch);
                } else {
                    break;
                }
            }
            p += UTF16.getCharCount(ch);
        }
        pos[0] = p;
        return buf.toString();
    }

    static final char DIGITS[] = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
        'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
        'U', 'V', 'W', 'X', 'Y', 'Z'
    };

    /**
     * Append the digits of a positive integer to the given
     * <code>Appendable</code> in the given radix. This is
     * done recursively since it is easiest to generate the low-
     * order digit first, but it must be appended last.
     *
     * @param result is the <code>Appendable</code> to append to
     * @param n is the positive integer
     * @param radix is the radix, from 2 to 36 inclusive
     * @param minDigits is the minimum number of digits to append.
     */
    private static <T extends Appendable> void recursiveAppendNumber(T result, int n,
            int radix, int minDigits)
    {
        try {
            int digit = n % radix;

            if (n >= radix || minDigits > 1) {
                recursiveAppendNumber(result, n / radix, radix, minDigits - 1);
            }
            result.append(DIGITS[digit]);
        } catch (IOException e) {
            throw new IllegalIcuArgumentException(e);
        }
    }

    /**
     * Append a number to the given Appendable in the given radix.
     * Standard digits '0'-'9' are used and letters 'A'-'Z' for
     * radices 11 through 36.
     * @param result the digits of the number are appended here
     * @param n the number to be converted to digits; may be negative.
     * If negative, a '-' is prepended to the digits.
     * @param radix a radix from 2 to 36 inclusive.
     * @param minDigits the minimum number of digits, not including
     * any '-', to produce.  Values less than 2 have no effect.  One
     * digit is always emitted regardless of this parameter.
     * @return a reference to result
     */
    public static <T extends Appendable> T appendNumber(T result, int n,
            int radix, int minDigits)
    {
        try {
            if (radix < 2 || radix > 36) {
                throw new IllegalArgumentException("Illegal radix " + radix);
            }


            int abs = n;

            if (n < 0) {
                abs = -n;
                result.append("-");
            }

            recursiveAppendNumber(result, abs, radix, minDigits);

            return result;
        } catch (IOException e) {
            throw new IllegalIcuArgumentException(e);
        }

    }

    /**
     * Parse an unsigned 31-bit integer at the given offset.  Use
     * UCharacter.digit() to parse individual characters into digits.
     * @param text the text to be parsed
     * @param pos INPUT-OUTPUT parameter.  On entry, pos[0] is the
     * offset within text at which to start parsing; it should point
     * to a valid digit.  On exit, pos[0] is the offset after the last
     * parsed character.  If the parse failed, it will be unchanged on
     * exit.  Must be >= 0 on entry.
     * @param radix the radix in which to parse; must be >= 2 and <=
     * 36.
     * @return a non-negative parsed number, or -1 upon parse failure.
     * Parse fails if there are no digits, that is, if pos[0] does not
     * point to a valid digit on entry, or if the number to be parsed
     * does not fit into a 31-bit unsigned integer.
     */
    public static int parseNumber(String text, int[] pos, int radix) {
        // assert(pos[0] >= 0);
        // assert(radix >= 2);
        // assert(radix <= 36);
        int n = 0;
        int p = pos[0];
        while (p < text.length()) {
            int ch = Character.codePointAt(text, p);
            int d = UCharacter.digit(ch, radix);
            if (d < 0) {
                break;
            }
            n = radix*n + d;
            // ASSUME that when a 32-bit integer overflows it becomes
            // negative.  E.g., 214748364 * 10 + 8 => negative value.
            if (n < 0) {
                return -1;
            }
            ++p;
        }
        if (p == pos[0]) {
            return -1;
        }
        pos[0] = p;
        return n;
    }

    /**
     * Return true if the character is NOT printable ASCII.  The tab,
     * newline and linefeed characters are considered unprintable.
     */
    public static boolean isUnprintable(int c) {
        //0x20 = 32 and 0x7E = 126
        return !(c >= 0x20 && c <= 0x7E);
    }

    /**
     * @return true for control codes and for surrogate and noncharacter code points
     */
    public static boolean shouldAlwaysBeEscaped(int c) {
        if (c < 0x20) {
            return true;  // C0 control codes
        } else if (c <= 0x7e) {
            return false;  // printable ASCII
        } else if (c <= 0x9f) {
            return true;  // C1 control codes
        } else if (c < 0xd800) {
            return false;  // most of the BMP
        } else if (c <= 0xdfff || (0xfdd0 <= c && c <= 0xfdef) || (c & 0xfffe) == 0xfffe) {
            return true;  // surrogate or noncharacter code points
        } else if (c <= 0x10ffff) {
            return false;  // all else
        } else {
            return true;  // not a code point
        }
    }

    /**
     * Escapes one unprintable code point using <backslash>uxxxx notation
     * for U+0000 to U+FFFF and <backslash>Uxxxxxxxx for U+10000 and
     * above.  If the character is printable ASCII, then do nothing
     * and return false.  Otherwise, append the escaped notation and
     * return true.
     */
    public static <T extends Appendable> boolean escapeUnprintable(T result, int c) {
        if (isUnprintable(c)) {
            escape(result, c);
            return true;
        }
        return false;
    }

    /**
     * Escapes one code point using <backslash>uxxxx notation
     * for U+0000 to U+FFFF and <backslash>Uxxxxxxxx for U+10000 and above.
     * @return result
     */
    public static <T extends Appendable> T escape(T result, int c) {
        try {
            result.append('\\');
            if ((c & ~0xFFFF) != 0) {
                result.append('U');
                result.append(DIGITS[0xF&(c>>28)]);
                result.append(DIGITS[0xF&(c>>24)]);
                result.append(DIGITS[0xF&(c>>20)]);
                result.append(DIGITS[0xF&(c>>16)]);
            } else {
                result.append('u');
            }
            result.append(DIGITS[0xF&(c>>12)]);
            result.append(DIGITS[0xF&(c>>8)]);
            result.append(DIGITS[0xF&(c>>4)]);
            result.append(DIGITS[0xF&c]);
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the index of the first character in a set, ignoring quoted text.
     * For example, in the string "abc'hide'h", the 'h' in "hide" will not be
     * found by a search for "h".  Unlike String.indexOf(), this method searches
     * not for a single character, but for any character of the string
     * <code>setOfChars</code>.
     * @param text text to be searched
     * @param start the beginning index, inclusive; <code>0 <= start
     * <= limit</code>.
     * @param limit the ending index, exclusive; <code>start <= limit
     * <= text.length()</code>.
     * @param setOfChars string with one or more distinct characters
     * @return Offset of the first character in <code>setOfChars</code>
     * found, or -1 if not found.
     * @see String#indexOf
     */
    public static int quotedIndexOf(String text, int start, int limit,
            String setOfChars) {
        for (int i=start; i<limit; ++i) {
            char c = text.charAt(i);
            if (c == BACKSLASH) {
                ++i;
            } else if (c == APOSTROPHE) {
                while (++i < limit
                        && text.charAt(i) != APOSTROPHE) {}
            } else if (setOfChars.indexOf(c) >= 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Append a character to a rule that is being built up.  To flush
     * the quoteBuf to rule, make one final call with isLiteral == true.
     * If there is no final character, pass in (int)-1 as c.
     * @param rule the string to append the character to
     * @param c the character to append, or (int)-1 if none.
     * @param isLiteral if true, then the given character should not be
     * quoted or escaped.  Usually this means it is a syntactic element
     * such as > or $
     * @param escapeUnprintable if true, then unprintable characters
     * should be escaped using escapeUnprintable().  These escapes will
     * appear outside of quotes.
     * @param quoteBuf a buffer which is used to build up quoted
     * substrings.  The caller should initially supply an empty buffer,
     * and thereafter should not modify the buffer.  The buffer should be
     * cleared out by, at the end, calling this method with a literal
     * character (which may be -1).
     */
    public static void appendToRule(StringBuffer rule,
            int c,
            boolean isLiteral,
            boolean escapeUnprintable,
            StringBuffer quoteBuf) {
        // If we are escaping unprintables, then escape them outside
        // quotes.  \\u and \\U are not recognized within quotes.  The same
        // logic applies to literals, but literals are never escaped.
        if (isLiteral ||
                (escapeUnprintable && Utility.isUnprintable(c))) {
            if (quoteBuf.length() > 0) {
                // We prefer backslash APOSTROPHE to double APOSTROPHE
                // (more readable, less similar to ") so if there are
                // double APOSTROPHEs at the ends, we pull them outside
                // of the quote.

                // If the first thing in the quoteBuf is APOSTROPHE
                // (doubled) then pull it out.
                while (quoteBuf.length() >= 2 &&
                        quoteBuf.charAt(0) == APOSTROPHE &&
                        quoteBuf.charAt(1) == APOSTROPHE) {
                    rule.append(BACKSLASH).append(APOSTROPHE);
                    quoteBuf.delete(0, 2);
                }
                // If the last thing in the quoteBuf is APOSTROPHE
                // (doubled) then remove and count it and add it after.
                int trailingCount = 0;
                while (quoteBuf.length() >= 2 &&
                        quoteBuf.charAt(quoteBuf.length()-2) == APOSTROPHE &&
                        quoteBuf.charAt(quoteBuf.length()-1) == APOSTROPHE) {
                    quoteBuf.setLength(quoteBuf.length()-2);
                    ++trailingCount;
                }
                if (quoteBuf.length() > 0) {
                    rule.append(APOSTROPHE);
                    rule.append(quoteBuf);
                    rule.append(APOSTROPHE);
                    quoteBuf.setLength(0);
                }
                while (trailingCount-- > 0) {
                    rule.append(BACKSLASH).append(APOSTROPHE);
                }
            }
            if (c != -1) {
                /* Since spaces are ignored during parsing, they are
                 * emitted only for readability.  We emit one here
                 * only if there isn't already one at the end of the
                 * rule.
                 */
                if (c == ' ') {
                    int len = rule.length();
                    if (len > 0 && rule.charAt(len-1) != ' ') {
                        rule.append(' ');
                    }
                } else if (!escapeUnprintable || !Utility.escapeUnprintable(rule, c)) {
                    rule.appendCodePoint(c);
                }
            }
        }

        // Escape ' and '\' and don't begin a quote just for them
        else if (quoteBuf.length() == 0 &&
                (c == APOSTROPHE || c == BACKSLASH)) {
            rule.append(BACKSLASH).append((char)c);
        }

        // Specials (printable ascii that isn't [0-9a-zA-Z]) and
        // whitespace need quoting.  Also append stuff to quotes if we are
        // building up a quoted substring already.
        else if (quoteBuf.length() > 0 ||
                (c >= 0x0021 && c <= 0x007E &&
                        !((c >= 0x0030/*'0'*/ && c <= 0x0039/*'9'*/) ||
                                (c >= 0x0041/*'A'*/ && c <= 0x005A/*'Z'*/) ||
                                (c >= 0x0061/*'a'*/ && c <= 0x007A/*'z'*/))) ||
                                PatternProps.isWhiteSpace(c)) {
            quoteBuf.appendCodePoint(c);
            // Double ' within a quote
            if (c == APOSTROPHE) {
                quoteBuf.append((char)c);
            }
        }

        // Otherwise just append
        else {
            rule.appendCodePoint(c);
        }
    }

    /**
     * Append the given string to the rule.  Calls the single-character
     * version of appendToRule for each character.
     */
    public static void appendToRule(StringBuffer rule,
            String text,
            boolean isLiteral,
            boolean escapeUnprintable,
            StringBuffer quoteBuf) {
        for (int i=0; i<text.length(); ++i) {
            // Okay to process in 16-bit code units here
            appendToRule(rule, text.charAt(i), isLiteral, escapeUnprintable, quoteBuf);
        }
    }

    /**
     * Given a matcher reference, which may be null, append its
     * pattern as a literal to the given rule.
     */
    public static void appendToRule(StringBuffer rule,
            UnicodeMatcher matcher,
            boolean escapeUnprintable,
            StringBuffer quoteBuf) {
        if (matcher != null) {
            appendToRule(rule, matcher.toPattern(escapeUnprintable),
                    true, escapeUnprintable, quoteBuf);
        }
    }

    /**
     * Compares 2 unsigned integers
     * @param source 32 bit unsigned integer
     * @param target 32 bit unsigned integer
     * @return 0 if equals, 1 if source is greater than target and -1
     *         otherwise
     */
    public static final int compareUnsigned(int source, int target)
    {
        source += MAGIC_UNSIGNED;
        target += MAGIC_UNSIGNED;
        if (source < target) {
            return -1;
        }
        else if (source > target) {
            return 1;
        }
        return 0;
    }

    /**
     * Find the highest bit in a positive integer. This is done
     * by doing a binary search through the bits.
     *
     * @param n is the integer
     *
     * @return the bit number of the highest bit, with 0 being
     * the low order bit, or -1 if <code>n</code> is not positive
     */
    public static final byte highBit(int n)
    {
        if (n <= 0) {
            return -1;
        }

        byte bit = 0;

        if (n >= 1 << 16) {
            n >>= 16;
        bit += 16;
        }

        if (n >= 1 << 8) {
            n >>= 8;
        bit += 8;
        }

        if (n >= 1 << 4) {
            n >>= 4;
        bit += 4;
        }

        if (n >= 1 << 2) {
            n >>= 2;
        bit += 2;
        }

        if (n >= 1 << 1) {
            n >>= 1;
        bit += 1;
        }

        return bit;
    }
    /**
     * Utility method to take a int[] containing codepoints and return
     * a string representation with code units.
     */
    public static String valueOf(int[]source){
        // TODO: Investigate why this method is not on UTF16 class
        StringBuilder result = new StringBuilder(source.length);
        for(int i=0; i<source.length; i++){
            result.appendCodePoint(source[i]);
        }
        return result.toString();
    }


    /**
     * Utility to duplicate a string count times
     * @param s String to be duplicated.
     * @param count Number of times to duplicate a string.
     */
    public static String repeat(String s, int count) {
        if (count <= 0) return "";
        if (count == 1) return s;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; ++i) {
            result.append(s);
        }
        return result.toString();
    }

    public static String[] splitString(String src, String target) {
        return src.split("\\Q" + target + "\\E");
    }

    /**
     * Split the string at runs of ascii whitespace characters.
     */
    public static String[] splitWhitespace(String src) {
        return src.split("\\s+");
    }

    /**
     * Parse a list of hex numbers and return a string
     * @param string String of hex numbers.
     * @param minLength Minimal length.
     * @param separator Separator.
     * @return A string from hex numbers.
     */
    public static String fromHex(String string, int minLength, String separator) {
        return fromHex(string, minLength, Pattern.compile(separator != null ? separator : "\\s+"));
    }

    /**
     * Parse a list of hex numbers and return a string
     * @param string String of hex numbers.
     * @param minLength Minimal length.
     * @param separator Separator.
     * @return A string from hex numbers.
     */
    public static String fromHex(String string, int minLength, Pattern separator) {
        StringBuilder buffer = new StringBuilder();
        String[] parts = separator.split(string);
        for (String part : parts) {
            if (part.length() < minLength) {
                throw new IllegalArgumentException("code point too short: " + part);
            }
            int cp = Integer.parseInt(part, 16);
            buffer.appendCodePoint(cp);
        }
        return buffer.toString();
    }

    /**
     * This implementation is equivalent to Java 8+ Math#addExact(int, int)
     * @param x the first value
     * @param y the second value
     * @return the result
     */
    public static int addExact(int x, int y) {
        int r = x + y;
        // HD 2-12 Overflow iff both arguments have the opposite sign of the result
        if (((x ^ r) & (y ^ r)) < 0) {
            throw new ArithmeticException("integer overflow");
        }
        return r;
    }

    /**
     * Returns whether the chars in the two CharSequences are equal.
     */
    public static boolean charSequenceEquals(CharSequence a, CharSequence b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        for (int i = 0; i < a.length(); i++) {
            if (a.charAt(i) != b.charAt(i))
                return false;
        }
        return true;
    }

    /**
     * Returns a hash code for a CharSequence that is equivalent to calling
     * charSequence.toString().hashCode()
     */
    public static int charSequenceHashCode(CharSequence value) {
        int hash = 0;
        for (int i = 0; i < value.length(); i++) {
            hash = hash * 31 + value.charAt(i);
        }
        return hash;
    }

    /**
     * Appends a CharSequence to an Appendable, converting IOException to UncheckedIOException.
     */
    public static <A extends Appendable> A appendTo(CharSequence string, A appendable) {
        try {
            appendable.append(string);
            return appendable;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Java 8+ String#join(CharSequence, Iterable<? extends CharSequence>) compatible method for Java 7 env.
     * @param delimiter the delimiter that separates each element
     * @param elements the elements to join together.
     * @return a new String that is composed of the elements separated by the delimiter
     * @throws NullPointerException If delimiter or elements is null
     */
    public static String joinStrings(CharSequence delimiter, Iterable<? extends CharSequence> elements) {
        if (delimiter == null || elements == null) {
            throw new NullPointerException("Delimiter or elements is null");
        }
        StringBuilder buf = new StringBuilder();
        Iterator<? extends CharSequence> itr = elements.iterator();
        boolean isFirstElem = true;
        while (itr.hasNext()) {
            CharSequence element = itr.next();
            if (element != null) {
                if (!isFirstElem) {
                    buf.append(delimiter);
                } else {
                    isFirstElem = false;
                }
                buf.append(element);
            }
        }
        return buf.toString();
    }
}
