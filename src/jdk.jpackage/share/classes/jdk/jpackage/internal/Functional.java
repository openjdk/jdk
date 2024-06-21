/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class Functional {

    @FunctionalInterface
    public interface ThrowingConsumer<T> {

        void accept(T t) throws Throwable;

        public static <T> Consumer<T> toConsumer(ThrowingConsumer<T> v) {
            return o -> {
                try {
                    v.accept(o);
                } catch (Throwable ex) {
                    throw rethrowUnchecked(ex);
                }
            };
        }
    }

    @FunctionalInterface
    public interface ThrowingBiConsumer<T, U> {

        void accept(T t, U u) throws Throwable;

        public static <T, U> BiConsumer<T, U> toBiConsumer(ThrowingBiConsumer<T, U> v) {
            return (t, u) -> {
                try {
                    v.accept(t, u);
                } catch (Throwable ex) {
                    throw rethrowUnchecked(ex);
                }
            };
        }
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {

        T get() throws Throwable;

        public static <T> Supplier<T> toSupplier(ThrowingSupplier<T> v) {
            return () -> {
                try {
                    return v.get();
                } catch (Throwable ex) {
                    throw rethrowUnchecked(ex);
                }
            };
        }
    }

    @FunctionalInterface
    public interface ThrowingFunction<T, R> {

        R apply(T t) throws Throwable;

        public static <T, R> Function<T, R> toFunction(ThrowingFunction<T, R> v) {
            return (t) -> {
                try {
                    return v.apply(t);
                } catch (Throwable ex) {
                    throw rethrowUnchecked(ex);
                }
            };
        }
    }

    @FunctionalInterface
    public interface ThrowingBiFunction<T, U, R> {

        R apply(T t, U u) throws Throwable;

        public static <T, U, R> BiFunction<T, U, R> toBiFunction(ThrowingBiFunction<T, U, R> v) {
            return (t, u) -> {
                try {
                    return v.apply(t, u);
                } catch (Throwable ex) {
                    throw rethrowUnchecked(ex);
                }
            };
        }
    }

    @FunctionalInterface
    public interface ThrowingUnaryOperator<T> {

        T apply(T t) throws Throwable;

        public static <T> UnaryOperator<T> toUnaryOperator(ThrowingUnaryOperator<T> v) {
            return (t) -> {
                try {
                    return v.apply(t);
                } catch (Throwable ex) {
                    throw rethrowUnchecked(ex);
                }
            };
        }
    }

    @FunctionalInterface
    public interface ThrowingRunnable {

        void run() throws Throwable;

        public static Runnable toRunnable(ThrowingRunnable v) {
            return () -> {
                try {
                    v.run();
                } catch (Throwable ex) {
                    throw rethrowUnchecked(ex);
                }
            };
        }
    }

    public static <T> Supplier<T> identity(Supplier<T> v) {
        return v;
    }

    public static <T> Consumer<T> identity(Consumer<T> v) {
        return v;
    }

    public static <T, U> BiConsumer<T, U> identity(BiConsumer<T, U> v) {
        return v;
    }

    public static Runnable identity(Runnable v) {
        return v;
    }

    public static <T, U, R> BiFunction<T, U, R> identityBiFunction(BiFunction<T, U, R> v) {
        return v;
    }

    public static <T, R> Function<T, R> identityFunction(Function<T, R> v) {
        return v;
    }

    public static <T> UnaryOperator<T> identityUnaryOperator(UnaryOperator<T> v) {
        return v;
    }

    public static <T> Predicate<T> identityPredicate(Predicate<T> v) {
        return v;
    }

    public static class ExceptionBox extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public ExceptionBox(Throwable throwable) {
            super(throwable);
        }
    }

    public static RuntimeException rethrowUnchecked(Throwable throwable) throws
            ExceptionBox {
        if (throwable instanceof RuntimeException runtimeThrowable) {
            throw runtimeThrowable;
        }

        if (throwable instanceof InvocationTargetException) {
            throw new ExceptionBox(throwable.getCause());
        }

        throw new ExceptionBox(throwable);
    }

    @SuppressWarnings("unchecked")
    static <T extends B, B, C extends Collection<T>> C toCollection(Collection<B> v) {
        Collection<?> tmp = v;
        return (C) tmp;
    }
}
