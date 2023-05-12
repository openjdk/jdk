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
package jdk.internal.foreign.layout;

import jdk.internal.foreign.Utils;
import jdk.internal.misc.Unsafe;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;
import sun.invoke.util.Wrapper;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A value layout. A value layout is used to model the memory layout associated with values of basic data types, such as <em>integral</em> types
 * (either signed or unsigned) and <em>floating-point</em> types. Each value layout has a size, an alignment (in bits),
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

    private ValueLayouts() {
    }

    abstract sealed static class AbstractValueLayout<V extends AbstractValueLayout<V> & ValueLayout> extends AbstractLayout<V> {

        static final int ADDRESS_SIZE_BITS = Unsafe.ADDRESS_SIZE * 8;

        private final Class<?> carrier;
        private final ByteOrder order;
        @Stable
        private VarHandle handle;

        AbstractValueLayout(Class<?> carrier, ByteOrder order, long bitSize) {
            this(carrier, order, bitSize, bitSize, Optional.empty());
        }

        AbstractValueLayout(Class<?> carrier, ByteOrder order, long bitSize, long bitAlignment, Optional<String> name) {
            super(bitSize, bitAlignment, name);
            this.carrier = carrier;
            this.order = order;
            checkCarrierSize(carrier, bitSize);
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
        abstract V withOrder(ByteOrder order);

        @Override
        public final String toString() {
            char descriptor = carrier == MemorySegment.class ? 'A' : carrier.descriptorString().charAt(0);
            if (order == ByteOrder.LITTLE_ENDIAN) {
                descriptor = Character.toLowerCase(descriptor);
            }
            return decorateLayoutString(String.format("%s%d", descriptor, bitSize()));
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!super.equals(other)) {
                return false;
            }
            return other instanceof AbstractValueLayout<?> otherValue &&
                    carrier.equals(otherValue.carrier) &&
                    order.equals(otherValue.order);
        }

        public final VarHandle arrayElementVarHandle(int... shape) {
            Objects.requireNonNull(shape);
            MemoryLayout layout = self();
            List<MemoryLayout.PathElement> path = new ArrayList<>();
            for (int i = shape.length; i > 0; i--) {
                int size = shape[i - 1];
                if (size < 0) throw new IllegalArgumentException("Invalid shape size: " + size);
                layout = MemoryLayout.sequenceLayout(size, layout);
                path.add(MemoryLayout.PathElement.sequenceElement());
            }
            layout = MemoryLayout.sequenceLayout(layout);
            path.add(MemoryLayout.PathElement.sequenceElement());
            return layout.varHandle(path.toArray(new MemoryLayout.PathElement[0]));
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
        abstract V dup(long bitAlignment, Optional<String> name);

        static void checkCarrierSize(Class<?> carrier, long size) {
            if (!isValidCarrier(carrier)) {
                throw new IllegalArgumentException("Invalid carrier: " + carrier.getName());
            }
            if (carrier == MemorySegment.class && size != ADDRESS_SIZE_BITS) {
                throw new IllegalArgumentException("Address size mismatch: " + ADDRESS_SIZE_BITS + " != " + size);
            }
            if (carrier.isPrimitive()) {
                int expectedSize = carrier == boolean.class ? 8 : Wrapper.forPrimitiveType(carrier).bitWidth();
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
                    || carrier == MemorySegment.class;
        }


        @ForceInline
        public final VarHandle accessHandle() {
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

        private OfBooleanImpl(ByteOrder order) {
            super(boolean.class, order, 8);
        }

        private OfBooleanImpl(ByteOrder order, long bitAlignment, Optional<String> name) {
            super(boolean.class, order, 8, bitAlignment, name);
        }

        @Override
        OfBooleanImpl dup(long bitAlignment, Optional<String> name) {
            return new OfBooleanImpl(order(), bitAlignment, name);
        }

        @Override
        public OfBooleanImpl withOrder(ByteOrder order) {
            Objects.requireNonNull(order);
            return new OfBooleanImpl(order, bitAlignment(), name());
        }

        public static OfBoolean of(ByteOrder order) {
            return new OfBooleanImpl(order);
        }
    }

    public static final class OfByteImpl extends AbstractValueLayout<OfByteImpl> implements ValueLayout.OfByte {

        private OfByteImpl(ByteOrder order) {
            super(byte.class, order, 8);
        }

        private OfByteImpl(ByteOrder order, long bitAlignment, Optional<String> name) {
            super(byte.class, order, 8, bitAlignment, name);
        }

        @Override
        OfByteImpl dup(long bitAlignment, Optional<String> name) {
            return new OfByteImpl(order(), bitAlignment, name);
        }

        @Override
        public OfByteImpl withOrder(ByteOrder order) {
            Objects.requireNonNull(order);
            return new OfByteImpl(order, bitAlignment(), name());
        }

        public static OfByte of(ByteOrder order) {
            return new OfByteImpl(order);
        }
    }

    public static final class OfCharImpl extends AbstractValueLayout<OfCharImpl> implements ValueLayout.OfChar {

        private OfCharImpl(ByteOrder order) {
            super(char.class, order, 16);
        }

        private OfCharImpl(ByteOrder order, long bitAlignment, Optional<String> name) {
            super(char.class, order, 16, bitAlignment, name);
        }

        @Override
        OfCharImpl dup(long bitAlignment, Optional<String> name) {
            return new OfCharImpl(order(), bitAlignment, name);
        }

        @Override
        public OfCharImpl withOrder(ByteOrder order) {
            Objects.requireNonNull(order);
            return new OfCharImpl(order, bitAlignment(), name());
        }

        public static OfChar of(ByteOrder order) {
            return new OfCharImpl(order);
        }
    }

    public static final class OfShortImpl extends AbstractValueLayout<OfShortImpl> implements ValueLayout.OfShort {

        private OfShortImpl(ByteOrder order) {
            super(short.class, order, 16);
        }

        private OfShortImpl(ByteOrder order, long bitAlignment, Optional<String> name) {
            super(short.class, order, 16, bitAlignment, name);
        }

        @Override
        OfShortImpl dup(long bitAlignment, Optional<String> name) {
            return new OfShortImpl(order(), bitAlignment, name);
        }

        @Override
        public OfShortImpl withOrder(ByteOrder order) {
            Objects.requireNonNull(order);
            return new OfShortImpl(order, bitAlignment(), name());
        }

        public static OfShort of(ByteOrder order) {
            return new OfShortImpl(order);
        }
    }

    public static final class OfIntImpl extends AbstractValueLayout<OfIntImpl> implements ValueLayout.OfInt {

        private OfIntImpl(ByteOrder order) {
            super(int.class, order, 32);
        }

        private OfIntImpl(ByteOrder order, long bitAlignment, Optional<String> name) {
            super(int.class, order, 32, bitAlignment, name);
        }

        @Override
        OfIntImpl dup(long bitAlignment, Optional<String> name) {
            return new OfIntImpl(order(), bitAlignment, name);
        }

        @Override
        public OfIntImpl withOrder(ByteOrder order) {
            Objects.requireNonNull(order);
            return new OfIntImpl(order, bitAlignment(), name());
        }

        public static OfInt of(ByteOrder order) {
            return new OfIntImpl(order);
        }
    }

    public static final class OfFloatImpl extends AbstractValueLayout<OfFloatImpl> implements ValueLayout.OfFloat {

        private OfFloatImpl(ByteOrder order) {
            super(float.class, order, 32);
        }

        private OfFloatImpl(ByteOrder order, long bitAlignment, Optional<String> name) {
            super(float.class, order, 32, bitAlignment, name);
        }

        @Override
        OfFloatImpl dup(long bitAlignment, Optional<String> name) {
            return new OfFloatImpl(order(), bitAlignment, name);
        }

        @Override
        public OfFloatImpl withOrder(ByteOrder order) {
            Objects.requireNonNull(order);
            return new OfFloatImpl(order, bitAlignment(), name());
        }

        public static OfFloat of(ByteOrder order) {
            return new OfFloatImpl(order);
        }
    }

    public static final class OfLongImpl extends AbstractValueLayout<OfLongImpl> implements ValueLayout.OfLong {

        private OfLongImpl(ByteOrder order) {
            super(long.class, order, 64);
        }

        private OfLongImpl(ByteOrder order, long bitAlignment, Optional<String> name) {
            super(long.class, order, 64, bitAlignment, name);
        }

        @Override
        OfLongImpl dup(long bitAlignment, Optional<String> name) {
            return new OfLongImpl(order(), bitAlignment, name);
        }

        @Override
        public OfLongImpl withOrder(ByteOrder order) {
            Objects.requireNonNull(order);
            return new OfLongImpl(order, bitAlignment(), name());
        }

        public static OfLong of(ByteOrder order) {
            return new OfLongImpl(order);
        }
    }

    public static final class OfDoubleImpl extends AbstractValueLayout<OfDoubleImpl> implements ValueLayout.OfDouble {

        private OfDoubleImpl(ByteOrder order) {
            super(double.class, order, 64);
        }

        private OfDoubleImpl(ByteOrder order, long bitAlignment, Optional<String> name) {
            super(double.class, order, 64, bitAlignment, name);
        }

        @Override
        OfDoubleImpl dup(long bitAlignment, Optional<String> name) {
            return new OfDoubleImpl(order(), bitAlignment, name);
        }

        @Override
        public OfDoubleImpl withOrder(ByteOrder order) {
            Objects.requireNonNull(order);
            return new OfDoubleImpl(order, bitAlignment(), name());
        }

        public static OfDouble of(ByteOrder order) {
            return new OfDoubleImpl(order);
        }

    }

    public static final class OfAddressImpl extends AbstractValueLayout<OfAddressImpl> implements ValueLayout.OfAddress {

        private final boolean isUnbounded;

        private OfAddressImpl(ByteOrder order) {
            super(MemorySegment.class, order, ADDRESS_SIZE_BITS);
            this.isUnbounded = false; // safe
        }

        private OfAddressImpl(ByteOrder order, long size, long bitAlignment, boolean isUnbounded, Optional<String> name) {
            super(MemorySegment.class, order, size, bitAlignment, name);
            this.isUnbounded = isUnbounded;
        }

        @Override
        OfAddressImpl dup(long alignment, Optional<String> name) {
            return new OfAddressImpl(order(), bitSize(), alignment, isUnbounded, name);
        }

        @Override
        public OfAddressImpl withOrder(ByteOrder order) {
            Objects.requireNonNull(order);
            return new OfAddressImpl(order, bitSize(), bitAlignment(), isUnbounded, name());
        }

        @Override
        public boolean equals(Object other) {
            return super.equals(other) &&
                    ((OfAddressImpl) other).isUnbounded == this.isUnbounded;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), isUnbounded);
        }

        @Override
        @CallerSensitive
        public OfAddress asUnbounded() {
            Reflection.ensureNativeAccess(Reflection.getCallerClass(), OfAddress.class, "asUnbounded");
            return new OfAddressImpl(order(), bitSize(), bitAlignment(), true, name());
        }

        @Override
        public boolean isUnbounded() {
            return isUnbounded;
        }

        public static OfAddress of(ByteOrder order) {
            return new OfAddressImpl(order);
        }
    }

}
