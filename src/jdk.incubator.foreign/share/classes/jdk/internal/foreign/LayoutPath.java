/*
 *  Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */
package jdk.internal.foreign;

import jdk.incubator.foreign.MemoryLayout;
import jdk.internal.access.JavaLangInvokeAccess;
import jdk.internal.access.SharedSecrets;
import sun.invoke.util.Wrapper;

import jdk.incubator.foreign.GroupLayout;
import jdk.incubator.foreign.SequenceLayout;
import jdk.incubator.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.util.function.UnaryOperator;
import java.util.stream.LongStream;

/**
 * This class provide support for constructing layout paths; that is, starting from a root path (see {@link #rootPath(MemoryLayout)},
 * a path can be constructed by selecting layout elements using the selector methods provided by this class
 * (see {@link #sequenceElement()}, {@link #sequenceElement(long)}, {@link #sequenceElement(long, long)}, {@link #groupElement(String)}).
 * Once a path has been fully constructed, clients can ask for the offset associated with the layout element selected
 * by the path (see {@link #offset}), or obtain a memory access var handle to access the selected layout element
 * given an address pointing to a segment associated with the root layout (see {@link #dereferenceHandle(Class)}).
 */
public class LayoutPath {

    private static JavaLangInvokeAccess JLI = SharedSecrets.getJavaLangInvokeAccess();

    private final MemoryLayout layout;
    private final long offset;
    private final LayoutPath enclosing;
    private final long[] strides;

    private LayoutPath(MemoryLayout layout, long offset, long[] strides, LayoutPath enclosing) {
        this.layout = layout;
        this.offset = offset;
        this.strides = strides;
        this.enclosing = enclosing;
    }

    // Layout path selector methods

    public LayoutPath sequenceElement() {
        check(SequenceLayout.class, "attempting to select a sequence element from a non-sequence layout");
        SequenceLayout seq = (SequenceLayout)layout;
        MemoryLayout elem = seq.elementLayout();
        return LayoutPath.nestedPath(elem, offset, addStride(elem.bitSize()), this);
    }

    public LayoutPath sequenceElement(long start, long step) {
        check(SequenceLayout.class, "attempting to select a sequence element from a non-sequence layout");
        SequenceLayout seq = (SequenceLayout)layout;
        checkSequenceBounds(seq, start);
        MemoryLayout elem = seq.elementLayout();
        long elemSize = elem.bitSize();
        return LayoutPath.nestedPath(elem, offset + (start * elemSize), addStride(elemSize * step), this);
    }

    public LayoutPath sequenceElement(long index) {
        check(SequenceLayout.class, "attempting to select a sequence element from a non-sequence layout");
        SequenceLayout seq = (SequenceLayout)layout;
        checkSequenceBounds(seq, index);
        long elemOffset = 0;
        if (index > 0) {
            //if index == 0, we do not depend on sequence element size, so skip
            long elemSize = seq.elementLayout().bitSize();
            elemOffset = elemSize * index;
        }
        return LayoutPath.nestedPath(seq.elementLayout(), offset + elemOffset, strides, this);
    }

    public LayoutPath groupElement(String name) {
        check(GroupLayout.class, "attempting to select a group element from a non-group layout");
        GroupLayout g = (GroupLayout)layout;
        long offset = 0;
        MemoryLayout elem = null;
        for (int i = 0; i < g.memberLayouts().size(); i++) {
            MemoryLayout l = g.memberLayouts().get(i);
            if (l.name().isPresent() &&
                l.name().get().equals(name)) {
                elem = l;
                break;
            } else {
                offset += l.bitSize();
            }
        }
        if (elem == null) {
            throw badLayoutPath("cannot resolve '" + name + "' in layout " + layout);
        }
        return LayoutPath.nestedPath(elem, this.offset + offset, strides, this);
    }

    // Layout path projections

    public long offset() {
        return offset;
    }

    public VarHandle dereferenceHandle(Class<?> carrier) {
        if (!(layout instanceof ValueLayout)) {
            throw badLayoutPath("layout path does not select a value layout");
        }

        if (!carrier.isPrimitive() || carrier == void.class || carrier == boolean.class // illegal carrier?
                || Wrapper.forPrimitiveType(carrier).bitWidth() != layout.bitSize()) { // carrier has the right size?
            throw new IllegalArgumentException("Invalid carrier: " + carrier + ", for layout " + layout);
        }

        checkAlignment(this);

        return JLI.memoryAddressViewVarHandle(
                carrier,
                layout.byteAlignment() - 1, //mask
                ((ValueLayout) layout).order(),
                Utils.bitsToBytesOrThrow(offset, IllegalStateException::new),
                LongStream.of(strides).map(s -> Utils.bitsToBytesOrThrow(s, IllegalStateException::new)).toArray());
    }

    // Layout path construction

    public static LayoutPath rootPath(MemoryLayout layout) {
        return new LayoutPath(layout, 0L, EMPTY_STRIDES, null);
    }

    private static LayoutPath nestedPath(MemoryLayout layout, long offset, long[] strides, LayoutPath encl) {
        return new LayoutPath(layout, offset, strides, encl);
    }

    // Helper methods

    private void check(Class<?> layoutClass, String msg) {
        if (!layoutClass.isAssignableFrom(layout.getClass())) {
            throw badLayoutPath(msg);
        }
    }

    private void checkSequenceBounds(SequenceLayout seq, long index) {
        if (seq.elementCount().isPresent() && index >= seq.elementCount().getAsLong()) {
            throw badLayoutPath(String.format("Sequence index out of bound; found: %d, size: %d", index, seq.elementCount().getAsLong()));
        }
    }

    private static IllegalArgumentException badLayoutPath(String cause) {
        return new IllegalArgumentException("Bad layout path: " + cause);
    }

    private static void checkAlignment(LayoutPath path) {
        MemoryLayout layout = path.layout;
        long alignment = layout.bitAlignment();
        if (path.offset % alignment != 0) {
            throw new UnsupportedOperationException("Invalid alignment requirements for layout " + layout);
        }
        for (long stride : path.strides) {
            if (stride % alignment != 0) {
                throw new UnsupportedOperationException("Alignment requirements for layout " + layout + " do not match stride " + stride);
            }
        }
        LayoutPath encl = path.enclosing;
        if (encl != null) {
            if (encl.layout.bitAlignment() < alignment) {
                throw new UnsupportedOperationException("Alignment requirements for layout " + layout + " do not match those for enclosing layout " + encl.layout);
            }
            checkAlignment(encl);
        }
    }

    private long[] addStride(long stride) {
        long[] newStrides = new long[strides.length + 1];
        System.arraycopy(strides, 0, newStrides, 0, strides.length);
        newStrides[strides.length] = stride;
        return newStrides;
    }

    private static long[] EMPTY_STRIDES = new long[0];

    /**
     * This class provides an immutable implementation for the {@code PathElement} interface. A path element implementation
     * is simply a pointer to one of the selector methods provided by the {@code LayoutPath} class.
     */
    public static class PathElementImpl implements MemoryLayout.PathElement, UnaryOperator<LayoutPath> {

        final UnaryOperator<LayoutPath> pathOp;

        public PathElementImpl(UnaryOperator<LayoutPath> pathOp) {
            this.pathOp = pathOp;
        }

        @Override
        public LayoutPath apply(LayoutPath layoutPath) {
            return pathOp.apply(layoutPath);
        }
    }
}
