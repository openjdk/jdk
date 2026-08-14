/*
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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

/**
 * @test
 * @bug 8381563
 * @summary [lworld] SharedRuntime::allocate_inline_types hits "buffer not of expected class" assert
 * @enablePreview
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @run main/othervm -XX:-BackgroundCompilation
 *                   -XX:CompileOnly=compiler.valhalla.inlinetypes.TestScalarizedCCReceiverOnlyEntryAllocBuffer::test1
 *                   ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

public class TestScalarizedCCReceiverOnlyEntryAllocBuffer {

    static Object field1;

    public static void main(String[] args) {
        MyValue2 v2 = new MyValue2(42);
        MyValue3 v3 = new MyValue3(42);
        MyValue4 v4 = new MyValue4(42);

        for (int i = 0; i < 20_000; i++) {
            test1(v2);
            test1(v3);
            test1(v4);
        }
    }

    static void test1(I i) {
        MyValue1 v1 = new MyValue1(42);
        field1 = v1;
        i.m(v1);
    }


    static value class MyValue1 {
        int intField;

        MyValue1(int intField) {
            this.intField = intField;
        }
    }

    static interface I {
        default public void m(MyValue1 v) {
        }
    }

    static value class MyValue2 implements I {
        int intField;

        MyValue2(int intField) {
            this.intField = intField;
        }

        public void m(MyValue1 v) {
        }
    }

    static value class MyValue3 implements I {
        int intField;

        MyValue3(int intField) {
            this.intField = intField;
        }

        public void m(MyValue1 v) {
        }
    }

    static value class MyValue4 implements I {
        int intField;

        MyValue4(int intField) {
            this.intField = intField;
        }

        public void m(MyValue1 v) {
        }
    }
}
