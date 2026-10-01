/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.valueclass;

import java.util.Arrays;

/**
 * A reusable value class helper for Collections tests.
 * Wraps an int and an int array (x, arr), supports equality, ordering, and hashing.
 * When compiled with -Xplugin:ValueClassPlugin --enable-preview this class
 * is treated as a value class; otherwise it is a plain identity class,
 * allowing the same tests to exercise both modes.
 */
@AsValueClass
public final class VClass implements Comparable<VClass> {
    public int x;
    public int[] arr;
    public VClass(int x, int[] arr) { this.x = x; this.arr = arr; }
    public int compareTo(VClass other) {
        int cmp = Integer.compare(x, other.x);
        return cmp != 0 ? cmp : Arrays.compare(arr, other.arr);
    }
    public boolean equals(Object o) { return o instanceof VClass t && x == t.x && Arrays.equals(arr, t.arr); }
    public int hashCode() { return 31 * x + Arrays.hashCode(arr); }
}
