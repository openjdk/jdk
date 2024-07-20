/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
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

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

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
     * add value length into current length
     * @param length      String length
     * @param value       value to mix in
     * @return            new length
     */
    @ForceInline
    static int stringSize(int length, boolean value) {
        return length + (value ? 4 : 5);
    }

    /**
     * add value length into current length
     * @param length      String length
     * @param value       value to mix in
     * @return            new length
     */
    static int stringSize(int length, char value) {
        return length + 1;
    }

    /**
     * add value length into current length
     * @param length      String length
     * @param value       value to mix in
     * @return            new length
     */
    static int stringSize(int length, int value) {
        return length + Integer.stringSize(value);
    }

    /**
     * add value length into current length
     * @param length      String length
     * @param value       value to mix in
     * @return            new length
     */
    static int stringSize(int length, long value) {
        return length + Long.stringSize(value);
    }

    /**
     * add value length into current length
     * @param length      String length
     * @param value       value to mix in
     * @return            new length
     */
    static int stringSize(int length, String value) {
        return length + value.length();
    }

    /**
     * Prepends the stringly representation of boolean value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param index      final char index in the buffer
     * @param coder      the coder of buf
     * @param buf        buffer to append to
     * @param value      boolean value to encode
     * @return           updated index (coder value retained)
     */
    static int prepend(int index, byte coder, byte[] buf, boolean value) {
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
     * Prepends constant and the stringly representation of value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param index      final char index in the buffer
     * @param coder      the coder of buf
     * @param buf        buffer to append to
     * @param value      boolean value to encode
     * @param prefix     a constant to prepend before value
     * @return           updated index (coder value retained)
     */
    static int prepend(int index, byte coder, byte[] buf, boolean value, String prefix) {
        index = prepend(index, coder, buf, value);
        index = prepend(index, coder, buf, prefix);
        return index;
    }

    /**
     * Prepends the stringly representation of char value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param index      final char index in the buffer
     * @param coder      the coder of buf
     * @param buf        buffer to append to
     * @param value      char value to encode
     * @return           updated index (coder value retained)
     */
    static int prepend(int index, byte coder, byte[] buf, char value) {
        index--;
        if (coder == String.LATIN1) {
            buf[index] = (byte) (value & 0xFF);
        } else {
            StringUTF16.putChar(buf, index, value);
        }
        return index;
    }

    /**
     * Prepends constant and the stringly representation of value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param indexCoder final char index in the buffer, along with coder packed
     *                   into higher bits.
     * @param buf        buffer to append to
     * @param value      boolean value to encode
     * @param prefix     a constant to prepend before value
     * @return           updated index (coder value retained)
     */
    static int prepend(int index, byte coder, byte[] buf, char value, String prefix) {
        index = prepend(index, coder, buf, value);
        index = prepend(index, coder, buf, prefix);
        return index;
    }

    /**
     * Prepends the stringly representation of integer value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param index      final char index in the buffer
     * @param coder      the coder of buf
     * @param buf        buffer to append to
     * @param value      integer value to encode
     * @return           updated index (coder value retained)
     */
    static int prepend(int index, byte coder, byte[] buf, int value) {
        if (coder == String.LATIN1) {
            return StringLatin1.getChars(value, index, buf);
        } else {
            return StringUTF16.getChars(value, index, buf);
        }
    }

    /**
     * Prepends constant and the stringly representation of value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param index      final char index in the buffer
     * @param coder      the coder of buf
     * @param buf        buffer to append to
     * @param value      boolean value to encode
     * @param prefix     a constant to prepend before value
     * @return           updated index (coder value retained)
     */
    static int prepend(int index, byte coder, byte[] buf, int value, String prefix) {
        index = prepend(index, coder, buf, value);
        index = prepend(index, coder, buf, prefix);
        return index;
    }

    /**
     * Prepends the stringly representation of long value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param index      final char index in the buffer
     * @param coder      the coder of buf
     * @param buf        buffer to append to
     * @param value      long value to encode
     * @return           updated index (coder value retained)
     */
    static int prepend(int index, byte coder, byte[] buf, long value) {
        if (coder == String.LATIN1) {
            return StringLatin1.getChars(value, index, buf);
        } else {
            return StringUTF16.getChars(value, index, buf);
        }
    }

    /**
     * Prepends constant and the stringly representation of value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param index      final char index in the buffer
     * @param coder      the coder of buf
     * @param buf        buffer to append to
     * @param value      boolean value to encode
     * @param prefix     a constant to prepend before value
     * @return           updated index (coder value retained)
     */
    static int prepend(int index, byte coder, byte[] buf, long value, String prefix) {
        index = prepend(index, coder, buf, value);
        index = prepend(index, coder, buf, prefix);
        return index;
    }

    /**
     * Prepends the stringly representation of String value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param index      final char index in the buffer
     * @param coder      the coder of buf
     * @param buf        buffer to append to
     * @param value      String value to encode
     * @return           updated index (coder value retained)
     */
    static int prepend(int index, byte coder, byte[] buf, String value) {
        index -= value.length();
        value.getBytes(buf, index, coder);
        return index;
    }

    /**
     * Prepends constant and the stringly representation of value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param index      final char index in the buffer
     * @param coder      the coder of buf
     * @param buf        buffer to append to
     * @param value      boolean value to encode
     * @param prefix     a constant to prepend before value
     * @return           updated index (coder value retained)
     */
    static int prepend(int index, byte coder, byte[] buf, String value, String prefix) {
        index = prepend(index, coder, buf, value);
        index = prepend(index, coder, buf, prefix);
        return index;
    }

    /**
     * Instantiates the String with given buffer and coder
     * @param buf           buffer to use
     * @param indexCoder    remaining index (should be zero) and coder
     * @return String       resulting string
     */
    @ForceInline
    static String newString(byte[] buf, byte coder) {
        // Use the private, non-copying constructor (unsafe!)
        return new String(buf, coder);
    }

    static byte stringCoder(byte coder, char value) {
        return StringLatin1.canEncode(value) ? coder : String.UTF16;
    }

    static byte stringCoder(byte coder, int value) {
        return coder;
    }

    static byte stringCoder(byte coder, long value) {
        return coder;
    }

    static byte stringCoder(byte coder, boolean value) {
        return coder;
    }

    static byte stringCoder(byte coder, String str) {
        return (byte) (coder | str.coder());
    }

    /**
     * Perform a simple concatenation between two objects. Added for startup
     * performance, but also demonstrates the code that would be emitted by
     * {@code java.lang.invoke.StringConcatFactory$MethodHandleInlineCopyStrategy}
     * for two Object arguments.
     *
     * @param first         first argument
     * @param second        second argument
     * @return String       resulting string
     */
    @ForceInline
    static String simpleConcat(Object first, Object second) {
        String s1 = stringOf(first);
        String s2 = stringOf(second);
        if (s1.isEmpty()) {
            // newly created string required, see JLS 15.18.1
            return new String(s2);
        }
        if (s2.isEmpty()) {
            // newly created string required, see JLS 15.18.1
            return new String(s1);
        }
        return doConcat(s1, s2);
    }

    /**
     * Perform a simple concatenation between two non-empty strings.
     *
     * @param s1         first argument
     * @param s2         second argument
     * @return String    resulting string
     */
    @ForceInline
    static String doConcat(String s1, String s2) {
        byte coder = (byte) (s1.coder() | s2.coder());
        int newLength = (s1.length() + s2.length()) << coder;
        byte[] buf = newArray(newLength);
        s1.getBytes(buf, 0, coder);
        s2.getBytes(buf, s1.length(), coder);
        return new String(buf, coder);
    }

    /**
     * Produce a String from a concatenation of single argument, which we
     * end up using for trivial concatenations like {@code "" + arg}.
     *
     * This will always create a new Object to comply with JLS {@jls 15.18.1}:
     * "The String object is newly created unless the expression is a
     * compile-time constant expression".
     *
     * @param arg           the only argument
     * @return String       resulting string
     */
    @ForceInline
    static String newStringOf(Object arg) {
        return new String(stringOf(arg));
    }

    /**
     * We need some additional conversion for Objects in general, because
     * {@code String.valueOf(Object)} may return null. String conversion rules
     * in Java state we need to produce "null" String in this case, so we
     * provide a customized version that deals with this problematic corner case.
     */
    static String stringOf(Object value) {
        String s;
        return (value == null || (s = value.toString()) == null) ? "null" : s;
    }

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    /**
     * Allocates an uninitialized byte array based on the length and coder
     * information, then prepends the given suffix string at the end of the
     * byte array before returning it. The calling code must adjust the
     * indexCoder so that it's taken the coder of the suffix into account, but
     * subtracted the length of the suffix.
     *
     * @param suffix
     * @param indexCoder
     * @return the newly allocated byte array
     */
    @ForceInline
    static byte[] newArrayWithSuffix(String suffix, int index, byte coder) {
        byte[] buf = newArray((index + suffix.length()) << coder);
        suffix.getBytes(buf, index, coder);
        return buf;
    }

    /**
     * Allocates an uninitialized byte array based on the length and coder information
     * in indexCoder
     * @param indexCoder
     * @return the newly allocated byte array
     */
    @ForceInline
    static byte[] newArray(int index, byte coder) {
        index = index << coder;
        return newArray(index);
    }

    /**
     * Allocates an uninitialized byte array based on the length
     * @param length
     * @return the newly allocated byte array
     */
    @ForceInline
    static byte[] newArray(int length) {
        if (length < 0) {
            throw new OutOfMemoryError("Overflow: String length out of range");
        }
        return (byte[]) UNSAFE.allocateUninitializedArray(byte.class, length);
    }

    /**
     * Provides the initial coder for the String.
     * @return initial coder, adjusted into the upper half
     */
    static byte initialCoder() {
        return String.COMPACT_STRINGS ? String.LATIN1 : String.UTF16;
    }

    static MethodHandle lookupStatic(String name, MethodType methodType) {
        try {
            return MethodHandles.lookup()
                    .findStatic(StringConcatHelper.class, name, methodType);
        } catch (NoSuchMethodException|IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

}
