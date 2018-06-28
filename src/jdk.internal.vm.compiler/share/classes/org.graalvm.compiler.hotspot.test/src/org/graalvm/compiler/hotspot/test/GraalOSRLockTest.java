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


package org.graalvm.compiler.hotspot.test;

import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import jdk.internal.vm.compiler.collections.EconomicMap;
import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.phases.HighTier;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.hotspot.phases.OnStackReplacementPhase;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Test on-stack-replacement with locks.
 */
public class GraalOSRLockTest extends GraalOSRTestBase {

    private static boolean TestInSeparateThread = false;
    private static final String COMPILE_ONLY_FLAG = "-Xcomp";

    @BeforeClass
    public static void checkVMArguments() {
        /*
         * Note: The -Xcomp execution mode of the VM will stop most of the OSR test cases from
         * working as every method is compiled at level3 (followed by level4 on the second
         * invocation). The tests in this class are written in a way that they expect a method to be
         * executed at the invocation BCI with the interpreter and then perform an OSR to an
         * installed nmethod at a given BCI.
         *
         */
        List<String> arguments = GraalServices.getInputArguments();
        Assume.assumeTrue("cannot check for monitors without", arguments != null);
        for (String arg : arguments) {
            Assume.assumeFalse(arg.equals(COMPILE_ONLY_FLAG));
        }
    }

    // testing only
    public static boolean isMonitorLockHeld(Object o) {
        return isMonitorLockHeldByThread(o, null);
    }

    public static boolean isMonitorLockHeldByThread(Object o, Thread t) {
        int oihc = System.identityHashCode(o);
        ThreadMXBean tmxbean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] tinfos = tmxbean.dumpAllThreads(true, false);

        for (ThreadInfo ti : tinfos) {
            if (!(t != null && t.getId() != ti.getThreadId())) {
                for (MonitorInfo mi : ti.getLockedMonitors()) {
                    if (mi.getIdentityHashCode() == oihc) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    protected static void run(Runnable r) {
        if (TestInSeparateThread) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    beforeOSRLockTest();
                    r.run();
                    afterOSRLockTest();
                }
            });
            t.start();
            try {
                t.join();
            } catch (Throwable t1) {
                throw new GraalError(t1);
            }
        } else {
            beforeOSRLockTest();
            r.run();
            afterOSRLockTest();
        }
    }

    private static boolean wasLocked() {
        return isMonitorLockHeld(lock) || isMonitorLockHeld(lock1);
    }

    protected static EconomicMap<OptionKey<?>, Object> osrLockNoDeopt() {
        EconomicMap<OptionKey<?>, Object> overrides = OptionValues.newOptionMap();
        overrides.put(OnStackReplacementPhase.Options.DeoptAfterOSR, false);
        overrides.put(OnStackReplacementPhase.Options.SupportOSRWithLocks, true);
        return overrides;
    }

    protected static EconomicMap<OptionKey<?>, Object> osrLockDeopt() {
        EconomicMap<OptionKey<?>, Object> overrides = OptionValues.newOptionMap();
        overrides.put(OnStackReplacementPhase.Options.SupportOSRWithLocks, true);
        return overrides;
    }

    public static int SideEffectI;

    private static void lockOnObject(Object o, String msg) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (o) {
                    SideEffectI = 1;
                }
            }
        });
        t.start();
        try {
            t.join(1000);
        } catch (InterruptedException e) {
            Assert.fail("Object " + msg + " was locked");
        }
    }

    private static void beforeOSRLockTest() {
        // try lock both objects
        lockOnObject(lock, "lock");
        lockOnObject(lock1, "lock1");
        Assert.assertFalse(wasLocked());
    }

    private static void afterOSRLockTest() {
        // try lock both objects
        lockOnObject(lock, "lock");
        lockOnObject(lock1, "lock1");
        Assert.assertFalse(wasLocked());
        // force a safepoint and hope the inflated locks are deflated
        System.gc();
    }

    // @Test
    @SuppressWarnings("try")
    public void testLockOSROuterImmediateDeoptAfter() {
        run(() -> {
            OptionValues options = new OptionValues(getInitialOptions(), osrLockDeopt());
            testOSR(options, "testOuterLockImmediateDeoptAfter");
        });
    }

    static class A {

    }

    static class B {
        @SuppressWarnings("unused")
        B(A a) {

        }
    }

    HashMap<String, HashSet<A>> listeners = new HashMap<>();

    public synchronized ReturnValue synchronizedSnippet() {
        /*
         * Test method for which liveness would conclude the original object is no longer alive
         * although it is.
         */
        Collection<HashSet<A>> allListeners = listeners.values();
        for (HashSet<A> group : allListeners) {
            GraalDirectives.blackhole(group);
        }
        return ReturnValue.SUCCESS;
    }

    @Test
    @SuppressWarnings("try")
    public void testSynchronizedSnippet() {
        GraalOSRLockTest instance = new GraalOSRLockTest();
        // enough entries to trigger OSR
        for (int i = 0; i < 100000; i++) {
            instance.listeners.put("hello" + i, null);
        }
        testOSR(getInitialOptions(), "synchronizedSnippet", instance);
        Assert.assertFalse(isMonitorLockHeld(instance));
    }

    @Test
    @SuppressWarnings("try")
    public void testOSRTrivialLoop() {
        run(() -> {
            OptionValues options = new OptionValues(getInitialOptions(), osrLockDeopt());
            try {
                testOSR(options, "testReduceOSRTrivialLoop");
            } catch (Throwable t) {
                Assert.assertEquals("OSR compilation without OSR entry loop.", t.getMessage());
            }
        });
    }

    @Test
    @SuppressWarnings("try")
    public void testLockOSROuterInnerImmediateDeoptAfter() {
        run(() -> {
            OptionValues options = new OptionValues(getInitialOptions(), osrLockDeopt());
            testOSR(options, "testOuterInnerLockImmediateDeoptAfter");
        });
    }

    @Test
    @SuppressWarnings("try")
    public void testLockOSROuterCompileRestOfMethod() {
        run(() -> {
            EconomicMap<OptionKey<?>, Object> overrides = osrLockNoDeopt();
            overrides.put(HighTier.Options.Inline, false);
            OptionValues options = new OptionValues(getInitialOptions(), overrides);
            testOSR(options, "testOuterLockCompileRestOfMethod");
        });
    }

    @Test
    @SuppressWarnings("try")
    public void testLockOSROuterInnerCompileRestOfMethod() {
        run(() -> {
            OptionValues options = new OptionValues(getInitialOptions(), osrLockNoDeopt());
            testOSR(options, "testOuterInnerLockCompileRestOfMethod");
        });
    }

    @Test
    @SuppressWarnings("try")
    public void testLockOSROuterInnerLockDepthCompileRestOfMethod() {
        run(() -> {
            EconomicMap<OptionKey<?>, Object> overrides = osrLockNoDeopt();
            overrides.put(HighTier.Options.Inline, false);
            OptionValues options = new OptionValues(getInitialOptions(), overrides);
            testOSR(options, "testOuterInnerLockDepth1CompileRestOfMethod");
        });
    }

    @Test
    @SuppressWarnings("try")
    public void testLockOSROuterInnerLockDepthDeopt() {
        run(() -> {
            EconomicMap<OptionKey<?>, Object> overrides = osrLockNoDeopt();
            overrides.put(HighTier.Options.Inline, false);
            OptionValues options = new OptionValues(getInitialOptions(), overrides);
            testOSR(options, "testOuterInnerLockDepth1DeoptAfter");
        });
    }

    @Test
    @SuppressWarnings("try")
    public void testLockOSROuterInnerLockDepthRecursiveCompileRestOfMethod0() {
        run(() -> {
            OptionValues options = new OptionValues(getInitialOptions(), osrLockNoDeopt());
            testOSR(options, "testOuterInnerLockDepth1RecursiveCompileRestOfMethod1");
        });
    }

    @Test
    @SuppressWarnings("try")
    public void testLockOSROuterInnerLockDepthRecursiveCompileRestOfMethod1() {
        run(() -> {
            OptionValues options = new OptionValues(getInitialOptions(), osrLockNoDeopt());
            testOSR(options, "testOuterInnerLockDepth1RecursiveCompileRestOfMethod2");
        });
    }

    @Test
    @SuppressWarnings("try")
    public void testLockOSROuterCompileRestOfMethodSubsequentLock() {
        run(() -> {
            OptionValues options = new OptionValues(getInitialOptions(), osrLockNoDeopt());
            testOSR(options, "testOuterLockCompileRestOfMethodSubsequentLock");
        });
    }

    @Test
    @SuppressWarnings("try")
    public void testLockOSROuterInnerSameLockCompileRestOfMethod() {
        run(() -> {
            OptionValues options = new OptionValues(getInitialOptions(), osrLockNoDeopt());
            testOSR(options, "testOuterInnerSameLockCompileRestOfMethod");
        });
    }

    @Test
    @SuppressWarnings("try")
    public void testLockOSRRecursive() {
        run(() -> {
            // call it
            testRecursiveLockingLeaf();
            ResolvedJavaMethod leaf = getResolvedJavaMethod("testRecursiveLockingLeaf");
            // profile it
            leaf.reprofile();
            testRecursiveLockingLeaf();
            EconomicMap<OptionKey<?>, Object> overrides = osrLockNoDeopt();
            overrides.put(HighTier.Options.Inline, false);
            OptionValues options = new OptionValues(getInitialOptions(), overrides);
            DebugContext debug = getDebugContext(options);
            compile(debug, leaf, -1);
            testOSR(options, "testRecursiveLockingRoot");
        });
    }

    @Test
    @SuppressWarnings("try")
    public void testLockOSRRecursiveLeafOSR() {
        run(() -> {
            testRecursiveRootNoOSR();
            ResolvedJavaMethod root = getResolvedJavaMethod("testRecursiveRootNoOSR");
            EconomicMap<OptionKey<?>, Object> overrides = osrLockNoDeopt();
            overrides.put(HighTier.Options.Inline, false);
            OptionValues options = new OptionValues(getInitialOptions(), overrides);
            DebugContext debug = getDebugContext(options);
            compile(debug, root, -1);
            testOSR(options, "testRecursiveLeafOSR");
            // force a safepoint and hope the inflated locks are deflated
            System.gc();
            // call the root to call into the leaf and enter the osr-ed code
            testRecursiveRootNoOSR();

        });
    }

    protected static int limit = 10000;
    protected static Object lock = new Object();
    protected static Object lock1 = new Object();
    private static final boolean LOG = false;

    static {
        // force identity hash code for easy displaced mark identification
        int h1 = System.identityHashCode(lock);
        int h2 = System.identityHashCode(lock1);
        if (LOG) {
            TTY.println("Forcing a system identity hashcode on lock object " + h1);
            TTY.println("Forcing a system identity hashcode on lock1 object " + h2);
        }
    }

    public static ReturnValue testReduceOSRTrivialLoop() {
        for (int i = 0; i < limit * limit; i++) {
            GraalDirectives.blackhole(i);
            if (GraalDirectives.inCompiledCode()) {
                return ReturnValue.SUCCESS;
            }
        }
        return ReturnValue.FAILURE;
    }

    public static ReturnValue testOuterLockImmediateDeoptAfter() {
        ReturnValue ret = ReturnValue.FAILURE;
        synchronized (lock) {
            for (int i = 1; i < 10 * limit; i++) {
                GraalDirectives.blackhole(i);
                if (i % 33 == 0) {
                    ret = ReturnValue.SUCCESS;
                    if (GraalDirectives.inCompiledCode() && i + 33 > (10 * limit)) {
                        GraalDirectives.blackhole(ret);
                    }
                }
            }
            GraalDirectives.controlFlowAnchor();
            if (GraalDirectives.inCompiledCode()) {
                throw new Error("Must not be part of compiled code");
            }
            return ret;
        }
    }

    public static ReturnValue testOuterInnerLockImmediateDeoptAfter() {
        ReturnValue ret = ReturnValue.FAILURE;
        synchronized (lock) {
            for (int i = 1; i < 10 * limit; i++) {
                synchronized (lock1) {
                    GraalDirectives.blackhole(i);
                    if (i % 33 == 0) {
                        ret = ReturnValue.SUCCESS;
                        if (GraalDirectives.inCompiledCode() && i + 33 > (10 * limit)) {
                            GraalDirectives.blackhole(ret);
                        }
                    }
                }
            }
            GraalDirectives.controlFlowAnchor();
            GraalDirectives.deoptimize();
            return ret;
        }
    }

    public static ReturnValue testOuterLockCompileRestOfMethod() {
        ReturnValue ret = ReturnValue.FAILURE;
        synchronized (lock) {
            for (int i = 1; i < limit; i++) {
                GraalDirectives.blackhole(i);
                if (i % 1001 == 0) {
                    ret = ReturnValue.SUCCESS;
                    System.gc();
                }
            }
            return ret;
        }
    }

    public static ReturnValue testOuterInnerLockCompileRestOfMethod() {
        ReturnValue ret = ReturnValue.FAILURE;
        synchronized (lock) {
            for (int i = 1; i < 10 * limit; i++) {
                synchronized (lock1) {
                    GraalDirectives.blackhole(i);
                    if (i % 33 == 0) {
                        ret = ReturnValue.SUCCESS;
                        if (GraalDirectives.inCompiledCode() && i + 33 > (10 * limit)) {
                            GraalDirectives.blackhole(ret);
                            System.gc();
                        }
                    }
                }
            }
            GraalDirectives.controlFlowAnchor();
            if (!GraalDirectives.inCompiledCode()) {
                throw new Error("Must be part of compiled code");
            }
            return ret;
        }
    }

    public static ReturnValue testOuterInnerLockDepth1CompileRestOfMethod() {
        // testing the order of the lock releasing
        ReturnValue ret = ReturnValue.FAILURE;
        synchronized (lock) {
            synchronized (lock1) {
                for (int i = 1; i < 10 * limit; i++) {
                    GraalDirectives.blackhole(i);
                    if (i % 33 == 0) {
                        ret = ReturnValue.SUCCESS;
                        if (GraalDirectives.inCompiledCode() && i + 33 > (10 * limit)) {
                            GraalDirectives.blackhole(ret);
                            System.gc();
                        }
                    }
                }
            }
            GraalDirectives.controlFlowAnchor();
            if (!GraalDirectives.inCompiledCode()) {
                throw new Error("Must be part of compiled code already hereeeeee");
            } else {
                // lock 1 must be free
                if (isMonitorLockHeld(lock1)) {
                    throw new Error("Lock 1 must have been released already");
                }

                // lock 2 must still be locked and cannot be acquired by another thread
                if (!isMonitorLockHeldByThread(lock, Thread.currentThread())) {
                    throw new Error("Lock must not have been released already");
                }
            }
            return ret;
        }
    }

    public static ReturnValue testOuterInnerLockDepth1DeoptAfter() {
        // testing the order of the lock releasing
        ReturnValue ret = ReturnValue.FAILURE;
        synchronized (lock) {
            synchronized (lock1) {
                for (int i = 1; i < 10 * limit; i++) {
                    GraalDirectives.blackhole(i);
                    if (i % 33 == 0) {
                        ret = ReturnValue.SUCCESS;
                        if (GraalDirectives.inCompiledCode() && i + 33 > (10 * limit)) {
                            GraalDirectives.blackhole(ret);
                        }
                    }
                }
                GraalDirectives.controlFlowAnchor();
                GraalDirectives.deoptimize();
                if (GraalDirectives.inCompiledCode()) {
                    throw new Error("Must not part of compiled code");
                }
            }
        }
        return ret;
    }

    public static ReturnValue testOuterInnerLockDepth1RecursiveCompileRestOfMethod1() {
        // testing the order of the lock releasing
        ReturnValue ret = ReturnValue.FAILURE;
        synchronized (lock) {
            synchronized (lock) {
                for (int i = 1; i < 10 * limit; i++) {
                    GraalDirectives.blackhole(i);
                    if (i % 33 == 0) {
                        ret = ReturnValue.SUCCESS;
                        if (GraalDirectives.inCompiledCode() && i + 33 > (10 * limit)) {
                            GraalDirectives.blackhole(ret);
                        }
                    }
                }
            }
            GraalDirectives.controlFlowAnchor();
            if (!GraalDirectives.inCompiledCode()) {
                throw new Error("Must be part of compiled code");
            }
            return ret;
        }
    }

    public static ReturnValue testOuterInnerLockDepth1RecursiveCompileRestOfMethod2() {
        // testing the order of the lock releasing
        final Object l = lock;
        ReturnValue ret = ReturnValue.FAILURE;
        synchronized (l) {
            synchronized (l) {
                for (int i = 1; i < 10 * limit; i++) {
                    GraalDirectives.blackhole(i);
                    if (i % 33 == 0) {
                        ret = ReturnValue.SUCCESS;
                        if (GraalDirectives.inCompiledCode() && i + 33 > (10 * limit)) {
                            GraalDirectives.blackhole(ret);
                        }
                    }
                }
            }
            GraalDirectives.controlFlowAnchor();
            if (!GraalDirectives.inCompiledCode()) {
                throw new Error("Must be part of compiled code");
            }
            return ret;
        }
    }

    public static ReturnValue testRecursiveLockingRoot() {
        // testing the order of the lock releasing
        final Object l = lock;
        ReturnValue ret = ReturnValue.FAILURE;
        synchronized (l) {
            synchronized (l) {
                for (int i = 1; i < limit; i++) {
                    GraalDirectives.blackhole(i);
                    testRecursiveLockingLeaf();
                    if (i % 33 == 0) {
                        ret = ReturnValue.SUCCESS;
                        if (GraalDirectives.inCompiledCode() && i + 33 > (10 * limit)) {
                            GraalDirectives.blackhole(ret);
                        }
                    }
                }
            }
            GraalDirectives.controlFlowAnchor();
            if (!GraalDirectives.inCompiledCode()) {
                throw new Error("Must be part of compiled code");
            }
            return ret;
        }
    }

    public static ReturnValue testRecursiveLockingLeaf() {
        // testing the order of the lock releasing
        final Object l = lock;
        ReturnValue ret = ReturnValue.FAILURE;
        synchronized (l) {
            synchronized (l) {
                for (int i = 1; i < limit; i++) {
                    GraalDirectives.blackhole(i);
                    if (i % 33 == 0) {
                        ret = ReturnValue.SUCCESS;
                    }
                }
            }
            return ret;
        }
    }

    public static ReturnValue testRecursiveRootNoOSR() {
        // testing the order of the lock releasing
        final Object l = lock;
        synchronized (l) {
            ReturnValue ret = ReturnValue.FAILURE;
            for (int i = 0; i < 5; i++) {
                if (GraalDirectives.inCompiledCode()) {
                    ret = testRecursiveLeafOSR();
                }
                GraalDirectives.controlFlowAnchor();
                if (ret == ReturnValue.FAILURE) {
                    return ret;
                }
            }
            GraalDirectives.controlFlowAnchor();
            return ret;
        }
    }

    public static ReturnValue testRecursiveLeafOSR() {
        ReturnValue ret = ReturnValue.FAILURE;
        // lock is already locked by the caller
        synchronized (lock) {
            for (int i = 1; i < 10 * limit; i++) {
                GraalDirectives.blackhole(i);
                if (i % 33 == 0) {
                    ret = ReturnValue.SUCCESS;
                    if (GraalDirectives.inCompiledCode() && i + 33 > (10 * limit)) {
                        GraalDirectives.blackhole(ret);
                    }
                }
            }
            GraalDirectives.controlFlowAnchor();
            return ret;
        }
    }

    // test cases for optimizations
    public static ReturnValue testOuterLockCompileRestOfMethodSubsequentLock() {
        final Object monitor = lock;
        ReturnValue ret = ReturnValue.FAILURE;
        synchronized (monitor) {
            for (int i = 1; i < 10 * limit; i++) {
                GraalDirectives.blackhole(i);
                if (i % 33 == 0) {
                    ret = ReturnValue.SUCCESS;
                    if (GraalDirectives.inCompiledCode() && i + 33 > (10 * limit)) {
                        GraalDirectives.blackhole(ret);
                    }
                }
            }
        }
        synchronized (monitor) {
            GraalDirectives.controlFlowAnchor();
            if (!GraalDirectives.inCompiledCode()) {
                throw new Error("Must be part of compiled code");
            }
        }
        return ret;

    }

    public static ReturnValue testOuterInnerSameLockCompileRestOfMethod() {
        final Object monitor = lock;
        ReturnValue ret = ReturnValue.FAILURE;
        synchronized (monitor) {
            for (int i = 1; i < 10 * limit; i++) {
                synchronized (monitor) {
                    GraalDirectives.blackhole(i);
                    if (i % 33 == 0) {
                        ret = ReturnValue.SUCCESS;
                        if (GraalDirectives.inCompiledCode() && i + 33 > (10 * limit)) {
                            GraalDirectives.blackhole(ret);
                        }
                    }
                }
            }
            GraalDirectives.controlFlowAnchor();
            if (!GraalDirectives.inCompiledCode()) {
                throw new Error("Must be part of compiled code");
            }
            return ret;
        }
    }

}
