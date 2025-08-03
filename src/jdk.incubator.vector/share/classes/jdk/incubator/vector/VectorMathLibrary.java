/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.incubator.vector;

import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;
import jdk.internal.vm.vector.VectorSupport;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.util.function.IntFunction;

import static jdk.incubator.vector.Util.requires;
import static jdk.incubator.vector.VectorOperators.*;
import static jdk.internal.util.Architecture.*;
import static jdk.internal.vm.vector.Utils.debug;

/**
 * A wrapper for native vector math libraries bundled with the JDK (SVML and SLEEF).
 * Binds vector operations to native implementations provided by the libraries.
 */
/*package-private*/ class VectorMathLibrary {
    private static final SymbolLookup LOOKUP = SymbolLookup.loaderLookup();

    interface Library {
        String symbolName(Operator op, VectorSpecies<?> vspecies);
        boolean isSupported(Operator op, VectorSpecies<?> vspecies);

        String SVML  = "svml";
        String SLEEF = "sleef";
        String JAVA  = "java";

        static Library getInstance() {
            String libraryName = System.getProperty("jdk.incubator.vector.VectorMathLibrary", getDefaultName());
            try {
                return switch (libraryName) {
                    case SVML  -> new SVML();
                    case SLEEF -> new SLEEF();
                    case JAVA  -> new Java();
                    default    -> throw new IllegalArgumentException("Unsupported vector math library: " + libraryName);
                };
            } catch (Throwable e) {
                debug("Error during initialization of %s library: %s", libraryName, e);
                return new Java(); // fallback
            }
        }

        static String getDefaultName() {
            return switch (System.getProperty("os.arch")) {
                case "amd64", "x86_64" -> SVML;
                case "aarch64", "riscv64" -> SLEEF;
                default -> JAVA;
            };
        }
    }

    private static final Library LIBRARY = Library.getInstance();

    static {
        debug("%s library is used (cpu features: %s)", LIBRARY.getClass().getSimpleName(), CPUFeatures.features());
    }

    private static class Java implements Library {
        @Override
        public String symbolName(Operator op, VectorSpecies<?> vspecies) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSupported(Operator op, VectorSpecies<?> vspecies) {
            return false; // always use default implementation
        }
    }

    /**
     * Naming convention in SVML vector math library.
     * All the methods are named as __jsvml_<op><T><N>_ha_<VV> where:
     *      ha stands for high accuracy
     *      <T> is optional to indicate float/double
     *              Set to f for vector float operation
     *              Omitted for vector double operation
     *      <N> is the number of elements in the vector
     *              1, 2, 4, 8, 16
     *              e.g. 128 bit float vector has 4 float elements
     *      <VV> indicates the avx/sse level:
     *              z0 is AVX512, l9 is AVX2, e9 is AVX1 and ex is for SSE2
     *      e.g. __jsvml_expf16_ha_z0 is the method for computing 16 element vector float exp using AVX 512 insns
     *           __jsvml_exp8_ha_z0 is the method for computing 8 element vector double exp using AVX 512 insns
     */
    private static class SVML implements Library {
        static {
            loadNativeLibrary();
        }

        private static void loadNativeLibrary() {
            requires(isX64(), "SVML library is x64-specific");
            VectorSupport.loadNativeLibrary("jsvml");
        }

        private static String suffix(VectorSpecies<?> vspecies) {
            assert vspecies.vectorBitSize() <= VectorShape.getMaxVectorBitSize(vspecies.elementType());

            if (vspecies.vectorBitSize() == 512) {
                assert CPUFeatures.X64.SUPPORTS_AVX512F;
                return "z0";
            } else if (CPUFeatures.X64.SUPPORTS_AVX2) {
                return "l9";
            } else if (CPUFeatures.X64.SUPPORTS_AVX) {
                return "e9";
            } else {
                return "ex";
            }
        }

        @Override
        public String symbolName(Operator op, VectorSpecies<?> vspecies) {
            String suffix = suffix(vspecies);
            String elemType = (vspecies.elementType() == float.class ? "f" : "");
            boolean isFloat64Vector = (vspecies.elementType() == float.class) && (vspecies.length() == 2); // Float64Vector or FloatMaxVector
            int vlen = (isFloat64Vector ? 4 : vspecies.length()); // reuse 128-bit variant for 64-bit float vectors
            return String.format("__jsvml_%s%s%d_ha_%s", op.operatorName(), elemType, vlen, suffix);
        }

        @Override
        public boolean isSupported(Operator op, VectorSpecies<?> vspecies) {
            Class<?> etype = vspecies.elementType();
            if (etype != float.class && etype != double.class) {
                return false; // only FP types are supported
            }
            int maxLaneCount = VectorSupport.getMaxLaneCount(vspecies.elementType());
            if (vspecies.length() > maxLaneCount) {
                return false; // lacking vector support (either hardware or disabled on JVM side)
            }
            if (vspecies == DoubleVector.SPECIES_64) {
                return false; // 64-bit double vectors are not supported
            }
            if (vspecies.vectorBitSize() == 512) {
                if (op == LOG || op == LOG10 || op == POW) {
                    return CPUFeatures.X64.SUPPORTS_AVX512DQ; // requires AVX512DQ CPU support
                }
            } else if (op == POW) {
                return false; // not supported
            }
            return true;
        }
    }

    /**
     * Naming convention in SLEEF-based vector math library .
     * All the methods are named as <OP><T><N>_<U><suffix> where:
     *     <OP>     is the operation name, e.g. sin
     *     <T>      is optional to indicate float/double
     *              "f/d" for vector float/double operation
     *     <N>      is the number of elements in the vector
     *              "2/4" for neon, and "x" for sve/rvv
     *     <U>      is the precision level
     *              "u10/u05" represents 1.0/0.5 ULP error bounds
     *               We use "u10" for all operations by default
     *               But for those functions do not have u10 support, we use "u05" instead
     *     <suffix> indicates neon/sve/rvv
     *              "sve/advsimd/rvv" for sve/neon/rvv implementations
     *     e.g. sinfx_u10sve is the method for computing vector float sin using SVE instructions
     *          cosd2_u10advsimd is the method for computing 2 elements vector double cos using NEON instructions
     */
    private static class SLEEF implements Library {
        static {
            VectorSupport.loadNativeLibrary("sleef");
        }

        private static String suffix(VectorShape vshape, boolean isShapeAgnostic) {
            if (isAARCH64()) {
                if (isShapeAgnostic) {
                    return "sve";
                } else {
                    return "advsimd";
                }
            } else if (isRISCV64()) {
                assert isShapeAgnostic : "not supported";
                return "rvv";
            } else {
                throw new InternalError("unsupported platform");
            }
        }

        private static String precisionLevel(Operator op) {
            return (op == HYPOT ? "u05" : "u10");
        }

        @Override
        public String symbolName(Operator op, VectorSpecies<?> vspecies) {
            boolean isFloat64Vector = (vspecies.elementType() == float.class) && (vspecies.length() == 2); // Float64Vector or FloatMaxVector
            int vlen = (isFloat64Vector ? 4 : vspecies.length()); // reuse 128-bit variant for 64-bit float vectors
            boolean isShapeAgnostic = isRISCV64() || (isAARCH64() && vspecies.vectorBitSize() > 128);
            return String.format("%s%s%s_%s%s", op.operatorName(),
                                 (vspecies.elementType() == float.class ? "f" : "d"),
                                 (isShapeAgnostic ? "x" : Integer.toString(vlen)),
                                 precisionLevel(op),
                                 suffix(vspecies.vectorShape(), isShapeAgnostic));
        }

        @Override
        public boolean isSupported(Operator op, VectorSpecies<?> vspecies) {
            Class<?> etype = vspecies.elementType();
            if (etype != float.class && etype != double.class) {
                return false; // only FP element types are supported
            }
            int maxLaneCount = VectorSupport.getMaxLaneCount(vspecies.elementType());
            if (vspecies.length() > maxLaneCount) {
                return false; // lacking vector support (either hardware or disabled on JVM side)
            }
            if (vspecies == DoubleVector.SPECIES_64) {
                return false; // 64-bit double vectors are not supported
            }
            if (op == TANH) {
                return false; // skip due to performance considerations
            }
            return true;
        }
    }

    private static final int SIZE = VectorSupport.VECTOR_OP_MATHLIB_LAST - VectorSupport.VECTOR_OP_MATHLIB_FIRST + 1;

    private record Entry<T> (String name, MemorySegment entry, T impl) {}

    private static final @Stable Entry<?>[][][] LIBRARY_ENTRIES = new Entry<?>[SIZE][LaneType.SK_LIMIT][VectorShape.SK_LIMIT]; // OP x SHAPE x TYPE

    @ForceInline
    private static <T> Entry<T> lookup(Operator op, int opc, VectorSpecies<?> vspecies, IntFunction<T> implSupplier) {
        int idx = opc - VectorSupport.VECTOR_OP_MATHLIB_FIRST;
        int elem_idx = ((AbstractSpecies<?>)vspecies).laneType.switchKey;
        int shape_idx = vspecies.vectorShape().switchKey;
        @SuppressWarnings({"unchecked"})
        Entry<T> entry = (Entry<T>)LIBRARY_ENTRIES[idx][elem_idx][shape_idx];
        if (entry == null) {
            entry = constructEntry(op, opc, vspecies, implSupplier);
            LIBRARY_ENTRIES[idx][elem_idx][shape_idx] = entry;
        }
        return entry;
    }

    @DontInline
    private static
    <E,T>
    Entry<T> constructEntry(Operator op, int opc, VectorSpecies<E> vspecies, IntFunction<T> implSupplier) {
        if (LIBRARY.isSupported(op, vspecies)) {
            String symbol = LIBRARY.symbolName(op, vspecies);
            try {
                MemorySegment addr = LOOKUP.findOrThrow(symbol);
                debug("%s %s => 0x%016x\n", op, symbol, addr.address());
                T impl = implSupplier.apply(opc); // TODO: should call the very same native implementation eventually (once FFM API supports vectors)
                return new Entry<>(symbol, addr, impl);
            } catch (RuntimeException e) {
              throw new InternalError("not supported: " + op + " " + vspecies + " " + symbol, e);
            }
        } else {
            return new Entry<>(null, MemorySegment.NULL, implSupplier.apply(opc));
        }
    }

    @ForceInline
    /*package-private*/ static
    <E, V extends Vector<E>>
    V unaryMathOp(Unary op, int opc, VectorSpecies<E> vspecies,
                  IntFunction<VectorSupport.UnaryOperation<V,?>> implSupplier,
                  V v) {
        var entry = lookup(op, opc, vspecies, implSupplier);

        long entryAddress = entry.entry.address();
        if (entryAddress != 0) {
            @SuppressWarnings({"unchecked"})
            Class<V> vt = (Class<V>)vspecies.vectorType();
            return VectorSupport.libraryUnaryOp(
                    entry.entry.address(), vt, vspecies.elementType(), vspecies.length(), entry.name,
                    v,
                    entry.impl);
        } else {
            return entry.impl.apply(v, null);
        }
    }

    @ForceInline
    /*package-private*/ static
    <E, V extends Vector<E>>
    V binaryMathOp(Binary op, int opc, VectorSpecies<E> vspecies,
                   IntFunction<VectorSupport.BinaryOperation<V,?>> implSupplier,
                   V v1, V v2) {
        var entry = lookup(op, opc, vspecies, implSupplier);

        long entryAddress = entry.entry.address();
        if (entryAddress != 0) {
            @SuppressWarnings({"unchecked"})
            Class<V> vt = (Class<V>)vspecies.vectorType();
            return VectorSupport.libraryBinaryOp(
                    entry.entry.address(), vt, vspecies.elementType(), vspecies.length(), entry.name,
                    v1, v2,
                    entry.impl);
        } else {
            return entry.impl.apply(v1, v2, null);
        }
    }
}
