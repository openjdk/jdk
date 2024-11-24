package java.util;

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

    static SymbolLookup getLibraryLookup () {
        try {
            return SymbolLookup.libraryLookup(System.mapLibraryName("simdsort"), LIBRARY_ARENA);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isPresent() {
        return SYMBOL_LOOKUP != null;
    }

    public static final ValueLayout.OfInt C_INT = ValueLayout.JAVA_INT;

    public static final AddressLayout C_POINTER = ValueLayout.ADDRESS
            .withTargetLayout(MemoryLayout.sequenceLayout(java.lang.Long.MAX_VALUE, JAVA_BYTE));

    public static final FunctionDescriptor SORT_DESC = FunctionDescriptor.ofVoid(C_POINTER, C_INT, C_INT);
    public static final FunctionDescriptor PARTITION_DESC = FunctionDescriptor.ofVoid(
            C_POINTER, C_INT, C_INT, C_POINTER, C_INT, C_INT);

    private static class avx2_sort_int {
        public static final MemorySegment ADDR = findOrThrow("avx2_sort_int");
        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, SORT_DESC);
    }

    public static void avx2_sort_int(MemorySegment array, int from_index, int to_index) {
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("avx2_sort_int", array, from_index, to_index);
            }
            avx2_sort_int.HANDLE.invokeExact(array, from_index, to_index);
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
            avx2_sort_float.HANDLE.invokeExact(array, from_index, to_index);
        } catch (Throwable ex$) {
           throw new AssertionError("should not reach here", ex$);
        }
    }

    private static class avx512_sort_int {
        public static final MemorySegment ADDR = findOrThrow("avx512_sort_int");
        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, SORT_DESC);
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
        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, SORT_DESC);
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
        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, SORT_DESC);
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
        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, SORT_DESC);
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
        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, PARTITION_DESC);
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
        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, PARTITION_DESC);
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
        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, PARTITION_DESC);
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
        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, PARTITION_DESC);
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
        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, PARTITION_DESC);
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
        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, PARTITION_DESC);
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
}
