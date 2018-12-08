/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.jtt.lang;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import org.graalvm.compiler.jtt.JTTTest;

@RunWith(Parameterized.class)
public class Math_round extends JTTTest {

    @Parameter(value = 0) public double input;

    public static double rint(double arg) {
        return Math.rint(arg);
    }

    @Test
    public void runRint() throws Throwable {
        runTest("rint", input);
    }

    public static double floor(double arg) {
        return Math.floor(arg);
    }

    @Test
    public void runFloor() throws Throwable {
        runTest("floor", input);
    }

    public static double ceil(double arg) {
        return Math.ceil(arg);
    }

    @Test
    public void runCeil() throws Throwable {
        runTest("ceil", input);
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> tests = new ArrayList<>();
        for (int i = -3; i < 3; i++) {
            addTest(tests, i);
            addTest(tests, i + 0.2);
            addTest(tests, i + 0.5);
            addTest(tests, i + 0.7);
        }
        addTest(tests, -0.0);
        addTest(tests, Double.NaN);
        addTest(tests, Double.NEGATIVE_INFINITY);
        addTest(tests, Double.POSITIVE_INFINITY);
        return tests;
    }

    private static void addTest(ArrayList<Object[]> tests, double input) {
        tests.add(new Object[]{input});
    }
}
