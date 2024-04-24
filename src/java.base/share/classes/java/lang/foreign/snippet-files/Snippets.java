/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.foreign.snippets;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.lang.foreign.MemoryLayout.sequenceLayout;
import static java.lang.foreign.MemoryLayout.structLayout;
import static java.lang.foreign.SymbolLookup.libraryLookup;
import static java.lang.foreign.SymbolLookup.loaderLookup;
import static java.lang.foreign.ValueLayout.*;
import static java.nio.ByteOrder.BIG_ENDIAN;

/**
 * Snippets for the java.lang.foreign documentation.
 */
class Snippets {

    /**
     * Creates a new snippet.
     */
    public Snippets() {
    }

    static class ArenaSnippets {

        void globalArena() {
            // @start region="global-allocation":
            MemorySegment segment = Arena.global().allocate(100, 1); // @highlight regex='global()'
            // ...
            // segment is never deallocated!
            // @end
        }

        void autoArena() {
            // @start region="auto-allocation":
            MemorySegment segment = Arena.ofAuto().allocate(100, 1); // @highlight regex='ofAuto()'
            // ...
            segment = null; // the segment region becomes available for deallocation after this point
            // @end
        }

        void confinedArena() {
            // @start region="confined-allocation":
            MemorySegment segment = null;
            try (Arena arena = Arena.ofConfined()) { // @highlight regex='ofConfined()'
                segment = arena.allocate(100);
                // ...
            } // segment region deallocated here
            segment.get(ValueLayout.JAVA_BYTE, 0); // throws IllegalStateException
            // @end
        }

        static
        // @start region="slicing-arena":
        class SlicingArena implements Arena {
            final Arena arena = Arena.ofConfined();
            final SegmentAllocator slicingAllocator;

            SlicingArena(long size) {
                slicingAllocator = SegmentAllocator.slicingAllocator(arena.allocate(size));
            }

            public MemorySegment allocate(long byteSize, long byteAlignment) {
                return slicingAllocator.allocate(byteSize, byteAlignment);
            }

            public MemorySegment.Scope scope() {
                return arena.scope();
            }

            public void close() {
                arena.close();
            }

        }
        // @end

        public static void main(String[] args) {
            // @start region="slicing-arena-main":
            try (Arena slicingArena = new SlicingArena(1000)) {
                for (int i = 0; i < 10; i++) {
                    MemorySegment s = slicingArena.allocateFrom(JAVA_INT, 1, 2, 3, 4, 5);
                    // ...
                }
            } // all memory allocated is released here
            // @end
        }

        void arenaOverlap() {
            try (var arena = Arena.ofConfined()) {
                var S1 = arena.allocate(16L);
                var S2 = arena.allocate(16L);

                if (
                    // @start region="arena-overlap":
                        S1.asOverlappingSlice(S2).isEmpty() == true
                    // @end
                ) {}

            }
        }
    }

    static class AddressLayoutSnippets {
        void withTargetLayout() {
            AddressLayout addressLayout = ADDRESS;
            AddressLayout unboundedLayout = addressLayout.withTargetLayout(
                    sequenceLayout(Long.MAX_VALUE, ValueLayout.JAVA_BYTE));
        }
    }

    static class FunctionDescriptionSnippets {
    }

    static class GroupLayoutSnippets {
    }

    static class LinkerSnippets {

        void downcall() throws Throwable {
            Linker linker = Linker.nativeLinker();
            MethodHandle strlen = linker.downcallHandle(
                    linker.defaultLookup().findOrThrow("strlen"),
                    FunctionDescriptor.of(JAVA_LONG, ADDRESS)
            );

            try (Arena arena = Arena.ofConfined()) {
                MemorySegment str = arena.allocateFrom("Hello");
                long len = (long) strlen.invokeExact(str);  // 5
            }

        }

        void qsort() throws Throwable {
            Linker linker = Linker.nativeLinker();
            MethodHandle qsort = linker.downcallHandle(
                    linker.defaultLookup().findOrThrow("qsort"),
                    FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_LONG, ADDRESS)
            );

            class Qsort {
                static int qsortCompare(MemorySegment elem1, MemorySegment elem2) {
                    return Integer.compare(elem1.get(JAVA_INT, 0), elem2.get(JAVA_INT, 0));
                }
            }

            FunctionDescriptor comparDesc = FunctionDescriptor.of(JAVA_INT,
                    ADDRESS.withTargetLayout(JAVA_INT),
                    ADDRESS.withTargetLayout(JAVA_INT));
            MethodHandle comparHandle = MethodHandles.lookup()
                    .findStatic(Qsort.class, "qsortCompare",
                            comparDesc.toMethodType());


            try (Arena arena = Arena.ofConfined()) {
                MemorySegment compareFunc = linker.upcallStub(comparHandle, comparDesc, arena);
                MemorySegment array = arena.allocateFrom(JAVA_INT, 0, 9, 3, 4, 6, 5, 1, 8, 2, 7);
                qsort.invokeExact(array, 10L, 4L, compareFunc);
                int[] sorted = array.toArray(JAVA_INT); // [ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 ]

            }
        }

        void returnPointer() throws Throwable {
            Linker linker = Linker.nativeLinker();

            MethodHandle malloc = linker.downcallHandle(
                    linker.defaultLookup().findOrThrow("malloc"),
                    FunctionDescriptor.of(ADDRESS, JAVA_LONG)
            );

            MethodHandle free = linker.downcallHandle(
                    linker.defaultLookup().findOrThrow("free"),
                    FunctionDescriptor.ofVoid(ADDRESS)
            );

            MemorySegment segment = (MemorySegment) malloc.invokeExact(100);

            class AllocateMemory {

                MemorySegment allocateMemory(long byteSize, Arena arena) throws Throwable {
                    MemorySegment segment = (MemorySegment) malloc.invokeExact(byteSize); // size = 0, scope = always alive
                    return segment.reinterpret(byteSize, arena, s -> {
                        try {
                            free.invokeExact(s);
                        } catch (Throwable e) {
                            throw new RuntimeException(e);
                        }
                    });  // size = byteSize, scope = arena.scope()
                }

            }


            class AllocateMemory2 {

                MemorySegment allocateMemory(long byteSize, Arena arena) {
                    MemorySegment segment = trySupplier(() -> (MemorySegment) malloc.invokeExact(byteSize));   // size = 0, scope = always alive
                    return segment.reinterpret(byteSize, arena, s -> trySupplier(() -> free.invokeExact(s)));  // size = byteSize, scope = arena.scope()
                }

                @FunctionalInterface
                interface ThrowingSupplier<T> {
                    T get() throws Throwable;

                }

                <T> T trySupplier(ThrowingSupplier<? extends T> supplier) {
                    try {
                        return supplier.get();
                    } catch (Throwable t) {
                        throw new RuntimeException(t);
                    }
                }


            }


            class AllocateMemory3 {

                MemorySegment allocateMemory(long byteSize, Arena arena) throws Throwable {
                    MemorySegment segment = (MemorySegment) malloc.invokeExact(byteSize); // size = 0, scope = always alive
                    return segment.reinterpret(byteSize, arena, this::freeMemory);        // size = byteSize, scope = arena.scope()
                }

                void freeMemory(MemorySegment segment) {
                    try {
                        free.invokeExact(segment);
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }
            }

        }

        void variadicFunc() throws Throwable {

            Linker linker = Linker.nativeLinker();
            MethodHandle printf = linker.downcallHandle(
                    linker.defaultLookup().findOrThrow("printf"),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT),
                    Linker.Option.firstVariadicArg(1) // first int is variadic
            );

            try (Arena arena = Arena.ofConfined()) {
                int res = (int) printf.invokeExact(arena.allocateFrom("%d plus %d equals %d"), 2, 2, 4); //prints "2 plus 2 equals 4"
            }

        }

        void downcallHandle() {
            Linker linker = Linker.nativeLinker();
            FunctionDescriptor function = null;
            MemorySegment symbol = null;

            linker.downcallHandle(function).bindTo(symbol);

        }

        void captureCallState() throws Throwable {

            MemorySegment targetAddress = null; // ...
            Linker.Option ccs = Linker.Option.captureCallState("errno");
            MethodHandle handle = Linker.nativeLinker().downcallHandle(targetAddress, FunctionDescriptor.ofVoid(), ccs);

            StructLayout capturedStateLayout = Linker.Option.captureStateLayout();
            VarHandle errnoHandle = capturedStateLayout.varHandle(PathElement.groupElement("errno"));
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment capturedState = arena.allocate(capturedStateLayout);
                handle.invoke(capturedState);
                int errno = (int) errnoHandle.get(capturedState, 0L);
                // use errno
            }
        }

        void captureStateLayout() {
            String capturedNames = Linker.Option.captureStateLayout().memberLayouts().stream()
                    .map(MemoryLayout::name)
                    .flatMap(Optional::stream)
                    .map(Objects::toString)
                    .collect(Collectors.joining(", "));
        }


    }

    static class MemoryLayoutSnippets {

        void header() throws Throwable {
            SequenceLayout taggedValues = sequenceLayout(5,
                    structLayout(
                            ValueLayout.JAVA_BYTE.withName("kind"),
                            MemoryLayout.paddingLayout(24),
                            ValueLayout.JAVA_INT.withName("value")
                    )
            ).withName("TaggedValues");

            long valueOffset = taggedValues.byteOffset(PathElement.sequenceElement(0),
                    PathElement.groupElement("value")); // yields 4

            MemoryLayout value = taggedValues.select(PathElement.sequenceElement(),
                    PathElement.groupElement("value"));

            VarHandle valueHandle = taggedValues.varHandle(PathElement.sequenceElement(),
                    PathElement.groupElement("value"));

            MethodHandle offsetHandle = taggedValues.byteOffsetHandle(PathElement.sequenceElement(),
                    PathElement.groupElement("kind"));
            long offset1 = (long) offsetHandle.invokeExact(0L, 1L); // 8
            long offset2 = (long) offsetHandle.invokeExact(0L, 2L); // 16
        }

        void sliceHandle() {
            MemorySegment segment = null;
            long offset = 0;
            MemoryLayout layout = null;

            segment.asSlice(offset, layout.byteSize());
        }

        void sequenceLayout0() {
            MemoryLayout elementLayout = JAVA_INT;

            sequenceLayout(Long.MAX_VALUE / elementLayout.byteSize(), elementLayout);
        }

        void structLayout0() {
            MemoryLayout elementLayout = JAVA_INT;

            structLayout(JAVA_SHORT, JAVA_INT);
            structLayout(JAVA_SHORT, MemoryLayout.paddingLayout(16), JAVA_INT);
            structLayout(JAVA_SHORT, JAVA_INT.withByteAlignment(2));
        }

    }

    static class MemorySegmentSnippets {
        void header() throws NoSuchMethodException, IllegalAccessException {

            {
                MemorySegment segment = null; // ...
                int value = segment.get(ValueLayout.JAVA_INT, 0);
            }

            {
                MemorySegment segment = null; // ...

                int value = segment.get(ValueLayout.JAVA_INT.withOrder(BIG_ENDIAN), 0);
            }

            {
                MemorySegment segment = null; // ...

                VarHandle intHandle = ValueLayout.JAVA_INT.varHandle();
                MethodHandle multiplyExact = MethodHandles.lookup()
                        .findStatic(Math.class, "multiplyExact",
                                MethodType.methodType(long.class, long.class, long.class));
                intHandle = MethodHandles.filterCoordinates(intHandle, 1,
                        MethodHandles.insertArguments(multiplyExact, 0, ValueLayout.JAVA_INT.byteSize()));
                int value = (int) intHandle.get(segment, 3L); // get int element at offset 3 * 4 = 12
            }

            {
                MemorySegment segment = null; // ...

                MemoryLayout segmentLayout = MemoryLayout.structLayout(
                    ValueLayout.JAVA_INT.withName("size"),
                    MemoryLayout.sequenceLayout(4, ValueLayout.JAVA_INT).withName("data") // array of 4 elements
                );
                VarHandle intHandle = segmentLayout.varHandle(MemoryLayout.PathElement.groupElement("data"),
                                                              MemoryLayout.PathElement.sequenceElement());
                int value = (int) intHandle.get(segment, 0L, 3L); // get int element at offset 0 + offsetof(data) + 3 * 4 = 12
            }

            {
                Arena arena = Arena.ofConfined();
                MemorySegment segment = arena.allocate(100);
                MemorySegment slice = segment.asSlice(50, 10);
                slice.get(ValueLayout.JAVA_INT, 20); // Out of bounds!
                arena.close();
                slice.get(ValueLayout.JAVA_INT, 0); // Already closed!
            }


            {
                try (Arena arena = Arena.ofShared()) {
                    SequenceLayout SEQUENCE_LAYOUT = MemoryLayout.sequenceLayout(1024, ValueLayout.JAVA_INT);
                    MemorySegment segment = arena.allocate(SEQUENCE_LAYOUT);
                    int sum = segment.elements(ValueLayout.JAVA_INT).parallel()
                            .mapToInt(s -> s.get(ValueLayout.JAVA_INT, 0))
                            .sum();
                }
            }

            {
                MemorySegment byteSegment = MemorySegment.ofArray(new byte[10]);
                byteSegment.get(ValueLayout.JAVA_INT, 0); // fails: layout alignment is 4, segment max alignment is 1
            }

            {
                MemorySegment longSegment = MemorySegment.ofArray(new long[10]);
                longSegment.get(ValueLayout.JAVA_INT, 0); // ok: layout alignment is 4, segment max alignment is 8
            }

            {
                MemorySegment byteSegment = MemorySegment.ofArray(new byte[10]);
                byteSegment.get(ValueLayout.JAVA_INT_UNALIGNED, 0); // ok: layout alignment is 1, segment max alignment is 1
            }

            {
                MemorySegment segment = null;
                long offset = 42;

                MemorySegment z = segment.get(ValueLayout.ADDRESS, offset);   // size = 0
                MemorySegment ptr = z.reinterpret(16);                // size = 16
                int x = ptr.getAtIndex(ValueLayout.JAVA_INT, 3);        // ok
            }

            {
                MemorySegment segment = null;
                long offset = 42;

                MemorySegment ptr = null;
                try (Arena arena = Arena.ofConfined()) {
                    MemorySegment z = segment.get(ValueLayout.ADDRESS, offset);   // size = 0, scope = always alive
                    ptr = z.reinterpret(16, arena, null);          // size = 4, scope = arena.scope()
                    int x = ptr.getAtIndex(ValueLayout.JAVA_INT, 3);        // ok
                }
                int x = ptr.getAtIndex(ValueLayout.JAVA_INT, 3);            // throws IllegalStateException
            }

            {
                MemorySegment segment = null;
                long offset = 42;

                AddressLayout intArrPtrLayout = ValueLayout.ADDRESS.withTargetLayout(
                        MemoryLayout.sequenceLayout(4, ValueLayout.JAVA_INT)); // layout for int (*ptr)[4]
                MemorySegment ptr = segment.get(intArrPtrLayout, offset);                  // size = 16
                int x = ptr.getAtIndex(ValueLayout.JAVA_INT, 3);                     // ok
            }


        }

        boolean isAligned(MemorySegment segment, long offset, MemoryLayout layout) {
            return ((segment.address() + offset) % layout.byteAlignment()) == 0;
        }

        void elements() {
            MemorySegment segment = null;
            MemoryLayout elementLayout = JAVA_INT;

            StreamSupport.stream(segment.spliterator(elementLayout), false);
        }

        void asSlice() {
            MemorySegment segment = null;
            long offset = 42;
            MemoryLayout layout = JAVA_INT;

            segment.asSlice(offset, layout.byteSize(), 1);

            segment.asSlice(offset, layout.byteSize(), layout.byteAlignment());

            segment.asSlice(offset, segment.byteSize() - offset);

        }

        void reinterpret() {
            MemorySegment segment = null;

            MemorySegment cleanupSegment = MemorySegment.ofAddress(segment.address());

        }

        void segmentOffset() {
            MemorySegment segment = null;
            MemorySegment other = null;

            long offset = other.address() - segment.address();
        }

        void fill() {
            MemorySegment segment = null;
            byte value = 42;

            for (long l = 0; l < segment.byteSize(); l++) {
                segment.set(JAVA_BYTE, l, value);
            }
        }

        void copyFrom() {
            MemorySegment src = null;
            MemorySegment dst = null;

            // MemorySegment.copy(src, 0, this, 0, src.byteSize());
            MemorySegment.copy(src, 0, dst, 0, src.byteSize());
        }

        void copy() {
            MemorySegment srcSegment = null;
            long srcOffset = 42;
            MemorySegment dstSegment = null;
            long dstOffset = 13;
            long bytes = 3;

            MemorySegment.copy(srcSegment, ValueLayout.JAVA_BYTE, srcOffset, dstSegment, ValueLayout.JAVA_BYTE, dstOffset, bytes);
        }


    }

    static class PackageInfoSnippets {

        void header() throws Throwable {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment segment = arena.allocate(10 * 4);
                for (int i = 0; i < 10; i++) {
                    segment.setAtIndex(ValueLayout.JAVA_INT, i, i);
                }
            }

            Linker linker = Linker.nativeLinker();
            SymbolLookup stdlib = linker.defaultLookup();
            MethodHandle strlen = linker.downcallHandle(
                    stdlib.findOrThrow("strlen"),
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
            );

            try (Arena arena = Arena.ofConfined()) {
                MemorySegment cString = arena.allocateFrom("Hello");
                long len = (long) strlen.invokeExact(cString); // 5
            }

        }
    }

    static class PaddingLayoutSnippets {
    }

    static class SegmentAllocatorSnippets {
        void prefixAllocator() {
            MemorySegment segment = null; //...
            SegmentAllocator prefixAllocator = (size, align) -> segment.asSlice(0, size);
        }

    }

    static class SequenceLayoutSnippets {
        void header() {
            MemoryLayout.sequenceLayout(3, ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN));

            MemoryLayout.structLayout(
                    ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN),
                    ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN),
                    ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN));

        }

        void reshape() {
            var seq = MemoryLayout.sequenceLayout(4, MemoryLayout.sequenceLayout(3, ValueLayout.JAVA_INT));
            var reshapeSeq = MemoryLayout.sequenceLayout(2, MemoryLayout.sequenceLayout(6, ValueLayout.JAVA_INT));

            var reshapeSeqImplicit1 = seq.reshape(-1, 6);
            var reshapeSeqImplicit2 = seq.reshape(2, -1);

        }

        void flatten() {
            var seq = MemoryLayout.sequenceLayout(4, MemoryLayout.sequenceLayout(3, ValueLayout.JAVA_INT));
            var flattenedSeq = MemoryLayout.sequenceLayout(12, ValueLayout.JAVA_INT);
        }

    }

    static class StructLayoutSnippets {
    }

    static class SymbolLookupSnippets {

        void header() {
            try (Arena arena = Arena.ofConfined()) {
                SymbolLookup libGL = libraryLookup("libGL.so", arena); // libGL.so loaded here
                MemorySegment glGetString = libGL.findOrThrow("glGetString");
                // ...
            } //  libGL.so unloaded here

            System.loadLibrary("GL"); // libGL.so loaded here
            // ...
            SymbolLookup libGL = loaderLookup();
            MemorySegment glGetString = libGL.findOrThrow("glGetString");


            Arena arena = Arena.ofAuto();


            libraryLookup("libGL.so", arena).find("glGetString").isPresent(); // true
            loaderLookup().find("glGetString").isPresent(); // false

            libraryLookup("libGL.so", arena).find("glGetString").isPresent(); // true
            loaderLookup().find("glGetString").isPresent(); // false

            Linker nativeLinker = Linker.nativeLinker();
            SymbolLookup stdlib = nativeLinker.defaultLookup();
            MemorySegment malloc = stdlib.findOrThrow("malloc");
        }

    }

    static class UnionLayoutSnippets {
    }

    static class ValueLayoutSnippets {

        void statics() {
            ADDRESS.withByteAlignment(1);
            JAVA_CHAR.withByteAlignment(1);
            JAVA_SHORT.withByteAlignment(1);
            JAVA_INT.withByteAlignment(1);
            JAVA_LONG.withByteAlignment(1);
            JAVA_FLOAT.withByteAlignment(1);
            JAVA_DOUBLE.withByteAlignment(1);
        }

    }

}
