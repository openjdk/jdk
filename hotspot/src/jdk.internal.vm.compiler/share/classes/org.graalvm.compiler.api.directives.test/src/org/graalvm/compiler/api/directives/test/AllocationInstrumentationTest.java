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

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.options.OptionValue;
import org.graalvm.compiler.options.OptionValue.OverrideScope;

import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@SuppressWarnings("try")
public class AllocationInstrumentationTest extends GraalCompilerTest {

    private TinyInstrumentor instrumentor;

    public AllocationInstrumentationTest() {
        HotSpotResolvedJavaMethod method = (HotSpotResolvedJavaMethod) getResolvedJavaMethod(ClassA.class, "notInlinedMethod");
        method.setNotInlineable();

        try {
            instrumentor = new TinyInstrumentor(AllocationInstrumentationTest.class, "instrumentation");
        } catch (IOException e) {
            Assert.fail("unable to initialize the instrumentor: " + e);
        }
    }

    public static class ClassA {
        // This method should be marked as not inlineable
        public void notInlinedMethod() {
        }
    }

    public static boolean allocationWasExecuted;

    private static void resetFlag() {
        allocationWasExecuted = false;
    }

    static void instrumentation() {
        GraalDirectives.instrumentationBeginForPredecessor();
        allocationWasExecuted = true;
        GraalDirectives.instrumentationEnd();
    }

    public static void notEscapeSnippet() {
        @SuppressWarnings("unused")
        ClassA a = new ClassA(); // a does not escape
    }

    @Test
    public void testNotEscape() {
        try (OverrideScope s = OptionValue.override(GraalOptions.UseGraalInstrumentation, true)) {
            Class<?> clazz = instrumentor.instrument(AllocationInstrumentationTest.class, "notEscapeSnippet", Opcodes.NEW);
            ResolvedJavaMethod method = getResolvedJavaMethod(clazz, "notEscapeSnippet");
            executeExpected(method, null); // ensure the method is fully resolved
            resetFlag();
            // The allocation in the snippet does not escape and will be optimized away. We expect
            // the instrumentation is removed.
            InstalledCode code = getCode(method);
            code.executeVarargs();
            Assert.assertFalse("allocation should not take place", allocationWasExecuted);
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }

    public static void mustEscapeSnippet() {
        ClassA a = new ClassA();
        a.notInlinedMethod(); // a escapses
    }

    @Test
    public void testMustEscape() {
        try (OverrideScope s = OptionValue.override(GraalOptions.UseGraalInstrumentation, true)) {
            Class<?> clazz = instrumentor.instrument(AllocationInstrumentationTest.class, "mustEscapeSnippet", Opcodes.NEW);
            ResolvedJavaMethod method = getResolvedJavaMethod(clazz, "mustEscapeSnippet");
            executeExpected(method, null); // ensure the method is fully resolved
            resetFlag();
            // The allocation in the snippet escapes. We expect the instrumentation is preserved.
            InstalledCode code = getCode(method);
            code.executeVarargs();
            Assert.assertTrue("allocation should take place", allocationWasExecuted);
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }

    public static void partialEscapeSnippet(boolean condition) {
        ClassA a = new ClassA();

        if (condition) {
            a.notInlinedMethod(); // a escapes in the then-clause
        }
    }

    @Test
    public void testPartialEscape() {
        try (OverrideScope s = OptionValue.override(GraalOptions.UseGraalInstrumentation, true)) {
            Class<?> clazz = instrumentor.instrument(AllocationInstrumentationTest.class, "partialEscapeSnippet", Opcodes.NEW);
            ResolvedJavaMethod method = getResolvedJavaMethod(clazz, "partialEscapeSnippet");
            executeExpected(method, null, true); // ensure the method is fully resolved
            resetFlag();
            // The allocation in the snippet escapes in the then-clause, and will be relocated to
            // this branch. We expect the instrumentation follows and will only be effective when
            // the then-clause is taken.
            InstalledCode code = getCode(method);
            code.executeVarargs(false);
            Assert.assertFalse("allocation should not take place", allocationWasExecuted);
            code.executeVarargs(true);
            Assert.assertTrue("allocation should take place", allocationWasExecuted);
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }

}
