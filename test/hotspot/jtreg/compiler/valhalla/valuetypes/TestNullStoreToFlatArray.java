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

package compiler.valhalla;

import jdk.test.lib.Asserts;

/**
 * @test
 * @bug 8396157
 * @summary Test that (null) stores to arrays with unknown layout check for flatness.
 * @enablePreview
 * @library /test/lib
 * @run main ${test.main.class}
 * @run main/othervm -Xbatch -XX:TieredStopAtLevel=1
 *                   -XX:CompileCommand=compileonly,${test.main.class}::store
 *                   ${test.main.class}
 */
public class TestNullStoreToFlatArray {
    static void store(Object[] array, int i, String value) {
        array[i] = value;
    }

    public static void main(String[] args) {
        String[] stringArray = new String[1];
        for (int i = 0; i < 20_000; i++) {
            store(stringArray, 0, null);
        }

        Integer[] integerArray = { 42 };
        store(integerArray, 0, null);
        Asserts.assertNull(integerArray[0]);
    }
}
