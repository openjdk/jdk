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

import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Supplier;

public sealed interface InternalMonotonic<V> extends Monotonic<V> {

    final class ReferenceMonotonic<V> implements InternalMonotonic<V> {

        private static final long VALUE_OFFSET =
                MonotonicUtil.UNSAFE.objectFieldOffset(ReferenceMonotonic.class, "value");

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
        public boolean isPresent() {
            return value != null || valueVolatile() != null;
        }

        @Override
        public void put(V value) {
            Objects.requireNonNull(value);
            freeze();
            if (caeValue(value) != null) {
                throw valueAlreadyBound(get());
            }
        }

        @Override
        public V putIfAbsent(V value) {
            Objects.requireNonNull(value);
            freeze();
            V witness = caeValue(value);
            return witness == null ? value : witness;
        }

        @Override
        public V computeIfAbsent(Supplier<? extends V> supplier) {
            return computeIfUnbound0(supplier, Supplier::get);
        }

        @SuppressWarnings("unchecked")
        @Override
        public V computeIfAbsent(MethodHandle supplier) {
            return computeIfUnbound0(supplier, s -> (V) (Object) s.invokeExact());
        }

        private <T> V computeIfUnbound0(T supplier, MonotonicUtil.ThrowingFunction<T, V> mapper) {
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
            V witness;
            // Make sure the supplier is only invoked at most once by this method
            synchronized (supplier) {
                // Re-check
                v = valueVolatile();
                if (v != null) {
                    return get();
                }
                try {
                    v = mapper.apply(supplier);
                } catch (Throwable t) {
                    if (t instanceof Error e) {
                        throw e;
                    }
                    throw new NoSuchElementException(t);
                }
                Objects.requireNonNull(v);
                freeze();
                witness = caeValue(v);
            }
            if (witness == null) {
                return v;
            }
            return witness;
        }

        @Override
        public MethodHandle getter() {
            class Holder {
                static final MethodHandle HANDLE;
                static {
                    try {
                        HANDLE = MethodHandles.lookup()
                                .findVirtual(ReferenceMonotonic.class, "get", MethodType.methodType(Object.class))
                                .asType(MethodType.methodType(Object.class, Monotonic.class));;
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
            return (V) MonotonicUtil.UNSAFE.getReferenceVolatile(this, VALUE_OFFSET);
        }

        @SuppressWarnings("unchecked")
        private V caeValue(V value) {
            return (V) MonotonicUtil.UNSAFE.compareAndExchangeReference(this, VALUE_OFFSET, null, value);
        }
    }

    final class NullableReferenceMonotonic<V> extends AbstractNullableMonotonic<V> implements InternalMonotonic<V> {

        private static final long VALUE_OFFSET = MonotonicUtil.UNSAFE.objectFieldOffset(NullableReferenceMonotonic.class, "value");

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
        public synchronized void put(V value) {
            Objects.requireNonNull(value);
            freeze();
            // Prevent several threads from succeeding in binding null values
            if (caeValue(value) != null || !casBound()) {
                throw valueAlreadyBound(get());
            }
        }

        @Override
        public V putIfAbsent(V value) {
            Objects.requireNonNull(value);
            freeze();
            V witness = caeValue(value);
            return witness == null ? value : witness;
        }

        @Override
        public V computeIfAbsent(Supplier<? extends V> supplier) {
            return computeIfUnbound0(supplier, Supplier::get);
        }

        @SuppressWarnings("unchecked")
        @Override
        public V computeIfAbsent(MethodHandle supplier) {
            return computeIfUnbound0(supplier, s -> (V) (Object) s.invokeExact());
        }

        private <T> V computeIfUnbound0(T supplier, MonotonicUtil.ThrowingFunction<T, V> mapper) {
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
            // Make sure the supplier is only invoked at most once by this method
            synchronized (supplier) {
                // Re-check
                if (boundVolatile()) {
                    return get();
                }
                try {
                    v = mapper.apply(supplier); // Nullable
                } catch (Throwable t) {
                    if (t instanceof Error e) {
                        throw e;
                    }
                    throw new NoSuchElementException(t);
                }
                V witness = caeValue(v);
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
                static final MethodHandle HANDLE;
                static {
                    try {
                        HANDLE = MethodHandles.lookup()
                                .findVirtual(NullableReferenceMonotonic.class, "get", MethodType.methodType(Object.class))
                                .asType(MethodType.methodType(Object.class, Monotonic.class));;
                    } catch (ReflectiveOperationException e) {
                        throw new ExceptionInInitializerError(e);
                    }
                }
            }
            return Holder.HANDLE;
        }

        @SuppressWarnings("unchecked")
        private V valueVolatile() {
            return (V) MonotonicUtil.UNSAFE.getReferenceVolatile(this, VALUE_OFFSET);
        }

        @SuppressWarnings("unchecked")
        private V caeValue(V value) {
            return (V) MonotonicUtil.UNSAFE.compareAndExchangeReference(this, VALUE_OFFSET, null, value);
        }

    }

    final class IntMonotonic extends AbstractNullableMonotonic<Integer> implements InternalMonotonic<Integer> {

        private static final long VALUE_OFFSET = MonotonicUtil.UNSAFE.objectFieldOffset(IntMonotonic.class, "value");

        @Stable
        private int value;

        @Override
        public Integer get() {
            return get0();
        }

        private int get0() {
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
        public Integer computeIfAbsent(Supplier<? extends Integer> supplier) {
            return computeIfUnbound0(supplier, Supplier::get);
        }

        @Override
        public Integer computeIfAbsent(MethodHandle supplier) {
            return computeIfUnbound0(supplier, s -> (Integer) (Object)s.invokeExact());
        }

        private <T> Integer computeIfUnbound0(T supplier, MonotonicUtil.ThrowingFunction<T, ? extends Integer> mapper) {
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

            int witness;
            // Make sure the supplier is only invoked at most once by this method
            synchronized (supplier) {
                // Re-check
                if (boundVolatile()) {
                    return get();
                }
                try {
                    v = mapper.apply(supplier);
                } catch (Throwable t) {
                    if (t instanceof Error e) {
                        throw e;
                    }
                    throw new NoSuchElementException(t);
                }

                // No freeze needed for primitive values
                witness = caeValue(v);
            }
            if (witness == 0 && !bound) {
                casBound();
            }
            return witness == 0 ? v : witness;
        }

        @Override
        public void put(Integer value) {
            Objects.requireNonNull(value);
            // No freeze needed for primitive values
            // Prevent several threads from succeeding in binding zero values
            if (caeValue(value) != 0 || !casBound()) {
                throw valueAlreadyBound(get());
            }
        }

        @Override
        public Integer putIfAbsent(Integer value) {
            Objects.requireNonNull(value);
            // No freeze needed for primitive values
            int witness = caeValue(value);
            return witness == 0 ? value : witness;
        }

        @Override
        public MethodHandle getter() {
            class Holder {
                static final MethodHandle HANDLE;
                static {
                    try {
                        HANDLE = MethodHandles.lookup()
                                .findVirtual(IntMonotonic.class, "get0", MethodType.methodType(int.class))
                                .asType(MethodType.methodType(int.class, Monotonic.class));
                    } catch (ReflectiveOperationException e) {
                        throw new ExceptionInInitializerError(e);
                    }
                }
            }
            return Holder.HANDLE;
        }

        private int valueVolatile() {
            return MonotonicUtil.UNSAFE.getIntVolatile(this, VALUE_OFFSET);
        }

        private int caeValue(int value) {
            return MonotonicUtil.UNSAFE.compareAndExchangeInt(this, VALUE_OFFSET, 0, value);
        }

    }

    final class LongMonotonic extends AbstractNullableMonotonic<Long> implements InternalMonotonic<Long> {

        private static final long VALUE_OFFSET = MonotonicUtil.UNSAFE.objectFieldOffset(LongMonotonic.class, "value");

        @Stable
        private long value;

        @Override
        public Long get() {
            return get0();
        }

        private long get0() {
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
        public Long computeIfAbsent(Supplier<? extends Long> supplier) {
            return computeIfUnbound0(supplier, Supplier::get);
        }

        @Override
        public Long computeIfAbsent(MethodHandle supplier) {
            return computeIfUnbound0(supplier, s -> (Long) (Object)s.invokeExact());
        }

        private <T> Long computeIfUnbound0(T supplier, MonotonicUtil.ThrowingFunction<T, ? extends Long> mapper) {
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

            long witness;
            // Make sure the supplier is only invoked at most once by this method
            synchronized (supplier) {
                // Re-check
                if (boundVolatile()) {
                    return get();
                }
                try {
                    v = mapper.apply(supplier);
                } catch (Throwable t) {
                    if (t instanceof Error e) {
                        throw e;
                    }
                    throw new NoSuchElementException(t);
                }

                // No freeze needed for primitive values
                witness = caeValue(v);
            }
            if (witness == 0 && !bound) {
                casBound();
            }
            return witness == 0 ? v : witness;
        }

        @Override
        public void put(Long value) {
            Objects.requireNonNull(value);
            // No freeze needed for primitive values
            // Prevent several threads from succeeding in binding zero values
            if (caeValue(value) != 0 || !casBound()) {
                throw valueAlreadyBound(get());
            }
        }

        @Override
        public Long putIfAbsent(Long value) {
            Objects.requireNonNull(value);
            // No freeze needed for primitive values
            long witness = caeValue(value);
            return witness == 0 ? value : witness;
        }

        @Override
        public MethodHandle getter() {
            class Holder {
                static final MethodHandle HANDLE;
                static {
                    try {
                        HANDLE = MethodHandles.lookup()
                                .findVirtual(LongMonotonic.class, "get0", MethodType.methodType(long.class))
                                .asType(MethodType.methodType(long.class, Monotonic.class));
                    } catch (ReflectiveOperationException e) {
                        throw new ExceptionInInitializerError(e);
                    }
                }
            }
            return Holder.HANDLE;
        }

        private int valueVolatile() {
            return MonotonicUtil.UNSAFE.getIntVolatile(this, VALUE_OFFSET);
        }

        private boolean casValue(long value) {
            return MonotonicUtil.UNSAFE.compareAndSetLong(this, VALUE_OFFSET, 0, value);
        }

        private long caeValue(long value) {
            return MonotonicUtil.UNSAFE.compareAndExchangeLong(this, VALUE_OFFSET, 0, value);
        }

    }

    abstract sealed class AbstractNullableMonotonic<V> implements InternalMonotonic<V> {

        private static final long BOUND_OFFSET = MonotonicUtil.UNSAFE.objectFieldOffset(AbstractNullableMonotonic.class, "bound");

        @Stable
        boolean bound;

        @Override
        public boolean isPresent() {
            return bound || boundVolatile();
        }

        @Override
        public final String toString() {
            return "Monotonic" +
                    (isPresent()
                            ? "[" + get() + "]"
                            : ".unbound");
        }


        protected final boolean boundVolatile() {
            return MonotonicUtil.UNSAFE.getBooleanVolatile(this, BOUND_OFFSET);
        }

        protected final boolean casBound() {
            return MonotonicUtil.UNSAFE.compareAndSetBoolean(this, BOUND_OFFSET, false, true);
        }
    }

    static String toString(Monotonic<?> monotonic) {
        return "Monotonic" +
                (monotonic.isPresent()
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
    static <V> Monotonic<V> of(Class<? extends V> backingType) {
        return (Monotonic<V>) switch (backingType) {
            case Class<? extends V> c when c.equals(int.class) -> new IntMonotonic();
            case Class<? extends V> c when c.equals(long.class) -> new LongMonotonic();
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
        MonotonicUtil.UNSAFE.storeFence();
    }

    static <V> Monotonic<V> ofNullable(Class<? extends V> backingType) {
        return new NullableReferenceMonotonic<>();
    }

    static <V> List<V> ofList(Class<? extends V> backingElementType,
                              int size) {
        Objects.requireNonNull(backingElementType);
        return new InternalMonotonicList.ReferenceList<>(size);
    }

    static <K, V> Map<K, V> ofMap(Class<? extends V> backingValueType,
                                  Collection<? extends K> keys) {
        return new InternalMonotonicMap.MonotonicMapImpl<>(backingValueType, keys.toArray());
    }
}
