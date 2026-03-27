/*
 * Copyright (c) 2026 SAP SE. All rights reserved.
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
 *
 */

package compiler.vectorapi;

import java.util.Arrays;

import compiler.lib.ir_framework.*;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorSpecies;

import static jdk.test.lib.Asserts.*;

/**
 * @test
 * @bug 8380565
 * @library /test/lib /
 * @summary Test deoptimization involving vector reallocation
 * @modules jdk.incubator.vector
 * @requires vm.opt.final.MaxVectorSize == null | vm.opt.final.MaxVectorSize >= 16
 *
 * @run driver compiler.vectorapi.TestVectorReallocation
 */

public class TestVectorReallocation {

    private static final VectorSpecies<Byte>    B_SPECIES = ByteVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Short>   S_SPECIES = ShortVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Long>    L_SPECIES = LongVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Float>   F_SPECIES = FloatVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Double>  D_SPECIES = DoubleVector.SPECIES_PREFERRED;
    private static final int B_LENGTH = B_SPECIES.length();
    private static final int S_LENGTH = S_SPECIES.length();
    private static final int I_LENGTH = I_SPECIES.length();
    private static final int L_LENGTH = L_SPECIES.length();
    private static final int F_LENGTH = F_SPECIES.length();
    private static final int D_LENGTH = D_SPECIES.length();

    // The input arrays for the @Test methods match the length of the preferred species for each type
    private static byte[]   b_a;
    private static short[]  s_a;
    private static int[]    i_a;
    private static long[]   l_a;
    private static float[]  f_a;
    private static double[] d_a;

    // The output arrays for the @Test methods
    private static byte[]   b_r;
    private static short[]  s_r;
    private static int[]    i_r;
    private static long[]   l_r;
    private static float[]  f_r;
    private static double[] d_r;

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }

    // The test methods annotated with @Test are warmed up by the framework. The calls are indirect
    // through the runner methods annotated with @Run. Note that each @Test method has its own instance of
    // the test class TestVectorReallocation as receiver of the calls.
    //
    // The @Test methods just copy the elements of the input array (0, 1, 2, 3, ...) to the output array
    // by means of a vector add operation. The added value is computed but actually always zero. The
    // computation is done in a loop with a virtual call that is inlined based on class hierarchy analysis
    // when the method gets compiled.
    //
    // The final call after warmup of the now compiled @Test method is performed concurrently in a second
    // thread. Before the variable `loopIterations` is set very high such that the loop runs practically
    // infinitely. While the loop is running, a class with an overridden version of the method `value`
    // (called in the loop) is loaded. This invalidates the result of the class hierarchy analysis that
    // there is just one implementation of the method and causes deoptimization where the vector `v0` used
    // in the @Test method is reallocated from a register to the java heap. Finally it is verified that
    // input and ouput arrays are equal.
    //
    // NB: each @Test needs its own Zero class for the desired result of the class hierarchy analysis.

    volatile boolean enteredLoop;
    volatile long loopIterations;

    void sharedRunner(RunInfo runInfo, Runnable test, Runnable loadOverridingClass, Runnable verify) {
        enteredLoop = false;
        if (runInfo.isWarmUp()) {
            loopIterations = 100;
            test.run();
        } else {
            loopIterations = 1L << 60; // basically infinite
            Thread t = Thread.ofPlatform().start(test);
            waitUntilLoopEntered();
            loadOverridingClass.run(); // invalidates inlining causing deoptimization/reallocation of v0
            loopIterations = 0;
            waitUntilLoopLeft();
            joinThread(t);
            verify.run();              // verify that input and ouput arrays are equal
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////
    // byte

    static class ByteZero {
        volatile byte zero;
        byte value() {
            return zero;
        }
    }
    volatile ByteZero byteZero = new ByteZero();

    @Run(test = "byteIdentityWithReallocation")
    void byteIdentityWithReallocation_runner(RunInfo runInfo) {
        sharedRunner(runInfo, () -> byteIdentityWithReallocation(), () -> {
            // Loading the class with the overridden method will cause deoptimization and reallocation of v0
            byteZero = new ByteZero() {
                    @Override
                    byte value() {
                        return super.value(); // override but doing the same
                    }
                };
            },
            () -> assertTrue(Arrays.equals(b_a, b_r), "Input/Output arrays differ"));
    }

    @Test
    @IR(counts = {IRNode.ADD_VB, " >0 "})
    void byteIdentityWithReallocation() {
        ByteVector v0 = ByteVector.fromArray(B_SPECIES, b_a, 0);
        byte zeroSum = 0;
        enteredLoop = true;
        for (long i = 0; i < loopIterations; i++) {
            zeroSum += byteZero.value(); // inlined based on class hierarchy analysis
        }
        v0.add(zeroSum).intoArray(b_r, 0);
        enteredLoop = false;
    }

    /////////////////////////////////////////////////////////////////////////////////////
    // short

    static class ShortZero {
        volatile short zero;
        short value() {
            return zero;
        }
    }
    volatile ShortZero shortZero = new ShortZero();

    @Run(test = "shortIdentityWithReallocation")
    void shortIdentityWithReallocation_runner(RunInfo runInfo) {
        sharedRunner(runInfo, () -> shortIdentityWithReallocation(), () -> {
            // Loading the class with the overridden method will cause deoptimization and reallocation of v0
            shortZero = new ShortZero() {
                    @Override
                    short value() {
                        return super.value(); // override but doing the same
                    }
                };
            },
            () -> assertTrue(Arrays.equals(s_a, s_r), "Input/Output arrays differ"));
    }

    @Test
    @IR(counts = {IRNode.ADD_VS, " >0 "})
    void shortIdentityWithReallocation() {
        ShortVector v0 = ShortVector.fromArray(S_SPECIES, s_a, 0);
        short zeroSum = 0;
        enteredLoop = true;
        for (long i = 0; i < loopIterations; i++) {
            zeroSum += shortZero.value(); // inlined based on class hierarchy analysis
        }
        v0.add(zeroSum).intoArray(s_r, 0);
        enteredLoop = false;
    }

    /////////////////////////////////////////////////////////////////////////////////////
    // int

    static class IntZero {
        volatile int zero;
        int value() {
            return zero;
        }
    }
    volatile IntZero intZero = new IntZero();

    @Run(test = "intIdentityWithReallocation")
    void intIdentityWithReallocation_runner(RunInfo runInfo) {
        sharedRunner(runInfo, () -> intIdentityWithReallocation(), () -> {
            // Loading the class with the overridden method will cause deoptimization and reallocation of v0
            intZero = new IntZero() {
                    @Override
                    int value() {
                        return super.value(); // override but doing the same
                    }
                };
            },
            () -> assertTrue(Arrays.equals(i_a, i_r), "Input/Output arrays differ"));
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, " >0 "})
    void intIdentityWithReallocation() {
        IntVector v0 = IntVector.fromArray(I_SPECIES, i_a, 0);
        int zeroSum = 0;
        enteredLoop = true;
        for (long i = 0; i < loopIterations; i++) {
            zeroSum += intZero.value(); // inlined based on class hierarchy analysis
        }
        v0.add(zeroSum).intoArray(i_r, 0);
        enteredLoop = false;
    }

    /////////////////////////////////////////////////////////////////////////////////////
    // long

    static class LongZero {
        volatile long zero;
        long value() {
            return zero;
        }
    }
    volatile LongZero longZero = new LongZero();

    @Run(test = "longIdentityWithReallocation")
    void longIdentityWithReallocation_runner(RunInfo runInfo) {
        sharedRunner(runInfo, () -> longIdentityWithReallocation(), () -> {
            // Loading the class with the overridden method will cause deoptimization and reallocation of v0
            longZero = new LongZero() {
                    @Override
                    long value() {
                        return super.value(); // override but doing the same
                    }
                };
            },
            () -> assertTrue(Arrays.equals(l_a, l_r), "Input/Output arrays differ"));
    }

    @Test
    @IR(counts = {IRNode.ADD_VL, " >0 "})
    void longIdentityWithReallocation() {
        LongVector v0 = LongVector.fromArray(L_SPECIES, l_a, 0);
        long zeroSum = 0;
        enteredLoop = true;
        for (long i = 0; i < loopIterations; i++) {
            zeroSum += longZero.value(); // inlined based on class hierarchy analysis
        }
        v0.add(zeroSum).intoArray(l_r, 0);
        enteredLoop = false;
    }

    /////////////////////////////////////////////////////////////////////////////////////
    // float

    static class FloatZero {
        volatile float zero;
        float value() {
            return zero;
        }
    }
    volatile FloatZero floatZero = new FloatZero();

    @Run(test = "floatIdentityWithReallocation")
    void floatIdentityWithReallocation_runner(RunInfo runInfo) {
        sharedRunner(runInfo, () -> floatIdentityWithReallocation(), () -> {
            // Loading the class with the overridden method will cause deoptimization and reallocation of v0
            floatZero = new FloatZero() {
                    @Override
                    float value() {
                        return super.value(); // override but doing the same
                    }
                };
            },
            () -> assertTrue(Arrays.equals(f_a, f_r), "Input/Output arrays differ"));
    }

    @Test
    @IR(counts = {IRNode.ADD_VF, " >0 "})
    void floatIdentityWithReallocation() {
        FloatVector v0 = FloatVector.fromArray(F_SPECIES, f_a, 0);
        float zeroSum = 0;
        enteredLoop = true;
        for (long i = 0; i < loopIterations; i++) {
            zeroSum += floatZero.value(); // inlined based on class hierarchy analysis
        }
        v0.add(zeroSum).intoArray(f_r, 0);
        enteredLoop = false;
    }

    /////////////////////////////////////////////////////////////////////////////////////
    // double

    static class DoubleZero {
        volatile double zero;
        double value() {
            return zero;
        }
    }
    volatile DoubleZero doubleZero = new DoubleZero();

    @Run(test = "doubleIdentityWithReallocation")
    void doubleIdentityWithReallocation_runner(RunInfo runInfo) {
        sharedRunner(runInfo, () -> doubleIdentityWithReallocation(), () -> {
            // Loading the class with the overridden method will cause deoptimization and reallocation of v0
            doubleZero = new DoubleZero() {
                    @Override
                    double value() {
                        return super.value(); // override but doing the same
                    }
                };
            },
            () -> assertTrue(Arrays.equals(d_a, d_r), "Input/Output arrays differ"));
    }

    @Test
    @IR(counts = {IRNode.ADD_VD, " >0 "})
    void doubleIdentityWithReallocation() {
        DoubleVector v0 = DoubleVector.fromArray(D_SPECIES, d_a, 0);
        double zeroSum = 0;
        enteredLoop = true;
        for (long i = 0; i < loopIterations; i++) {
            zeroSum += doubleZero.value(); // inlined based on class hierarchy analysis
        }
        v0.add(zeroSum).intoArray(d_r, 0);
        enteredLoop = false;
    }

    /////////////////////////////////////////////////////////////////////////////////////

    private void waitUntilLoopEntered() {
        while (!enteredLoop) {
            sleep(10);
        }
    }

    private void waitUntilLoopLeft() {
        while (enteredLoop) {
            sleep(10);
        }
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) { /* ignore */ }
    }

    private static void joinThread(Thread t) {
        try {
            t.join();
        } catch (InterruptedException e) { /* ignore */ }
    }

    static {
        b_a = new byte[B_LENGTH];
        s_a = new short[S_LENGTH];
        i_a = new int[I_LENGTH];
        l_a = new long[L_LENGTH];
        f_a = new float[F_LENGTH];
        d_a = new double[D_LENGTH];

        b_r = new byte[B_LENGTH];
        s_r = new short[S_LENGTH];
        i_r = new int[I_LENGTH];
        l_r = new long[L_LENGTH];
        f_r = new float[F_LENGTH];
        d_r = new double[D_LENGTH];

        for (int i = 0; i < b_a.length ; i++) {
            b_a[i] = (byte)i;
        }
        for (int i = 0; i < s_a.length ; i++) {
            s_a[i] = (short)i;
        }
        for (int i = 0; i < i_a.length ; i++) {
            i_a[i] = i;
        }
        for (int i = 0; i < l_a.length ; i++) {
            l_a[i] = i;
        }
        for (int i = 0; i < f_a.length ; i++) {
            f_a[i] = i;
        }
        for (int i = 0; i < d_a.length ; i++) {
            d_a[i] = i;
        }
    }
}
