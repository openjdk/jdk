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

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.junit.Before;
import org.junit.Test;

public final class MathFMAConstantInputTest extends GraalCompilerTest {

    @Before
    public void checkNotSPARC() {
        assumeFalse("skipping test on SPARC ", isSPARC(getTarget().arch));
    }

    public static float floatFMA() {
        return Math.fma(2.0f, 2.0f, 2.0f);
    }

    @Test
    public void testFloatFMA() {
        test("floatFMA");
    }

    public static float floatFMAWithPi() {
        float[] input = {Float.MAX_VALUE, 2.0F, -Float.MAX_VALUE};
        return Math.fma(input[0], input[1], input[2]);
    }

    @Test
    public void testFloatFMAWithPi() {
        test("floatFMAWithPi");
    }

    public static double doubleFMA() {
        return Math.fma(2.0d, 2.0d, 2.0d);
    }

    @Test
    public void testDoubleFMA() {
        test("doubleFMA");
    }

    public static double doubleFMAWithPi() {
        double[] input = {Double.MAX_VALUE, 2.0D, -Double.MAX_VALUE};
        return Math.fma(input[0], input[1], input[2]);
    }

    @Test
    public void testDoubleFMAWithPi() {
        test("doubleFMAWithPi");
    }

}
