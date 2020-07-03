/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.options.OptionValues;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/*
 */
public class Math_abs extends UnaryMath {
    @SuppressWarnings("serial")
    public static class NaN extends Throwable {
    }

    public static double testAbsD(double arg) throws NaN {
        double v = Math.abs(arg);
        if (Double.isNaN(v)) {
            // NaN can't be tested against itself
            throw new NaN();
        }
        return v;
    }

    @Test
    public void run0() throws Throwable {
        runTest("testAbsD", 5.0d);
    }

    @Test
    public void run1() throws Throwable {
        runTest("testAbsD", -5.0d);
    }

    @Test
    public void run2() throws Throwable {
        runTest("testAbsD", 0.0d);
    }

    @Test
    public void run3() throws Throwable {
        runTest("testAbsD", -0.0d);
    }

    @Test
    public void run4() throws Throwable {
        runTest("testAbsD", java.lang.Double.NEGATIVE_INFINITY);
    }

    @Test
    public void run5() throws Throwable {
        runTest("testAbsD", java.lang.Double.POSITIVE_INFINITY);
    }

    @Test
    public void run6() throws Throwable {
        runTest("testAbsD", java.lang.Double.NaN);
    }

    @Test
    public void run7() {
        OptionValues options = getInitialOptions();
        ResolvedJavaMethod method = getResolvedJavaMethod("testAbsD");
        testManyValues(options, method);
    }

    public static int testAbsI(int arg) {
        return Math.abs(arg);
    }

    public static long testAbsL(long arg) {
        return Math.abs(arg);
    }

    @Test
    public void run8() {
        runTest("testAbsI", Integer.MIN_VALUE);
        runTest("testAbsI", -326543323);
        runTest("testAbsI", -21325);
        runTest("testAbsI", -0);
        runTest("testAbsI", 5432);
        runTest("testAbsI", 352438548);
        runTest("testAbsI", Integer.MAX_VALUE);
    }

    @Test
    public void run9() {
        runTest("testAbsL", Long.MIN_VALUE);
        runTest("testAbsL", -425423654342L);
        runTest("testAbsL", -21543224L);
        runTest("testAbsL", -0L);
        runTest("testAbsL", 1325488L);
        runTest("testAbsL", 313567897765L);
        runTest("testAbsL", Long.MAX_VALUE);
    }
}
