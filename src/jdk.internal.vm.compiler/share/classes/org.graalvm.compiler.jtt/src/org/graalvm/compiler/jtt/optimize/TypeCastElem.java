/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.jtt.optimize;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
public class TypeCastElem extends JTTTest {

    interface Int1 {

        int do1();
    }

    interface Int2 {

        int do2();
    }

    interface Int3 extends Int1 {

        int do3();
    }

    public static class ClassA implements Int1 {

        private int a;

        public ClassA(int a) {
            this.a = a;
        }

        @Override
        public int do1() {
            return a;
        }
    }

    public static class ClassB extends ClassA implements Int2 {

        int b;

        public ClassB(int a, int b) {
            super(a);
            this.b = b;
        }

        @Override
        public int do2() {
            return b;
        }
    }

    public static class ClassC implements Int3 {

        private int a;
        private int b;

        public ClassC(int a, int b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public int do3() {
            return b;
        }

        @Override
        public int do1() {
            return a;
        }

    }

    public static int test1(Object o) {
        if (o instanceof ClassB) {
            ClassB b = (ClassB) o;
            if (o instanceof Int1) {
                return b.b - b.b + 1;
            }
            return 7;
        }
        return 3;
    }

    public static int test2(Object o) {
        Object b = o;
        if (o instanceof ClassB) {
            ClassA a = (ClassA) o;
            if (b instanceof Int1) {
                return ((Int1) a).do1();
            }
            return 7;
        }
        return 3;
    }

    public static int test3(Object o) {
        Object b = o;
        boolean t = o instanceof Int3;
        if (t) {
            Int1 a = (Int1) b;
            return a.do1();
        }
        return 3;
    }

    public static int test(int a, int b, int c) {
        ClassA ca = new ClassA(a);
        ClassB cb = new ClassB(a, b);
        ClassC cc = new ClassC(c, c);
        int sum1 = test1(ca) + test1(cb) * 10 + test1(cc) * 100;
        int sum2 = test2(ca) + test2(cb) * 10 + test2(cc) * 100;
        int sum3 = test3(ca) + test3(cb) * 10 + test3(cc) * 100;
        int result = sum1 * 5 + sum2 * 7 + sum3 * 9;
        return result;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 10, 13, 25);
    }

}
