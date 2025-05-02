/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.gcbarriers;

import compiler.lib.ir_framework.*;
import java.lang.invoke.VarHandle;
import java.lang.invoke.MethodHandles;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.concurrent.ThreadLocalRandom;
import jdk.test.lib.Asserts;

/**
 * @test
 * @summary Test that G1 barriers are generated and optimized as expected.
 * @library /test/lib /
 * @requires vm.gc.G1
 * @run driver compiler.gcbarriers.TestG1BarrierGeneration
 */

public class TestG1BarrierGeneration {
    static final String PRE_ONLY = "pre";
    static final String POST_ONLY = "post";
    static final String POST_ONLY_NOT_NULL = "post notnull";
    static final String PRE_AND_POST = "pre post";
    static final String PRE_AND_POST_NOT_NULL = "pre post notnull";
    static final String ANY = ".*";

    static class Outer {
        Object f;
    }

    static class OuterWithVolatileField {
        volatile Object f;
    }

    static class OuterWithFewFields implements Cloneable {
        Object f1;
        Object f2;
        public Object clone() throws CloneNotSupportedException {
            return super.clone();
        }
    }

    static class OuterWithManyFields implements Cloneable {
        Object f1;
        Object f2;
        Object f3;
        Object f4;
        Object f5;
        Object f6;
        Object f7;
        Object f8;
        Object f9;
        Object f10;
        public Object clone() throws CloneNotSupportedException {
            return super.clone();
        }
    }

    static final VarHandle fVarHandle;
    static {
        MethodHandles.Lookup l = MethodHandles.lookup();
        try {
            fVarHandle = l.findVarHandle(Outer.class, "f", Object.class);
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    @DontInline
    static void nonInlinedMethod() {}

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        Scenario[] scenarios = new Scenario[2*2];
        int scenarioIndex = 0;
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                scenarios[scenarioIndex] =
                    new Scenario(scenarioIndex,
                                 "-XX:CompileCommand=inline,java.lang.ref.*::*",
                                 "-XX:" + (i == 0 ? "-" : "+") + "UseCompressedOops",
                                 "-XX:" + (j == 0 ? "-" : "+") + "ReduceInitialCardMarks");
                scenarioIndex++;
            }
        }
        framework.addScenarios(scenarios);
        framework.start();
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    public static void testStore(Outer o, Object o1) {
        o.f = o1;
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, PRE_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_STORE_N_WITH_BARRIER_FLAG, PRE_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    public static void testStoreNull(Outer o) {
        o.f = null;
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, PRE_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_STORE_N_WITH_BARRIER_FLAG, PRE_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    public static void testStoreObfuscatedNull(Outer o, Object o1) {
        Object o2 = o1;
        for (int i = 0; i < 4; i++) {
            if ((i % 2) == 0) {
                o2 = null;
            }
        }
        // o2 is null here, but this is only known to C2 after applying some
        // optimizations (loop unrolling, IGVN).
        o.f = o2;
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, PRE_AND_POST_NOT_NULL, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, PRE_AND_POST_NOT_NULL, "1"},
        phase = CompilePhase.FINAL_CODE)
    public static void testStoreNotNull(Outer o, Object o1) {
        if (o1.hashCode() == 42) {
            return;
        }
        o.f = o1;
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, PRE_AND_POST, "2"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, PRE_AND_POST, "2"},
        phase = CompilePhase.FINAL_CODE)
    public static void testStoreTwice(Outer o, Outer p, Object o1) {
        o.f = o1;
        p.f = o1;
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    public static void testStoreVolatile(OuterWithVolatileField o, Object o1) {
        o.f = o1;
    }

    @Test
    @IR(applyIfAnd = {"UseCompressedOops", "false", "ReduceInitialCardMarks", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, POST_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "true", "ReduceInitialCardMarks", "false"},
        counts = {IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, POST_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "false", "ReduceInitialCardMarks", "true"},
        failOn = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "true", "ReduceInitialCardMarks", "true"},
        failOn = {IRNode.G1_STORE_N_WITH_BARRIER_FLAG, ANY,
                  IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    public static Outer testStoreOnNewObject(Object o1) {
        Outer o = new Outer();
        o.f = o1;
        return o;
    }

    @Test
    @IR(failOn = {IRNode.STORE_P, IRNode.STORE_N},
        phase = CompilePhase.BEFORE_MACRO_EXPANSION)
    public static Outer testStoreNullOnNewObject() {
        Outer o = new Outer();
        o.f = null;
        return o;
    }

    @Test
    @IR(applyIfAnd = {"UseCompressedOops", "false", "ReduceInitialCardMarks", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, POST_ONLY_NOT_NULL, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "true", "ReduceInitialCardMarks", "false"},
        counts = {IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, POST_ONLY_NOT_NULL, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "false", "ReduceInitialCardMarks", "true"},
        failOn = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "true", "ReduceInitialCardMarks", "true"},
        failOn = {IRNode.G1_STORE_N_WITH_BARRIER_FLAG, ANY,
                  IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    public static Outer testStoreNotNullOnNewObject(Object o1) {
        if (o1.hashCode() == 42) {
            return null;
        }
        Outer o = new Outer();
        o.f = o1;
        return o;
    }

    @Test
    @IR(applyIfAnd = {"UseCompressedOops", "false", "ReduceInitialCardMarks", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, POST_ONLY, "2"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "true", "ReduceInitialCardMarks", "false"},
        counts = {IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, POST_ONLY, "2"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "false", "ReduceInitialCardMarks", "true"},
        failOn = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "true", "ReduceInitialCardMarks", "true"},
        failOn = {IRNode.G1_STORE_N_WITH_BARRIER_FLAG, ANY,
                  IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    public static Outer testStoreOnNewObjectInTwoPaths(Object o1, boolean c) {
        Outer o;
        if (c) {
            o = new Outer();
            o.f = o1;
        } else {
            o = new Outer();
            o.f = o1;
        }
        return o;
    }

    @Test
    @IR(applyIfAnd = {"UseCompressedOops", "false", "ReduceInitialCardMarks", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, POST_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "true", "ReduceInitialCardMarks", "false"},
        counts = {IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, POST_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "false", "ReduceInitialCardMarks", "true"},
        failOn = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "true", "ReduceInitialCardMarks", "true"},
        failOn = {IRNode.G1_STORE_N, IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    public static Outer testStoreConditionallyOnNewObject(Object o1, boolean c) {
        Outer o = new Outer();
        if (c) {
            o.f = o1;
        }
        return o;
    }

    @Test
    @IR(applyIfAnd = {"UseCompressedOops", "false", "ReduceInitialCardMarks", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, POST_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "true", "ReduceInitialCardMarks", "false"},
        counts = {IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, POST_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "false", "ReduceInitialCardMarks", "true"},
        failOn = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "true", "ReduceInitialCardMarks", "true"},
        failOn = {IRNode.G1_STORE_N, IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    public static Outer testStoreOnNewObjectAfterException(Object o1, boolean c) throws Exception {
        Outer o = new Outer();
        if (c) {
            throw new Exception("");
        }
        o.f = o1;
        return o;
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    public static Outer testStoreOnNewObjectAfterCall(Object o1) {
        Outer o = new Outer();
        nonInlinedMethod();
        o.f = o1;
        return o;
    }

    @Run(test = {"testStore",
                 "testStoreNull",
                 "testStoreObfuscatedNull",
                 "testStoreNotNull",
                 "testStoreTwice",
                 "testStoreVolatile",
                 "testStoreOnNewObject",
                 "testStoreNullOnNewObject",
                 "testStoreNotNullOnNewObject",
                 "testStoreOnNewObjectInTwoPaths",
                 "testStoreConditionallyOnNewObject",
                 "testStoreOnNewObjectAfterException",
                 "testStoreOnNewObjectAfterCall"})
    public void runStoreTests() {
        {
            Outer o = new Outer();
            Object o1 = new Object();
            testStore(o, o1);
            Asserts.assertEquals(o1, o.f);
        }
        {
            Outer o = new Outer();
            testStoreNull(o);
            Asserts.assertNull(o.f);
        }
        {
            Outer o = new Outer();
            Object o1 = new Object();
            testStoreObfuscatedNull(o, o1);
            Asserts.assertNull(o.f);
        }
        {
            Outer o = new Outer();
            Object o1 = new Object();
            testStoreNotNull(o, o1);
            Asserts.assertEquals(o1, o.f);
        }
        {
            Outer o = new Outer();
            Outer p = new Outer();
            Object o1 = new Object();
            testStoreTwice(o, p, o1);
            Asserts.assertEquals(o1, o.f);
            Asserts.assertEquals(o1, p.f);
        }
        {
            OuterWithVolatileField o = new OuterWithVolatileField();
            Object o1 = new Object();
            testStoreVolatile(o, o1);
            Asserts.assertEquals(o1, o.f);
        }
        {
            Object o1 = new Object();
            Outer o = testStoreOnNewObject(o1);
            Asserts.assertEquals(o1, o.f);
        }
        {
            Outer o = testStoreNullOnNewObject();
            Asserts.assertNull(o.f);
        }
        {
            Object o1 = new Object();
            Outer o = testStoreNotNullOnNewObject(o1);
            Asserts.assertEquals(o1, o.f);
        }
        {
            Object o1 = new Object();
            Outer o = testStoreOnNewObjectInTwoPaths(o1, ThreadLocalRandom.current().nextBoolean());
            Asserts.assertEquals(o1, o.f);
        }
        {
            Object o1 = new Object();
            boolean c = ThreadLocalRandom.current().nextBoolean();
            Outer o = testStoreConditionallyOnNewObject(o1, c);
            Asserts.assertTrue(o.f == (c ? o1 : null));
        }
        {
            Object o1 = new Object();
            boolean c = ThreadLocalRandom.current().nextBoolean();
            try {
                Outer o = testStoreOnNewObjectAfterException(o1, c);
            } catch (Exception e) {}
        }
        {
            Object o1 = new Object();
            Outer o = testStoreOnNewObjectAfterCall(o1);
            Asserts.assertEquals(o1, o.f);
        }
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    public static void testArrayStore(Object[] a, int index, Object o1) {
        a[index] = o1;
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, PRE_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_STORE_N_WITH_BARRIER_FLAG, PRE_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    public static void testArrayStoreNull(Object[] a, int index) {
        a[index] = null;
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, PRE_AND_POST_NOT_NULL, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, PRE_AND_POST_NOT_NULL, "1"},
        phase = CompilePhase.FINAL_CODE)
    public static void testArrayStoreNotNull(Object[] a, int index, Object o1) {
        if (o1.hashCode() == 42) {
            return;
        }
        a[index] = o1;
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, PRE_AND_POST, "2"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, PRE_AND_POST, "2"},
        phase = CompilePhase.FINAL_CODE)
    public static void testArrayStoreTwice(Object[] a, Object[] b, int index, Object o1) {
        a[index] = o1;
        b[index] = o1;
    }

    @Test
    @IR(applyIfAnd = {"UseCompressedOops", "false", "ReduceInitialCardMarks", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, POST_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "true", "ReduceInitialCardMarks", "false"},
        counts = {IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, POST_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "false", "ReduceInitialCardMarks", "true"},
        failOn = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "true", "ReduceInitialCardMarks", "true"},
        failOn = {IRNode.G1_STORE_N_WITH_BARRIER_FLAG, ANY,
                  IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    public static Object[] testStoreOnNewArrayAtKnownIndex(Object o1) {
        Object[] a = new Object[10];
        a[4] = o1;
        return a;
    }

    @Test
    @IR(applyIfAnd = {"UseCompressedOops", "false", "ReduceInitialCardMarks", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, POST_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "true", "ReduceInitialCardMarks", "false"},
        counts = {IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, POST_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "false", "ReduceInitialCardMarks", "true"},
        failOn = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "true", "ReduceInitialCardMarks", "true"},
        failOn = {IRNode.G1_STORE_N_WITH_BARRIER_FLAG, ANY,
                  IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    public static Object[] testStoreOnNewArrayAtUnknownIndex(Object o1, int index) {
        Object[] a = new Object[10];
        a[index] = o1;
        return a;
    }

    @Test
    @IR(failOn = IRNode.SAFEPOINT)
    @IR(applyIfAnd = {"UseCompressedOops", "false", "ReduceInitialCardMarks", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, POST_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "true", "ReduceInitialCardMarks", "false"},
        counts = {IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, POST_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "false", "ReduceInitialCardMarks", "true"},
        failOn = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "true", "ReduceInitialCardMarks", "true"},
        failOn = {IRNode.G1_STORE_N, IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    public static Object[] testStoreAllOnNewSmallArray(Object o1) {
        Object[] a = new Object[64];
        for (int i = 0; i < a.length; i++) {
            a[i] = o1;
        }
        return a;
    }

    @Test
    @IR(counts = {IRNode.SAFEPOINT, "1"})
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    public static Object[] testStoreAllOnNewLargeArray(Object o1) {
        Object[] a = new Object[1024];
        for (int i = 0; i < a.length; i++) {
            a[i] = o1;
        }
        return a;
    }

    @Run(test = {"testArrayStore",
                 "testArrayStoreNull",
                 "testArrayStoreNotNull",
                 "testArrayStoreTwice",
                 "testStoreOnNewArrayAtKnownIndex",
                 "testStoreOnNewArrayAtUnknownIndex",
                 "testStoreAllOnNewSmallArray",
                 "testStoreAllOnNewLargeArray"})
    public void runArrayStoreTests() {
        {
            Object[] a = new Object[10];
            Object o1 = new Object();
            testArrayStore(a, 4, o1);
            Asserts.assertEquals(o1, a[4]);
        }
        {
            Object[] a = new Object[10];
            testArrayStoreNull(a, 4);
            Asserts.assertNull(a[4]);
        }
        {
            Object[] a = new Object[10];
            Object o1 = new Object();
            testArrayStoreNotNull(a, 4, o1);
            Asserts.assertEquals(o1, a[4]);
        }
        {
            Object[] a = new Object[10];
            Object[] b = new Object[10];
            Object o1 = new Object();
            testArrayStoreTwice(a, b, 4, o1);
            Asserts.assertEquals(o1, a[4]);
            Asserts.assertEquals(o1, b[4]);
        }
        {
            Object o1 = new Object();
            Object[] a = testStoreOnNewArrayAtKnownIndex(o1);
            Asserts.assertEquals(o1, a[4]);
        }
        {
            Object o1 = new Object();
            Object[] a = testStoreOnNewArrayAtUnknownIndex(o1, 5);
            Asserts.assertEquals(o1, a[5]);
        }
        {
            Object o1 = new Object();
            Object[] a = testStoreAllOnNewSmallArray(o1);
            for (int i = 0; i < a.length; i++) {
                Asserts.assertEquals(o1, a[i]);
            }
        }
        {
            Object o1 = new Object();
            Object[] a = testStoreAllOnNewLargeArray(o1);
            for (int i = 0; i < a.length; i++) {
                Asserts.assertEquals(o1, a[i]);
            }
        }
    }

    @Test
    public static Object[] testCloneArrayOfObjects(Object[] a) {
        Object[] a1 = null;
        try {
            a1 = a.clone();
        } catch (Exception e) {}
        return a1;
    }

    @Test
    @IR(applyIf = {"ReduceInitialCardMarks", "true"},
        failOn = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, ANY,
                  IRNode.G1_STORE_N_WITH_BARRIER_FLAG, ANY,
                  IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"ReduceInitialCardMarks", "false", "UseCompressedOops", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, POST_ONLY, "2"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"ReduceInitialCardMarks", "false", "UseCompressedOops", "true"},
        counts = {IRNode.G1_STORE_N_WITH_BARRIER_FLAG, POST_ONLY, "2"},
        phase = CompilePhase.FINAL_CODE)
    public static OuterWithFewFields testCloneObjectWithFewFields(OuterWithFewFields o) {
        Object o1 = null;
        try {
            o1 = o.clone();
        } catch (Exception e) {}
        return (OuterWithFewFields)o1;
    }

    @Test
    @IR(applyIf = {"ReduceInitialCardMarks", "true"},
        counts = {IRNode.CALL_OF, "jlong_disjoint_arraycopy", "1"})
    @IR(applyIf = {"ReduceInitialCardMarks", "false"},
        counts = {IRNode.CALL_OF, "G1BarrierSetRuntime::clone", "1"})
    public static OuterWithManyFields testCloneObjectWithManyFields(OuterWithManyFields o) {
        Object o1 = null;
        try {
            o1 = o.clone();
        } catch (Exception e) {}
        return (OuterWithManyFields)o1;
    }

    @Run(test = {"testCloneArrayOfObjects",
                 "testCloneObjectWithFewFields",
                 "testCloneObjectWithManyFields"})
    public void runCloneTests() {
        {
            Object o1 = new Object();
            Object[] a = new Object[4];
            for (int i = 0; i < 4; i++) {
                a[i] = o1;
            }
            Object[] a1 = testCloneArrayOfObjects(a);
            for (int i = 0; i < 4; i++) {
                Asserts.assertEquals(o1, a1[i]);
            }
        }
        {
            Object a = new Object();
            Object b = new Object();
            OuterWithFewFields o = new OuterWithFewFields();
            o.f1 = a;
            o.f2 = b;
            OuterWithFewFields o1 = testCloneObjectWithFewFields(o);
            Asserts.assertEquals(a, o1.f1);
            Asserts.assertEquals(b, o1.f2);
        }
        {
            Object a = new Object();
            Object b = new Object();
            Object c = new Object();
            Object d = new Object();
            Object e = new Object();
            Object f = new Object();
            Object g = new Object();
            Object h = new Object();
            Object i = new Object();
            Object j = new Object();
            OuterWithManyFields o = new OuterWithManyFields();
            o.f1 = a;
            o.f2 = b;
            o.f3 = c;
            o.f4 = d;
            o.f5 = e;
            o.f6 = f;
            o.f7 = g;
            o.f8 = h;
            o.f9 = i;
            o.f10 = j;
            OuterWithManyFields o1 = testCloneObjectWithManyFields(o);
            Asserts.assertEquals(a, o1.f1);
            Asserts.assertEquals(b, o1.f2);
            Asserts.assertEquals(c, o1.f3);
            Asserts.assertEquals(d, o1.f4);
            Asserts.assertEquals(e, o1.f5);
            Asserts.assertEquals(f, o1.f6);
            Asserts.assertEquals(g, o1.f7);
            Asserts.assertEquals(h, o1.f8);
            Asserts.assertEquals(i, o1.f9);
            Asserts.assertEquals(j, o1.f10);
        }
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_COMPARE_AND_EXCHANGE_P_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_COMPARE_AND_EXCHANGE_N_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    static Object testCompareAndExchange(Outer o, Object oldVal, Object newVal) {
        return fVarHandle.compareAndExchange(o, oldVal, newVal);
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_COMPARE_AND_SWAP_P_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_COMPARE_AND_SWAP_N_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    static boolean testCompareAndSwap(Outer o, Object oldVal, Object newVal) {
        return fVarHandle.compareAndSet(o, oldVal, newVal);
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_GET_AND_SET_P_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_GET_AND_SET_N_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    static Object testGetAndSet(Outer o, Object newVal) {
        return fVarHandle.getAndSet(o, newVal);
    }

    // IR checks are disabled for s390 because barriers are not elided (to be investigated).
    @Test
    @IR(applyIfAnd = {"UseCompressedOops", "false", "ReduceInitialCardMarks", "false"},
        applyIfPlatform = {"s390", "false"},
        counts = {IRNode.G1_COMPARE_AND_EXCHANGE_P_WITH_BARRIER_FLAG, POST_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "true", "ReduceInitialCardMarks", "false"},
        applyIfPlatform = {"s390", "false"},
        counts = {IRNode.G1_COMPARE_AND_EXCHANGE_N_WITH_BARRIER_FLAG, POST_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "false", "ReduceInitialCardMarks", "true"},
        applyIfPlatform = {"s390", "false"},
        failOn = {IRNode.G1_COMPARE_AND_EXCHANGE_P_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "true", "ReduceInitialCardMarks", "true"},
        applyIfPlatform = {"s390", "false"},
        failOn = {IRNode.G1_COMPARE_AND_EXCHANGE_N_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    static Object testCompareAndExchangeOnNewObject(Object oldVal, Object newVal) {
        Outer o = new Outer();
        o.f = oldVal;
        return fVarHandle.compareAndExchange(o, oldVal, newVal);
    }

    // IR checks are disabled for s390 when OOPs compression is disabled
    // because barriers are not elided in this configuration (to be investigated).
    @Test
    @IR(applyIfAnd = {"UseCompressedOops", "false", "ReduceInitialCardMarks", "false"},
        applyIfPlatform = {"s390", "false"},
        counts = {IRNode.G1_COMPARE_AND_SWAP_P_WITH_BARRIER_FLAG, POST_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "true", "ReduceInitialCardMarks", "false"},
        counts = {IRNode.G1_COMPARE_AND_SWAP_N_WITH_BARRIER_FLAG, POST_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "false", "ReduceInitialCardMarks", "true"},
        applyIfPlatform = {"s390", "false"},
        failOn = {IRNode.G1_COMPARE_AND_SWAP_P_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "true", "ReduceInitialCardMarks", "true"},
        failOn = {IRNode.G1_COMPARE_AND_SWAP_N_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    static boolean testCompareAndSwapOnNewObject(Object oldVal, Object newVal) {
        Outer o = new Outer();
        o.f = oldVal;
        return fVarHandle.compareAndSet(o, oldVal, newVal);
    }

    @Test
    @IR(applyIfAnd = {"UseCompressedOops", "false", "ReduceInitialCardMarks", "false"},
        counts = {IRNode.G1_GET_AND_SET_P_WITH_BARRIER_FLAG, POST_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "true", "ReduceInitialCardMarks", "false"},
        counts = {IRNode.G1_GET_AND_SET_N_WITH_BARRIER_FLAG, POST_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "false", "ReduceInitialCardMarks", "true"},
        failOn = {IRNode.G1_GET_AND_SET_P_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "true", "ReduceInitialCardMarks", "true"},
        failOn = {IRNode.G1_GET_AND_SET_N_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    static Object testGetAndSetOnNewObject(Object oldVal, Object newVal) {
        Outer o = new Outer();
        o.f = oldVal;
        return fVarHandle.getAndSet(o, newVal);
    }

    @Test
    @IR(applyIfAnd = {"UseCompressedOops", "false", "ReduceInitialCardMarks", "false"},
        counts = {IRNode.G1_GET_AND_SET_P_WITH_BARRIER_FLAG, POST_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "true", "ReduceInitialCardMarks", "false"},
        counts = {IRNode.G1_GET_AND_SET_N_WITH_BARRIER_FLAG, POST_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "false", "ReduceInitialCardMarks", "true"},
        failOn = {IRNode.G1_GET_AND_SET_P_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "true", "ReduceInitialCardMarks", "true"},
        failOn = {IRNode.G1_GET_AND_SET_N_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    static Object testGetAndSetConditionallyOnNewObject(Object oldVal, Object newVal, boolean c) {
        Outer o = new Outer();
        o.f = oldVal;
        if (c) {
            return fVarHandle.getAndSet(o, newVal);
        }
        return oldVal;
    }

    @Test
    @IR(applyIfAnd = {"UseCompressedOops", "false", "ReduceInitialCardMarks", "false"},
        counts = {IRNode.G1_GET_AND_SET_P_WITH_BARRIER_FLAG, POST_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "true", "ReduceInitialCardMarks", "false"},
        counts = {IRNode.G1_GET_AND_SET_N_WITH_BARRIER_FLAG, POST_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "false", "ReduceInitialCardMarks", "true"},
        failOn = {IRNode.G1_GET_AND_SET_P_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIfAnd = {"UseCompressedOops", "true", "ReduceInitialCardMarks", "true"},
        failOn = {IRNode.G1_GET_AND_SET_N_WITH_BARRIER_FLAG, ANY},
        phase = CompilePhase.FINAL_CODE)
    static Object testGetAndSetOnNewObjectAfterException(Object oldVal, Object newVal, boolean c) throws Exception {
        Outer o = new Outer();
        if (c) {
            throw new Exception("");
        }
        o.f = oldVal;
        return fVarHandle.getAndSet(o, newVal);
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_GET_AND_SET_P_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_GET_AND_SET_N_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    static Object testGetAndSetOnNewObjectAfterCall(Object oldVal, Object newVal) {
        Outer o = new Outer();
        nonInlinedMethod();
        o.f = oldVal;
        return fVarHandle.getAndSet(o, newVal);
    }

    @Run(test = {"testCompareAndExchange",
                 "testCompareAndSwap",
                 "testGetAndSet",
                 "testCompareAndExchangeOnNewObject",
                 "testCompareAndSwapOnNewObject",
                 "testGetAndSetOnNewObject",
                 "testGetAndSetConditionallyOnNewObject",
                 "testGetAndSetOnNewObjectAfterException",
                 "testGetAndSetOnNewObjectAfterCall"})
    public void runAtomicTests() {
        {
            Outer o = new Outer();
            Object oldVal = new Object();
            o.f = oldVal;
            Object newVal = new Object();
            Object oldVal2 = testCompareAndExchange(o, oldVal, newVal);
            Asserts.assertEquals(oldVal, oldVal2);
            Asserts.assertEquals(o.f, newVal);
        }
        {
            Outer o = new Outer();
            Object oldVal = new Object();
            o.f = oldVal;
            Object newVal = new Object();
            boolean b = testCompareAndSwap(o, oldVal, newVal);
            Asserts.assertTrue(b);
            Asserts.assertEquals(o.f, newVal);
        }
        {
            Outer o = new Outer();
            Object oldVal = new Object();
            o.f = oldVal;
            Object newVal = new Object();
            Object oldVal2 = testGetAndSet(o, newVal);
            Asserts.assertEquals(oldVal, oldVal2);
            Asserts.assertEquals(o.f, newVal);
        }
        {
            Object oldVal = new Object();
            Object newVal = new Object();
            Object oldVal2 = testCompareAndExchangeOnNewObject(oldVal, newVal);
            Asserts.assertEquals(oldVal, oldVal2);
        }
        {
            Object oldVal = new Object();
            Object newVal = new Object();
            boolean b = testCompareAndSwapOnNewObject(oldVal, newVal);
            Asserts.assertTrue(b);
        }
        {
            Object oldVal = new Object();
            Object newVal = new Object();
            Object oldVal2 = testGetAndSetOnNewObject(oldVal, newVal);
            Asserts.assertEquals(oldVal, oldVal2);
        }
        {
            Object oldVal = new Object();
            Object newVal = new Object();
            boolean c = ThreadLocalRandom.current().nextBoolean();
            Object oldVal2 = testGetAndSetConditionallyOnNewObject(oldVal, newVal, c);
            Asserts.assertEquals(oldVal, oldVal2);
        }
        {
            Object oldVal = new Object();
            Object newVal = new Object();
            boolean c = ThreadLocalRandom.current().nextBoolean();
            try {
                Object oldVal2 = testGetAndSetOnNewObjectAfterException(oldVal, newVal, c);
            } catch (Exception e) {}
        }
        {
            Object oldVal = new Object();
            Object newVal = new Object();
            Object oldVal2 = testGetAndSetOnNewObjectAfterCall(oldVal, newVal);
            Asserts.assertEquals(oldVal, oldVal2);
        }
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_LOAD_P_WITH_BARRIER_FLAG, PRE_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_LOAD_N_WITH_BARRIER_FLAG, PRE_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    static Object testLoadSoftReference(SoftReference<Object> ref) {
        return ref.get();
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_LOAD_P_WITH_BARRIER_FLAG, PRE_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_LOAD_N_WITH_BARRIER_FLAG, PRE_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    static Object testLoadWeakReference(WeakReference<Object> ref) {
        return ref.get();
    }

    @Run(test = {"testLoadSoftReference",
                 "testLoadWeakReference"})
    public void runReferenceTests() {
        {
            Object o1 = new Object();
            SoftReference<Object> sref = new SoftReference<Object>(o1);
            Object o2 = testLoadSoftReference(sref);
            Asserts.assertTrue(o2 == o1 || o2 == null);
        }
        {
            Object o1 = new Object();
            WeakReference<Object> wref = new WeakReference<Object>(o1);
            Object o2 = testLoadWeakReference(wref);
            Asserts.assertTrue(o2 == o1 || o2 == null);
        }
    }
}
