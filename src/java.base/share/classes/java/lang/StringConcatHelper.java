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
     * Check for overflow, throw the exception on overflow.
     * @param len String length
     * @return length
     */
    private static int checkOverflow(int len) {
        if (len < 0) {
            throw new OutOfMemoryError("Overflow: String length out of range");
        }
        return len;
    }

    /**
     * Mix value length into current length
     * @param current current length
     * @param value   value to mix in
     * @return new length
     */
    static int mixLen(int current, boolean value) {
        return checkOverflow(current + (value ? 4 : 5));
    }

    /**
     * Mix value length into current length
     * @param current current length
     * @param value   value to mix in
     * @return new length
     */
    static int mixLen(int current, byte value) {
        return mixLen(current, (int)value);
    }

    /**
     * Mix value length into current length
     * @param current current length
     * @param value   value to mix in
     * @return new length
     */
    static int mixLen(int current, char value) {
        return checkOverflow(current + 1);
    }

    /**
     * Mix value length into current length
     * @param current current length
     * @param value   value to mix in
     * @return new length
     */
    static int mixLen(int current, short value) {
        return mixLen(current, (int)value);
    }

    /**
     * Mix value length into current length
     * @param current current length
     * @param value   value to mix in
     * @return new length
     */
    static int mixLen(int current, int value) {
        return checkOverflow(current + Integer.stringSize(value));
    }

    /**
     * Mix value length into current length
     * @param current current length
     * @param value   value to mix in
     * @return new length
     */
    static int mixLen(int current, long value) {
        return checkOverflow(current + Long.stringSize(value));
    }

    /**
     * Mix value length into current length
     * @param current current length
     * @param value   value to mix in
     * @return new length
     */
    static int mixLen(int current, String value) {
        return checkOverflow(current + value.length());
    }

    /**
     * Mix coder into current coder
     * @param current current coder
     * @param value   value to mix in
     * @return new coder
     */
    static byte mixCoder(byte current, char value) {
        return (byte)(current | (StringLatin1.canEncode(value) ? 0 : 1));
    }

    /**
     * Mix coder into current coder
     * @param current current coder
     * @param value   value to mix in
     * @return new coder
     */
    static byte mixCoder(byte current, String value) {
        return (byte)(current | value.coder());
    }

    /**
     * Prepends the stringly representation of boolean value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param index final char index in the buffer
     * @param buf   buffer to append to
     * @param coder coder to add with
     * @param value boolean value to encode
     * @return new index
     */
    static int prepend(int index, byte[] buf, byte coder, boolean value) {
        if (coder == String.LATIN1) {
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
        }
        return index;
    }

    /**
     * Prepends the stringly representation of byte value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param index final char index in the buffer
     * @param buf   buffer to append to
     * @param coder coder to add with
     * @param value byte value to encode
     * @return new index
     */
    static int prepend(int index, byte[] buf, byte coder, byte value) {
        return prepend(index, buf, coder, (int)value);
    }

    /**
     * Prepends the stringly representation of char value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param index final char index in the buffer
     * @param buf   buffer to append to
     * @param coder coder to add with
     * @param value char value to encode
     * @return new index
     */
    static int prepend(int index, byte[] buf, byte coder, char value) {
        if (coder == String.LATIN1) {
            buf[--index] = (byte) (value & 0xFF);
        } else {
            StringUTF16.putChar(buf, --index, value);
        }
        return index;
    }

    /**
     * Prepends the stringly representation of short value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param index final char index in the buffer
     * @param buf   buffer to append to
     * @param coder coder to add with
     * @param value short value to encode
     * @return new index
     */
    static int prepend(int index, byte[] buf, byte coder, short value) {
        return prepend(index, buf, coder, (int)value);
    }

    /**
     * Prepends the stringly representation of integer value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param index final char index in the buffer
     * @param buf   buffer to append to
     * @param coder coder to add with
     * @param value integer value to encode
     * @return new index
     */
    static int prepend(int index, byte[] buf, byte coder, int value) {
        if (coder == String.LATIN1) {
            return Integer.getChars(value, index, buf);
        } else {
            return StringUTF16.getChars(value, index, buf);
        }
    }

    /**
     * Prepends the stringly representation of long value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param index final char index in the buffer
     * @param buf   buffer to append to
     * @param coder coder to add with
     * @param value long value to encode
     * @return new index
     */
    static int prepend(int index, byte[] buf, byte coder, long value) {
        if (coder == String.LATIN1) {
            return Long.getChars(value, index, buf);
        } else {
            return StringUTF16.getChars(value, index, buf);
        }
    }

    /**
     * Prepends the stringly representation of String value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param index final char index in the buffer
     * @param buf   buffer to append to
     * @param coder coder to add with
     * @param value String value to encode
     * @return new index
     */
    static int prepend(int index, byte[] buf, byte coder, String value) {
        index -= value.length();
        value.getBytes(buf, index, coder);
        return index;
    }

    /**
     * Instantiates the String with given buffer and coder
     * @param buf     buffer to use
     * @param index   remaining index
     * @param coder   coder to use
     * @return String resulting string
     */
    static String newString(byte[] buf, int index, byte coder) {
        // Use the private, non-copying constructor (unsafe!)
        if (index != 0) {
            throw new InternalError("Storage is not completely initialized, " + index + " bytes left");
        }
        return new String(buf, coder);
    }

    /**
     * Provides the initial coder for the String.
     * @return initial coder
     */
    static byte initialCoder() {
        return String.COMPACT_STRINGS ? String.LATIN1 : String.UTF16;
    }

}
