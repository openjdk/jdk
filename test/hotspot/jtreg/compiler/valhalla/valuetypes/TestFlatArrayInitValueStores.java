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

package compiler.valhalla.valuetypes;

import jdk.internal.value.ValueClass;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8395888
 * @summary Test initializing stores to flat arrays.
 * @enablePreview
 * @library /test/lib
 * @modules java.base/jdk.internal.value
 * @run main/othervm -Xbatch ${test.main.class}
 */
public class TestFlatArrayInitValueStores {
    static value record MyValue(MyValue other, int i) { }

    static final MyValue INIT_VAL = new MyValue(null, 42);

    // Test that a zero initializing store is observed
    static int testZeroInitStore(int i) {
        // array is non-escaping and thus flat stores are expanded non-atomic
        MyValue[] array = (MyValue[])ValueClass.newNullRestrictedAtomicArray(MyValue.class, 1, INIT_VAL);
        array[0] = new MyValue(null, 0); // Zero store to i is incorrectly skipped
        return array[0].i();
    }

    // Test that a non-zero initializing store is observed
    static boolean testInitValueStore(int i) {
        MyValue[] array = (MyValue[])ValueClass.newNullRestrictedAtomicArray(MyValue.class, 1, INIT_VAL);
        array[0] = new MyValue(INIT_VAL, i + 1); // INIT_VAL store to 'other' is incorrectly skipped
        return array[0].other() == INIT_VAL;
    }

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            Asserts.assertEQ(testZeroInitStore(i), 0);
            Asserts.assertTrue(testInitValueStore(i));
        }
    }
}
