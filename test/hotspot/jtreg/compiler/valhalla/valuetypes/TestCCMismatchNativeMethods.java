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
import java.util.Random;
import jdk.test.lib.Utils;
import jdk.test.whitebox.WhiteBox;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

/*
 * @test
 * @bug 8395610 8396567
 * @summary native method calls are not scalarized and cause mismatches
 * @enablePreview
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 *
 * @run main/othervm -Xbatch -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:-WhiteBoxAPI ${test.main.class}
 * @run main/othervm -Xbatch  -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:-WhiteBoxAPI
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:ThrowOOMEAtC2iC1NativeEntryPoints=3 ${test.main.class}
 */


public class TestCCMismatchNativeMethods {
    static {
        System.loadLibrary("TestCCMismatchNativeMethods");
    }

    static public int test1(A1 a) {
        return a.m1(myObject.v1, myObject.v1);
    }

    static public int test2(A2 a, V1 v) {
        return a.m1(v, v);
    }

    static public int test3(A1 a) {
        return a.m2().sum();
    }

    static public int test4(A2 a) {
        return a.m2().sum();
    }

    static public int test5(I i, V1 v1) {
        return i.add(v1);
    }
    
    static final Random RAND = Utils.getRandomInstance();

    static class MyClass {
        @NullRestricted
        public V1 v1;

        MyClass() {
            v1 = new V1(RAND.nextInt(), RAND.nextInt(), RAND.nextInt(), RAND.nextInt(), RAND.nextInt(), RAND.nextInt());
            super();
        }
    }
    
    static MyClass myObject = new MyClass();
    
    public static void main(String[] args) {
        A1 a1 = new A1();
        B1 b1 = new B1();
        C1 c1 = new C1();
        A2 a2 = new A2();
        B2 b2 = new B2();
        C2 c2 = new C2();
        boolean oom = false;
        V1 v1 = myObject.v1;
        try {
            for (int i = 0; i < 20_000; i++) {
                if (test1(a1) != v1.sum()) {
                    throw new RuntimeException("incorrect result");
                }
                if (test1(b1) != v1.sum()) {
                    throw new RuntimeException("incorrect result");
                }
                if (test1(c1) != v1.sum()) {
                    throw new RuntimeException("incorrect result");
                }
                if (test2(a2, v1) != v1.sum()) {
                    throw new RuntimeException("incorrect result");
                }
                if (test2(b2, v1) != v1.sum()) {
                    throw new RuntimeException("incorrect result");
                }
                if (test2(c2, v1) != v1.sum()) {
                    throw new RuntimeException("incorrect result");
                }
                if (test3(a1) != v1.sum()) {
                    throw new RuntimeException("incorrect result");
                }
                if (test3(b1) != v1.sum()) {
                    throw new RuntimeException("incorrect result");
                }
                if (test3(c1) != v1.sum()) {
                    throw new RuntimeException("incorrect result");
                }
                if (test4(a2) != v1.sum()) {
                    throw new RuntimeException("incorrect result");
                }
                if (test4(b2) != v1.sum()) {
                    throw new RuntimeException("incorrect result");
                }
                if (test4(c2) != v1.sum()) {
                    throw new RuntimeException("incorrect result");
                }
                if (test5(v1, v1) != v1.sum() * 2) {
                    throw new RuntimeException("incorrect result");
                }
                if (test5(new A3(), v1) != v1.sum() * 2) {
                    throw new RuntimeException("incorrect result");
                }
                if (test5(new A4(), v1) != v1.sum() * 2) {
                    throw new RuntimeException("incorrect result");
                }
            }
            UnloadedClass.loadClassAndRerun();
        } catch (OutOfMemoryError oome) {
            oom = true;
        }
        
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
        V1 v1 = TestCCMismatchNativeMethods.myObject.v1;
        for (int i = 0; i < 20_000; i++) {
            if (TestCCMismatchNativeMethods.test2(a2, v1) != v1.sum()) {
                throw new RuntimeException("incorrect result");
            }
            if (TestCCMismatchNativeMethods.test2(b2, v1) != v1.sum()) {
                throw new RuntimeException("incorrect result");
            }
            if (TestCCMismatchNativeMethods.test2(c2, v1) != v1.sum()) {
                throw new RuntimeException("incorrect result");
            }
            if (TestCCMismatchNativeMethods.test2(d2, v1) != v1.sum()) {
                throw new RuntimeException("incorrect result");
            }
            if (TestCCMismatchNativeMethods.test4(a2) != v1.sum()) {
                throw new RuntimeException("incorrect result");
            }
            if (TestCCMismatchNativeMethods.test4(b2) != v1.sum()) {
                throw new RuntimeException("incorrect result");
            }
            if (TestCCMismatchNativeMethods.test4(c2) != v1.sum()) {
                throw new RuntimeException("incorrect result");
            }
            if (TestCCMismatchNativeMethods.test4(d2) != v1.sum()) {
                throw new RuntimeException("incorrect result");
            }
        }
    }
}

interface I {
    int add(V1 v1);
}

@LooselyConsistentValue
value class V1 implements I {
    int x0;
    int x1;
    int x2;
    int x3;
    int x4;
    int x5;

    V1(int x0, int x1, int x2, int x3, int x4, int x5) {
        this.x0 = x0;
        this.x1 = x1;
        this.x2 = x2;
        this.x3 = x3;
        this.x4 = x4;
        this.x5 = x5;
    }
    
    public int sum() {
        return x0 + x1 + x2 + x3 + x4 + x5;
    }

    public native int add(V1 v1);
}

class A1 {
    static V1 fieldV1 = TestCCMismatchNativeMethods.myObject.v1;

    int m1(V1 v1, V1 v2) {
        return v1.sum();
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
    static V1 fieldV1 = TestCCMismatchNativeMethods.myObject.v1;

    int m1(V1 v1, V1 v2) {
        return v1.sum();
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

class A3 implements I {
    public int add(V1 v1) {
        return TestCCMismatchNativeMethods.myObject.v1.sum() + TestCCMismatchNativeMethods.myObject.v1.sum();
    }
}

class A4 implements I {
    public int add(V1 v1) {
        return TestCCMismatchNativeMethods.myObject.v1.sum() + TestCCMismatchNativeMethods.myObject.v1.sum();
    }
}
