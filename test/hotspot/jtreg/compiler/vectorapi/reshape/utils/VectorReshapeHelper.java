/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

package compiler.vectorapi.reshape.utils;

import compiler.lib.ir_framework.ForceInline;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.TestFramework;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Array;
import java.nio.ByteOrder;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.lang.foreign.MemorySegment;
import jdk.incubator.vector.*;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

public class VectorReshapeHelper {
    public static final int INVOCATIONS = 10_000;

    public static final VectorSpecies<Byte>    BSPEC64  =   ByteVector.SPECIES_64;
    public static final VectorSpecies<Short>   SSPEC64  =  ShortVector.SPECIES_64;
    public static final VectorSpecies<Integer> ISPEC64  =    IntVector.SPECIES_64;
    public static final VectorSpecies<Long>    LSPEC64  =   LongVector.SPECIES_64;
    public static final VectorSpecies<Float>   FSPEC64  =  FloatVector.SPECIES_64;
    public static final VectorSpecies<Double>  DSPEC64  = DoubleVector.SPECIES_64;

    public static final VectorSpecies<Byte>    BSPEC128 =   ByteVector.SPECIES_128;
    public static final VectorSpecies<Short>   SSPEC128 =  ShortVector.SPECIES_128;
    public static final VectorSpecies<Integer> ISPEC128 =    IntVector.SPECIES_128;
    public static final VectorSpecies<Long>    LSPEC128 =   LongVector.SPECIES_128;
    public static final VectorSpecies<Float>   FSPEC128 =  FloatVector.SPECIES_128;
    public static final VectorSpecies<Double>  DSPEC128 = DoubleVector.SPECIES_128;

    public static final VectorSpecies<Byte>    BSPEC256 =   ByteVector.SPECIES_256;
    public static final VectorSpecies<Short>   SSPEC256 =  ShortVector.SPECIES_256;
    public static final VectorSpecies<Integer> ISPEC256 =    IntVector.SPECIES_256;
    public static final VectorSpecies<Long>    LSPEC256 =   LongVector.SPECIES_256;
    public static final VectorSpecies<Float>   FSPEC256 =  FloatVector.SPECIES_256;
    public static final VectorSpecies<Double>  DSPEC256 = DoubleVector.SPECIES_256;

    public static final VectorSpecies<Byte>    BSPEC512 =   ByteVector.SPECIES_512;
    public static final VectorSpecies<Short>   SSPEC512 =  ShortVector.SPECIES_512;
    public static final VectorSpecies<Integer> ISPEC512 =    IntVector.SPECIES_512;
    public static final VectorSpecies<Long>    LSPEC512 =   LongVector.SPECIES_512;
    public static final VectorSpecies<Float>   FSPEC512 =  FloatVector.SPECIES_512;
    public static final VectorSpecies<Double>  DSPEC512 = DoubleVector.SPECIES_512;

    public static final String REINTERPRET_NODE = IRNode.VECTOR_REINTERPRET;

    public static void runMainHelper(Class<?> testClass, Stream<VectorSpeciesPair> testMethods, String... flags) {
        var test = new TestFramework(testClass);
        test.setDefaultWarmup(1);
        test.addHelperClasses(VectorReshapeHelper.class);
        test.addFlags("--add-modules=jdk.incubator.vector", "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED", "--enable-preview");
        test.addFlags(flags);
        String testMethodNames = testMethods
                .filter(p -> p.isp().length() <= VectorSpecies.ofLargestShape(p.isp().elementType()).length())
                .filter(p -> p.osp().length() <= VectorSpecies.ofLargestShape(p.osp().elementType()).length())
                .map(VectorSpeciesPair::format)
                .collect(Collectors.joining(","));
        test.addFlags("-DTest=" + testMethodNames);
        test.start();
    }

    @ForceInline
    public static <T, U> void vectorCast(VectorOperators.Conversion<T, U> cop,
                                         VectorSpecies<T> isp, VectorSpecies<U> osp, Object input, Object output) {
        var outputVector = readVector(isp, input)
                .convertShape(cop, osp, 0);
        writeVector(osp, outputVector, output);
    }

    public static <T, U> void runCastHelper(VectorOperators.Conversion<T, U> castOp,
                                            VectorSpecies<T> isp, VectorSpecies<U> osp) throws Throwable {
        var random = Utils.getRandomInstance();
        boolean isUnsignedCast = castOp.name().startsWith("ZERO");
        String testMethodName = VectorSpeciesPair.makePair(isp, osp, isUnsignedCast).format();
        var caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        var testMethod = MethodHandles.lookup().findStatic(caller,
                    testMethodName,
                    MethodType.methodType(void.class, isp.elementType().arrayType(), osp.elementType().arrayType()))
                .asType(MethodType.methodType(void.class, Object.class, Object.class));
        Object input = Array.newInstance(isp.elementType(), isp.length());
        Object output = Array.newInstance(osp.elementType(), osp.length());
        long ibase = UnsafeUtils.arrayBase(isp.elementType());
        long obase = UnsafeUtils.arrayBase(osp.elementType());
        for (int iter = 0; iter < INVOCATIONS; iter++) {
            // We need to generate arrays with NaN or very large values occasionally
            boolean normalArray = random.nextBoolean();
            var abnormalValue = List.of(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -1e30, 1e30);
            for (int i = 0; i < isp.length(); i++) {
                switch (isp.elementType().getName()) {
                    case "byte"   -> UnsafeUtils.putByte(input, ibase, i, (byte)random.nextInt());
                    case "short"  -> UnsafeUtils.putShort(input, ibase, i, (short)random.nextInt());
                    case "int"    -> UnsafeUtils.putInt(input, ibase, i, random.nextInt());
                    case "long"   -> UnsafeUtils.putLong(input, ibase, i, random.nextLong());
                    case "float"  -> {
                        if (normalArray || random.nextBoolean()) {
                            UnsafeUtils.putFloat(input, ibase, i, random.nextFloat(Byte.MIN_VALUE, Byte.MAX_VALUE));
                        } else {
                            UnsafeUtils.putFloat(input, ibase, i, abnormalValue.get(random.nextInt(abnormalValue.size())).floatValue());
                        }
                    }
                    case "double" -> {
                        if (normalArray || random.nextBoolean()) {
                            UnsafeUtils.putDouble(input, ibase, i, random.nextDouble(Byte.MIN_VALUE, Byte.MAX_VALUE));
                        } else {
                            UnsafeUtils.putDouble(input, ibase, i, abnormalValue.get(random.nextInt(abnormalValue.size())));
                        }
                    }
                    default -> throw new AssertionError();
                }
            }

            testMethod.invokeExact(input, output);

            for (int i = 0; i < osp.length(); i++) {
                Number expected, actual;
                if (i < isp.length()) {
                    Number initial = switch (isp.elementType().getName()) {
                        case "byte"   -> UnsafeUtils.getByte(input, ibase, i);
                        case "short"  -> UnsafeUtils.getShort(input, ibase, i);
                        case "int"    -> UnsafeUtils.getInt(input, ibase, i);
                        case "long"   -> UnsafeUtils.getLong(input, ibase, i);
                        case "float"  -> UnsafeUtils.getFloat(input, ibase, i);
                        case "double" -> UnsafeUtils.getDouble(input, ibase, i);
                        default -> throw new AssertionError();
                    };
                    expected = switch (osp.elementType().getName()) {
                        case "byte" -> initial.byteValue();
                        case "short" -> {
                            if (isUnsignedCast) {
                                yield (short) (initial.longValue() & ((1L << isp.elementSize()) - 1));
                            } else {
                                yield initial.shortValue();
                            }
                        }
                        case "int" -> {
                            if (isUnsignedCast) {
                                yield (int) (initial.longValue() & ((1L << isp.elementSize()) - 1));
                            } else {
                                yield initial.intValue();
                            }
                        }
                        case "long" -> {
                            if (isUnsignedCast) {
                                yield (long) (initial.longValue() & ((1L << isp.elementSize()) - 1));
                            } else {
                                yield initial.longValue();
                            }
                        }
                        case "float" -> initial.floatValue();
                        case "double" -> initial.doubleValue();
                        default -> throw new AssertionError();
                    };
                } else {
                    expected = switch (osp.elementType().getName()) {
                        case "byte"   -> (byte)0;
                        case "short"  -> (short)0;
                        case "int"    -> (int)0;
                        case "long"   -> (long)0;
                        case "float"  -> (float)0;
                        case "double" -> (double)0;
                        default -> throw new AssertionError();
                    };
                }
                actual = switch (osp.elementType().getName()) {
                    case "byte"   -> UnsafeUtils.getByte(output, obase, i);
                    case "short"  -> UnsafeUtils.getShort(output, obase, i);
                    case "int"    -> UnsafeUtils.getInt(output, obase, i);
                    case "long"   -> UnsafeUtils.getLong(output, obase, i);
                    case "float"  -> UnsafeUtils.getFloat(output, obase, i);
                    case "double" -> UnsafeUtils.getDouble(output, obase, i);
                    default -> throw new AssertionError();
                };
                Asserts.assertEquals(expected, actual);
            }
        }
    }

    @ForceInline
    public static void vectorExpandShrink(VectorSpecies<Byte> isp, VectorSpecies<Byte> osp,
                                          MemorySegment input, MemorySegment output) {
        isp.fromMemorySegment(input, 0, ByteOrder.nativeOrder())
                .reinterpretShape(osp, 0)
                .intoMemorySegment(output, 0, ByteOrder.nativeOrder());
    }

    public static void runExpandShrinkHelper(VectorSpecies<Byte> isp, VectorSpecies<Byte> osp) throws Throwable {
        var random = Utils.getRandomInstance();
        String testMethodName = VectorSpeciesPair.makePair(isp, osp).format();
        var caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        var testMethod = MethodHandles.lookup().findStatic(caller,
                testMethodName,
                MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class));
        byte[] input = new byte[isp.vectorByteSize()];
        byte[] output = new byte[osp.vectorByteSize()];
        MemorySegment msInput = MemorySegment.ofArray(input);
        MemorySegment msOutput = MemorySegment.ofArray(output);
        for (int iter = 0; iter < INVOCATIONS; iter++) {
            random.nextBytes(input);

            testMethod.invokeExact(msInput, msOutput);

            for (int i = 0; i < osp.vectorByteSize(); i++) {
                int expected = i < isp.vectorByteSize() ? input[i] : 0;
                int actual = output[i];
                Asserts.assertEquals(expected, actual);
            }
        }
    }

    @ForceInline
    public static void vectorDoubleExpandShrink(VectorSpecies<Byte> isp, VectorSpecies<Byte> osp,
                                                MemorySegment input, MemorySegment output) {
        isp.fromMemorySegment(input, 0, ByteOrder.nativeOrder())
                .reinterpretShape(osp, 0)
                .reinterpretShape(isp, 0)
                .intoMemorySegment(output, 0, ByteOrder.nativeOrder());
    }

    public static void runDoubleExpandShrinkHelper(VectorSpecies<Byte> isp, VectorSpecies<Byte> osp) throws Throwable {
        var random = Utils.getRandomInstance();
        String testMethodName = VectorSpeciesPair.makePair(isp, osp).format();
        var caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        var testMethod = MethodHandles.lookup().findStatic(caller,
                testMethodName,
                MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class));
        byte[] input = new byte[isp.vectorByteSize()];
        byte[] output = new byte[isp.vectorByteSize()];
        MemorySegment msInput = MemorySegment.ofArray(input);
        MemorySegment msOutput = MemorySegment.ofArray(output);
        for (int iter = 0; iter < INVOCATIONS; iter++) {
            random.nextBytes(input);

            testMethod.invokeExact(msInput, msOutput);

            for (int i = 0; i < isp.vectorByteSize(); i++) {
                int expected = i < osp.vectorByteSize() ? input[i] : 0;
                int actual = output[i];
                Asserts.assertEquals(expected, actual);
            }
        }
    }

    @ForceInline
    public static <T, U> void vectorRebracket(VectorSpecies<T> isp, VectorSpecies<U> osp, Object input, Object output) {
        var outputVector = readVector(isp, input)
                .reinterpretShape(osp, 0);
        writeVector(osp, outputVector, output);
    }

    public static <T, U> void runRebracketHelper(VectorSpecies<T> isp, VectorSpecies<U> osp) throws Throwable {
        var random = Utils.getRandomInstance();
        String testMethodName = VectorSpeciesPair.makePair(isp, osp).format();
        var caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        var testMethod = MethodHandles.lookup().findStatic(caller,
                    testMethodName,
                    MethodType.methodType(void.class, isp.elementType().arrayType(), osp.elementType().arrayType()))
                .asType(MethodType.methodType(void.class, Object.class, Object.class));
        Object input = Array.newInstance(isp.elementType(), isp.length());
        Object output = Array.newInstance(osp.elementType(), osp.length());
        long ibase = UnsafeUtils.arrayBase(isp.elementType());
        long obase = UnsafeUtils.arrayBase(osp.elementType());
        for (int iter = 0; iter < INVOCATIONS; iter++) {
            for (int i = 0; i < isp.vectorByteSize(); i++) {
                UnsafeUtils.putByte(input, ibase, i, (byte)random.nextInt());
            }

            testMethod.invokeExact(input, output);

            for (int i = 0; i < osp.vectorByteSize(); i++) {
                int expected = i < isp.vectorByteSize() ? UnsafeUtils.getByte(input, ibase, i) : 0;
                int actual = UnsafeUtils.getByte(output, obase, i);
                Asserts.assertEquals(expected, actual);
            }
        }
    }

    @ForceInline
    private static <T> Vector<T> readVector(VectorSpecies<T> isp, Object input) {
        return isp.fromArray(input, 0);
    }

    @ForceInline
    private static <U> void writeVector(VectorSpecies<U> osp, Vector<U> vector, Object output) {
        var otype = osp.elementType();
        if (otype == byte.class) {
            ((ByteVector)vector).intoArray((byte[])output, 0);
        } else if (otype == short.class) {
            ((ShortVector)vector).intoArray((short[])output, 0);
        } else if (otype == int.class) {
            ((IntVector)vector).intoArray((int[])output, 0);
        } else if (otype == long.class) {
            ((LongVector)vector).intoArray((long[])output, 0);
        } else if (otype == float.class) {
            ((FloatVector)vector).intoArray((float[])output, 0);
        } else if (otype == double.class) {
            ((DoubleVector)vector).intoArray((double[])output, 0);
        } else {
            throw new AssertionError();
        }
    }
}
