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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

import jdk.internal.foreign.layout.ValueLayouts;
import jdk.internal.javac.PreviewFeature;
import jdk.internal.reflect.CallerSensitive;

/**
 * A layout that models values of basic data types. Examples of values modelled by a value layout are
 * <em>integral</em> values (either signed or unsigned), <em>floating-point</em> values and
 * <em>address</em> values.
 * <p>
 * Each value layout has a size, an alignment (in bits),
 * a {@linkplain ByteOrder byte order}, and a <em>carrier</em>, that is, the Java type that should be used when
 * {@linkplain MemorySegment#get(OfInt, long) accessing} a region of memory using the value layout.
 * <p>
 * This class defines useful value layout constants for Java primitive types and addresses.
 * The layout constants in this class make implicit alignment and byte-ordering assumption: all layout
 * constants in this class are byte-aligned, and their byte order is set to the {@linkplain ByteOrder#nativeOrder() platform default},
 * thus making it easy to work with other APIs, such as arrays and {@link java.nio.ByteBuffer}.
 *
 * @implSpec implementing classes and subclasses are immutable, thread-safe and <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 *
 * @since 19
 */
@PreviewFeature(feature=PreviewFeature.Feature.FOREIGN)
public sealed interface ValueLayout extends MemoryLayout {

    /**
     * {@return the value's byte order}
     */
    ByteOrder order();

    /**
     * Returns a value layout with the same carrier, alignment constraint and name as this value layout,
     * but with the specified byte order.
     *
     * @param order the desired byte order.
     * @return a value layout with the given byte order.
     */
    ValueLayout withOrder(ByteOrder order);

    /**
     * Creates a <em>strided</em> var handle that can be used to access a memory segment as multi-dimensional
     * array. The layout of this array is a sequence layout with {@code shape.length} nested sequence layouts. The element
     * layout of the sequence layout at depth {@code shape.length} is this value layout.
     * As a result, if {@code shape.length == 0}, the array layout will feature only one dimension.
     * <p>
     * The resulting var handle will feature {@code sizes.length + 1} coordinates of type {@code long}, which are
     * used as indices into a multi-dimensional array.
     * <p>
     * For instance, the following method call:
     *
     * {@snippet lang=java :
     * VarHandle arrayHandle = ValueLayout.JAVA_INT.arrayElementVarHandle(10, 20);
     * }
     *
     * Can be used to access a multi-dimensional array whose layout is as follows:
     *
     * {@snippet lang = java:
     * SequenceLayout arrayLayout = MemoryLayout.sequenceLayout(
     *                                      MemoryLayout.sequenceLayout(10,
     *                                                  MemoryLayout.sequenceLayout(20, ValueLayout.JAVA_INT)));
     *}
     *
     * The resulting var handle {@code arrayHandle} will feature 3 coordinates of type {@code long}; each coordinate
     * is interpreted as an index into the corresponding sequence layout. If we refer to the var handle coordinates, from left
     * to right, as {@code x}, {@code y} and {@code z} respectively, the final offset accessed by the var handle can be
     * computed with the following formula:
     *
     * <blockquote><pre>{@code
     * offset = (10 * 20 * 4 * x) + (20 * 4 * y) + (4 * z)
     * }</pre></blockquote>
     *
     * Additionally, the values of {@code x}, {@code y} and {@code z} are constrained as follows:
     * <ul>
     *     <li>{@code 0 <= x < arrayLayout.elementCount() }</li>
     *     <li>{@code 0 <= y < 10 }</li>
     *     <li>{@code 0 <= z < 20 }</li>
     * </ul>
     * <p>
     * Consider the following access expressions:
     * {@snippet lang=java :
     * int value1 = arrayHandle.get(10, 2, 4); // ok, accessed offset = 8176
     * int value2 = arrayHandle.get(0, 0, 30); // out of bounds value for z
     * }
     * In the first case, access is well-formed, as the values for {@code x}, {@code y} and {@code z} conform to
     * the bounds specified above. In the second case, access fails with {@link IndexOutOfBoundsException},
     * as the value for {@code z} is outside its specified bounds.
     *
     * @param shape the size of each nested array dimension.
     * @return a var handle which can be used to access a memory segment as a multi-dimensional array,
     * featuring {@code shape.length + 1}
     * {@code long} coordinates.
     * @throws IllegalArgumentException if {@code shape[i] < 0}, for at least one index {@code i}.
     * @throws UnsupportedOperationException if {@code bitAlignment() > bitSize()}.
     * @see MethodHandles#memorySegmentViewVarHandle
     * @see MemoryLayout#varHandle(PathElement...)
     * @see SequenceLayout
     */
    VarHandle arrayElementVarHandle(int... shape);

    /**
     * {@return the carrier associated with this value layout}
     */
    Class<?> carrier();

    /**
     * {@inheritDoc}
     */
    @Override
    ValueLayout withName(String name);

    /**
     * {@inheritDoc}
     */
    @Override

    ValueLayout withBitAlignment(long bitAlignment);

    /**
     * A value layout whose carrier is {@code boolean.class}.
     *
     * @see #JAVA_BOOLEAN
     * @since 19
     */
    @PreviewFeature(feature = PreviewFeature.Feature.FOREIGN)
    sealed interface OfBoolean extends ValueLayout permits ValueLayouts.OfBooleanImpl {

        /**
         * {@inheritDoc}
         */
        @Override
        OfBoolean withName(String name);

        /**
         * {@inheritDoc}
         */
        @Override
        OfBoolean withBitAlignment(long bitAlignment);

        /**
         * {@inheritDoc}
         */
        @Override
        OfBoolean withOrder(ByteOrder order);

    }

    /**
     * A value layout whose carrier is {@code byte.class}.
     *
     * @see #JAVA_BYTE
     * @since 19
     */
    @PreviewFeature(feature = PreviewFeature.Feature.FOREIGN)
    sealed interface OfByte extends ValueLayout permits ValueLayouts.OfByteImpl {

        /**
         * {@inheritDoc}
         */
        @Override
        OfByte withName(String name);

        /**
         * {@inheritDoc}
         */
        @Override
        OfByte withBitAlignment(long bitAlignment);

        /**
         * {@inheritDoc}
         */
        @Override
        OfByte withOrder(ByteOrder order);

    }

    /**
     * A value layout whose carrier is {@code char.class}.
     *
     * @see #JAVA_CHAR
     * @see #JAVA_CHAR_UNALIGNED
     * @since 19
     */
    @PreviewFeature(feature = PreviewFeature.Feature.FOREIGN)
    sealed interface OfChar extends ValueLayout permits ValueLayouts.OfCharImpl {

        /**
         * {@inheritDoc}
         */
        @Override
        OfChar withName(String name);

        /**
         * {@inheritDoc}
         */
        @Override
        OfChar withBitAlignment(long bitAlignment);

        /**
         * {@inheritDoc}
         */
        @Override
        OfChar withOrder(ByteOrder order);

    }

    /**
     * A value layout whose carrier is {@code short.class}.
     *
     * @see #JAVA_SHORT
     * @see #JAVA_SHORT_UNALIGNED
     * @since 19
     */
    @PreviewFeature(feature = PreviewFeature.Feature.FOREIGN)
    sealed interface OfShort extends ValueLayout permits ValueLayouts.OfShortImpl {

        /**
         * {@inheritDoc}
         */
        @Override
        OfShort withName(String name);

        /**
         * {@inheritDoc}
         */
        @Override
        OfShort withBitAlignment(long bitAlignment);

        /**
         * {@inheritDoc}
         */
        @Override
        OfShort withOrder(ByteOrder order);

    }

    /**
     * A value layout whose carrier is {@code int.class}.
     *
     * @see #JAVA_INT
     * @see #JAVA_INT_UNALIGNED
     * @since 19
     */
    @PreviewFeature(feature = PreviewFeature.Feature.FOREIGN)
    sealed interface OfInt extends ValueLayout permits ValueLayouts.OfIntImpl {

        /**
         * {@inheritDoc}
         */
        @Override
        OfInt withName(String name);

        /**
         * {@inheritDoc}
         */
        @Override
        OfInt withBitAlignment(long bitAlignment);

        /**
         * {@inheritDoc}
         */
        @Override
        OfInt withOrder(ByteOrder order);

    }

    /**
     * A value layout whose carrier is {@code float.class}.
     *
     * @see #JAVA_FLOAT
     * @see #JAVA_FLOAT_UNALIGNED
     * @since 19
     */
    @PreviewFeature(feature = PreviewFeature.Feature.FOREIGN)
    sealed interface OfFloat extends ValueLayout permits ValueLayouts.OfFloatImpl {

        /**
         * {@inheritDoc}
         */
        @Override
        OfFloat withName(String name);

        /**
         * {@inheritDoc}
         */
        @Override
        OfFloat withBitAlignment(long bitAlignment);

        /**
         * {@inheritDoc}
         */
        @Override
        OfFloat withOrder(ByteOrder order);

    }

    /**
     * A value layout whose carrier is {@code long.class}.
     *
     * @see #JAVA_LONG
     * @see #JAVA_LONG_UNALIGNED
     * @since 19
     */
    @PreviewFeature(feature = PreviewFeature.Feature.FOREIGN)
    sealed interface OfLong extends ValueLayout permits ValueLayouts.OfLongImpl {

        /**
         * {@inheritDoc}
         */
        @Override
        OfLong withName(String name);

        /**
         * {@inheritDoc}
         */
        @Override
        OfLong withBitAlignment(long bitAlignment);

        /**
         * {@inheritDoc}
         */
        @Override
        OfLong withOrder(ByteOrder order);

    }

    /**
     * A value layout whose carrier is {@code double.class}.
     *
     * @see #JAVA_DOUBLE
     * @see #JAVA_DOUBLE_UNALIGNED
     * @since 19
     */
    @PreviewFeature(feature = PreviewFeature.Feature.FOREIGN)
    sealed interface OfDouble extends ValueLayout permits ValueLayouts.OfDoubleImpl {

        /**
         * {@inheritDoc}
         */
        @Override
        OfDouble withName(String name);

        /**
         * {@inheritDoc}
         */
        @Override
        OfDouble withBitAlignment(long bitAlignment);

        /**
         * {@inheritDoc}
         */
        @Override
        OfDouble withOrder(ByteOrder order);

    }

    /**
     * A value layout whose carrier is {@code MemorySegment.class}.
     *
     * @see #ADDRESS
     * @see #ADDRESS_UNALIGNED
     * @since 19
     */
    @PreviewFeature(feature = PreviewFeature.Feature.FOREIGN)
    sealed interface OfAddress extends ValueLayout permits ValueLayouts.OfAddressImpl {

        /**
         * {@inheritDoc}
         */
        @Override
        OfAddress withName(String name);

        /**
         * {@inheritDoc}
         */
        @Override
        OfAddress withBitAlignment(long bitAlignment);

        /**
         * {@inheritDoc}
         */
        @Override
        OfAddress withOrder(ByteOrder order);

        /**
         * Returns an <em>unbounded</em> address layout with the same carrier, alignment constraint, name and order as this address layout,
         * but with the specified pointee layout. An unbounded address layout allow raw addresses to be accessed
         * as {@linkplain MemorySegment memory segments} whose size is set to {@link Long#MAX_VALUE}. As such,
         * these segments can be used in subsequent access operations.
         * <p>
         * This method is <a href="package-summary.html#restricted"><em>restricted</em></a>.
         * Restricted methods are unsafe, and, if used incorrectly, their use might crash
         * the JVM or, worse, silently result in memory corruption. Thus, clients should refrain from depending on
         * restricted methods, and use safe and supported functionalities, where possible.
         *
         * @return an unbounded address layout with same characteristics as this layout.
         * @throws IllegalCallerException If the caller is in a module that does not have native access enabled.
         * @see #isUnbounded()
         */
        @CallerSensitive
        OfAddress asUnbounded();

        /**
         * {@return {@code true}, if this address layout is an {@linkplain #asUnbounded() unbounded address layout}}.
         */
        boolean isUnbounded();

    }

    /**
     * A value layout constant whose size is the same as that of a machine address ({@code size_t}),
     * bit alignment set to {@code sizeof(size_t) * 8}, byte order set to {@link ByteOrder#nativeOrder()}.
     * Equivalent to the following code:
     * {@snippet lang=java :
     * MemoryLayout.valueLayout(MemorySegment.class, ByteOrder.nativeOrder());
     * }
     */
    OfAddress ADDRESS = ValueLayouts.OfAddressImpl.of(ByteOrder.nativeOrder());

    /**
     * A value layout constant whose size is the same as that of a Java {@code byte},
     * bit alignment set to 8, and byte order set to {@link ByteOrder#nativeOrder()}.
     * Equivalent to the following code:
     * {@snippet lang=java :
     * MemoryLayout.valueLayout(byte.class, ByteOrder.nativeOrder());
     * }
     */
    OfByte JAVA_BYTE = ValueLayouts.OfByteImpl.of(ByteOrder.nativeOrder());

    /**
     * A value layout constant whose size is the same as that of a Java {@code boolean},
     * bit alignment set to 8, and byte order set to {@link ByteOrder#nativeOrder()}.
     * Equivalent to the following code:
     * {@snippet lang=java :
     * MemoryLayout.valueLayout(boolean.class, ByteOrder.nativeOrder());
     * }
     */
    OfBoolean JAVA_BOOLEAN = ValueLayouts.OfBooleanImpl.of(ByteOrder.nativeOrder());

    /**
     * A value layout constant whose size is the same as that of a Java {@code char},
     * bit alignment set to 16, and byte order set to {@link ByteOrder#nativeOrder()}.
     * Equivalent to the following code:
     * {@snippet lang=java :
     * MemoryLayout.valueLayout(char.class, ByteOrder.nativeOrder());
     * }
     */
    OfChar JAVA_CHAR = ValueLayouts.OfCharImpl.of(ByteOrder.nativeOrder());

    /**
     * A value layout constant whose size is the same as that of a Java {@code short},
     * bit alignment set to 16, and byte order set to {@link ByteOrder#nativeOrder()}.
     * Equivalent to the following code:
     * {@snippet lang=java :
     * MemoryLayout.valueLayout(short.class, ByteOrder.nativeOrder());
     * }
     */
    OfShort JAVA_SHORT = ValueLayouts.OfShortImpl.of(ByteOrder.nativeOrder());

    /**
     * A value layout constant whose size is the same as that of a Java {@code int},
     * bit alignment set to 32, and byte order set to {@link ByteOrder#nativeOrder()}.
     * Equivalent to the following code:
     * {@snippet lang=java :
     * MemoryLayout.valueLayout(int.class, ByteOrder.nativeOrder());
     * }
     */
    OfInt JAVA_INT = ValueLayouts.OfIntImpl.of(ByteOrder.nativeOrder());

    /**
     * A value layout constant whose size is the same as that of a Java {@code long},
     * bit alignment set to 64, and byte order set to {@link ByteOrder#nativeOrder()}.
     * Equivalent to the following code:
     * {@snippet lang=java :
     * MemoryLayout.valueLayout(long.class, ByteOrder.nativeOrder());
     * }
     */
    OfLong JAVA_LONG = ValueLayouts.OfLongImpl.of(ByteOrder.nativeOrder());

    /**
     * A value layout constant whose size is the same as that of a Java {@code float},
     * bit alignment set to 32, and byte order set to {@link ByteOrder#nativeOrder()}.
     * Equivalent to the following code:
     * {@snippet lang=java :
     * MemoryLayout.valueLayout(float.class, ByteOrder.nativeOrder()).withBitAlignment(32);
     * }
     */
    OfFloat JAVA_FLOAT = ValueLayouts.OfFloatImpl.of(ByteOrder.nativeOrder());

    /**
     * A value layout constant whose size is the same as that of a Java {@code double},
     * bit alignment set to 64, and byte order set to {@link ByteOrder#nativeOrder()}.
     * Equivalent to the following code:
     * {@snippet lang=java :
     * MemoryLayout.valueLayout(double.class, ByteOrder.nativeOrder());
     * }
     */
    OfDouble JAVA_DOUBLE = ValueLayouts.OfDoubleImpl.of(ByteOrder.nativeOrder());

    /**
     * An unaligned value layout constant whose size is the same as that of a machine address ({@code size_t}),
     * and byte order set to {@link ByteOrder#nativeOrder()}.
     * Equivalent to the following code:
     * {@snippet lang=java :
     * ADDRESS.withBitAlignment(8);
     * }
     * @apiNote Care should be taken when using unaligned value layouts as they may induce
     *          performance and portability issues.
     */
    OfAddress ADDRESS_UNALIGNED = ADDRESS.withBitAlignment(8);

    /**
     * An unaligned value layout constant whose size is the same as that of a Java {@code char}
     * and byte order set to {@link ByteOrder#nativeOrder()}.
     * Equivalent to the following code:
     * {@snippet lang=java :
     * JAVA_CHAR.withBitAlignment(8);
     * }
     * @apiNote Care should be taken when using unaligned value layouts as they may induce
     *          performance and portability issues.
     */
    OfChar JAVA_CHAR_UNALIGNED = JAVA_CHAR.withBitAlignment(8);

    /**
     * An unaligned value layout constant whose size is the same as that of a Java {@code short}
     * and byte order set to {@link ByteOrder#nativeOrder()}.
     * Equivalent to the following code:
     * {@snippet lang=java :
     * JAVA_SHORT.withBitAlignment(8);
     * }
     * @apiNote Care should be taken when using unaligned value layouts as they may induce
     *          performance and portability issues.
     */
    OfShort JAVA_SHORT_UNALIGNED = JAVA_SHORT.withBitAlignment(8);

    /**
     * An unaligned value layout constant whose size is the same as that of a Java {@code int}
     * and byte order set to {@link ByteOrder#nativeOrder()}.
     * Equivalent to the following code:
     * {@snippet lang=java :
     * JAVA_INT.withBitAlignment(8);
     * }
     * @apiNote Care should be taken when using unaligned value layouts as they may induce
     *          performance and portability issues.
     */
    OfInt JAVA_INT_UNALIGNED = JAVA_INT.withBitAlignment(8);

    /**
     * An unaligned value layout constant whose size is the same as that of a Java {@code long}
     * and byte order set to {@link ByteOrder#nativeOrder()}.
     * Equivalent to the following code:
     * {@snippet lang=java :
     * JAVA_LONG.withBitAlignment(8);
     * }
     * @apiNote Care should be taken when using unaligned value layouts as they may induce
     *          performance and portability issues.
     */
    OfLong JAVA_LONG_UNALIGNED = JAVA_LONG.withBitAlignment(8);

    /**
     * An unaligned value layout constant whose size is the same as that of a Java {@code float}
     * and byte order set to {@link ByteOrder#nativeOrder()}.
     * Equivalent to the following code:
     * {@snippet lang=java :
     * JAVA_FLOAT.withBitAlignment(8);
     * }
     * @apiNote Care should be taken when using unaligned value layouts as they may induce
     *          performance and portability issues.
     */
    OfFloat JAVA_FLOAT_UNALIGNED = JAVA_FLOAT.withBitAlignment(8);

    /**
     * An unaligned value layout constant whose size is the same as that of a Java {@code double}
     * and byte order set to {@link ByteOrder#nativeOrder()}.
     * Equivalent to the following code:
     * {@snippet lang=java :
     * JAVA_DOUBLE.withBitAlignment(8);
     * }
     * @apiNote Care should be taken when using unaligned value layouts as they may induce
     *          performance and portability issues.
     */
    OfDouble JAVA_DOUBLE_UNALIGNED = JAVA_DOUBLE.withBitAlignment(8);

}
