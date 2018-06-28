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

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Test on-stack-replacement with Graal. The test manually triggers a Graal OSR-compilation which is
 * later invoked when hitting the backedge counter overflow.
 */
public class GraalOSRTest extends GraalOSRTestBase {

    @Test
    public void testOSR01() {
        try {
            testOSR(getInitialOptions(), "testReduceLoop");
        } catch (Throwable t) {
            Assert.assertEquals("OSR compilation without OSR entry loop.", t.getMessage());
        }
    }

    @Test
    public void testOSR02() {
        testOSR(getInitialOptions(), "testSequentialLoop");
    }

    @Test
    public void testOSR03() {
        testOSR(getInitialOptions(), "testNonReduceLoop");
    }

    static int limit = 10000;

    public static int sideEffect;

    public static ReturnValue testReduceLoop() {
        for (int i = 0; i < limit * limit; i++) {
            GraalDirectives.blackhole(i);
            if (GraalDirectives.inCompiledCode()) {
                return ReturnValue.SUCCESS;
            }
        }
        return ReturnValue.FAILURE;
    }

    public static ReturnValue testSequentialLoop() {
        ReturnValue ret = ReturnValue.FAILURE;
        for (int i = 1; i < limit * limit; i++) {
            GraalDirectives.blackhole(i);
            if (i % 7 == 0) {
                ret = ReturnValue.SUCCESS;
            }
        }
        GraalDirectives.controlFlowAnchor();
        if (sideEffect == 123) {
            return ReturnValue.SIDE;
        }
        for (int i = 1; i < limit * limit; i++) {
            GraalDirectives.blackhole(i);
            if (i % 33 == 0) {
                ret = ReturnValue.SUCCESS;
            }
        }
        GraalDirectives.controlFlowAnchor();
        return ret;
    }

    public static ReturnValue testNonReduceLoop() {
        ReturnValue ret = ReturnValue.FAILURE;
        for (int i = 0; i < limit * limit; i++) {
            GraalDirectives.blackhole(i);
            if (i % 33 == 0) {
                ret = ReturnValue.SUCCESS;
            }
        }
        GraalDirectives.controlFlowAnchor();
        return ret;
    }

    @Test
    public void testOSR04() {
        testFunnyOSR("testDoWhile", GraalOSRTest::testDoWhile);
    }

    @Test
    public void testOSR05() {
        testFunnyOSR("testDoWhileLocked", GraalOSRTest::testDoWhileLocked);
    }

    /**
     * Because of a bug in C1 profile collection HotSpot can sometimes request an OSR compilation
     * for a backedge which isn't ever taken. This test synthetically creates that situation.
     */
    private void testFunnyOSR(String name, Runnable warmup) {
        ResolvedJavaMethod method = getResolvedJavaMethod(name);
        int iterations = 0;
        while (true) {
            ProfilingInfo profilingInfo = method.getProfilingInfo();
            if (profilingInfo.isMature()) {
                break;
            }

            warmup.run();
            if (iterations++ % 1000 == 0) {
                System.err.print('.');
            }
            if (iterations > 200000) {
                throw new AssertionError("no profile");
            }
        }
        compileOSR(getInitialOptions(), method);
        Result result = executeExpected(method, null);
        checkResult(result);
    }

    private static boolean repeatLoop;

    public static ReturnValue testDoWhile() {
        do {
            sideEffect++;
        } while (repeatLoop);
        return ReturnValue.SUCCESS;
    }

    public static synchronized ReturnValue testDoWhileLocked() {
        // synchronized (GraalOSRTest.class) {
        do {
            sideEffect++;
        } while (repeatLoop);
        // }
        return ReturnValue.SUCCESS;
    }
}
