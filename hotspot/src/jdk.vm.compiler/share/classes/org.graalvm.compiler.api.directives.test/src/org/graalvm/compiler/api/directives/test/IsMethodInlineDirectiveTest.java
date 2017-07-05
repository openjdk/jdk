/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.api.directives.test;

import org.junit.Test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.options.OptionValue;
import org.graalvm.compiler.options.OptionValue.OverrideScope;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class IsMethodInlineDirectiveTest extends GraalCompilerTest {

    public IsMethodInlineDirectiveTest() {
        HotSpotResolvedJavaMethod calleeSnippet = (HotSpotResolvedJavaMethod) getResolvedJavaMethod(IsMethodInlineDirectiveTest.class, "calleeSnippet");
        calleeSnippet.shouldBeInlined();

        HotSpotResolvedJavaMethod calleeWithInstrumentationSnippet = (HotSpotResolvedJavaMethod) getResolvedJavaMethod(IsMethodInlineDirectiveTest.class, "calleeWithInstrumentationSnippet");
        calleeWithInstrumentationSnippet.shouldBeInlined();
    }

    public static boolean rootMethodSnippet() {
        return GraalDirectives.isMethodInlined();
    }

    @SuppressWarnings("try")
    @Test
    public void testRootMethod() {
        ResolvedJavaMethod method = getResolvedJavaMethod("rootMethodSnippet");
        executeExpected(method, null); // ensure the method is fully resolved
        // The target method is not inlined. We expect the result to be false.
        InstalledCode code = getCode(method);
        try {
            Result result = new Result(code.executeVarargs(), null);
            assertEquals(new Result(false, null), result);
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }

    public static boolean calleeSnippet() {
        return GraalDirectives.isMethodInlined();
    }

    public static boolean callerSnippet() {
        return calleeSnippet();
    }

    @Test
    public void testInlinedCallee() {
        ResolvedJavaMethod method = getResolvedJavaMethod("callerSnippet");
        executeExpected(method, null); // ensure the method is fully resolved
        // calleeSnippet will be inlined. We expect the result to be true.
        InstalledCode code = getCode(method);
        try {
            Result result = new Result(code.executeVarargs(), null);
            assertEquals(new Result(true, null), result);
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }

    static boolean isCalleeInlined;
    static boolean isCallerInlined;

    public static void calleeWithInstrumentationSnippet() {
        GraalDirectives.instrumentationBegin();
        isCalleeInlined = GraalDirectives.isMethodInlined();
        GraalDirectives.instrumentationEnd();
    }

    public static void callerSnippet1() {
        calleeWithInstrumentationSnippet();

        GraalDirectives.instrumentationBegin();
        isCallerInlined = GraalDirectives.isMethodInlined();
        GraalDirectives.instrumentationEnd();
    }

    @SuppressWarnings("try")
    @Test
    public void testInlinedCalleeWithInstrumentation() {
        try (OverrideScope s = OptionValue.override(GraalOptions.UseGraalInstrumentation, true)) {
            ResolvedJavaMethod method = getResolvedJavaMethod("callerSnippet1");
            executeExpected(method, null); // ensure the method is fully resolved
            isCalleeInlined = false;
            isCallerInlined = false;
            // calleeWithInstrumentationSnippet will be inlined. We expect the flag1 set in
            // calleeWithInstrumentationSnippet to be true, and the flag2 set in callerSnippet1 to
            // be false.
            InstalledCode code = getCode(method);
            code.executeVarargs();
            assertTrue("calleWithInstrumentationSnippet should be inlined", isCalleeInlined);
            assertFalse("callerSnippet1 should not be inlined", isCallerInlined);
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }

}
