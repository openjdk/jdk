package jdk.incubator.vector;

import jdk.internal.util.StaticProperty;
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

    interface Library {
        String symbolName(Operator op, VectorSpecies<?> vspecies);
        boolean isSupported(Operator op, VectorSpecies<?> vspecies);

        String SVML  = "svml";
        String SLEEF = "sleef";
        String JAVA  = "java";

        static Library getInstance() {
            String libraryName = System.getProperty("jdk.incubator.vector.VectorMathLib", getDefaultName());
            try {
                switch (libraryName) {
                    case SVML:  return new SVML();
                    case SLEEF: return new SLEEF();
                    case JAVA:  return new Java();

                    default: return new Java();
                }
            } catch (Throwable e) {
                if (DEBUG) {
                    System.out.printf("DEBUG: VectorMathLibrary: Error during initialization of %s library: %s\n",
                                      libraryName, e);
                    e.printStackTrace(System.out);
                }
                return new Java(); // fallback
            }
        }

        static String getDefaultName() {
            switch (StaticProperty.osArch()) {
                case "amd64":
                case "x86_64":
                    return SVML;
                case "aarch64":
                    return SLEEF;
                default:
                    return JAVA;
            }
        }
    }

    private static final Library LIBRARY = Library.getInstance();

    static {
        if (DEBUG) {
            System.out.printf("DEBUG: VectorMathLibrary: %s library is used\n", LIBRARY.getClass().getSimpleName());
        }
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
    private static class SVML implements Library {
        static {
            loadNativeLibrary();
        }

        @SuppressWarnings({"removal", "restricted"})
        private static void loadNativeLibrary() {
            System.loadLibrary("jsvml");
        }

        private static String suffix(VectorSpecies<?> vspecies) {
            assert vspecies.vectorBitSize() <= VectorShape.getMaxVectorBitSize(vspecies.elementType());

            boolean hasAVX2 = VectorShape.getMaxVectorBitSize(byte.class)  >= 256;
            boolean hasAVX1 = VectorShape.getMaxVectorBitSize(float.class) >= 256;

            if (vspecies.vectorBitSize() == 512) {
                return "z0";
            } else if (hasAVX2) {
                return "l9";
            } else if (hasAVX1) {
                return "e9";
            } else {
                return "ex";
            }
        }

        @Override
        public String symbolName(Operator op, VectorSpecies<?> vspecies) {
            String suffix = suffix(vspecies);
            String elemType = (vspecies.elementType() == float.class ? "f" : "");
            return String.format("__jsvml_%s%s%d_ha_%s", op.operatorName(), elemType, vspecies.length(), suffix);
        }

        @Override
        public boolean isSupported(Operator op, VectorSpecies<?> vspecies) {
            Class<?> etype = vspecies.elementType();
            if (etype != float.class && etype != double.class) {
                return false; // only FP types are supported
            }
            int maxLaneCount = VectorSupport.getMaxLaneCount(vspecies.elementType());
            if (vspecies.length() > maxLaneCount) {
                return false; // lacking vector support
            }
            if (vspecies.vectorBitSize() < 128) {
                return false; // 64-bit vectors are not supported
            }
            if (op == POW) {
                return false; // not supported
            }
            if (vspecies.vectorBitSize() == 512 && (op == LOG || op == LOG10)) {
                return false; // FIXME: requires VM_Version::supports_avx512dq())
            }
            return true;
        }
    }

    private static class SLEEF implements Library {
        static {
            loadNativeLibrary();
        }

        @SuppressWarnings({"removal", "restricted"})
        private static void loadNativeLibrary() {
            System.loadLibrary("sleef");
        }

        private static String suffix(VectorShape vshape) {
            return (vshape.vectorBitSize() > 128 ? "sve" : "advsimd");
        }

        private static String precisionLevel(Operator op) {
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
        @Override
        public String symbolName(Operator op, VectorSpecies<?> vspecies) {
            return String.format("%s%s%d_%s%s", op.operatorName(),
                                 (vspecies.elementType() == float.class ? "f" : "d"),
                                 vspecies.length(),
                                 precisionLevel(op),
                                 suffix(vspecies.vectorShape()));
        }

        @Override
        public boolean isSupported(Operator op, VectorSpecies<?> vspecies) {
            Class<?> etype = vspecies.elementType();
            if (etype != float.class && etype != double.class) {
                return false; // only FP element types are supported
            }
            int maxLaneCount = VectorSupport.getMaxLaneCount(vspecies.elementType());
            if (vspecies.length() > maxLaneCount) {
                return false; // lacking vector support
            }
            if (vspecies.vectorBitSize() < 128) {
                return false; // 64-bit vectors are not supported
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
            LIBRARY_ENTRIES[idx][elem_idx][shape_idx] = entry; // FIXME: CAS
        }
        return entry;
    }

    @DontInline
    private static
    <E,T>
    Entry<T> constructEntry(Operator op, int opc, VectorSpecies<E> vspecies, IntFunction<T> implSupplier) {
        if (LIBRARY.isSupported(op, vspecies)) {
            String symbol = LIBRARY.symbolName(op, vspecies);
            MemorySegment addr = LOOKUP.find(symbol).orElseThrow(() -> new InternalError("not supported: " + op + " " + vspecies + " " + symbol));
            if (DEBUG) {
                System.out.printf("DEBUG: VectorMathLibrary: %s %s => 0x%016x\n", op, symbol, addr.address());
            }
            T impl = implSupplier.apply(opc); // FIXME: should call the very same native impl
            return new Entry<>(symbol, addr, impl);
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
                    entry.entry.address(), vt, vspecies.elementType(), vspecies.length(),
                    v,
                    entry.impl,
                    entry.name);
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
                    entry.entry.address(), vt, vspecies.elementType(), vspecies.length(),
                    v1, v2,
                    entry.impl,
                    entry.name);
        } else {
            return entry.impl.apply(v1, v2, null);
        }
    }
}
