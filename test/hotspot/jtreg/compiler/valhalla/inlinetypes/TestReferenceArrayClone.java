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

/*
 * @test
 * @summary Verify that clone preserves the layout of reference arrays.
 * @bug 8388256
 * @requires vm.compiler2.enabled
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 * @run main ${test.main.class}
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:-UseTLAB
 *                   -XX:CompileCommand=compileonly,${test.main.class}::testClone
 *                   ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

import jdk.internal.value.ValueClass;
import jdk.test.lib.Asserts;

public class TestReferenceArrayClone {
    static Integer[] testClone(Integer[] a) {
        return a.clone();
    }

    public static void main(String[] args) {
        Integer[] array = (Integer[])ValueClass.newReferenceArray(Integer.class, 1);
        array[0] = 42;
        array = testClone(array);
        Asserts.assertEQ(array[0], 42, "unexpected element");
        Asserts.assertFalse(ValueClass.isFlatArray(array), "should not be flat");
        Asserts.assertFalse(ValueClass.isNullRestrictedArray(array), "should not be null-restricted");
        Asserts.assertTrue(ValueClass.isAtomicArray(array), "should be atomic");
    }
}
