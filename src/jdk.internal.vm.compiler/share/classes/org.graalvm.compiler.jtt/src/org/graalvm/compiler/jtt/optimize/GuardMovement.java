/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Assert;
import org.junit.Test;

import static org.graalvm.compiler.core.common.GraalOptions.MaximumInliningSize;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.jtt.JTTTest;
import org.graalvm.compiler.options.OptionValues;

public class GuardMovement extends JTTTest {

    private static int staticValue;

    private static class A {
        int x;
        int y;
    }

    public int foo(A a) {
        Assert.assertNotEquals("a cannot be null, because a field of a is accessed before the call", a, null);
        return 42;
    }

    @SuppressWarnings("all")
    public int test(A a) {

        int value;
        int result = 0;

        // Use a condition that folds after floating guards and before guard lowering.
        // After disabling PEA and read elimination, the following condition does the trick.
        if (staticValue == staticValue) {
            // Access a.x to generate a null checked value.
            value = a.x;
            result = foo(a);
        }

        // Access a.y to generate another null checked value.
        return result + a.y;
    }

    @Test
    public void run0() throws Throwable {
        OptionValues options = new OptionValues(getInitialOptions(), MaximumInliningSize, -1, GraalOptions.TrivialInliningSize, -1, GraalOptions.PartialEscapeAnalysis, false,
                        GraalOptions.OptReadElimination, false);
        runTest(options, "test", new A());
        runTest(options, "test", new Object[]{null});
    }
}
