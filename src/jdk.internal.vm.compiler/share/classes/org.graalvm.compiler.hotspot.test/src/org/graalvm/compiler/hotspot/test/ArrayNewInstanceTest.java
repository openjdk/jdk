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


package org.graalvm.compiler.hotspot.test;

import java.lang.reflect.Array;
import java.util.ArrayList;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArrayNewInstanceTest extends GraalCompilerTest {

    @Parameters(name = "{index}: class {0} length {1}")
    public static Iterable<Object[]> data() {
        ArrayList<Object[]> parameters = new ArrayList<>();
        Class<?>[] classesToTest = new Class<?>[]{
                        byte.class,
                        boolean.class,
                        short.class,
                        char.class,
                        int.class,
                        long.class,
                        Void.class,
                        ArrayNewInstanceTest.class
        };
        for (Class<?> clazz : classesToTest) {
            // Negative sizes always deopt
            parameters.add(new Object[]{clazz, -1, true});
            parameters.add(new Object[]{clazz, 0, false});
            parameters.add(new Object[]{clazz, 42, false});
        }
        // The void type always throws an exception where graal deopts
        parameters.add(new Object[]{void.class, -1, true});
        parameters.add(new Object[]{void.class, 0, true});
        parameters.add(new Object[]{void.class, 42, true});
        return parameters;
    }

    private final Class<?> type;
    private final int length;
    private final boolean shouldDeopt;
    private final DeoptimizationBox box = new DeoptimizationBox();

    public ArrayNewInstanceTest(Class<?> type, int length, boolean shouldDeopt) {
        super();
        this.type = type;
        this.length = length;
        this.shouldDeopt = shouldDeopt;
    }

    public static Object newArray(Class<?> klass, int length, DeoptimizationBox box) {
        Object result = Array.newInstance(klass, length);
        box.inCompiledCode = GraalDirectives.inCompiledCode();
        return result;
    }

    @Test
    public void testNewArray() {
        test("newArray", type, length, box);
        assertTrue(box.inCompiledCode != shouldDeopt);
    }

    public static Object newArrayInLoop(Class<?> klass, int length, int iterations, DeoptimizationBox box) {
        Object o = null;
        for (int i = 0; i < iterations; i++) {
            o = Array.newInstance(klass, length);
        }
        box.inCompiledCode = GraalDirectives.inCompiledCode();
        return o;
    }

    @Test
    public void testNewArrayInLoop() {
        test("newArrayInLoop", type, length, 2, box);
        assertTrue(box.inCompiledCode != shouldDeopt);
    }

    private static class DeoptimizationBox {
        volatile boolean inCompiledCode = false;
    }

}
