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
public abstract class BC_float_base extends JTTTest {

    /** Some interesting values. */
    private static final float[] values = {
                    0.0f,
                    -0.0f,
                    1.0f,
                    -1.0f,
                    Float.POSITIVE_INFINITY,
                    Float.NEGATIVE_INFINITY,
                    Float.NaN,
                    10.0f,
                    -10.0f,
                    311.0f,
                    -311.0f,
    };

    @Parameters(name = "{0}, {1}")
    public static Collection<Object[]> data() {
        List<Object[]> d = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            float x = values[i];
            for (int j = 0; j < values.length; j++) {
                float y = values[j];
                d.add(new Object[]{x, y});
            }
        }
        return d;
    }

    @Parameter(value = 0) public float x;
    @Parameter(value = 1) public float y;

}
