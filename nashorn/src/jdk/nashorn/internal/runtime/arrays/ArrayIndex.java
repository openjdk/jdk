/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime.arrays;

import jdk.nashorn.internal.runtime.ConsString;
import jdk.nashorn.internal.runtime.JSType;

/**
 * Array index computation helpers. that both throw exceptions or return
 * invalid values.
 *
 */
public final class ArrayIndex {

    private static final int  INVALID_ARRAY_INDEX = -1;
    private static final long MAX_ARRAY_INDEX = 0xfffffffeL;

    private ArrayIndex() {
    }

    /**
     * Fast conversion of non-negative integer string to long.
     * @param key Key as a string.
     * @return long value of string or {@code -1} if string does not represent a valid index.
     */
    private static long fromString(final String key) {
        long value = 0;
        final int length = key.length();

        // Check for empty string or leading 0
        if (length == 0 || (length > 1 && key.charAt(0) == '0')) {
            return INVALID_ARRAY_INDEX;
        }

        // Fast toNumber.
        for (int i = 0; i < length; i++) {
            final char digit = key.charAt(i);

            // If not a digit.
            if (digit < '0' || digit > '9') {
                return INVALID_ARRAY_INDEX;
            }

            // Insert digit.
            value = value * 10 + digit - '0';

            // Check for overflow (need to catch before wrap around.)
            if (value > MAX_ARRAY_INDEX) {
                return INVALID_ARRAY_INDEX;
            }
        }

        return value;
    }

    /**
     * Returns a valid array index in an int, if the object represents one. This
     * routine needs to perform quickly since all keys are tested with it.
     *
     * @param  key key to check for array index
     * @return the array index, or {@code -1} if {@code key} does not represent a valid index.
     *         Note that negative return values other than {@code -1} are considered valid and can be converted to
     *         the actual index using {@link #toLongIndex(int)}.
     */
    public static int getArrayIndex(final Object key) {
        if (key instanceof Integer) {
            return getArrayIndex(((Integer) key).intValue());
        } else if (key instanceof Number) {
            return getArrayIndex(((Number) key).doubleValue());
        } else if (key instanceof String) {
            return (int)fromString((String) key);
        } else if (key instanceof ConsString) {
            return (int)fromString(key.toString());
        }

        return INVALID_ARRAY_INDEX;
    }

    /**
     * Returns a valid array index in an int, if the long represents one.
     *
     * @param key key to check
     * @return the array index, or {@code -1} if long is not a valid array index.
     *         Note that negative return values other than {@code -1} are considered valid and can be converted to
     *         the actual index using {@link #toLongIndex(int)}.
     */
    public static int getArrayIndex(final long key) {
        if (key >= 0 && key <= MAX_ARRAY_INDEX) {
            return (int)key;
        }

        return INVALID_ARRAY_INDEX;
    }


    /**
     * Return a valid index for this double, if it represents one.
     *
     * Doubles that aren't representable exactly as longs/ints aren't working
     * array indexes, however, array[1.1] === array["1.1"] in JavaScript.
     *
     * @param key the key to check
     * @return the array index this double represents or {@code -1} if this isn't a valid index.
     *         Note that negative return values other than {@code -1} are considered valid and can be converted to
     *         the actual index using {@link #toLongIndex(int)}.
     */
    public static int getArrayIndex(final double key) {
        if (JSType.isRepresentableAsInt(key)) {
            final int intKey = (int)key;
            if (intKey >= 0) {
                return intKey;
            }
        } else if (JSType.isRepresentableAsLong(key)) {
            return getArrayIndex((long) key);
        }

        return INVALID_ARRAY_INDEX;
    }


    /**
     * Return a valid array index for this string, if it represents one.
     *
     * @param key the key to check
     * @return the array index this string represents or {@code -1} if this isn't a valid index.
     *         Note that negative return values other than {@code -1} are considered valid and can be converted to
     *         the actual index using {@link #toLongIndex(int)}.
     */
    public static int getArrayIndex(final String key) {
        return (int)fromString(key);
    }

    /**
     * Check whether an index is valid as an array index. This check only tests if
     * it is the special "invalid array index" type, not if it is e.g. less than zero
     * or corrupt in some other way
     *
     * @param index index to test
     * @return true if {@code index} is not the special invalid array index type
     */
    public static boolean isValidArrayIndex(final int index) {
        return index != INVALID_ARRAY_INDEX;
    }

    /**
     * Convert an index to a long value. This basically amounts to ANDing it
     * with {@link JSType#MAX_UINT}, as the maximum array index in JavaScript
     * is 0xfffffffe
     *
     * @param index index to convert to long form
     * @return index as uint32 in a long
     */
    public static long toLongIndex(final int index) {
        return index & JSType.MAX_UINT;
    }

    /**
     * Check whether a key string represents a valid array index in JavaScript and is small enough
     * to fit into a positive int.
     *
     * @param key the key
     * @return true if key works as a valid int array index
     */
    public static boolean isIntArrayIndex(final String key) {
        return getArrayIndex(key) >= 0;
    }
}

