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
 * @enablePreview
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64" | os.simpleArch == "riscv64")
 * @run main/othervm -XX:-BackgroundCompilation -XX:+IgnoreUnrecognizedVMOptions -XX:+AlwaysIncrementalInline ${test.main.class}
 */

package compiler.valhalla.inlinetypes;


public class TestLateInliningIncorrectArgs {
    static public void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test1();
            test2();
        }
    }


    static final MyValue2 v3 = new MyValue2((byte)42, null);
    static final MyValue2 v2 = new MyValue2((byte)42, v3);
    static final MyValue1 v1 = new MyValue1((byte)42, v2);
    static final C c = new C(42);

    static void test1() {
        lateInlined1(v1, c);
    }

    static int lateInlined1(MyValue1 v1, C c) {
        return c.field;
    }

    static void test2() {
        lateInlined2(v1, v1, c);
    }

    static int lateInlined2(MyValue1 v1, MyValue1 v2, C c) {
        return c.field;
    }

    static value class MyValue1 {
        byte byteField;
        MyValue2 next;

        MyValue1(byte byteField, MyValue2 next) {
            this.byteField = byteField;
            this.next = next;
        }
    };

    static value class MyValue2 {
        byte byteField;
        MyValue2 next;

        MyValue2(byte byteField, MyValue2 next) {
            this.byteField = byteField;
            this.next = next;
        }
    };

    static class C {
        int field;

        C(int field) {
            this.field = field;
        }
    }
}
