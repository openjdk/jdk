/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

final class StableTestUtil {

    private StableTestUtil() {}

    public static final class CountingSupplier<T>
            extends AbstractCounting<Supplier<T>>
            implements Supplier<T> {

        public CountingSupplier(Supplier<T> delegate) {
            super(delegate);
        }

        @Override
        public T get() {
            incrementCounter();
            return delegate.get();
        }

    }

    public static final class CountingIntFunction<R>
            extends AbstractCounting<IntFunction<R>>
            implements IntFunction<R> {

        public CountingIntFunction(IntFunction<R> delegate) {
            super(delegate);
        }

        @Override
        public R apply(int value) {
            incrementCounter();
            return delegate.apply(value);
        }

    }

    public static final class CountingFunction<T, R>
            extends AbstractCounting<Function<T, R>>
            implements Function<T, R> {

        public CountingFunction(Function<T, R> delegate) {
            super(delegate);
        }

        @Override
        public R apply(T t) {
            incrementCounter();
            return delegate.apply(t);
        }

    }

    public static final class CountingBiFunction<T, U, R>
            extends AbstractCounting<BiFunction<T, U, R>>
            implements BiFunction<T, U, R> {

        public CountingBiFunction(BiFunction<T, U, R> delegate) {
            super(delegate);
        }

        @Override
        public R apply(T t, U u) {
            incrementCounter();
            return delegate.apply(t, u);
        }
    }

    abstract static class AbstractCounting<D> {

        private final AtomicInteger cnt = new AtomicInteger();
        protected final D delegate;

        protected AbstractCounting(D delegate) {
            this.delegate = delegate;
        }

        protected final void incrementCounter() {
            cnt.incrementAndGet();
        }

        public final int cnt() {
            return cnt.get();
        }

        @Override
        public final String toString() {
            return cnt.toString();
        }
    }

}
