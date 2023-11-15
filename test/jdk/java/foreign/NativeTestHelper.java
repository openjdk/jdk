/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.UnionLayout;
import java.lang.foreign.ValueLayout;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.random.RandomGenerator;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.MemoryLayout.PathElement.sequenceElement;

public class NativeTestHelper {

    public static final boolean IS_WINDOWS = System.getProperty("os.name").startsWith("Windows");

    private static final MethodHandle MH_SAVER;
    private static final RandomGenerator DEFAULT_RANDOM;

    static {
        int seed = Integer.getInteger("NativeTestHelper.DEFAULT_RANDOM.seed", ThreadLocalRandom.current().nextInt());
        System.out.println("NativeTestHelper::DEFAULT_RANDOM.seed = " + seed);
        System.out.println("Re-run with '-DNativeTestHelper.DEFAULT_RANDOM.seed=" + seed + "' to reproduce");
        DEFAULT_RANDOM = new Random(seed);

        try {
            MH_SAVER = MethodHandles.lookup().findStatic(NativeTestHelper.class, "saver",
                    MethodType.methodType(Object.class, Object[].class, List.class, AtomicReference.class, SegmentAllocator.class, int.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static boolean isIntegral(MemoryLayout layout) {
        return layout instanceof ValueLayout valueLayout && isIntegral(valueLayout.carrier());
    }

    static boolean isIntegral(Class<?> clazz) {
        return clazz == byte.class || clazz == char.class || clazz == short.class
                || clazz == int.class || clazz == long.class;
    }

    public static boolean isPointer(MemoryLayout layout) {
        return layout instanceof ValueLayout valueLayout && valueLayout.carrier() == MemorySegment.class;
    }

    public static final Linker LINKER = Linker.nativeLinker();

    // the constants below are useful aliases for C types. The type/carrier association is only valid for 64-bit platforms.

    /**
     * The layout for the {@code bool} C type
     */
    public static final ValueLayout.OfBoolean C_BOOL = (ValueLayout.OfBoolean) LINKER.canonicalLayouts().get("bool");
    /**
     * The layout for the {@code char} C type
     */
    public static final ValueLayout.OfByte C_CHAR = (ValueLayout.OfByte) LINKER.canonicalLayouts().get("char");
    /**
     * The layout for the {@code short} C type
     */
    public static final ValueLayout.OfShort C_SHORT = (ValueLayout.OfShort) LINKER.canonicalLayouts().get("short");
    /**
     * The layout for the {@code int} C type
     */
    public static final ValueLayout.OfInt C_INT = (ValueLayout.OfInt) LINKER.canonicalLayouts().get("int");

    /**
     * The layout for the {@code long long} C type.
     */
    public static final ValueLayout.OfLong C_LONG_LONG = (ValueLayout.OfLong) LINKER.canonicalLayouts().get("long long");
    /**
     * The layout for the {@code float} C type
     */
    public static final ValueLayout.OfFloat C_FLOAT = (ValueLayout.OfFloat) LINKER.canonicalLayouts().get("float");
    /**
     * The layout for the {@code double} C type
     */
    public static final ValueLayout.OfDouble C_DOUBLE = (ValueLayout.OfDouble) LINKER.canonicalLayouts().get("double");
    /**
     * The {@code T*} native type.
     */
    public static final AddressLayout C_POINTER = ((AddressLayout) LINKER.canonicalLayouts().get("void*"))
            .withTargetLayout(MemoryLayout.sequenceLayout(Long.MAX_VALUE, C_CHAR));
    /**
     * The layout for the {@code size_t} C type
     */
    public static final ValueLayout C_SIZE_T = (ValueLayout) LINKER.canonicalLayouts().get("size_t");

    // Common layout shared by some tests
    // struct S_PDI { void* p0; double p1; int p2; };
    public static final MemoryLayout S_PDI_LAYOUT = switch ((int) ValueLayout.ADDRESS.byteSize()) {
        case 8 -> MemoryLayout.structLayout(
            C_POINTER.withName("p0"),
            C_DOUBLE.withName("p1"),
            C_INT.withName("p2"),
            MemoryLayout.paddingLayout(4));
        case 4 -> MemoryLayout.structLayout(
            C_POINTER.withName("p0"),
            C_DOUBLE.withName("p1"),
            C_INT.withName("p2"));
        default -> throw new UnsupportedOperationException("Unsupported address size");
    };

    private static final MethodHandle FREE = LINKER.downcallHandle(
            LINKER.defaultLookup().find("free").get(), FunctionDescriptor.ofVoid(C_POINTER));

    private static final MethodHandle MALLOC = LINKER.downcallHandle(
            LINKER.defaultLookup().find("malloc").get(), FunctionDescriptor.of(C_POINTER, C_LONG_LONG));

    public static void freeMemory(MemorySegment address) {
        try {
            FREE.invokeExact(address);
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static MemorySegment allocateMemory(long size) {
        try {
            return (MemorySegment) MALLOC.invokeExact(size);
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static MemorySegment findNativeOrThrow(String name) {
        return SymbolLookup.loaderLookup().find(name).orElseThrow();
    }

    public static MethodHandle downcallHandle(String symbol, FunctionDescriptor desc, Linker.Option... options) {
        return LINKER.downcallHandle(findNativeOrThrow(symbol), desc, options);
    }

    public static MemorySegment upcallStub(Class<?> holder, String name, FunctionDescriptor descriptor) {
        try {
            MethodHandle target = MethodHandles.lookup().findStatic(holder, name, descriptor.toMethodType());
            return LINKER.upcallStub(target, descriptor, Arena.ofAuto());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static TestValue[] genTestArgs(FunctionDescriptor descriptor, SegmentAllocator allocator) {
        return genTestArgs(DEFAULT_RANDOM, descriptor, allocator);
    }

    public static TestValue[] genTestArgs(RandomGenerator random, FunctionDescriptor descriptor, SegmentAllocator allocator) {
        TestValue[] result = new TestValue[descriptor.argumentLayouts().size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = genTestValue(random, descriptor.argumentLayouts().get(i), allocator);
        }
        return result;
    }

    public record TestValue (Object value, Consumer<Object> check) {
        public void check(Object actual) { check.accept(actual); }
    }

    public static TestValue genTestValue(MemoryLayout layout, SegmentAllocator allocator) {
        return genTestValue(DEFAULT_RANDOM, layout, allocator);
    }

    public static TestValue genTestValue(RandomGenerator random, MemoryLayout layout, SegmentAllocator allocator) {
        if (layout instanceof StructLayout struct) {
            MemorySegment segment = allocator.allocate(struct);
            List<Consumer<Object>> fieldChecks = new ArrayList<>();
            for (MemoryLayout fieldLayout : struct.memberLayouts()) {
                if (fieldLayout instanceof PaddingLayout) continue;
                MemoryLayout.PathElement fieldPath = groupElement(fieldLayout.name().orElseThrow());
                fieldChecks.add(initField(random, segment, struct, fieldLayout, fieldPath, allocator));
            }
            return new TestValue(segment, actual -> fieldChecks.forEach(check -> check.accept(actual)));
        } else if (layout instanceof UnionLayout union) {
            MemorySegment segment = allocator.allocate(union);
            List<MemoryLayout> filteredFields = union.memberLayouts().stream()
                                                                     .filter(l -> !(l instanceof PaddingLayout))
                                                                     .toList();
            int fieldIdx = random.nextInt(filteredFields.size());
            MemoryLayout fieldLayout = filteredFields.get(fieldIdx);
            MemoryLayout.PathElement fieldPath = groupElement(fieldLayout.name().orElseThrow());
            Consumer<Object> check = initField(random, segment, union, fieldLayout, fieldPath, allocator);
            return new TestValue(segment, check);
        } else if (layout instanceof SequenceLayout array) {
            MemorySegment segment = allocator.allocate(array);
            List<Consumer<Object>> elementChecks = new ArrayList<>();
            for (int i = 0; i < array.elementCount(); i++) {
                elementChecks.add(initField(random, segment, array, array.elementLayout(), sequenceElement(i), allocator));
            }
            return new TestValue(segment, actual -> elementChecks.forEach(check -> check.accept(actual)));
        } else if (layout instanceof AddressLayout) {
            MemorySegment value = MemorySegment.ofAddress(random.nextLong());
            return new TestValue(value, actual -> assertEquals(actual, value));
        }else if (layout instanceof ValueLayout.OfByte) {
            byte value = (byte) random.nextInt();
            return new TestValue(value, actual -> assertEquals(actual, value));
        } else if (layout instanceof ValueLayout.OfShort) {
            short value = (short) random.nextInt();
            return new TestValue(value, actual -> assertEquals(actual, value));
        } else if (layout instanceof ValueLayout.OfChar) {
            char value = (char) random.nextInt();
            return new TestValue(value, actual -> assertEquals(actual, value));
        } else if (layout instanceof ValueLayout.OfInt) {
            int value = random.nextInt();
            return new TestValue(value, actual -> assertEquals(actual, value));
        } else if (layout instanceof ValueLayout.OfLong) {
            long value = random.nextLong();
            return new TestValue(value, actual -> assertEquals(actual, value));
        } else if (layout instanceof ValueLayout.OfFloat) {
            float value = random.nextFloat();
            return new TestValue(value, actual -> assertEquals(actual, value));
        } else if (layout instanceof ValueLayout.OfDouble) {
            double value = random.nextDouble();
            return new TestValue(value, actual -> assertEquals(actual, value));
        }

        throw new IllegalStateException("Unexpected layout: " + layout);
    }

    private static Consumer<Object> initField(RandomGenerator random, MemorySegment container, MemoryLayout containerLayout,
                                              MemoryLayout fieldLayout, MemoryLayout.PathElement fieldPath,
                                              SegmentAllocator allocator) {
        TestValue fieldValue = genTestValue(random, fieldLayout, allocator);
        Consumer<Object> fieldCheck = fieldValue.check();
        if (fieldLayout instanceof GroupLayout || fieldLayout instanceof SequenceLayout) {
            UnaryOperator<MemorySegment> slicer = slicer(containerLayout, fieldPath);
            MemorySegment slice = slicer.apply(container);
            slice.copyFrom((MemorySegment) fieldValue.value());
            return actual -> fieldCheck.accept(slicer.apply((MemorySegment) actual));
        } else {
            VarHandle accessor = containerLayout.varHandle(fieldPath);
            //set value
            accessor.set(container, 0L, fieldValue.value());
            return actual -> fieldCheck.accept(accessor.get((MemorySegment) actual, 0L));
        }
    }

    private static UnaryOperator<MemorySegment> slicer(MemoryLayout containerLayout, MemoryLayout.PathElement fieldPath) {
        MethodHandle slicer = containerLayout.sliceHandle(fieldPath);
        return container -> {
              try {
                return (MemorySegment) slicer.invokeExact(container, 0L);
            } catch (Throwable e) {
                throw new IllegalStateException(e);
            }
        };
    }

    private static void assertEquals(Object actual, Object expected) {
        if (actual.getClass() != expected.getClass()) {
            throw new AssertionError("Type mismatch: " + actual.getClass() + " != " + expected.getClass());
        }
        if (!actual.equals(expected)) {
            throw new AssertionError("Not equal: " + actual + " != " + expected);
        }
    }

    /**
     * Make an upcall stub that saves its arguments into the given 'ref' array
     *
     * @param fd function descriptor for the upcall stub
     * @param capturedArgs box to save arguments in
     * @param arena allocator for making copies of by-value structs
     * @param retIdx the index of the argument to return
     * @return return the upcall stub
     */
    public static MemorySegment makeArgSaverCB(FunctionDescriptor fd, Arena arena,
                                               AtomicReference<Object[]> capturedArgs, int retIdx) {
        MethodHandle target = MethodHandles.insertArguments(MH_SAVER, 1, fd.argumentLayouts(), capturedArgs, arena, retIdx);
        target = target.asCollector(Object[].class, fd.argumentLayouts().size());
        target = target.asType(fd.toMethodType());
        return LINKER.upcallStub(target, fd, arena);
    }

    private static Object saver(Object[] o, List<MemoryLayout> argLayouts, AtomicReference<Object[]> ref, SegmentAllocator allocator, int retArg) {
        for (int i = 0; i < o.length; i++) {
            if (argLayouts.get(i) instanceof GroupLayout gl) {
                MemorySegment ms = (MemorySegment) o[i];
                MemorySegment copy = allocator.allocate(gl);
                copy.copyFrom(ms);
                o[i] = copy;
            }
        }
        ref.set(o);
        return retArg != -1 ? o[retArg] : null;
    }
}
