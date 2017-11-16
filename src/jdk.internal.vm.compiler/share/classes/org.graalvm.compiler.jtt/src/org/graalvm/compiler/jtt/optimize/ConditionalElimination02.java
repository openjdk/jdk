/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt.optimize;

import java.util.EnumSet;

import jdk.vm.ci.meta.DeoptimizationReason;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.OptimisticOptimizations.Optimization;
import org.graalvm.compiler.phases.tiers.HighTierContext;

public class ConditionalElimination02 extends JTTTest {

    private static Object o = null;

    private static class A {

        A(int y) {
            this.y = y;
        }

        int y;
    }

    public int test(A a, boolean isNull, boolean isVeryNull) {
        if (o == null) {
            if (!isNull) {
                if (o == null) {
                    return a.y;
                }
            }
            if (!isVeryNull) {
                if (o == null) {
                    return a.y;
                }
            }
        }
        return -1;
    }

    /**
     * These tests assume all code paths are reachable so disable profile based dead code removal.
     */
    @Override
    protected HighTierContext getDefaultHighTierContext() {
        return new HighTierContext(getProviders(), getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL.remove(Optimization.RemoveNeverExecutedCode));
    }

    @Test
    public void run0() throws Throwable {
        runTest(EnumSet.of(DeoptimizationReason.NullCheckException), "test", new A(5), false, false);
    }

    @Test
    public void run1() throws Throwable {
        runTest(EnumSet.of(DeoptimizationReason.NullCheckException), "test", new Object[]{null, true, true});
    }

}
