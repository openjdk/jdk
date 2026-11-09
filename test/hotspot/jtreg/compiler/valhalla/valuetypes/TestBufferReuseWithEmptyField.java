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
 * @bug 8392540
 * @summary Test C2's buffer reuse with a nullable empty value class field.
 * @library /test/lib
 * @enablePreview
 * @run main/othervm -Xbatch ${test.main.class}
 */

package compiler.valhalla.valuetypes;

import jdk.test.lib.Asserts;

public class TestBufferReuseWithEmptyField {
    static value class Empty { }

    static value class MyValue {
        int payload;
        Empty empty;

        MyValue(int payload, Empty empty) {
            this.payload = payload;
            this.empty = empty;
        }
    }

    static Object src;
    static Object dst;

    static void test() {
        MyValue val = (MyValue)src;
        // C2 should not reuse the 'src' buffer here, because the newly
        // created value object has a different, non-null value for 'empty'.
        dst = new MyValue(val.payload, new Empty());
    }

    public static void main(String[] args) {
        src = new MyValue(42, null);
        for (int i = 0; i < 20_000; i++) {
            test();
            MyValue result = (MyValue)dst;
            Asserts.assertNotNull(result.empty);
            Asserts.assertEQ(result.payload, 42);
        }
    }
}

