/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package java.util.concurrent.lazy;

import jdk.internal.javac.PreviewFeature;
import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

// Build time, background, etc. computation (time shifting) (referentially transparent)

/**
 * An object reference in which the value can be lazily and atomically computed.
 * <p>
 * At most one invocation is made of any provided set of suppliers.
 * <p>
 * This contrasts to {@link AtomicReference } where any number of updates can be done
 * and where there is no simple way to atomically compute
 * a value (guaranteed to only be computed once) if missing.
 * <p>
 * The implementation is optimized for the case where there are N invocations
 * trying to obtain the value and where N >> 1, for example where N is > 2<sup>20</sup>.
 * <p>
 * A supplier may return {@code null} which then will be perpetually recorded as the value.
 * <p>
 * This class is thread-safe.
 * <p>
 * The JVM may apply certain optimizations as it knows the value is updated just once
 * at most as described by {@link Stable} as exemplified here:
 * {@snippet lang = java:
 *     private static final LazyReference<Value> MY_LAZY_VALUE = Lazy.of(Value::new);
 *     // ...
 *     public Value value() {
 *         // This will likely be constant-folded by the JIT C2 compiler.
 *         return MY_LAZY_VALUE.get();
 *     }
 *}
 *
 * @param <V> The type of the value to be recorded
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY)
public final class LazyReference<V>
        implements Supplier<V> {

    // Allows access to the state variable with arbitary memory semantics
    private static final VarHandle VALUE_HANDLE = valueHandle();

    private final Lazy.Evaluation earliestEvaluation;
    private final Semaphore semaphore;

    private Supplier<? extends V> presetProvider;

    @Stable
    private Object value;

    LazyReference(Lazy.Evaluation earliestEvaluation,
                  Supplier<? extends V> presetSupplier) {
        this.earliestEvaluation = earliestEvaluation;
        this.presetProvider = presetSupplier;
        this.semaphore = new Semaphore(1);
        if (earliestEvaluation != Lazy.Evaluation.AT_USE && presetSupplier != null) {
            // Start computing the value via a background Thread.
            Thread.ofVirtual()
                    .name("Lazy evaluator: " + presetSupplier)
                    .start(() -> supplyIfEmpty0(presetSupplier));
        }
    }

    // To be called by builders/compilers/destillers to eagerly pre-compute a value (e.g. Constable)
    LazyReference(V value) {
        this.earliestEvaluation = Lazy.Evaluation.CREATION;
        this.presetProvider = null;
        this.value = Objects.requireNonNull(value);
        this.semaphore = null;
    }

    /**
     * {@return The {@link Lazy.State } of this Lazy}.
     * <p>
     * The value is a snapshot of the current State.
     * No attempt is made to compute a value if it is not already present.
     * <p>
     * If the returned State is either {@link Lazy.State#PRESENT} or
     * {@link Lazy.State#ERROR}, it is guaranteed the state will
     * never change in the future.
     * <p>
     * This method can be used to act on a value if it is present:
     * {@snippet lang = java:
     *     if (lazy.state() == State.PRESENT) {
     *         T value = lazy.get();
     *         // perform action on the value
     *     }
     *}
     */
    public Lazy.State state() {
        Object o = value;
        if (o != null) {
            return o instanceof Exception
                    ? Lazy.State.ERROR
                    : Lazy.State.PRESENT;
        }

        if (semaphore.availablePermits() == 0) {
            return Lazy.State.CONSTRUCTING;
        }

        semaphore.acquireUninterruptibly();
        try {
            o = value;
            if (o instanceof Exception) {
                return Lazy.State.ERROR;
            }
            if (o == null) {
                return Lazy.State.EMPTY;
            }
            return Lazy.State.PRESENT;
        } finally {
            semaphore.release();
        }
    }

    /**
     * {@return The erliest point at which this Lazy can be evaluated}.
     */
    Lazy.Evaluation earliestEvaluation() {
        return earliestEvaluation;
    }

    /**
     * Returns the present value or, if no present value exists, atomically attempts
     * to compute the value using the <em>pre-set {@linkplain Lazy#of(Supplier)} supplier}</em>.
     * If no pre-set {@linkplain Lazy#of(Supplier)} supplier} exists,
     * throws an IllegalStateException exception.
     * <p>
     * If the pre-set supplier itself throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded. The most
     * common usage is to construct a new object serving as a memoized result, as in:
     * <p>
     * {@snippet lang = java:
     *    LazyReference<V> lazy = Lazy.of(Value::new);
     *    // ...
     *    V value = lazy.get();
     *    assertNotNull(value); // Value is non-null
     *}
     * <p>
     * If another thread attempts to compute the value, the current thread will be suspended until
     * the atempt completes (successfully or not).
     *
     * @return the value (pre-existing or newly computed)
     * @throws NullPointerException   if the pre-set supplier returns {@code null}.
     * @throws IllegalStateException  if a value was not already present and no
     *                                pre-set supplier was specified.
     * @throws NoSuchElementException if a supplier has previously thrown an exception.
     */
    @SuppressWarnings("unchecked")
    public V get() {
        try {
            V v = (V) value;
            if (v != null) {
                return v;
            }
            return supplyIfEmpty0(presetProvider);
        } catch (ClassCastException cce) {
            throw new NoSuchElementException((Throwable) value);
        }
    }

    /**
     * Returns the present value or, if no present value exists, atomically attempts
     * to compute the value using the <em>provided {@code supplier}</em>.
     * <p>
     * If the provided {@code supplier} itself throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded.  The most
     * common usage is to construct a new object serving as a memoized result, as in:
     * <p>
     * {@snippet lang = java:
     *    LazyReference<V> lazy = Lazy.ofEmpty();
     *    // ...
     *    V value = lazy.supplyIfAbsent(Value::new);
     *    assertNotNull(value); // Value is non-null
     *}
     * <p>
     * If another thread attempts to compute the value, the current thread will be suspended until
     * the atempt completes (successfully or not).
     *
     * @param supplier to apply if no previous value exists
     * @return the value (pre-existing or newly computed)
     * @throws NullPointerException   if the provided {@code supplier} is {@code null} or if the provider
     *                                {@code supplier} returns {@code null}.
     * @throws NoSuchElementException if a supplier has previously thrown an exception.
     */
    public V supplyIfEmpty(Supplier<? extends V> supplier) {
        Objects.requireNonNull(supplier);
        return supplyIfEmpty0(supplier);
    }

    /**
     * {@return the excption thrown by the supplier invoked or
     * {@link Optional#empty()} if no exception was thrown}.
     */
    public Optional<Throwable> exception() {
        return state() == Lazy.State.ERROR
                ? Optional.of((Throwable) value)
                : Optional.empty();
    }

    private V supplyIfEmpty0(Supplier<? extends V> supplier) {
        Object o = value;
        if (o != null) {
            return castOrThrow(o);
        }

        // implies acquire/release semantics when entering/leaving the monitor
        semaphore.acquireUninterruptibly();
        try {
            // Here, visibility is guaranteed
            o = value;
            if (o != null) {
                return castOrThrow(o);
            }
            try {
                if (supplier == null) {
                    throw new IllegalStateException("No pre-set supplier given");
                }

                V v = supplier.get();
                if (v == null) {
                    throw new NullPointerException("Supplier returned null");
                }

                // Alt 1
                // Prevents reordering. Changes only go in one direction.
                // https://developer.arm.com/documentation/102336/0100/Load-Acquire-and-Store-Release-instructions
                setValueRelease(v);

                // Alt 2
                // VarHandle.fullFence();
                // VarHandle.fullFence();
                return v;
            } catch (Throwable e) {
                // Record the throwable instead of the value.
                // Prevents reordering.
                setValueRelease(e);
                // Rethrow
                throw e;
            } finally {
                forgetPresetProvided();
            }
        } finally {
            semaphore.release();
        }
    }

    @Override
    public final String toString() {
        return getClass().getSimpleName() + "[" + switch (state()) {
            case EMPTY -> Lazy.State.EMPTY;
            case CONSTRUCTING -> Lazy.State.CONSTRUCTING;
            case PRESENT -> Objects.toString(value);
            case ERROR -> Lazy.State.ERROR + " [" + value + "]";
        } + "]";
    }

    // Todo: Consider adding checked exception constructior. E.g. Cache value from an SQL query (Check with Ron)
    // Todo: Consider adding a lazy that shields a POJO

    /**
     * A builder that can be used to configure a LazyReference.
     *
     * @param <T> the type of the value.
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY)
    public interface Builder<T> {

        /**
         * {@return a builder that will use the provided {@code supplier} when
         * eventially {@linkplain #build() building} a LazyReference}.
         *
         * @param supplier to use
         */
        Builder<T> withSupplier(Supplier<? extends T> supplier);

        /**
         * {@return a builder that will have no {@code supplier} when
         * eventially {@linkplain #build() building} a LazyReference}.
         */
        Builder<T> withoutSuplier();

        /**
         * {@return a builder that will use the provided {@code earliestEvaluation} when
         * eventially {@linkplain #build() building} a LazyReference}.
         *
         * @param earliestEvaluation to use
         */
        Builder<T> withEarliestEvaluation(Lazy.Evaluation earliestEvaluation);

        /**
         * {@return a builder that will use the provided eagerly computed {@code value} when
         * eventially {@linkplain #build() building} a LazyReference}.
         *
         * @param value to use
         */
        Builder<T> withValue(T value);

        /**
         * {@return a new LazyReference with the builder's configured setting}.
         */
        LazyReference<T> build();
    }

    record LazyReferenceBuilder<T>(Lazy.Evaluation binding,
                                   Supplier<? extends T> supplier,
                                   boolean hasValue,
                                   T value) implements Builder<T> {

        LazyReferenceBuilder() {
            this(null);
        }

        LazyReferenceBuilder(Supplier<? extends T> supplier) {
            this(Lazy.Evaluation.AT_USE, supplier, false, null);
        }

        @Override
        public Builder<T> withEarliestEvaluation(Lazy.Evaluation earliestEvaluation) {
            return new LazyReferenceBuilder<>(Objects.requireNonNull(earliestEvaluation), supplier, hasValue, value);
        }

        @Override
        public Builder<T> withSupplier(Supplier<? extends T> supplier) {
            return new LazyReferenceBuilder<>(binding, Objects.requireNonNull(supplier), hasValue, value);
        }

        @Override
        public Builder<T> withoutSuplier() {
            return new LazyReferenceBuilder<>(binding, null, hasValue, value);
        }

        @Override
        public Builder<T> withValue(T value) {
            return new LazyReferenceBuilder<>(binding, supplier, true, value);
        }

        @Override
        public LazyReference<T> build() {
            return hasValue
                    ? new LazyReference<>(value)
                    : new LazyReference<>(binding, supplier);
        }
    }

    // Private support methods

    @SuppressWarnings("unchecked")
    private V castOrThrow(Object o) {
        if (o instanceof Throwable throwable) {
            throw new NoSuchElementException(throwable);
        }
        return (V) o;
    }

    private void setValueRelease(Object value) {
        VALUE_HANDLE.setRelease(this, value);
    }

    private void forgetPresetProvided() {
        // Stops preventing the provider from being collected once it has been
        // used (if initially set).
        this.presetProvider = null;
    }

    private static VarHandle valueHandle() {
        try {
            return MethodHandles.lookup()
                    .findVarHandle(LazyReference.class, "value", Object.class);
            // .withInvokeExactBehavior(); // Make sure no boxing is made?
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
