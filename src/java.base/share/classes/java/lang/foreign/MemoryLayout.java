/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.foreign;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import jdk.internal.foreign.LayoutPath;
import jdk.internal.foreign.LayoutPath.PathElementImpl.PathKind;
import jdk.internal.foreign.Utils;
import jdk.internal.foreign.layout.MemoryLayoutUtil;
import jdk.internal.foreign.layout.PaddingLayoutImpl;
import jdk.internal.foreign.layout.SequenceLayoutImpl;
import jdk.internal.foreign.layout.StructLayoutImpl;
import jdk.internal.foreign.layout.UnionLayoutImpl;
import jdk.internal.javac.PreviewFeature;

/**
 * A memory layout describes the contents of a memory segment.
 * <p>
 * There are two leaves in the layout hierarchy, {@linkplain ValueLayout value layouts}, which are used to represent values of given size and kind (see
 * and {@linkplain PaddingLayout padding layouts} which are used, as the name suggests, to represent a portion of a memory
 * segment whose contents should be ignored, and which are primarily present for alignment reasons.
 * Some common value layout constants, such as {@link ValueLayout#JAVA_INT} and {@link ValueLayout#JAVA_FLOAT_UNALIGNED}
 * are defined in the {@link ValueLayout} class. A special kind of value layout, namely an {@linkplain AddressLayout address layout},
 * is used to model values that denote the address of a region of memory.
 * <p>
 * More complex layouts can be derived from simpler ones: a {@linkplain SequenceLayout sequence layout} denotes a
 * homogeneous repetition of zero or more occurrences of an element layout; a {@linkplain GroupLayout group layout}
 * denotes a heterogeneous aggregation of zero or more member layouts. Group layouts come in two
 * flavors: {@linkplain StructLayout struct layouts}, where member layouts are laid out one after the other, and
 * {@linkplain UnionLayout union layouts} where member layouts are laid out at the same starting offset.
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
 *         MemoryLayout.paddingLayout(3),
 *         ValueLayout.JAVA_INT.withName("value")
 *     )
 * ).withName("TaggedValues");
 * }
 *
 * <h2 id="layout-align">Characteristics of memory layouts</h2>
 *
 * All layouts have a <em>size</em> (expressed in bytes), which is defined as follows:
 * <ul>
 *     <li>The size of a value layout is determined by the {@linkplain ValueLayout#carrier()}
 *     associated with the value layout. That is, the constant {@link ValueLayout#JAVA_INT} has carrier {@code int}, and
 *     size of 4 bytes;</li>
 *     <li>The size of an address layout is platform-dependent. That is, the constant {@link ValueLayout#ADDRESS}
 *     has size of 8 bytes on a 64-bit platform;</li>
 *     <li>The size of a padding layout is always provided explicitly, on {@linkplain MemoryLayout#paddingLayout(long) construction};</li>
 *     <li>The size of a sequence layout whose element layout is <em>E</em> and element count is <em>L</em>,
 *     is the size of <em>E</em>, multiplied by <em>L</em>;</li>
 *     <li>The size of a struct layout with member layouts <em>M1</em>, <em>M2</em>, ... <em>Mn</em> whose sizes are
 *     <em>S1</em>, <em>S2</em>, ... <em>Sn</em>, respectively, is <em>S1 + S2 + ... + Sn</em>;</li>
 *     <li>The size of a union layout <em>U</em> with member layouts <em>M1</em>, <em>M2</em>, ... <em>Mn</em> whose sizes are
 *     <em>S1</em>, <em>S2</em>, ... <em>Sn</em>, respectively, is <em>max(S1, S2, ... Sn).</em></li>
 * </ul>
 * <p>
 * Furthermore, all layouts have a <em>natural alignment</em> (expressed in bytes) which is defined as follows:
 * <ul>
 *     <li>The natural alignment of a padding layout is 1;</li>
 *     <li>The natural alignment of a value layout whose size is <em>N</em> is <em>N</em>;</li>
 *     <li>The natural alignment of a sequence layout whose element layout is <em>E</em> is the alignment of <em>E</em>;</li>
 *     <li>The natural alignment of a group layout with member layouts <em>M1</em>, <em>M2</em>, ... <em>Mn</em> whose
 *     alignments are <em>A1</em>, <em>A2</em>, ... <em>An</em>, respectively, is <em>max(A1, A2 ... An)</em>.</li>
 * </ul>
 * A layout's alignment can be overridden if needed (see {@link MemoryLayout#withByteAlignment(long)}), which can be useful to describe
 * layouts with weaker or stronger alignment constraints.
 *
 * <h2 id="layout-paths">Layout paths</h2>
 *
 * A <em>layout path</em> is used to unambiguously select a layout that is nested in some other layout.
 * Layout paths are typically expressed as a sequence of one or more {@linkplain PathElement path elements}.
 * (A more formal definition of layout paths is provided <a href="#well-formedness">below</a>).
 * <p>
 * Layout paths can be used to:
 * <ul>
 *     <li>obtain {@linkplain MemoryLayout#byteOffset(PathElement...) offsets} of arbitrarily nested layouts;</li>
 *     <li>obtain a {@linkplain #varHandle(PathElement...) var handle} that can be used to access the value corresponding
 *     to the selected layout;</li>
 *     <li>{@linkplain #select(PathElement...) select} an arbitrarily nested layout.</li>
 * </ul>
 * <p>
 * For instance, given the {@code taggedValues} sequence layout constructed above, we can obtain the offset,
 * in bytes, of the member layout named <code>value</code> in the <em>first</em> sequence element, as follows:
 * {@snippet lang=java :
 * long valueOffset = taggedValues.byteOffset(PathElement.sequenceElement(0),
 *                                           PathElement.groupElement("value")); // yields 4
 * }
 *
 * Similarly, we can select the member layout named {@code value}, as follows:
 * {@snippet lang=java :
 * MemoryLayout value = taggedValues.select(PathElement.sequenceElement(),
 *                                          PathElement.groupElement("value"));
 * }
 *
 * <h3 id="open-path-elements">Open path elements</h3>
 *
 * Some layout path elements, said <em>open path elements</em>, can select multiple layouts at once. For instance,
 * the open path elements {@link PathElement#sequenceElement()}, {@link PathElement#sequenceElement(long, long)} select
 * an unspecified element in a sequence layout. A var handle derived from a layout path containing one or more
 * open path element features additional coordinates of type {@code long}, which can be used by clients to <em>bind</em>
 * the open elements in the path:
 *
 * {@snippet lang=java :
 * VarHandle valueHandle = taggedValues.varHandle(PathElement.sequenceElement(),
 *                                                PathElement.groupElement("value"));
 * MemorySegment valuesSegment = ...
 * int val = (int) valueHandle.get(valuesSegment, 2); // reads the "value" field of the third struct in the array
 * }
 *
 * <p>
 * Open path elements also affects the creation of
 * {@linkplain #byteOffsetHandle(PathElement...) offset-computing method handles}. Each open path element becomes
 * an additional {@code long} parameter in the obtained method handle. This parameter can be used to specify the index
 * of the sequence element whose offset is to be computed:
 *
 * {@snippet lang=java :
 * MethodHandle offsetHandle = taggedValues.byteOffsetHandle(PathElement.sequenceElement(),
 *                                                           PathElement.groupElement("kind"));
 * long offset1 = (long) offsetHandle.invokeExact(1L); // 8
 * long offset2 = (long) offsetHandle.invokeExact(2L); // 16
 * }
 *
 * <h3 id="deref-path-elements">Dereference path elements</h3>
 *
 * A special kind of path element, called <em>dereference path element</em>, allows var handles obtained from
 * memory layouts to follow pointers. Consider the following layout:
 *
 * {@snippet lang=java :
 * StructLayout RECTANGLE = MemoryLayout.structLayout(
 *         ValueLayout.ADDRESS.withTargetLayout(
 *                 MemoryLayout.sequenceLayout(4,
 *                         MemoryLayout.structLayout(
 *                                 ValueLayout.JAVA_INT.withName("x"),
 *                                 ValueLayout.JAVA_INT.withName("y")
 *                         ).withName("point")
*                  )
*          ).withName("points")
 * );
 * }
 *
 * This layout is a struct layout which describe a rectangle. It contains a single field, namely {@code points},
 * an address layout whose {@linkplain AddressLayout#targetLayout() target layout} is a sequence layout of four
 * struct layouts. Each struct layout describes a two-dimensional point, and is defined as a pair or
 * {@link ValueLayout#JAVA_INT} coordinates, with names {@code x} and {@code y}, respectively.
 * <p>
 * With dereference path elements, we can obtain a var handle which accesses the {@code y} coordinate of one of the
 * point in the rectangle, as follows:
 *
 * {@snippet lang=java :
 * VarHandle rectPointYs = RECTANGLE.varHandle(
 *         PathElement.groupElement("points"),
 *         PathElement.dereferenceElement(),
 *         PathElement.sequenceElement(),
 *         PathElement.groupElement("y")
 * );
 *
 * MemorySegment rect = ...
 * int rect_y_4 = (int) rectPointYs.get(rect, 2); // rect.points[2]->y
 * }
 *
 * <h3 id="well-formedness">Layout path well-formedness</h3>
 *
 * A layout path is applied to a layout {@code C_0}, also called the <em>initial layout</em>. Each path element in a
 * layout path can be thought of as a function which updates the current layout {@code C_i-1} to some other layout
 * {@code C_i}. That is, for each path element {@code E1, E2, ... En}, in a layout path {@code P}, we compute
 * {@code C_i = f_i(C_i-1)}, where {@code f_i} is the selection function associated with the path element under consideration,
 * denoted as {@code E_i}. The final layout {@code C_i} is also called the <em>selected layout</em>.
 * <p>
 * A layout path {@code P} is considered well-formed for an initial layout {@code C_0} if all its path elements
 * {@code E1, E2, ... En} are well-formed for their corresponding input layouts {@code C_0, C_1, ... C_n-1}.
 * A path element {@code E} is considered well-formed for a layout {@code L} if any of the following is true:
 * <ul>
 * <li>{@code L} is a sequence layout and {@code E} is a sequence path element (one of {@link PathElement#sequenceElement(long)},
 * {@link PathElement#sequenceElement(long, long)} or {@link PathElement#sequenceElement()}). Moreover, if {@code E}
 * contains one or more sequence indices, such indices have to be compatible with the sequence layout's element count;</li>
 * <li>{@code L} is a group layout and {@code E} is a group path element (one of {@link PathElement#groupElement(String)}
 * or {@link PathElement#groupElement(long)}). Moreover, the group path element must refer to a valid member layout in
 * {@code L}, either by name, or index;</li>
 * <li>{@code L} is an address layout and {@code E} is a {@linkplain PathElement#dereferenceElement() dereference path element}.
 * Moreover, {@code L} must define some {@linkplain AddressLayout#targetLayout() target layout}.</li>
 * </ul>
 * Any attempt to provide a layout path {@code P} that is not well-formed for an initial layout {@code C_0} will result
 * in an {@link IllegalArgumentException}.
 *
 * @implSpec
 * Implementations of this interface are immutable, thread-safe and <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 *
 * @sealedGraph
 * @since 19
 */
@PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
public sealed interface MemoryLayout permits SequenceLayout, GroupLayout, PaddingLayout, ValueLayout {

    /**
     * {@return the layout size, in bytes}
     */
    long byteSize();

    /**
     * {@return the name (if any) associated with this layout}
     * @see MemoryLayout#withName(String)
     */
    Optional<String> name();

    /**
     * {@return a memory layout with the same characteristics as this layout, but with the given name}
     *
     * @param name the layout name.
     * @see MemoryLayout#name()
     */
    MemoryLayout withName(String name);

    /**
     * {@return a memory layout with the same characteristics as this layout, but with no name}
     *
     * @apiNote This can be useful to compare two layouts that have different names, but are otherwise equal.
     * @see MemoryLayout#name()
     */
    MemoryLayout withoutName();

    /**
     * {@return the alignment constraint associated with this layout, expressed in bytes} Layout alignment defines a power
     * of two {@code A} which is the byte-wise alignment of the layout, where {@code A} is the number of bytes that must be aligned
     * for any pointer that correctly points to this layout. Thus:
     *
     * <ul>
     * <li>{@code A=1} means unaligned (in the usual sense), which is common in packets.</li>
     * <li>{@code A=8} means word aligned (on LP64), {@code A=4} int aligned, {@code A=2} short aligned, etc.</li>
     * <li>{@code A=64} is the most strict alignment required by the x86/SV ABI (for AVX-512 data).</li>
     * </ul>
     *
     * If no explicit alignment constraint was set on this layout (see {@link #withByteAlignment(long)}),
     * then this method returns the <a href="#layout-align">natural alignment</a> constraint (in bytes) associated with this layout.
     */
    long byteAlignment();

    /**
     * {@return a memory layout with the same characteristics as this layout, but with the given
     * alignment constraint (in bytes)}
     *
     * @param byteAlignment the layout alignment constraint, expressed in bytes.
     * @throws IllegalArgumentException if {@code byteAlignment} is not a power of two.
     */
    MemoryLayout withByteAlignment(long byteAlignment);

    /**
     * Computes the offset, in bytes, of the layout selected by the given layout path, where the initial layout in the
     * path is this layout.
     *
     * @param elements the layout path elements.
     * @return The offset, in bytes, of the layout selected by the layout path in {@code elements}.
     * @throws IllegalArgumentException if the layout path is not <a href="#well-formedness">well-formed</a> for this layout.
     * @throws IllegalArgumentException if the layout path contains one or more <a href=#open-path-elements>open path elements</a>.
     * @throws IllegalArgumentException if the layout path contains one or more <a href=#deref-path-elements>dereference path elements</a>.
     */
    default long byteOffset(PathElement... elements) {
        return computePathOp(LayoutPath.rootPath(this), LayoutPath::offset,
                EnumSet.of(PathKind.SEQUENCE_ELEMENT, PathKind.SEQUENCE_RANGE, PathKind.DEREF_ELEMENT), elements);
    }

    /**
     * Creates a method handle that computes the offset, in bytes, of the layout selected
     * by the given layout path, where the initial layout in the path is this layout.
     * <p>
     * The returned method handle has the following characteristics:
     * <ul>
     *     <li>its return type is {@code long};</li>
     *     <li>it has as zero or more parameters of type {@code long}, one for each <a href=#open-path-elements>open path element</a>
     *     in the provided layout path. The order of these parameters corresponds to the order in which the open path
     *     elements occur in the provided layout path.
     * </ul>
     * <p>
     * The final offset returned by the method handle is computed as follows:
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
     * @apiNote The returned method handle can be used to compute a layout offset, similarly to {@link #byteOffset(PathElement...)},
     * but more flexibly, as some indices can be specified when invoking the method handle.
     *
     * @param elements the layout path elements.
     * @return a method handle that computes the offset, in bytes, of the layout selected by the given layout path.
     * @throws IllegalArgumentException if the layout path is not <a href="#well-formedness">well-formed</a> for this layout.
     * @throws IllegalArgumentException if the layout path contains one or more <a href=#deref-path-elements>dereference path elements</a>.
     */
    default MethodHandle byteOffsetHandle(PathElement... elements) {
        return computePathOp(LayoutPath.rootPath(this), LayoutPath::offsetHandle,
                EnumSet.of(PathKind.DEREF_ELEMENT), elements);
    }

    /**
     * Creates a var handle that accesses a memory segment at the offset selected by the given layout path,
     * where the initial layout in the path is this layout.
     * <p>
     * The returned var handle has the following characteristics:
     * <ul>
     *     <li>its type is derived from the {@linkplain ValueLayout#carrier() carrier} of the
     *     selected value layout;</li>
     *     <li>it has as zero or more access coordinates of type {@code long}, one for each
     *     <a href=#open-path-elements>open path element</a> in the provided layout path. The order of these access
     *     coordinates corresponds to the order in which the open path elements occur in the provided
     *     layout path.
     * </ul>
     * <p>
     * The final address accessed by the returned var handle can be computed as follows:
     *
     * <blockquote><pre>{@code
     * address = base(segment) + offset
     * }</pre></blockquote>
     *
     * Where {@code base(segment)} denotes a function that returns the physical base address of the accessed
     * memory segment. For native segments, this function just returns the native segment's
     * {@linkplain MemorySegment#address() address}. For heap segments, this function is more complex, as the address
     * of heap segments is virtualized. The {@code offset} value can be expressed in the following form:
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
     * Additionally, the provided dynamic values must conform to bounds which are derived from the layout path, that is,
     * {@code 0 <= x_i < b_i}, where {@code 1 <= i <= n}, or {@link IndexOutOfBoundsException} is thrown.
     * <p>
     * The base address must be <a href="MemorySegment.html#segment-alignment">aligned</a> according to the {@linkplain
     * #byteAlignment() alignment constraint} of the root layout (this layout). Note that this can be more strict
     * (but not less) than the alignment constraint of the selected value layout.
     * <p>
     * Multiple paths can be chained, with <a href=#deref-path-elements>dereference path elements</a>.
     * A dereference path element constructs a fresh native memory segment whose base address is the address value
     * read obtained by accessing a memory segment at the offset determined by the layout path elements immediately preceding
     * the dereference path element. In other words, if a layout path contains one or more dereference path elements,
     * the final address accessed by the returned var handle can be computed as follows:
     *
     * <blockquote><pre>{@code
     * address_1 = base(segment) + offset_1
     * address_2 = base(segment_1) + offset_2
     * ...
     * address_k = base(segment_k-1) + offset_k
     * }</pre></blockquote>
     *
     * where {@code k} is the number of dereference path elements in a layout path, {@code segment} is the input segment,
     * {@code segment_1}, ...  {@code segment_k-1} are the segments obtained by dereferencing the address associated with
     * a given dereference path element (e.g. {@code segment_1} is a native segment whose base address is {@code address_1}),
     * and {@code offset_1}, {@code offset_2}, ... {@code offset_k} are the offsets computed by evaluating
     * the path elements after a given dereference operation (these offsets are obtained using the computation described
     * above). In these more complex access operations, all memory accesses immediately preceding a dereference operation
     * (e.g. those at addresses {@code address_1}, {@code address_2}, ...,  {@code address_k-1} are performed using the
     * {@link VarHandle.AccessMode#GET} access mode.
     *
     * @apiNote The resulting var handle features certain <em>access mode restrictions</em>, which are common to all
     * {@linkplain MethodHandles#memorySegmentViewVarHandle(ValueLayout) memory segment view handles}.
     *
     * @param elements the layout path elements.
     * @return a var handle that accesses a memory segment at the offset selected by the given layout path.
     * @throws IllegalArgumentException if the layout path is not <a href="#well-formedness">well-formed</a> for this layout.
     * @throws IllegalArgumentException if the layout selected by the provided path is not a {@linkplain ValueLayout value layout}.
     * @see MethodHandles#memorySegmentViewVarHandle(ValueLayout)
     */
    default VarHandle varHandle(PathElement... elements) {
        return computePathOp(LayoutPath.rootPath(this), LayoutPath::dereferenceHandle,
                Set.of(), elements);
    }

    /**
     * Creates a method handle which, given a memory segment, returns a {@linkplain MemorySegment#asSlice(long,long) slice}
     * corresponding to the layout selected by the given layout path, where the initial layout in the path is this layout.
     * <p>
     * The returned method handle has the following characteristics:
     * <ul>
     *     <li>its return type is {@code MemorySegment};</li>
     *     <li>it has a leading parameter of type {@code MemorySegment}, corresponding to the memory segment
     *     to be sliced;</li>
     *     <li>it has as zero or more parameters of type {@code long}, one for each <a href=#open-path-elements>open path element</a>
     *     in the provided layout path. The order of these parameters corresponds to the order in which the open path
     *     elements occur in the provided layout path.
     * </ul>
     * <p>
     * The offset of the returned segment is computed as follows:
     * {@snippet lang=java :
     * long offset = byteOffset(elements);
     * long size = select(elements).byteSize();
     * MemorySegment slice = segment.asSlice(offset, size);
     * }
     * <p>
     * The segment to be sliced must be <a href="MemorySegment.html#segment-alignment">aligned</a> according to the
     * {@linkplain #byteAlignment() alignment constraint} of the root layout (this layout). Note that this can be more
     * strict (but not less) than the alignment constraint of the selected value layout.
     *
     * @apiNote The returned method handle can be used to obtain a memory segment slice, similarly to {@link MemorySegment#asSlice(long, long)},
     * but more flexibly, as some indices can be specified when invoking the method handle.
     *
     * @param elements the layout path elements.
     * @return a method handle which is used to slice a memory segment at the offset selected by the given layout path.
     * @throws IllegalArgumentException if the layout path is not <a href="#well-formedness">well-formed</a> for this layout.
     * @throws IllegalArgumentException if the layout path contains one or more <a href=#deref-path-elements>dereference path elements</a>.
     */
    default MethodHandle sliceHandle(PathElement... elements) {
        return computePathOp(LayoutPath.rootPath(this), LayoutPath::sliceHandle,
                Set.of(PathKind.DEREF_ELEMENT), elements);
    }

    /**
     * Returns the layout selected from the provided path, where the initial layout in the path is this layout.
     *
     * @param elements the layout path elements.
     * @return the layout selected by the layout path in {@code elements}.
     * @throws IllegalArgumentException if the layout path is not <a href="#well-formedness">well-formed</a> for this layout.
     * @throws IllegalArgumentException if the layout path contains one or more <a href=#deref-path-elements>dereference path elements</a>.
     * @throws IllegalArgumentException if the layout path contains one or more path elements that select one or more
     * sequence element indices, such as {@link PathElement#sequenceElement(long)} and {@link PathElement#sequenceElement(long, long)}).
     */
    default MemoryLayout select(PathElement... elements) {
        return computePathOp(LayoutPath.rootPath(this), LayoutPath::layout,
                EnumSet.of(PathKind.SEQUENCE_ELEMENT_INDEX, PathKind.SEQUENCE_RANGE, PathKind.DEREF_ELEMENT), elements);
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
     * An element in a <a href="MemoryLayout.html#layout-paths"><em>layout path</em></a>. There
     * are three kinds of path elements:
     * <ul>
     *     <li><em>group path elements</em>, used to select a member layout within a {@link GroupLayout}, either by name or by index;</li>
     *     <li><em>sequence path elements</em>, used to select one or more sequence element layouts within a {@link SequenceLayout}; and</li>
     *     <li><em>dereference path elements</em>, used to <a href="MemoryLayout.html#deref-path-elements">dereference</a>
     *     an address layout as its target layout.</li>
     * </ul>
     * Sequence path elements selecting more than one sequence element layout are called
     * <a href="MemoryLayout.html#open-path-elements">open path elements</a>.
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
         *
         * @implSpec in case multiple group elements with a matching name exist, the path element returned by this
         * method will select the first one; that is, the group element with the lowest offset from current path is selected.
         * In such cases, using {@link #groupElement(long)} might be preferable.
         *
         * @param name the name of the member layout to be selected.
         * @return a path element which selects the group member layout with the given name.
         */
        static PathElement groupElement(String name) {
            Objects.requireNonNull(name);
            return new LayoutPath.PathElementImpl(PathKind.GROUP_ELEMENT,
                                                  path -> path.groupElement(name));
        }

        /**
         * Returns a path element which selects a member layout with the given index in a group layout.
         *
         * @param index the index of the member layout element to be selected.
         * @return a path element which selects the group member layout with the given index.
         * @throws IllegalArgumentException if {@code index < 0}.
         */
        static PathElement groupElement(long index) {
            if (index < 0) {
                throw new IllegalArgumentException("Index < 0");
            }
            return new LayoutPath.PathElementImpl(PathKind.GROUP_ELEMENT,
                    path -> path.groupElement(index));
        }

        /**
         * Returns a path element which selects the element layout at the specified position in a sequence layout.
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
         * Returns an <a href="MemoryLayout.html#open-path-elements">open path element</a> which selects the element
         * layout in a <em>range</em> of positions in a sequence layout. The range is expressed as a pair of starting
         * index (inclusive) {@code S} and step factor (which can also be negative) {@code F}.
         * <p>
         * The exact sequence element selected by this layout is expressed as an index {@code I}. If {@code C} is the
         * sequence element count, it follows that {@code 0 <= I < B}, where {@code B} is computed as follows:
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
         * Returns an <a href="MemoryLayout.html#open-path-elements">open path element</a> which selects an unspecified
         * element layout in a sequence layout.
         * <p>
         * The exact sequence element selected by this layout is expressed as an index {@code I}. If {@code C} is the
         * sequence element count, it follows that {@code 0 <= I < C}.
         *
         * @return a path element which selects an unspecified sequence element layout.
         */
        static PathElement sequenceElement() {
            return new LayoutPath.PathElementImpl(PathKind.SEQUENCE_ELEMENT,
                                                  LayoutPath::sequenceElement);
        }

        /**
         * Returns a path element which dereferences an address layout as its
         * {@linkplain AddressLayout#targetLayout() target layout} (where set).
         *
         * @return a path element which dereferences an address layout.
         */
        static PathElement dereferenceElement() {
            return new LayoutPath.PathElementImpl(PathKind.DEREF_ELEMENT,
                    LayoutPath::derefElement);
        }
    }

    /**
     * Compares the specified object with this layout for equality. Returns {@code true} if and only if the specified
     * object is also a layout, and it is equal to this layout. Two layouts are considered equal if they are of
     * the same kind, have the same size, name and alignment constraint. Furthermore, depending on the layout kind, additional
     * conditions must be satisfied:
     * <ul>
     *     <li>two value layouts are considered equal if they have the same {@linkplain ValueLayout#order() order},
     *     and {@linkplain ValueLayout#carrier() carrier}. Additionally, two address layouts are considered equal if they
     *     also have the same {@linkplain AddressLayout#targetLayout() target layout};</li>
     *     <li>two sequence layouts are considered equal if they have the same element count (see {@link SequenceLayout#elementCount()}), and
     *     if their element layouts (see {@link SequenceLayout#elementLayout()}) are also equal;</li>
     *     <li>two group layouts are considered equal if they are of the same type (see {@link StructLayout},
     *     {@link UnionLayout}) and if their member layouts (see {@link GroupLayout#memberLayouts()}) are also equal.</li>
     * </ul>
     *
     * @param other the object to be compared for equality with this layout.
     * @return {@code true} if the specified object is equal to this layout.
     */
    boolean equals(Object other);

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
     * Creates a padding layout with the given byte size. The alignment constraint of the returned layout
     * is 1. As such, regardless of its size, in the absence of an {@linkplain #withByteAlignment(long) explicit}
     * alignment constraint, a padding layout does not affect the natural alignment of the group or sequence layout
     * it is nested into.
     *
     * @param byteSize the padding size (expressed in bytes).
     * @return the new selector layout.
     * @throws IllegalArgumentException if {@code byteSize <= 0}.
     */
    static PaddingLayout paddingLayout(long byteSize) {
        return PaddingLayoutImpl.of(MemoryLayoutUtil.requireByteSizeValid(byteSize, false));
    }

    /**
     * Creates a sequence layout with the given element layout and element count.
     *
     * @param elementCount the sequence element count.
     * @param elementLayout the sequence element layout.
     * @return the new sequence layout with the given element layout and size.
     * @throws IllegalArgumentException if {@code elementCount} is negative.
     * @throws IllegalArgumentException if {@code elementLayout.byteSize() * elementCount} overflows.
     * @throws IllegalArgumentException if {@code elementLayout.byteSize() % elementLayout.byteAlignment() != 0}.
     */
    static SequenceLayout sequenceLayout(long elementCount, MemoryLayout elementLayout) {
        MemoryLayoutUtil.requireNonNegative(elementCount);
        Objects.requireNonNull(elementLayout);
        Utils.checkElementAlignment(elementLayout, "Element layout size is not multiple of alignment");
        return Utils.wrapOverflow(() ->
                SequenceLayoutImpl.of(elementCount, elementLayout));
    }

    /**
     * Creates a sequence layout with the given element layout and the maximum element
     * count such that it does not overflow a {@code long}.
     *
     * This is equivalent to the following code:
     * {@snippet lang = java:
     * sequenceLayout(Long.MAX_VALUE / elementLayout.byteSize(), elementLayout);
     * }
     *
     * @param elementLayout the sequence element layout.
     * @return a new sequence layout with the given element layout and maximum element count.
     * @throws IllegalArgumentException if {@code elementLayout.byteSize() % elementLayout.byteAlignment() != 0}.
     */
    static SequenceLayout sequenceLayout(MemoryLayout elementLayout) {
        Objects.requireNonNull(elementLayout);
        return sequenceLayout(Long.MAX_VALUE / elementLayout.byteSize(), elementLayout);
    }

    /**
     * Creates a struct layout with the given member layouts.
     *
     * @param elements The member layouts of the struct layout.
     * @return a struct layout with the given member layouts.
     * @throws IllegalArgumentException if the sum of the {@linkplain #byteSize() byte sizes} of the member layouts
     * overflows.
     * @throws IllegalArgumentException if a member layout in {@code elements} occurs at an offset (relative to the start
     * of the struct layout) which is not compatible with its alignment constraint.
     *
     * @apiNote This factory does not automatically align element layouts, by inserting additional {@linkplain PaddingLayout
     * padding layout} elements. As such, the following struct layout creation will fail with an exception:
     *
     * {@snippet lang = java:
     * structLayout(JAVA_SHORT, JAVA_INT);
     * }
     *
     * To avoid the exception, clients can either insert additional padding layout elements:
     *
     * {@snippet lang = java:
     * structLayout(JAVA_SHORT, MemoryLayout.paddingLayout(2), JAVA_INT);
     * }
     *
     * Or, alternatively, they can use a member layout which features a smaller alignment constraint. This will result
     * in a <em>packed</em> struct layout:
     *
     * {@snippet lang = java:
     * structLayout(JAVA_SHORT, JAVA_INT.withByteAlignment(2));
     * }
     */
    static StructLayout structLayout(MemoryLayout... elements) {
        Objects.requireNonNull(elements);
        return Utils.wrapOverflow(() ->
                StructLayoutImpl.of(Stream.of(elements)
                        .map(Objects::requireNonNull)
                        .toList()));
    }

    /**
     * Creates a union layout with the given member layouts.
     *
     * @param elements The member layouts of the union layout.
     * @return a union layout with the given member layouts.
     */
    static UnionLayout unionLayout(MemoryLayout... elements) {
        Objects.requireNonNull(elements);
        return UnionLayoutImpl.of(Stream.of(elements)
                .map(Objects::requireNonNull)
                .toList());
    }
}
