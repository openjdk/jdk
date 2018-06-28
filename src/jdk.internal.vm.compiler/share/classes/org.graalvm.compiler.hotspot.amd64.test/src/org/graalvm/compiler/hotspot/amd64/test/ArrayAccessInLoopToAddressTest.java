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


package org.graalvm.compiler.hotspot.amd64.test;

import jdk.vm.ci.hotspot.HotSpotSpeculationLog;
import jdk.vm.ci.meta.SpeculationLog;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.junit.Test;

public class ArrayAccessInLoopToAddressTest extends GraalCompilerTest {

    public static int positiveInductionVariable(short[] array) {
        int sum = 0;
        for (int i = 0; i < array.length - 1; i++) {
            sum += array[i + 1];
        }
        return sum;
    }

    @Test
    public void testPositiveInductionVariable() {
        test("positiveInductionVariable", new short[]{1, 3, 7, 9});
    }

    public static int negativeInductionVariable(short[] array) {
        int sum = 0;
        for (int i = -array.length; i < array.length - 4; i++) {
            sum += array[i + 4];
        }
        return sum;
    }

    @Test
    public void testNegativeInductionVariable() {
        test("negativeInductionVariable", new short[]{1, 3, 7, 9});
    }

    @Override
    protected SpeculationLog getSpeculationLog() {
        return new HotSpotSpeculationLog();
    }

}
