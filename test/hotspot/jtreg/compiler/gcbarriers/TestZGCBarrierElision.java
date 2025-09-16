/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * @test
 * @summary Test that the ZGC barrier elision optimization does not elide
 *          necessary barriers. The tests use volatile memory accesses and
 *          blackholes to prevent C2 from simply optimizing them away.
 * @library /test/lib /
 * @requires vm.gc.Z
 * @run driver compiler.gcbarriers.TestZGCBarrierElision test-correctness
 */

/**
 * @test
 * @summary Test that the ZGC barrier elision optimization elides unnecessary
 *          barriers following simple allocation and domination rules.
 * @library /test/lib /
 * @requires vm.gc.Z & (vm.simpleArch == "x64" | vm.simpleArch == "aarch64")
 * @run driver compiler.gcbarriers.TestZGCBarrierElision test-effectiveness
 */

class Inner {}

class Outer {
    volatile Inner field1;
    volatile Inner field2;
    Outer() {}
}

class Common {

    static Inner inner = new Inner();
    static Outer outer = new Outer();
    static Outer outer2 = new Outer();
    static Outer[] outerArray = new Outer[42];

    static final VarHandle field1VarHandle;
    static final VarHandle field2VarHandle;
    static {
        MethodHandles.Lookup l = MethodHandles.lookup();
        try {
            field1VarHandle = l.findVarHandle(Outer.class, "field1", Inner.class);
            field2VarHandle = l.findVarHandle(Outer.class, "field2", Inner.class);
        } catch (Exception e) {
            throw new Error(e);
        }
    }
    static final VarHandle outerArrayVarHandle =
        MethodHandles.arrayElementVarHandle(Outer[].class);

    static final String REMAINING = "strong";
    static final String ELIDED = "elided";

    static void blackhole(Object o) {}
    static void nonInlinedMethod() {}
}

public class TestZGCBarrierElision {

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException();
        }
        Class testClass;
        if (args[0].equals("test-correctness")) {
            testClass = TestZGCCorrectBarrierElision.class;
        } else if (args[0].equals("test-effectiveness")) {
            testClass = TestZGCEffectiveBarrierElision.class;
        } else {
            throw new IllegalArgumentException();
        }
        String commonName = Common.class.getName();
        TestFramework test = new TestFramework(testClass);
        test.addFlags("-XX:+UseZGC", "-XX:+UnlockExperimentalVMOptions",
                      "-XX:CompileCommand=blackhole," + commonName + "::blackhole",
                      "-XX:CompileCommand=dontinline," + commonName + "::nonInlinedMethod",
                      "-XX:LoopMaxUnroll=0");
        test.start();
    }
}

class TestZGCCorrectBarrierElision {

    @Test
    @IR(counts = { IRNode.Z_STORE_P_WITH_BARRIER_FLAG, Common.REMAINING, "1" }, phase = CompilePhase.FINAL_CODE)
    @IR(counts = { IRNode.Z_LOAD_P_WITH_BARRIER_FLAG, Common.REMAINING, "1" },  phase = CompilePhase.FINAL_CODE)
    static void testLoadThenStore(Outer o, Inner i) {
        Common.blackhole(o.field1);
        o.field1 = i;
    }

    @Test
    @IR(counts = { IRNode.Z_LOAD_P_WITH_BARRIER_FLAG, Common.REMAINING, "2" }, phase = CompilePhase.FINAL_CODE)
    static void testLoadThenLoadAnotherField(Outer o) {
        Common.blackhole(o.field1);
        Common.blackhole(o.field2);
    }

    @Test
    @IR(counts = { IRNode.Z_LOAD_P_WITH_BARRIER_FLAG, Common.REMAINING, "2" }, phase = CompilePhase.FINAL_CODE)
    static void testLoadThenLoadFromAnotherObject(Outer o1, Outer o2) {
        Common.blackhole(o1.field1);
        Common.blackhole(o2.field1);
    }

    @Run(test = {"testLoadThenStore",
                 "testLoadThenLoadAnotherField",
                 "testLoadThenLoadFromAnotherObject"})
    void runBasicTests() {
        testLoadThenStore(Common.outer, Common.inner);
        testLoadThenLoadAnotherField(Common.outer);
        testLoadThenLoadFromAnotherObject(Common.outer, Common.outer2);
    }

    @Test
    @IR(counts = { IRNode.Z_LOAD_P_WITH_BARRIER_FLAG, Common.REMAINING, "1" }, phase = CompilePhase.FINAL_CODE)
    @IR(counts = { IRNode.Z_STORE_P_WITH_BARRIER_FLAG, Common.REMAINING, "1" }, phase = CompilePhase.FINAL_CODE)
    static void testArrayLoadThenStore(Outer[] a, Outer o) {
        Common.blackhole(Common.outerArrayVarHandle.getVolatile(a, 0));
        Common.outerArrayVarHandle.setVolatile(a, 0, o);
    }

    @Test
    @IR(counts = { IRNode.Z_LOAD_P_WITH_BARRIER_FLAG, Common.REMAINING, "2" }, phase = CompilePhase.FINAL_CODE)
    static void testArrayLoadThenLoadAnotherElement(Outer[] a) {
        Common.blackhole(Common.outerArrayVarHandle.getVolatile(a, 0));
        Common.blackhole(Common.outerArrayVarHandle.getVolatile(a, 10));
    }

    @Run(test = {"testArrayLoadThenStore",
                 "testArrayLoadThenLoadAnotherElement"})
    void runArrayTests() {
        testArrayLoadThenStore(Common.outerArray, Common.outer);
        testArrayLoadThenLoadAnotherElement(Common.outerArray);
    }

    @Test
    @IR(counts = { IRNode.Z_STORE_P_WITH_BARRIER_FLAG, Common.REMAINING, "2" }, phase = CompilePhase.FINAL_CODE)
    static void testConditionalStoreThenStore(Outer o, Inner i, int value) {
        if (value % 2 == 0) {
            o.field1 = i;
        }
        o.field1 = i;
    }

    @Test
    @IR(counts = { IRNode.Z_STORE_P_WITH_BARRIER_FLAG, Common.REMAINING, "2" }, phase = CompilePhase.FINAL_CODE)
    static void testStoreThenCallThenStore(Outer o, Inner i) {
        o.field1 = i;
        Common.nonInlinedMethod();
        o.field1 = i;
    }

    @Run(test = {"testConditionalStoreThenStore",
                 "testStoreThenCallThenStore"})
    void runControlFlowTests() {
        testConditionalStoreThenStore(Common.outer, Common.inner, ThreadLocalRandom.current().nextInt(0, 100));
        testStoreThenCallThenStore(Common.outer, Common.inner);
    }

    @Test
    @IR(counts = { IRNode.Z_GET_AND_SET_P_WITH_BARRIER_FLAG, Common.REMAINING, "1" }, phase = CompilePhase.FINAL_CODE)
    static void testAllocateThenAtomic(Inner i) {
        Outer o = new Outer();
        Common.blackhole(o);
        Common.field1VarHandle.getAndSet(o, i);
    }

    @Test
    @IR(counts = { IRNode.Z_LOAD_P_WITH_BARRIER_FLAG, Common.REMAINING, "1" }, phase = CompilePhase.FINAL_CODE)
    @IR(counts = { IRNode.Z_GET_AND_SET_P_WITH_BARRIER_FLAG, Common.REMAINING, "1" }, phase = CompilePhase.FINAL_CODE)
    static void testLoadThenAtomic(Outer o, Inner i) {
        Common.blackhole(o.field1);
        Common.field1VarHandle.getAndSet(o, i);
    }

    @Test
    @IR(counts = { IRNode.Z_GET_AND_SET_P_WITH_BARRIER_FLAG, Common.REMAINING, "2" }, phase = CompilePhase.FINAL_CODE)
    static void testAtomicThenAtomicAnotherField(Outer o, Inner i) {
        Common.field1VarHandle.getAndSet(o, i);
        Common.field2VarHandle.getAndSet(o, i);
    }

    @Test
    @IR(counts = { IRNode.Z_GET_AND_SET_P_WITH_BARRIER_FLAG, Common.REMAINING, "1" }, phase = CompilePhase.FINAL_CODE)
    static void testAllocateArrayThenAtomicAtKnownIndex(Outer o) {
        Outer[] a = new Outer[42];
        Common.blackhole(a);
        Common.outerArrayVarHandle.getAndSet(a, 2, o);
    }

    @Test
    @IR(counts = { IRNode.Z_GET_AND_SET_P_WITH_BARRIER_FLAG, Common.REMAINING, "1" }, phase = CompilePhase.FINAL_CODE)
    static void testAllocateArrayThenAtomicAtUnknownIndex(Outer o, int index) {
        Outer[] a = new Outer[42];
        Common.blackhole(a);
        Common.outerArrayVarHandle.getAndSet(a, index, o);
    }

    @Test
    @IR(counts = { IRNode.Z_GET_AND_SET_P_WITH_BARRIER_FLAG, Common.REMAINING, "2" }, phase = CompilePhase.FINAL_CODE)
    static void testArrayAtomicThenAtomicAtUnknownIndices(Outer[] a, Outer o, int index1, int index2) {
        Common.outerArrayVarHandle.getAndSet(a, index1, o);
        Common.outerArrayVarHandle.getAndSet(a, index2, o);
    }

    @Run(test = {"testAllocateThenAtomic",
                 "testLoadThenAtomic",
                 "testAtomicThenAtomicAnotherField",
                 "testAllocateArrayThenAtomicAtKnownIndex",
                 "testAllocateArrayThenAtomicAtUnknownIndex",
                 "testArrayAtomicThenAtomicAtUnknownIndices"})
    void runAtomicOperationTests() {
        testAllocateThenAtomic(Common.inner);
        testLoadThenAtomic(Common.outer, Common.inner);
        testAtomicThenAtomicAnotherField(Common.outer, Common.inner);
        testAllocateArrayThenAtomicAtKnownIndex(Common.outer);
        testAllocateArrayThenAtomicAtUnknownIndex(Common.outer, 10);
        testArrayAtomicThenAtomicAtUnknownIndices(Common.outerArray, Common.outer, 10, 20);
    }
}

class TestZGCEffectiveBarrierElision {

    @Test
    @IR(counts = { IRNode.Z_LOAD_P_WITH_BARRIER_FLAG, Common.ELIDED, "1" }, phase = CompilePhase.FINAL_CODE)
    static void testAllocateThenLoad() {
        Outer o1 = new Outer();
        Common.blackhole(o1);
        // This load is directly optimized away by C2.
        Common.blackhole(o1.field1);
        Common.blackhole(o1.field1);
    }

    @Test
    @IR(counts = { IRNode.Z_STORE_P_WITH_BARRIER_FLAG, Common.ELIDED, "1" }, phase = CompilePhase.FINAL_CODE)
    static void testAllocateThenStore(Inner i) {
        Outer o1 = new Outer();
        Common.blackhole(o1);
        o1.field1 = i;
    }

    @Test
    @IR(counts = { IRNode.Z_LOAD_P_WITH_BARRIER_FLAG, Common.REMAINING, "1" }, phase = CompilePhase.FINAL_CODE)
    @IR(counts = { IRNode.Z_LOAD_P_WITH_BARRIER_FLAG, Common.ELIDED, "1" }, phase = CompilePhase.FINAL_CODE)
    static void testLoadThenLoad(Outer o) {
        Common.blackhole(o.field1);
        Common.blackhole(o.field1);
    }

    @Test
    @IR(counts = { IRNode.Z_STORE_P_WITH_BARRIER_FLAG, Common.REMAINING, "1" }, phase = CompilePhase.FINAL_CODE)
    @IR(counts = { IRNode.Z_STORE_P_WITH_BARRIER_FLAG, Common.ELIDED, "1" }, phase = CompilePhase.FINAL_CODE)
    static void testStoreThenStore(Outer o, Inner i) {
        o.field1 = i;
        o.field1 = i;
    }

    @Test
    @IR(counts = { IRNode.Z_STORE_P_WITH_BARRIER_FLAG, Common.REMAINING, "1" }, phase = CompilePhase.FINAL_CODE)
    @IR(counts = { IRNode.Z_LOAD_P_WITH_BARRIER_FLAG, Common.ELIDED, "1" },  phase = CompilePhase.FINAL_CODE)
    static void testStoreThenLoad(Outer o, Inner i) {
        o.field1 = i;
        Common.blackhole(o.field1);
    }

    @Run(test = {"testAllocateThenLoad",
                 "testAllocateThenStore",
                 "testLoadThenLoad",
                 "testStoreThenStore",
                 "testStoreThenLoad"})
    void runBasicTests() {
        testAllocateThenLoad();
        testAllocateThenStore(Common.inner);
        testLoadThenLoad(Common.outer);
        testStoreThenStore(Common.outer, Common.inner);
        testStoreThenLoad(Common.outer, Common.inner);
    }

    @Test
    @IR(counts = { IRNode.Z_STORE_P_WITH_BARRIER_FLAG, Common.ELIDED, "1" }, phase = CompilePhase.FINAL_CODE)
    static void testAllocateArrayThenStoreAtKnownIndex(Outer o) {
        Outer[] a = new Outer[42];
        Common.blackhole(a);
        Common.outerArrayVarHandle.setVolatile(a, 0, o);
    }

    @Test
    @IR(counts = { IRNode.Z_STORE_P_WITH_BARRIER_FLAG, Common.ELIDED, "1" }, phase = CompilePhase.FINAL_CODE)
    static void testAllocateArrayThenStoreAtUnknownIndex(Outer o, int index) {
        Outer[] a = new Outer[42];
        Common.blackhole(a);
        Common.outerArrayVarHandle.setVolatile(a, index, o);
    }

    @Test
    @IR(counts = { IRNode.Z_LOAD_P_WITH_BARRIER_FLAG, Common.REMAINING, "1" }, phase = CompilePhase.FINAL_CODE)
    @IR(counts = { IRNode.Z_LOAD_P_WITH_BARRIER_FLAG, Common.ELIDED, "1" }, phase = CompilePhase.FINAL_CODE)
    static void testArrayLoadThenLoad(Outer[] a) {
        Common.blackhole(Common.outerArrayVarHandle.getVolatile(a, 0));
        Common.blackhole(Common.outerArrayVarHandle.getVolatile(a, 0));
    }

    @Test
    @IR(counts = { IRNode.Z_STORE_P_WITH_BARRIER_FLAG, Common.REMAINING, "1" }, phase = CompilePhase.FINAL_CODE)
    @IR(counts = { IRNode.Z_STORE_P_WITH_BARRIER_FLAG, Common.ELIDED, "1" }, phase = CompilePhase.FINAL_CODE)
    static void testArrayStoreThenStore(Outer[] a, Outer o) {
        Common.outerArrayVarHandle.setVolatile(a, 0, o);
        Common.outerArrayVarHandle.setVolatile(a, 0, o);
    }

    @Test
    @IR(counts = { IRNode.Z_STORE_P_WITH_BARRIER_FLAG, Common.REMAINING, "1" }, phase = CompilePhase.FINAL_CODE)
    @IR(counts = { IRNode.Z_LOAD_P_WITH_BARRIER_FLAG, Common.ELIDED, "1" }, phase = CompilePhase.FINAL_CODE)
    static void testArrayStoreThenLoad(Outer[] a, Outer o) {
        Common.outerArrayVarHandle.setVolatile(a, 0, o);
        Common.blackhole(Common.outerArrayVarHandle.getVolatile(a, 0));
    }

    @Run(test = {"testAllocateArrayThenStoreAtKnownIndex",
                 "testAllocateArrayThenStoreAtUnknownIndex",
                 "testArrayLoadThenLoad",
                 "testArrayStoreThenStore",
                 "testArrayStoreThenLoad"})
    void runArrayTests() {
        testAllocateArrayThenStoreAtKnownIndex(Common.outer);
        testAllocateArrayThenStoreAtUnknownIndex(Common.outer, 10);
        testArrayLoadThenLoad(Common.outerArray);
        testArrayStoreThenStore(Common.outerArray, Common.outer);
        testArrayStoreThenLoad(Common.outerArray, Common.outer);
    }

    @Test
    @IR(counts = { IRNode.Z_STORE_P_WITH_BARRIER_FLAG, Common.REMAINING, "1" }, phase = CompilePhase.FINAL_CODE)
    @IR(counts = { IRNode.Z_STORE_P_WITH_BARRIER_FLAG, Common.ELIDED, "1" }, phase = CompilePhase.FINAL_CODE)
    static void testStoreThenConditionalStore(Outer o, Inner i, int value) {
        o.field1 = i;
        if (value % 2 == 0) {
            o.field1 = i;
        }
    }

    @Test
    @IR(counts = { IRNode.Z_STORE_P_WITH_BARRIER_FLAG, Common.REMAINING, "1" }, phase = CompilePhase.FINAL_CODE)
    @IR(counts = { IRNode.Z_STORE_P_WITH_BARRIER_FLAG, Common.ELIDED, "1" }, phase = CompilePhase.FINAL_CODE)
    static void testStoreThenStoreInLoop(Outer o, Inner i) {
        o.field1 = i;
        for (int j = 0; j < 100; j++) {
            o.field1 = i;
        }
    }

    @Run(test = {"testStoreThenConditionalStore",
                 "testStoreThenStoreInLoop"})
    void runControlFlowTests() {
        testStoreThenConditionalStore(Common.outer, Common.inner, ThreadLocalRandom.current().nextInt(0, 100));
        testStoreThenStoreInLoop(Common.outer, Common.inner);
    }

    @Test
    @IR(counts = { IRNode.Z_STORE_P_WITH_BARRIER_FLAG, Common.REMAINING, "1" }, phase = CompilePhase.FINAL_CODE)
    @IR(counts = { IRNode.Z_GET_AND_SET_P_WITH_BARRIER_FLAG, Common.ELIDED, "1" }, phase = CompilePhase.FINAL_CODE)
    static void testStoreThenAtomic(Outer o, Inner i) {
        o.field1 = i;
        Common.field1VarHandle.getAndSet(o, i);
    }

    @Test
    @IR(counts = { IRNode.Z_GET_AND_SET_P_WITH_BARRIER_FLAG, Common.REMAINING, "1" }, phase = CompilePhase.FINAL_CODE)
    @IR(counts = { IRNode.Z_LOAD_P_WITH_BARRIER_FLAG, Common.ELIDED, "1" }, phase = CompilePhase.FINAL_CODE)
    static void testAtomicThenLoad(Outer o, Inner i) {
        Common.field1VarHandle.getAndSet(o, i);
        Common.blackhole(o.field1);
    }

    @Test
    @IR(counts = { IRNode.Z_GET_AND_SET_P_WITH_BARRIER_FLAG, Common.REMAINING, "1" }, phase = CompilePhase.FINAL_CODE)
    @IR(counts = { IRNode.Z_STORE_P_WITH_BARRIER_FLAG, Common.ELIDED, "1" }, phase = CompilePhase.FINAL_CODE)
    static void testAtomicThenStore(Outer o, Inner i) {
        Common.field1VarHandle.getAndSet(o, i);
        o.field1 = i;
    }

    @Test
    @IR(counts = { IRNode.Z_GET_AND_SET_P_WITH_BARRIER_FLAG, Common.REMAINING, "1" }, phase = CompilePhase.FINAL_CODE)
    @IR(counts = { IRNode.Z_GET_AND_SET_P_WITH_BARRIER_FLAG, Common.ELIDED, "1" }, phase = CompilePhase.FINAL_CODE)
    static void testAtomicThenAtomic(Outer o, Inner i) {
        Common.field1VarHandle.getAndSet(o, i);
        Common.field1VarHandle.getAndSet(o, i);
    }

    @Test
    @IR(counts = { IRNode.Z_GET_AND_SET_P_WITH_BARRIER_FLAG, Common.REMAINING, "1" }, phase = CompilePhase.FINAL_CODE)
    @IR(counts = { IRNode.Z_GET_AND_SET_P_WITH_BARRIER_FLAG, Common.ELIDED, "1" }, phase = CompilePhase.FINAL_CODE)
    static void testArrayAtomicThenAtomic(Outer[] a, Outer o) {
        Common.outerArrayVarHandle.getAndSet(a, 0, o);
        Common.outerArrayVarHandle.getAndSet(a, 0, o);
    }

    @Run(test = {"testStoreThenAtomic",
                 "testAtomicThenLoad",
                 "testAtomicThenStore",
                 "testAtomicThenAtomic",
                 "testArrayAtomicThenAtomic"})
    void runAtomicOperationTests() {
        testStoreThenAtomic(Common.outer, Common.inner);
        testAtomicThenLoad(Common.outer, Common.inner);
        testAtomicThenStore(Common.outer, Common.inner);
        testAtomicThenAtomic(Common.outer, Common.inner);
        testArrayAtomicThenAtomic(Common.outerArray, Common.outer);
    }
}
