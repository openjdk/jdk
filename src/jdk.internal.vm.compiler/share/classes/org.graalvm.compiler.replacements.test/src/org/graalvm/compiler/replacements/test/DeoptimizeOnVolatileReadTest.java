/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests that deoptimization upon volatile read will not roll back the read. The test cases rely on
 * the fact that invocations to {@link GraalDirectives} utilities are substituted and not valid
 * targets for deoptimization.
 */
public class DeoptimizeOnVolatileReadTest extends GraalCompilerTest {

    static class Dummy {
        boolean f1 = false;
        volatile boolean f2 = false;
    }

    public static int test1Snippet(Dummy dummy) {
        if (GraalDirectives.injectBranchProbability(0, GraalDirectives.inCompiledCode() & dummy.f1)) {
            return 1;
        }

        return 0;
    }

    @Test
    public void test1() {
        ResolvedJavaMethod method = getResolvedJavaMethod("test1Snippet");

        Dummy dummy = new Dummy();
        Result expected = executeExpected(method, null, dummy);
        assertEquals(new Result(0, null), expected);

        dummy.f1 = true;

        InstalledCode code = getCode(method);
        Result actual;

        try {
            actual = new Result(code.executeVarargs(dummy), null);
        } catch (Exception e) {
            actual = new Result(null, e);
        }

        // The code should get deoptimized, and resume execution at the beginning of the method.
        // Therefore it does not re-enter the branch as inCompiledCode() is false.
        assertEquals(new Result(0, null), actual);
        assertFalse(code.isValid());
    }

    public static int test2Snippet(Dummy dummy) {
        if (GraalDirectives.injectBranchProbability(0, GraalDirectives.inCompiledCode() & dummy.f2)) {
            return 1;
        }

        return 0;
    }

    @Test
    public void test2() {
        ResolvedJavaMethod method = getResolvedJavaMethod("test2Snippet");

        Dummy dummy = new Dummy();
        Result expected = executeExpected(method, null, dummy);
        assertEquals(new Result(0, null), expected);

        dummy.f2 = true;

        InstalledCode code = getCode(method);
        Result actual;

        try {
            actual = new Result(code.executeVarargs(dummy), null);
        } catch (Exception e) {
            actual = new Result(null, e);
        }

        // The code should get deoptimized, and resume execution at the then-branch.
        assertEquals(new Result(1, null), actual);
        assertFalse(code.isValid());
    }

}
