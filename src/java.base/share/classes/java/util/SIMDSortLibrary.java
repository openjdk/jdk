package java.util;

import jdk.internal.misc.VM;
import jdk.internal.vm.vector.VectorSupport;

import java.lang.invoke.*;
import java.lang.foreign.*;
import java.nio.ByteOrder;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import static java.lang.foreign.ValueLayout.*;
import static java.lang.foreign.MemoryLayout.PathElement.*;

@SuppressWarnings("restricted")
class SIMDSortLibrary {

    SIMDSortLibrary() {
        // Should not be called directly
    }

    static final boolean DEBUG   = Boolean.getBoolean("java.util.SIMDSortLibrary.DEBUG");
    static final boolean DISABLE = Boolean.getBoolean("java.util.SIMDSortLibrary.DISABLE");

    static final Arena LIBRARY_ARENA = Arena.ofAuto();
    static final boolean TRACE_DOWNCALLS = Boolean.getBoolean("jextract.trace.downcalls");

    static void traceDowncall(String name, Object... args) {
         String traceArgs = Arrays.stream(args)
                       .map(Object::toString)
                       .collect(Collectors.joining(", "));
         System.out.printf("%s(%s)\n", name, traceArgs);
    }

    static MemorySegment findOrThrow(String symbol) {
        return SYMBOL_LOOKUP.find(symbol)
            .orElseThrow(() -> new UnsatisfiedLinkError("unresolved symbol: " + symbol));
    }

    static final SymbolLookup SYMBOL_LOOKUP = getLibraryLookup();

    static void debug(String format, Object ... args) {
        if (DEBUG) {
            System.out.printf("DEBUG: SIMDSortLibrary: " + format + "\n", args);
        }
    }

    static SymbolLookup getLibraryLookup () {
        if (DISABLE) {
            debug("library is disabled");
            return null;
        }
        try {
            return SymbolLookup.libraryLookup(System.mapLibraryName("simdsort"), LIBRARY_ARENA);
        } catch (Exception e) {
            debug("library failed to load: " + e);
            return null;
        }
    }

    public static boolean isPresent() {
        return SYMBOL_LOOKUP != null;
    }

    public static final ValueLayout.OfInt C_INT = ValueLayout.JAVA_INT;

    public static final AddressLayout C_POINTER = ValueLayout.ADDRESS
            .withTargetLayout(MemoryLayout.sequenceLayout(java.lang.Long.MAX_VALUE, JAVA_BYTE));

    public static final MemorySegment SORT_INT_ADDR;
    public static final MemorySegment SORT_LONG_ADDR;
    public static final MemorySegment SORT_FLOAT_ADDR;
    public static final MemorySegment SORT_DOUBLE_ADDR;

    public static final MemorySegment PARTITION_INT_ADDR;
    public static final MemorySegment PARTITION_LONG_ADDR;
    public static final MemorySegment PARTITION_FLOAT_ADDR;
    public static final MemorySegment PARTITION_DOUBLE_ADDR;

    // FIXME
    private static int AVX = computeAVXLevel();

    static {
        debug("AVX=%d", AVX);
        debug("cpu_features=%s", VM.getCPUFeaturesString());
    }

    private static int computeAVXLevel() {
        if (SIMDSortLibrary.isPresent()) {
            if (VectorSupport.getMaxLaneCount(long.class) == 8) {
                String cpuFeatures = VM.getCPUFeaturesString();
                if (cpuFeatures.contains("avx512dq")) {
                    return 4;
                } else {
                    return 3;
                }
            } else if (VectorSupport.getMaxLaneCount(long.class) == 4) {
                return 2;
            }
        }
        return 0; // not supported
    }

    static {
        if (isPresent()) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment lib = arena.allocate(library.LAYOUT);
                simdsort_link(lib, AVX);

                SORT_INT_ADDR    = library.sort_jint(lib);
                SORT_LONG_ADDR   = library.sort_jlong(lib);
                SORT_FLOAT_ADDR  = library.sort_jfloat(lib);
                SORT_DOUBLE_ADDR = library.sort_jdouble(lib);

                PARTITION_INT_ADDR    = library.partition_jint(lib);
                PARTITION_LONG_ADDR   = library.partition_jlong(lib);
                PARTITION_FLOAT_ADDR  = library.partition_jfloat(lib);
                PARTITION_DOUBLE_ADDR = library.partition_jdouble(lib);
            }
        } else {
            SORT_INT_ADDR    = MemorySegment.NULL;
            SORT_LONG_ADDR   = MemorySegment.NULL;
            SORT_FLOAT_ADDR  = MemorySegment.NULL;
            SORT_DOUBLE_ADDR = MemorySegment.NULL;

            PARTITION_INT_ADDR    = MemorySegment.NULL;
            PARTITION_LONG_ADDR   = MemorySegment.NULL;
            PARTITION_FLOAT_ADDR  = MemorySegment.NULL;
            PARTITION_DOUBLE_ADDR = MemorySegment.NULL;
        }
    }

    private static class simdsort_link {
        static final FunctionDescriptor DESC = FunctionDescriptor.ofVoid(C_POINTER, C_INT);
        static final MemorySegment ADDR = findOrThrow("simdsort_link");
        static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, DESC);
    }

    /**
     * {@snippet lang=c :
     * void simdsort_link(struct library *lib, int config)
     * }
     */
    public static void simdsort_link(MemorySegment lib, int config) {
        var mh$ = simdsort_link.HANDLE;
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("simdsort_link", lib, config);
            }
            mh$.invokeExact(lib, config);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static void sort_int(MemorySegment array, int from_index, int to_index) {
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("sort_int", array, from_index, to_index);
            }
            SORT_HANDLE.invokeExact(SORT_INT_ADDR, array, from_index, to_index);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static void sort_long(MemorySegment array, int from_index, int to_index) {
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("sort_long", array, from_index, to_index);
            }
            SORT_HANDLE.invokeExact(SORT_LONG_ADDR, array, from_index, to_index);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static void sort_float(MemorySegment array, int from_index, int to_index) {
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("sort_float", array, from_index, to_index);
            }
            SORT_HANDLE.invokeExact(SORT_FLOAT_ADDR, array, from_index, to_index);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static void sort_double(MemorySegment array, int from_index, int to_index) {
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("sort_int", array, from_index, to_index);
            }
            SORT_HANDLE.invokeExact(SORT_DOUBLE_ADDR, array, from_index, to_index);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static void partition_int(MemorySegment array, int from_index, int to_index, MemorySegment pivot_indices, int index_pivot1, int index_pivot2) {
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("partition_int", array, from_index, to_index);
            }
            PART_HANDLE.invokeExact(PARTITION_INT_ADDR, array, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static void partition_long(MemorySegment array, int from_index, int to_index, MemorySegment pivot_indices, int index_pivot1, int index_pivot2) {
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("partition_long", array, from_index, to_index);
            }
            PART_HANDLE.invokeExact(PARTITION_LONG_ADDR, array, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static void partition_float(MemorySegment array, int from_index, int to_index, MemorySegment pivot_indices, int index_pivot1, int index_pivot2) {
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("partition_float", array, from_index, to_index);
            }
            PART_HANDLE.invokeExact(PARTITION_FLOAT_ADDR, array, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static void partition_double(MemorySegment array, int from_index, int to_index, MemorySegment pivot_indices, int index_pivot1, int index_pivot2) {
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("partition_double", array, from_index, to_index);
            }
            PART_HANDLE.invokeExact(PARTITION_DOUBLE_ADDR, array, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    public static final FunctionDescriptor SORT_DESC = FunctionDescriptor.ofVoid(C_POINTER, C_INT, C_INT);
    public static final FunctionDescriptor PART_DESC = FunctionDescriptor.ofVoid(C_POINTER, C_INT, C_INT, C_POINTER, C_INT, C_INT);

    public static final MethodHandle SORT_HANDLE = Linker.nativeLinker().downcallHandle(SORT_DESC, Linker.Option.critical(true));
    public static final MethodHandle PART_HANDLE = Linker.nativeLinker().downcallHandle(PART_DESC, Linker.Option.critical(true));

    private static class avx2_sort_int {
        public static final MemorySegment ADDR = findOrThrow("avx2_sort_int");
    }

    public static void avx2_sort_int(MemorySegment array, int from_index, int to_index) {
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("avx2_sort_int", array, from_index, to_index);
            }
            SORT_HANDLE.invokeExact(avx2_sort_int.ADDR, array, from_index, to_index);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    private static class avx2_sort_float {
        public static final MemorySegment ADDR = findOrThrow("avx2_sort_float");
        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, SORT_DESC);
    }

    public static void avx2_sort_float(MemorySegment array, int from_index, int to_index) {
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("avx2_sort_float", array, from_index, to_index);
            }
            SORT_HANDLE.invokeExact(avx2_sort_float.ADDR, array, from_index, to_index);
        } catch (Throwable ex$) {
           throw new AssertionError("should not reach here", ex$);
        }
    }

    private static class avx512_sort_int {
        public static final MemorySegment ADDR = findOrThrow("avx512_sort_int");
        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, SORT_DESC, Linker.Option.critical(true));
    }

    public static void avx512_sort_int(MemorySegment array, int from_index, int to_index) {
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("avx512_sort_int", array, from_index, to_index);
            }
            avx512_sort_int.HANDLE.invokeExact(array, from_index, to_index);
        } catch (Throwable ex$) {
           throw new AssertionError("should not reach here", ex$);
        }
    }
    private static class avx512_sort_long {
        public static final MemorySegment ADDR = findOrThrow("avx512_sort_long");
        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, SORT_DESC, Linker.Option.critical(true));
    }

     public static void avx512_sort_long(MemorySegment array, int from_index, int to_index) {
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("avx512_sort_long", array, from_index, to_index);
            }
            avx512_sort_long.HANDLE.invokeExact(array, from_index, to_index);
        } catch (Throwable ex$) {
           throw new AssertionError("should not reach here", ex$);
        }
    }

    private static class avx512_sort_float {
        public static final MemorySegment ADDR = findOrThrow("avx512_sort_float");
        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, SORT_DESC, Linker.Option.critical(true));
    }

    public static void avx512_sort_float(MemorySegment array, int from_index, int to_index) {
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("avx512_sort_float", array, from_index, to_index);
            }
            avx512_sort_float.HANDLE.invokeExact(array, from_index, to_index);
        } catch (Throwable ex$) {
           throw new AssertionError("should not reach here", ex$);
        }
    }

    private static class avx512_sort_double {
        public static final MemorySegment ADDR = findOrThrow("avx512_sort_double");
        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, SORT_DESC, Linker.Option.critical(true));
    }

    public static void avx512_sort_double(MemorySegment array, int from_index, int to_index) {
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("avx512_sort_double", array, from_index, to_index);
            }
            avx512_sort_double.HANDLE.invokeExact(array, from_index, to_index);
        } catch (Throwable ex$) {
           throw new AssertionError("should not reach here", ex$);
        }
    }

    private static class avx2_partition_int {
        public static final MemorySegment ADDR = findOrThrow("avx2_partition_int");
        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, PART_DESC, Linker.Option.critical(true));
    }

    public static void avx2_partition_int(MemorySegment array, int from_index, int to_index, MemorySegment pivot_indices, int index_pivot1, int index_pivot2) {
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("avx2_partition_int", array, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
            }
            avx2_partition_int.HANDLE.invokeExact(array, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
        } catch (Throwable ex$) {
           throw new AssertionError("should not reach here", ex$);
        }
    }

    private static class avx2_partition_float {
        public static final MemorySegment ADDR = findOrThrow("avx2_partition_float");
        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, PART_DESC, Linker.Option.critical(true));
    }

    public static void avx2_partition_float(MemorySegment array, int from_index, int to_index, MemorySegment pivot_indices, int index_pivot1, int index_pivot2) {
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("avx2_partition_float", array, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
            }
            avx2_partition_float.HANDLE.invokeExact(array, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
        } catch (Throwable ex$) {
           throw new AssertionError("should not reach here", ex$);
        }
    }

    private static class avx512_partition_int {
        public static final MemorySegment ADDR = findOrThrow("avx512_partition_int");
        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, PART_DESC, Linker.Option.critical(true));
    }

    public static void avx512_partition_int(MemorySegment array, int from_index, int to_index, MemorySegment pivot_indices, int index_pivot1, int index_pivot2) {
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("avx512_partition_int", array, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
            }
            avx512_partition_int.HANDLE.invokeExact(array, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
        } catch (Throwable ex$) {
           throw new AssertionError("should not reach here", ex$);
        }
    }

    private static class avx512_partition_long {
        public static final MemorySegment ADDR = findOrThrow("avx512_partition_long");
        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, PART_DESC, Linker.Option.critical(true));
    }

    public static void avx512_partition_long(MemorySegment array, int from_index, int to_index, MemorySegment pivot_indices, int index_pivot1, int index_pivot2) {
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("avx512_partition_long", array, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
            }
            avx512_partition_long.HANDLE.invokeExact(array, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
        } catch (Throwable ex$) {
            throw new AssertionError("should not reach here", ex$);
        }
    }

    private static class avx512_partition_float {
        public static final MemorySegment ADDR = findOrThrow("avx512_partition_float");
        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, PART_DESC, Linker.Option.critical(true));
    }

    public static void avx512_partition_float(MemorySegment array, int from_index, int to_index, MemorySegment pivot_indices, int index_pivot1, int index_pivot2) {
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("avx512_partition_float", array, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
            }
            avx512_partition_float.HANDLE.invokeExact(array, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
        } catch (Throwable ex$) {
           throw new AssertionError("should not reach here", ex$);
        }
    }

    private static class avx512_partition_double {
        public static final MemorySegment ADDR = findOrThrow("avx512_partition_double");
        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, PART_DESC, Linker.Option.critical(true));
    }

    public static void avx512_partition_double(MemorySegment array, int from_index, int to_index, MemorySegment pivot_indices, int index_pivot1, int index_pivot2) {
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("avx512_partition_double", array, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
            }
            avx512_partition_double.HANDLE.invokeExact(array, from_index, to_index, pivot_indices, index_pivot1, index_pivot2);
        } catch (Throwable ex$) {
           throw new AssertionError("should not reach here", ex$);
        }
    }


    /**
     * {@snippet lang=c :
     * struct library {
     *     void (*sort_jint)(jint *, jint, jint);
     *     void (*sort_jlong)(jlong *, jint, jint);
     *     void (*sort_jfloat)(jfloat *, jint, jint);
     *     void (*sort_jdouble)(jdouble *, jint, jint);
     *     void (*partition_jint)(jint *, jint, jint, jint *, jint, jint);
     *     void (*partition_jlong)(jlong *, jint, jint, jint *, jint, jint);
     *     void (*partition_jfloat)(jfloat *, jint, jint, jint *, jint, jint);
     *     void (*partition_jdouble)(jdouble *, jint, jint, jint *, jint, jint);
     * }
     * }
     */
    private static class library {
        library() {
            // Should not be called directly
        }

        private static final String sort_jint$NAME    = "sort_jint";
        private static final String sort_jlong$NAME   = "sort_jlong";
        private static final String sort_jfloat$NAME  = "sort_jfloat";
        private static final String sort_jdouble$NAME = "sort_jdouble";
        private static final String partition_jint$NAME    = "partition_jint";
        private static final String partition_jlong$NAME   = "partition_jlong";
        private static final String partition_jfloat$NAME  = "partition_jfloat";
        private static final String partition_jdouble$NAME = "partition_jdouble";

        private static final AddressLayout C_POINTER = ValueLayout.ADDRESS
                .withTargetLayout(MemoryLayout.sequenceLayout(java.lang.Long.MAX_VALUE, JAVA_BYTE));

        private static final GroupLayout LAYOUT = MemoryLayout.structLayout(
                C_POINTER.withName(sort_jint$NAME),
                C_POINTER.withName(sort_jlong$NAME),
                C_POINTER.withName(sort_jfloat$NAME),
                C_POINTER.withName(sort_jdouble$NAME),
                C_POINTER.withName(partition_jint$NAME),
                C_POINTER.withName(partition_jlong$NAME),
                C_POINTER.withName(partition_jfloat$NAME),
                C_POINTER.withName(partition_jdouble$NAME)
        ).withName("library");

        private static final AddressLayout sort_jint$LAYOUT    = (AddressLayout)LAYOUT.select(groupElement(sort_jint$NAME));
        private static final AddressLayout sort_jlong$LAYOUT   = (AddressLayout)LAYOUT.select(groupElement(sort_jlong$NAME));
        private static final AddressLayout sort_jfloat$LAYOUT  = (AddressLayout)LAYOUT.select(groupElement(sort_jfloat$NAME));
        private static final AddressLayout sort_jdouble$LAYOUT = (AddressLayout)LAYOUT.select(groupElement(sort_jdouble$NAME));
        private static final AddressLayout partition_jint$LAYOUT    = (AddressLayout)LAYOUT.select(groupElement(partition_jint$NAME));
        private static final AddressLayout partition_jlong$LAYOUT   = (AddressLayout)LAYOUT.select(groupElement(partition_jlong$NAME));
        private static final AddressLayout partition_jfloat$LAYOUT  = (AddressLayout)LAYOUT.select(groupElement(partition_jfloat$NAME));
        private static final AddressLayout partition_jdouble$LAYOUT = (AddressLayout)LAYOUT.select(groupElement(partition_jdouble$NAME));

        private static final long sort_jint$OFFSET    = LAYOUT.byteOffset(groupElement(sort_jint$NAME));
        private static final long sort_jlong$OFFSET   = LAYOUT.byteOffset(groupElement(sort_jlong$NAME));
        private static final long sort_jfloat$OFFSET  = LAYOUT.byteOffset(groupElement(sort_jfloat$NAME));
        private static final long sort_jdouble$OFFSET = LAYOUT.byteOffset(groupElement(sort_jdouble$NAME));
        private static final long partition_jint$OFFSET    = LAYOUT.byteOffset(groupElement(partition_jint$NAME));
        private static final long partition_jlong$OFFSET   = LAYOUT.byteOffset(groupElement(partition_jlong$NAME));
        private static final long partition_jfloat$OFFSET  = LAYOUT.byteOffset(groupElement(partition_jfloat$NAME));
        private static final long partition_jdouble$OFFSET = LAYOUT.byteOffset(groupElement(partition_jdouble$NAME));

        public static MemorySegment sort_jint(MemorySegment struct) {
            return struct.get(sort_jint$LAYOUT, sort_jint$OFFSET);
        }
        public static MemorySegment sort_jlong(MemorySegment struct) {
            return struct.get(sort_jlong$LAYOUT, sort_jlong$OFFSET);
        }
        public static MemorySegment sort_jfloat(MemorySegment struct) {
            return struct.get(sort_jfloat$LAYOUT, sort_jfloat$OFFSET);
        }
        public static MemorySegment sort_jdouble(MemorySegment struct) {
            return struct.get(sort_jdouble$LAYOUT, sort_jdouble$OFFSET);
        }

        public static MemorySegment partition_jint(MemorySegment struct) {
            return struct.get(partition_jint$LAYOUT, partition_jint$OFFSET);
        }
        public static MemorySegment partition_jlong(MemorySegment struct) {
            return struct.get(partition_jlong$LAYOUT, partition_jlong$OFFSET);
        }
        public static MemorySegment partition_jfloat(MemorySegment struct) {
            return struct.get(partition_jfloat$LAYOUT, partition_jfloat$OFFSET);
        }
        public static MemorySegment partition_jdouble(MemorySegment struct) {
            return struct.get(partition_jdouble$LAYOUT, partition_jdouble$OFFSET);
        }
    }
}
