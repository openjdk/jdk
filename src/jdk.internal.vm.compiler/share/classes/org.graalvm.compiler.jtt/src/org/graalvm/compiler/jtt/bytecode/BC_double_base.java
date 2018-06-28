/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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



package org.graalvm.compiler.jtt.bytecode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import org.graalvm.compiler.jtt.JTTTest;

@RunWith(Parameterized.class)
public abstract class BC_double_base extends JTTTest {

    /** Some interesting values. */
    private static final double[] values = {
                    0.0d,
                    -0.0d,
                    1.0d,
                    -1.0d,
                    Double.POSITIVE_INFINITY,
                    Double.NEGATIVE_INFINITY,
                    Double.NaN,
                    10.0d,
                    -10.0d,
                    311.0d,
                    -311.0d,
    };

    @Parameters(name = "{0}, {1}")
    public static Collection<Object[]> data() {
        List<Object[]> d = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            double x = values[i];
            for (int j = 0; j < values.length; j++) {
                double y = values[j];
                d.add(new Object[]{x, y});
            }
        }
        return d;
    }

    @Parameter(value = 0) public double x;
    @Parameter(value = 1) public double y;
}
