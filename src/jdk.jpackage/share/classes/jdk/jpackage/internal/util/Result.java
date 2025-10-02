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
package jdk.jpackage.internal.util;

import static jdk.jpackage.internal.util.function.ExceptionBox.rethrowUnchecked;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.StreamSupport;


public record Result<T>(Optional<T> value, Collection<? extends Exception> errors) {
    public Result {
        if (value.isEmpty() == errors.isEmpty()) {
            throw new IllegalArgumentException();
        }

        if (value.isEmpty() && errors.isEmpty()) {
            throw new IllegalArgumentException("Error collection must be non-empty");
        }

    }

    public T orElseThrow() {
        firstError().ifPresent(ex -> {
            rethrowUnchecked(ex);
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
        return new Result<>(value.map(conv), errors);
    }

    public <U> Result<U> flatMap(Function<T, Result<U>> conv) {
        return value.map(conv).orElseGet(() -> {
            return new Result<>(Optional.empty(), errors);
        });
    }

    public Result<T> mapErrors(UnaryOperator<Collection<? extends Exception>> errorsMapper) {
        return new Result<>(value, errorsMapper.apply(errors));
    }

    public <U> Result<U> mapErrors() {
        return new Result<>(Optional.empty(), errors);
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

    public static <T> Result<T> create(Supplier<T> supplier) {
        try {
            return ofValue(supplier.get());
        } catch (Exception ex) {
            return ofError(ex);
        }
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
