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

/**
 * @test
 * @bug 8375694
 * @summary C2: Dead loop constructed with CastPP in late inlining
 * @enablePreview
 * @modules java.base/jdk.internal.vm.annotation
 * @run main ${test.main.class}
 * @run main/othervm -XX:CompileCommand=option,${test.main.class}::lateInlined2,delayinline
 *                   -XX:CompileCommand=option,${test.main.class}::lateInlined1,delayinline
 *                   -XX:CompileCommand=option,${test.main.class}$A::lateInlined,delayinline
 *                   -XX:CompileOnly=${test.main.class}::test* -Xcomp ${test.main.class}
 */

package compiler.c2;
import jdk.internal.vm.annotation.NullRestricted;

public class TestDeadLoopLateInliningWithValues {
    private static Object fieldObject;
    private static int field;
    private static A fieldA = new A();
    private static B fieldB = new B();

    static value class MyValue {
        Object o;

        MyValue(Object o) {
            this.o = o;
        }
    }

    public static void main(String[] args) {
        MyValue v = new MyValue(null);
        test1(0, true);
        test2(0, 0, true);
    }

    private static Object test1(int j, boolean flag) {
        if (j < 42) {
            if (flag) {
                field = 42;
            }
            Object o = fieldObject;
            if (j >= 42) {
                for (int i = 1; i < 10; ) {
                    boolean boolRes = lateInlined2();
                    if (boolRes) {
                        i *= 2;
                        MyValue v = new MyValue(o);
                        o = lateInlined1(v).o;
                        if (o == null) {
                            throw new RuntimeException();
                        }
                    } else {
                        i++;
                    }
                }
            }
            return o;
        }
        return null;
    }

    static final MyValue[] valueArray = new MyValue[1];

    private static Object test2(int j, int k, boolean flag) {
        A a;
        if (k < 42) {
            if (flag) {
                field = 42;
            }
            if (k >= 42) {
                a = fieldA;
            } else {
                a = fieldB;
            }
            if (a == null) {
                throw new RuntimeException("never taken");
            }
            if (j < 42) {
                if (flag) {
                    field = 42;
                }
                Object o = fieldObject;
                if (j >= 42) {
                    for (int i = 1; i < 10; ) {
                        boolean boolRes = lateInlined2();
                        if (boolRes) {
                            i *= 2;
                            MyValue v = new MyValue(o);
                            o = a.lateInlined(v).o;
                            if (o == null) {
                                throw new RuntimeException();
                            }
                        } else {
                            i++;
                        }
                    }
                }
                return o;
            }
        }
        return null;
    }

    private static boolean lateInlined2() {
        return true;
    }

    private static MyValue lateInlined1(MyValue o) {
        return o;
    }


    static class A {
        MyValue lateInlined(MyValue v) {
            return v;
        }
    }

    static class B extends A {
        MyValue lateInlined(MyValue v) {
            return v;
        }
    }
}
