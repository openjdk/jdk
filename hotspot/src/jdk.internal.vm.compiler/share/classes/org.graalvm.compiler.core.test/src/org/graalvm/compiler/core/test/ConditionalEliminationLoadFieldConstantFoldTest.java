/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import java.lang.reflect.Field;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.IterativeConditionalEliminationPhase;
import org.graalvm.compiler.virtual.phases.ea.EarlyReadEliminationPhase;

import sun.misc.Unsafe;

public class ConditionalEliminationLoadFieldConstantFoldTest extends GraalCompilerTest {
    public static int intSideEffect;

    public static final B FinalField = new B(10);

    private abstract static class A {

    }

    private static class B extends A {
        final int a;

        B(int a) {
            this.a = a;
        }
    }

    private static class C extends A {
        final B b;

        C(B b) {
            this.b = b;
        }
    }

    private static class D extends A {
        final C c;

        D(C c) {
            this.c = c;
        }
    }

    private static class E extends D {
        final Object o;

        E(C c, Object o) {
            super(c);
            this.o = o;
        }
    }

    public static final B CONST_B = new B(10);
    public static final C CONST_C = new C(CONST_B);
    public static final D CONST_D = new D(CONST_C);

    public int testReadConstInBranch(B b) {
        if (b == CONST_B) {
            if (b.a == 5) {
                intSideEffect = b.a;
            } else {
                intSideEffect = 10;
            }
        }
        return 0;
    }

    public int testMultipleReadsConstInBranch(D d) {
        if (d == CONST_D) {
            C c = d.c;
            B b = c.b;
            int res = b.a + 12;
            if (res == 125) {
                intSideEffect = 12;
            }
        }
        return 0;
    }

    public int testLoadFinalInstanceOf(E e) {
        Object o = e.o;
        if (o == CONST_C) {
            if (o instanceof A) {
                // eliminate, implied by a.x == Const(Subclass)
                intSideEffect = 1;
            } else {
                intSideEffect = 10;
            }
        }
        return 0;
    }

    public int testLoadFinalTwiceInstanceOf(E e) {
        if (e.o == CONST_C) {
            if (e.o instanceof A) {
                intSideEffect = 1;
            } else {
                intSideEffect = 10;
            }
        }
        return 0;
    }

    static class C1 {
        final int a;

        C1(int a) {
            this.a = a;
        }
    }

    static class C2 {
        final C1 c1;

        C2(C1 c1) {
            this.c1 = c1;
        }
    }

    public static int foldThatIsNotAllowed(C2 c2) {
        // read before, this will be used to load through when folding
        C1 c1Unknown = c2.c1;

        // be naughty (will be a store field after canonicalization as it has a constant offset, so
        // we would be able to eliminate the inner if after an early read elimination but we would
        // fold before and ce the inner if already)
        //
        // note: if the offset would not be constant but a parameter we would not even be able to
        // remove in inner most if as we cannot rewrite the unsafe store to a store field node as
        // the store might kill ANY_LOCATION
        UNSAFE.putObject(c2, C2_C1_OFFSET, C1_AFTER_READ_CONST);

        if (c2 == C2_CONST) {
            if (c1Unknown == C1_CONST) {
                /*
                 * This if can be eliminated (as we rewrite the unsafe store with a constant offset
                 * to a store field node) but the remaining branch must be the false branch. If we
                 * do not fold through both field loads we will canonicalize the unsafe store to a
                 * store field, see the new value and can thus eliminate the true branch
                 *
                 * if we fold through the load fields we would load from the object read before the
                 * store so we miss the unsafe update
                 */
                if (c2.c1.a == 10) {
                    intSideEffect = 1;
                    return 1;
                } else {
                    intSideEffect = 2;
                    return 2;
                }
            } else {
                intSideEffect = -2;
                return -2;
            }
        } else {
            intSideEffect = -1;
            return -1;
        }
    }

    public int testLoadFinalTwiceNoReadEliminationInstanceOf(E e) {
        if (e.o == CONST_C) {
            /*
             * we cannot eliminate the second read of e.o although it is a final field. the call to
             * System.gc (or any other memory checkpoint killing ANY_LOCATION) will prohibit the
             * elimination of the second load, thus we have two different load nodes, we know that
             * that first load field is a constant but we do not know for the second one, assuming
             * e.o is final, as it might have been written in between
             *
             * this prohibits us to remove the if (fold through all loads to final fields) and the
             * instance of e.o
             */
            System.gc();
            C c = (C) e.o;
            if (c.b.a == 10) {
                intSideEffect = 1;
            } else {
                intSideEffect = 10;
            }
        }
        return 0;

    }

    private static final C1 C1_CONST = new C1(0);
    private static final C2 C2_CONST = new C2(C1_CONST);
    private static final C1 C1_AFTER_READ_CONST = new C1(10);

    private static Unsafe getUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException e) {
        }
        try {
            Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeInstance.setAccessible(true);
            return (Unsafe) theUnsafeInstance.get(Unsafe.class);
        } catch (Exception e) {
            throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e);
        }
    }

    private static final sun.misc.Unsafe UNSAFE = getUnsafe();
    private static final long C2_C1_OFFSET;

    static {
        try {
            Field f = C2.class.getDeclaredField("c1");
            C2_C1_OFFSET = UNSAFE.objectFieldOffset(f);
        } catch (NoSuchFieldException | SecurityException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void test01() {
        checkGraph("testReadConstInBranch", 1);
        test("testReadConstInBranch", new B(1));
    }

    @Test
    public void test02() {
        checkGraph("testMultipleReadsConstInBranch", 1);
    }

    @Test
    public void test03() {
        checkGraph("testLoadFinalInstanceOf", 1);
    }

    @Test
    public void test04() {
        checkGraph("testLoadFinalTwiceInstanceOf", 1);
    }

    @Test
    public void test05() {
        checkGraph("testLoadFinalTwiceNoReadEliminationInstanceOf", 2);
    }

    @Test(expected = AssertionError.class)
    @SuppressWarnings("try")
    public void test06() {
        Result actual = executeActual(getResolvedJavaMethod("foldThatIsNotAllowed"), null, C2_CONST);
        UNSAFE.putObject(C2_CONST, C2_C1_OFFSET, C1_CONST);
        Result expected = executeExpected(getResolvedJavaMethod("foldThatIsNotAllowed"), null, C2_CONST);
        Assert.assertEquals(expected.returnValue, actual.returnValue);
    }

    @SuppressWarnings("try")
    private StructuredGraph checkGraph(String name, int nrOfIfsAfter) {
        StructuredGraph g = parseForCompile(getResolvedJavaMethod(name));
        CanonicalizerPhase c = new CanonicalizerPhase();
        c.apply(g, getDefaultHighTierContext());
        new EarlyReadEliminationPhase(c).apply(g, getDefaultHighTierContext());
        new IterativeConditionalEliminationPhase(c, false).apply(g, getDefaultHighTierContext());
        Assert.assertEquals("Nr of Ifs left does not match", nrOfIfsAfter, g.getNodes().filter(IfNode.class).count());
        c.apply(g, getDefaultHighTierContext());
        return g;
    }

}
