/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.foreign.abi;

import jdk.incubator.foreign.Addressable;
import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.GroupLayout;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.NativeSymbol;
import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.foreign.SegmentAllocator;
import jdk.incubator.foreign.SequenceLayout;
import jdk.incubator.foreign.VaList;
import jdk.incubator.foreign.ValueLayout;
import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.JavaLangInvokeAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.foreign.Scoped;
import jdk.internal.foreign.CABI;
import jdk.internal.foreign.MemoryAddressImpl;
import jdk.internal.foreign.ResourceScopeImpl;
import jdk.internal.foreign.Utils;
import jdk.internal.foreign.abi.aarch64.linux.LinuxAArch64Linker;
import jdk.internal.foreign.abi.aarch64.macos.MacOsAArch64Linker;
import jdk.internal.foreign.abi.x64.sysv.SysVx64Linker;
import jdk.internal.foreign.abi.x64.windows.Windowsx64Linker;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.ref.Reference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.invoke.MethodHandles.collectArguments;
import static java.lang.invoke.MethodHandles.constant;
import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.dropReturn;
import static java.lang.invoke.MethodHandles.empty;
import static java.lang.invoke.MethodHandles.foldArguments;
import static java.lang.invoke.MethodHandles.identity;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodHandles.permuteArguments;
import static java.lang.invoke.MethodHandles.tryFinally;
import static java.lang.invoke.MethodType.methodType;
import static jdk.incubator.foreign.ValueLayout.ADDRESS;
import static jdk.incubator.foreign.ValueLayout.JAVA_BOOLEAN;
import static jdk.incubator.foreign.ValueLayout.JAVA_BYTE;
import static jdk.incubator.foreign.ValueLayout.JAVA_CHAR;
import static jdk.incubator.foreign.ValueLayout.JAVA_DOUBLE;
import static jdk.incubator.foreign.ValueLayout.JAVA_FLOAT;
import static jdk.incubator.foreign.ValueLayout.JAVA_INT;
import static jdk.incubator.foreign.ValueLayout.JAVA_LONG;
import static jdk.incubator.foreign.ValueLayout.JAVA_SHORT;

public class SharedUtils {

    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();
    private static final JavaLangInvokeAccess JLIA = SharedSecrets.getJavaLangInvokeAccess();

    private static final MethodHandle MH_ALLOC_BUFFER;
    private static final MethodHandle MH_BASEADDRESS;
    private static final MethodHandle MH_BUFFER_COPY;
    private static final MethodHandle MH_MAKE_CONTEXT_NO_ALLOCATOR;
    private static final MethodHandle MH_MAKE_CONTEXT_BOUNDED_ALLOCATOR;
    private static final MethodHandle MH_CLOSE_CONTEXT;
    private static final MethodHandle MH_REACHBILITY_FENCE;
    private static final MethodHandle MH_HANDLE_UNCAUGHT_EXCEPTION;
    private static final MethodHandle ACQUIRE_MH;
    private static final MethodHandle RELEASE_MH;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MH_ALLOC_BUFFER = lookup.findVirtual(SegmentAllocator.class, "allocate",
                    methodType(MemorySegment.class, MemoryLayout.class));
            MH_BASEADDRESS = lookup.findVirtual(MemorySegment.class, "address",
                    methodType(MemoryAddress.class));
            MH_BUFFER_COPY = lookup.findStatic(SharedUtils.class, "bufferCopy",
                    methodType(MemoryAddress.class, MemoryAddress.class, MemorySegment.class));
            MH_MAKE_CONTEXT_NO_ALLOCATOR = lookup.findStatic(Binding.Context.class, "ofScope",
                    methodType(Binding.Context.class));
            MH_MAKE_CONTEXT_BOUNDED_ALLOCATOR = lookup.findStatic(Binding.Context.class, "ofBoundedAllocator",
                    methodType(Binding.Context.class, long.class));
            MH_CLOSE_CONTEXT = lookup.findVirtual(Binding.Context.class, "close",
                    methodType(void.class));
            MH_REACHBILITY_FENCE = lookup.findStatic(Reference.class, "reachabilityFence",
                    methodType(void.class, Object.class));
            MH_HANDLE_UNCAUGHT_EXCEPTION = lookup.findStatic(SharedUtils.class, "handleUncaughtException",
                    methodType(void.class, Throwable.class));
            ACQUIRE_MH = MethodHandles.lookup().findStatic(SharedUtils.class, "acquire",
                    MethodType.methodType(void.class, Scoped[].class));
            RELEASE_MH = MethodHandles.lookup().findStatic(SharedUtils.class, "release",
                    MethodType.methodType(void.class, Scoped[].class));
        } catch (ReflectiveOperationException e) {
            throw new BootstrapMethodError(e);
        }
    }

    // this allocator should be used when no allocation is expected
    public static final SegmentAllocator THROWING_ALLOCATOR = (size, align) -> {
        throw new IllegalStateException("Cannot get here");
    };

    /**
     * Align the specified type from a given address
     * @return The address the data should be at based on alignment requirement
     */
    public static long align(MemoryLayout t, boolean isVar, long addr) {
        return alignUp(addr, alignment(t, isVar));
    }

    public static long alignUp(long addr, long alignment) {
        return ((addr - 1) | (alignment - 1)) + 1;
    }

    /**
     * The alignment requirement for a given type
     * @param isVar indicate if the type is a standalone variable. This change how
     * array is aligned. for example.
     */
    public static long alignment(MemoryLayout t, boolean isVar) {
        if (t instanceof ValueLayout) {
            return alignmentOfScalar((ValueLayout) t);
        } else if (t instanceof SequenceLayout) {
            // when array is used alone
            return alignmentOfArray((SequenceLayout) t, isVar);
        } else if (t instanceof GroupLayout) {
            return alignmentOfContainer((GroupLayout) t);
        } else if (t.isPadding()) {
            return 1;
        } else {
            throw new IllegalArgumentException("Invalid type: " + t);
        }
    }

    private static long alignmentOfScalar(ValueLayout st) {
        return st.byteSize();
    }

    private static long alignmentOfArray(SequenceLayout ar, boolean isVar) {
        if (ar.elementCount().orElseThrow() == 0) {
            // VLA or incomplete
            return 16;
        } else if ((ar.byteSize()) >= 16 && isVar) {
            return 16;
        } else {
            // align as element type
            MemoryLayout elementType = ar.elementLayout();
            return alignment(elementType, false);
        }
    }

    private static long alignmentOfContainer(GroupLayout ct) {
        // Most strict member
        return ct.memberLayouts().stream().mapToLong(t -> alignment(t, false)).max().orElse(1);
    }

    /**
     * Takes a MethodHandle that takes an input buffer as a first argument (a MemoryAddress), and returns nothing,
     * and adapts it to return a MemorySegment, by allocating a MemorySegment for the input
     * buffer, calling the target MethodHandle, and then returning the allocated MemorySegment.
     *
     * This allows viewing a MethodHandle that makes use of in memory return (IMR) as a MethodHandle that just returns
     * a MemorySegment without requiring a pre-allocated buffer as an explicit input.
     *
     * @param handle the target handle to adapt
     * @param cDesc the function descriptor of the native function (with actual return layout)
     * @return the adapted handle
     */
    public static MethodHandle adaptDowncallForIMR(MethodHandle handle, FunctionDescriptor cDesc) {
        if (handle.type().returnType() != void.class)
            throw new IllegalArgumentException("return expected to be void for in memory returns: " + handle.type());
        if (handle.type().parameterType(2) != MemoryAddress.class)
            throw new IllegalArgumentException("MemoryAddress expected as third param: " + handle.type());
        if (cDesc.returnLayout().isEmpty())
            throw new IllegalArgumentException("Return layout needed: " + cDesc);

        MethodHandle ret = identity(MemorySegment.class); // (MemorySegment) MemorySegment
        handle = collectArguments(ret, 1, handle); // (MemorySegment, Addressable, SegmentAllocator, MemoryAddress, ...) MemorySegment
        handle = collectArguments(handle, 3, MH_BASEADDRESS); // (MemorySegment, Addressable, SegmentAllocator, MemorySegment, ...) MemorySegment
        handle = mergeArguments(handle, 0, 3);  // (MemorySegment, Addressable, SegmentAllocator, ...) MemorySegment
        handle = collectArguments(handle, 0, insertArguments(MH_ALLOC_BUFFER, 1, cDesc.returnLayout().get())); // (SegmentAllocator, Addressable, SegmentAllocator, ...) MemoryAddress
        handle = mergeArguments(handle, 0, 2);  // (SegmentAllocator, Addressable, ...) MemoryAddress
        handle = swapArguments(handle, 0, 1); // (Addressable, SegmentAllocator, ...) MemoryAddress
        return handle;
    }

    /**
     * Takes a MethodHandle that returns a MemorySegment, and adapts it to take an input buffer as a first argument
     * (a MemoryAddress), and upon invocation, copies the contents of the returned MemorySegment into the input buffer
     * passed as the first argument.
     *
     * @param target the target handle to adapt
     * @return the adapted handle
     */
    public static MethodHandle adaptUpcallForIMR(MethodHandle target, boolean dropReturn) {
        if (target.type().returnType() != MemorySegment.class)
            throw new IllegalArgumentException("Must return MemorySegment for IMR");

        target = collectArguments(MH_BUFFER_COPY, 1, target); // (MemoryAddress, ...) MemoryAddress

        if (dropReturn) { // no handling for return value, need to drop it
            target = dropReturn(target);
        }

        return target;
    }

    private static MemoryAddress bufferCopy(MemoryAddress dest, MemorySegment buffer) {
        MemoryAddressImpl.ofLongUnchecked(dest.toRawLongValue(), buffer.byteSize()).copyFrom(buffer);
        return dest;
    }

    public static Class<?> primitiveCarrierForSize(long size, boolean useFloat) {
        if (useFloat) {
            if (size == 4) {
                return float.class;
            } else if (size == 8) {
                return double.class;
            }
        } else {
            if (size == 1) {
                return byte.class;
            } else if (size == 2) {
                return short.class;
            } else if (size <= 4) {
                return int.class;
            } else if (size <= 8) {
                return long.class;
            }
        }

        throw new IllegalArgumentException("No type for size: " + size + " isFloat=" + useFloat);
    }

    public static CLinker getSystemLinker() {
        return switch (CABI.current()) {
            case Win64 -> Windowsx64Linker.getInstance();
            case SysV -> SysVx64Linker.getInstance();
            case LinuxAArch64 -> LinuxAArch64Linker.getInstance();
            case MacOsAArch64 -> MacOsAArch64Linker.getInstance();
        };
    }

    public static String toJavaStringInternal(MemorySegment segment, long start) {
        int len = strlen(segment, start);
        byte[] bytes = new byte[len];
        MemorySegment.copy(segment, JAVA_BYTE, start, bytes, 0, len);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int strlen(MemorySegment segment, long start) {
        // iterate until overflow (String can only hold a byte[], whose length can be expressed as an int)
        for (int offset = 0; offset >= 0; offset++) {
            byte curr = segment.get(JAVA_BYTE, start + offset);
            if (curr == 0) {
                return offset;
            }
        }
        throw new IllegalArgumentException("String too large");
    }

    static long bufferCopySize(CallingSequence callingSequence) {
        // FIXME: > 16 bytes alignment might need extra space since the
        // starting address of the allocator might be un-aligned.
        long size = 0;
        for (int i = 0; i < callingSequence.argumentCount(); i++) {
            List<Binding> bindings = callingSequence.argumentBindings(i);
            for (Binding b : bindings) {
                if (b instanceof Binding.Copy) {
                    Binding.Copy c = (Binding.Copy) b;
                    size = Utils.alignUp(size, c.alignment());
                    size += c.size();
                } else if (b instanceof Binding.Allocate) {
                    Binding.Allocate c = (Binding.Allocate) b;
                    size = Utils.alignUp(size, c.alignment());
                    size += c.size();
                }
            }
        }
        return size;
    }

    static Map<VMStorage, Integer> indexMap(Binding.Move[] moves) {
        return IntStream.range(0, moves.length)
                        .boxed()
                        .collect(Collectors.toMap(i -> moves[i].storage(), i -> i));
    }

    static MethodHandle mergeArguments(MethodHandle mh, int sourceIndex, int destIndex) {
        MethodType oldType = mh.type();
        Class<?> sourceType = oldType.parameterType(sourceIndex);
        Class<?> destType = oldType.parameterType(destIndex);
        if (sourceType != destType) {
            // TODO meet?
            throw new IllegalArgumentException("Parameter types differ: " + sourceType + " != " + destType);
        }
        MethodType newType = oldType.dropParameterTypes(destIndex, destIndex + 1);
        int[] reorder = new int[oldType.parameterCount()];
        assert destIndex > sourceIndex;
        for (int i = 0, index = 0; i < reorder.length; i++) {
            if (i != destIndex) {
                reorder[i] = index++;
            } else {
                reorder[i] = sourceIndex;
            }
        }
        return permuteArguments(mh, newType, reorder);
    }


    static MethodHandle swapArguments(MethodHandle mh, int firstArg, int secondArg) {
        MethodType mtype = mh.type();
        int[] perms = new int[mtype.parameterCount()];
        MethodType swappedType = MethodType.methodType(mtype.returnType());
        for (int i = 0 ; i < perms.length ; i++) {
            int dst = i;
            if (i == firstArg) dst = secondArg;
            if (i == secondArg) dst = firstArg;
            perms[i] = dst;
            swappedType = swappedType.appendParameterTypes(mtype.parameterType(dst));
        }
        return permuteArguments(mh, swappedType, perms);
    }

    private static MethodHandle reachabilityFenceHandle(Class<?> type) {
        return MH_REACHBILITY_FENCE.asType(MethodType.methodType(void.class, type));
    }

    static void handleUncaughtException(Throwable t) {
        if (t != null) {
            t.printStackTrace();
            JLA.exit(1);
        }
    }

    static MethodHandle wrapWithAllocator(MethodHandle specializedHandle,
                                          int allocatorPos, long bufferCopySize,
                                          boolean upcall) {
        // insert try-finally to close the NativeScope used for Binding.Copy
        MethodHandle closer;
        int insertPos;
        if (specializedHandle.type().returnType() == void.class) {
            if (!upcall) {
                closer = empty(methodType(void.class, Throwable.class)); // (Throwable) -> void
            } else {
                closer = MH_HANDLE_UNCAUGHT_EXCEPTION;
            }
            insertPos = 1;
        } else {
            closer = identity(specializedHandle.type().returnType()); // (V) -> V
            if (!upcall) {
                closer = dropArguments(closer, 0, Throwable.class); // (Throwable, V) -> V
            } else {
                closer = collectArguments(closer, 0, MH_HANDLE_UNCAUGHT_EXCEPTION); // (Throwable, V) -> V
            }
            insertPos = 2;
        }

        // downcalls get the leading NativeSymbol/SegmentAllocator param as well
        if (!upcall) {
            closer = collectArguments(closer, insertPos++, reachabilityFenceHandle(NativeSymbol.class));
            closer = dropArguments(closer, insertPos++, SegmentAllocator.class); // (Throwable, V?, NativeSymbol, SegmentAllocator) -> V/void
        }

        closer = collectArguments(closer, insertPos++, MH_CLOSE_CONTEXT); // (Throwable, V?, NativeSymbol?, BindingContext) -> V/void

        MethodHandle contextFactory;

        if (bufferCopySize > 0) {
            contextFactory = MethodHandles.insertArguments(MH_MAKE_CONTEXT_BOUNDED_ALLOCATOR, 0, bufferCopySize);
        } else if (upcall) {
            contextFactory = MH_MAKE_CONTEXT_NO_ALLOCATOR;
        } else {
            // this path is probably never used now, since ProgrammableInvoker never calls this routine with bufferCopySize == 0
            contextFactory = constant(Binding.Context.class, Binding.Context.DUMMY);
        }

        specializedHandle = tryFinally(specializedHandle, closer);
        specializedHandle = collectArguments(specializedHandle, allocatorPos, contextFactory);
        return specializedHandle;
    }

    @ForceInline
    @SuppressWarnings("fallthrough")
    public static void acquire(Scoped[] args) {
        ResourceScope scope4 = null;
        ResourceScope scope3 = null;
        ResourceScope scope2 = null;
        ResourceScope scope1 = null;
        ResourceScope scope0 = null;
        switch (args.length) {
            default:
                // slow path, acquire all remaining addressable parameters in isolation
                for (int i = 5 ; i < args.length ; i++) {
                    acquire(args[i].scope());
                }
            // fast path, acquire only scopes not seen in other parameters
            case 5:
                scope4 = args[4].scope();
                acquire(scope4);
            case 4:
                scope3 = args[3].scope();
                if (scope3 != scope4)
                    acquire(scope3);
            case 3:
                scope2 = args[2].scope();
                if (scope2 != scope3 && scope2 != scope4)
                    acquire(scope2);
            case 2:
                scope1 = args[1].scope();
                if (scope1 != scope2 && scope1 != scope3 && scope1 != scope4)
                    acquire(scope1);
            case 1:
                scope0 = args[0].scope();
                if (scope0 != scope1 && scope0 != scope2 && scope0 != scope3 && scope0 != scope4)
                    acquire(scope0);
            case 0: break;
        }
    }

    @ForceInline
    @SuppressWarnings("fallthrough")
    public static void release(Scoped[] args) {
        ResourceScope scope4 = null;
        ResourceScope scope3 = null;
        ResourceScope scope2 = null;
        ResourceScope scope1 = null;
        ResourceScope scope0 = null;
        switch (args.length) {
            default:
                // slow path, release all remaining addressable parameters in isolation
                for (int i = 5 ; i < args.length ; i++) {
                    release(args[i].scope());
                }
            // fast path, release only scopes not seen in other parameters
            case 5:
                scope4 = args[4].scope();
                release(scope4);
            case 4:
                scope3 = args[3].scope();
                if (scope3 != scope4)
                    release(scope3);
            case 3:
                scope2 = args[2].scope();
                if (scope2 != scope3 && scope2 != scope4)
                    release(scope2);
            case 2:
                scope1 = args[1].scope();
                if (scope1 != scope2 && scope1 != scope3 && scope1 != scope4)
                    release(scope1);
            case 1:
                scope0 = args[0].scope();
                if (scope0 != scope1 && scope0 != scope2 && scope0 != scope3 && scope0 != scope4)
                    release(scope0);
            case 0: break;
        }
    }

    @ForceInline
    private static void acquire(ResourceScope scope) {
        ((ResourceScopeImpl)scope).acquire0();
    }

    @ForceInline
    private static void release(ResourceScope scope) {
        ((ResourceScopeImpl)scope).release0();
    }

    /*
     * This method adds a try/finally block to a downcall method handle, to make sure that all by-reference
     * parameters (including the target address of the native function) are kept alive for the duration of
     * the downcall.
     */
    public static MethodHandle wrapDowncall(MethodHandle downcallHandle, FunctionDescriptor descriptor) {
        boolean hasReturn = descriptor.returnLayout().isPresent();
        MethodHandle tryBlock = downcallHandle;
        MethodHandle cleanup = hasReturn ?
                MethodHandles.identity(downcallHandle.type().returnType()) :
                MethodHandles.empty(MethodType.methodType(void.class));
        int addressableCount = 0;
        List<UnaryOperator<MethodHandle>> adapters = new ArrayList<>();
        for (int i = 0 ; i < downcallHandle.type().parameterCount() ; i++) {
            Class<?> ptype = downcallHandle.type().parameterType(i);
            if (ptype == Addressable.class || ptype == NativeSymbol.class) {
                addressableCount++;
            } else {
                int pos = i;
                adapters.add(mh -> dropArguments(mh, pos, ptype));
            }
        }

        if (addressableCount > 0) {
            cleanup = dropArguments(cleanup, 0, Throwable.class);

            MethodType adapterType = MethodType.methodType(void.class);
            for (int i = 0 ; i < addressableCount ; i++) {
                adapterType = adapterType.appendParameterTypes(i == 0 ? NativeSymbol.class : Addressable.class);
            }

            MethodHandle acquireHandle = ACQUIRE_MH.asCollector(Scoped[].class, addressableCount).asType(adapterType);
            MethodHandle releaseHandle = RELEASE_MH.asCollector(Scoped[].class, addressableCount).asType(adapterType);

            for (UnaryOperator<MethodHandle> adapter : adapters) {
                acquireHandle = adapter.apply(acquireHandle);
                releaseHandle = adapter.apply(releaseHandle);
            }

            tryBlock = foldArguments(tryBlock, acquireHandle);
            cleanup = collectArguments(cleanup, hasReturn ? 2 : 1, releaseHandle);

            return tryFinally(tryBlock, cleanup);
        } else {
            return downcallHandle;
        }
    }

    public static void checkExceptions(MethodHandle target) {
        Class<?>[] exceptions = JLIA.exceptionTypes(target);
        if (exceptions != null && exceptions.length != 0) {
            throw new IllegalArgumentException("Target handle may throw exceptions: " + Arrays.toString(exceptions));
        }
    }

    // lazy init MH_ALLOC and MH_FREE handles
    private static class AllocHolder {

        private static final CLinker SYS_LINKER = getSystemLinker();

        static final MethodHandle MH_MALLOC = SYS_LINKER.downcallHandle(CLinker.systemCLinker().lookup("malloc").get(),
                FunctionDescriptor.of(ADDRESS, JAVA_LONG));

        static final MethodHandle MH_FREE = SYS_LINKER.downcallHandle(CLinker.systemCLinker().lookup("free").get(),
                FunctionDescriptor.ofVoid(ADDRESS));
    }

    public static void checkSymbol(NativeSymbol symbol) {
        checkAddressable(symbol, "Symbol is NULL");
    }

    public static void checkAddress(MemoryAddress address) {
        checkAddressable(address, "Address is NULL");
    }

    private static void checkAddressable(Addressable symbol, String msg) {
        Objects.requireNonNull(symbol);
        if (symbol.address().toRawLongValue() == 0)
            throw new IllegalArgumentException("Symbol is NULL: " + symbol);
    }

    public static MemoryAddress allocateMemoryInternal(long size) {
        try {
            return (MemoryAddress) AllocHolder.MH_MALLOC.invokeExact(size);
        } catch (Throwable th) {
            throw new RuntimeException(th);
        }
    }

    public static void freeMemoryInternal(MemoryAddress addr) {
        try {
            AllocHolder.MH_FREE.invokeExact((Addressable)addr);
        } catch (Throwable th) {
            throw new RuntimeException(th);
        }
    }

    public static VaList newVaList(Consumer<VaList.Builder> actions, ResourceScope scope) {
        return switch (CABI.current()) {
            case Win64 -> Windowsx64Linker.newVaList(actions, scope);
            case SysV -> SysVx64Linker.newVaList(actions, scope);
            case LinuxAArch64 -> LinuxAArch64Linker.newVaList(actions, scope);
            case MacOsAArch64 -> MacOsAArch64Linker.newVaList(actions, scope);
        };
    }

    public static VaList newVaListOfAddress(MemoryAddress ma, ResourceScope scope) {
        return switch (CABI.current()) {
            case Win64 -> Windowsx64Linker.newVaListOfAddress(ma, scope);
            case SysV -> SysVx64Linker.newVaListOfAddress(ma, scope);
            case LinuxAArch64 -> LinuxAArch64Linker.newVaListOfAddress(ma, scope);
            case MacOsAArch64 -> MacOsAArch64Linker.newVaListOfAddress(ma, scope);
        };
    }

    public static VaList emptyVaList() {
        return switch (CABI.current()) {
            case Win64 -> Windowsx64Linker.emptyVaList();
            case SysV -> SysVx64Linker.emptyVaList();
            case LinuxAArch64 -> LinuxAArch64Linker.emptyVaList();
            case MacOsAArch64 -> MacOsAArch64Linker.emptyVaList();
        };
    }

    static void checkType(Class<?> actualType, Class<?> expectedType) {
        if (expectedType != actualType) {
            throw new IllegalArgumentException(
                    String.format("Invalid operand type: %s. %s expected", actualType, expectedType));
        }
    }

    public static boolean isTrivial(FunctionDescriptor cDesc) {
        return false; // FIXME: use system property?
    }

    public static boolean isVarargsIndex(FunctionDescriptor descriptor, int argIndex) {
        int firstPos = descriptor.firstVariadicArgumentIndex();
        return firstPos != -1 && argIndex >= firstPos;
    }

    public static class SimpleVaArg {
        public final Class<?> carrier;
        public final MemoryLayout layout;
        public final Object value;

        public SimpleVaArg(Class<?> carrier, MemoryLayout layout, Object value) {
            this.carrier = carrier;
            this.layout = layout;
            this.value = value;
        }

        public VarHandle varHandle() {
            return layout.varHandle();
        }
    }

    public static non-sealed class EmptyVaList implements VaList, Scoped {

        private final MemoryAddress address;

        public EmptyVaList(MemoryAddress address) {
            this.address = address;
        }

        private static UnsupportedOperationException uoe() {
            return new UnsupportedOperationException("Empty VaList");
        }

        @Override
        public int nextVarg(ValueLayout.OfInt layout) {
            throw uoe();
        }

        @Override
        public long nextVarg(ValueLayout.OfLong layout) {
            throw uoe();
        }

        @Override
        public double nextVarg(ValueLayout.OfDouble layout) {
            throw uoe();
        }

        @Override
        public MemoryAddress nextVarg(ValueLayout.OfAddress layout) {
            throw uoe();
        }

        @Override
        public MemorySegment nextVarg(GroupLayout layout, SegmentAllocator allocator) {
            throw uoe();
        }

        @Override
        public void skip(MemoryLayout... layouts) {
            throw uoe();
        }

        @Override
        public ResourceScope scope() {
            return ResourceScope.globalScope();
        }

        @Override
        public VaList copy() {
            return this;
        }

        @Override
        public MemoryAddress address() {
            return address;
        }
    }

    static void writeOverSized(MemorySegment ptr, Class<?> type, Object o) {
        // use VH_LONG for integers to zero out the whole register in the process
        if (type == long.class) {
            ptr.set(JAVA_LONG, 0, (long) o);
        } else if (type == int.class) {
            ptr.set(JAVA_LONG, 0, (int) o);
        } else if (type == short.class) {
            ptr.set(JAVA_LONG, 0, (short) o);
        } else if (type == char.class) {
            ptr.set(JAVA_LONG, 0, (char) o);
        } else if (type == byte.class) {
            ptr.set(JAVA_LONG, 0, (byte) o);
        } else if (type == float.class) {
            ptr.set(JAVA_FLOAT, 0, (float) o);
        } else if (type == double.class) {
            ptr.set(JAVA_DOUBLE, 0, (double) o);
        } else if (type == boolean.class) {
            ptr.set(JAVA_BOOLEAN, 0, (boolean) o);
        } else {
            throw new IllegalArgumentException("Unsupported carrier: " + type);
        }
    }

    static void write(MemorySegment ptr, Class<?> type, Object o) {
        if (type == long.class) {
            ptr.set(JAVA_LONG, 0, (long) o);
        } else if (type == int.class) {
            ptr.set(JAVA_INT, 0, (int) o);
        } else if (type == short.class) {
            ptr.set(JAVA_SHORT, 0, (short) o);
        } else if (type == char.class) {
            ptr.set(JAVA_CHAR, 0, (char) o);
        } else if (type == byte.class) {
            ptr.set(JAVA_BYTE, 0, (byte) o);
        } else if (type == float.class) {
            ptr.set(JAVA_FLOAT, 0, (float) o);
        } else if (type == double.class) {
            ptr.set(JAVA_DOUBLE, 0, (double) o);
        } else if (type == boolean.class) {
            ptr.set(JAVA_BOOLEAN, 0, (boolean) o);
        } else {
            throw new IllegalArgumentException("Unsupported carrier: " + type);
        }
    }

    static Object read(MemorySegment ptr, Class<?> type) {
        if (type == long.class) {
            return ptr.get(JAVA_LONG, 0);
        } else if (type == int.class) {
            return ptr.get(JAVA_INT, 0);
        } else if (type == short.class) {
            return ptr.get(JAVA_SHORT, 0);
        } else if (type == char.class) {
            return ptr.get(JAVA_CHAR, 0);
        } else if (type == byte.class) {
            return ptr.get(JAVA_BYTE, 0);
        } else if (type == float.class) {
            return ptr.get(JAVA_FLOAT, 0);
        } else if (type == double.class) {
            return ptr.get(JAVA_DOUBLE, 0);
        } else if (type == boolean.class) {
            return ptr.get(JAVA_BOOLEAN, 0);
        } else {
            throw new IllegalArgumentException("Unsupported carrier: " + type);
        }
    }

    public static MethodType inferMethodType(FunctionDescriptor descriptor, boolean upcall) {
        MethodType type = MethodType.methodType(descriptor.returnLayout().isPresent() ?
                carrierFor(descriptor.returnLayout().get(), upcall) : void.class);
        for (MemoryLayout argLayout : descriptor.argumentLayouts()) {
            type = type.appendParameterTypes(carrierFor(argLayout, !upcall));
        }
        return type;
    }

    static Class<?> carrierFor(MemoryLayout layout, boolean forArg) {
        if (layout instanceof ValueLayout valueLayout) {
            return (forArg && valueLayout.carrier().equals(MemoryAddress.class)) ?
                    Addressable.class : valueLayout.carrier();
        } else if (layout instanceof GroupLayout) {
            return MemorySegment.class;
        } else {
            throw new IllegalArgumentException("Unsupported layout: " + layout);
        }
    }
}
