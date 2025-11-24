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
package jdk.jpackage.internal.cli;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import jdk.jpackage.internal.cli.Validator.ParsedValue;
import jdk.jpackage.internal.util.Result;

/**
 * Defines creating an option value of type {@link U} from value of type {@link T}.
 *
 * @param <T> input option value type
 * @param <U> output option value type
 */
interface OptionValueConverter<T, U> {

    /**
     * Converts the given value of type {@link T} corresponding to the given option name
     * and option string value to an object of type {@link U}.
     *
     * @param optionName  the option name
     * @param optionValue the string value of the option
     * @param value       the value of the option to convert
     * @return the conversion result
     * @throws ConverterException if internal converter error occurs
     */
    Result<U> convert(OptionName optionName, StringToken optionValue, T value) throws ConverterException;

    /**
     * Gives the class of the type of values this converter converts to.
     *
     * @return the target class for conversion
     */
    Class<? extends U> valueType();

    static <T> Result<T> convertString(OptionValueConverter<String, T> converter, OptionName optionName, StringToken optionValue) {
        return converter.convert(optionName, optionValue, optionValue.value());
    }

    /**
     * Thrown to indicate an error in the normal execution of the converter.
     */
    static final class ConverterException extends RuntimeException {

        private ConverterException(Throwable cause) {
            super(cause);
        }

        private static final long serialVersionUID = 1L;
    }

    static <T> Builder<T> build() {
        return new Builder<>();
    }

    static <T, U> MapperBuilder<T, U> buildMapper() {
        return new MapperBuilder<>();
    }

    static final class MapperBuilder<T, U> {

        private MapperBuilder() {
        }

        private MapperBuilder(MapperBuilder<T, U> other) {
            converter = other.converter;
            validator = other.validator;
            formatString = other.formatString;
            exceptionFactory = other.exceptionFactory;
        }

        MapperBuilder<T, U> copy() {
            return new MapperBuilder<>(this);
        }

        OptionValueConverter<T, U> create() {
            return new DefaultOptionValueConverter<>(
                    converter,
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
                    }),
                    validator());
        }

        MapperBuilder<T, U> converter(ValueConverter<T, U> v) {
            converter = v;
            return this;
        }

        MapperBuilder<T, U> validator(Validator<U, ? extends RuntimeException> v) {
            validator = v;
            return this;
        }

        MapperBuilder<T, U> formatString(String v) {
            formatString = v;
            return this;
        }

        MapperBuilder<T, U> exceptionFactory(OptionValueExceptionFactory<? extends RuntimeException> v) {
            exceptionFactory = v;
            return this;
        }

        MapperBuilder<T, U> mutate(Consumer<MapperBuilder<T, U>> mutator) {
            mutator.accept(this);
            return this;
        }

        Optional<ValueConverter<T, U>> converter() {
            return Optional.ofNullable(converter);
        }

        Optional<Validator<U, ? extends RuntimeException>> validator() {
            return Optional.ofNullable(validator);
        }

        Optional<String> formatString() {
            return Optional.ofNullable(formatString);
        }

        Optional<OptionValueExceptionFactory<? extends RuntimeException>> exceptionFactory() {
            return Optional.ofNullable(exceptionFactory);
        }


        private record DefaultOptionValueConverter<T, U>(ValueConverter<T, U> converter, String formatString,
                OptionValueExceptionFactory<? extends RuntimeException> exceptionFactory,
                Optional<Validator<U, ? extends RuntimeException>> validator) implements OptionValueConverter<T, U> {

            DefaultOptionValueConverter {
                Objects.requireNonNull(converter);
                Objects.requireNonNull(formatString);
                Objects.requireNonNull(exceptionFactory);
                Objects.requireNonNull(validator);
            }

            @Override
            public Result<U> convert(OptionName optionName, StringToken optionValue, T value) {
                Objects.requireNonNull(optionName);
                Objects.requireNonNull(optionValue);
                Objects.requireNonNull(value);

                if (value instanceof String && !value.equals(optionValue.value())) {
                    throw new IllegalArgumentException();
                }

                final U convertedValue;
                try {
                    convertedValue = converter.convert(value);
                } catch (Exception ex) {
                    return handleException(optionName, optionValue, ex);
                }

                final List<? extends Exception> validationExceptions = validator.map(val -> {
                    try {
                        return val.validate(optionName, ParsedValue.create(convertedValue, optionValue));
                    } catch (Validator.ValidatorException ex) {
                        // All unexpected exceptions that the converter yields should be tunneled via ConverterException.
                        throw new ConverterException(ex.getCause());
                    }
                }).orElseGet(List::of);

                if (validationExceptions.isEmpty()) {
                    return Result.ofValue(convertedValue);
                } else {
                    return Result.ofErrors(validationExceptions);
                }
            }

            @Override
            public Class<? extends U> valueType() {
                return converter.valueType();
            }

            private Result<U> handleException(OptionName optionName, StringToken optionValue, Exception ex) {
                if (ex instanceof IllegalArgumentException) {
                    return Result.ofError(exceptionFactory.create(optionName, optionValue, formatString, Optional.of(ex)));
                } else {
                    throw new ConverterException(ex);
                }
            }
        }


        private ValueConverter<T, U> converter;
        private Validator<U, ? extends RuntimeException> validator;
        private String formatString;
        private OptionValueExceptionFactory<? extends RuntimeException> exceptionFactory;
    }


    static final class Builder<T> {

        private Builder() {
            mapperBuilder = buildMapper();
        }

        private Builder(Builder<T> other) {
            mapperBuilder = other.mapperBuilder.copy();
            tokenizer = other.tokenizer;
        }

        Builder<T> copy() {
            return new Builder<>(this);
        }

        OptionValueConverter<String, T> create() {
            return mapperBuilder.create();
        }

        OptionArrayValueConverter<T> createArray() {
            return new DefaultOptionArrayValueConverter<>(create(), tokenizer);
        }

        Builder<T> converter(ValueConverter<String, T> v) {
            mapperBuilder.converter(v);
            return this;
        }

        Builder<T> validator(Validator<T, ? extends RuntimeException> v) {
            mapperBuilder.validator(v);
            return this;
        }

        Builder<T> tokenizer(Function<String, String[]> v) {
            tokenizer = v;
            return this;
        }

        Builder<T> formatString(String v) {
            mapperBuilder.formatString(v);
            return this;
        }

        Builder<T> exceptionFactory(OptionValueExceptionFactory<? extends RuntimeException> v) {
            mapperBuilder.exceptionFactory(v);
            return this;
        }

        Builder<T> mutate(Consumer<Builder<T>> mutator) {
            mutator.accept(this);
            return this;
        }

        Optional<ValueConverter<String, T>> converter() {
            return mapperBuilder.converter();
        }

        Optional<Validator<T, ? extends RuntimeException>> validator() {
            return mapperBuilder.validator();
        }

        Optional<Function<String, String[]>> tokenizer() {
            return Optional.ofNullable(tokenizer);
        }

        Optional<String> formatString() {
            return mapperBuilder.formatString();
        }

        Optional<OptionValueExceptionFactory<? extends RuntimeException>> exceptionFactory() {
            return mapperBuilder.exceptionFactory();
        }


        private record DefaultOptionArrayValueConverter<T>(OptionValueConverter<String, T> elementConverter,
                Function<String, String[]> tokenizer) implements OptionArrayValueConverter<T> {

            DefaultOptionArrayValueConverter {
                Objects.requireNonNull(elementConverter);
                Objects.requireNonNull(tokenizer);
            }

            @SuppressWarnings("unchecked")
            @Override
            public Result<T[]> convert(OptionName optionName, StringToken optionValue, String value) {

                if (!value.equals(optionValue.value())) {
                    throw new IllegalArgumentException();
                }

                final List<Exception> exceptions = new ArrayList<>();
                final List<T> convertedValues = new ArrayList<>();

                final var tokens = tokenize(optionValue.value());
                for (var token : tokens) {
                    final var result = elementConverter.convert(optionName, StringToken.of(optionValue.value(), token), token);
                    exceptions.addAll(result.errors());
                    if (exceptions.isEmpty()) {
                        result.value().ifPresent(convertedValues::add);
                    }
                }

                if (!exceptions.isEmpty()) {
                    return Result.ofErrors(exceptions);
                } else {
                    return Result.ofValue(convertedValues.toArray(length -> {
                        return (T[])Array.newInstance(elementConverter.valueType(), length);
                    }));
                }
            }

            @SuppressWarnings("unchecked")
            @Override
            public Class<? extends T[]> valueType() {
                return (Class<? extends T[]>)elementConverter.valueType().arrayType();
            }

            @Override
            public String[] tokenize(String str) {
                return tokenizer.apply(Objects.requireNonNull(str));
            }
        }

        private final MapperBuilder<String, T> mapperBuilder;
        private Function<String, String[]> tokenizer;
    }
}
