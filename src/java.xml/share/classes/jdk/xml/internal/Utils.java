/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.xml.internal;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * General utility. Use JdkXmlUtils for XML processing related functions.
 */
public class Utils {
    // The debug flag
    private static boolean debug = false;

    /*
     * The {@systemProperty jaxp.debug} property is supported by JAXP factories
     * and used to print out information related to the configuration of factories
     * and processors
     */
    static {
        String val = System.getProperty("jaxp.debug");
        // Allow simply setting the prop to turn on debug
        debug = val != null && !"false".equals(val);
    }

    // print out debug information if jaxp.debug is enabled
    public static void dPrint(Supplier<String> msgGen) {
        if (debug) {
            System.err.println("JAXP: " + msgGen.get());
        }
    }

    /**
     * Creates a new array with copies of the original array and additional items
     * appended to the end of it.
     *
     * @param original the original array
     * @param items items to be appended to the original array
     * @return a new array with copies of the original array and additional items
     */
    public static Class<?>[] arraysAppend(final Class<?>[] original, final Class<?>... items) {
        if (original == null && items == null) {
            return null;
        }
        if (items == null) {
            return Arrays.copyOf(original, original.length);
        }
        if (original == null) {
            return Arrays.copyOf(items, items.length);
        }

        Class<?>[] result = Arrays.copyOf(original, original.length + items.length);
        System.arraycopy(items, 0, result, original.length, items.length);
        return result;
    }

    /**
     * Returns the original array, or an empty array if it is {@code null}.
     * @param array the specified array
     * @return the original array, or an empty array if it is {@code null}
     */
    public static byte[] createEmptyArrayIfNull(byte[] array) {
        return (array != null) ? array : new byte[0];
    }

    /**
     * Returns the original array, or an empty array if it is {@code null}.
     * @param array the specified array
     * @return the original array, or an empty array if it is {@code null}
     */
    public static int[] createEmptyArrayIfNull(int[] array) {
        return (array != null) ? array : new int[0];
    }

    /**
     * Returns the original array, or an empty array if it is {@code null}.
     * @param <T> the class type
     * @param array the specified array
     * @param type the type of the array
     * @return the original array, or an empty array if it is {@code null}
     */
    public static <T> T[] createEmptyArrayIfNull(final T[] array, final Class<T[]> type) {
        Objects.requireNonNull(type, "The type argument should not be null.");

        return (array != null) ? array : type.cast(Array.newInstance(type.getComponentType(), 0));
    }

    /**
     * Returns the new stream created by {@code Stream.of(values)} or an empty
     * sequential stream created by {@code Stream.empty()} if values is null.
     *
     * @param <T> the type of stream elements
     * @param values the elements of the new stream
     * @return the new stream created by {@code Stream.of(values)} or an empty
     * sequential stream created by {@code Stream.empty()} if values is null.
     */
    @SafeVarargs
    @SuppressWarnings("varargs") // Creating a stream from an array is safe
    public static <T> Stream<T> streamOfIfNonNull(final T... values) {
        return values == null ? Stream.empty() : Stream.of(values);
    }

    /**
     * Checks if a CharSequence is empty ("") or null.
     * @param cs the CharSequence to check, may be null
     * @return {@code true} if the CharSequence is empty or null
     */
    public static boolean isEmpty(final CharSequence cs) {
        return cs == null || cs.length() == 0;
    }
}
