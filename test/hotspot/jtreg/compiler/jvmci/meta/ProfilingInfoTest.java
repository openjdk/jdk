/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * These tests are explicitly testing the profiling behavior of the
 * interpreter. C1-based profiling differs slightly and when -Xcomp
 * is present, profiles will be created by C1 compiled code, not the
 * interpreter.
 *
 * @test
 * @requires vm.jvmci
 * @requires vm.compMode != "Xcomp"
 * @requires vm.opt.TieredStopAtLevel == null | vm.opt.TieredStopAtLevel > 1
 * @modules jdk.internal.vm.ci/jdk.vm.ci.meta
 *          jdk.internal.vm.ci/jdk.vm.ci.hotspot
 *          jdk.internal.vm.ci/jdk.vm.ci.runtime
 * @run junit/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -XX:-UseJVMCICompiler -Xbootclasspath/a:. compiler.jvmci.meta.ProfilingInfoTest
 */
package compiler.jvmci.meta;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import jdk.vm.ci.hotspot.HotSpotProfilingInfo;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.TriState;
import jdk.vm.ci.runtime.JVMCI;

/**
 * Tests profiling information provided by the runtime.
 * <p>
 * NOTE: These tests are actually not very robust. The problem is that only partial profiling
 * information may be gathered for any given method. For example, HotSpot's advanced compilation
 * policy can decide to only gather partial profiles in a first level compilation (see
 * AdvancedThresholdPolicy::common(...) in advancedThresholdPolicy.cpp). Because of this,
 * occasionally tests for {@link ProfilingInfo#getNullSeen(int)} can fail since HotSpot only sets
 * the null_seen bit when doing full profiling.
 */
public class ProfilingInfoTest {

    private static final int N = 10;
    private static final double DELTA = 1d / Integer.MAX_VALUE;

    @Test
    public void testBranchTakenProbability() {
        ProfilingInfo info = profile("branchProbabilitySnippet", 0);
        Assert.assertEquals(0.0, info.getBranchTakenProbability(1), DELTA);
        Assert.assertEquals(N, info.getExecutionCount(1));
        Assert.assertEquals(-1.0, info.getBranchTakenProbability(8), DELTA);
        Assert.assertEquals(0, info.getExecutionCount(8));

        info = profile("branchProbabilitySnippet", 1);
        Assert.assertEquals(1.0, info.getBranchTakenProbability(1), DELTA);
        Assert.assertEquals(N, info.getExecutionCount(1));
        Assert.assertEquals(0.0, info.getBranchTakenProbability(8), DELTA);
        Assert.assertEquals(N, info.getExecutionCount(8));

        info = profile("branchProbabilitySnippet", 2);
        Assert.assertEquals(1.0, info.getBranchTakenProbability(1), DELTA);
        Assert.assertEquals(N, info.getExecutionCount(1));
        Assert.assertEquals(1.0, info.getBranchTakenProbability(8), DELTA);
        Assert.assertEquals(N, info.getExecutionCount(8));

        continueProfiling(3 * N, "branchProbabilitySnippet", 0);
        Assert.assertEquals(0.25, info.getBranchTakenProbability(1), DELTA);
        Assert.assertEquals(4 * N, info.getExecutionCount(1));
        Assert.assertEquals(1.0, info.getBranchTakenProbability(8), DELTA);
        Assert.assertEquals(N, info.getExecutionCount(8));

        resetProfile("branchProbabilitySnippet");
        Assert.assertEquals(-1.0, info.getBranchTakenProbability(1), DELTA);
        Assert.assertEquals(0, info.getExecutionCount(1));
        Assert.assertEquals(-1.0, info.getBranchTakenProbability(8), DELTA);
        Assert.assertEquals(0, info.getExecutionCount(8));
    }

    public static int branchProbabilitySnippet(int value) {
        if (value == 0) {
            return -1;
        } else if (value == 1) {
            return -2;
        } else {
            return -3;
        }
    }

    @Test
    public void testSwitchProbabilities() {
        ProfilingInfo info = profile("switchProbabilitySnippet", 0);
        Assert.assertArrayEquals(new double[]{1.0, 0.0, 0.0}, info.getSwitchProbabilities(1), DELTA);

        info = profile("switchProbabilitySnippet", 1);
        Assert.assertArrayEquals(new double[]{0.0, 1.0, 0.0}, info.getSwitchProbabilities(1), DELTA);

        info = profile("switchProbabilitySnippet", 2);
        Assert.assertArrayEquals(new double[]{0.0, 0.0, 1.0}, info.getSwitchProbabilities(1), DELTA);

        resetProfile("switchProbabilitySnippet");
        Assert.assertNull(info.getSwitchProbabilities(1));
    }

    public static int switchProbabilitySnippet(int value) {
        switch (value) {
            case 0:
                return -1;
            case 1:
                return -2;
            default:
                return -3;
        }
    }

    @Test
    public void testProfileInvokeVirtual() {
        testTypeProfile("invokeVirtualSnippet", 1);
    }

    public static int invokeVirtualSnippet(Object obj) {
        return obj.hashCode();
    }

    @Test
    public void testTypeProfileInvokeInterface() {
        testTypeProfile("invokeInterfaceSnippet", 1);
    }

    public static int invokeInterfaceSnippet(CharSequence a) {
        return a.length();
    }

    @Test
    public void testTypeProfileCheckCast() {
        testTypeProfile("checkCastSnippet", 1);
    }

    public static Serializable checkCastSnippet(Object obj) {
        try {
            return (Serializable) obj;
        } catch (ClassCastException e) {
            return null;
        }
    }

    @Test
    public void testTypeProfileInstanceOf() {
        testTypeProfile("instanceOfSnippet", 1);
    }

    public static boolean instanceOfSnippet(Object obj) {
        return obj instanceof Serializable;
    }

    private void testTypeProfile(String testSnippet, int bci) {
        MetaAccessProvider metaAccess = JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess();
        ResolvedJavaType stringType = metaAccess.lookupJavaType(String.class);
        ResolvedJavaType stringBuilderType = metaAccess.lookupJavaType(StringBuilder.class);

        ProfilingInfo info = profile(testSnippet, "ABC");
        JavaTypeProfile typeProfile = info.getTypeProfile(bci);
        Assert.assertEquals(0.0, typeProfile.getNotRecordedProbability(), DELTA);
        Assert.assertEquals(1, typeProfile.getTypes().length);
        Assert.assertEquals(stringType, typeProfile.getTypes()[0].getType());
        Assert.assertEquals(1.0, typeProfile.getTypes()[0].getProbability(), DELTA);

        continueProfiling(testSnippet, new StringBuilder());
        typeProfile = info.getTypeProfile(bci);
        Assert.assertEquals(0.0, typeProfile.getNotRecordedProbability(), DELTA);
        Assert.assertEquals(2, typeProfile.getTypes().length);
        Assert.assertEquals(stringType, typeProfile.getTypes()[0].getType());
        Assert.assertEquals(stringBuilderType, typeProfile.getTypes()[1].getType());
        Assert.assertEquals(0.5, typeProfile.getTypes()[0].getProbability(), DELTA);
        Assert.assertEquals(0.5, typeProfile.getTypes()[1].getProbability(), DELTA);

        resetProfile(testSnippet);
        typeProfile = info.getTypeProfile(bci);
        Assert.assertNull(typeProfile);

        // Basic test that the counters are non-negative
        HotSpotProfilingInfo hsInfo = (HotSpotProfilingInfo) info;
        int count = hsInfo.getDecompileCount();
        Assert.assertTrue("count = " + count, count >= 0);
        count = hsInfo.getOverflowRecompileCount();
        Assert.assertTrue("count = " + count, count >= 0);
        count = hsInfo.getOverflowTrapCount();
        Assert.assertTrue("count = " + count, count >= 0);
    }

    public ProfilingInfoTest() {
    }

    @Test
    public void testExceptionSeen() {
        // NullPointerException
        ProfilingInfo info = profile("nullPointerExceptionSnippet", 5);
        Assert.assertEquals(TriState.FALSE, info.getExceptionSeen(1));

        info = profile("nullPointerExceptionSnippet", (Object) null);
        Assert.assertEquals(TriState.TRUE, info.getExceptionSeen(1));

        resetProfile("nullPointerExceptionSnippet");
        Assert.assertEquals(TriState.FALSE, info.getExceptionSeen(1));

        // ArrayOutOfBoundsException
        info = profile("arrayIndexOutOfBoundsExceptionSnippet", new int[1]);
        Assert.assertEquals(TriState.FALSE, info.getExceptionSeen(2));

        info = profile("arrayIndexOutOfBoundsExceptionSnippet", new int[0]);
        Assert.assertEquals(TriState.TRUE, info.getExceptionSeen(2));

        resetProfile("arrayIndexOutOfBoundsExceptionSnippet");
        Assert.assertEquals(TriState.FALSE, info.getExceptionSeen(2));

        // CheckCastException
        info = profile("checkCastExceptionSnippet", "ABC");
        Assert.assertEquals(TriState.FALSE, info.getExceptionSeen(1));

        info = profile("checkCastExceptionSnippet", 5);
        Assert.assertEquals(TriState.TRUE, info.getExceptionSeen(1));

        resetProfile("checkCastExceptionSnippet");
        Assert.assertEquals(TriState.FALSE, info.getExceptionSeen(1));

        // Invoke with exception
        info = profile("invokeWithExceptionSnippet", false);
        Assert.assertEquals(TriState.FALSE, info.getExceptionSeen(1));

        info = profile("invokeWithExceptionSnippet", true);
        Assert.assertEquals(TriState.TRUE, info.getExceptionSeen(1));

        resetProfile("invokeWithExceptionSnippet");
        Assert.assertEquals(TriState.FALSE, info.getExceptionSeen(1));
    }

    public static int nullPointerExceptionSnippet(Object obj) {
        try {
            return obj.hashCode();
        } catch (NullPointerException e) {
            return 1;
        }
    }

    public static int arrayIndexOutOfBoundsExceptionSnippet(int[] array) {
        try {
            return array[0];
        } catch (ArrayIndexOutOfBoundsException e) {
            return 1;
        }
    }

    public static int checkCastExceptionSnippet(Object obj) {
        try {
            return ((String) obj).length();
        } catch (ClassCastException e) {
            return 1;
        }
    }

    public static int invokeWithExceptionSnippet(boolean doThrow) {
        try {
            return throwException(doThrow);
        } catch (IllegalArgumentException e) {
            return 1;
        }
    }

    private static int throwException(boolean doThrow) {
        if (doThrow) {
            throw new IllegalArgumentException();
        } else {
            return 1;
        }
    }

    @Test
    public void testNullSeen() {
        testNullSeen("instanceOfSnippet");
        testNullSeen("checkCastSnippet");
    }

    private void testNullSeen(String snippet) {
        ProfilingInfo info = profile(snippet, 1);
        Assert.assertEquals(TriState.FALSE, info.getNullSeen(1));

        continueProfiling(snippet, "ABC");
        Assert.assertEquals(TriState.FALSE, info.getNullSeen(1));

        continueProfiling(snippet, new Object());
        Assert.assertEquals(TriState.FALSE, info.getNullSeen(1));

        if (TriState.TRUE == info.getNullSeen(1)) {
            // See the javadoc comment for ProfilingInfoTest.
            continueProfiling(snippet, (Object) null);
            Assert.assertEquals(TriState.TRUE, info.getNullSeen(1));

            continueProfiling(snippet, 0.0);
            Assert.assertEquals(TriState.TRUE, info.getNullSeen(1));

            continueProfiling(snippet, new Object());
            Assert.assertEquals(TriState.TRUE, info.getNullSeen(1));
        }

        resetProfile(snippet);
        Assert.assertEquals(TriState.FALSE, info.getNullSeen(1));
    }

    private ProfilingInfo profile(String methodName, Object... args) {
        return profile(true, N, methodName, args);
    }

    private void continueProfiling(String methodName, Object... args) {
        profile(false, N, methodName, args);
    }

    private void continueProfiling(int executions, String methodName, Object... args) {
        profile(false, executions, methodName, args);
    }

    private ProfilingInfo profile(boolean resetProfile, int executions, String methodName, Object... args) {
        MetaAccessProvider metaAccess = JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess();
        Method method = getMethod(methodName);
        ResolvedJavaMethod javaMethod = metaAccess.lookupJavaMethod(method);
        Assert.assertTrue(javaMethod.isStatic());
        if (resetProfile) {
            javaMethod.reprofile();
        }

        for (int i = 0; i < executions; ++i) {
            try {
                method.invoke(null, args);
            } catch (Throwable e) {
                Assert.fail("method should not throw an exception: " + e.toString());
            }
        }

        ProfilingInfo info = javaMethod.getProfilingInfo();
        // The execution counts are low so force maturity
        info.setMature();
        return info;
    }

    static Method getMethod(String methodName) {
        for (Method method : ProfilingInfoTest.class.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        throw new IllegalArgumentException();
    }

    private void resetProfile(String methodName) {
        MetaAccessProvider metaAccess = JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess();
        ResolvedJavaMethod javaMethod = metaAccess.lookupJavaMethod(getMethod(methodName));
        javaMethod.reprofile();
    }
}
