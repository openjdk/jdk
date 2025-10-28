/*
 * Copyright (c) 2025 IBM Corporation. All rights reserved.
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
 * @bug 8339526
 * @summary C2: store incorrectly removed for clone() transformed to series of loads/stores
 * @run main/othervm -XX:-BackgroundCompilation compiler.arraycopy.TestCloneUnknownClassAtParseTime
 * @run main compiler.arraycopy.TestCloneUnknownClassAtParseTime
 */

package compiler.arraycopy;

public class TestCloneUnknownClassAtParseTime {
    private static volatile int volatileField;
    static A field;

    public static void main(String[] args) throws CloneNotSupportedException {
        A a = new A();
        for (int i = 0; i < 20_000; i++) {
            B b = (B)test1(-1);
            if (b.field1 != 42 || b.field2 != 42|| b.field3 != 42) {
                throw new RuntimeException("Clone wrongly initialized");
            }
            inlined1(42);
            field = a;
            inlined2();
        }
    }

    private static A test1(int i) throws CloneNotSupportedException {
        int[] nonEscapingArray = new int[1];
        field = new B(42, 42, 42);

        if (i > 0) {
            throw new RuntimeException("never taken");
        }
        inlined1(i);

        nonEscapingArray[0] = 42;
        return inlined2();
    }

    private static A inlined2() throws CloneNotSupportedException {
        A a = field;
        return (A)a.clone();
    }

    private static void inlined1(int i) {
        if (i > 0) {
            volatileField = 42;
        }
    }

    private static class A implements Cloneable {
        public Object clone() throws CloneNotSupportedException {
            return super.clone();
        }
    }

    private static class B extends A {
        int field1;
        int field2;
        int field3;

        B(int v1, int v2, int v3) {
            field1 = v1;
            field2 = v2;
            field3 = v3;
        }
    }
}
