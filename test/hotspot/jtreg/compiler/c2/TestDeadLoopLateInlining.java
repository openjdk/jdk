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
 * @run main ${test.main.class}
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+AlwaysIncrementalInline
 *                   -XX:CompileOnly=${test.main.class}::test* -Xcomp ${test.main.class}
 */

package compiler.c2;

public class TestDeadLoopLateInlining {
    private static Object fieldObject;
    private static int field;
    private static A fieldA = new A();
    private static B fieldB = new B();

    public static void main(String[] args) {
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
                        o = lateInlined1(o);
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
                            o = a.lateInlined(o);
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

    private static Object lateInlined1(Object o) {
        return o;
    }


    static class A {
        Object lateInlined(Object o) {
            return o;
        }
    }

    static class B extends A {
        Object lateInlined(Object o) {
            return o;
        }
    }
}
