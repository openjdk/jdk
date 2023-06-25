/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

/**
 * General utility. Use JdkXmlUtils for XML processing related functions.
 */
public class Utils {
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
}
