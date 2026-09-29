/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.vectorapi;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import jdk.incubator.vector.*;

/*
 * @test
 * @bug 8375631
 * @key randomness
 * @summary Testing part number range exception.
 * @modules jdk.incubator.vector
 * @run main ${test.main.class}
 */
public class PartNumberTest {
    public static void main(String[] args) {
        runExamples();
        runExhaustive();
    }

    @FunctionalInterface
    interface TestMethod {
        Object run();
    }

    public static void expectSuccess(TestMethod t) {
        try {
            t.run();
        } catch (Exception e) {
            throw new RuntimeException("Test failed unexpectedly: ", e);
        }
    }

    public static void expectAIOOBE(TestMethod t, String msg) {
        try {
            t.run();
        } catch (ArrayIndexOutOfBoundsException e) {
            String m = e.getMessage();
            if (m != null && !m.contains(msg)) {
                throw new RuntimeException("Got exception, but with wrong message. Expected '" + msg + "'.", e);
            }
            return; // passed
        } catch (Exception e) {
            throw new RuntimeException("Test failed unexpectedly: ", e);
        }
        throw new RuntimeException("Did not throw exception. Expected '" + msg + "'.");
    }

    public static void runExamples() {
        String msg = null;

        var f2 = FloatVector.broadcast(FloatVector.SPECIES_64, 42.0f);
        msg = " should be in [0..1], output selection with MS=2; logical: conversion lanewise expanding by ML=2; physical: shape-invariant (MP=1); Species[float, 2, S_64_BIT] -> Species[double, 1, S_64_BIT].";
        expectAIOOBE( () -> { return f2.convertShape(VectorOperators.F2D, DoubleVector.SPECIES_64, -1); }, "bad part number -1" + msg);
        expectSuccess(() -> { return f2.convertShape(VectorOperators.F2D, DoubleVector.SPECIES_64, 0); });
        expectSuccess(() -> { return f2.convertShape(VectorOperators.F2D, DoubleVector.SPECIES_64, 1); });
        expectAIOOBE( () -> { return f2.convertShape(VectorOperators.F2D, DoubleVector.SPECIES_64, 2); }, "bad part number 2" + msg);

        expectAIOOBE( () -> { return f2.convert(VectorOperators.F2D, -1); }, "bad part number -1" + msg);
        expectSuccess(() -> { return f2.convert(VectorOperators.F2D, 0); });
        expectSuccess(() -> { return f2.convert(VectorOperators.F2D, 1); });
        expectAIOOBE( () -> { return f2.convert(VectorOperators.F2D, 2); }, "bad part number 2" + msg);

        msg = " should be 0, output in-place (MO=MS=1); logical: conversion lanewise expanding by ML=2; physical: expansion by MP=2; Species[float, 2, S_64_BIT] -> Species[double, 2, S_128_BIT].";
        expectAIOOBE( () -> { return f2.convertShape(VectorOperators.F2D, DoubleVector.SPECIES_128, -1); }, "bad part number -1" + msg);
        expectSuccess(() -> { return f2.convertShape(VectorOperators.F2D, DoubleVector.SPECIES_128, 0); });
        expectAIOOBE( () -> { return f2.convertShape(VectorOperators.F2D, DoubleVector.SPECIES_128, 1); }, "bad part number 1" + msg);

        msg = " should be in [-1..0], output insertion with MO=2; logical: conversion lanewise expanding by ML=2; physical: expansion by MP=4; Species[float, 2, S_64_BIT] -> Species[double, 4, S_256_BIT]";
        expectAIOOBE( () -> { return f2.convertShape(VectorOperators.F2D, DoubleVector.SPECIES_256, -2); }, "bad part number -2" + msg);
        expectSuccess(() -> { return f2.convertShape(VectorOperators.F2D, DoubleVector.SPECIES_256, -1); });
        expectSuccess(() -> { return f2.convertShape(VectorOperators.F2D, DoubleVector.SPECIES_256, 0); });
        expectAIOOBE( () -> { return f2.convertShape(VectorOperators.F2D, DoubleVector.SPECIES_256, 1); }, "bad part number 1" + msg);

        msg = " should be in [-3..0], output insertion with MO=4; logical: conversion lanewise expanding by ML=2; physical: expansion by MP=8; Species[float, 2, S_64_BIT] -> Species[double, 8, S_512_BIT]";
        expectAIOOBE( () -> { return f2.convertShape(VectorOperators.F2D, DoubleVector.SPECIES_512, -4); }, "bad part number -4" + msg);
        expectSuccess(() -> { return f2.convertShape(VectorOperators.F2D, DoubleVector.SPECIES_512, -3); });
        expectSuccess(() -> { return f2.convertShape(VectorOperators.F2D, DoubleVector.SPECIES_512, -2); });
        expectSuccess(() -> { return f2.convertShape(VectorOperators.F2D, DoubleVector.SPECIES_512, -1); });
        expectSuccess(() -> { return f2.convertShape(VectorOperators.F2D, DoubleVector.SPECIES_512, 0); });
        expectAIOOBE( () -> { return f2.convertShape(VectorOperators.F2D, DoubleVector.SPECIES_512, 1); }, "bad part number 1" + msg);

        msg = " should be 0, output in-place (MO=MS=1); logical: reinterpreting (ML=1); physical: shape-invariant (MP=1); Species[float, 2, S_64_BIT] -> Species[double, 1, S_64_BIT].";
        expectAIOOBE( () -> { return f2.reinterpretShape(DoubleVector.SPECIES_64, -1); }, "bad part number -1" + msg);
        expectSuccess(() -> { return f2.reinterpretShape(DoubleVector.SPECIES_64, 0); });
        expectAIOOBE( () -> { return f2.reinterpretShape(DoubleVector.SPECIES_64, 1); }, "bad part number 1" + msg);

        msg = " should be in [-1..0], output insertion with MO=2; logical: reinterpreting (ML=1); physical: expansion by MP=2; Species[float, 2, S_64_BIT] -> Species[double, 2, S_128_BIT].";
        expectAIOOBE( () -> { return f2.reinterpretShape(DoubleVector.SPECIES_128, -2); }, "bad part number -2" + msg);
        expectSuccess(() -> { return f2.reinterpretShape(DoubleVector.SPECIES_128, -1); });
        expectSuccess(() -> { return f2.reinterpretShape(DoubleVector.SPECIES_128, 0); });
        expectAIOOBE( () -> { return f2.reinterpretShape(DoubleVector.SPECIES_128, 1); }, "bad part number 1" + msg);

        msg = " should be in [-3..0], output insertion with MO=4; logical: reinterpreting (ML=1); physical: expansion by MP=4; Species[float, 2, S_64_BIT] -> Species[double, 4, S_256_BIT].";
        expectAIOOBE( () -> { return f2.reinterpretShape(DoubleVector.SPECIES_256, -4); }, "bad part number -4" + msg);
        expectSuccess(() -> { return f2.reinterpretShape(DoubleVector.SPECIES_256, -3); });
        expectSuccess(() -> { return f2.reinterpretShape(DoubleVector.SPECIES_256, -2); });
        expectSuccess(() -> { return f2.reinterpretShape(DoubleVector.SPECIES_256, -1); });
        expectSuccess(() -> { return f2.reinterpretShape(DoubleVector.SPECIES_256, 0); });
        expectAIOOBE( () -> { return f2.reinterpretShape(DoubleVector.SPECIES_256, 1); }, "bad part number 1" + msg);

        msg = " should be in [-7..0], output insertion with MO=8; logical: reinterpreting (ML=1); physical: expansion by MP=8; Species[float, 2, S_64_BIT] -> Species[double, 8, S_512_BIT].";
        expectAIOOBE( () -> { return f2.reinterpretShape(DoubleVector.SPECIES_512, -8); }, "bad part number -8" + msg);
        expectSuccess(() -> { return f2.reinterpretShape(DoubleVector.SPECIES_512, -7); });
        expectSuccess(() -> { return f2.reinterpretShape(DoubleVector.SPECIES_512, -6); });
        expectSuccess(() -> { return f2.reinterpretShape(DoubleVector.SPECIES_512, -5); });
        expectSuccess(() -> { return f2.reinterpretShape(DoubleVector.SPECIES_512, -4); });
        expectSuccess(() -> { return f2.reinterpretShape(DoubleVector.SPECIES_512, -3); });
        expectSuccess(() -> { return f2.reinterpretShape(DoubleVector.SPECIES_512, -2); });
        expectSuccess(() -> { return f2.reinterpretShape(DoubleVector.SPECIES_512, -1); });
        expectSuccess(() -> { return f2.reinterpretShape(DoubleVector.SPECIES_512, 0); });
        expectAIOOBE( () -> { return f2.reinterpretShape(DoubleVector.SPECIES_512, 1); }, "bad part number 1" + msg);

        expectAIOOBE( () -> { return f2.unslice(1, f2, -1); }, "bad part number -1 for slice operation");
        expectSuccess(() -> { return f2.unslice(1, f2, 0); });
        expectSuccess(() -> { return f2.unslice(1, f2, 1); });
        expectAIOOBE( () -> { return f2.unslice(1, f2, 2); }, "bad part number 2 for slice operation");

        var i8 = IntVector.broadcast(IntVector.SPECIES_256, 42);
        msg = " should be in [0..7], output selection with MS=8; logical: conversion lanewise expanding by ML=2; physical: contraction by MP=1/4; Species[int, 8, S_256_BIT] -> Species[long, 1, S_64_BIT].";
        expectAIOOBE( () -> { return i8.convertShape(VectorOperators.I2L, LongVector.SPECIES_64, -1); }, "bad part number -1" + msg);
        expectSuccess(() -> { return i8.convertShape(VectorOperators.I2L, LongVector.SPECIES_64, 0); });
        expectSuccess(() -> { return i8.convertShape(VectorOperators.I2L, LongVector.SPECIES_64, 1); });
        expectSuccess(() -> { return i8.convertShape(VectorOperators.I2L, LongVector.SPECIES_64, 2); });
        expectSuccess(() -> { return i8.convertShape(VectorOperators.I2L, LongVector.SPECIES_64, 3); });
        expectSuccess(() -> { return i8.convertShape(VectorOperators.I2L, LongVector.SPECIES_64, 4); });
        expectSuccess(() -> { return i8.convertShape(VectorOperators.I2L, LongVector.SPECIES_64, 5); });
        expectSuccess(() -> { return i8.convertShape(VectorOperators.I2L, LongVector.SPECIES_64, 6); });
        expectSuccess(() -> { return i8.convertShape(VectorOperators.I2L, LongVector.SPECIES_64, 7); });
        expectAIOOBE( () -> { return i8.convertShape(VectorOperators.I2L, LongVector.SPECIES_64, 8); }, "bad part number 8" + msg);

        msg = " should be in [0..3], output selection with MS=4; logical: conversion lanewise expanding by ML=2; physical: contraction by MP=1/2; Species[int, 8, S_256_BIT] -> Species[long, 2, S_128_BIT].";
        expectAIOOBE( () -> { return i8.convertShape(VectorOperators.I2L, LongVector.SPECIES_128, -1); }, "bad part number -1" + msg);
        expectSuccess(() -> { return i8.convertShape(VectorOperators.I2L, LongVector.SPECIES_128, 0); });
        expectSuccess(() -> { return i8.convertShape(VectorOperators.I2L, LongVector.SPECIES_128, 1); });
        expectSuccess(() -> { return i8.convertShape(VectorOperators.I2L, LongVector.SPECIES_128, 2); });
        expectSuccess(() -> { return i8.convertShape(VectorOperators.I2L, LongVector.SPECIES_128, 3); });
        expectAIOOBE( () -> { return i8.convertShape(VectorOperators.I2L, LongVector.SPECIES_128, 4); }, "bad part number 4" + msg);

        msg = " should be in [0..1], output selection with MS=2; logical: conversion lanewise expanding by ML=2; physical: shape-invariant (MP=1); Species[int, 8, S_256_BIT] -> Species[long, 4, S_256_BIT].";
        expectAIOOBE( () -> { return i8.convertShape(VectorOperators.I2L, LongVector.SPECIES_256, -1); }, "bad part number -1" + msg);
        expectSuccess(() -> { return i8.convertShape(VectorOperators.I2L, LongVector.SPECIES_256, 0); });
        expectSuccess(() -> { return i8.convertShape(VectorOperators.I2L, LongVector.SPECIES_256, 1); });
        expectAIOOBE( () -> { return i8.convertShape(VectorOperators.I2L, LongVector.SPECIES_256, 2); }, "bad part number 2" + msg);

        expectAIOOBE( () -> { return i8.convert(VectorOperators.I2L, -1); }, "bad part number -1" + msg);
        expectSuccess(() -> { return i8.convert(VectorOperators.I2L, 0); });
        expectSuccess(() -> { return i8.convert(VectorOperators.I2L, 1); });
        expectAIOOBE( () -> { return i8.convert(VectorOperators.I2L, 2); }, "bad part number 2" + msg);

        msg = " should be 0, output in-place (MO=MS=1); logical: conversion lanewise expanding by ML=2; physical: expansion by MP=2; Species[int, 8, S_256_BIT] -> Species[long, 8, S_512_BIT].";
        expectAIOOBE( () -> { return i8.convertShape(VectorOperators.I2L, LongVector.SPECIES_512, -1); }, "bad part number -1" + msg);
        expectSuccess(() -> { return i8.convertShape(VectorOperators.I2L, LongVector.SPECIES_512, 0); });
        expectAIOOBE( () -> { return i8.convertShape(VectorOperators.I2L, LongVector.SPECIES_512, 1); }, "bad part number 1" + msg);

        msg = " should be in [0..3], output selection with MS=4; logical: reinterpreting (ML=1); physical: contraction by MP=1/4; Species[int, 8, S_256_BIT] -> Species[long, 1, S_64_BIT].";
        expectAIOOBE( () -> { return i8.reinterpretShape(LongVector.SPECIES_64, -1); }, "bad part number -1" + msg);
        expectSuccess(() -> { return i8.reinterpretShape(LongVector.SPECIES_64, 0); });
        expectSuccess(() -> { return i8.reinterpretShape(LongVector.SPECIES_64, 1); });
        expectSuccess(() -> { return i8.reinterpretShape(LongVector.SPECIES_64, 2); });
        expectSuccess(() -> { return i8.reinterpretShape(LongVector.SPECIES_64, 3); });
        expectAIOOBE( () -> { return i8.reinterpretShape(LongVector.SPECIES_64, 4); }, "bad part number 4" + msg);

        msg = " should be in [0..1], output selection with MS=2; logical: reinterpreting (ML=1); physical: contraction by MP=1/2; Species[int, 8, S_256_BIT] -> Species[long, 2, S_128_BIT].";
        expectAIOOBE( () -> { return i8.reinterpretShape(LongVector.SPECIES_128, -1); }, "bad part number -1" + msg);
        expectSuccess(() -> { return i8.reinterpretShape(LongVector.SPECIES_128, 0); });
        expectSuccess(() -> { return i8.reinterpretShape(LongVector.SPECIES_128, 1); });
        expectAIOOBE( () -> { return i8.reinterpretShape(LongVector.SPECIES_128, 2); }, "bad part number 2" + msg);

        msg = " should be 0, output in-place (MO=MS=1); logical: reinterpreting (ML=1); physical: shape-invariant (MP=1); Species[int, 8, S_256_BIT] -> Species[long, 4, S_256_BIT].";
        expectAIOOBE( () -> { return i8.reinterpretShape(LongVector.SPECIES_256, -1); }, "bad part number -1" + msg);
        expectSuccess(() -> { return i8.reinterpretShape(LongVector.SPECIES_256, 0); });
        expectAIOOBE( () -> { return i8.reinterpretShape(LongVector.SPECIES_256, 1); }, "bad part number 1" + msg);

        msg = " should be in [-1..0], output insertion with MO=2; logical: reinterpreting (ML=1); physical: expansion by MP=2; Species[int, 8, S_256_BIT] -> Species[long, 8, S_512_BIT].";
        expectAIOOBE( () -> { return i8.reinterpretShape(LongVector.SPECIES_512, -2); }, "bad part number -2" + msg);
        expectSuccess(() -> { return i8.reinterpretShape(LongVector.SPECIES_512, -1); });
        expectSuccess(() -> { return i8.reinterpretShape(LongVector.SPECIES_512, 0); });
        expectAIOOBE( () -> { return i8.reinterpretShape(LongVector.SPECIES_512, 1); }, "bad part number 1" + msg);

        expectAIOOBE( () -> { return i8.unslice(1, i8, -1); }, "bad part number -1 for slice operation");
        expectSuccess(() -> { return i8.unslice(1, i8, 0); });
        expectSuccess(() -> { return i8.unslice(1, i8, 1); });
        expectAIOOBE( () -> { return i8.unslice(1, i8, 2); }, "bad part number 2 for slice operation");

        var l4 = LongVector.broadcast(LongVector.SPECIES_256, 42);
        msg = " should be in [0..1], output selection with MS=2; logical: conversion lanewise contracting by ML=1/2; physical: contraction by MP=1/4; Species[long, 4, S_256_BIT] -> Species[int, 2, S_64_BIT].";
        expectAIOOBE( () -> { return l4.convertShape(VectorOperators.L2I, IntVector.SPECIES_64, -1); }, "bad part number -1" + msg);
        expectSuccess(() -> { return l4.convertShape(VectorOperators.L2I, IntVector.SPECIES_64, 0); });
        expectSuccess(() -> { return l4.convertShape(VectorOperators.L2I, IntVector.SPECIES_64, 1); });
        expectAIOOBE( () -> { return l4.convertShape(VectorOperators.L2I, IntVector.SPECIES_64, 2); }, "bad part number 2" + msg);

        msg = " should be 0, output in-place (MO=MS=1); logical: conversion lanewise contracting by ML=1/2; physical: contraction by MP=1/2; Species[long, 4, S_256_BIT] -> Species[int, 4, S_128_BIT].";
        expectAIOOBE( () -> { return l4.convertShape(VectorOperators.L2I, IntVector.SPECIES_128, -1); }, "bad part number -1" + msg);
        expectSuccess(() -> { return l4.convertShape(VectorOperators.L2I, IntVector.SPECIES_128, 0); });
        expectAIOOBE( () -> { return l4.convertShape(VectorOperators.L2I, IntVector.SPECIES_128, 1); }, "bad part number 1" + msg);

        msg = " should be in [-1..0], output insertion with MO=2; logical: conversion lanewise contracting by ML=1/2; physical: shape-invariant (MP=1); Species[long, 4, S_256_BIT] -> Species[int, 8, S_256_BIT].";
        expectAIOOBE( () -> { return l4.convertShape(VectorOperators.L2I, IntVector.SPECIES_256, -2); }, "bad part number -2" + msg);
        expectSuccess(() -> { return l4.convertShape(VectorOperators.L2I, IntVector.SPECIES_256, -1); });
        expectSuccess(() -> { return l4.convertShape(VectorOperators.L2I, IntVector.SPECIES_256, 0); });
        expectAIOOBE( () -> { return l4.convertShape(VectorOperators.L2I, IntVector.SPECIES_256, 1); }, "bad part number 1" + msg);

        expectAIOOBE( () -> { return l4.convert(VectorOperators.L2I, -2); }, "bad part number -2" + msg);
        expectSuccess(() -> { return l4.convert(VectorOperators.L2I, -1); });
        expectSuccess(() -> { return l4.convert(VectorOperators.L2I, 0); });
        expectAIOOBE( () -> { return l4.convert(VectorOperators.L2I, 1); }, "bad part number 1" + msg);

        msg = " should be in [-3..0], output insertion with MO=4; logical: conversion lanewise contracting by ML=1/2; physical: expansion by MP=2; Species[long, 4, S_256_BIT] -> Species[int, 16, S_512_BIT].";
        expectAIOOBE( () -> { return l4.convertShape(VectorOperators.L2I, IntVector.SPECIES_512, -4); }, "bad part number -4" + msg);
        expectSuccess(() -> { return l4.convertShape(VectorOperators.L2I, IntVector.SPECIES_512, -3); });
        expectSuccess(() -> { return l4.convertShape(VectorOperators.L2I, IntVector.SPECIES_512, -2); });
        expectSuccess(() -> { return l4.convertShape(VectorOperators.L2I, IntVector.SPECIES_512, -1); });
        expectSuccess(() -> { return l4.convertShape(VectorOperators.L2I, IntVector.SPECIES_512, 0); });
        expectAIOOBE( () -> { return l4.convertShape(VectorOperators.L2I, IntVector.SPECIES_512, 1); }, "bad part number 1" + msg);

        msg = " should be in [0..3], output selection with MS=4; logical: reinterpreting (ML=1); physical: contraction by MP=1/4; Species[long, 4, S_256_BIT] -> Species[int, 2, S_64_BIT].";
        expectAIOOBE( () -> { return l4.reinterpretShape(IntVector.SPECIES_64, -1); }, "bad part number -1" + msg);
        expectSuccess(() -> { return l4.reinterpretShape(IntVector.SPECIES_64, 0); });
        expectSuccess(() -> { return l4.reinterpretShape(IntVector.SPECIES_64, 1); });
        expectSuccess(() -> { return l4.reinterpretShape(IntVector.SPECIES_64, 2); });
        expectSuccess(() -> { return l4.reinterpretShape(IntVector.SPECIES_64, 3); });
        expectAIOOBE( () -> { return l4.reinterpretShape(IntVector.SPECIES_64, 4); }, "bad part number 4" + msg);

        msg = " should be in [0..1], output selection with MS=2; logical: reinterpreting (ML=1); physical: contraction by MP=1/2; Species[long, 4, S_256_BIT] -> Species[int, 4, S_128_BIT].";
        expectAIOOBE( () -> { return l4.reinterpretShape(IntVector.SPECIES_128, -1); }, "bad part number -1" + msg);
        expectSuccess(() -> { return l4.reinterpretShape(IntVector.SPECIES_128, 0); });
        expectSuccess(() -> { return l4.reinterpretShape(IntVector.SPECIES_128, 1); });
        expectAIOOBE( () -> { return l4.reinterpretShape(IntVector.SPECIES_128, 2); }, "bad part number 2" + msg);

        msg = " should be 0, output in-place (MO=MS=1); logical: reinterpreting (ML=1); physical: shape-invariant (MP=1); Species[long, 4, S_256_BIT] -> Species[int, 8, S_256_BIT].";
        expectAIOOBE( () -> { return l4.reinterpretShape(IntVector.SPECIES_256, -1); }, "bad part number -1" + msg);
        expectSuccess(() -> { return l4.reinterpretShape(IntVector.SPECIES_256, 0); });
        expectAIOOBE( () -> { return l4.reinterpretShape(IntVector.SPECIES_256, 1); }, "bad part number 1" + msg);

        msg = " should be in [-1..0], output insertion with MO=2; logical: reinterpreting (ML=1); physical: expansion by MP=2; Species[long, 4, S_256_BIT] -> Species[int, 16, S_512_BIT].";
        expectAIOOBE( () -> { return l4.reinterpretShape(IntVector.SPECIES_512, -2); }, "bad part number -2" + msg);
        expectSuccess(() -> { return l4.reinterpretShape(IntVector.SPECIES_512, -1); });
        expectSuccess(() -> { return l4.reinterpretShape(IntVector.SPECIES_512, 0); });
        expectAIOOBE( () -> { return l4.reinterpretShape(IntVector.SPECIES_512, 1); }, "bad part number 1" + msg);

        expectAIOOBE( () -> { return l4.unslice(1, l4, -1); }, "bad part number -1 for slice operation");
        expectSuccess(() -> { return l4.unslice(1, l4, 0); });
        expectSuccess(() -> { return l4.unslice(1, l4, 1); });
        expectAIOOBE( () -> { return l4.unslice(1, l4, 2); }, "bad part number 2 for slice operation");
    }

    public static List<VectorSpecies> generateSpecies() {
        List<VectorSpecies> s = new ArrayList<>();
        List<VectorShape> shapes = List.of(VectorShape.S_64_BIT,
                                   VectorShape.S_128_BIT,
                                   VectorShape.S_256_BIT,
                                   VectorShape.S_512_BIT);
        List<Class> etypes = List.of(byte.class,
                                     short.class,
                                     int.class,
                                     long.class,
                                     float.class,
                                     double.class);
        for (var etype : etypes) {
            for (var shape : shapes) {
                s.add(VectorSpecies.of(etype, shape));
            }
        }
        return s;
    }

    public static void runExhaustive() {
        Random rnd = new Random();
        List<VectorSpecies> allSpecies = generateSpecies();

        List<Integer> parts = new ArrayList<>();
        for (int i = -100; i <= 100; i++) {
            parts.add(i);
        }
        for (int i = 0; i <= 10; i++) {
            parts.add(rnd.nextInt());
        }

        for (int part : parts) {
            for (var s1 : allSpecies) {
                for (var s2 : allSpecies) {
                    var convC = VectorOperators.Conversion.ofCast(s1.elementType(), s2.elementType());
                    var convR = VectorOperators.Conversion.ofReinterpret(s1.elementType(), s2.elementType());
                    testConvert(s1, s2, part, () -> { return s1.zero().convertShape(convC, s2, part); });
                    testConvert(s1, s2, part, () -> { return s1.zero().convertShape(convR, s2, part); });
                    if (s1.vectorBitSize() == s2.vectorBitSize()) {
                        // Shape-invariant
                        testConvert(s1, s2, part, () -> { return s1.zero().convert(convC, part); });
                        testConvert(s1, s2, part, () -> { return s1.zero().convert(convR, part); });
                    }
                    testReinterpretShape(s1, s2, part);
                }
                testUnslice(s1, part);
            }
        }
    }

    public static void testConvert(VectorSpecies s1, VectorSpecies s2, int part, TestMethod op) {
        int size1 = s1.vectorBitSize();
        int size2 = s2.vectorBitSize();
        int sizeLogical = size1 * s2.elementSize() / s1.elementSize();

        String logicalOp = null;
        if (size1 == sizeLogical) {
            logicalOp = "conversion lanewise in-place (ML=1)";
        } else if (size1 > sizeLogical) {
            logicalOp = "conversion lanewise contracting by ML=1/" + (size1 / sizeLogical);
        } else {
            logicalOp = "conversion lanewise expanding by ML=" + (sizeLogical / size1);
        }

        String physicalOp = null;
        if (size1 == size2) {
            physicalOp = "shape-invariant (MP=1)";
        } else if (size1 > size2) {
            physicalOp = "contraction by MP=1/" + (size1 / size2);
        } else {
            physicalOp = "expansion by MP=" + (size2 / size1);
        }

        String outputOp = null;
        String partRange = null;
        int lo = 0, hi = 0;
        if (sizeLogical == size2) {
            outputOp = "output in-place (MO=MS=1)";
            partRange = "0";
            lo = 0; hi = 0;
        } else if (sizeLogical > size2) {
            int MS = sizeLogical / size2;
            outputOp = "output selection with MS=" + MS;
            partRange = "in [0.." + (MS-1) + "]";
            lo = 0; hi = MS-1;
        } else {
            int MO = size2 / sizeLogical;
            outputOp = "output insertion with MO=" + MO;
            partRange = "in [" + (-MO+1) + "..0]";
            lo = -MO+1; hi = 0;
        }

        if (lo <= part && part <= hi) {
            expectSuccess(op);
        } else {
            String msg = String.format("bad part number %d should be %s, %s; logical: %s; physical: %s; %s -> %s.",
                                       part, partRange, outputOp, logicalOp, physicalOp, s1, s2);
            expectAIOOBE(op, msg);
        }
    }

    public static void testReinterpretShape(VectorSpecies s1, VectorSpecies s2, int part) {
        TestMethod op = () -> { return s1.zero().reinterpretShape(s2, part); };
        int size1 = s1.vectorBitSize();
        int size2 = s2.vectorBitSize();

        String logicalOp = "reinterpreting (ML=1)";
        String physicalOp = null;
        String outputOp = null;
        String partRange = null;
        int lo = 0, hi = 0;
        if (size1 == size2) {
            physicalOp = "shape-invariant (MP=1)";
            outputOp = "output in-place (MO=MS=1)";
            partRange = "0";
            lo = 0; hi = 0;
        } else if (size1 > size2) {
            int MS = size1 / size2;
            physicalOp = "contraction by MP=1/" + MS;
            outputOp = "output selection with MS=" + MS;
            partRange = "in [0.." + (MS-1) + "]";
            lo = 0; hi = MS-1;
        } else {
            int MO = size2 / size1;
            physicalOp = "expansion by MP=" + MO;
            outputOp = "output insertion with MO=" + MO;
            partRange = "in [" + (-MO+1) + "..0]";
            lo = -MO+1; hi = 0;
        }

        if (lo <= part && part <= hi) {
            expectSuccess(op);
        } else {
            String msg = String.format("bad part number %d should be %s, %s; logical: %s; physical: %s; %s -> %s.",
                                       part, partRange, outputOp, logicalOp, physicalOp, s1, s2);
            expectAIOOBE(op, msg);
        }
    }

    public static void testUnslice(VectorSpecies s1, int part) {
        TestMethod op = () -> {
            var v = s1.zero();
            return v.unslice(1, v, part);
        };

        if (0 <= part && part <= 1) {
            expectSuccess(op);
        } else {
            String msg = String.format("bad part number %d for slice operation", part);
            expectAIOOBE(op, msg);
        }
    }
}
