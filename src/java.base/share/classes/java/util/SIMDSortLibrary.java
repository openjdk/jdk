package java.util;

import jdk.internal.misc.VM;
import jdk.internal.vm.annotation.ForceInline;
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
    static final boolean TRACE   = Boolean.getBoolean("java.util.SIMDSortLibrary.TRACE");

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
            return SymbolLookup.libraryLookup(System.mapLibraryName("simdsort"), Arena.ofAuto());
        } catch (Exception e) {
            debug("library failed to load: " + e);
            return null;
        }
    }

    public static boolean isPresent() {
        return SYMBOL_LOOKUP != null;
    }

    public static final ValueLayout.OfInt  C_INT = ValueLayout.JAVA_INT;
    public static final ValueLayout.OfLong C_INT64 = ValueLayout.JAVA_LONG;

    public static final AddressLayout C_POINTER = ValueLayout.ADDRESS
            .withTargetLayout(MemoryLayout.sequenceLayout(java.lang.Long.MAX_VALUE, JAVA_BYTE));

    public static final MethodHandle SORT_INT;
    public static final MethodHandle SORT_LONG;
    public static final MethodHandle SORT_FLOAT;
    public static final MethodHandle SORT_DOUBLE;

    public static final MethodHandle PARTITION_INT;
    public static final MethodHandle PARTITION_LONG;
    public static final MethodHandle PARTITION_FLOAT;
    public static final MethodHandle PARTITION_DOUBLE;

    static {
        debug("cpu_features[0x%016x]=%s", VM.getCPUFeatures(), VM.getCPUFeaturesString());

        if (isPresent()) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment lib = arena.allocate(library.LAYOUT);
                
                simdsort_link(lib, VM.getCPUFeatures());

                MethodHandle sortInvoker = Linker.nativeLinker().downcallHandle(
                        FunctionDescriptor.ofVoid(C_POINTER, C_INT, C_INT),
                        Linker.Option.critical(true));

                SORT_INT = prepareSort(int[].class, wrapAddress(library.sort_jint(lib), sortInvoker));
                SORT_LONG = prepareSort(long[].class, wrapAddress(library.sort_jlong(lib), sortInvoker));
                SORT_FLOAT = prepareSort(float[].class, wrapAddress(library.sort_jfloat(lib), sortInvoker));
                SORT_DOUBLE = prepareSort(double[].class, wrapAddress(library.sort_jdouble(lib), sortInvoker));

                MethodHandle partitionInvoker = Linker.nativeLinker().downcallHandle(
                        FunctionDescriptor.ofVoid(C_POINTER, C_INT, C_INT, C_POINTER, C_INT, C_INT),
                        Linker.Option.critical(true));

                PARTITION_INT = preparePartition(int[].class, wrapAddress(library.partition_jint(lib), partitionInvoker));
                PARTITION_LONG = preparePartition(long[].class, wrapAddress(library.partition_jlong(lib), partitionInvoker));
                PARTITION_FLOAT = preparePartition(float[].class, wrapAddress(library.partition_jfloat(lib), partitionInvoker));
                PARTITION_DOUBLE = preparePartition(double[].class, wrapAddress(library.partition_jdouble(lib), partitionInvoker));
            }
        } else {
            SORT_INT    = null;
            SORT_LONG   = null;
            SORT_FLOAT  = null;
            SORT_DOUBLE = null;

            PARTITION_INT    = null;
            PARTITION_LONG   = null;
            PARTITION_FLOAT  = null;
            PARTITION_DOUBLE = null;
        }
    }

    private static MethodHandle prepareSort(Class<?> cls, MethodHandle mh) {
        if (mh != null) {
            try {
                MethodType mt = MethodType.methodType(MemorySegment.class, cls);
                MethodHandle ofArray = MethodHandles.lookup().findStatic(MemorySegment.class, "ofArray", mt);
                return MethodHandles.filterArguments(mh, 0, ofArray);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new InternalError(e);
            }
        } else {
            return null;
        }
    }

    @ForceInline
    private static int[] partitionInvoker(MethodHandle mh, MemorySegment a, int low, int high, int pivotIndex1, int pivotIndex2) {
        int[] pivotIndices = new int[2];
        try {
            mh.invokeExact(a, low, high, pivotIndices, pivotIndex1, pivotIndex2);
        } catch (Throwable e) {
            throw new InternalError(e);
        }
        return pivotIndices;
    }

    private static MethodHandle preparePartition(Class<?> cls, MethodHandle mh) {
        if (mh != null) {
            try {
                MethodHandle ofArray = MethodHandles.lookup().findStatic(MemorySegment.class, "ofArray",
                        MethodType.methodType(MemorySegment.class, cls));

                MethodHandle invoker = MethodHandles.lookup().findStatic(SIMDSortLibrary.class, "partitionInvoker",
                        MethodType.methodType(int[].class, MethodHandle.class, MemorySegment.class,
                                int.class, int.class, int.class, int.class));

                mh = invoker.bindTo(mh);

                mh = MethodHandles.filterArguments(mh, 0, ofArray);
                return mh;
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new InternalError(e);
            }
        } else {
            return null;
        }
    }

    private static MethodHandle wrapAddress(MemorySegment func, MethodHandle invoker) {
        if (func != MemorySegment.NULL) {
            return invoker.bindTo(func);
        } else {
            return null;
        }
    }

    /**
     * {@snippet lang=c :
     * void simdsort_link(struct library *lib, int config)
     * }
     */
    private static class simdsort_link {
        static final FunctionDescriptor DESC = FunctionDescriptor.ofVoid(C_POINTER, C_INT64);
        static final MemorySegment ADDR = findOrThrow("simdsort_link");
        static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, DESC);
    }

    private static void simdsort_link(MemorySegment lib, long config) {
        try {
            if (TRACE) {
                traceDowncall("simdsort_link", lib, config);
            }
            simdsort_link.HANDLE.invokeExact(lib, config);
        } catch (Throwable t) {
            throw new InternalError(t);
        }
    }

    static <A> DualPivotQuicksort.SortOperation<A> wrapSort(Class<A> cls, DualPivotQuicksort.SortOperation<A> defaultImpl) {
        return wrapSort(sortFor(cls), defaultImpl);
    }

    static MethodHandle sortFor(Class<?> cls) {
        if (cls == int[].class) {
            return SORT_INT;
        } else if (cls == long[].class) {
            return SORT_LONG;
        } else if (cls == float[].class) {
            return SORT_FLOAT;
        } else if (cls == double[].class) {
            return SORT_DOUBLE;
        } else {
            throw new InternalError("not supported: " + cls);
        }
    }

    @SuppressWarnings("unchecked")
    static <A> DualPivotQuicksort.SortOperation<A> wrapSort(MethodHandle impl, DualPivotQuicksort.SortOperation<A> defaultImpl) {
        if (impl != null) {
            return MethodHandleProxies.asInterfaceInstance(DualPivotQuicksort.SortOperation.class, impl);
        } else {
            return defaultImpl;
        }
    }

    static <A> DualPivotQuicksort.PartitionOperation<A> wrapPartition(Class<A> cls, DualPivotQuicksort.PartitionOperation<A> defaultImpl) {
        return wrapPartition(partitionFor(cls), defaultImpl);
    }

    static MethodHandle partitionFor(Class<?> cls) {
        if (cls == int[].class) {
            return PARTITION_INT;
        } else if (cls == long[].class) {
            return PARTITION_LONG;
        } else if (cls == float[].class) {
            return PARTITION_FLOAT;
        } else if (cls == double[].class) {
            return PARTITION_DOUBLE;
        } else {
            throw new InternalError("not supported: " + cls);
        }
    }

    @SuppressWarnings("unchecked")
    static <A> DualPivotQuicksort.PartitionOperation<A> wrapPartition(MethodHandle impl, DualPivotQuicksort.PartitionOperation<A> defaultImpl) {
        if (impl != null) {
            return MethodHandleProxies.asInterfaceInstance(DualPivotQuicksort.PartitionOperation.class, impl);
        } else {
            return defaultImpl;
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
