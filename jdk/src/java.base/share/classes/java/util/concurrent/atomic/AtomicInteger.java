/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent.atomic;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;

/**
 * An {@code int} value that may be updated atomically.  See the
 * {@link VarHandle} specification for descriptions of the properties
 * of atomic accesses. An {@code AtomicInteger} is used in
 * applications such as atomically incremented counters, and cannot be
 * used as a replacement for an {@link java.lang.Integer}. However,
 * this class does extend {@code Number} to allow uniform access by
 * tools and utilities that deal with numerically-based classes.
 *
 * @since 1.5
 * @author Doug Lea
 */
public class AtomicInteger extends Number implements java.io.Serializable {
    private static final long serialVersionUID = 6214790243416807050L;
    private static final VarHandle VALUE;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            VALUE = l.findVarHandle(AtomicInteger.class, "value", int.class);
        } catch (ReflectiveOperationException e) {
            throw new Error(e);
        }
    }

    private volatile int value;

    /**
     * Creates a new AtomicInteger with the given initial value.
     *
     * @param initialValue the initial value
     */
    public AtomicInteger(int initialValue) {
        value = initialValue;
    }

    /**
     * Creates a new AtomicInteger with initial value {@code 0}.
     */
    public AtomicInteger() {
    }

    /**
     * Returns the current value,
     * with memory effects as specified by {@link VarHandle#getVolatile}.
     *
     * @return the current value
     */
    public final int get() {
        return value;
    }

    /**
     * Sets the value to {@code newValue},
     * with memory effects as specified by {@link VarHandle#setVolatile}.
     *
     * @param newValue the new value
     */
    public final void set(int newValue) {
        value = newValue;
    }

    /**
     * Sets the value to {@code newValue},
     * with memory effects as specified by {@link VarHandle#setRelease}.
     *
     * @param newValue the new value
     * @since 1.6
     */
    public final void lazySet(int newValue) {
        VALUE.setRelease(this, newValue);
    }

    /**
     * Atomically sets the value to {@code newValue} and returns the old value,
     * with memory effects as specified by {@link VarHandle#getAndSet}.
     *
     * @param newValue the new value
     * @return the previous value
     */
    public final int getAndSet(int newValue) {
        return (int)VALUE.getAndSet(this, newValue);
    }

    /**
     * Atomically sets the value to {@code newValue}
     * if the current value {@code == expectedValue},
     * with memory effects as specified by {@link VarHandle#compareAndSet}.
     *
     * @param expectedValue the expected value
     * @param newValue the new value
     * @return {@code true} if successful. False return indicates that
     * the actual value was not equal to the expected value.
     */
    public final boolean compareAndSet(int expectedValue, int newValue) {
        return VALUE.compareAndSet(this, expectedValue, newValue);
    }

    /**
     * Possibly atomically sets the value to {@code newValue}
     * if the current value {@code == expectedValue},
     * with memory effects as specified by {@link VarHandle#weakCompareAndSet}.
     *
     * @param expectedValue the expected value
     * @param newValue the new value
     * @return {@code true} if successful
     */
    public final boolean weakCompareAndSet(int expectedValue, int newValue) {
        return VALUE.weakCompareAndSet(this, expectedValue, newValue);
    }

    /**
     * Atomically increments the current value,
     * with memory effects as specified by {@link VarHandle#getAndAdd}.
     *
     * <p>Equivalent to {@code getAndAdd(1)}.
     *
     * @return the previous value
     */
    public final int getAndIncrement() {
        return (int)VALUE.getAndAdd(this, 1);
    }

    /**
     * Atomically decrements the current value,
     * with memory effects as specified by {@link VarHandle#getAndAdd}.
     *
     * <p>Equivalent to {@code getAndAdd(-1)}.
     *
     * @return the previous value
     */
    public final int getAndDecrement() {
        return (int)VALUE.getAndAdd(this, -1);
    }

    /**
     * Atomically adds the given value to the current value,
     * with memory effects as specified by {@link VarHandle#getAndAdd}.
     *
     * @param delta the value to add
     * @return the previous value
     */
    public final int getAndAdd(int delta) {
        return (int)VALUE.getAndAdd(this, delta);
    }

    /**
     * Atomically increments the current value,
     * with memory effects as specified by {@link VarHandle#addAndGet}.
     *
     * <p>Equivalent to {@code addAndGet(1)}.
     *
     * @return the updated value
     */
    public final int incrementAndGet() {
        return (int)VALUE.addAndGet(this, 1);
    }

    /**
     * Atomically decrements the current value,
     * with memory effects as specified by {@link VarHandle#addAndGet}.
     *
     * <p>Equivalent to {@code addAndGet(-1)}.
     *
     * @return the updated value
     */
    public final int decrementAndGet() {
        return (int)VALUE.addAndGet(this, -1);
    }

    /**
     * Atomically adds the given value to the current value,
     * with memory effects as specified by {@link VarHandle#addAndGet}.
     *
     * @param delta the value to add
     * @return the updated value
     */
    public final int addAndGet(int delta) {
        return (int)VALUE.addAndGet(this, delta);
    }

    /**
     * Atomically updates the current value with the results of
     * applying the given function, returning the previous value. The
     * function should be side-effect-free, since it may be re-applied
     * when attempted updates fail due to contention among threads.
     *
     * @param updateFunction a side-effect-free function
     * @return the previous value
     * @since 1.8
     */
    public final int getAndUpdate(IntUnaryOperator updateFunction) {
        int prev = get(), next = 0;
        for (boolean haveNext = false;;) {
            if (!haveNext)
                next = updateFunction.applyAsInt(prev);
            if (weakCompareAndSetVolatile(prev, next))
                return prev;
            haveNext = (prev == (prev = get()));
        }
    }

    /**
     * Atomically updates the current value with the results of
     * applying the given function, returning the updated value. The
     * function should be side-effect-free, since it may be re-applied
     * when attempted updates fail due to contention among threads.
     *
     * @param updateFunction a side-effect-free function
     * @return the updated value
     * @since 1.8
     */
    public final int updateAndGet(IntUnaryOperator updateFunction) {
        int prev = get(), next = 0;
        for (boolean haveNext = false;;) {
            if (!haveNext)
                next = updateFunction.applyAsInt(prev);
            if (weakCompareAndSetVolatile(prev, next))
                return next;
            haveNext = (prev == (prev = get()));
        }
    }

    /**
     * Atomically updates the current value with the results of
     * applying the given function to the current and given values,
     * returning the previous value. The function should be
     * side-effect-free, since it may be re-applied when attempted
     * updates fail due to contention among threads.  The function
     * is applied with the current value as its first argument,
     * and the given update as the second argument.
     *
     * @param x the update value
     * @param accumulatorFunction a side-effect-free function of two arguments
     * @return the previous value
     * @since 1.8
     */
    public final int getAndAccumulate(int x,
                                      IntBinaryOperator accumulatorFunction) {
        int prev = get(), next = 0;
        for (boolean haveNext = false;;) {
            if (!haveNext)
                next = accumulatorFunction.applyAsInt(prev, x);
            if (weakCompareAndSetVolatile(prev, next))
                return prev;
            haveNext = (prev == (prev = get()));
        }
    }

    /**
     * Atomically updates the current value with the results of
     * applying the given function to the current and given values,
     * returning the updated value. The function should be
     * side-effect-free, since it may be re-applied when attempted
     * updates fail due to contention among threads.  The function
     * is applied with the current value as its first argument,
     * and the given update as the second argument.
     *
     * @param x the update value
     * @param accumulatorFunction a side-effect-free function of two arguments
     * @return the updated value
     * @since 1.8
     */
    public final int accumulateAndGet(int x,
                                      IntBinaryOperator accumulatorFunction) {
        int prev = get(), next = 0;
        for (boolean haveNext = false;;) {
            if (!haveNext)
                next = accumulatorFunction.applyAsInt(prev, x);
            if (weakCompareAndSetVolatile(prev, next))
                return next;
            haveNext = (prev == (prev = get()));
        }
    }

    /**
     * Returns the String representation of the current value.
     * @return the String representation of the current value
     */
    public String toString() {
        return Integer.toString(get());
    }

    /**
     * Returns the current value of this {@code AtomicInteger} as an
     * {@code int},
     * with memory effects as specified by {@link VarHandle#getVolatile}.
     *
     * Equivalent to {@link #get()}.
     */
    public int intValue() {
        return get();
    }

    /**
     * Returns the current value of this {@code AtomicInteger} as a
     * {@code long} after a widening primitive conversion,
     * with memory effects as specified by {@link VarHandle#getVolatile}.
     * @jls 5.1.2 Widening Primitive Conversions
     */
    public long longValue() {
        return (long)get();
    }

    /**
     * Returns the current value of this {@code AtomicInteger} as a
     * {@code float} after a widening primitive conversion,
     * with memory effects as specified by {@link VarHandle#getVolatile}.
     * @jls 5.1.2 Widening Primitive Conversions
     */
    public float floatValue() {
        return (float)get();
    }

    /**
     * Returns the current value of this {@code AtomicInteger} as a
     * {@code double} after a widening primitive conversion,
     * with memory effects as specified by {@link VarHandle#getVolatile}.
     * @jls 5.1.2 Widening Primitive Conversions
     */
    public double doubleValue() {
        return (double)get();
    }

    // jdk9

    /**
     * Returns the current value, with memory semantics of reading as
     * if the variable was declared non-{@code volatile}.
     *
     * @return the value
     * @since 9
     */
    public final int getPlain() {
        return (int)VALUE.get(this);
    }

    /**
     * Sets the value to {@code newValue}, with memory semantics
     * of setting as if the variable was declared non-{@code volatile}
     * and non-{@code final}.
     *
     * @param newValue the new value
     * @since 9
     */
    public final void setPlain(int newValue) {
        VALUE.set(this, newValue);
    }

    /**
     * Returns the current value,
     * with memory effects as specified by {@link VarHandle#getOpaque}.
     *
     * @return the value
     * @since 9
     */
    public final int getOpaque() {
        return (int)VALUE.getOpaque(this);
    }

    /**
     * Sets the value to {@code newValue},
     * with memory effects as specified by {@link VarHandle#setOpaque}.
     *
     * @param newValue the new value
     * @since 9
     */
    public final void setOpaque(int newValue) {
        VALUE.setOpaque(this, newValue);
    }

    /**
     * Returns the current value,
     * with memory effects as specified by {@link VarHandle#getAcquire}.
     *
     * @return the value
     * @since 9
     */
    public final int getAcquire() {
        return (int)VALUE.getAcquire(this);
    }

    /**
     * Sets the value to {@code newValue},
     * with memory effects as specified by {@link VarHandle#setRelease}.
     *
     * @param newValue the new value
     * @since 9
     */
    public final void setRelease(int newValue) {
        VALUE.setRelease(this, newValue);
    }

    /**
     * Atomically sets the value to {@code newValue} if the current value,
     * referred to as the <em>witness value</em>, {@code == expectedValue},
     * with memory effects as specified by
     * {@link VarHandle#compareAndExchange}.
     *
     * @param expectedValue the expected value
     * @param newValue the new value
     * @return the witness value, which will be the same as the
     * expected value if successful
     * @since 9
     */
    public final int compareAndExchange(int expectedValue, int newValue) {
        return (int)VALUE.compareAndExchange(this, expectedValue, newValue);
    }

    /**
     * Atomically sets the value to {@code newValue} if the current value,
     * referred to as the <em>witness value</em>, {@code == expectedValue},
     * with memory effects as specified by
     * {@link VarHandle#compareAndExchangeAcquire}.
     *
     * @param expectedValue the expected value
     * @param newValue the new value
     * @return the witness value, which will be the same as the
     * expected value if successful
     * @since 9
     */
    public final int compareAndExchangeAcquire(int expectedValue, int newValue) {
        return (int)VALUE.compareAndExchangeAcquire(this, expectedValue, newValue);
    }

    /**
     * Atomically sets the value to {@code newValue} if the current value,
     * referred to as the <em>witness value</em>, {@code == expectedValue},
     * with memory effects as specified by
     * {@link VarHandle#compareAndExchangeRelease}.
     *
     * @param expectedValue the expected value
     * @param newValue the new value
     * @return the witness value, which will be the same as the
     * expected value if successful
     * @since 9
     */
    public final int compareAndExchangeRelease(int expectedValue, int newValue) {
        return (int)VALUE.compareAndExchangeRelease(this, expectedValue, newValue);
    }

    /**
     * Possibly atomically sets the value to {@code newValue} if
     * the current value {@code == expectedValue},
     * with memory effects as specified by
     * {@link VarHandle#weakCompareAndSetVolatile}.
     *
     * @param expectedValue the expected value
     * @param newValue the new value
     * @return {@code true} if successful
     * @since 9
     */
    public final boolean weakCompareAndSetVolatile(int expectedValue, int newValue) {
        return VALUE.weakCompareAndSetVolatile(this, expectedValue, newValue);
    }

    /**
     * Possibly atomically sets the value to {@code newValue} if
     * the current value {@code == expectedValue},
     * with memory effects as specified by
     * {@link VarHandle#weakCompareAndSetAcquire}.
     *
     * @param expectedValue the expected value
     * @param newValue the new value
     * @return {@code true} if successful
     * @since 9
     */
    public final boolean weakCompareAndSetAcquire(int expectedValue, int newValue) {
        return VALUE.weakCompareAndSetAcquire(this, expectedValue, newValue);
    }

    /**
     * Possibly atomically sets the value to {@code newValue} if
     * the current value {@code == expectedValue},
     * with memory effects as specified by
     * {@link VarHandle#weakCompareAndSetRelease}.
     *
     * @param expectedValue the expected value
     * @param newValue the new value
     * @return {@code true} if successful
     * @since 9
     */
    public final boolean weakCompareAndSetRelease(int expectedValue, int newValue) {
        return VALUE.weakCompareAndSetRelease(this, expectedValue, newValue);
    }

}
