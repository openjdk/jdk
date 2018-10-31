/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package java.lang;

/**
 * Helper for string concatenation. These methods are mostly looked up with private lookups
 * from {@link java.lang.invoke.StringConcatFactory}, and used in {@link java.lang.invoke.MethodHandle}
 * combinators there.
 */
final class StringConcatHelper {

    private StringConcatHelper() {
        // no instantiation
    }

    /**
     * Check for overflow, throw exception on overflow.
     * @param lengthCoder String length and coder
     * @return lengthCoder
     */
    private static long checkOverflow(long lengthCoder) {
        if ((int)lengthCoder >= 0) {
            return lengthCoder;
        }
        throw new OutOfMemoryError("Overflow: String length out of range");
    }

    /**
     * Mix value length and coder into current length and coder.
     * @param current current length
     * @param value   value to mix in
     * @return new length and coder
     */
    static long mix(long current, boolean value) {
        return checkOverflow(current + (value ? 4 : 5));
    }

    /**
     * Mix value length and coder into current length and coder.
     * @param current current length
     * @param value   value to mix in
     * @return new length and coder
     */
    static long mix(long current, byte value) {
        return mix(current, (int)value);
    }

    /**
     * Mix value length and coder into current length and coder.
     * @param current current length
     * @param value   value to mix in
     * @return new length and coder
     */
    static long mix(long current, char value) {
        return checkOverflow(current + 1) | (StringLatin1.canEncode(value) ? 0 : UTF16);
    }

    /**
     * Mix value length and coder into current length and coder.
     * @param current current length
     * @param value   value to mix in
     * @return new length and coder
     */
    static long mix(long current, short value) {
        return mix(current, (int)value);
    }

    /**
     * Mix value length and coder into current length and coder.
     * @param current current length
     * @param value   value to mix in
     * @return new length and coder
     */
    static long mix(long current, int value) {
        return checkOverflow(current + Integer.stringSize(value));
    }

    /**
     * Mix value length and coder into current length and coder.
     * @param current current length
     * @param value   value to mix in
     * @return new length and coder
     */
    static long mix(long current, long value) {
        return checkOverflow(current + Long.stringSize(value));
    }

    /**
     * Mix value length and coder into current length and coder.
     * @param current current length
     * @param value   value to mix in
     * @return new length and coder
     */
    static long mix(long current, String value) {
        current += value.length();
        if (value.coder() == String.UTF16) {
            current |= UTF16;
        }
        return checkOverflow(current);
    }

    /**
     * Prepends the stringly representation of boolean value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param indexCoder final char index in the buffer, along with coder packed
     *                   into higher bits.
     * @param buf        buffer to append to
     * @param value      boolean value to encode
     * @return           updated index (coder value retained)
     */
    static long prepend(long indexCoder, byte[] buf, boolean value) {
        int index = (int)indexCoder;
        if (indexCoder < UTF16) {
            if (value) {
                buf[--index] = 'e';
                buf[--index] = 'u';
                buf[--index] = 'r';
                buf[--index] = 't';
            } else {
                buf[--index] = 'e';
                buf[--index] = 's';
                buf[--index] = 'l';
                buf[--index] = 'a';
                buf[--index] = 'f';
            }
            return index;
        } else {
            if (value) {
                StringUTF16.putChar(buf, --index, 'e');
                StringUTF16.putChar(buf, --index, 'u');
                StringUTF16.putChar(buf, --index, 'r');
                StringUTF16.putChar(buf, --index, 't');
            } else {
                StringUTF16.putChar(buf, --index, 'e');
                StringUTF16.putChar(buf, --index, 's');
                StringUTF16.putChar(buf, --index, 'l');
                StringUTF16.putChar(buf, --index, 'a');
                StringUTF16.putChar(buf, --index, 'f');
            }
            return index | UTF16;
        }
    }

    /**
     * Prepends the stringly representation of byte value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param indexCoder final char index in the buffer, along with coder packed
     *                   into higher bits.
     * @param buf        buffer to append to
     * @param value      byte value to encode
     * @return           updated index (coder value retained)
     */
    static long prepend(long indexCoder, byte[] buf, byte value) {
        return prepend(indexCoder, buf, (int)value);
    }

    /**
     * Prepends the stringly representation of char value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param indexCoder final char index in the buffer, along with coder packed
     *                   into higher bits.
     * @param buf        buffer to append to
     * @param value      char value to encode
     * @return           updated index (coder value retained)
     */
    static long prepend(long indexCoder, byte[] buf, char value) {
        if (indexCoder < UTF16) {
            buf[(int)(--indexCoder)] = (byte) (value & 0xFF);
        } else {
            StringUTF16.putChar(buf, (int)(--indexCoder), value);
        }
        return indexCoder;
    }

    /**
     * Prepends the stringly representation of short value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param indexCoder final char index in the buffer, along with coder packed
     *                   into higher bits.
     * @param buf        buffer to append to
     * @param value      short value to encode
     * @return           updated index (coder value retained)
     */
    static long prepend(long indexCoder, byte[] buf, short value) {
        return prepend(indexCoder, buf, (int)value);
    }

    /**
     * Prepends the stringly representation of integer value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param indexCoder final char index in the buffer, along with coder packed
     *                   into higher bits.
     * @param buf        buffer to append to
     * @param value      integer value to encode
     * @return           updated index (coder value retained)
     */
    static long prepend(long indexCoder, byte[] buf, int value) {
        if (indexCoder < UTF16) {
            return Integer.getChars(value, (int)indexCoder, buf);
        } else {
            return StringUTF16.getChars(value, (int)indexCoder, buf) | UTF16;
        }
    }

    /**
     * Prepends the stringly representation of long value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param indexCoder final char index in the buffer, along with coder packed
     *                   into higher bits.
     * @param buf        buffer to append to
     * @param value      long value to encode
     * @return           updated index (coder value retained)
     */
    static long prepend(long indexCoder, byte[] buf, long value) {
        if (indexCoder < UTF16) {
            return Long.getChars(value, (int)indexCoder, buf);
        } else {
            return StringUTF16.getChars(value, (int)indexCoder, buf) | UTF16;
        }
    }

    /**
     * Prepends the stringly representation of String value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param indexCoder final char index in the buffer, along with coder packed
     *                   into higher bits.
     * @param buf        buffer to append to
     * @param value      String value to encode
     * @return           updated index (coder value retained)
     */
    static long prepend(long indexCoder, byte[] buf, String value) {
        indexCoder -= value.length();
        if (indexCoder < UTF16) {
            value.getBytes(buf, (int)indexCoder, String.LATIN1);
        } else {
            value.getBytes(buf, (int)indexCoder, String.UTF16);
        }
        return indexCoder;
    }

    /**
     * Instantiates the String with given buffer and coder
     * @param buf           buffer to use
     * @param indexCoder    remaining index (should be zero) and coder
     * @return String       resulting string
     */
    static String newString(byte[] buf, long indexCoder) {
        // Use the private, non-copying constructor (unsafe!)
        if (indexCoder == LATIN1) {
            return new String(buf, String.LATIN1);
        } else if (indexCoder == UTF16) {
            return new String(buf, String.UTF16);
        } else {
            throw new InternalError("Storage is not completely initialized, " + (int)indexCoder + " bytes left");
        }
    }

    private static final long LATIN1 = (long)String.LATIN1 << 32;

    private static final long UTF16 = (long)String.UTF16 << 32;

    /**
     * Provides the initial coder for the String.
     * @return initial coder, adjusted into the upper half
     */
    static long initialCoder() {
        return String.COMPACT_STRINGS ? LATIN1 : UTF16;
    }

}
