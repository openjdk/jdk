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

import static java.util.stream.Collectors.toUnmodifiableMap;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;


/**
 * R/O collection of objects associated with option identifiers.
 * <p>
 * Use {@link OptionValue} for typed access of the stored objects.
 */
public sealed interface Options permits
        JOptSimpleOptionsBuilder.ExtendedOptions,
        DefaultOptions {

    Optional<Object> find(OptionIdentifier id);

    boolean contains(OptionName optionName);

    Set<? extends OptionIdentifier> ids();

    default Optional<Object> find(WithOptionIdentifier withId) {
        return find(withId.id());
    }

    default boolean contains(OptionIdentifier id) {
        return find(id).isPresent();
    }

    default boolean contains(WithOptionIdentifier withId) {
        return find(withId).isPresent();
    }

    default Options copyWithDefaultValue(WithOptionIdentifier withId, Object value) {
        Objects.requireNonNull(value);
        return copyWithDefaultValue(withId, () -> value);
    }

    default Options copyWithDefaultValue(WithOptionIdentifier withId, Supplier<Object> valueSupplier) {
        Objects.requireNonNull(withId);
        Objects.requireNonNull(valueSupplier);
        if (contains(withId)) {
            return this;
        } else {
            return copyWithParent(of(Map.of(withId, valueSupplier.get())));
        }
    }

    default Options copyWithParent(Options other) {
        return concat(this, other);
    }

    /**
     * Creates a copy of this instance without the given option identifiers.
     * <p>
     * {@link #contains(OptionIdentifier)} called on the return instance with any of
     * the given identifiers will return {@code false}.
     * {@link #contains(OptionName)} called on the return instance with any option
     * name of option specifications associated with given identifiers will return
     * {@code false}.
     *
     * @param ids the identifiers to exclude
     * @return a copy of this instance without the given option identifiers
     */
    default Options copyWithout(OptionIdentifier... ids) {
        return copyWithout(List.of(ids));
    }

    /**
     * Creates a copy of this instance without the given option identifiers.
     * <p>
     * Same as {@link #copyWithout(OptionIdentifier...)} but takes an
     * {@code Iterable<OptionIdentifier>} instead of an {@code OptionIdentifier[]}.
     *
     * @param ids the identifiers to exclude
     * @return a copy of this instance without the given option identifiers
     */
    Options copyWithout(Iterable<? extends OptionIdentifier> ids);

    /**
     * Creates a copy of this instance with all but the given option identifiers
     * filtered out.
     * <p>
     * {@link #contains(OptionIdentifier)} called on the returned instance with any
     * identifiers not in the given array will return {@code false}.
     * {@link #contains(OptionName)} called on the returned instance with any name
     * returned by {@link OptionSpec#name()} method called on option specifications
     * of identifiers of type {@code Option} not from the given identifier array
     * will return {@code false}.
     *
     * @param ids the identifiers to keep in the copy
     * @return a copy of this instance with all but the given option identifiers
     *         filtered out
     */
    default Options copyWith(OptionIdentifier... ids) {
        return copyWith(List.of(ids));
    }

    /**
     * Creates a copy of this instance with all but the given option identifiers
     * filtered out.
     * <p>
     * Same as {@link #copyWith(OptionIdentifier...)} but takes an
     * {@code Iterable<OptionIdentifier>} instead of an {@code OptionIdentifier[]}.
     *
     * @param ids the identifiers to keep in the copy
     * @return a copy of this instance with all but the given option identifiers
     *         filtered out
     */
    default Options copyWith(Iterable<? extends OptionIdentifier> ids) {
        var excludes = new HashSet<>(ids());
        ids.forEach(excludes::remove);
        return copyWithout(excludes);
    }

    /**
     * Returns a map representation of this instance.
     * @return the map representation of this instance.
     */
    default Map<OptionIdentifier, Object> toMap() {
        return StreamSupport.stream(ids().spliterator(), false).collect(toUnmodifiableMap(x -> x, id -> {
            return find(id).orElseThrow();
        }));
    }

    /**
     * Creates {@code Options} instance from the map of objects with option
     * identifiers and associated option values.
     *
     * @param map the map of objects with option identifiers and associated option
     *            values
     * @return a new {@code Options} instance
     */
    public static Options of(Map<? extends WithOptionIdentifier, Object> map) {
        return new DefaultOptions(map);
    }

    /**
     * Creates {@code Options} instance from the map of option identifiers and
     * associated option values.
     * <p>
     * Similar to {@link #of(Map)} method, but {@link #contains(OptionName)} called
     * on the return instance will always return {@code false}.
     *
     * @param map the map of option identifiers and associated option values
     * @return a new {@code Options} instance
     */
    public static Options ofIDs(Map<OptionIdentifier, Object> map) {
        return new DefaultOptions(map.entrySet().stream().collect(toUnmodifiableMap(e -> {
            return new WithOptionIdentifierStub(e.getKey());
        }, Map.Entry::getValue)));
    }

    public static Options concat(Options... options) {
        return Stream.of(options).map(Options::toDefaultOptions).reduce(DefaultOptions.EMPTY, DefaultOptions::add);
    }

    private static DefaultOptions toDefaultOptions(Options v) {
        switch (Objects.requireNonNull(v)) {
            case DefaultOptions u -> {
                return u;
            }
            case JOptSimpleOptionsBuilder.ExtendedOptions<?> u -> {
                return u.toDefaultOptions();
            }
        }
    }
}
