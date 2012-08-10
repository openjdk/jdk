/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

/* @test
 * @summary BoundMethodHandle tests with primitive types
 * @compile MaxTest.java
 * @run junit/othervm test.java.lang.invoke.MaxTest
 */

package test.java.lang.invoke;

import static org.junit.Assert.assertEquals;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.junit.Test;

public class MaxTest {

    static MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private MethodHandle getMax(Class<?> t) throws Throwable {
        return LOOKUP.findStatic(Math.class, "max", MethodType.methodType(t, t, t));
    }

    static int ITERATION_COUNT = 40000;
    static {
        String iterations = System.getProperty(MaxTest.class.getSimpleName() + ".ITERATION_COUNT");
        if (iterations == null) {
            iterations = System.getProperty(MaxTest.class.getName() + ".ITERATION_COUNT");
        }
        if (iterations != null) {
            ITERATION_COUNT = Integer.parseInt(iterations);
        }
    }

    @Test
    public void testMaxLong() throws Throwable {
        final Class<?> C = long.class;
        final long P = 23L;
        final long Q = 42L;
        final long R = Math.max(P, Q);
        for (int i = 0; i < ITERATION_COUNT; ++i) {
            MethodHandle h = getMax(C);
            assertEquals((long) h.invokeExact(P, Q), R);
            MethodHandle bh = MethodHandles.insertArguments(h, 0, P);
            assertEquals((long) bh.invokeExact(Q), R);
            MethodHandle bbh = MethodHandles.insertArguments(bh, 0, Q);
            assertEquals((long) bbh.invokeExact(), R);
            MethodHandle b2h = MethodHandles.insertArguments(h, 1, Q);
            assertEquals((long) b2h.invokeExact(P), R);
            MethodHandle bb2h = MethodHandles.insertArguments(b2h, 0, P);
            assertEquals((long) bb2h.invokeExact(), R);
        }
    }

    @Test
    public void testMaxInt() throws Throwable {
        final Class<?> C = int.class;
        final int P = 23;
        final int Q = 42;
        final int R = Math.max(P, Q);
        for (int i = 0; i < ITERATION_COUNT; ++i) {
            MethodHandle h = getMax(C);
            assertEquals((int) h.invokeExact(P, Q), R);
            MethodHandle bh = MethodHandles.insertArguments(h, 0, P);
            assertEquals((int) bh.invokeExact(Q), R);
            MethodHandle bbh = MethodHandles.insertArguments(bh, 0, Q);
            assertEquals((int) bbh.invokeExact(), R);
            MethodHandle b2h = MethodHandles.insertArguments(h, 1, Q);
            assertEquals((int) b2h.invokeExact(P), R);
            MethodHandle bb2h = MethodHandles.insertArguments(b2h, 0, P);
            assertEquals((int) bb2h.invokeExact(), R);
        }
    }

    @Test
    public void testMaxFloat() throws Throwable {
        final Class<?> C = float.class;
        final float P = 23F;
        final float Q = 42F;
        final float R = Math.max(P, Q);
        final float D = 0.1F;
        for (int i = 0; i < ITERATION_COUNT; ++i) {
            MethodHandle h = getMax(C);
            assertEquals((float) h.invokeExact(P, Q), R, D);
            MethodHandle bh = MethodHandles.insertArguments(h, 0, P);
            assertEquals((float) bh.invokeExact(Q), R, D);
            MethodHandle bbh = MethodHandles.insertArguments(bh, 0, Q);
            assertEquals((float) bbh.invokeExact(), R, D);
            MethodHandle b2h = MethodHandles.insertArguments(h, 1, Q);
            assertEquals((float) b2h.invokeExact(P), R, D);
            MethodHandle bb2h = MethodHandles.insertArguments(b2h, 0, P);
            assertEquals((float) bb2h.invokeExact(), R, D);
        }
    }

    @Test
    public void testMaxDouble() throws Throwable {
        final Class<?> C = double.class;
        final double P = 23F;
        final double Q = 42F;
        final double R = Math.max(P, Q);
        final double D = 0.1;
        for (int i = 0; i < ITERATION_COUNT; ++i) {
            MethodHandle h = getMax(C);
            assertEquals((double) h.invokeExact(P, Q), R, D);
            MethodHandle bh = MethodHandles.insertArguments(h, 0, P);
            assertEquals((double) bh.invokeExact(Q), R, D);
            MethodHandle bbh = MethodHandles.insertArguments(bh, 0, Q);
            assertEquals((double) bbh.invokeExact(), R, D);
            MethodHandle b2h = MethodHandles.insertArguments(h, 1, Q);
            assertEquals((double) b2h.invokeExact(P), R, D);
            MethodHandle bb2h = MethodHandles.insertArguments(b2h, 0, P);
            assertEquals((double) bb2h.invokeExact(), R, D);
        }
    }

}
