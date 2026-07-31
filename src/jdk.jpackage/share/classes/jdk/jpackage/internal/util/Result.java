/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.util;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;
import jdk.jpackage.internal.util.function.ExceptionBox;
import jdk.jpackage.internal.util.function.ThrowingSupplier;


public record Result<T>(Optional<T> value, Collection<? extends Exception> errors) {
    public Result {
        if (value.isEmpty() == errors.isEmpty()) {
            throw new IllegalArgumentException("'value' and 'errors' cannot both be non-empty or both be empty");
        }
    }

    public T orElseThrow() {
        firstError().ifPresent(ex -> {
            throw ExceptionBox.toUnchecked(ex);
        });
        return value.orElseThrow();
    }

    public boolean hasValue() {
        return value.isPresent();
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public <U> Result<U> map(Function<T, U> conv) {
        if (hasValue()) {
            var mapped = value.map(conv);
            if (mapped.isEmpty()) {
                throw new NullPointerException();
            } else {
                return new Result<>(mapped, errors);
            }
        } else {
            return mapErrors();
        }
    }

    public <U> Result<U> flatMap(Function<T, Result<U>> conv) {
        return value.map(conv).orElseGet(() -> {
            return new Result<>(Optional.empty(), errors);
        });
    }

    @SuppressWarnings("unchecked")
    public <U> Result<U> mapErrors() {
        if (hasValue()) {
            throw new IllegalStateException("Can not map errors from a result without errors");
        }
        return (Result<U>)this;
    }

    public Result<T> peekErrors(Consumer<Collection<? extends Exception>> consumer) {
        if (hasErrors()) {
            consumer.accept(errors);
        }
        return this;
    }

    public Result<T> peekValue(Consumer<T> consumer) {
        value.ifPresent(consumer);
        return this;
    }

    public Optional<? extends Exception> firstError() {
        return errors.stream().findFirst();
    }

    public static <T> Result<T> of(Supplier<T> supplier) {
        return of(supplier::get, RuntimeException.class);
    }

    public static <T, E extends Exception> Result<T> of(
            ThrowingSupplier<T, ? extends E> supplier, Class<? extends E> supplierExceptionType) {

        Objects.requireNonNull(supplier);
        Objects.requireNonNull(supplierExceptionType);

        T value;
        try {
            value = supplier.get();
        } catch (Exception ex) {
            if (supplierExceptionType.isInstance(ex)) {
                return ofError(ex);
            } else if (ex instanceof RuntimeException rex) {
                throw rex;
            } else {
                // Unreachable because the `supplier` can throw exceptions of type or supertype `E` or runtime exceptions.
                throw ExceptionBox.reachedUnreachable();
            }
        }

        return ofValue(value);
    }

    public static <T> Result<T> ofValue(T value) {
        return new Result<>(Optional.of(value), List.of());
    }

    public static <T> Result<T> ofErrors(Collection<? extends Exception> errors) {
        return new Result<>(Optional.empty(), List.copyOf(errors));
    }

    public static <T> Result<T> ofError(Exception error) {
        return ofErrors(List.of(error));
    }

    public static boolean allHaveValues(Iterable<? extends Result<?>> results) {
        return StreamSupport.stream(results.spliterator(), false).allMatch(Result::hasValue);
    }

    public static boolean allHaveValues(Result<?>... results) {
        return allHaveValues(List.of(results));
    }
}
