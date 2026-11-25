/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.cli;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

@FunctionalInterface
interface Validator<T, U extends Exception> {

    /**
     * Validates the given option value.
     *
     * @param optionName  the name of an option to validate
     * @param optionValue the value of an option to validate
     * @return the list of validation errors
     * @throws ValidatorException if internal validator error occurs
     */
    List<U> validate(OptionName optionName, ParsedValue<T> optionValue) throws ValidatorException;

    default Validator<T, ? extends Exception> andGreedy(Validator<T, ? extends Exception> after) {
        Objects.requireNonNull(after);
        var before = this;
        return (optionName, optionValue) -> {
            return Stream.concat(
                    before.validate(optionName, optionValue).stream(),
                    after.validate(optionName, optionValue).stream()
            ).toList();
        };
    }

    default Validator<T, ? extends Exception> andLazy(Validator<T, ? extends Exception> after) {
        Objects.requireNonNull(after);
        var before = this;
        return (optionName, optionValue) -> {
            var bErrors = before.validate(optionName, optionValue);
            if (!bErrors.isEmpty()) {
                return bErrors.stream().map(Exception.class::cast).toList();
            } else {
                return after.validate(optionName, optionValue).stream().map(Exception.class::cast).toList();
            }
        };
    }

    default Validator<T, ? extends Exception> or(Validator<T, ? extends Exception> after) {
        Objects.requireNonNull(after);
        var before = this;
        return (optionName, optionValue) -> {
            var bErrors = before.validate(optionName, optionValue);
            if (bErrors.isEmpty()) {
                return List.of();
            }

            var aErrors = after.validate(optionName, optionValue);
            if (aErrors.isEmpty()) {
                return List.of();
            }

            return Stream.concat(bErrors.stream(), aErrors.stream()).toList();
        };
    }

    @SuppressWarnings("unchecked")
    static <T, U extends Exception> Validator<T, U> andGreedy(Validator<T, U> first, Validator<T, U> second) {
        return (Validator<T, U>)first.andGreedy(second);
    }

    @SuppressWarnings("unchecked")
    static <T, U extends Exception> Validator<T, U> andLazy(Validator<T, U> first, Validator<T, U> second) {
        return (Validator<T, U>)first.andLazy(second);
    }

    @SuppressWarnings("unchecked")
    static <T, U extends Exception> Validator<T, U> or(Validator<T, U> first, Validator<T, U> second) {
        return (Validator<T, U>)first.or(second);
    }

    /**
     * Thrown to indicate that the given value didn't pass validation.
     */
    static final class ValidatingConsumerException extends RuntimeException {

        ValidatingConsumerException(String msg, Exception cause) {
            super(msg, Objects.requireNonNull(cause));
        }

        ValidatingConsumerException(Throwable cause) {
            super(Objects.requireNonNull(cause));
        }

        private static final long serialVersionUID = 1L;
    }


    /**
     * Thrown to indicate an error in the normal execution of a validator.
     */
    static final class ValidatorException extends RuntimeException {

        private ValidatorException(Exception cause) {
            super(cause);
        }

        private static final long serialVersionUID = 1L;
    }


    sealed interface ParsedValue<T> {
        StringToken sourceToken();
        T value();

        static <T> ParsedValue<T> create(T value, StringToken sourceToken) {
            return new Details.DefaultParsedValue<>(value, sourceToken);
        }
    }


    static <T, U extends Exception> Builder<T, U> build() {
        return new Builder<>();
    }


    static final class Builder<T, U extends Exception> {

        Validator<T, U> create() {
            return new Details.ScalarValidator<>(
                    Optional.ofNullable(predicate),
                    Optional.ofNullable(consumer),
                    formatString().orElseGet(() -> {
                        if (exceptionFactory == null) {
                            return "";
                        } else {
                            return null;
                        }
                    }),
                    exceptionFactory().orElseGet(() -> {
                        if (formatString == null) {
                            return OptionValueExceptionFactory.unreachable();
                        } else {
                            return null;
                        }
                    }));
        }

        private Builder() {
        }

        Builder(Builder<T, U> other) {
            predicate = other.predicate;
            consumer = other.consumer;
            formatString = other.formatString;
            exceptionFactory = other.exceptionFactory;
        }

        Builder<T, U> copy() {
            return new Builder<>(this);
        }

        Builder<T, U> predicate(Predicate<T> v) {
            consumer = null;
            predicate = v;
            return this;
        }

        Builder<T, U> consumer(Consumer<T> v) {
            predicate = null;
            consumer = v;
            return this;
        }

        Builder<T, U> formatString(String v) {
            formatString = v;
            return this;
        }

        Builder<T, U> exceptionFactory(OptionValueExceptionFactory<? extends U> v) {
            exceptionFactory = v;
            return this;
        }

        Builder<T, U> mutate(Consumer<Builder<T, U>> mutator) {
            mutator.accept(this);
            return this;
        }

        Optional<Predicate<T>> predicate() {
            return Optional.ofNullable(predicate);
        }

        Optional<Consumer<T>> consumer() {
            return Optional.ofNullable(consumer);
        }

        boolean hasValidatingMethod() {
            return predicate().isPresent() || consumer().isPresent();
        }

        Optional<String> formatString() {
            return Optional.ofNullable(formatString);
        }

        Optional<OptionValueExceptionFactory<? extends U>> exceptionFactory() {
            return Optional.ofNullable(exceptionFactory);
        }

        private Predicate<T> predicate;
        private Consumer<T> consumer;
        private String formatString;
        private OptionValueExceptionFactory<? extends U> exceptionFactory;
    }


    static final class Details {

        private Details() {
        }

        private record ScalarValidator<T, U extends Exception>(Optional<Predicate<T>> predicate, Optional<Consumer<T>> consumer,
                String formatString, OptionValueExceptionFactory<? extends U> exceptionFactory) implements Validator<T, U> {

            ScalarValidator {
                Objects.requireNonNull(predicate);
                Objects.requireNonNull(consumer);
                if (predicate.isEmpty() == consumer.isEmpty()) {
                    throw new IllegalArgumentException("Either consumer or predicate must be non-empty");
                }
                Objects.requireNonNull(formatString);
                Objects.requireNonNull(exceptionFactory);
            }

            @Override
            public List<U> validate(OptionName optionName, ParsedValue<T> optionValue) {
                Objects.requireNonNull(optionName);
                Objects.requireNonNull(optionValue);

                try {
                    return predicate.map(validator -> {
                        if (validator.test(optionValue.value())) {
                            return List.<U>of();
                        } else {
                            return List.of((U)exceptionFactory.create(
                                    optionName,
                                    optionValue.sourceToken(),
                                    formatString,
                                    Optional.empty()));
                        }
                    }).or(() -> {
                        return consumer.map(validator -> {
                            try {
                                validator.accept(optionValue.value());
                                return List.of();
                            } catch (RuntimeException ex) {
                                return handleException(optionName, optionValue, ex);
                            }
                        });
                    }).orElseThrow();
                } catch (Exception ex) {
                    throw new ValidatorException(ex);
                }
            }

            private List<U> handleException(OptionName optionName,  ParsedValue<T> optionValue, RuntimeException ex) {
                if (ex instanceof ValidatingConsumerException) {
                    return List.of((U)exceptionFactory.create(
                            optionName,
                            optionValue.sourceToken(),
                            formatString,
                            Optional.of((Exception)ex.getCause())));
                } else if (ex instanceof IllegalArgumentException) {
                    return List.of((U)exceptionFactory.create(
                            optionName,
                            optionValue.sourceToken(),
                            formatString,
                            Optional.of(ex)));
                } else {
                    throw ex;
                }
            }
        }


        private record DefaultParsedValue<T>(T value, StringToken sourceToken) implements ParsedValue<T> {
            DefaultParsedValue {
                Objects.requireNonNull(sourceToken);
                Objects.requireNonNull(value);
            }
        }
    }
}
