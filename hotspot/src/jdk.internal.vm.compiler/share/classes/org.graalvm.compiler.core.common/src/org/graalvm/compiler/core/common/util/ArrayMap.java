/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.common.util;

/**
 * The {@code ArrayMap} class implements an efficient one-level map which is implemented as an
 * array. Note that because of the one-level array inside, this data structure performs best when
 * the range of integer keys is small and densely used. Note that the implementation can handle
 * arbitrary intervals, including negative numbers, up to intervals of size 2^31 - 1.
 */
public class ArrayMap<T> {

    private static final int INITIAL_SIZE = 5; // how big the initial array should be
    private static final int EXTRA = 2; // how far on the left or right of a new element to grow

    Object[] map;
    int low;

    /**
     * Constructs a new {@code ArrayMap} with no initial assumptions.
     */
    public ArrayMap() {
    }

    /**
     * Constructs a new {@code ArrayMap} that initially covers the specified interval. Note that
     * this map will automatically expand if necessary later.
     *
     * @param low the low index, inclusive
     * @param high the high index, exclusive
     */
    public ArrayMap(int low, int high) {
        this.low = low;
        this.map = new Object[high - low + 1];
    }

    /**
     * Puts a new value in the map at the specified index.
     *
     * @param i the index at which to store the value
     * @param value the value to store at the specified index
     */
    public void put(int i, T value) {
        int index = i - low;
        if (map == null) {
            // no map yet
            map = new Object[INITIAL_SIZE];
            low = index - 2;
            map[INITIAL_SIZE / 2] = value;
        } else if (index < 0) {
            // grow backwards
            growBackward(i, value);
        } else if (index >= map.length) {
            // grow forwards
            growForward(i, value);
        } else {
            // no growth necessary
            map[index] = value;
        }
    }

    /**
     * Gets the value at the specified index in the map.
     *
     * @param i the index
     * @return the value at the specified index; {@code null} if there is no value at the specified
     *         index, or if the index is out of the currently stored range
     */
    public T get(int i) {
        int index = i - low;
        if (map == null || index < 0 || index >= map.length) {
            return null;
        }
        Class<T> type = null;
        return Util.uncheckedCast(type, map[index]);
    }

    public int length() {
        return map.length;
    }

    private void growBackward(int i, T value) {
        int nlow = i - EXTRA;
        Object[] nmap = new Object[low - nlow + map.length];
        System.arraycopy(map, 0, nmap, low - nlow, map.length);
        map = nmap;
        low = nlow;
        map[i - low] = value;
    }

    private void growForward(int i, T value) {
        int nlen = i - low + 1 + EXTRA;
        Object[] nmap = new Object[nlen];
        System.arraycopy(map, 0, nmap, 0, map.length);
        map = nmap;
        map[i - low] = value;
    }
}
