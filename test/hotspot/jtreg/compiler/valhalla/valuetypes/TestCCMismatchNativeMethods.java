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

package compiler.valhalla.valuetypes;

/*
 * @test
 * @bug 8395610
 * @summary native method calls are not scalarized and cause mismatches
 * @enablePreview
 * @run main/othervm -Xbatch ${test.main.class}
 */


public class TestCCMismatchNativeMethods {
    static {
        System.loadLibrary("TestCCMismatchNativeMethods");
    }

    static public int test1(A1 a, V1 v) {
        return a.m1(v, v);
    }

    static public int test2(A2 a, V1 v) {
        return a.m1(v, v);
    }

    static public int test3(A1 a) {
        return a.m2().x();
    }
    
    static public int test4(A2 a) {
        return a.m2().x();
    }

    public static void main(String[] args) {
        A1 a1 = new A1();
        B1 b1 = new B1();
        C1 c1 = new C1();
        A2 a2 = new A2();
        B2 b2 = new B2();
        C2 c2 = new C2();
        for (int i = 0; i < 20_000; i++) {
            if (test1(a1, new V1(42)) != 42) {
                throw new RuntimeException("incorrect result");
            }
            if (test1(b1, new V1(42)) != 42) {
                throw new RuntimeException("incorrect result");
            }
            if (test1(c1, new V1(42)) != 42) {
                throw new RuntimeException("incorrect result");
            }
            if (test2(a2, new V1(42)) != 42) {
                throw new RuntimeException("incorrect result");
            }
            if (test2(b2, new V1(42)) != 42) {
                throw new RuntimeException("incorrect result");
            }
            if (test2(c2, new V1(42)) != 42) {
                throw new RuntimeException("incorrect result");
            }
            if (test3(a1) != 42) {
                throw new RuntimeException("incorrect result");
            }
            if (test3(b1) != 42) {
                throw new RuntimeException("incorrect result");
            }
            if (test3(c1) != 42) {
                throw new RuntimeException("incorrect result");
            }
            if (test4(a2) != 42) {
                throw new RuntimeException("incorrect result");
            }
            if (test4(b2) != 42) {
                throw new RuntimeException("incorrect result");
            }
            if (test4(c2) != 42) {
                throw new RuntimeException("incorrect result");
            }
        }
        UnloadedClass.loadClassAndRerun();
    }
}

class UnloadedClass {
    public static void loadClassAndRerun() {
        A1 a1 = new A1();
        B1 b1 = new B1();
        C1 c1 = new C1();
        A2 a2 = new A2();
        B2 b2 = new B2();
        C2 c2 = new C2();
        D2 d2 = new D2();
        for (int i = 0; i < 20_000; i++) {
            if (TestCCMismatchNativeMethods.test2(a2, new V1(42)) != 42) {
                throw new RuntimeException("incorrect result");
            }
            if (TestCCMismatchNativeMethods.test2(b2, new V1(42)) != 42) {
                throw new RuntimeException("incorrect result");
            }
            if (TestCCMismatchNativeMethods.test2(c2, new V1(42)) != 42) {
                throw new RuntimeException("incorrect result");
            }
            if (TestCCMismatchNativeMethods.test2(d2, new V1(42)) != 42) {
                throw new RuntimeException("incorrect result");
            }
            if (TestCCMismatchNativeMethods.test4(a2) != 42) {
                throw new RuntimeException("incorrect result");
            }
            if (TestCCMismatchNativeMethods.test4(b2) != 42) {
                throw new RuntimeException("incorrect result");
            }
            if (TestCCMismatchNativeMethods.test4(c2) != 42) {
                throw new RuntimeException("incorrect result");
            }
            if (TestCCMismatchNativeMethods.test4(d2) != 42) {
                throw new RuntimeException("incorrect result");
            }
        }
    }
}


value record V1(int x) { }

class A1 {
    static V1 fieldV1 = new V1(42);
    
    int m1(V1 v1, V1 v2) {
        return v1.x();
    }
    
    V1 m2() {
        return fieldV1;
    }
}

class B1 extends A1 {
    native int m1(V1 v1, V1 v2);
    native V1 m2();
}

class C1 extends A1 {
}

class A2 {
    static V1 fieldV1 = new V1(42);
    
    int m1(V1 v1, V1 v2) {
        return v1.x();
    }
    
    V1 m2() {
        return fieldV1;
    }
}

class B2 extends A2 {
}

class C2 extends A2 {
}

class D2 extends A2 {
    native int m1(V1 v1, V1 v2);
    native V1 m2();
}

    
