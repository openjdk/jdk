/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.jdk9.test;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class MathDoubleFMATest extends GraalCompilerTest {

    @Before
    public void checkNotSPARC() {
        assumeFalse("skipping tests on SPARC", isSPARC(getTarget().arch));
        HotSpotGraalRuntimeProvider rt = (HotSpotGraalRuntimeProvider) Graal.getRequiredCapability(RuntimeProvider.class);
        assumeTrue("skipping FMA specific test", rt.getVMConfig().useFMAIntrinsics);
    }

    @Parameters(name = "{0}, {1}, {2}")
    public static Collection<Object[]> data() {
        double[] inputs = {0.0d, 1.0d, 4.0d, -0.0d, -1.0d, -4.0d, Double.MIN_VALUE, Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
                        Double.NaN, Double.longBitsToDouble(0xfff0000000000001L)};

        List<Object[]> tests = new ArrayList<>();
        for (double a : inputs) {
            for (double b : inputs) {
                for (double c : inputs) {
                    tests.add(new Object[]{a, b, c});
                }
            }
        }
        return tests;
    }

    @Parameter(value = 0) public double input0;
    @Parameter(value = 1) public double input1;
    @Parameter(value = 2) public double input2;

    public static double fma(double a, double b, double c) {
        return Math.fma(a, b, c);
    }

    @Test
    public void testFMA() {
        test("fma", input0, input1, input2);
    }

}
