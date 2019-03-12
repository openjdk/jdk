/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.meta.SpeculationLog;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.DebugContext;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class DeoptimizeOnIntegerExactTest extends GraalCompilerTest {

    private final SpeculationLog speculationLog;

    static boolean highlyLikely = true;
    static boolean highlyUnlikely = false;

    public DeoptimizeOnIntegerExactTest() {
        speculationLog = getCodeCache().createSpeculationLog();
    }

    public static int testAddExactSnippet(int x, int y) {
        if (highlyLikely) {
            return highlyUnlikely ? Math.addExact(x, y) : x;
        } else {
            return highlyUnlikely ? y : Math.addExact(x, y);
        }
    }

    public static int testSubtractExactSnippet(int x, int y) {
        if (highlyLikely) {
            return highlyUnlikely ? Math.subtractExact(x, y) : x;
        } else {
            return highlyUnlikely ? y : Math.subtractExact(x, y);
        }
    }

    public static int testMultiplyExactSnippet(int x, int y) {
        if (highlyLikely) {
            return highlyUnlikely ? Math.multiplyExact(x, y) : x;
        } else {
            return highlyUnlikely ? y : Math.multiplyExact(x, y);
        }
    }

    public static int testIncrementExactSnippet(int x, int y) {
        if (highlyLikely) {
            return highlyUnlikely ? Math.incrementExact(x) : x;
        } else {
            return highlyUnlikely ? y : Math.incrementExact(x);
        }
    }

    public static int testDecrementExactSnippet(int x, int y) {
        if (highlyLikely) {
            return highlyUnlikely ? Math.decrementExact(x) : x;
        } else {
            return highlyUnlikely ? y : Math.decrementExact(x);
        }
    }

    public void testAgainIfDeopt(String methodName, int x, int y) throws InvalidInstalledCodeException {
        ResolvedJavaMethod method = getResolvedJavaMethod(methodName);
        // We speculate on the first compilation. The global value numbering will merge the two
        // floating integer exact operation nodes.
        InstalledCode code = getCode(method);
        code.executeVarargs(x, y);
        if (!code.isValid()) {
            // At the recompilation, we anchor the floating integer exact operation nodes at their
            // corresponding branches.
            code = getCode(method);
            code.executeVarargs(x, y);
            // The recompiled code should not get deoptimized.
            assertTrue(code.isValid());
        }
    }

    @Test
    public void testAddExact() throws InvalidInstalledCodeException {
        testAgainIfDeopt("testAddExactSnippet", Integer.MAX_VALUE, 1);
    }

    @Test
    public void testSubtractExact() throws InvalidInstalledCodeException {
        testAgainIfDeopt("testSubtractExactSnippet", 0, Integer.MIN_VALUE);
    }

    @Test
    public void testMultiplyExact() throws InvalidInstalledCodeException {
        testAgainIfDeopt("testMultiplyExactSnippet", Integer.MAX_VALUE, 2);
    }

    @Test
    public void testIncrementExact() throws InvalidInstalledCodeException {
        testAgainIfDeopt("testIncrementExactSnippet", Integer.MAX_VALUE, 1);
    }

    @Test
    public void testDecrementExact() throws InvalidInstalledCodeException {
        testAgainIfDeopt("testDecrementExactSnippet", Integer.MIN_VALUE, 1);
    }

    @Override
    protected SpeculationLog getSpeculationLog() {
        speculationLog.collectFailedSpeculations();
        return speculationLog;
    }

    @Override
    protected InstalledCode addMethod(DebugContext debug, final ResolvedJavaMethod method, final CompilationResult compilationResult) {
        assert speculationLog == compilationResult.getSpeculationLog();
        return getBackend().createInstalledCode(debug, method, compilationResult, null, false);
    }
}
