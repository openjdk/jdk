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


package org.graalvm.compiler.jtt.lang;

import org.graalvm.compiler.options.OptionValues;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/*
 */
public class Math_sin extends UnaryMath {

    @SuppressWarnings("serial")
    public static class NaN extends Throwable {
    }

    public static double test(double arg) throws NaN {
        double v = Math.sin(arg) * Math.sin(arg * 5);
        if (Double.isNaN(v)) {
            // NaN can't be tested against itself
            throw new NaN();
        }
        return v;
    }

    @Test
    public void runFirst() throws Throwable {
        /*
         * Execute Double.isNaN enough times to create a profile indicating that the path returning
         * false is never taken. Then compile and execute the test with a NaN value to test that
         * deoptimization works in the case of an uncommon trap inlined into an intrinsic. Of
         * course, this relies on Double.isNaN never having yet been called with NaN. if it has,
         * this test is equivalent to run0.
         */
        for (int i = 0; i < 10000; i++) {
            Double.isNaN(1D);
        }
        executeActual(getResolvedJavaMethod("test"), null, java.lang.Double.NaN);
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", java.lang.Double.NaN);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", java.lang.Double.NEGATIVE_INFINITY);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test", java.lang.Double.POSITIVE_INFINITY);
    }

    @Test
    public void run3() throws Throwable {
        runTest("test", -0.0d);
    }

    @Test
    public void run4() throws Throwable {
        runTest("test", 0.0d);
    }

    @Test
    public void run5() {
        OptionValues options = getInitialOptions();
        ResolvedJavaMethod method = getResolvedJavaMethod("test");
        testManyValues(options, method);
    }
}
