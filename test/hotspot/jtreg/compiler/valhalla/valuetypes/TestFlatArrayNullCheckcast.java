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

package compiler.c2;

import jdk.test.lib.Asserts;
import jdk.internal.value.ValueClass;

/*
 * @test
 * @bug 8395897
 * @summary Test that a null checkcast does not change the array layout type information.
 * @enablePreview
 * @modules java.base/jdk.internal.value
 * @requires vm.compiler2.enabled
 * @library /test/lib
 * @run main ${test.main.class}
 * @run main/othervm -Xbatch -XX:+UnlockDiagnosticVMOptions -XX:-UseArrayLoadStoreProfile
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test*
 *                   ${test.main.class}
 */
public class TestFlatArrayNullCheckcast {
    static Object testLoad(Object[] array, int index) {
        String unused = (String)array[0];
        return array[index];
    }

    static void testStore(Object[] array, int index) {
        String unused = (String)array[0];
        array[index] = 42;
    }

    public static void main(String[] args) {
        Integer[] array = new Integer[2];
        array[1] = 42;
        for (int i = 0; i < 50_000; i++) {
            Asserts.assertNull(testLoad(array, 0));
            Asserts.assertEquals(42, testLoad(array, 1));
            array[1] = 43;
            testStore(array, 1);
            Asserts.assertEquals(42, array[1]);
        }
    }
}

