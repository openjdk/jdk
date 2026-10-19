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


    static final class Builder<T> {

        private Builder() {
            this(new OneStepBackend<>(new StepBuilder<>(true)));
        }

        private Builder(Backend<T> backend) {
            this.backend = Objects.requireNonNull(backend);
        }

        private Builder(Builder<T> other) {
            backend = other.backend.copy();
            tokenizer = other.tokenizer;
        }

        Builder<T> copy() {
            return new Builder<>(this);
        }

        <U> Builder<U> map(ValueConverter<T, U> converter) {
            Objects.requireNonNull(converter);
            return new Builder<>(new TwoStepBackend<>(
                    backend,
                    new StepBuilder<T, U>(false).converter(converter))).tokenizer(tokenizer);
        }

        <U> Builder<T> map(Builder<U> other) {
            Objects.requireNonNull(other);
            switch (backend) {
                case OneStepBackend<T> _ -> {
                    throw new UnsupportedOperationException();
                }
                case TwoStepBackend<?, T> b -> {
                    var fromInterimValueType = other.backend.valueType().orElseThrow();
                    var toInterimValueType = b.interimValueType();
                    if (fromInterimValueType.equals(toInterimValueType)) {
                        @SuppressWarnings("unchecked")
                        var twoStepBackend = (TwoStepBackend<U, T>)b;
                        return new Builder<>(new TwoStepBackend<>(
                                other.backend,
                                twoStepBackend.otherConvBuilder())).tokenizer(tokenizer);
                    } else {
                        throw new IllegalArgumentException(String.format(
                                "Expected (%s); actual (%s)", toInterimValueType, fromInterimValueType));
                    }
                }
            }
        }

        OptionValueConverter<String, T> create() {
            return backend.create();
        }

        OptionArrayValueConverter<T> createArray() {
            return new DefaultOptionArrayValueConverter<>(create(), tokenizer);
        }

        Builder<T> converter(ValueConverter<String, T> v) {
            switch (backend) {
                case OneStepBackend<T> b -> {
                    b.stringConvBuilder().converter(v);
                }
                case TwoStepBackend<?, T> _ -> {
                    throw new UnsupportedOperationException();
                }
            }
            return this;
        }

        Builder<T> validator(Validator<T, ? extends RuntimeException> v) {
            backend.validator(v);
            return this;
        }

        Builder<T> tokenizer(Function<String, String[]> v) {
            tokenizer = v;
            return this;
        }

        Builder<T> formatString(String v) {
            backend.formatString(v);
            return this;
        }

        Builder<T> exceptionFactory(OptionValueExceptionFactory<? extends RuntimeException> v) {
            backend.exceptionFactory(v);
            return this;
        }

        Builder<T> mutate(Consumer<Builder<T>> mutator) {
            mutator.accept(this);
            return this;
        }

        boolean hasConverter() {
            switch (backend) {
                case OneStepBackend<T> b -> {
                    return b.stringConvBuilder().converter().isPresent();
                }
                case TwoStepBackend<?, T> _ -> {
                    return true;
                }
            }
        }

        Optional<Validator<T, ? extends RuntimeException>> validator() {
            return backend.validator();
        }

        Optional<ValueConverter<String, T>> converter() {
            switch (backend) {
                case OneStepBackend<T> b -> {
                    return b.stringConvBuilder().converter();
                }
                case TwoStepBackend<?, T> _ -> {
                    throw new UnsupportedOperationException();
                }
            }
        }

        Optional<Function<String, String[]>> tokenizer() {
            return Optional.ofNullable(tokenizer);
        }

        Optional<String> formatString() {
            return backend.formatString();
        }

        Optional<OptionValueExceptionFactory<? extends RuntimeException>> exceptionFactory() {
            return backend.exceptionFactory();
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


        private record TwoStepOptionValueConverter<T, U>(OptionValueConverter<String, T> stringConverter,
                OptionValueConverter<T, U> converter) implements OptionValueConverter<String, U> {

            TwoStepOptionValueConverter {
                Objects.requireNonNull(stringConverter);
                Objects.requireNonNull(converter);
            }

            @Override
            public Result<U> convert(OptionName optionName, StringToken optionValue, String value) {
                final var interimResult = stringConverter.convert(optionName, optionValue, value);
                return interimResult.flatMap(interimValue -> {
                    return converter.convert(optionName, optionValue, interimValue);
                });
            }

            @Override
            public Class<? extends U> valueType() {
                return converter.valueType();
            }
        }


        private sealed interface Backend<T> {

            OptionValueConverter<String, T> create();

            Backend<T> copy();

            void validator(Validator<T, ? extends RuntimeException> v);

            void formatString(String v);

            void exceptionFactory(OptionValueExceptionFactory<? extends RuntimeException> v);

            Optional<Class<? extends T>> valueType();

            Optional<Validator<T, ? extends RuntimeException>> validator();

            Optional<String> formatString();

            Optional<OptionValueExceptionFactory<? extends RuntimeException>> exceptionFactory();
        }


        private record OneStepBackend<T>(StepBuilder<String, T> stringConvBuilder) implements Backend<T> {

            OneStepBackend {
                Objects.requireNonNull(stringConvBuilder);
            }

            @Override
            public Backend<T> copy() {
                return new OneStepBackend<>(stringConvBuilder.copy());
            }

            @Override
            public OptionValueConverter<String, T> create() {
                return stringConvBuilder.create();
            }

            @Override
            public void validator(Validator<T, ? extends RuntimeException> v) {
                stringConvBuilder.validator(v);
            }

            @Override
            public void formatString(String v) {
                stringConvBuilder.formatString(v);
            }

            @Override
            public void exceptionFactory(OptionValueExceptionFactory<? extends RuntimeException> v) {
                stringConvBuilder.exceptionFactory(v);
            }

            @Override
            public Optional<Class<? extends T>> valueType() {
                return stringConvBuilder.converter().map(ValueConverter::valueType);
            }

            @Override
            public Optional<Validator<T, ? extends RuntimeException>> validator() {
                return stringConvBuilder.validator();
            }

            @Override
            public Optional<String> formatString() {
                return stringConvBuilder.formatString();
            }

            @Override
            public Optional<OptionValueExceptionFactory<? extends RuntimeException>> exceptionFactory() {
                return stringConvBuilder.exceptionFactory();
            }
        }


        private record TwoStepBackend<T, U>(Backend<T> stringConvBuilder, StepBuilder<T, U> otherConvBuilder) implements Backend<U> {

            TwoStepBackend {
                Objects.requireNonNull(stringConvBuilder);
                Objects.requireNonNull(otherConvBuilder);
            }

            Class<? extends T> interimValueType() {
                return stringConvBuilder.valueType().orElseThrow();
            }

            @Override
            public Backend<U> copy() {
                return new TwoStepBackend<>(stringConvBuilder.copy(), otherConvBuilder.copy());
            }

            @Override
            public OptionValueConverter<String, U> create() {
                return new TwoStepOptionValueConverter<>(stringConvBuilder.create(), otherConvBuilder.create());
            }

            @Override
            public void validator(Validator<U, ? extends RuntimeException> v) {
                otherConvBuilder.validator(v);
            }

            @Override
            public void formatString(String v) {
                otherConvBuilder.formatString(v);
            }

            @Override
            public void exceptionFactory(OptionValueExceptionFactory<? extends RuntimeException> v) {
                otherConvBuilder.exceptionFactory(v);
            }

            @Override
            public Optional<Class<? extends U>> valueType() {
                return otherConvBuilder.converter().map(ValueConverter::valueType);
            }

            @Override
            public Optional<Validator<U, ? extends RuntimeException>> validator() {
                return otherConvBuilder.validator();
            }

            @Override
            public Optional<String> formatString() {
                return otherConvBuilder.formatString();
            }

            @Override
            public Optional<OptionValueExceptionFactory<? extends RuntimeException>> exceptionFactory() {
                return otherConvBuilder.exceptionFactory();
            }
        }


        private static final class StepBuilder<T, U> {

            private StepBuilder(boolean starter) {
                this.starter = starter;
            }

            private StepBuilder(StepBuilder<T, U> other) {
                starter = other.starter;
                converter = other.converter;
                validator = other.validator;
                formatString = other.formatString;
                exceptionFactory = other.exceptionFactory;
            }

            StepBuilder<T, U> copy() {
                return new StepBuilder<>(this);
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
                        validator(),
                        starter);
            }

            StepBuilder<T, U> converter(ValueConverter<T, U> v) {
                converter = v;
                return this;
            }

            StepBuilder<T, U> validator(Validator<U, ? extends RuntimeException> v) {
                validator = v;
                return this;
            }

            StepBuilder<T, U> formatString(String v) {
                formatString = v;
                return this;
            }

            StepBuilder<T, U> exceptionFactory(OptionValueExceptionFactory<? extends RuntimeException> v) {
                exceptionFactory = v;
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


            private record DefaultOptionValueConverter<T, U>(
                    ValueConverter<T, U> converter,
                    String formatString,
                    OptionValueExceptionFactory<? extends RuntimeException> exceptionFactory,
                    Optional<Validator<U, ? extends RuntimeException>> validator,
                    boolean starter) implements OptionValueConverter<T, U> {

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

                    if (starter && !value.equals(optionValue.value())) {
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


            private final boolean starter;
            private ValueConverter<T, U> converter;
            private Validator<U, ? extends RuntimeException> validator;
            private String formatString;
            private OptionValueExceptionFactory<? extends RuntimeException> exceptionFactory;
        }


        private final Backend<T> backend;
        private Function<String, String[]> tokenizer;
    }
}

