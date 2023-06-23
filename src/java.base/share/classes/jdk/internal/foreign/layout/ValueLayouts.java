/*
 *  Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.foreign.layout;

import jdk.internal.foreign.Utils;
import jdk.internal.misc.Unsafe;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.Optional;

/**
 * A value layout. A value layout is used to model the memory layout associated with values of basic data types, such as <em>integral</em> types
 * (either signed or unsigned) and <em>floating-point</em> types. Each value layout has a size, an alignment (expressed in bytes),
 * a {@linkplain ByteOrder byte order}, and a <em>carrier</em>, that is, the Java type that should be used when
 * {@linkplain MemorySegment#get(ValueLayout.OfInt, long) accessing} a memory region using the value layout.
 * <p>
 * This class defines useful value layout constants for Java primitive types and addresses.
 * The layout constants in this class make implicit alignment and byte-ordering assumption: all layout
 * constants in this class are byte-aligned, and their byte order is set to the {@linkplain ByteOrder#nativeOrder() platform default},
 * thus making it easy to work with other APIs, such as arrays and {@link java.nio.ByteBuffer}.
 *
 * @implSpec This class and its subclasses are immutable, thread-safe and <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>.
 */
public final class ValueLayouts {

    // Suppresses default constructor, ensuring non-instantiability.
    private ValueLayouts() {}

    abstract sealed static class AbstractValueLayout<V extends AbstractValueLayout<V> & ValueLayout> extends AbstractLayout<V> {

        static final int ADDRESS_SIZE_BYTES = Unsafe.ADDRESS_SIZE;

        private final Class<?> carrier;
        private final ByteOrder order;
        @Stable
        private VarHandle handle;

        AbstractValueLayout(Class<?> carrier, ByteOrder order, long byteSize, long byteAlignment, Optional<String> name) {
            super(byteSize, byteAlignment, name);
            this.carrier = carrier;
            this.order = order;
            assertCarrierSize(carrier, byteSize);
        }

        /**
         * {@return the value's byte order}
         */
        public final ByteOrder order() {
            return order;
        }

        /**
         * Returns a value layout with the same carrier, alignment constraints and name as this value layout,
         * but with the specified byte order.
         *
         * @param order the desired byte order.
         * @return a value layout with the given byte order.
         */
        public final V withOrder(ByteOrder order) {
            Objects.requireNonNull(order);
            return dup(order, byteAlignment(), name());
        }

        @Override
        public String toString() {
            char descriptor = carrier.descriptorString().charAt(0);
            if (order == ByteOrder.LITTLE_ENDIAN) {
                descriptor = Character.toLowerCase(descriptor);
            }
            return decorateLayoutString(String.format("%s%d", descriptor, byteSize()));
        }

        @Override
        public boolean equals(Object other) {
            return this == other ||
                    other instanceof AbstractValueLayout<?> otherValue &&
                            super.equals(other) &&
                            carrier.equals(otherValue.carrier) &&
                            order.equals(otherValue.order);
        }

        /**
         * {@return the carrier associated with this value layout}
         */
        public final Class<?> carrier() {
            return carrier;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), order, carrier);
        }

        @Override
        final V dup(long byteAlignment, Optional<String> name) {
            return dup(order(), byteAlignment, name);
        }

        abstract V dup(ByteOrder order, long byteAlignment, Optional<String> name);

        static void assertCarrierSize(Class<?> carrier, long byteSize) {
            assert isValidCarrier(carrier);
            assert carrier != MemorySegment.class
                    // MemorySegment byteSize must always equal ADDRESS_SIZE_BYTES
                    || byteSize == ADDRESS_SIZE_BYTES;
            assert !carrier.isPrimitive() ||
                    // Primitive class byteSize must always correspond
                    byteSize == (carrier == boolean.class ? 1 :
                            Utils.byteWidthOfPrimitive(carrier));
        }

        static boolean isValidCarrier(Class<?> carrier) {
            // void.class is not valid
            return carrier == boolean.class
                    || carrier == byte.class
                    || carrier == short.class
                    || carrier == char.class
                    || carrier == int.class
                    || carrier == long.class
                    || carrier == float.class
                    || carrier == double.class
                    || carrier == MemorySegment.class;
        }

        @ForceInline
        public final VarHandle varHandle() {
            if (handle == null) {
                // this store to stable field is safe, because return value of 'makeMemoryAccessVarHandle' has stable identity
                handle = Utils.makeSegmentViewVarHandle(self());
            }
            return handle;
        }

        @SuppressWarnings("unchecked")
        final V self() {
            return (V) this;
        }
    }

    public static final class OfBooleanImpl extends AbstractValueLayout<OfBooleanImpl> implements ValueLayout.OfBoolean {

        private OfBooleanImpl(ByteOrder order, long byteAlignment, Optional<String> name) {
            super(boolean.class, order, Byte.BYTES, byteAlignment, name);
        }

        @Override
        OfBooleanImpl dup(ByteOrder order, long byteAlignment, Optional<String> name) {
            return new OfBooleanImpl(order, byteAlignment, name);
        }

        public static OfBoolean of(ByteOrder order) {
            return new OfBooleanImpl(order, Byte.BYTES, Optional.empty());
        }
    }

    public static final class OfByteImpl extends AbstractValueLayout<OfByteImpl> implements ValueLayout.OfByte {

        private OfByteImpl(ByteOrder order, long byteAlignment, Optional<String> name) {
            super(byte.class, order, Byte.BYTES, byteAlignment, name);
        }

        @Override
        OfByteImpl dup(ByteOrder order, long byteAlignment, Optional<String> name) {
            return new OfByteImpl(order, byteAlignment, name);
        }

        public static OfByte of(ByteOrder order) {
            return new OfByteImpl(order, Byte.BYTES, Optional.empty());
        }
    }

    public static final class OfCharImpl extends AbstractValueLayout<OfCharImpl> implements ValueLayout.OfChar {

        private OfCharImpl(ByteOrder order, long byteAlignment, Optional<String> name) {
            super(char.class, order, Character.BYTES, byteAlignment, name);
        }

        @Override
        OfCharImpl dup(ByteOrder order, long byteAlignment, Optional<String> name) {
            return new OfCharImpl(order, byteAlignment, name);
        }

        public static OfChar of(ByteOrder order) {
            return new OfCharImpl(order, Character.BYTES, Optional.empty());
        }
    }

    public static final class OfShortImpl extends AbstractValueLayout<OfShortImpl> implements ValueLayout.OfShort {

        private OfShortImpl(ByteOrder order, long byteAlignment, Optional<String> name) {
            super(short.class, order, Short.BYTES, byteAlignment, name);
        }

        @Override
        OfShortImpl dup(ByteOrder order, long byteAlignment, Optional<String> name) {
            return new OfShortImpl(order, byteAlignment, name);
        }

        public static OfShort of(ByteOrder order) {
            return new OfShortImpl(order, Short.BYTES, Optional.empty());
        }
    }

    public static final class OfIntImpl extends AbstractValueLayout<OfIntImpl> implements ValueLayout.OfInt {

        private OfIntImpl(ByteOrder order, long byteAlignment, Optional<String> name) {
            super(int.class, order, Integer.BYTES, byteAlignment, name);
        }

        @Override
        OfIntImpl dup(ByteOrder order, long byteAlignment, Optional<String> name) {
            return new OfIntImpl(order, byteAlignment, name);
        }

        public static OfInt of(ByteOrder order) {
            return new OfIntImpl(order, Integer.BYTES, Optional.empty());
        }
    }

    public static final class OfFloatImpl extends AbstractValueLayout<OfFloatImpl> implements ValueLayout.OfFloat {

        private OfFloatImpl(ByteOrder order, long byteAlignment, Optional<String> name) {
            super(float.class, order, Float.BYTES, byteAlignment, name);
        }

        @Override
        OfFloatImpl dup(ByteOrder order, long byteAlignment, Optional<String> name) {
            return new OfFloatImpl(order, byteAlignment, name);
        }

        public static OfFloat of(ByteOrder order) {
            return new OfFloatImpl(order, Float.BYTES, Optional.empty());
        }
    }

    public static final class OfLongImpl extends AbstractValueLayout<OfLongImpl> implements ValueLayout.OfLong {

        private OfLongImpl(ByteOrder order, long byteAlignment, Optional<String> name) {
            super(long.class, order, Long.BYTES, byteAlignment, name);
        }

        @Override
        OfLongImpl dup(ByteOrder order, long byteAlignment, Optional<String> name) {
            return new OfLongImpl(order, byteAlignment, name);
        }

        public static OfLong of(ByteOrder order) {
            return new OfLongImpl(order, ADDRESS_SIZE_BYTES, Optional.empty());
        }
    }

    public static final class OfDoubleImpl extends AbstractValueLayout<OfDoubleImpl> implements ValueLayout.OfDouble {

        private OfDoubleImpl(ByteOrder order, long byteAlignment, Optional<String> name) {
            super(double.class, order, Double.BYTES, byteAlignment, name);
        }

        @Override
        OfDoubleImpl dup(ByteOrder order, long byteAlignment, Optional<String> name) {
            return new OfDoubleImpl(order, byteAlignment, name);
        }

        public static OfDouble of(ByteOrder order) {
            return new OfDoubleImpl(order, ADDRESS_SIZE_BYTES, Optional.empty());
        }

    }

    public static final class OfAddressImpl extends AbstractValueLayout<OfAddressImpl> implements AddressLayout {

        private final MemoryLayout targetLayout;

        private OfAddressImpl(ByteOrder order, long byteSize, long byteAlignment, MemoryLayout targetLayout, Optional<String> name) {
            super(MemorySegment.class, order, byteSize, byteAlignment, name);
            this.targetLayout = targetLayout;
        }

        @Override
        OfAddressImpl dup(ByteOrder order, long byteAlignment, Optional<String> name) {
            return new OfAddressImpl(order, byteSize(), byteAlignment,targetLayout, name);
        }

        @Override
        public boolean equals(Object other) {
            return super.equals(other) &&
                    Objects.equals(((OfAddressImpl)other).targetLayout, this.targetLayout);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), targetLayout);
        }

        @Override
        @CallerSensitive
        public AddressLayout withTargetLayout(MemoryLayout layout) {
            Reflection.ensureNativeAccess(Reflection.getCallerClass(), AddressLayout.class, "withTargetLayout");
            Objects.requireNonNull(layout);
            return new OfAddressImpl(order(), byteSize(), byteAlignment(), layout, name());
        }

        @Override
        public AddressLayout withoutTargetLayout() {
            return new OfAddressImpl(order(), byteSize(), byteAlignment(), null, name());
        }

        @Override
        public Optional<MemoryLayout> targetLayout() {
            return Optional.ofNullable(targetLayout);
        }

        public static AddressLayout of(ByteOrder order) {
            return new OfAddressImpl(order, ADDRESS_SIZE_BYTES, ADDRESS_SIZE_BYTES, null, Optional.empty());
        }

        @Override
        public String toString() {
            char descriptor = 'A';
            if (order() == ByteOrder.LITTLE_ENDIAN) {
                descriptor = Character.toLowerCase(descriptor);
            }
            String str = decorateLayoutString(String.format("%s%d", descriptor, byteSize()));
            if (targetLayout != null) {
                str += ":" + targetLayout;
            }
            return str;
        }
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
     *     <li>{@link ValueLayout.OfAddress}, for {@code MemorySegment.class}</li>
     * </ul>
     * @param carrier the value layout carrier.
     * @param order the value layout's byte order.
     * @return a value layout with the given Java carrier and byte-order.
     * @throws IllegalArgumentException if the carrier type is not supported.
     */
    public static ValueLayout valueLayout(Class<?> carrier, ByteOrder order) {
        Objects.requireNonNull(carrier);
        Objects.requireNonNull(order);
        if (carrier == boolean.class) {
            return ValueLayouts.OfBooleanImpl.of(order);
        } else if (carrier == char.class) {
            return ValueLayouts.OfCharImpl.of(order);
        } else if (carrier == byte.class) {
            return ValueLayouts.OfByteImpl.of(order);
        } else if (carrier == short.class) {
            return ValueLayouts.OfShortImpl.of(order);
        } else if (carrier == int.class) {
            return ValueLayouts.OfIntImpl.of(order);
        } else if (carrier == float.class) {
            return ValueLayouts.OfFloatImpl.of(order);
        } else if (carrier == long.class) {
            return ValueLayouts.OfLongImpl.of(order);
        } else if (carrier == double.class) {
            return ValueLayouts.OfDoubleImpl.of(order);
        } else if (carrier == MemorySegment.class) {
            return ValueLayouts.OfAddressImpl.of(order);
        } else {
            throw new IllegalArgumentException("Unsupported carrier: " + carrier.getName());
        }
    }
}
