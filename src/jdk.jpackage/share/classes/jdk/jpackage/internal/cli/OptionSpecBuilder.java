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

import java.io.File;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import jdk.jpackage.internal.cli.OptionSpec.MergePolicy;

final class OptionSpecBuilder<T> {

    static <T> OptionSpecBuilder<T> create(Class<? extends T> valueType) {
        return new OptionSpecBuilder<>(valueType);
    }

    static String pathSeparator() {
        return File.pathSeparator;
    }

    static <T> Function<OptionValue.Builder<T[]>, OptionValue<List<T>>> toList() {
        return builder -> {
            return builder.to(List::of).create();
        };
    }

    private OptionSpecBuilder(Class<? extends T> valueType) {
        this.valueType = Objects.requireNonNull(valueType);
    }

    OptionSpecBuilder(OptionSpecBuilder<T> other) {
        valueType = other.valueType;
        name = other.name;
        nameAliases.addAll(other.nameAliases);
        description = other.description;
        mergePolicy = other.mergePolicy;
        scope = Set.copyOf(other.scope);
        defaultValue = other.defaultValue;
        defaultOptionalValue = other.defaultOptionalValue;
        valuePattern = other.valuePattern;
        converterBuilder = other.converterBuilder.copy();
        validatorBuilder = other.validatorBuilder.copy();

        if (other.arrayDefaultValue != null) {
            arrayDefaultValue = Arrays.copyOf(other.arrayDefaultValue, arrayDefaultValue.length);
        }
        arrayValuePatternSeparator = other.arrayValuePatternSeparator;
        arrayTokenizer = other.arrayTokenizer;
    }

    OptionSpecBuilder<T> copy() {
        return new OptionSpecBuilder<>(this);
    }

    Class<? extends T> valueType() {
        return valueType;
    }

    OptionValue<T> create() {
        return toOptionValueBuilder().create();
    }

    OptionValue<T[]> createArray() {
        return toArrayOptionValueBuilder().create();
    }

    <U> OptionValue<U> create(Function<OptionValue.Builder<T>, OptionValue<U>> transformer) {
        return transformer.apply(toOptionValueBuilder());
    }

    <U> OptionValue<U> createArray(Function<OptionValue.Builder<T[]>, OptionValue<U>> transformer) {
        return transformer.apply(toArrayOptionValueBuilder());
    }

    OptionValue.Builder<T> toOptionValueBuilder() {
        final var builder = OptionValue.<T>build().spec(createOptionSpec());
        defaultValue().ifPresent(builder::defaultValue);
        return builder;
    }

    OptionValue.Builder<T[]> toArrayOptionValueBuilder() {
        final var builder = OptionValue.<T[]>build().spec(createArrayOptionSpec());
        defaultArrayValue().ifPresent(builder::defaultValue);
        return builder;
    }

    OptionSpec<T> createOptionSpec() {
        return new OptionSpec<>(
                names(),
                createConverter(),
                scope,
                mergePolicy().orElse(MergePolicy.USE_LAST),
                defaultOptionalValue(),
                valuePattern(),
                description().orElse(""));
    }

    OptionSpec<T[]> createArrayOptionSpec() {
        return new OptionSpec<>(
                names(),
                Optional.of(createArrayConverter()),
                scope,
                OptionSpecBuilder.this.mergePolicy().orElse(MergePolicy.CONCATENATE),
                defaultArrayOptionalValue(),
                Optional.of(arryValuePattern()),
                OptionSpecBuilder.this.description().orElse(""));
    }

    OptionSpecBuilder<T> tokenizer(String splitRegexp) {
        Objects.requireNonNull(splitRegexp);
        return tokenizer(str -> {
            return str.split(splitRegexp);
        }).arrayValuePatternSeparator(splitRegexp);
    }

    OptionSpecBuilder<T> tokenizeOne() {
        return tokenizer((Function<String, String[]>)null);
    }

    OptionSpecBuilder<T> tokenizer(Function<String, String[]> v) {
        arrayTokenizer = v;
        return this;
    }

    OptionSpecBuilder<T> mutate(Consumer<OptionSpecBuilder<T>> mutator) {
        mutator.accept(this);
        return this;
    }

    OptionSpecBuilder<T> validatorExceptionFormatString(String v) {
        validatorBuilder.formatString(v);
        return this;
    }

    OptionSpecBuilder<T> validatorExceptionFormatString(UnaryOperator<String> mutator) {
        validatorBuilder.formatString(mutator.apply(validatorBuilder.formatString().orElse(null)));
        return this;
    }

    OptionSpecBuilder<T> converterExceptionFormatString(String v) {
        converterBuilder.formatString(v);
        return this;
    }

    OptionSpecBuilder<T> converterExceptionFormatString(UnaryOperator<String> mutator) {
        converterBuilder.formatString(mutator.apply(converterBuilder.formatString().orElse(null)));
        return this;
    }

    OptionSpecBuilder<T> validatorExceptionFactory(OptionValueExceptionFactory<? extends RuntimeException> v) {
        validatorBuilder.exceptionFactory(v);
        return this;
    }

    OptionSpecBuilder<T> validatorExceptionFactory(UnaryOperator<OptionValueExceptionFactory<? extends RuntimeException>> mutator) {
        return validatorExceptionFactory(mutator.apply(validatorBuilder.exceptionFactory().orElse(null)));
    }

    OptionSpecBuilder<T> converterExceptionFactory(OptionValueExceptionFactory<? extends RuntimeException> v) {
        converterBuilder.exceptionFactory(v);
        return this;
    }

    OptionSpecBuilder<T> converterExceptionFactory(UnaryOperator<OptionValueExceptionFactory<? extends RuntimeException>> mutator) {
        return converterExceptionFactory(mutator.apply(converterBuilder.exceptionFactory().orElse(null)));
    }

    OptionSpecBuilder<T> exceptionFormatString(String v) {
        return validatorExceptionFormatString(v).converterExceptionFormatString(v);
    }

    OptionSpecBuilder<T> exceptionFormatString(UnaryOperator<String> mutator) {
        return validatorExceptionFormatString(mutator).converterExceptionFormatString(mutator);
    }

    OptionSpecBuilder<T> exceptionFactory(OptionValueExceptionFactory<? extends RuntimeException> v) {
        return validatorExceptionFactory(v).converterExceptionFactory(v);
    }

    OptionSpecBuilder<T> exceptionFactory(UnaryOperator<OptionValueExceptionFactory<? extends RuntimeException>> mutator) {
        return validatorExceptionFactory(mutator).converterExceptionFactory(mutator);
    }

    OptionSpecBuilder<T> converter(ValueConverter<T> v) {
        converterBuilder.converter(v);
        return this;
    }

    OptionSpecBuilder<T> converter(Function<String, T> v) {
        return converter(ValueConverter.create(v, valueType));
    }

    OptionSpecBuilder<T> validator(Predicate<T> v) {
        validatorBuilder.predicate(v::test);
        return this;
    }

    @SuppressWarnings("overloads")
    OptionSpecBuilder<T> validator(Consumer<T> v) {
        validatorBuilder.consumer(v::accept);
        return this;
    }

    @SuppressWarnings("overloads")
    OptionSpecBuilder<T> validator(UnaryOperator<Validator.Builder<T, RuntimeException>> mutator) {
        validatorBuilder = mutator.apply(validatorBuilder);
        return this;
    }

    OptionSpecBuilder<T> withoutConverter() {
        converterBuilder.converter(null);
        return this;
    }

    OptionSpecBuilder<T> withoutValidator() {
        validatorBuilder.predicate(null).consumer(null);
        return this;
    }

    OptionSpecBuilder<T> name(String v) {
        name = v;
        return this;
    }

    OptionSpecBuilder<T> addAliases(String... v) {
        nameAliases.addAll(List.of(v));
        return this;
    }

    OptionSpecBuilder<T> description(String v) {
        description = v;
        return this;
    }

    OptionSpecBuilder<T> mergePolicy(MergePolicy v) {
        mergePolicy = v;
        return this;
    }

    OptionSpecBuilder<T> scope(OptionScope... v) {
        return scope(Set.of(v));
    }

    OptionSpecBuilder<T> scope(Collection<? extends OptionScope> v) {
        scope = Set.copyOf(v);
        return this;
    }

    OptionSpecBuilder<T> scope(UnaryOperator<Set<OptionScope>> mutator) {
        return scope(mutator.apply(scope().orElseGet(Set::of)));
    }

    OptionSpecBuilder<T> inScope(OptionScope... v) {
        return inScope(Set.of(v));
    }

    OptionSpecBuilder<T> inScope(Collection<? extends OptionScope> v) {
        final Set<OptionScope> newScope = new HashSet<>(v);
        scope().ifPresent(newScope::addAll);
        scope = newScope;
        return this;
    }

    OptionSpecBuilder<T> outOfScope(OptionScope... v) {
        return outOfScope(Set.of(v));
    }

    OptionSpecBuilder<T> outOfScope(Collection<? extends OptionScope> v) {
        if (scope != null) {
            final Set<OptionScope> newScope = new HashSet<>(scope);
            newScope.removeAll(v);
            scope = newScope;
        }
        return this;
    }

    OptionSpecBuilder<T> defaultValue(T v) {
        defaultValue = v;
        return this;
    }

    OptionSpecBuilder<T> defaultOptionalValue(T v) {
        defaultOptionalValue = v;
        return this;
    }

    OptionSpecBuilder<T> defaultArrayValue(T[] v) {
        arrayDefaultValue = v;
        return this;
    }

    OptionSpecBuilder<T> arrayValuePatternSeparator(String v) {
        arrayValuePatternSeparator = v;
        return this;
    }

    OptionSpecBuilder<T> valuePattern(String v) {
        valuePattern = v;
        return this;
    }

    private Optional<String> name() {
        return Optional.ofNullable(name);
    }

    private Optional<String> description() {
        return Optional.ofNullable(description);
    }

    private Optional<MergePolicy> mergePolicy() {
        return Optional.ofNullable(mergePolicy);
    }

    private Optional<Set<OptionScope>> scope() {
        return Optional.ofNullable(scope);
    }

    private Optional<T> defaultValue() {
        return Optional.ofNullable(defaultValue);
    }

    private Optional<T[]> defaultArrayValue() {
        return Optional.ofNullable(arrayDefaultValue).or(() -> {
            return OptionSpecBuilder.this.defaultValue().map(this::toOneElementArray);
        });
    }

    private Optional<T> defaultOptionalValue() {
        return Optional.ofNullable(defaultOptionalValue);
    }

    private Optional<T[]> defaultArrayOptionalValue() {
        return defaultOptionalValue().map(this::toOneElementArray);
    }

    private Optional<String> valuePattern() {
        return Optional.ofNullable(valuePattern).or(this::defaultValuePattern).map(str -> {
            if (str.isEmpty()) {
                return "<>";
            } else {
                var sb = new StringBuilder();
                switch (str.charAt(0)) {
                    case '<', '[' -> {
                    }
                    default -> {
                        sb.append("<");
                    }
                }
                sb.append(str);
                switch (str.charAt(str.length() - 1)) {
                    case '>', ']' -> {
                    }
                    default -> {
                        sb.append(">");
                    }
                }
                return sb.toString();
            }
        });
    }

    private Optional<String> defaultValuePattern() {
        return converterBuilder.converter().map(_ -> {
            final var tokens = name.split("-");
            return tokens[tokens.length - 1];
        });
    }

    private List<OptionName> names() {
        return Stream.of(
                List.of(name().orElseThrow()),
                nameAliases
        ).flatMap(Collection::stream).map(OptionName::new).distinct().toList();
    }

    private Optional<OptionValueConverter<T>> createConverter() {
        if (converterBuilder.converter().isPresent()) {
            final var newBuilder = converterBuilder.copy();
            createValidator().ifPresent(newBuilder::validator);
            return Optional.of(newBuilder.create());
        } else {
            return Optional.empty();
        }
    }

    private Optional<Validator<T, ? extends RuntimeException>> createValidator() {
        if (validatorBuilder.hasValidatingMethod()) {
            return Optional.of(validatorBuilder.create());
        } else {
            return Optional.empty();
        }
    }

    private OptionValueConverter<T[]> createArrayConverter() {
        final var newBuilder = converterBuilder.copy();
        newBuilder.tokenizer(Optional.ofNullable(arrayTokenizer).orElse(str -> {
            return new String[] { str };
        }));
        createValidator().ifPresent(newBuilder::validator);
        return newBuilder.createArray();
    }

    private String arryValuePattern() {
        final var elementValuePattern = OptionSpecBuilder.this.valuePattern().orElseThrow();
        if (arrayValuePatternSeparator == null) {
            return elementValuePattern;
        } else {
            return String.format("%s[%s%s...]", elementValuePattern, arrayValuePatternSeparator, elementValuePattern);
        }
    }

    private T[] toOneElementArray(T v) {
        Objects.requireNonNull(v);
        @SuppressWarnings("unchecked")
        final var arr = (T[]) Array.newInstance(valueType, 1);
        arr[0] = v;
        return arr;
    }

    private final Class<? extends T> valueType;
    private String name;
    private Collection<String> nameAliases = new LinkedHashSet<>();
    private String description;
    private MergePolicy mergePolicy;
    private Set<OptionScope> scope;
    private T defaultValue;
    private T defaultOptionalValue;
    private String valuePattern;
    private OptionValueConverter.Builder<T> converterBuilder = OptionValueConverter.build();
    private Validator.Builder<T, RuntimeException> validatorBuilder = Validator.build();

    private T[] arrayDefaultValue;
    private String arrayValuePatternSeparator;
    private Function<String, String[]> arrayTokenizer;
}
