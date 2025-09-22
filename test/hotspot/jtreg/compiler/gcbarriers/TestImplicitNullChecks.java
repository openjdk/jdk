/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.Asserts;

/**
 * @test
 * @summary Test that implicit null checks are generated as expected for
            different GC memory accesses.
 * @library /test/lib /
 * @run driver compiler.gcbarriers.TestImplicitNullChecks
 */


public class TestImplicitNullChecks {

    static class Outer {
        Object f;
    }

    static class OuterWithVolatileField {
        volatile Object f;
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

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:CompileCommand=inline,java.lang.ref.*::*",
                                   "-XX:-TieredCompilation");
    }

    @Test
    // On AIX, implicit null checks are limited because the zero page is
    // readable (but not writable). See os::zero_page_read_protected().
    @IR(applyIfPlatform = {"aix", "false"},
        applyIfOr = {"UseZGC", "true", "UseG1GC", "true"},
        counts = {IRNode.NULL_CHECK, "1"},
        phase = CompilePhase.FINAL_CODE)
    static Object testLoad(Outer o) {
        return o.f;
    }

    @Test
    // On aarch64, volatile loads always use indirect memory operands, which
    // leads to a pattern that cannot be exploited by the current C2 analysis.
    // On PPC64, volatile loads are preceded by membar_volatile instructions,
    // which also inhibits the current C2 analysis.
    @IR(applyIfPlatformAnd = {"aarch64", "false", "ppc", "false"},
        applyIfOr = {"UseZGC", "true", "UseG1GC", "true"},
        counts = {IRNode.NULL_CHECK, "1"},
        phase = CompilePhase.FINAL_CODE)
    static Object testLoadVolatile(OuterWithVolatileField o) {
        return o.f;
    }

    @Run(test = {"testLoad",
                 "testLoadVolatile"},
         mode = RunMode.STANDALONE)
    static void runLoadTests() {
        {
            Outer o = new Outer();
            // Trigger compilation with implicit null check.
            for (int i = 0; i < 10_000; i++) {
                testLoad(o);
            }
            // Trigger null pointer exception.
            o = null;
            boolean nullPointerException = false;
            try {
                testLoad(o);
            } catch (NullPointerException e) { nullPointerException = true; }
            Asserts.assertTrue(nullPointerException);
        }
        {
            OuterWithVolatileField o = new OuterWithVolatileField();
            // Trigger compilation with implicit null check.
            for (int i = 0; i < 10_000; i++) {
                testLoadVolatile(o);
            }
            // Trigger null pointer exception.
            o = null;
            boolean nullPointerException = false;
            try {
                testLoadVolatile(o);
            } catch (NullPointerException e) { nullPointerException = true; }
            Asserts.assertTrue(nullPointerException);
        }
    }

    @Test
    // G1 and ZGC stores cannot be currently used to implement implicit null
    // checks, because they expand into multiple memory access instructions that
    // are not necessarily located at the initial instruction start address.
    @IR(applyIfOr = {"UseZGC", "true", "UseG1GC", "true"},
        failOn = IRNode.NULL_CHECK,
        phase = CompilePhase.FINAL_CODE)
    static void testStore(Outer o, Object o1) {
        o.f = o1;
    }

    @Run(test = {"testStore"})
    static void runStoreTests() {
        {
            Outer o = new Outer();
            Object o1 = new Object();
            testStore(o, o1);
        }
    }

    @Test
    // G1 and ZGC compare-and-exchange operations cannot be currently used to
    // implement implicit null checks, because they expand into multiple memory
    // access instructions that are not necessarily located at the initial
    // instruction start address. The same holds for testCompareAndSwap and
    // testGetAndSet below.
    @IR(applyIfOr = {"UseZGC", "true", "UseG1GC", "true"},
        failOn = IRNode.NULL_CHECK,
        phase = CompilePhase.FINAL_CODE)
    static Object testCompareAndExchange(Outer o, Object oldVal, Object newVal) {
        return fVarHandle.compareAndExchange(o, oldVal, newVal);
    }

    @Test
    @IR(applyIfOr = {"UseZGC", "true", "UseG1GC", "true"},
        failOn = IRNode.NULL_CHECK,
        phase = CompilePhase.FINAL_CODE)
    static boolean testCompareAndSwap(Outer o, Object oldVal, Object newVal) {
        return fVarHandle.compareAndSet(o, oldVal, newVal);
    }

    @Test
    @IR(applyIfOr = {"UseZGC", "true", "UseG1GC", "true"},
        failOn = IRNode.NULL_CHECK,
        phase = CompilePhase.FINAL_CODE)
    static Object testGetAndSet(Outer o, Object newVal) {
        return fVarHandle.getAndSet(o, newVal);
    }

    @Run(test = {"testCompareAndExchange",
                 "testCompareAndSwap",
                 "testGetAndSet"})
    static void runAtomicTests() {
        {
            Outer o = new Outer();
            Object oldVal = new Object();
            Object newVal = new Object();
            testCompareAndExchange(o, oldVal, newVal);
        }
        {
            Outer o = new Outer();
            Object oldVal = new Object();
            Object newVal = new Object();
            testCompareAndSwap(o, oldVal, newVal);
        }
        {
            Outer o = new Outer();
            Object oldVal = new Object();
            Object newVal = new Object();
            testGetAndSet(o, newVal);
        }
    }

    @Test
    // G1 reference loads use indirect memory operands, which leads to a pattern
    // that cannot be exploited by the current C2 analysis. The same holds for
    // testLoadWeakReference.
    @IR(applyIf = {"UseZGC", "true"},
        counts = {IRNode.NULL_CHECK, "1"},
        phase = CompilePhase.FINAL_CODE)
    static Object testLoadSoftReference(SoftReference<Object> ref) {
        return ref.get();
    }

    @Test
    @IR(applyIf = {"UseZGC", "true"},
        counts = {IRNode.NULL_CHECK, "1"},
        phase = CompilePhase.FINAL_CODE)
    static Object testLoadWeakReference(WeakReference<Object> ref) {
        return ref.get();
    }

    @Run(test = {"testLoadSoftReference",
                 "testLoadWeakReference"})
    static void runReferenceTests() {
        {
            Object o1 = new Object();
            SoftReference<Object> sref = new SoftReference<Object>(o1);
            Object o2 = testLoadSoftReference(sref);
        }
        {
            Object o1 = new Object();
            WeakReference<Object> wref = new WeakReference<Object>(o1);
            Object o2 = testLoadWeakReference(wref);
        }
    }

}
