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
import java.util.Set;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.Result;


/**
 * Option spec.
 *
 * Names are used to identify an option. Converter converts option value from a
 * {@link String} to an object of type {@link T}. Scope defines where the option
 * applies. The standard scopes are: {@link BundlingOperationOptionScope},
 * {@link BundlingOperationModifier}. Merge policy defines how to handle
 * multiple values of the option. Value pattern and description are targeted for
 * help output.
 *
 * @param names                the names. Must be non-empty
 * @param converter            the converter. Converts from a {@link String} to
 *                             an object of type {@link T}
 * @param scope                the scope. Must be non-empty
 * @param defaultOptionalValue the default optional value or an empty
 *                             {@link Optional} instance if the option has a
 *                             mandatory value or doesn't have a value
 * @param mergePolicy          the merge policy
 * @param valuePattern         the value pattern. Used in help output
 * @param description          the description. Used in help output
 * @param <T>                  option value type
 */
record OptionSpec<T>(
        List<OptionName> names,
        Optional<OptionValueConverter<String, T>> converter,
        Set<OptionScope> scope,
        MergePolicy mergePolicy,
        Optional<T> defaultOptionalValue,
        Optional<String> valuePattern,
        String description) {

    enum MergePolicy {
        USE_FIRST,
        USE_LAST,
        CONCATENATE
    }

    OptionSpec {
        Objects.requireNonNull(names);
        if (names.isEmpty()) {
            throw new IllegalArgumentException("Empty name list");
        }
        Objects.requireNonNull(converter);
        Objects.requireNonNull(scope);
        if (scope.isEmpty()) {
            throw new IllegalArgumentException("Empty scope");
        }
        Objects.requireNonNull(mergePolicy);
        Objects.requireNonNull(valuePattern);
        if (converter.isEmpty() && valuePattern.isPresent()) {
            throw new IllegalArgumentException("Option without a value can not have a value pattern");
        }
        if (converter.isEmpty() && defaultOptionalValue.isPresent()) {
            throw new IllegalArgumentException("Option with optional value should have a converter");
        }
        Objects.requireNonNull(description);

        final var typeMustBeArray = mergePolicy.equals(MergePolicy.CONCATENATE);
        final var type = valueType(converter);
        if (typeMustBeArray && !type.map(Class::isArray).orElse(false)) {
            throw new IllegalArgumentException(String.format("Invalid merge policy [%s] for type [%s]",
                    mergePolicy, type.map(Class::getName).orElse("")));
        }

        names = List.copyOf(names);
        scope = Set.copyOf(scope);
    }

    /**
     * Returns the first (primary) name of this option spec.
     *
     * @return the first name of this option spec
     */
    OptionName name() {
        return names.getFirst();
    }

    /**
     * Returns all names but the first of this option spec. Returns an empty list if
     * the option spec has only one name.
     *
     * @return the additional names
     */
    List<OptionName> otherNames() {
        return names.subList(1, names.size());
    }

    /**
     * Returns a stream of copy option spec objects, each having a single name.
     * <p>
     * If the option has three names "a", "b", and "c", the stream will have three
     * option spec objects each with a single name. The first will have name "a", the
     * second - "b", and the third "c".
     *
     * @return the stream of copy option spec objects each having a single name
     */
    Stream<OptionSpec<T>> copyForEveryName() {
        return names().stream().map(v -> {
            return new OptionSpec<>(List.of(v), converter, scope, mergePolicy, defaultOptionalValue, valuePattern, description);
        });
    }

    <U> OptionSpec<U> copyWithConverter(OptionValueConverter<String, U> converter) {
        if (!defaultOptionalValue.isEmpty()) {
            throw new UnsupportedOperationException("Can not convert an option spec with optional value");
        }
        return new OptionSpec<>(names, Optional.of(converter), scope, mergePolicy, Optional.empty(), valuePattern, description);
    }

    OptionSpec<T> copyWithDescription(String description) {
        return new OptionSpec<>(names, converter, scope, mergePolicy, defaultOptionalValue, valuePattern, Objects.requireNonNull(description));
    }

    OptionSpec<T> copyWithName(OptionName name) {
        return new OptionSpec<>(List.of(name), converter, scope, mergePolicy, defaultOptionalValue, valuePattern, description);
    }

    List<OptionName> findNamesIn(Options cmdline) {
        return names().stream().filter(cmdline::contains).toList();
    }

    OptionName getFirstNameIn(Options cmdline) {
        return findNamesIn(cmdline).getFirst();
    }

    boolean hasValue() {
        return converter.isPresent();
    }

    boolean isValueOptional() {
        return defaultOptionalValue.isPresent();
    }

    Class<? extends T> valueType() {
        return valueType(converter).orElseThrow();
    }

    Result<T> convert(OptionName optionName, StringToken optionValue) {
        return OptionValueConverter.convertString(converter().orElseThrow(), optionName, optionValue);
    }

    @SuppressWarnings("unchecked")
    <U> Optional<OptionArrayValueConverter<U>> arrayValueConverter() {
        return converter.filter(OptionArrayValueConverter.class::isInstance).map(v -> (OptionArrayValueConverter<U>)v);
    }

    private static <T> Optional<Class<? extends T>> valueType(Optional<OptionValueConverter<String, T>> valueConverter) {
        return valueConverter.map(OptionValueConverter::valueType);
    }
}
