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
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import jdk.internal.foreign.LayoutPath;
import jdk.internal.foreign.LayoutPath.PathElementImpl.PathKind;
import jdk.internal.foreign.Utils;
import jdk.internal.foreign.layout.MemoryLayoutUtil;
import jdk.internal.foreign.layout.PaddingLayoutImpl;
import jdk.internal.foreign.layout.SequenceLayoutImpl;
import jdk.internal.foreign.layout.StructLayoutImpl;
import jdk.internal.foreign.layout.UnionLayoutImpl;

/**
 * A memory layout describes the contents of a memory segment.
 * <p>
 * There are two leaves in the layout hierarchy, {@linkplain ValueLayout value layouts},
 * which are used to represent values of given size and kind and
 * {@linkplain PaddingLayout padding layouts} which are used, as the name suggests, to
 * represent a portion of a memory segment whose contents should be ignored, and which
 * are primarily present for alignment reasons. Some common value layout constants, such
 * as {@link ValueLayout#JAVA_INT} and {@link ValueLayout#JAVA_FLOAT_UNALIGNED} are
 * defined in the {@link ValueLayout} class. A special kind of value layout, namely an
 * {@linkplain AddressLayout address layout}, is used to model values that denote the
 * address of a region of memory.
 * <p>
 * More complex layouts can be derived from simpler ones: a
 * {@linkplain SequenceLayout sequence layout} denotes a homogeneous repetition of zero
 * or more occurrences of an element layout; a {@linkplain GroupLayout group layout}
 * denotes a heterogeneous aggregation of zero or more member layouts. Group layouts
 * come in two flavors: {@linkplain StructLayout struct layouts}, where member layouts
 * are laid out one after the other, and {@linkplain UnionLayout union layouts} where
 * member layouts are laid out at the same starting offset.
 * <p>
 * Layouts can be optionally associated with a <em>name</em>. A layout name can be
 * referred to when constructing <a href="MemoryLayout.html#layout-paths"><em>layout paths</em></a>.
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
 * The above declaration can be modeled using a layout object, as follows:
 *
 * {@snippet lang=java :
 * SequenceLayout TAGGED_VALUES = MemoryLayout.sequenceLayout(5,
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
 *     associated with the value layout. That is, the constant {@link ValueLayout#JAVA_INT}
 *     has carrier {@code int}, and size of 4 bytes;</li>
 *     <li>The size of an address layout is platform-dependent. That is, the constant
 *     {@link ValueLayout#ADDRESS} has a size of 8 bytes on a 64-bit platform;</li>
 *     <li>The size of a padding layout is always provided explicitly, on
 *     {@linkplain MemoryLayout#paddingLayout(long) construction};</li>
 *     <li>The size of a sequence layout whose element layout is <em>E</em>
 *     and element count is <em>L</em>, is the size of <em>E</em>,
 *     multiplied by <em>L</em>;</li>
 *     <li>The size of a struct layout with member layouts <em>M1</em>, <em>M2</em>, ... <em>Mn</em>
 *     whose sizes are <em>S1</em>, <em>S2</em>, ... <em>Sn</em>, respectively,
 *     is <em>S1 + S2 + ... + Sn</em>;</li>
 *     <li>The size of a union layout <em>U</em> with member layouts
 *     <em>M1</em>, <em>M2</em>, ... <em>Mn</em> whose sizes are
 *     <em>S1</em>, <em>S2</em>, ... <em>Sn</em>, respectively, is <em>max(S1, S2, ... Sn).</em></li>
 * </ul>
 * <p>
 * Furthermore, all layouts have a <em>natural alignment</em> (expressed in bytes) which
 * is defined as follows:
 * <ul>
 *     <li>The natural alignment of a padding layout is 1;</li>
 *     <li>The natural alignment of a value layout whose size is <em>N</em> is
 *     <em>N</em>;</li>
 *     <li>The natural alignment of a sequence layout whose element layout is
 *     <em>E</em> is the alignment of <em>E</em>;</li>
 *     <li>The natural alignment of a group layout with member layouts
 *     <em>M1</em>, <em>M2</em>, ... <em>Mn</em> whose alignments are
 *     <em>A1</em>, <em>A2</em>, ... <em>An</em>, respectively, is <em>max(A1, A2 ... An)</em>.</li>
 * </ul>
 * A layout's alignment can be overridden if needed
 * (see {@link MemoryLayout#withByteAlignment(long)}), which can be useful to describe
 * layouts with weaker or stronger alignment constraints.
 *
 * <h2 id="layout-paths">Layout paths</h2>
 *
 * A <em>layout path</em> is used to unambiguously select a layout that is nested in some
 * other layout. Layout paths are typically expressed as a sequence of one or more
 * {@linkplain PathElement path elements}. (A more formal definition of layout paths is
 * provided <a href="#well-formedness">below</a>).
 * <p>
 * Layout paths can be used to:
 * <ul>
 *     <li>obtain {@linkplain MemoryLayout#byteOffset(PathElement...) offsets} of
 *     arbitrarily nested layouts;</li>
 *     <li>obtain a {@linkplain #varHandle(PathElement...) var handle} that can be used
 *     to access the value corresponding to the selected layout;</li>
 *     <li>{@linkplain #select(PathElement...) select} an arbitrarily nested layout.</li>
 * </ul>
 * <p>
 * For instance, given the {@code taggedValues} sequence layout constructed above, we can
 * obtain the offset, in bytes, of the member layout named <code>value</code> in the
 * <em>first</em> sequence element, as follows:
 * {@snippet lang=java :
 * long valueOffset = TAGGED_VALUES.byteOffset(PathElement.sequenceElement(0),
 *                                           PathElement.groupElement("value")); // yields 4
 * }
 *
 * Similarly, we can select the member layout named {@code value}, as follows:
 * {@snippet lang=java :
 * MemoryLayout value = TAGGED_VALUES.select(PathElement.sequenceElement(),
 *                                          PathElement.groupElement("value"));
 * }
 *
 * <h3 id="open-path-elements">Open path elements</h3>
 *
 * Some layout path elements, said <em>open path elements</em>, can select multiple
 * layouts at once. For instance, the open path elements
 * {@link PathElement#sequenceElement()}, {@link PathElement#sequenceElement(long, long)}
 * select an unspecified element in a sequence layout. A var handle derived from a
 * layout path containing one or more open path element features additional coordinates
 * of type {@code long}, which can be used by clients to <em>bind</em> the open elements
 * in the path:
 *
 * {@snippet lang=java :
 * VarHandle valueHandle = TAGGED_VALUES.varHandle(PathElement.sequenceElement(),
 *                                                PathElement.groupElement("value"));
 * MemorySegment taggedValues = ...
 * // reads the "value" field of the third struct in the array (taggedValues[2].value)
 * int val = (int) valueHandle.get(taggedValues,
 *         0L,  // base offset
 *         2L); // sequence index
 * }
 *
 * <p>
 * Open path elements also affect the creation of
 * {@linkplain #byteOffsetHandle(PathElement...) offset-computing method handles}. Each
 * open path element becomes an additional {@code long} parameter in the obtained method
 * handle. This parameter can be used to specify the index of the sequence element whose
 * offset is to be computed:
 *
 * {@snippet lang=java :
 * MethodHandle offsetHandle = TAGGED_VALUES.byteOffsetHandle(PathElement.sequenceElement(),
 *                                                           PathElement.groupElement("kind"));
 * long offset1 = (long) offsetHandle.invokeExact(0L, 1L); // 0 + (1 * 8) = 8
 * long offset2 = (long) offsetHandle.invokeExact(0L, 2L); // 0 + (2 * 8) = 16
 * }
 *
 * <h3 id="deref-path-elements">Dereference path elements</h3>
 *
 * A special kind of path element, called <em>dereference path element</em>, allows var
 * handles obtained from memory layouts to follow pointers. Consider the following layout:
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
 * This layout is a struct layout describing a rectangle. It contains a single field,
 * namely {@code points}, an address layout whose
 * {@linkplain AddressLayout#targetLayout() target layout} is a sequence layout of four
 * struct layouts. Each struct layout describes a two-dimensional point, and is defined
 * as a pair or {@link ValueLayout#JAVA_INT} coordinates, with names {@code x} and
 * {@code y}, respectively.
 * <p>
 * With dereference path elements, we can obtain a var handle that accesses the {@code y} coordinate of one of the
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
 * // dereferences the third point struct in the "points" array, and reads its "y" coordinate (rect.points[2]->y)
 * int rect_y_2 = (int) rectPointYs.get(rect,
 *     0L,  // base offset
 *     2L); // sequence index
 * }
 *
 * <h3 id="well-formedness">Layout path well-formedness</h3>
 *
 * A layout path is applied to a layout {@code C_0}, also called the
 * <em>initial layout</em>. Each path element in a layout path can be thought of as a
 * function that updates the current layout {@code C_i-1} to some other layout
 * {@code C_i}. That is, for each path element {@code E1, E2, ... En}, in a layout path
 * {@code P}, we compute {@code C_i = f_i(C_i-1)}, where {@code f_i} is the selection
 * function associated with the path element under consideration, denoted as {@code E_i}.
 * The final layout {@code C_i} is also called the <em>selected layout</em>.
 * <p>
 * A layout path {@code P} is considered well-formed for an initial layout {@code C_0}
 * if all its path elements {@code E1, E2, ... En} are well-formed for their
 * corresponding input layouts {@code C_0, C_1, ... C_n-1}. A path element {@code E} is
 * considered well-formed for a layout {@code L} if any of the following is true:
 * <ul>
 * <li>{@code L} is a sequence layout and {@code E} is a sequence path element
 * (one of {@link PathElement#sequenceElement(long)}, {@link PathElement#sequenceElement(long, long)}
 * or {@link PathElement#sequenceElement()}). Moreover, if {@code E} contains one or
 * more sequence indices, such indices have to be compatible with the sequence layout's
 * element count;</li>
 * <li>{@code L} is a group layout and {@code E} is a group path element (one of
 * {@link PathElement#groupElement(String)} or {@link PathElement#groupElement(long)}).
 * Moreover, the group path element must refer to a valid member layout in
 * {@code L}, either by name, or index;</li>
 * <li>{@code L} is an address layout and {@code E} is a {@linkplain PathElement#dereferenceElement()
 * dereference path element}.
 * Moreover, {@code L} must define some {@linkplain AddressLayout#targetLayout() target layout}.</li>
 * </ul>
 * Any attempt to provide a layout path {@code P} that is not well-formed for an initial
 * layout {@code C_0} will result in an {@link IllegalArgumentException}.
 *
 * <h2 id="access-mode-restrictions">Access mode restrictions</h2>
 *
 * A var handle returned by {@link #varHandle(PathElement...)} or
 * {@link ValueLayout#varHandle()} features certain access characteristics, which are
 * derived from the selected layout {@code L}:
 * <ul>
 * <li>A carrier type {@code T}, derived from {@code L.carrier()}</li>
 * <li>An alignment constraint {@code A}, derived from {@code L.byteAlignment()}</li>
 * <li>An access size {@code S}, derived from {@code L.byteSize()}</li>
 * </ul>
 * Depending on the above characteristics, the returned var handle might feature certain
 * <i>access mode restrictions</i>. We say that a var handle is <em>aligned</em> if its
 * alignment constraint {@code A} is compatible with the access size {@code S}, that is
 * if {@code A >= S}. An aligned var handle is guaranteed to support the following
 * access modes:
 * <ul>
 * <li>read write access modes for all {@code T}. On 32-bit platforms, access modes
 *     {@code get} and {@code set} for {@code long}, {@code double} and {@code MemorySegment}
 *     are supported but might lead to word tearing, as described in Section {@jls 17.7}.
 *     of <cite>The Java Language Specification</cite>.
 * <li>atomic update access modes for {@code int}, {@code long},
 *     {@code float}, {@code double} and {@link MemorySegment}.
 *     (Future major platform releases of the JDK may support additional
 *     types for certain currently unsupported access modes.)
 * <li>numeric atomic update access modes for {@code int}, {@code long} and {@link MemorySegment}.
 *     (Future major platform releases of the JDK may support additional
 *     numeric types for certain currently unsupported access modes.)
 * <li>bitwise atomic update access modes for {@code int}, {@code long} and {@link MemorySegment}.
 *     (Future major platform releases of the JDK may support additional
 *     numeric types for certain currently unsupported access modes.)
 * </ul>
 * If {@code T} is {@code float}, {@code double} or {@link MemorySegment} then atomic
 * update access modes compare values using their bitwise representation
 * (see {@link Float#floatToRawIntBits}, {@link Double#doubleToRawLongBits}
 * and {@link MemorySegment#address()}, respectively).
 * <p>
 * Alternatively, a var handle is <em>unaligned</em> if its alignment constraint {@code A}
 * is incompatible with the access size {@code S}, that is, if {@code A < S}. An
 * unaligned var handle only supports the {@code get} and {@code set} access modes. All
 * other access modes will result in {@link UnsupportedOperationException} being thrown.
 * Moreover, while supported, access modes {@code get} and {@code set} might lead to
 * word tearing.
 *
 * <h2 id="variable-length">Working with variable-length arrays</h2>
 *
 * We have seen how sequence layouts are used to describe the contents of an array whose
 * size is known <em>statically</em>. There are cases, however, where the array size is
 * only known <em>dynamically</em>. We call such arrays <em>variable-length arrays</em>.
 * There are two common kinds of variable-length arrays:
 * <ul>
 *     <li>a <em>toplevel</em> variable-length array whose size depends on the value of
 *     some unrelated variable, or parameter;</li>
 *     <li>an variable-length array <em>nested</em> in a struct, whose size depends on
 *     the value of some other field in the enclosing struct.</li>
 * </ul>
 * While variable-length arrays cannot be modeled directly using sequence layouts,
 * clients can still enjoy structured access to elements of variable-length arrays
 * using var handles as demonstrated in the following sections.
 *
 * <h3 id="variable-length-toplevel">Toplevel variable-length arrays</h3>
 *
 * Consider the following struct declaration in C:
 *
 * {@snippet lang=c :
 * typedef struct {
 *     int x;
 *     int y;
 * } Point;
 * }
 *
 * In the above code, a point is modeled as two coordinates ({@code x} and
 * {@code y} respectively). Now consider the following snippet of C code:
 *
 * {@snippet lang=c :
 * int size = ...
 * Point *points = (Point*)malloc(sizeof(Point) * size);
 * for (int i = 0 ; i < size ; i++) {
 *    ... points[i].x ...
 * }
 * }
 *
 * Here, we allocate an array of points ({@code points}). Crucially, the size of
 * the array is dynamically bound to the value of the {@code size} variable. Inside
 * the loop, the {@code x} coordinate of all the points in the array is accessed.
 * <p>
 * To model this code in Java, let's start by defining a layout for the {@code Point}
 * struct, as follows:
 *
 * {@snippet lang=java :
 * StructLayout POINT = MemoryLayout.structLayout(
 *             ValueLayout.JAVA_INT.withName("x"),
 *             ValueLayout.JAVA_INT.withName("y")
 * );
 * }
 *
 * Since we know we need to create and access an array of points, it would be tempting to
 * create a sequence layout modelling the variable-length array, and then derive the
 * necessary access var handles from the sequence layout. But this approach is
 * problematic, as the size of the variable-length array is not known. Instead, a
 * var handle that provides structured access to the elements of a variable-length array
 * can be obtained directly from the layout describing the array elements
 * (e.g. the point layout), as demonstrated below:
 *
 * {@snippet lang=java :
 * VarHandle POINT_ARR_X = POINT.arrayElementVarHandle(PathElement.groupElement("x"));
 *
 * int size = ...
 * MemorySegment points = ...
 * for (int i = 0 ; i < size ; i++) {
 *     ... POINT_ARR_X.get(segment, 0L, (long)i) ...
 * }
 * }
 *
 * Here, the coordinate {@code x} of subsequent point in the array is accessed using the
 * {@code POINT_ARR_X} var handle, which is obtained using the
 * {@link #arrayElementVarHandle(PathElement...)} method. This var handle features two
 * {@code long} coordinates: the first is a base offset (set to {@code 0L}), while the
 * second is a logical index that can be used to stride over all the elements of the
 * point array.
 * <p>
 * The base offset coordinate allows clients to express complex access operations, by
 * injecting additional offset computation into the var handle (we will see an example
 * of that below). In cases where the base offset is constant (as in the previous
 * example) clients can, if desired, drop the base offset parameter and make the access
 * expression simpler. This is achieved using the
 * {@link java.lang.invoke.MethodHandles#insertCoordinates(VarHandle, int, Object...)}
 * var handle adapter.
 *
 * <h3 id="variable-length-nested">Nested variable-length arrays</h3>
 *
 * Consider the following struct declaration in C:
 *
 * {@snippet lang=c :
 * typedef struct {
 *     int size;
 *     Point points[];
 * } Polygon;
 * }
 *
 * In the above code, a polygon is modeled as a size (the number of edges in the polygon)
 * and an array of points (one for each vertex in the polygon). The number of vertices
 * depends on the number of edges in the polygon. As such, the size of the {@code points}
 * array is left <em>unspecified</em> in the C declaration, using a
 * <em>Flexible Array Member</em> (a feature standardized in C99).
 * <p>
 * Again, clients can perform structured access to elements in the nested variable-length
 * array using the {@link #arrayElementVarHandle(PathElement...)} method, as demonstrated
 * below:
 *
 * {@snippet lang=java :
 * StructLayout POLYGON = MemoryLayout.structLayout(
 *             ValueLayout.JAVA_INT.withName("size"),
 *             MemoryLayout.sequenceLayout(0, POINT).withName("points")
 * );
 *
 * VarHandle POLYGON_SIZE = POLYGON.varHandle(0, PathElement.groupElement("size"));
 * long POINTS_OFFSET = POLYGON.byteOffset(PathElement.groupElement("points"));
 * }
 *
 * The {@code POLYGON} layout contains a sequence layout of size <em>zero</em>. The
 * element layout of the sequence layout is the {@code POINT} layout, shown previously.
 * The polygon layout is used to obtain a var handle that provides access to the polygon
 * size, as well as an offset ({@code POINTS_OFFSET}) to the start of the variable-length
 * {@code points} array.
 * <p>
 * The {@code x} coordinates of all the points in a polygon can then be accessed as
 * follows:
 * {@snippet lang=java :
 * MemorySegment polygon = ...
 * int size = POLYGON_SIZE.get(polygon, 0L);
 * for (int i = 0 ; i < size ; i++) {
 *     ... POINT_ARR_X.get(polygon, POINTS_OFFSET, (long)i) ...
 * }
 *  }
 * Here, we first obtain the polygon size, using the {@code POLYGON_SIZE} var handle.
 * Then, in a loop, we read the {@code x} coordinates of all the points in the polygon.
 * This is done by providing a custom offset (namely, {@code POINTS_OFFSET}) to the
 * offset coordinate of the {@code POINT_ARR_X} var handle. As before, the loop
 * induction variable {@code i} is passed as the index of the {@code POINT_ARR_X}
 * var handle, to stride over all the elements of the variable-length array.
 *
 * @implSpec
 * Implementations of this interface are immutable, thread-safe and
 * <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 *
 * @sealedGraph
 * @since 22
 */
public sealed interface MemoryLayout
        permits SequenceLayout, GroupLayout, PaddingLayout, ValueLayout {

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
     * {@return a memory layout with the same characteristics as this layout, but with
     *          the given name}
     *
     * @param name the layout name
     * @see MemoryLayout#name()
     */
    MemoryLayout withName(String name);

    /**
     * {@return a memory layout with the same characteristics as this layout, but with
     *          no name}
     *
     * @apiNote This can be useful to compare two layouts that have different names, but
     *          are otherwise equal.
     * @see MemoryLayout#name()
     */
    MemoryLayout withoutName();

    /**
     * {@return the alignment constraint associated with this layout, expressed in bytes}
     * <p>
     * Layout alignment defines a power of two {@code A} which is the byte-wise alignment
     * of the layout, where {@code A} is the number of bytes that must be aligned for any
     * pointer that correctly points to this layout. Thus:
     *
     * <ul>
     * <li>{@code A=1} means unaligned (in the usual sense), which is common in packets.</li>
     * <li>{@code A=8} means word aligned (on LP64), {@code A=4} int aligned,
     * {@code A=2} short aligned, etc.</li>
     * <li>{@code A=64} is the most strict alignment required by the x86/SV ABI
     * (for AVX-512 data).</li>
     * </ul>
     *
     * If no explicit alignment constraint was set on this layout (
     * see {@link #withByteAlignment(long)}), then this method returns the
     * <a href="#layout-align">natural alignment</a> constraint (in bytes) associated
     * with this layout.
     */
    long byteAlignment();

    /**
     * {@return a memory layout with the same characteristics as this layout, but with
     *          the given alignment constraint (in bytes)}
     *
     * @param byteAlignment the layout alignment constraint, expressed in bytes
     * @throws IllegalArgumentException if {@code byteAlignment} is not a power of two
     */
    MemoryLayout withByteAlignment(long byteAlignment);

    /**
     * {@return {@code offset + (byteSize() * index)}}
     *
     * @param offset the base offset
     * @param index the index to be scaled by the byte size of this layout
     * @throws IllegalArgumentException if {@code offset} or {@code index} is negative
     * @throws ArithmeticException if either the addition or multiplication overflows
     */
    long scale(long offset, long index);

    /**
     *{@return a method handle that can be used to invoke {@link #scale(long, long)}
     *         on this layout}
     */
    MethodHandle scaleHandle();

    /**
     * Computes the offset, in bytes, of the layout selected by the given layout path,
     * where the initial layout in the path is this layout.
     *
     * @param elements the layout path elements
     * @return The offset, in bytes, of the layout selected by the layout path in
     *         {@code elements}
     * @throws IllegalArgumentException if the layout path is not
     *         <a href="#well-formedness">well-formed</a> for this layout
     * @throws IllegalArgumentException if the layout path contains one or more
     *         <a href=#open-path-elements>open path elements</a>
     * @throws IllegalArgumentException if the layout path contains one or more
     *         <a href=#deref-path-elements>dereference path elements</a>
     */
    long byteOffset(PathElement... elements);

    /**
     * Creates a method handle that computes the offset, in bytes, of the layout selected
     * by the given layout path, where the initial layout in the path is this layout.
     * <p>
     * The returned method handle has the following characteristics:
     * <ul>
     *     <li>its return type is {@code long};</li>
     *     <li>it has one leading {@code long} parameter representing the base offset;</li>
     *     <li>it has as zero or more trailing parameters of type {@code long}, one for
     *     each <a href=#open-path-elements>open path element</a> in the provided layout
     *     path. The order of these parameters corresponds to the order in which the
     *     open path elements occur in the provided layout path.
     * </ul>
     * <p>
     * The final offset returned by the method handle is computed as follows:
     *
     * <blockquote><pre>{@code
     * offset = b + c_1 + c_2 + ... + c_m + (x_1 * s_1) + (x_2 * s_2) + ... + (x_n * s_n)
     * }</pre></blockquote>
     *
     * where {@code b} represents the base offset provided as a <em>dynamic</em>
     * {@code long} argument, {@code x_1}, {@code x_2}, ... {@code x_n} represent indices
     * into sequences provided as <em>dynamic</em> {@code long} arguments, whereas
     * {@code s_1}, {@code s_2}, ... {@code s_n} are <em>static</em> stride constants
     * derived from the size of the element layout of a sequence, and
     * {@code c_1}, {@code c_2}, ... {@code c_m} are other <em>static</em> offset
     * constants (such as field offsets) which are derived from the layout path.
     *
     * @apiNote The returned method handle can be used to compute a layout offset,
     *          similarly to {@link #byteOffset(PathElement...)}, but more flexibly, as
     *          some indices can be specified when invoking the method handle.
     *
     * @param elements the layout path elements
     * @return a method handle that computes the offset, in bytes, of the layout selected
     *         by the given layout path
     * @throws IllegalArgumentException if the layout path is not
     *         <a href="#well-formedness">well-formed</a> for this layout
     * @throws IllegalArgumentException if the layout path contains one or more
     *         <a href=#deref-path-elements>dereference path elements</a>
     */
    MethodHandle byteOffsetHandle(PathElement... elements);

    /**
     * Creates a var handle that accesses a memory segment at the offset selected by the
     * given layout path, where the initial layout in the path is this layout.
     * <p>
     * The returned var handle has the following characteristics:
     * <ul>
     *     <li>its type is derived from the {@linkplain ValueLayout#carrier() carrier} of the
     *     selected value layout;</li>
     *     <li>it has a leading parameter of type {@code MemorySegment} representing the
     *     accessed segment</li>
     *     <li>a following {@code long} parameter, corresponding to the base offset,
     *     denoted as {@code B};</li>
     *     <li>it has zero or more trailing access coordinates of type {@code long},
     *     one for each <a href=#open-path-elements>open path element</a> in the provided
     *     layout path, denoted as {@code I1, I2, ... In}, respectively. The order of
     *     these access coordinates corresponds to the order in which the open path
     *     elements occur in the provided layout path.
     * </ul>
     * <p>
     * If the provided layout path {@code P} contains no dereference elements, then the
     * offset {@code O} of the access operation is computed as follows:
     *
     * {@snippet lang = "java":
     * O = this.offsetHandle(P).invokeExact(B, I1, I2, ... In);
     * }
     * <p>
     * Accessing a memory segment using the var handle returned by this method is subject
     * to the following checks:
     * <ul>
     *     <li>The physical address of the accessed memory segment must be
     *     <a href="MemorySegment.html#segment-alignment">aligned</a> according to the
     *     {@linkplain #byteAlignment() alignment constraint} of the root layout
     *     (this layout), or an {@link IllegalArgumentException} is thrown. Note
     *     that the alignment constraint of the root layout can be more strict
     *     (but not less) than the alignment constraint of the selected value layout.</li>
     *     <li>The offset of the access operation (computed as above) must fall inside
     *     the spatial bounds of the accessed memory segment, or an
     *     {@link IndexOutOfBoundsException} is thrown. This is the case when
     *     {@code O + A <= S}, where {@code O} is the accessed offset (computed as above),
     *     {@code A} is the size of the selected layout and {@code S} is the size of the
     *     accessed memory segment.</li>
     *     <li>The accessed memory segment must be
     *     {@link MemorySegment#isAccessibleBy(Thread) accessible} from the thread
     *     performing the access operation, or a {@link WrongThreadException} is thrown.</li>
     *     <li>For write operations, the accessed memory segment must not be
     *     {@link MemorySegment#isReadOnly() read only}, or an
     *     {@link IllegalArgumentException} is thrown.</li>
     *     <li>The {@linkplain MemorySegment#scope() scope} associated with the accessed
     *     segment must be {@linkplain MemorySegment.Scope#isAlive() alive}, or an
     *     {@link IllegalStateException} is thrown.</li>
     * </ul>
     * <p>
     * If the selected layout is an {@linkplain AddressLayout address layout}, calling
     * {@link VarHandle#get(Object...)} on the returned var handle will return a new
     * memory segment. The segment is associated with the global scope. Moreover, the
     * size of the segment depends on whether the address layout has a
     * {@linkplain AddressLayout#targetLayout() target layout}. More specifically:
     * <ul>
     *     <li>If the address layout has a target layout {@code T}, then the size
     *     of the returned segment is {@code T.byteSize()};</li>
     *     <li>Otherwise, the address layout has no target layout and the size
     *     of the returned segment
     *     is <a href="MemorySegment.html#wrapping-addresses">zero</a>.</li>
     * </ul>
     * Moreover, if the selected layout is an {@linkplain AddressLayout address layout},
     * calling {@link VarHandle#set(Object...)} can throw {@link IllegalArgumentException}
     * if the memory segment representing the address to be written is not a
     * {@linkplain MemorySegment#isNative() native} memory segment.
     * <p>
     * If the provided layout path has size {@code m} and contains a dereference path
     * element in position {@code k} (where {@code k <= m}) then two layout paths
     * {@code P} and {@code P'} are derived, where P contains all the path elements from
     * 0 to {@code k - 1} and {@code P'} contains all the path elements from {@code k + 1}
     * to {@code m} (if any). Then, the returned var handle is computed as follows:
     *
     * {@snippet lang = "java":
     * VarHandle baseHandle = this.varHandle(P);
     * MemoryLayout target = ((AddressLayout)this.select(P)).targetLayout().get();
     * VarHandle targetHandle = target.varHandle(P);
     * targetHandle = MethodHandles.insertCoordinates(targetHandle, 1, 0L); // always access nested targets at offset 0
     * targetHandle = MethodHandles.collectCoordinates(targetHandle, 0,
     *         baseHandle.toMethodHandle(VarHandle.AccessMode.GET));
     * }
     *
     * (The above can be trivially generalized to cases where the provided layout path
     * contains more than one dereference path elements).
     * <p>
     * As an example, consider the memory layout expressed by a {@link GroupLayout}
     * instance constructed as follows:
     * {@snippet lang = "java":
     *     GroupLayout grp = java.lang.foreign.MemoryLayout.structLayout(
     *             MemoryLayout.paddingLayout(4),
     *             ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN).withName("value")
     *     );
     * }
     * To access the member layout named {@code value}, we can construct a var handle as
     * follows:
     * {@snippet lang = "java":
     *     VarHandle handle = grp.varHandle(PathElement.groupElement("value")); //(MemorySegment, long) -> int
     * }
     *
     * @apiNote The resulting var handle features certain
     * <a href="#access-mode-restrictions"><em>access mode restrictions</em></a>, which
     * are common to all var handles derived from memory layouts.
     *
     * @param elements the layout path elements
     * @return a var handle that accesses a memory segment at the offset selected by the
     *         given layout path
     * @throws IllegalArgumentException if the layout path is not
     *         <a href="#well-formedness">well-formed</a> for this layout
     * @throws IllegalArgumentException if the layout selected by the provided path is not a
     *         {@linkplain ValueLayout value layout}
     */
    VarHandle varHandle(PathElement... elements);

    /**
     * Creates a var handle that accesses adjacent elements in a memory segment at
     * offsets selected by the given layout path, where the accessed elements have this
     * layout, and where the initial layout in the path is this layout.
     * <p>
     * The returned var handle has the following characteristics:
     * <ul>
     *     <li>its type is derived from the {@linkplain ValueLayout#carrier() carrier} of the
     *     selected value layout;</li>
     *     <li>it has a leading parameter of type {@code MemorySegment} representing
     *     the accessed segment</li>
     *     <li>a following {@code long} parameter, corresponding to the base offset,
     *     denoted as {@code B};</li>
     *     <li>a following {@code long} parameter, corresponding to the array index,
     *     denoted as {@code I0}. The array index is used to scale the accessed offset
     *     by this layout size;</li>
     *     <li>it has zero or more trailing access coordinates of type {@code long},
     *     one for each <a href=#open-path-elements>open path element</a> in the provided
     *     layout path, denoted as {@code I1, I2, ... In}, respectively. The order of
     *     these access coordinates corresponds to the order in which the open path
     *     elements occur in the provided layout path.
     * </ul>
     * <p>
     * If the provided layout path {@code P} contains no dereference elements, then the
     * offset {@code O} of the access operation is computed as follows:
     *
     * {@snippet lang = "java":
     * O = this.offsetHandle(P).invokeExact(this.scale(B, I0), I1, I2, ... In);
     * }
     * <p>
     * More formally, this method can be obtained from the {@link #varHandle(PathElement...)},
     * as follows:
     * {@snippet lang = "java":
     * MethodHandles.collectCoordinates(varHandle(elements), 1, scaleHandle())
     * }
     *
     * @apiNote
     * As the leading index coordinate {@code I0} is not bound by any sequence layout, it
     * can assume <em>any</em> non-negative value - provided that the resulting offset
     * computation does not overflow, or that the computed offset does not fall outside
     * the spatial bound of the accessed memory segment. As such, the var handles
     * returned from this method can be especially useful when accessing
     * <a href="#variable-length">variable-length arrays</a>.
     *
     * @param elements the layout path elements
     * @return a var handle that accesses adjacent elements in a memory segment at
     *         offsets selected by the given layout path
     * @throws IllegalArgumentException if the layout path is not
     *         <a href="#well-formedness">well-formed</a> for this layout
     * @throws IllegalArgumentException if the layout selected by the provided path is
     *         not a {@linkplain ValueLayout value layout}
     */
    VarHandle arrayElementVarHandle(PathElement... elements);

    /**
     * Creates a method handle which, given a memory segment, returns a
     * {@linkplain MemorySegment#asSlice(long, long) slice} corresponding to
     * the layout selected by the given layout path, where the initial layout in
     * the path is this layout.
     * <p>
     * The returned method handle has the following characteristics:
     * <ul>
     *     <li>its return type is {@code MemorySegment};</li>
     *     <li>it has a leading parameter of type {@code MemorySegment} corresponding to
     *     the memory segment to be sliced</li>
     *     <li>a following {@code long} parameter, corresponding to the base offset</li>
     *     <li>it has as zero or more trailing parameters of type {@code long}, one for
     *     each <a href=#open-path-elements>open path element</a> in the provided
     *     layout path. The order of these parameters corresponds to the order in which
     *     the open path elements occur in the provided layout path.
     * </ul>
     * <p>
     * The offset {@code O} of the returned segment is computed as if by a call to a
     * {@linkplain #byteOffsetHandle(PathElement...) byte offset handle} constructed
     * using the given path elements.
     * <p>
     * Computing a slice of a memory segment using the method handle returned by this
     * method is subject to the following checks:
     * <ul>
     *     <li>The physical address of the accessed memory segment must be
     *     <a href="MemorySegment.html#segment-alignment">aligned</a> according to the
     *     {@linkplain #byteAlignment() alignment constraint} of the root layout
     *     (this layout), or an {@link IllegalArgumentException} will be issued. Note
     *     that the alignment constraint of the root layout can be more strict
     *     (but not less) than the alignment constraint of the selected layout.</li>
     *     <li>The start offset of the slicing operation (computed as above) must fall
     *     inside the spatial bounds of the accessed memory segment, or an
     *     {@link IndexOutOfBoundsException} is thrown. This is the case when
     *     {@code O + A <= S}, where {@code O} is the start offset of
     *     the slicing operation (computed as above), {@code A} is the size of the
     *     selected layout and {@code S} is the size of the accessed memory segment.</li>
     * </ul>
     *
     * @apiNote The returned method handle can be used to obtain a memory segment slice,
     *          similarly to {@link MemorySegment#asSlice(long, long)}, but more flexibly,
     *          as some indices can be specified when invoking the method handle.
     *
     * @param elements the layout path elements
     * @return a method handle that is used to slice a memory segment at
     *         the offset selected by the given layout path
     * @throws IllegalArgumentException if the layout path is not
     *         <a href="#well-formedness">well-formed</a> for this layout
     * @throws IllegalArgumentException if the layout path contains one or more
     *         <a href=#deref-path-elements>dereference path elements</a>
     */
    MethodHandle sliceHandle(PathElement... elements);

    /**
     * Returns the layout selected from the provided path, where the initial layout in
     * the path is this layout.
     *
     * @param elements the layout path elements
     * @return the layout selected by the layout path in {@code elements}
     * @throws IllegalArgumentException if the layout path is not
     *         <a href="#well-formedness">well-formed</a> for this layout
     * @throws IllegalArgumentException if the layout path contains one or more
     *         <a href=#deref-path-elements>dereference path elements</a>
     * @throws IllegalArgumentException if the layout path contains one or more path
     *         elements that select one or more sequence element indices, such as
     *         {@link PathElement#sequenceElement(long)} and
     *         {@link PathElement#sequenceElement(long, long)})
     */
    MemoryLayout select(PathElement... elements);

    /**
     * An element in a <a href="MemoryLayout.html#layout-paths"><em>layout path</em></a>.
     * There are three kinds of path elements:
     * <ul>
     *     <li><em>group path elements</em>, used to select a member layout within a
     *     {@link GroupLayout}, either by name or by index;</li>
     *     <li><em>sequence path elements</em>, used to select one or more
     *     sequence element layouts within a {@link SequenceLayout}; and</li>
     *     <li><em>dereference path elements</em>, used to
     *     <a href="MemoryLayout.html#deref-path-elements">dereference</a> an address
     *     layout as its target layout.</li>
     * </ul>
     * Sequence path elements selecting more than one sequence element layout are called
     * <a href="MemoryLayout.html#open-path-elements">open path elements</a>.
     *
     * @implSpec
     * Implementations of this interface are immutable, thread-safe and
     * <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
     *
     * @since 22
     */
    sealed interface PathElement permits LayoutPath.PathElementImpl {

        /**
         * {@return a path element which selects a member layout with the given name in a
         *          group layout}
         *
         * @implSpec in case multiple group elements with a matching name exist, the path
         *           element returned by this method will select the first one; that is,
         *           the group element with the lowest offset from the current path is
         *           selected. In such cases, using {@link #groupElement(long)} might be
         *           preferable.
         *
         * @param name the name of the member layout to be selected
         */
        static PathElement groupElement(String name) {
            Objects.requireNonNull(name);
            return new LayoutPath.PathElementImpl(PathKind.GROUP_ELEMENT,
                                                  path -> path.groupElement(name),
                                                  "groupElement(\"" + name + "\")");
        }

        /**
         * {@return a path element that selects a member layout with the given index in a
         * group layout}
         *
         * @param index the index of the member layout element to be selected
         * @throws IllegalArgumentException if {@code index < 0}
         */
        static PathElement groupElement(long index) {
            if (index < 0) {
                throw new IllegalArgumentException("Index < 0");
            }
            return new LayoutPath.PathElementImpl(PathKind.GROUP_ELEMENT,
                                                  path -> path.groupElement(index),
                                                  "groupElement(" + index + ")");
        }

        /**
         * {@return a path element which selects the element layout at the specified
         *          index in a sequence layout}
         *
         * @param index the index of the sequence element to be selected
         * @throws IllegalArgumentException if {@code index < 0}
         */
        static PathElement sequenceElement(long index) {
            if (index < 0) {
                throw new IllegalArgumentException("Index must be positive: " + index);
            }
            return new LayoutPath.PathElementImpl(PathKind.SEQUENCE_ELEMENT_INDEX,
                                                  path -> path.sequenceElement(index),
                                                  "sequenceElement(" + index + ")");
        }

        /**
         * Returns an <a href="MemoryLayout.html#open-path-elements">open path element</a>
         * that selects the element layout in a <em>range</em> of positions in a sequence
         * layout. The range is expressed as a pair of starting index (inclusive)
         * {@code S} and step factor (which can also be negative) {@code F}.
         * <p>
         * The exact sequence element selected by this layout is expressed as an index
         * {@code I}. If {@code C} is the
         * sequence element count, it follows that {@code 0 <= I < B}, where {@code B}
         * is computed as follows:
         * <ul>
         *    <li>if {@code F > 0}, then {@code B = ceilDiv(C - S, F)}</li>
         *    <li>if {@code F < 0}, then {@code B = ceilDiv(-(S + 1), -F)}</li>
         * </ul>
         *
         * @param start the index of the first sequence element to be selected
         * @param step the step factor at which subsequence sequence elements are to be
         *             selected
         * @return a path element that selects the sequence element layout with the
         *         given index.
         * @throws IllegalArgumentException if {@code start < 0}, or {@code step == 0}
         */
        static PathElement sequenceElement(long start, long step) {
            if (start < 0) {
                throw new IllegalArgumentException("Start index must be positive: " + start);
            }
            if (step == 0) {
                throw new IllegalArgumentException("Step must be != 0: " + step);
            }
            return new LayoutPath.PathElementImpl(PathKind.SEQUENCE_RANGE,
                                                  path -> path.sequenceElement(start, step),
                                                  "sequenceElement(" + start + ", " + step + ")");
        }

        /**
         * {@return an <a href="MemoryLayout.html#open-path-elements">open path element</a>
         * that selects an unspecified element layout in a sequence layout}
         * <p>
         * The exact sequence element selected by this layout is expressed as an index
         * {@code I}. If {@code C} is the sequence element count, it follows that
         * {@code 0 <= I < C}.
         */
        static PathElement sequenceElement() {
            return new LayoutPath.PathElementImpl(PathKind.SEQUENCE_ELEMENT,
                                                  LayoutPath::sequenceElement,
                                                  "sequenceElement()");
        }

        /**
         * {@return a path element that dereferences an address layout as its
         * {@linkplain AddressLayout#targetLayout() target layout} (where set)}
         */
        static PathElement dereferenceElement() {
            return new LayoutPath.PathElementImpl(PathKind.DEREF_ELEMENT,
                                                  LayoutPath::derefElement,
                                                  "dereferenceElement()");
        }
    }

    /**
     * Compares the specified object with this layout for equality. Returns {@code true}
     * if and only if the specified object is also a layout, and it is equal to this
     * layout. Two layouts are considered equal if they are of the same kind, have the
     * same size, name and alignment constraint. Furthermore, depending on the
     * layout kind, additional conditions must be satisfied:
     * <ul>
     *     <li>two value layouts are considered equal if they have the same
     *     {@linkplain ValueLayout#order() order}, and
     *     {@linkplain ValueLayout#carrier() carrier}. Additionally, two address
     *     layouts are considered equal if they also have the same
     *     {@linkplain AddressLayout#targetLayout() target layout};</li>
     *     <li>two sequence layouts are considered equal if they have the same element
     *     count (see {@link SequenceLayout#elementCount()}), and if their element
     *     layouts (see {@link SequenceLayout#elementLayout()}) are also equal;</li>
     *     <li>two group layouts are considered equal if they are of the same type
     *     (see {@link StructLayout}, {@link UnionLayout}) and if their member layouts
     *     (see {@link GroupLayout#memberLayouts()}) are also equal.</li>
     * </ul>
     *
     * @param other the object to be compared for equality with this layout
     * @return {@code true} if the specified object is equal to this layout
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
     * Creates a padding layout with the given byte size. The alignment constraint of the
     * returned layout is 1. As such, regardless of its size, in the absence of an
     * {@linkplain #withByteAlignment(long) explicit} alignment constraint, a padding
     * layout does not affect the natural alignment of the group or sequence layout it is
     * nested into.
     *
     * @param byteSize the padding size (expressed in bytes)
     * @return the new selector layout
     * @throws IllegalArgumentException if {@code byteSize <= 0}
     */
    static PaddingLayout paddingLayout(long byteSize) {
        return PaddingLayoutImpl.of(MemoryLayoutUtil.requireByteSizeValid(byteSize, false));
    }

    /**
     * Creates a sequence layout with the given element layout and element count.
     *
     * @param elementCount the sequence element count
     * @param elementLayout the sequence element layout
     * @return the new sequence layout with the given element layout and size
     * @throws IllegalArgumentException if {@code elementCount} is negative
     * @throws IllegalArgumentException if {@code elementLayout.byteSize() * elementCount}
     *         overflows
     * @throws IllegalArgumentException if {@code elementLayout.byteSize() % elementLayout.byteAlignment() != 0}
     */
    static SequenceLayout sequenceLayout(long elementCount, MemoryLayout elementLayout) {
        Utils.checkNonNegativeArgument(elementCount, "elementCount");
        Objects.requireNonNull(elementLayout);
        Utils.checkElementAlignment(elementLayout,
                "Element layout size is not multiple of alignment");
        return Utils.wrapOverflow(() ->
                SequenceLayoutImpl.of(elementCount, elementLayout));
    }

    /**
     * Creates a struct layout with the given member layouts.
     *
     * @param elements The member layouts of the struct layout
     * @return a struct layout with the given member layouts
     * @throws IllegalArgumentException if the sum of the {@linkplain #byteSize() byte sizes}
     *         of the member layouts overflows
     * @throws IllegalArgumentException if a member layout in {@code elements} occurs at
     *         an offset (relative to the start of the struct layout) which is not
     *         compatible with its alignment constraint
     *
     * @apiNote This factory does not automatically align element layouts, by inserting
     *          additional {@linkplain PaddingLayout padding layout} elements. As such,
     *          the following struct layout creation will fail with an exception:
     *
     * {@snippet lang = java:
     * structLayout(JAVA_SHORT, JAVA_INT);
     * }
     *
     * To avoid the exception, clients can either insert additional padding layout
     * elements:
     *
     * {@snippet lang = java:
     * structLayout(JAVA_SHORT, MemoryLayout.paddingLayout(2), JAVA_INT);
     * }
     *
     * Or, alternatively, they can use a member layout that features a smaller alignment
     * constraint. This will result in a <em>packed</em> struct layout:
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
     * @param elements The member layouts of the union layout
     * @return a union layout with the given member layouts
     */
    static UnionLayout unionLayout(MemoryLayout... elements) {
        Objects.requireNonNull(elements);
        return UnionLayoutImpl.of(Stream.of(elements)
                .map(Objects::requireNonNull)
                .toList());
    }
}
