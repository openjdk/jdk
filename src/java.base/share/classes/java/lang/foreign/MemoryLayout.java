/*
 *  Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.foreign;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.internal.foreign.LayoutPath;
import jdk.internal.foreign.LayoutPath.PathElementImpl.PathKind;
import jdk.internal.foreign.Utils;
import jdk.internal.javac.PreviewFeature;

/**
 * A memory layout can be used to describe the contents of a memory segment.
 * There are two leaves in the layout hierarchy, <em>value layouts</em>, which are used to represent values of given size and kind (see
 * {@link ValueLayout}) and <em>padding layouts</em> which are used, as the name suggests, to represent a portion of a memory
 * segment whose contents should be ignored, and which are primarily present for alignment reasons (see {@link MemoryLayout#paddingLayout(long)}).
 * Some common value layout constants are defined in the {@link ValueLayout} class.
 * <p>
 * More complex layouts can be derived from simpler ones: a <em>sequence layout</em> denotes a repetition of one or more
 * element layout (see {@link SequenceLayout}); a <em>group layout</em> denotes an aggregation of (typically) heterogeneous
 * member layouts (see {@link GroupLayout}).
 * <p>
 * Layouts can be optionally associated with a <em>name</em>. A layout name can be referred to when
 * constructing <a href="MemoryLayout.html#layout-paths"><em>layout paths</em></a>.
 * <p>
 * Consider the following struct declaration in C:
 *
 * {@snippet lang=c :
 * typedef struct {
 *     char kind;
 *     int value;
 * } TaggedValues[5];
 * }
 *
 * The above declaration can be modelled using a layout object, as follows:
 *
 * {@snippet lang=java :
 * SequenceLayout taggedValues = MemoryLayout.sequenceLayout(5,
 *     MemoryLayout.structLayout(
 *         ValueLayout.JAVA_BYTE.withName("kind"),
 *         MemoryLayout.paddingLayout(24),
 *         ValueLayout.JAVA_INT.withName("value")
 *     )
 * ).withName("TaggedValues");
 * }
 *
 * <h2><a id = "layout-align">Size, alignment and byte order</a></h2>
 *
 * All layouts have a size; layout size for value and padding layouts is always explicitly denoted; this means that a layout description
 * always has the same size in bits, regardless of the platform in which it is used. For derived layouts, the size is computed
 * as follows:
 * <ul>
 *     <li>for a sequence layout <em>S</em> whose element layout is <em>E</em> and size is <em>L</em>,
 *     the size of <em>S</em> is that of <em>E</em>, multiplied by <em>L</em></li>
 *     <li>for a group layout <em>G</em> with member layouts <em>M1</em>, <em>M2</em>, ... <em>Mn</em> whose sizes are
 *     <em>S1</em>, <em>S2</em>, ... <em>Sn</em>, respectively, the size of <em>G</em> is either <em>S1 + S2 + ... + Sn</em> or
 *     <em>max(S1, S2, ... Sn)</em> depending on whether the group is a <em>struct</em> or an <em>union</em>, respectively</li>
 * </ul>
 * <p>
 * Furthermore, all layouts feature a <em>natural alignment</em> which can be inferred as follows:
 * <ul>
 *     <li>for a padding layout <em>L</em>, the natural alignment is 1, regardless of its size; that is, in the absence
 *     of an explicit alignment constraint, a padding layout should not affect the alignment constraint of the group
 *     layout it is nested into</li>
 *     <li>for a value layout <em>L</em> whose size is <em>N</em>, the natural alignment of <em>L</em> is <em>N</em></li>
 *     <li>for a sequence layout <em>S</em> whose element layout is <em>E</em>, the natural alignment of <em>S</em> is that of <em>E</em></li>
 *     <li>for a group layout <em>G</em> with member layouts <em>M1</em>, <em>M2</em>, ... <em>Mn</em> whose alignments are
 *     <em>A1</em>, <em>A2</em>, ... <em>An</em>, respectively, the natural alignment of <em>G</em> is <em>max(A1, A2 ... An)</em></li>
 * </ul>
 * A layout's natural alignment can be overridden if needed (see {@link MemoryLayout#withBitAlignment(long)}), which can be useful to describe
 * hyper-aligned layouts.
 * <p>
 * All value layouts have an <em>explicit</em> byte order (see {@link java.nio.ByteOrder}) which is set when the layout is created.
 *
 * <h2><a id = "layout-paths">Layout paths</a></h2>
 *
 * A <em>layout path</em> originates from a <em>root</em> layout (typically a group or a sequence layout) and terminates
 * at a layout nested within the root layout - this is the layout <em>selected</em> by the layout path.
 * Layout paths are typically expressed as a sequence of one or more {@link PathElement} instances.
 * <p>
 * Layout paths are for example useful in order to obtain {@linkplain MemoryLayout#bitOffset(PathElement...) offsets} of
 * arbitrarily nested layouts inside another layout, to quickly obtain a {@linkplain #varHandle(PathElement...) memory access handle}
 * corresponding to the selected layout, or to {@linkplain #select(PathElement...) select} an arbitrarily nested layout inside
 * another layout.
 * <p>
 * Such <em>layout paths</em> can be constructed programmatically using the methods in this class.
 * For instance, given the {@code taggedValues} layout instance constructed as above, we can obtain the offset,
 * in bits, of the member layout named <code>value</code> in the <em>first</em> sequence element, as follows:
 * {@snippet lang=java :
 * long valueOffset = taggedValues.bitOffset(PathElement.sequenceElement(0),
 *                                           PathElement.groupElement("value")); // yields 32
 * }
 *
 * Similarly, we can select the member layout named {@code value}, as follows:
 * {@snippet lang=java :
 * MemoryLayout value = taggedValues.select(PathElement.sequenceElement(),
 *                                          PathElement.groupElement("value"));
 * }
 *
 * Layout paths can feature one or more <em>free dimensions</em>. For instance, a layout path traversing
 * an unspecified sequence element (that is, where one of the path component was obtained with the
 * {@link PathElement#sequenceElement()} method) features an additional free dimension, which will have to be bound at runtime.
 * This is important when obtaining a {@linkplain MethodHandles#memorySegmentViewVarHandle(ValueLayout) memory segment view var handle}
 * from layouts, as in the following code:
 *
 * {@snippet lang=java :
 * VarHandle valueHandle = taggedValues.varHandle(PathElement.sequenceElement(),
 *                                                PathElement.groupElement("value"));
 * }
 *
 * Since the layout path constructed in the above example features exactly one free dimension (as it doesn't specify
 * <em>which</em> member layout named {@code value} should be selected from the enclosing sequence layout),
 * it follows that the var handle {@code valueHandle} will feature an <em>additional</em> {@code long}
 * access coordinate.
 *
 * <p>A layout path with free dimensions can also be used to create an offset-computing method handle, using the
 * {@link #bitOffset(PathElement...)} or {@link #byteOffsetHandle(PathElement...)} method. Again, free dimensions are
 * translated into {@code long} parameters of the created method handle. The method handle can be used to compute the
 * offsets of elements of a sequence at different indices, by supplying these indices when invoking the method handle.
 * For instance:
 *
 * {@snippet lang=java :
 * MethodHandle offsetHandle = taggedValues.byteOffsetHandle(PathElement.sequenceElement(),
 *                                                           PathElement.groupElement("kind"));
 * long offset1 = (long) offsetHandle.invokeExact(1L); // 8
 * long offset2 = (long) offsetHandle.invokeExact(2L); // 16
 * }
 *
 * @implSpec
 * Implementations of this interface are immutable, thread-safe and <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 *
 * @since 19
 */
@PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
public sealed interface MemoryLayout permits AbstractLayout, SequenceLayout, GroupLayout, PaddingLayout, ValueLayout {

    /**
     * {@return the layout size, in bits}
     */
    long bitSize();

    /**
     * {@return the layout size, in bytes}
     * @throws UnsupportedOperationException if {@code bitSize()} is not a multiple of 8.
     */
    long byteSize();

    /**
     * {@return the name (if any) associated with this layout}
     * @see MemoryLayout#withName(String)
     */
    Optional<String> name();

    /**
     * Returns a memory layout with the same size and alignment constraints as this layout,
     * but with the specified name.
     *
     * @param name the layout name.
     * @return a memory layout with the given name.
     * @see MemoryLayout#name()
     */
    MemoryLayout withName(String name);

    /**
     * Returns the alignment constraint associated with this layout, expressed in bits. Layout alignment defines a power
     * of two {@code A} which is the bit-wise alignment of the layout. If {@code A <= 8} then {@code A/8} is the number of
     * bytes that must be aligned for any pointer that correctly points to this layout. Thus:
     *
     * <ul>
     * <li>{@code A=8} means unaligned (in the usual sense), which is common in packets.</li>
     * <li>{@code A=64} means word aligned (on LP64), {@code A=32} int aligned, {@code A=16} short aligned, etc.</li>
     * <li>{@code A=512} is the most strict alignment required by the x86/SV ABI (for AVX-512 data).</li>
     * </ul>
     *
     * If no explicit alignment constraint was set on this layout (see {@link #withBitAlignment(long)}),
     * then this method returns the <a href="#layout-align">natural alignment</a> constraint (in bits) associated with this layout.
     *
     * @return the layout alignment constraint, in bits.
     */
    long bitAlignment();

    /**
     * Returns the alignment constraint associated with this layout, expressed in bytes. Layout alignment defines a power
     * of two {@code A} which is the byte-wise alignment of the layout, where {@code A} is the number of bytes that must be aligned
     * for any pointer that correctly points to this layout. Thus:
     *
     * <ul>
     * <li>{@code A=1} means unaligned (in the usual sense), which is common in packets.</li>
     * <li>{@code A=8} means word aligned (on LP64), {@code A=4} int aligned, {@code A=2} short aligned, etc.</li>
     * <li>{@code A=64} is the most strict alignment required by the x86/SV ABI (for AVX-512 data).</li>
     * </ul>
     *
     * If no explicit alignment constraint was set on this layout (see {@link #withBitAlignment(long)}),
     * then this method returns the <a href="#layout-align">natural alignment</a> constraint (in bytes) associated with this layout.
     *
     * @return the layout alignment constraint, in bytes.
     * @throws UnsupportedOperationException if {@code bitAlignment()} is not a multiple of 8.
     */
    default long byteAlignment() {
        return Utils.bitsToBytesOrThrow(bitAlignment(),
                () -> new UnsupportedOperationException("Cannot compute byte alignment; bit alignment is not a multiple of 8"));
    }

    /**
     * Returns a memory layout with the same size and name as this layout,
     * but with the specified alignment constraints (in bits).
     *
     * @param bitAlignment the layout alignment constraint, expressed in bits.
     * @return a memory layout with the given alignment constraints.
     * @throws IllegalArgumentException if {@code bitAlignment} is not a power of two, or if it's less than 8.
     */
    MemoryLayout withBitAlignment(long bitAlignment);

    /**
     * Computes the offset, in bits, of the layout selected by the given layout path, where the path is considered rooted in this
     * layout.
     *
     * @param elements the layout path elements.
     * @return The offset, in bits, of the layout selected by the layout path in {@code elements}.
     * @throws IllegalArgumentException if the layout path does not select any layout nested in this layout, or if the
     * layout path contains one or more path elements that select multiple sequence element indices
     * (see {@link PathElement#sequenceElement()} and {@link PathElement#sequenceElement(long, long)}).
     * @throws NullPointerException if either {@code elements == null}, or if any of the elements
     * in {@code elements} is {@code null}.
     */
    default long bitOffset(PathElement... elements) {
        return computePathOp(LayoutPath.rootPath(this), LayoutPath::offset,
                EnumSet.of(PathKind.SEQUENCE_ELEMENT, PathKind.SEQUENCE_RANGE), elements);
    }

    /**
     * Creates a method handle that can be used to compute the offset, in bits, of the layout selected
     * by the given layout path, where the path is considered rooted in this layout.
     *
     * <p>The returned method handle has a return type of {@code long}, and features as many {@code long}
     * parameter types as there are free dimensions in the provided layout path (see {@link PathElement#sequenceElement()}),
     * where the order of the parameters corresponds to the order of the path elements.
     * The returned method handle can be used to compute a layout offset similar to {@link #bitOffset(PathElement...)},
     * but where some sequence indices are specified only when invoking the method handle.
     *
     * <p>The final offset returned by the method handle is computed as follows:
     *
     * <blockquote><pre>{@code
     * offset = c_1 + c_2 + ... + c_m + (x_1 * s_1) + (x_2 * s_2) + ... + (x_n * s_n)
     * }</pre></blockquote>
     *
     * where {@code x_1}, {@code x_2}, ... {@code x_n} are <em>dynamic</em> values provided as {@code long}
     * arguments, whereas {@code c_1}, {@code c_2}, ... {@code c_m} are <em>static</em> offset constants
     * and {@code s_0}, {@code s_1}, ... {@code s_n} are <em>static</em> stride constants which are derived from
     * the layout path.
     *
     * @param elements the layout path elements.
     * @return a method handle that can be used to compute the bit offset of the layout element
     * specified by the given layout path elements, when supplied with the missing sequence element indices.
     * @throws IllegalArgumentException if the layout path contains one or more path elements that select
     * multiple sequence element indices (see {@link PathElement#sequenceElement(long, long)}).
     */
    default MethodHandle bitOffsetHandle(PathElement... elements) {
        return computePathOp(LayoutPath.rootPath(this), LayoutPath::offsetHandle,
                EnumSet.of(PathKind.SEQUENCE_RANGE), elements);
    }

    /**
     * Computes the offset, in bytes, of the layout selected by the given layout path, where the path is considered rooted in this
     * layout.
     *
     * @param elements the layout path elements.
     * @return The offset, in bytes, of the layout selected by the layout path in {@code elements}.
     * @throws IllegalArgumentException if the layout path does not select any layout nested in this layout, or if the
     * layout path contains one or more path elements that select multiple sequence element indices
     * (see {@link PathElement#sequenceElement()} and {@link PathElement#sequenceElement(long, long)}).
     * @throws UnsupportedOperationException if {@code bitOffset(elements)} is not a multiple of 8.
     * @throws NullPointerException if either {@code elements == null}, or if any of the elements
     * in {@code elements} is {@code null}.
     */
    default long byteOffset(PathElement... elements) {
        return Utils.bitsToBytesOrThrow(bitOffset(elements), Utils.bitsToBytesThrowOffset);
    }

    /**
     * Creates a method handle that can be used to compute the offset, in bytes, of the layout selected
     * by the given layout path, where the path is considered rooted in this layout.
     *
     * <p>The returned method handle has a return type of {@code long}, and features as many {@code long}
     * parameter types as there are free dimensions in the provided layout path (see {@link PathElement#sequenceElement()}),
     * where the order of the parameters corresponds to the order of the path elements.
     * The returned method handle can be used to compute a layout offset similar to {@link #byteOffset(PathElement...)},
     * but where some sequence indices are specified only when invoking the method handle.
     *
     * <p>The final offset returned by the method handle is computed as follows:
     *
     * <blockquote><pre>{@code
     * bitOffset = c_1 + c_2 + ... + c_m + (x_1 * s_1) + (x_2 * s_2) + ... + (x_n * s_n)
     * offset = bitOffset / 8
     * }</pre></blockquote>
     *
     * where {@code x_1}, {@code x_2}, ... {@code x_n} are <em>dynamic</em> values provided as {@code long}
     * arguments, whereas {@code c_1}, {@code c_2}, ... {@code c_m} are <em>static</em> offset constants
     * and {@code s_0}, {@code s_1}, ... {@code s_n} are <em>static</em> stride constants which are derived from
     * the layout path.
     *
     * <p>The method handle will throw an {@link UnsupportedOperationException} if the computed
     * offset in bits is not a multiple of 8.
     *
     * @param elements the layout path elements.
     * @return a method handle that can be used to compute the byte offset of the layout element
     * specified by the given layout path elements, when supplied with the missing sequence element indices.
     * @throws IllegalArgumentException if the layout path contains one or more path elements that select
     * multiple sequence element indices (see {@link PathElement#sequenceElement(long, long)}).
     */
    default MethodHandle byteOffsetHandle(PathElement... elements) {
        MethodHandle mh = bitOffsetHandle(elements);
        mh = MethodHandles.filterReturnValue(mh, Utils.MH_bitsToBytesOrThrowForOffset);
        return mh;
    }

    /**
     * Creates an access var handle that can be used to dereference memory at the layout selected by the given layout path,
     * where the path is considered rooted in this layout.
     * <p>
     * The final memory location accessed by the returned var handle can be computed as follows:
     *
     * <blockquote><pre>{@code
     * address = base + offset
     * }</pre></blockquote>
     *
     * where {@code base} denotes the base address expressed by the {@link MemorySegment} access coordinate
     * (see {@link MemorySegment#address()} and {@link MemoryAddress#toRawLongValue()}) and {@code offset}
     * can be expressed in the following form:
     *
     * <blockquote><pre>{@code
     * offset = c_1 + c_2 + ... + c_m + (x_1 * s_1) + (x_2 * s_2) + ... + (x_n * s_n)
     * }</pre></blockquote>
     *
     * where {@code x_1}, {@code x_2}, ... {@code x_n} are <em>dynamic</em> values provided as {@code long}
     * arguments, whereas {@code c_1}, {@code c_2}, ... {@code c_m} are <em>static</em> offset constants
     * and {@code s_1}, {@code s_2}, ... {@code s_n} are <em>static</em> stride constants which are derived from
     * the layout path.
     * <p>
     * Additionally, the provided dynamic values must conform to some bound which is derived from the layout path, that is,
     * {@code 0 <= x_i < b_i}, where {@code 1 <= i <= n}, or {@link IndexOutOfBoundsException} is thrown.
     *
     * @apiNote the resulting var handle will feature an additional {@code long} access coordinate for every
     * unspecified sequence access component contained in this layout path. Moreover, the resulting var handle
     * features certain <em>access mode restrictions</em>, which are common to all memory segment view handles.
     *
     * @param elements the layout path elements.
     * @return a var handle which can be used to dereference memory at the (possibly nested) layout selected by the layout path in {@code elements}.
     * @throws UnsupportedOperationException if the layout path has one or more elements with incompatible alignment constraints.
     * @throws IllegalArgumentException if the layout path in {@code elements} does not select a value layout (see {@link ValueLayout}).
     * @see MethodHandles#memorySegmentViewVarHandle(ValueLayout)
     */
    default VarHandle varHandle(PathElement... elements) {
        return computePathOp(LayoutPath.rootPath(this), LayoutPath::dereferenceHandle,
                Set.of(), elements);
    }

    /**
     * Creates a method handle which, given a memory segment, returns a {@linkplain MemorySegment#asSlice(long,long) slice}
     * corresponding to the layout selected by the given layout path, where the path is considered rooted in this layout.
     *
     * <p>The returned method handle has a return type of {@code MemorySegment}, features a {@code MemorySegment}
     * parameter as leading parameter representing the segment to be sliced, and features as many trailing {@code long}
     * parameter types as there are free dimensions in the provided layout path (see {@link PathElement#sequenceElement()}),
     * where the order of the parameters corresponds to the order of the path elements.
     * The returned method handle can be used to create a slice similar to using {@link MemorySegment#asSlice(long, long)},
     * but where the offset argument is dynamically compute based on indices specified when invoking the method handle.
     *
     * <p>The offset of the returned segment is computed as follows:
     *
     * <blockquote><pre>{@code
     * bitOffset = c_1 + c_2 + ... + c_m + (x_1 * s_1) + (x_2 * s_2) + ... + (x_n * s_n)
     * offset = bitOffset / 8
     * }</pre></blockquote>
     *
     * where {@code x_1}, {@code x_2}, ... {@code x_n} are <em>dynamic</em> values provided as {@code long}
     * arguments, whereas {@code c_1}, {@code c_2}, ... {@code c_m} are <em>static</em> offset constants
     * and {@code s_1}, {@code s_2}, ... {@code s_n} are <em>static</em> stride constants which are derived from
     * the layout path.
     *
     * <p>After the offset is computed, the returned segment is created as if by calling:
     * {@snippet lang=java :
     * segment.asSlice(offset, layout.byteSize());
     * }
     *
     * where {@code segment} is the segment to be sliced, and where {@code layout} is the layout selected by the given
     * layout path, as per {@link MemoryLayout#select(PathElement...)}.
     *
     * <p>The method handle will throw an {@link UnsupportedOperationException} if the computed
     * offset in bits is not a multiple of 8.
     *
     * @param elements the layout path elements.
     * @return a method handle which can be used to create a slice of the selected layout element, given a segment.
     * @throws UnsupportedOperationException if the size of the selected layout in bits is not a multiple of 8.
     */
    default MethodHandle sliceHandle(PathElement... elements) {
        return computePathOp(LayoutPath.rootPath(this), LayoutPath::sliceHandle,
                Set.of(), elements);
    }

    /**
     * Selects the layout from a path rooted in this layout.
     *
     * @param elements the layout path elements.
     * @return the layout selected by the layout path in {@code elements}.
     * @throws IllegalArgumentException if the layout path does not select any layout nested in this layout,
     * or if the layout path contains one or more path elements that select one or more sequence element indices
     * (see {@link PathElement#sequenceElement(long)} and {@link PathElement#sequenceElement(long, long)}).
     */
    default MemoryLayout select(PathElement... elements) {
        return computePathOp(LayoutPath.rootPath(this), LayoutPath::layout,
                EnumSet.of(PathKind.SEQUENCE_ELEMENT_INDEX, PathKind.SEQUENCE_RANGE), elements);
    }

    private static <Z> Z computePathOp(LayoutPath path, Function<LayoutPath, Z> finalizer,
                                       Set<PathKind> badKinds, PathElement... elements) {
        Objects.requireNonNull(elements);
        for (PathElement e : elements) {
            LayoutPath.PathElementImpl pathElem = (LayoutPath.PathElementImpl)Objects.requireNonNull(e);
            if (badKinds.contains(pathElem.kind())) {
                throw new IllegalArgumentException(String.format("Invalid %s selection in layout path", pathElem.kind().description()));
            }
            path = pathElem.apply(path);
        }
        return finalizer.apply(path);
    }

    /**
     * {@return true, if this layout is a padding layout}
     */
    boolean isPadding();

    /**
     * An element in a <a href="MemoryLayout.html#layout-paths"><em>layout path</em></a>. There
     * are two kinds of path elements: <em>group path elements</em> and <em>sequence path elements</em>. Group
     * path elements are used to select a named member layout within a {@link GroupLayout}. Sequence
     * path elements are used to select a sequence element layout within a {@link SequenceLayout}; selection
     * of sequence element layout can be <em>explicit</em> (see {@link PathElement#sequenceElement(long)}) or
     * <em>implicit</em> (see {@link PathElement#sequenceElement()}). When a path uses one or more implicit
     * sequence path elements, it acquires additional <em>free dimensions</em>.
     *
     * @implSpec
     * Implementations of this interface are immutable, thread-safe and <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
     *
     * @since 19
     */
    @PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
    sealed interface PathElement permits LayoutPath.PathElementImpl {

        /**
         * Returns a path element which selects a member layout with the given name in a group layout.
         * The path element returned by this method does not alter the number of free dimensions of any path
         * that is combined with such element.
         *
         * @implSpec in case multiple group elements with a matching name exist, the path element returned by this
         * method will select the first one; that is, the group element with the lowest offset from current path is selected.
         *
         * @param name the name of the group element to be selected.
         * @return a path element which selects the group element with the given name.
         */
        static PathElement groupElement(String name) {
            Objects.requireNonNull(name);
            return new LayoutPath.PathElementImpl(PathKind.GROUP_ELEMENT,
                                                  path -> path.groupElement(name));
        }

        /**
         * Returns a path element which selects the element layout at the specified position in a sequence layout.
         * The path element returned by this method does not alter the number of free dimensions of any path
         * that is combined with such element.
         *
         * @param index the index of the sequence element to be selected.
         * @return a path element which selects the sequence element layout with the given index.
         * @throws IllegalArgumentException if {@code index < 0}.
         */
        static PathElement sequenceElement(long index) {
            if (index < 0) {
                throw new IllegalArgumentException("Index must be positive: " + index);
            }
            return new LayoutPath.PathElementImpl(PathKind.SEQUENCE_ELEMENT_INDEX,
                                                  path -> path.sequenceElement(index));
        }

        /**
         * Returns a path element which selects the element layout in a <em>range</em> of positions in a sequence layout.
         * The range is expressed as a pair of starting index (inclusive) {@code S} and step factor (which can also be negative)
         * {@code F}.
         * <p>
         * If a path with free dimensions {@code n} is combined with the path element returned by this method,
         * the number of free dimensions of the resulting path will be {@code 1 + n}. If the free dimension associated
         * with this path is bound by an index {@code I}, the resulting accessed offset can be obtained with the following
         * formula:
         *
         * <blockquote><pre>{@code
         * E * (S + I * F)
         * }</pre></blockquote>
         *
         * where {@code E} is the size (in bytes) of the sequence element layout.
         * <p>
         * Additionally, if {@code C} is the sequence element count, it follows that {@code 0 <= I < B},
         * where {@code B} is computed as follows:
         *
         * <ul>
         *    <li>if {@code F > 0}, then {@code B = ceilDiv(C - S, F)}</li>
         *    <li>if {@code F < 0}, then {@code B = ceilDiv(-(S + 1), -F)}</li>
         * </ul>
         *
         * @param start the index of the first sequence element to be selected.
         * @param step the step factor at which subsequence sequence elements are to be selected.
         * @return a path element which selects the sequence element layout with the given index.
         * @throws IllegalArgumentException if {@code start < 0}, or {@code step == 0}.
         */
        static PathElement sequenceElement(long start, long step) {
            if (start < 0) {
                throw new IllegalArgumentException("Start index must be positive: " + start);
            }
            if (step == 0) {
                throw new IllegalArgumentException("Step must be != 0: " + step);
            }
            return new LayoutPath.PathElementImpl(PathKind.SEQUENCE_RANGE,
                                                  path -> path.sequenceElement(start, step));
        }

        /**
         * Returns a path element which selects an unspecified element layout in a sequence layout.
         * <p>
         * If a path with free dimensions {@code n} is combined with the path element returned by this method,
         * the number of free dimensions of the resulting path will be {@code 1 + n}. If the free dimension associated
         * with this path is bound by an index {@code I}, the resulting accessed offset can be obtained with the following
         * formula:
         *
         * <blockquote><pre>{@code
         * E * I
         * }</pre></blockquote>
         *
         * where {@code E} is the size (in bytes) of the sequence element layout.
         * <p>
         * Additionally, if {@code C} is the sequence element count, it follows that {@code 0 <= I < C}.
         *
         * @return a path element which selects an unspecified sequence element layout.
         */
        static PathElement sequenceElement() {
            return new LayoutPath.PathElementImpl(PathKind.SEQUENCE_ELEMENT,
                                                  LayoutPath::sequenceElement);
        }
    }

    /**
     * Compares the specified object with this layout for equality. Returns {@code true} if and only if the specified
     * object is also a layout, and it is equal to this layout. Two layouts are considered equal if they are of
     * the same kind, have the same size, name and alignment constraints. Furthermore, depending on the layout kind, additional
     * conditions must be satisfied:
     * <ul>
     *     <li>two value layouts are considered equal if they have the same byte order (see {@link ValueLayout#order()})</li>
     *     <li>two sequence layouts are considered equal if they have the same element count (see {@link SequenceLayout#elementCount()}), and
     *     if their element layouts (see {@link SequenceLayout#elementLayout()}) are also equal</li>
     *     <li>two group layouts are considered equal if they are of the same kind (see {@link GroupLayout#isStruct()},
     *     {@link GroupLayout#isUnion()}) and if their member layouts (see {@link GroupLayout#memberLayouts()}) are also equal</li>
     * </ul>
     *
     * @param that the object to be compared for equality with this layout.
     * @return {@code true} if the specified object is equal to this layout.
     */
    boolean equals(Object that);

    /**
     * {@return the hash code value for this layout}
     */
    int hashCode();

    /**
     * {@return the string representation of this layout}
     */
    @Override
    String toString();

    /**
     * Creates a padding layout with the given size.
     *
     * @param size the padding size in bits.
     * @return the new selector layout.
     * @throws IllegalArgumentException if {@code size <= 0}.
     */
    static MemoryLayout paddingLayout(long size) {
        AbstractLayout.checkSize(size);
        return new PaddingLayout(size);
    }

    /**
     * Creates a value layout of given Java carrier and byte order. The type of resulting value layout is determined
     * by the carrier provided:
     * <ul>
     *     <li>{@link ValueLayout.OfBoolean}, for {@code boolean.class}</li>
     *     <li>{@link ValueLayout.OfByte}, for {@code byte.class}</li>
     *     <li>{@link ValueLayout.OfShort}, for {@code short.class}</li>
     *     <li>{@link ValueLayout.OfChar}, for {@code char.class}</li>
     *     <li>{@link ValueLayout.OfInt}, for {@code int.class}</li>
     *     <li>{@link ValueLayout.OfFloat}, for {@code float.class}</li>
     *     <li>{@link ValueLayout.OfLong}, for {@code long.class}</li>
     *     <li>{@link ValueLayout.OfDouble}, for {@code double.class}</li>
     *     <li>{@link ValueLayout.OfAddress}, for {@code MemoryAddress.class}</li>
     * </ul>
     * @param carrier the value layout carrier.
     * @param order the value layout's byte order.
     * @return a value layout with the given Java carrier and byte-order.
     * @throws IllegalArgumentException if the carrier type is not supported.
     */
    static ValueLayout valueLayout(Class<?> carrier, ByteOrder order) {
        Objects.requireNonNull(carrier);
        Objects.requireNonNull(order);
        if (carrier == boolean.class) {
            return new ValueLayout.OfBoolean(order);
        } else if (carrier == char.class) {
            return new ValueLayout.OfChar(order);
        } else if (carrier == byte.class) {
            return new ValueLayout.OfByte(order);
        } else if (carrier == short.class) {
            return new ValueLayout.OfShort(order);
        } else if (carrier == int.class) {
            return new ValueLayout.OfInt(order);
        } else if (carrier == float.class) {
            return new ValueLayout.OfFloat(order);
        } else if (carrier == long.class) {
            return new ValueLayout.OfLong(order);
        } else if (carrier == double.class) {
            return new ValueLayout.OfDouble(order);
        } else if (carrier == MemoryAddress.class) {
            return new ValueLayout.OfAddress(order);
        } else {
            throw new IllegalArgumentException("Unsupported carrier: " + carrier.getName());
        }
    }

    /**
     * Creates a sequence layout with the given element layout and element count. If the element count has the
     * special value {@code -1}, the element count is inferred to be the biggest possible count such that
     * the sequence layout size does not overflow, using the following formula:
     *
     * <blockquote><pre>{@code
     * inferredElementCount = Long.MAX_VALUE / elementLayout.bitSize();
     * }</pre></blockquote>
     *
     * @param elementCount the sequence element count; if set to {@code -1}, the sequence element count is inferred.
     * @param elementLayout the sequence element layout.
     * @return the new sequence layout with the given element layout and size.
     * @throws IllegalArgumentException if {@code elementCount < -1}.
     * @throws IllegalArgumentException if {@code elementCount != -1} and the computation {@code elementCount * elementLayout.bitSize()} overflows.
     */
    static SequenceLayout sequenceLayout(long elementCount, MemoryLayout elementLayout) {
        if (elementCount == -1) {
            // inferred element count
            long inferredElementCount = Long.MAX_VALUE / elementLayout.bitSize();
            return new SequenceLayout(inferredElementCount, elementLayout);
        } else {
            // explicit element count
            AbstractLayout.checkSize(elementCount, true);
            return wrapOverflow(() ->
                    new SequenceLayout(elementCount, Objects.requireNonNull(elementLayout)));
        }
    }

    /**
     * Creates a struct layout with the given member layouts.
     *
     * @param elements The member layouts of the struct layout.
     * @return a struct layout with the given member layouts.
     * @throws IllegalArgumentException if the sum of the {@linkplain #bitSize() bit sizes} of the member layouts
     * overflows.
     */
    static GroupLayout structLayout(MemoryLayout... elements) {
        Objects.requireNonNull(elements);
        return wrapOverflow(() ->
                new GroupLayout(GroupLayout.Kind.STRUCT,
                        Stream.of(elements)
                                .map(Objects::requireNonNull)
                                .collect(Collectors.toList())));
    }

    /**
     * Creates a union layout with the given member layouts.
     *
     * @param elements The member layouts of the union layout.
     * @return a union layout with the given member layouts.
     */
    static GroupLayout unionLayout(MemoryLayout... elements) {
        Objects.requireNonNull(elements);
        return new GroupLayout(GroupLayout.Kind.UNION,
                Stream.of(elements)
                        .map(Objects::requireNonNull)
                        .collect(Collectors.toList()));
    }

    private static <L extends MemoryLayout> L wrapOverflow(Supplier<L> layoutSupplier) {
        try {
            return layoutSupplier.get();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Layout size exceeds Long.MAX_VALUE");
        }
    }
}
