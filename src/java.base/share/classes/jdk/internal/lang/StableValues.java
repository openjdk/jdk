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

package jdk.internal.lang;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadFactory;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * This class consists of static methods returning constructs involving StableValue.
 *
 * <p>The methods of this class all throw {@code NullPointerException}
 * if provided with {@code null} arguments unless otherwise specified.
 * <p>
 * The constructs returned are eligible for similar JVM optimizations as the
 * {@linkplain StableValue} itself.
 *
 * @see   StableValue
 * @since 24
 */
public final class StableValues {

    // Suppresses default constructor, ensuring non-instantiability.
    private StableValues() {}

    /**
     * {@return a new thread-safe, stable, lazily computed {@linkplain Supplier supplier}
     * that records the value of the provided {@code original} supplier upon being first
     * accessed via {@linkplain Supplier#get()}, or optionally via a background thread
     * created from the provided {@code factory} (if non-null)}
     * <p>
     * The provided {@code original} supplier is guaranteed to be successfully invoked
     * at most once even in a multi-threaded environment. Competing threads invoking the
     * {@linkplain Supplier#get()} method when a value is already under computation
     * will block until a value is computed or an exception is thrown by the
     * computing thread.
     * <p>
     * If the {@code original} Supplier invokes the returned Supplier recursively,
     * a StackOverflowError will be thrown when the returned
     * Supplier's {@linkplain Supplier#get()} method is invoked.
     * <p>
     * If the provided {@code original} supplier throws an exception, it is relayed
     * to the initial caller. If the memoized supplier is computed by a background thread,
     * exceptions from the provided {@code original} supplier will be relayed to the
     * background thread's {@linkplain Thread#getUncaughtExceptionHandler() uncaught
     * exception handler}.
     *
     * @param original supplier used to compute a memoized value
     * @param factory  an optional factory that, if non-null, will be used to create
     *                 a background thread that will attempt to compute the memoized
     *                 value. If the factory is {@code null}, no background thread will
     *                 be created.
     * @param <T>      the type of results supplied by the returned supplier
     */
    public static <T> Supplier<T> memoizedSupplier(Supplier<? extends T> original,
                                                   ThreadFactory factory) {
          Objects.requireNonNull(original);
          // `factory` is nullable

          // The memoized value is backed by a StableValue
          final StableValue<T> stable = StableValue.newInstance();
          // A record provides better debug capabilities than a lambda
          record MemoizedSupplier<T>(StableValue<T> stable,
                                     Supplier<? extends T> original) implements Supplier<T> {
              @Override public T get() { return stable.computeIfUnset(original); }
          }
          final Supplier<T> memoized = new MemoizedSupplier<>(stable, original);

          if (factory != null) {
              final Thread thread = factory.newThread(new Runnable() {
                  @Override public void run() { memoized.get(); }
              });
              thread.start();
          }

          return memoized;
      }

    /**
     * {@return a new thread-safe, stable, lazily computed {@linkplain IntFunction}
     * that, for each allowed input, records the values of the provided {@code original}
     * IntFunction upon being first accessed via {@linkplain IntFunction#apply(int)}, or
     * optionally via background threads created from the provided {@code factory}
     * (if non-null)}
     * <p>
     * The provided {@code original} IntFunction is guaranteed to be successfully invoked
     * at most once per allowed input, even in a multi-threaded environment. Competing
     * threads invoking the {@linkplain IntFunction#apply(int)} method when a value is
     * already under computation will block until a value is computed or an exception is
     * thrown by the computing thread.
     * <p>
     * If the {@code original} IntFunction invokes the returned IntFunction recursively
     * for a particular input value, a StackOverflowError will be thrown when the returned
     * IntFunction's {@linkplain IntFunction#apply(int)} method is invoked.
     * <p>
     * If the provided {@code original} IntFunction throws an exception, it is relayed
     * to the initial caller. If the memoized IntFunction is computed by a background
     * thread, exceptions from the provided {@code original} IntFunction will be relayed
     * to the background thread's {@linkplain Thread#getUncaughtExceptionHandler()
     * uncaught exception handler}.
     * <p>
     * The order in which background threads are started is unspecified.
     *
     * @param size     the size of the allowed inputs in {@code [0, size)}
     * @param original IntFunction used to compute a memoized value
     * @param factory  an optional factory that, if non-null, will be used to create
     *                 {@code size} background threads that will attempt to compute all
     *                 the memoized values. If the provided factory is {@code null}, no
     *                 background threads will be created.
     * @param <R>      the type of results delivered by the returned IntFunction
     */
    public static <R> IntFunction<R> memoizedIntFunction(int size,
                                                         IntFunction<? extends R> original,
                                                         ThreadFactory factory) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(original);
        // `factory` is nullable

        final List<StableValue<R>> backing = StableValue.ofList(size);

        // A record provides better debug capabilities than a lambda
        record MemoizedIntFunction<R>(List<StableValue<R>> stables,
                                      IntFunction<? extends R> original) implements IntFunction<R> {
            @Override public R apply(int value) { return stables.get(value)
                    .mapIfUnset(value, original::apply); }
        }

        final IntFunction<R> memoized = new MemoizedIntFunction<>(backing, original);

        if (factory != null) {
            for (int i = 0; i < size; i++) {
                final int input = i;
                final Thread thread = factory.newThread(new Runnable() {
                    @Override public void run() { memoized.apply(input); }
                });
                thread.start();
            }
        }

        return memoized;
    }

    /**
     * {@return a new thread-safe, stable, lazily computed {@linkplain Function}
     * that, for each allowed input in the given set of {@code inputs}, records the
     * values of the provided {@code original} Function upon being first accessed via
     * {@linkplain Function#apply(Object)}, or optionally via background threads created
     * from the provided {@code factory} (if non-null)}
     * <p>
     * The provided {@code original} Function is guaranteed to be successfully invoked
     * at most once per allowed input, even in a multi-threaded environment. Competing
     * threads invoking the {@linkplain Function#apply(Object)} method when a value is
     * already under computation will block until a value is computed or an exception is
     * thrown by the computing thread.
     * <p>
     * If the {@code original} Function invokes the returned Function recursively
     * for a particular input value, a StackOverflowError will be thrown when the returned
     * IntFunction's {@linkplain IntFunction#apply(int)} method is invoked.
     * <p>
     * If the provided {@code original} Function throws an exception, it is relayed
     * to the initial caller. If the memoized Function is computed by a background
     * thread, exceptions from the provided {@code original} Function will be relayed to
     * the background thread's {@linkplain Thread#getUncaughtExceptionHandler() uncaught
     * exception handler}.
     * <p>
     * The order in which background threads are started is unspecified.
     *
     * @param inputs   the set of allowed input values
     * @param original Function used to compute a memoized value
     * @param factory  an optional factory that, if non-null, will be used to create
     *                 {@code size} background threads that will attempt to compute the
     *                 memoized values. If the provided factory is {@code null}, no
     *                 background threads will be created.
     * @param <R>      the type of results delivered by the returned Function
     */
    public static <T, R> Function<T, R> memoizedFunction(Set<T> inputs,
                                                         Function<? super T, ? extends R> original,
                                                         ThreadFactory factory) {
        Objects.requireNonNull(inputs);

        final Map<T, StableValue<R>> backing = StableValue.ofMap(inputs);

        // A record provides better debug capabilities than a lambda
        record MemoizedFunction<T, R>(Map<T, StableValue<R>> stables,
                                      Function<? super T, ? extends R> original) implements Function<T, R> {
            @Override
            public R apply(T value) {
                StableValue<R> stable = MemoizedFunction.this.stables.get(value);
                if (stable == null) {
                    throw new IllegalArgumentException("Input not allowed: " + value);
                }
                return stable.mapIfUnset(value, original);
            }
        }

        final Function<T, R> memoized = new MemoizedFunction<>(backing, original);

        if (factory != null) {
            for (final T t : inputs) {
                final Thread thread = factory.newThread(new Runnable() {
                    @Override public void run() { memoized.apply(t); }
                });
                thread.start();
            }
        }

        return memoized;
    }

}
