/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.lang.monotonic;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Supplier;

public sealed interface InternalMonotonic<V> extends Monotonic<V> {

    Unsafe UNSAFE = Unsafe.getUnsafe();

    final class ReferenceMonotonic<V> implements InternalMonotonic<V> {

        private static final long VALUE_OFFSET =
                UNSAFE.objectFieldOffset(NullableReferenceMonotonic.class, "value");

        @Stable
        private V value;

        @Override
        public V get() {
            // Optimistically try plain semantics first
            V v = value;
            if (v != null) {
                return v;
            }
            // Now, fall back to volatile semantics
            v = valueVolatile();
            if (v != null) {
                return v;
            }
            throw noSuchElement();
        }

        @Override
        public boolean isBound() {
            return value != null || valueVolatile() != null;
        }

        @Override
        public void bind(V value) {
            Objects.requireNonNull(value);
            freeze();
            if (!casValue(value)) {
                throw valueAlreadyBound(get());
            }

        }

        @Override
        public V computeIfUnbound(Supplier<? extends V> supplier) {
            // Optimistically try plain semantics first
            V v = value;
            if (v != null) {
                return v;
            }
            // Now, fall back to volatile semantics
            v = valueVolatile();
            if (v != null) {
                return v;
            }
            v = supplier.get();
            freeze();
            Objects.requireNonNull(v);
            V witness = caeValue(v);
            if (witness == null) {
                return v;
            }
            return witness;
        }

        @Override
        public MethodHandle getter() {
            class Holder {
                static MethodHandle HANDLE;
                static {
                    try {
                        HANDLE = MethodHandles.lookup()
                                .findGetter(ReferenceMonotonic.class, "value", Object.class);
                    } catch (ReflectiveOperationException e) {
                        throw new ExceptionInInitializerError(e);
                    }
                }
            }
            return Holder.HANDLE;
        }

        @Override
        public String toString() {
            return InternalMonotonic.toString(this);
        }

        @SuppressWarnings("unchecked")
        private V valueVolatile() {
            return (V) UNSAFE.getReferenceVolatile(this, VALUE_OFFSET);
        }

        private boolean casValue(V value) {
            return UNSAFE.compareAndSetReference(this, VALUE_OFFSET, null, value);
        }

        @SuppressWarnings("unchecked")
        private V caeValue(V value) {
            return (V) UNSAFE.compareAndExchangeReference(this, VALUE_OFFSET, null, value);
        }
    }

    final class NullableReferenceMonotonic<V> extends AbstractNullableMonotonic<V> implements InternalMonotonic<V> {

        private static final long VALUE_OFFSET = UNSAFE.objectFieldOffset(NullableReferenceMonotonic.class, "value");

        @Stable
        private V value;

        @Override
        public V get() {
            // Optimistically try plain semantics first
            V v = value;
            if (v != null) {
                return v;
            }
            if (bound) {
                return null;
            }
            // Now, fall back to volatile semantics
            v = valueVolatile();
            if (v != null) {
                return v;
            }
            if (boundVolatile()) {
                return null;
            }
            throw noSuchElement();
        }

        @Override
        public synchronized void bind(V value) {
            if (!casValue(value) || !casBound()) {
                throw valueAlreadyBound(get());
            }
            freeze();
        }

        @Override
        public V computeIfUnbound(Supplier<? extends V> supplier) {
            // Optimistically try plain semantics first
            V v = value;
            if (v != null) {
                return v;
            }
            if (bound) {
                return null;
            }
            // Now, fall back to volatile semantics
            v = valueVolatile();
            if (v != null) {
                return v;
            }
            if (boundVolatile()) {
                return null;
            }
            synchronized (this) {
                v = supplier.get(); // Nullable
                V witness = caeValue(supplier.get());
                if (witness == null && !bound) {
                    casBound();
                }
                if (witness == null) {
                    freeze();
                }
                return witness == null ? v : witness;
            }
        }

        @Override
        public MethodHandle getter() {
            class Holder {
                static MethodHandle HANDLE;
                static {
                    try {
                        HANDLE = MethodHandles.lookup()
                                .findGetter(NullableReferenceMonotonic.class, "value", Object.class);
                    } catch (ReflectiveOperationException e) {
                        throw new ExceptionInInitializerError(e);
                    }
                }
            }
            return Holder.HANDLE;
        }

        @SuppressWarnings("unchecked")
        private V valueVolatile() {
            return (V) UNSAFE.getReferenceVolatile(this, VALUE_OFFSET);
        }

        private boolean casValue(V value) {
            return UNSAFE.compareAndSetReference(this, VALUE_OFFSET, null, value);
        }

        @SuppressWarnings("unchecked")
        private V caeValue(V value) {
            return (V) UNSAFE.compareAndExchangeReference(this, VALUE_OFFSET, null, value);
        }

    }

    final class IntMonotonic extends AbstractNullableMonotonic<Integer> implements InternalMonotonic<Integer> {

        private static final long VALUE_OFFSET = UNSAFE.objectFieldOffset(IntMonotonic.class, "value");

        @Stable
        private int value;

        @Override
        public Integer get() {
            // Optimistically try plain semantics first
            int v = value;
            if (v != 0) {
                return v;
            }
            if (bound) {
                return 0;
            }
            // Now, fall back to volatile semantics
            v = valueVolatile();
            if (v != 0) {
                return v;
            }
            if (boundVolatile()) {
                return 0;
            }
            throw noSuchElement();
        }

        @Override
        public Integer computeIfUnbound(Supplier<? extends Integer> supplier) {
            // Optimistically try plain semantics first
            int v = value;
            if (v != 0) {
                return v;
            }
            if (bound) {
                return 0;
            }
            // Now, fall back to volatile semantics
            v = valueVolatile();
            if (v != 0) {
                return v;
            }
            if (boundVolatile()) {
                return 0;
            }
            Integer newV = supplier.get();
            v = Objects.requireNonNull(newV);
            int witness = caeValue(v);
            if (witness == 0 && !bound) {
                casBound();
            }
            // No freeze needed for primitive values
            return witness == 0 ? v : witness;
        }

        @Override
        public void bind(Integer value) {
            Objects.requireNonNull(value);
            // Several threads might succeed in binding null values
            if (casValue(value)) {
                casBound();
                // No freeze needed for primitive values
            }
            throw valueAlreadyBound(get());
        }

        @Override
        public MethodHandle getter() {
            class Holder {
                static MethodHandle HANDLE;
                static {
                    try {
                        HANDLE = MethodHandles.lookup()
                                .findGetter(IntMonotonic.class, "value", int.class);
                    } catch (ReflectiveOperationException e) {
                        throw new ExceptionInInitializerError(e);
                    }
                }
            }
            return Holder.HANDLE;
        }

        private int valueVolatile() {
            return UNSAFE.getIntVolatile(this, VALUE_OFFSET);
        }

        private boolean casValue(int value) {
            return UNSAFE.compareAndSetInt(this, VALUE_OFFSET, 0, value);
        }

        private int caeValue(int value) {
            return UNSAFE.compareAndExchangeInt(this, VALUE_OFFSET, 0, value);
        }

    }

    final class LongMonotonic extends AbstractNullableMonotonic<Long> implements InternalMonotonic<Long> {

        private static final long VALUE_OFFSET = UNSAFE.objectFieldOffset(LongMonotonic.class, "value");

        @Stable
        private long value;

        @Override
        public Long get() {
            // Optimistically try plain semantics first
            long v = value;
            if (v != 0) {
                return v;
            }
            if (bound) {
                return 0L;
            }
            // Now, fall back to volatile semantics
            v = valueVolatile();
            if (v != 0) {
                return v;
            }
            if (boundVolatile()) {
                return 0L;
            }
            throw noSuchElement();
        }

        @Override
        public Long computeIfUnbound(Supplier<? extends Long> supplier) {
            // Optimistically try plain semantics first
            long v = value;
            if (v != 0) {
                return v;
            }
            if (bound) {
                return 0L;
            }
            // Now, fall back to volatile semantics
            v = valueVolatile();
            if (v != 0) {
                return v;
            }
            if (boundVolatile()) {
                return 0L;
            }
            Long newV = supplier.get();
            v = Objects.requireNonNull(newV);
            long witness = caeValue(v);
            if (witness == 0 && !bound) {
                casBound();
            }
            // No freeze needed for primitive values
            return witness == 0 ? v : witness;
        }

        @Override
        public void bind(Long value) {
            Objects.requireNonNull(value);
            // Several threads might succeed in binding null values
            if (casValue(value)) {
                casBound();
                // No freeze needed for primitive values
            }
            throw valueAlreadyBound(get());
        }

        @Override
        public MethodHandle getter() {
            class Holder {
                static MethodHandle HANDLE;
                static {
                    try {
                        HANDLE = MethodHandles.lookup()
                                .findGetter(LongMonotonic.class, "value", long.class);
                    } catch (ReflectiveOperationException e) {
                        throw new ExceptionInInitializerError(e);
                    }
                }
            }
            return Holder.HANDLE;
        }

        private int valueVolatile() {
            return UNSAFE.getIntVolatile(this, VALUE_OFFSET);
        }

        private boolean casValue(long value) {
            return UNSAFE.compareAndSetLong(this, VALUE_OFFSET, 0, value);
        }

        private long caeValue(long value) {
            return UNSAFE.compareAndExchangeLong(this, VALUE_OFFSET, 0, value);
        }

    }

    abstract sealed class AbstractNullableMonotonic<V> implements InternalMonotonic<V> {


        private static final long BOUND_OFFSET = UNSAFE.objectFieldOffset(NullableReferenceMonotonic.class, "bound");

        @Stable
        boolean bound;

        @Override
        public boolean isBound() {
            return bound || boundVolatile();
        }

        @Override
        public final String toString() {
            return "Monotonic" +
                    (isBound()
                            ? "[" + get() + "]"
                            : ".unbound");
        }


        protected final boolean boundVolatile() {
            return UNSAFE.getBooleanVolatile(this, BOUND_OFFSET);
        }

        protected final boolean casBound() {
            return UNSAFE.compareAndSetBoolean(this, BOUND_OFFSET, false, true);
        }
    }

    static String toString(Monotonic<?> monotonic) {
        return "Monotonic" +
                (monotonic.isBound()
                        ? "[" + monotonic.get() + "]"
                        : ".unbound");
    }

    static NoSuchElementException noSuchElement() {
        return new NoSuchElementException();
    }

    static IllegalStateException valueAlreadyBound(Object value) {
        return new IllegalStateException("A value is already bound: " + value);
    }

    @SuppressWarnings("unchecked")
    static <V> Monotonic<V> of(Class<V> backingType) {
        return (Monotonic<V>) switch (backingType) {
            case Class<V> c when c.equals(int.class) -> new IntMonotonic();
            case Class<V> c when c.equals(long.class) -> new LongMonotonic();
            default -> new ReferenceMonotonic<>();
        };
    }

    /**
     * Performs a "freeze" operation, required to ensure safe publication under plain memory read
     * semantics.
     * <p>
     * This inserts a memory barrier, thereby establishing a happens-before constraint. This
     * prevents the reordering of store operations across the freeze boundary.
     */
    static void freeze() {
        // Issue a store fence, which is sufficient
        // to provide protection against store/store reordering.
        // See VarHandle::releaseFence
        UNSAFE.storeFence();
    }

    static <V> Monotonic<V> ofNullable(Class<V> backingType) {
        return new NullableReferenceMonotonic<>();
    }

    static <V> MonotonicList<V> ofList(Class<V> backingElementType,
                                       int size) {
        Objects.requireNonNull(backingElementType);
        return new InternalMonotonicList.MonotonicListImpl<>(backingElementType, size);
    }

    static <K, V> MonotonicMap<K, V> ofMap(Class<V> backingValueType,
                                           Collection<? extends K> keys) {

        throw new UnsupportedOperationException();
    }
}
