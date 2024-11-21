package jdk.incubator.vector;

import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;
import jdk.internal.vm.vector.VectorSupport;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.util.function.IntFunction;

import static jdk.incubator.vector.VectorOperators.*;

/*package-private*/ class VectorMathLibrary {
    private static final boolean DEBUG = Boolean.getBoolean("jdk.incubator.vector.VectorMathLibrary.DEBUG");

    private static final SymbolLookup LOOKUP = SymbolLookup.loaderLookup();

    // SVML method naming convention
    //   All the methods are named as __jsvml_<op><T><N>_ha_<VV>
    //   Where:
    //      ha stands for high accuracy
    //      <T> is optional to indicate float/double
    //              Set to f for vector float operation
    //              Omitted for vector double operation
    //      <N> is the number of elements in the vector
    //              1, 2, 4, 8, 16
    //              e.g. 128 bit float vector has 4 float elements
    //      <VV> indicates the avx/sse level:
    //              z0 is AVX512, l9 is AVX2, e9 is AVX1 and ex is for SSE2
    //      e.g. __jsvml_expf16_ha_z0 is the method for computing 16 element vector float exp using AVX 512 insns
    //           __jsvml_exp8_ha_z0 is the method for computing 8 element vector double exp using AVX 512 insns
    private static class SVML {
        static {
            loadNativeLibrary();
        }

        @SuppressWarnings({"removal", "restricted"})
        private static void loadNativeLibrary() {
            System.loadLibrary("jsvml");
        }

        static int AVX = 0;
        static String suffix(VectorShape vshape) {
            String avx_sse_str = (AVX >= 2) ? "l9" : ((AVX == 1) ? "e9" : "ex");
            return switch (vshape) {
                case S_64_BIT  -> avx_sse_str;
                case S_128_BIT -> avx_sse_str;
                case S_256_BIT -> avx_sse_str;
                case S_512_BIT -> "z0";
                case S_Max_BIT -> throw new InternalError("NYI");
            };
        }

        static String symbolName(Operator op, VectorSpecies<?> vspecies) {
            String suffix = suffix(vspecies.vectorShape());
            return String.format("__jsvml_%s%s%d_%s", op.operatorName(), vspecies.elementType(), vspecies.length(), suffix);
        }

        // VectorSupport::VEC_SIZE_512:
        // if ((!VM_Version::supports_avx512dq()) &&
        //     (vop == VectorSupport::VECTOR_OP_LOG || vop == VectorSupport::VECTOR_OP_LOG10 || vop == VectorSupport::VECTOR_OP_POW)) {
        //    continue;
        // }
        // if (vop == VectorSupport::VECTOR_OP_POW) {
        //   continue;
        // }
    }

    private static class SLEEF {
        static {
            loadNativeLibrary();
        }

        @SuppressWarnings({"removal", "restricted"})
        private static void loadNativeLibrary() {
            System.loadLibrary("sleef");
        }

        static String suffix(VectorShape vshape) {
            return "advsimd"; // FIXME
        }

        static String precisionLevel(Operator op) {
            return (op == HYPOT ? "u05" : "u10");
        }

        // Method naming convention
        //   All the methods are named as <OP><T><N>_<U><suffix>
        //   Where:
        //     <OP>     is the operation name, e.g. sin
        //     <T>      is optional to indicate float/double
        //              "f/d" for vector float/double operation
        //     <N>      is the number of elements in the vector
        //              "2/4" for neon, and "x" for sve
        //     <U>      is the precision level
        //              "u10/u05" represents 1.0/0.5 ULP error bounds
        //               We use "u10" for all operations by default
        //               But for those functions do not have u10 support, we use "u05" instead
        //     <suffix> indicates neon/sve
        //              "sve/advsimd" for sve/neon implementations
        //     e.g. sinfx_u10sve is the method for computing vector float sin using SVE instructions
        //          cosd2_u10advsimd is the method for computing 2 elements vector double cos using NEON instructions
        static String symbolName(Operator op, VectorSpecies<?> vspecies) {
            return String.format("%s%s%d_%s%s", op.operatorName(),
                                 (vspecies.elementType() == float.class ? "f" : "d"),
                                 vspecies.length(),
                                 precisionLevel(op),
                                 suffix(vspecies.vectorShape()));
        }
    }

    private static final int SIZE = VectorSupport.VECTOR_OP_MATHLIB_LAST - VectorSupport.VECTOR_OP_MATHLIB_FIRST + 1;

    private record Entry<T> (String name, MemorySegment entry, T impl) {}

    private static final @Stable Entry<?>[][][] LIBRARY = new Entry<?>[SIZE][LaneType.SK_LIMIT][VectorShape.SK_LIMIT]; // OP x SHAPE x TYPE

    @ForceInline
    private static <T> Entry<T> lookup(Operator op, int opc, VectorSpecies<?> vspecies, IntFunction<T> implSupplier) {
        int idx = opc - VectorSupport.VECTOR_OP_MATHLIB_FIRST;
        int elem_idx = ((AbstractSpecies<?>)vspecies).laneType.switchKey;
        int shape_idx = vspecies.vectorShape().switchKey;
        @SuppressWarnings({"unchecked"})
        Entry<T> entry = (Entry<T>)LIBRARY[idx][elem_idx][shape_idx];
        if (entry == null) {
            entry = constructEntry(op, opc, vspecies, implSupplier);
            LIBRARY[idx][elem_idx][shape_idx] = entry; // FIXME: CAS
        }
        return entry;
    }

    @DontInline
    private static
    <E, V extends Vector<E>, T>
    @SuppressWarnings({"unchecked"})
    Entry<T> constructEntry(Operator op, int opc, VectorSpecies<E> vspecies, IntFunction<T> implSupplier) {
        String symbol = SLEEF.symbolName(op, vspecies); // FIXME
        MemorySegment addr = LOOKUP.find(symbol).orElse(MemorySegment.NULL); // FIXME
        if (DEBUG) {
            System.out.printf("DEBUG: VectorMathLibrary: %s %s => 0x%016x\n", op, symbol, addr.address());
        }
        T impl;
        if (addr != MemorySegment.NULL) {
            @SuppressWarnings({"unchecked"})
            Class<V> vt = (Class<V>)vspecies.vectorType();
            if (op instanceof Unary) {
                var defaultImpl = (VectorSupport.UnaryOperation<V, ?>) implSupplier.apply(opc);

                impl = (T)(VectorSupport.UnaryOperation<V,?>) (v0, m) -> {
                    assert m == null;
                    return VectorSupport.libraryUnaryOp(
                            addr.address(), vt, vspecies.elementType(), vspecies.length(),
                            v0,
                            defaultImpl, // FIXME: should call the same native function
                            symbol);
                };
            } else if (op instanceof Binary) {
                var defaultImpl = (VectorSupport.BinaryOperation<V, ?>) implSupplier.apply(opc);

                impl = (T)(VectorSupport.BinaryOperation<V,?> ) (v0, v1, m) -> {
                    assert m == null;
                    return VectorSupport.libraryBinaryOp(
                            addr.address(), vt, vspecies.elementType(), vspecies.length(),
                            v0, v1,
                            defaultImpl, // FIXME: should call the same native function
                            symbol);
                };
            } else {
                throw new InternalError("operation not supported: " + op);
            }
        } else {
            impl = implSupplier.apply(opc); // use default implementation
        }
        return new Entry<>(symbol, addr, impl);
    }

    @ForceInline
    /*package-private*/ static
    <E, V extends Vector<E>>
    V unaryMathOp(VectorOperators.Unary op, int opc, VectorSpecies<E> vspecies,
                  IntFunction<VectorSupport.UnaryOperation<V,?>> implSupplier,
                  V v1) {
        var impl = lookup(op, opc, vspecies, implSupplier).impl;
        return impl.apply(v1, null);
    }

    @ForceInline
    /*package-private*/ static
    <E, V extends Vector<E>>
    V binaryMathOp(VectorOperators.Binary op, int opc, VectorSpecies<E> vspecies,
                   IntFunction<VectorSupport.BinaryOperation<V,?>> implSupplier,
                   V v1, V v2) {
        var impl = lookup(op, opc, vspecies, implSupplier).impl;
        return impl.apply(v1, v2, null);
    }
}
