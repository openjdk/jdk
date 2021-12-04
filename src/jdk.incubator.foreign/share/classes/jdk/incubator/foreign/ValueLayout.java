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
package jdk.incubator.foreign;

import jdk.internal.foreign.Utils;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;
import sun.invoke.util.Wrapper;

import java.lang.constant.ConstantDescs;
import java.lang.constant.DynamicConstantDesc;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * A value layout. A value layout is used to model the memory layout associated with values of basic data types, such as <em>integral</em> types
 * (either signed or unsigned) and <em>floating-point</em> types. Each value layout has a size, a {@linkplain ByteOrder byte order})
 * and a <em>carrier</em>, that is, the Java type that should be used when {@linkplain MemorySegment#get(OfInt, long) accessing}
 * a memory region using the value layout.
 * <p>
 * This class defines useful value layout constants for Java primitive types and addresses.
 * The layout constants in this class make implicit alignment and byte-ordering assumption: all layout
 * constants in this class are byte-aligned, and their byte order is set to the {@linkplain ByteOrder#nativeOrder() platform default},
 * thus making it easy to work with other APIs, such as arrays and {@link java.nio.ByteBuffer}.
 * <p>
 * This is a <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>
 * class; programmers should treat instances that are
 * {@linkplain #equals(Object) equal} as interchangeable and should not
 * use instances for synchronization, or unpredictable behavior may
 * occur. For example, in a future release, synchronization may fail.
 * The {@code equals} method should be used for comparisons.
 *
 * <p> Unless otherwise specified, passing a {@code null} argument, or an array argument containing one or more {@code null}
 * elements to a method in this class causes a {@link NullPointerException NullPointerException} to be thrown. </p>
 *
 * @implSpec
 * This class is immutable and thread-safe.
 */
public sealed class ValueLayout extends AbstractLayout implements MemoryLayout {

    private final Class<?> carrier;
    private final ByteOrder order;

    private static final int ADDRESS_SIZE_BITS = Unsafe.ADDRESS_SIZE * 8;

    ValueLayout(Class<?> carrier, ByteOrder order, long size) {
        this(carrier, order, size, size, Optional.empty());
    }

    ValueLayout(Class<?> carrier, ByteOrder order, long size, long alignment, Optional<String> name) {
        super(OptionalLong.of(size), alignment, name);
        this.carrier = carrier;
        this.order = order;
        checkCarrierSize(carrier, size);
    }

    /**
     * Returns the value's byte order.
     *
     * @return the value's  byte order.
     */
    public ByteOrder order() {
        return order;
    }

    /**
     * Returns a new value layout with given byte order.
     *
     * @param order the desired byte order.
     * @return a new value layout with given byte order.
     */
    public ValueLayout withOrder(ByteOrder order) {
        return new ValueLayout(carrier, Objects.requireNonNull(order), bitSize(), alignment, name());
    }

    @Override
    public String toString() {
        return decorateLayoutString(String.format("%s%d",
                order == ByteOrder.BIG_ENDIAN ? "B" : "b",
                bitSize()));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!super.equals(other)) {
            return false;
        }
        if (!(other instanceof ValueLayout v)) {
            return false;
        }
        return carrier.equals(v.carrier) &&
            order.equals(v.order) &&
            bitSize() == v.bitSize() &&
            alignment == v.alignment;
    }

    /**
     * Returns the carrier associated with this value layout.
     * @return the carrier associated with this value layout.
     */
    public Class<?> carrier() {
        return carrier;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), order, bitSize(), alignment);
    }

    @Override
    ValueLayout dup(long alignment, Optional<String> name) {
        return new ValueLayout(carrier, order, bitSize(), alignment, name());
    }

    @Override
    public Optional<DynamicConstantDesc<ValueLayout>> describeConstable() {
        return Optional.of(decorateLayoutConstant(DynamicConstantDesc.ofNamed(ConstantDescs.BSM_INVOKE, "value",
                CD_VALUE_LAYOUT, MH_VALUE, carrier().describeConstable().get(), order == ByteOrder.BIG_ENDIAN ? BIG_ENDIAN : LITTLE_ENDIAN)));
    }

    //hack: the declarations below are to make javadoc happy; we could have used generics in AbstractLayout
    //but that causes issues with javadoc, see JDK-8224052

    /**
     * {@inheritDoc}
     */
    @Override
    public ValueLayout withName(String name) {
        return (ValueLayout)super.withName(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ValueLayout withBitAlignment(long alignmentBits) {
        return (ValueLayout)super.withBitAlignment(alignmentBits);
    }

    static void checkCarrierSize(Class<?> carrier, long size) {
        if (!isValidCarrier(carrier)) {
            throw new IllegalArgumentException("Invalid carrier: " + carrier.getName());
        }
        if (carrier == MemoryAddress.class && size != ADDRESS_SIZE_BITS) {
            throw new IllegalArgumentException("Address size mismatch: " + ADDRESS_SIZE_BITS + " != " + size);
        }
        if (carrier.isPrimitive()) {
            int expectedSize =  carrier == boolean.class ? 8 : Wrapper.forPrimitiveType(carrier).bitWidth();
            if (size != expectedSize) {
                throw new IllegalArgumentException("Carrier size mismatch: " + carrier.getName() + " != " + size);
            }
        }
    }

    static boolean isValidCarrier(Class<?> carrier) {
        return carrier == boolean.class
                || carrier == byte.class
                || carrier == short.class
                || carrier == char.class
                || carrier == int.class
                || carrier == long.class
                || carrier == float.class
                || carrier == double.class
                || carrier == MemoryAddress.class;
    }

    @Stable
    private VarHandle handle;

    @ForceInline
    VarHandle accessHandle() {
        if (handle == null) {
            // this store to stable field is safe, because return value of 'makeMemoryAccessVarHandle' has stable identity
            handle = Utils.makeMemoryAccessVarHandle(this, false);
        }
        return handle;
    }

    /**
     * A value layout whose carrier is {@code boolean.class}.
     */
    public static final class OfBoolean extends ValueLayout {
        OfBoolean(ByteOrder order) {
            super(boolean.class, order, 8);
        }

        OfBoolean(ByteOrder order, long alignment, Optional<String> name) {
            super(boolean.class, order, 8, alignment, name);
        }

        @Override
        OfBoolean dup(long alignment, Optional<String> name) {
            return new OfBoolean(order(), alignment, name);
        }

        @Override
        public OfBoolean withName(String name) {
            return (OfBoolean)super.withName(name);
        }

        @Override
        public OfBoolean withBitAlignment(long alignmentBits) {
            return (OfBoolean)super.withBitAlignment(alignmentBits);
        }

        @Override
        public OfBoolean withOrder(ByteOrder order) {
            Objects.requireNonNull(order);
            return new OfBoolean(order, alignment, name());
        }
    }

    /**
     * A value layout whose carrier is {@code byte.class}.
     */
    public static final class OfByte extends ValueLayout {
        OfByte(ByteOrder order) {
            super(byte.class, order, 8);
        }

        OfByte(ByteOrder order, long alignment, Optional<String> name) {
            super(byte.class, order, 8, alignment, name);
        }

        @Override
        OfByte dup(long alignment, Optional<String> name) {
            return new OfByte(order(), alignment, name);
        }

        @Override
        public OfByte withName(String name) {
            return (OfByte)super.withName(name);
        }

        @Override
        public OfByte withBitAlignment(long alignmentBits) {
            return (OfByte)super.withBitAlignment(alignmentBits);
        }

        @Override
        public OfByte withOrder(ByteOrder order) {
            Objects.requireNonNull(order);
            return new OfByte(order, alignment, name());
        }
    }

    /**
     * A value layout whose carrier is {@code char.class}.
     */
    public static final class OfChar extends ValueLayout {
        OfChar(ByteOrder order) {
            super(char.class, order, 16);
        }

        OfChar(ByteOrder order, long alignment, Optional<String> name) {
            super(char.class, order, 16, alignment, name);
        }

        @Override
        OfChar dup(long alignment, Optional<String> name) {
            return new OfChar(order(), alignment, name);
        }

        @Override
        public OfChar withName(String name) {
            return (OfChar)super.withName(name);
        }

        @Override
        public OfChar withBitAlignment(long alignmentBits) {
            return (OfChar)super.withBitAlignment(alignmentBits);
        }

        @Override
        public OfChar withOrder(ByteOrder order) {
            Objects.requireNonNull(order);
            return new OfChar(order, alignment, name());
        }
    }

    /**
     * A value layout whose carrier is {@code short.class}.
     */
    public static final class OfShort extends ValueLayout {
        OfShort(ByteOrder order) {
            super(short.class, order, 16);
        }

        OfShort(ByteOrder order, long alignment, Optional<String> name) {
            super(short.class, order, 16, alignment, name);
        }

        @Override
        OfShort dup(long alignment, Optional<String> name) {
            return new OfShort(order(), alignment, name);
        }

        @Override
        public OfShort withName(String name) {
            return (OfShort)super.withName(name);
        }

        @Override
        public OfShort withBitAlignment(long alignmentBits) {
            return (OfShort)super.withBitAlignment(alignmentBits);
        }

        @Override
        public OfShort withOrder(ByteOrder order) {
            Objects.requireNonNull(order);
            return new OfShort(order, alignment, name());
        }
    }

    /**
     * A value layout whose carrier is {@code int.class}.
     */
    public static final class OfInt extends ValueLayout {
        OfInt(ByteOrder order) {
            super(int.class, order, 32);
        }

        OfInt(ByteOrder order, long alignment, Optional<String> name) {
            super(int.class, order, 32, alignment, name);
        }

        @Override
        OfInt dup(long alignment, Optional<String> name) {
            return new OfInt(order(), alignment, name);
        }

        @Override
        public OfInt withName(String name) {
            return (OfInt)super.withName(name);
        }

        @Override
        public OfInt withBitAlignment(long alignmentBits) {
            return (OfInt)super.withBitAlignment(alignmentBits);
        }

        @Override
        public OfInt withOrder(ByteOrder order) {
            Objects.requireNonNull(order);
            return new OfInt(order, alignment, name());
        }
    }

    /**
     * A value layout whose carrier is {@code float.class}.
     */
    public static final class OfFloat extends ValueLayout {
        OfFloat(ByteOrder order) {
            super(float.class, order, 32);
        }

        OfFloat(ByteOrder order, long alignment, Optional<String> name) {
            super(float.class, order, 32, alignment, name);
        }

        @Override
        OfFloat dup(long alignment, Optional<String> name) {
            return new OfFloat(order(), alignment, name);
        }

        @Override
        public OfFloat withName(String name) {
            return (OfFloat)super.withName(name);
        }

        @Override
        public OfFloat withBitAlignment(long alignmentBits) {
            return (OfFloat)super.withBitAlignment(alignmentBits);
        }

        @Override
        public OfFloat withOrder(ByteOrder order) {
            Objects.requireNonNull(order);
            return new OfFloat(order, alignment, name());
        }
    }

    /**
     * A value layout whose carrier is {@code long.class}.
     */
    public static final class OfLong extends ValueLayout {
        OfLong(ByteOrder order) {
            super(long.class, order, 64);
        }

        OfLong(ByteOrder order, long alignment, Optional<String> name) {
            super(long.class, order, 64, alignment, name);
        }

        @Override
        OfLong dup(long alignment, Optional<String> name) {
            return new OfLong(order(), alignment, name);
        }

        @Override
        public OfLong withName(String name) {
            return (OfLong)super.withName(name);
        }

        @Override
        public OfLong withBitAlignment(long alignmentBits) {
            return (OfLong)super.withBitAlignment(alignmentBits);
        }

        @Override
        public OfLong withOrder(ByteOrder order) {
            Objects.requireNonNull(order);
            return new OfLong(order, alignment, name());
        }
    }

    /**
     * A value layout whose carrier is {@code double.class}.
     */
    public static final class OfDouble extends ValueLayout {
        OfDouble(ByteOrder order) {
            super(double.class, order, 64);
        }

        OfDouble(ByteOrder order, long alignment, Optional<String> name) {
            super(double.class, order, 64, alignment, name);
        }

        @Override
        OfDouble dup(long alignment, Optional<String> name) {
            return new OfDouble(order(), alignment, name);
        }

        @Override
        public OfDouble withName(String name) {
            return (OfDouble)super.withName(name);
        }

        @Override
        public OfDouble withBitAlignment(long alignmentBits) {
            return (OfDouble)super.withBitAlignment(alignmentBits);
        }

        @Override
        public OfDouble withOrder(ByteOrder order) {
            Objects.requireNonNull(order);
            return new OfDouble(order, alignment, name());
        }
    }

    /**
     * A value layout whose carrier is {@code MemoryAddress.class}.
     */
    public static final class OfAddress extends ValueLayout {
        OfAddress(ByteOrder order) {
            super(MemoryAddress.class, order, ADDRESS_SIZE_BITS);
        }

        OfAddress(ByteOrder order, long size, long alignment, Optional<String> name) {
            super(MemoryAddress.class, order, size, alignment, name);
        }

        @Override
        OfAddress dup(long alignment, Optional<String> name) {
            return new OfAddress(order(), bitSize(), alignment, name);
        }

        @Override
        public OfAddress withName(String name) {
            return (OfAddress)super.withName(name);
        }

        @Override
        public OfAddress withBitAlignment(long alignmentBits) {
            return (OfAddress)super.withBitAlignment(alignmentBits);
        }

        @Override
        public OfAddress withOrder(ByteOrder order) {
            Objects.requireNonNull(order);
            return new OfAddress(order, bitSize(), alignment, name());
        }
    }

    /**
     * A value layout constant whose size is the same as that of a machine address (e.g. {@code size_t}),
     * bit-alignment set to 8, and byte order set to {@link ByteOrder#nativeOrder()}.
     * Equivalent to the following code:
     * <blockquote><pre>{@code
    MemoryLayout.valueLayout(MemoryAddress.class, ByteOrder.nativeOrder()).withBitAlignment(8);
     * }</pre></blockquote>
     */
    public static final OfAddress ADDRESS = new OfAddress(ByteOrder.nativeOrder()).withBitAlignment(8);

    /**
     * A value layout constant whose size is the same as that of a Java {@code byte},
     * bit-alignment set to 8, and byte order set to {@link ByteOrder#nativeOrder()}.
     * Equivalent to the following code:
     * <blockquote><pre>{@code
    MemoryLayout.valueLayout(byte.class, ByteOrder.nativeOrder()).withBitAlignment(8);
     * }</pre></blockquote>
     */
    public static final OfByte JAVA_BYTE = new OfByte(ByteOrder.nativeOrder()).withBitAlignment(8);

    /**
     * A value layout constant whose size is the same as that of a Java {@code boolean},
     * bit-alignment set to 8, and byte order set to {@link ByteOrder#nativeOrder()}.
     * Equivalent to the following code:
     * <blockquote><pre>{@code
    MemoryLayout.valueLayout(boolean.class, ByteOrder.nativeOrder()).withBitAlignment(8);
     * }</pre></blockquote>
     */
    public static final OfBoolean JAVA_BOOLEAN = new OfBoolean(ByteOrder.nativeOrder()).withBitAlignment(8);

    /**
     * A value layout constant whose size is the same as that of a Java {@code char},
     * bit-alignment set to 8, and byte order set to {@link ByteOrder#nativeOrder()}.
     * Equivalent to the following code:
     * <blockquote><pre>{@code
    MemoryLayout.valueLayout(char.class, ByteOrder.nativeOrder()).withBitAlignment(8);
     * }</pre></blockquote>
     */
    public static final OfChar JAVA_CHAR = new OfChar(ByteOrder.nativeOrder()).withBitAlignment(8);

    /**
     * A value layout constant whose size is the same as that of a Java {@code short},
     * bit-alignment set to 8, and byte order set to {@link ByteOrder#nativeOrder()}.
     * Equivalent to the following code:
     * <blockquote><pre>{@code
    MemoryLayout.valueLayout(short.class, ByteOrder.nativeOrder()).withBitAlignment(8);
     * }</pre></blockquote>
     */
    public static final OfShort JAVA_SHORT = new OfShort(ByteOrder.nativeOrder()).withBitAlignment(8);

    /**
     * A value layout constant whose size is the same as that of a Java {@code int},
     * bit-alignment set to 8, and byte order set to {@link ByteOrder#nativeOrder()}.
     * Equivalent to the following code:
     * <blockquote><pre>{@code
    MemoryLayout.valueLayout(int.class, ByteOrder.nativeOrder()).withBitAlignment(8);
     * }</pre></blockquote>
     */
    public static final OfInt JAVA_INT = new OfInt(ByteOrder.nativeOrder()).withBitAlignment(8);

    /**
     * A value layout constant whose size is the same as that of a Java {@code long},
     * bit-alignment set to 8, and byte order set to {@link ByteOrder#nativeOrder()}.
     * Equivalent to the following code:
     * <blockquote><pre>{@code
    MemoryLayout.valueLayout(long.class, ByteOrder.nativeOrder()).withBitAlignment(8);
     * }</pre></blockquote>
     */
    public static final OfLong JAVA_LONG = new OfLong(ByteOrder.nativeOrder())
            .withBitAlignment(8);

    /**
     * A value layout constant whose size is the same as that of a Java {@code float},
     * bit-alignment set to 8, and byte order set to {@link ByteOrder#nativeOrder()}.
     * Equivalent to the following code:
     * <blockquote><pre>{@code
    MemoryLayout.valueLayout(float.class, ByteOrder.nativeOrder()).withBitAlignment(8);
     * }</pre></blockquote>
     */
    public static final OfFloat JAVA_FLOAT = new OfFloat(ByteOrder.nativeOrder()).withBitAlignment(8);

    /**
     * A value layout constant whose size is the same as that of a Java {@code double},
     * bit-alignment set to 8, and byte order set to {@link ByteOrder#nativeOrder()}.
     * Equivalent to the following code:
     * <blockquote><pre>{@code
    MemoryLayout.valueLayout(double.class, ByteOrder.nativeOrder()).withBitAlignment(8);
     * }</pre></blockquote>
     */
    public static final OfDouble JAVA_DOUBLE = new OfDouble(ByteOrder.nativeOrder()).withBitAlignment(8);
}
