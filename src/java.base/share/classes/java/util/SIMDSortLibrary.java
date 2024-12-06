package java.util;

import jdk.internal.foreign.CABI;
import jdk.internal.misc.VM;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.annotation.Native;
import java.lang.invoke.*;
import java.lang.foreign.*;
import java.util.stream.*;

import static java.lang.foreign.ValueLayout.*;

@SuppressWarnings("restricted")
class SIMDSortLibrary {
    static final boolean TRACE = "trace".equalsIgnoreCase(System.getProperty("java.util.SIMDSortLibrary.LOG"));
    static final boolean DEBUG = "debug".equalsIgnoreCase(System.getProperty("java.util.SIMDSortLibrary.LOG")) || TRACE;
    static final boolean INFO  = "info".equalsIgnoreCase(System.getProperty("java.util.SIMDSortLibrary.LOG")) || DEBUG;

    static final boolean DISABLE = Boolean.getBoolean("java.util.SIMDSortLibrary.DISABLE");

    SIMDSortLibrary() {
        // Should not be called directly
    }

    private static final NativeSortingLibrary LIBRARY = loadLibrary();

    public static NativeSortingLibrary getLibrary() {
        return LIBRARY;
    }

    static NativeSortingLibrary loadLibrary() {
        if (DISABLE) {
            info("library is disabled");
            return new FallbackLibrary();
        }
        if (isLinuxX64() && LinuxX64Library.isPresent()) {
            return new LinuxX64Library();
        }
        return new FallbackLibrary();
    }

    /**
     * Represents a function that accepts the array and sorts the specified range
     * of the array into ascending order.
     */
    @FunctionalInterface
    interface SortOperation<A> {
        /**
         * Sorts the specified range of the array.
         *
         * @param a the array to be sorted
         * @param low the index of the first element, inclusive, to be sorted
         * @param high the index of the last element, exclusive, to be sorted
         */
        void sort(A a, int low, int high);
    }

    /**
     * Represents a function that accepts the array and partitions the specified range
     * of the array using the pivots provided.
     */
    @FunctionalInterface
    interface PartitionOperation<A> {
        /**
         * Partitions the specified range of the array using the given pivots.
         *
         * @param a the array to be partitioned
         * @param low the index of the first element, inclusive, to be partitioned
         * @param high the index of the last element, exclusive, to be partitioned
         * @param pivotIndex1 the index of pivot1, the first pivot
         * @param pivotIndex2 the index of pivot2, the second pivot
         */
        int[] partition(A a, int low, int high, int pivotIndex1, int pivotIndex2);
    }

    interface NativeSortingLibrary {
        <A> SortOperation<A> wrapSort(Class<A> cls, SortOperation<A> defaultImpl);

        <A> PartitionOperation<A> wrapPartition(Class<A> cls, PartitionOperation<A> defaultImpl);
    }

    static class FallbackLibrary implements NativeSortingLibrary {

        @Override
        public <A> SortOperation<A> wrapSort(Class<A> cls, SortOperation<A> defaultImpl) {
            return defaultImpl;
        }

        @Override
        public <A> PartitionOperation<A> wrapPartition(Class<A> cls, PartitionOperation<A> defaultImpl) {
            return defaultImpl;
        }
    }

    private static boolean isLinuxX64() {
        return (CABI.current() == CABI.SYS_V);
    }

    static class LinuxX64Library implements NativeSortingLibrary {
        public static final ValueLayout.OfInt C_INT = ValueLayout.JAVA_INT;

        public static final AddressLayout C_POINTER = ValueLayout.ADDRESS
                .withTargetLayout(MemoryLayout.sequenceLayout(java.lang.Long.MAX_VALUE, JAVA_BYTE));

        private static final long VM_AVX2 = getCPUFeature("avx2", 1L << 19);
        private static final long VM_AVX512DQ = getCPUFeature("avx512dq", 1L << 28);

        static long getCPUFeature(String name, long feature) {
            boolean namesFeature = VM.getCPUFeaturesString().contains(name);
            boolean hasFeature = ((VM.getCPUFeatures() & feature) == feature);
            if (namesFeature != hasFeature) {
                throw new InternalError(name);
            }
            return feature;
        }

        public static boolean isPresent() {
            return SYMBOL_LOOKUP != null;
        }

        static {
            if (!isLinuxX64()) {
                throw new InternalError("linux-x64 only");
            }
            debug("cpu_features[0x%016x]=%s", VM.getCPUFeatures(), VM.getCPUFeaturesString());

            USE_AVX2 = (VM.getCPUFeatures() & VM_AVX2) != 0L;
            USE_AVX512 = VM.isIntelCPU() && (VM.getCPUFeatures() & VM_AVX512DQ) != 0L;

            SYMBOL_LOOKUP = getLibraryLookup();
        }

        private static final boolean USE_AVX512;
        private static final boolean USE_AVX2;

        static final SymbolLookup SYMBOL_LOOKUP;

        static SymbolLookup getLibraryLookup() {
            try {
                return SymbolLookup.libraryLookup(System.mapLibraryName("simdsort"), Arena.ofAuto());
            } catch (Exception e) {
                info("library failed to load: " + e);
                return null;
            }
        }

        @SuppressWarnings("unchecked")
        public <A> SortOperation<A> wrapSort(Class<A> cls, SortOperation<A> defaultImpl) {
            if (isPresent()) {
                return (SortOperation<A>) wrap(cls, defaultImpl);
            } else {
                return defaultImpl;
            }
        }

        @SuppressWarnings("unchecked")
        public <A> PartitionOperation<A> wrapPartition(Class<A> cls, PartitionOperation<A> defaultImpl) {
            if (isPresent()) {
                return (PartitionOperation<A>) wrap(cls, defaultImpl);
            } else {
                return defaultImpl;
            }
        }

        private static <A> SortOperation<?> select(SortOperation<A> avx512Impl,
                                                   SortOperation<A> avx2Impl,
                                                   SortOperation<?> defaultImpl) {
            if (USE_AVX512 && avx512Impl != null) {
                return avx512Impl;
            } else if (USE_AVX2 && avx2Impl != null) {
                return avx2Impl;
            } else {
                return defaultImpl;
            }
        }

        private static <A> SortOperation<?> wrap(Class<A> cls, SortOperation<A> defaultImpl) {
            if (cls == int[].class) {
                return select(avx512_sort_int::sort, avx2_sort_int::sort, defaultImpl);
            } else if (cls == long[].class) {
                return select(avx512_sort_long::sort, null, defaultImpl);
            } else if (cls == float[].class) {
                return select(avx512_sort_float::sort, avx2_sort_float::sort, defaultImpl);
            } else if (cls == double[].class) {
                return select(avx512_sort_double::sort, null, defaultImpl);
            } else {
                throw new InternalError("not supported: " + cls);
            }
        }

        private static <A> PartitionOperation<?> select(PartitionOperation<A> avx512Impl,
                                                        PartitionOperation<A> avx2Impl,
                                                        PartitionOperation<?> defaultImpl) {
            if (USE_AVX512 && avx512Impl != null) {
                return avx512Impl;
            } else if (USE_AVX2 && avx2Impl != null) {
                return avx2Impl;
            } else {
                return defaultImpl;
            }
        }

        private static <A> PartitionOperation<?> wrap(Class<A> cls, PartitionOperation<A> defaultImpl) {
            if (cls == int[].class) {
                return select(avx512_partition_int::partition, avx2_partition_int::partition, defaultImpl);
            } else if (cls == long[].class) {
                return select(avx512_partition_long::partition, null, defaultImpl);
            } else if (cls == float[].class) {
                return select(avx512_partition_float::partition, avx2_partition_float::partition, defaultImpl);
            } else if (cls == double[].class) {
                return select(avx512_partition_double::partition, null, defaultImpl);
            } else {
                throw new InternalError("not supported: " + cls);
            }
        }

        private static MethodHandle SORT_INVOKER = Linker.nativeLinker().downcallHandle(
                FunctionDescriptor.ofVoid(C_POINTER, C_INT, C_INT),
                Linker.Option.critical(true));

        /**
         * {@snippet lang=c :
         * void avx2_sort_int(jint* array, jint from_index, jint to_index);
         * }
         */
        private static class avx2_sort_int {
            private static final MethodHandle HANDLER = findOrThrow("avx2_sort_int", SORT_INVOKER);

            @ForceInline
            private static void sort(int[] array, int low, int high) {
                try {
                    HANDLER.invokeExact(MemorySegment.ofArray(array), low, high);
                } catch (Throwable e) {
                    throw new InternalError(e);
                }
            }
        }

        /**
         * {@snippet lang=c :
         * void avx2_sort_float(jfloat* array, jint from_index, jint to_index);
         * }
         */
        private static class avx2_sort_float {
            private static final MethodHandle HANDLER = findOrThrow("avx2_sort_float", SORT_INVOKER);

            @ForceInline
            private static void sort(float[] array, int low, int high) {
                try {
                    HANDLER.invokeExact(MemorySegment.ofArray(array), low, high);
                } catch (Throwable e) {
                    throw new InternalError(e);
                }
            }
        }

        /**
         * {@snippet lang=c :
         * void avx512_sort_int(jint* array, jint from_index, jint to_index);
         * }
         */
        private static class avx512_sort_int {
            private static final MethodHandle HANDLER = findOrThrow("avx512_sort_int", SORT_INVOKER);

            @ForceInline
            private static void sort(int[] array, int low, int high) {
                try {
                    HANDLER.invokeExact(MemorySegment.ofArray(array), low, high);
                } catch (Throwable e) {
                    throw new InternalError(e);
                }
            }
        }

        /**
         * {@snippet lang=c :
         * void avx512_sort_long(jlong* array, jint from_index, jint to_index);
         * }
         */
        private static class avx512_sort_long {
            private static final MethodHandle HANDLER = findOrThrow("avx512_sort_long", SORT_INVOKER);

            @ForceInline
            private static void sort(long[] array, int low, int high) {
                try {
                    HANDLER.invokeExact(MemorySegment.ofArray(array), low, high);
                } catch (Throwable e) {
                    throw new InternalError(e);
                }
            }
        }

        /**
         * {@snippet lang=c :
         * void avx512_sort_float(jfloat* array, jint from_index, jint to_index);
         * }
         */
        private static class avx512_sort_float {
            private static final MethodHandle HANDLER = findOrThrow("avx512_sort_float", SORT_INVOKER);

            @ForceInline
            private static void sort(float[] array, int low, int high) {
                try {
                    HANDLER.invokeExact(MemorySegment.ofArray(array), low, high);
                } catch (Throwable e) {
                    throw new InternalError(e);
                }
            }
        }

        /**
         * {@snippet lang=c :
         * void avx512_sort_double(jdouble*  array, jint from_index, jint to_index);
         * }
         */
        private static class avx512_sort_double {
            private static final MethodHandle HANDLER = findOrThrow("avx512_sort_double", SORT_INVOKER);

            @ForceInline
            private static void sort(double[] array, int low, int high) {
                try {
                    HANDLER.invokeExact(MemorySegment.ofArray(array), low, high);
                } catch (Throwable e) {
                    throw new InternalError(e);
                }
            }
        }

        private static MethodHandle PARTITION_INVOKER = Linker.nativeLinker().downcallHandle(
                FunctionDescriptor.ofVoid(C_POINTER, C_INT, C_INT, C_POINTER, C_INT, C_INT),
                Linker.Option.critical(true));

        /**
         * {@snippet lang=c :
         * void avx2_partition_int(jint* array, jint from_index, jint to_index, jint* pivot_indices, jint index_pivot1, jint index_pivot2);
         * }
         */
        private static class avx2_partition_int {
            private static final MethodHandle HANDLER = findOrThrow("avx2_partition_int", PARTITION_INVOKER);

            @ForceInline
            private static int[] partition(int[] array, int low, int high, int pivotIndex1, int pivotIndex2) {
                int[] pivotIndices = new int[2];
                try {
                    HANDLER.invokeExact(MemorySegment.ofArray(array), low, high,
                            MemorySegment.ofArray(pivotIndices), pivotIndex1, pivotIndex2);
                } catch (Throwable e) {
                    throw new InternalError(e);
                }
                return pivotIndices;
            }
        }

        /**
         * {@snippet lang=c :
         * void avx2_partition_float(jfloat* array, jint from_index, jint to_index, jint* pivot_indices, jint index_pivot1, jint index_pivot2);
         * }
         */
        private static class avx2_partition_float {
            private static final MethodHandle HANDLER = findOrThrow("avx2_partition_float", PARTITION_INVOKER);

            @ForceInline
            private static int[] partition(float[] array, int low, int high, int pivotIndex1, int pivotIndex2) {
                int[] pivotIndices = new int[2];
                try {
                    HANDLER.invokeExact(MemorySegment.ofArray(array), low, high,
                            MemorySegment.ofArray(pivotIndices), pivotIndex1, pivotIndex2);
                } catch (Throwable e) {
                    throw new InternalError(e);
                }
                return pivotIndices;
            }
        }

        /**
         * {@snippet lang=c :
         * void avx512_partition_int(jint* array, jint from_index, jint to_index, jint* pivot_indices, jint index_pivot1, jint index_pivot2);
         * }
         */
        private static class avx512_partition_int {
            private static final MethodHandle HANDLER = findOrThrow("avx512_partition_int", PARTITION_INVOKER);

            @ForceInline
            private static int[] partition(int[] array, int low, int high, int pivotIndex1, int pivotIndex2) {
                int[] pivotIndices = new int[2];
                try {
                    HANDLER.invokeExact(MemorySegment.ofArray(array), low, high,
                            MemorySegment.ofArray(pivotIndices), pivotIndex1, pivotIndex2);
                } catch (Throwable e) {
                    throw new InternalError(e);
                }
                return pivotIndices;
            }
        }

        /**
         * {@snippet lang=c :
         * void avx512_partition_long(jlong* array, jint from_index, jint to_index, jint* pivot_indices, jint index_pivot1, jint index_pivot2);
         * }
         */
        private static class avx512_partition_long {
            private static final MethodHandle HANDLER = findOrThrow("avx512_partition_long", PARTITION_INVOKER);

            @ForceInline
            private static int[] partition(long[] array, int low, int high, int pivotIndex1, int pivotIndex2) {
                int[] pivotIndices = new int[2];
                try {
                    HANDLER.invokeExact(MemorySegment.ofArray(array), low, high,
                            MemorySegment.ofArray(pivotIndices), pivotIndex1, pivotIndex2);
                } catch (Throwable e) {
                    throw new InternalError(e);
                }
                return pivotIndices;
            }
        }

        /**
         * {@snippet lang=c :
         * void avx512_partition_float(jfloat* array, jint from_index, jint to_index, jint* pivot_indices, jint index_pivot1, jint index_pivot2);
         * }
         */
        private static class avx512_partition_float {
            private static final MethodHandle HANDLER = findOrThrow("avx512_partition_float", PARTITION_INVOKER);

            @ForceInline
            private static int[] partition(float[] array, int low, int high, int pivotIndex1, int pivotIndex2) {
                int[] pivotIndices = new int[2];
                try {
                    HANDLER.invokeExact(MemorySegment.ofArray(array), low, high,
                            MemorySegment.ofArray(pivotIndices), pivotIndex1, pivotIndex2);
                } catch (Throwable e) {
                    throw new InternalError(e);
                }
                return pivotIndices;
            }
        }

        /**
         * {@snippet lang=c :
         * void avx512_partition_double(jdouble* array, jint from_index, jint to_index, jint* pivot_indices, jint index_pivot1, jint index_pivot2);
         * }
         */
        private static class avx512_partition_double {
            private static final MethodHandle HANDLER = findOrThrow("avx512_partition_double", PARTITION_INVOKER);

            @ForceInline
            private static int[] partition(double[] array, int low, int high, int pivotIndex1, int pivotIndex2) {
                int[] pivotIndices = new int[2];
                try {
                    HANDLER.invokeExact(MemorySegment.ofArray(array), low, high,
                            MemorySegment.ofArray(pivotIndices), pivotIndex1, pivotIndex2);
                } catch (Throwable e) {
                    throw new InternalError(e);
                }
                return pivotIndices;
            }
        }

        static MemorySegment findOrThrow(String symbol) {
            return getLibraryLookup().find(symbol)
                    .orElseThrow(() -> new UnsatisfiedLinkError("unresolved symbol: " + symbol));
        }

        static MethodHandle findOrThrow(String symbol, MethodHandle invoker) {
            MemorySegment entry = findOrThrow(symbol);
            MethodHandle handler = invoker.bindTo(entry);
            if (TRACE) {
                handler = traceWrapper(symbol, handler);
            }
            return handler;
        }
    }

    /* Helper utilities */

    static void trace(String name, Object... args) {
        if (TRACE) {
            String traceArgs = Arrays.stream(args)
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            System.out.printf("TRACE: SIMDSortLibrary: %s(%s)\n", name, traceArgs);
        }
    }

    static void info(String format, Object ... args) {
        if (INFO) {
            System.out.printf("INFO: SIMDSortLibrary: " + format + "\n", args);
        }
    }

    static void debug(String format, Object ... args) {
        if (DEBUG) {
            System.out.printf("DEBUG: SIMDSortLibrary: " + format + "\n", args);
        }
    }

    private static MethodHandle traceWrapper(String symbol, MethodHandle handler) {
        try {
            MethodHandle tracer = MethodHandles.lookup().findStatic(SIMDSortLibrary.class, "trace",
                    MethodType.methodType(void.class, String.class, Object[].class));

            MethodHandle enter = tracer.bindTo("ENTRY: " + symbol)
                    .asCollector(Object[].class, handler.type().parameterCount())
                    .asType(handler.type());
            handler = MethodHandles.foldArguments(handler, enter);

            Class<?> retClass = handler.type().returnType();
            MethodHandle exit = tracer.bindTo("EXIT: " + symbol);
            if (retClass != void.class) {
                exit = exit.asCollector(Object[].class, 1);
                exit = exit.asType(MethodType.methodType(void.class, retClass));
                exit = MethodHandles.foldArguments(MethodHandles.identity(retClass), exit);
            } else {
                exit = exit.asCollector(Object[].class, 0);
            }
            return MethodHandles.filterReturnValue(handler, exit);
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }
}
