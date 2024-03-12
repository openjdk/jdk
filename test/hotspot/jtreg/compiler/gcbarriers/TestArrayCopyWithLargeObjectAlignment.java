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
package compiler.gcbarriers;

import java.util.Arrays;

/**
 * @test
 * @bug 8312749
 * @summary Test that, when using a larger object alignment, ZGC arraycopy
 *          barriers are only applied to actual OOPs, and not to object
 *          alignment padding words.
 * @requires vm.gc.ZGenerational
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:CompileOnly=compiler.gcbarriers.TestArrayCopyWithLargeObjectAlignment::*
 *                   -XX:ObjectAlignmentInBytes=16
 *                   -XX:+UseZGC -XX:+ZGenerational
 *                   compiler.gcbarriers.TestArrayCopyWithLargeObjectAlignment
 */

public class TestArrayCopyWithLargeObjectAlignment {

    static Object[] doCopyOf(Object[] array) {
        return Arrays.copyOf(array, array.length);
    }

    static Object[] doClone(Object[] array) {
        return array.clone();
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10_000; i++) {
            // This test allocates an array 'a', copies it into a new array 'b'
            // using Arrays.copyOf, and clones 'b' into yet another array. For
            // ObjectAlignmentInBytes=16, the intrinsic implementation of
            // Arrays.copyOf leaves the object alignment padding word "b[1]"
            // untouched, preserving the badHeapWordVal value '0xbaadbabe'. The
            // test checks that this padding word is not processed as a valid
            // OOP by the ZGC arraycopy stub underlying the intrinsic
            // implementation of Object.clone. Allocating b using the intrinsic
            // implementation of Arrays.copyOf is key to reproducing the issue
            // because, unlike regular (fast or slow) array allocation,
            // Arrays.copyOf does not zero-clear the padding word.
            Object[] a = {new Object()};
            Object[] b = doCopyOf(a);
            doClone(b);
        }
    }
}
