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

import static java.util.stream.Collectors.toSet;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;


/**
 * R/O collection of objects associated with identifiers.
 * <p>
 * Use {@link OptionValue} for typed access of the stored objects.
 */
public interface Options {

    Optional<Object> find(OptionIdentifier id);

    boolean contains(OptionName optionName);

    Set<? extends OptionIdentifier> ids();

    default boolean contains(OptionIdentifier id) {
        return find(id).isPresent();
    }

    default <T> Options copyWithDefaultValue(OptionValue<T> option, T value) {
        return copyWithDefaultValue(option.id(), value);
    }

    default Options copyWithDefaultValue(OptionIdentifier id, Object value) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(value);
        if (contains(id)) {
            return this;
        } else {
            return concat(this, of(Map.of(id, value)));
        }
    }

    default Options copyWithParent(Options other) {
        return concat(this, other);
    }

    /**
     * Creates a copy of this instance without the given option identifiers.
     * <p>
     * {@link #contains(OptionIdentifier)} called on the returned instance with any
     * of the given identifiers will return {@code false}.
     * {@link #contains(OptionName)} called on the returned instance with any name
     * returned by {@link OptionSpec#name()}} method called on option specifications
     * of identifiers of type {@code Option} from the given identifier array will
     * return {@code false}.
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
     * Same as {@link #contains(OptionIdentifier...)} but takes an
     * {@code Iterable<OptionIdentifier>} instead of an {@code OptionIdentifier[]}.
     *
     * @param ids the identifiers to exclude
     * @return a copy of this instance without the given option identifiers
     */
    default Options copyWithout(Iterable<OptionIdentifier> ids) {
        return Internal.copyWithout(this, StreamSupport.stream(ids.spliterator(), false));
    }

    /**
     * Creates a copy of this instance without the given option values.
     * <p>
     * Same as {@link #contains(OptionIdentifier...)} but takes
     * {@code OptionValue[]} instead of {@code OptionIdentifier[]}.
     *
     * @param ids the option values whose identifiers to exclude
     * @return a copy of this instance without the identifiers of the given option
     *         values
     */
    default Options copyWithout(OptionValue<?>... options) {
        return copyWithoutValues(List.of(options));
    }

    /**
     * Creates a copy of this instance without the given option values.
     * <p>
     * Same as {@link #contains(OptionIdentifier...)} but takes
     * {@code Iterable<OptionValue<?>>} instead of {@code OptionIdentifier[]}.
     *
     * @param ids the option values whose identifiers to exclude
     * @return a copy of this instance without the identifiers of the given option
     *         values
     */
    default Options copyWithoutValues(Iterable<OptionValue<?>> options) {
        return Internal.copyWithout(this, StreamSupport.stream(options.spliterator(), false).map(OptionValue::id));
    }

    /**
     * Returns a map representation of this instance.
     * @return the map representation of this instance.
     */
    default Map<OptionIdentifier, Object> toMap() {
        return StreamSupport.stream(ids().spliterator(), false).collect(Collectors.toMap(x -> x, id -> {
            return find(id).orElseThrow();
        }));
    }

    public static Options of(Map<OptionIdentifier, Object> map) {
        final var mapCopy = Map.copyOf(map);
        final var optionNames = Internal.optionNames(map.keySet().stream());
        return new Options() {
            @Override
            public Optional<Object> find(OptionIdentifier id) {
                return Optional.ofNullable(mapCopy.get(id));
            }

            @Override
            public boolean contains(OptionName optionName) {
                return optionNames.contains(Objects.requireNonNull(optionName));
            }

            @Override
            public Set<? extends OptionIdentifier> ids() {
                return map.keySet();
            }
        };
    }

    public static Options concat(Options... options) {
        final var copy = List.copyOf(List.of(options));
        return new Options() {
            @Override
            public Optional<Object> find(OptionIdentifier id) {
                Objects.requireNonNull(id);
                return copy.stream().map(o -> {
                    return o.find(id);
                }).filter(Optional::isPresent).map(Optional::orElseThrow).findFirst();
            }

            @Override
            public boolean contains(OptionName optionName) {
                Objects.requireNonNull(optionName);
                return copy.stream().anyMatch(v -> {
                    return v.contains(optionName);
                });
            }

            @Override
            public Set<? extends OptionIdentifier> ids() {
                return copy.stream().map(Options::ids).flatMap(Collection::stream).collect(toSet());
            }
        };
    }


    static final class Internal {

        private Internal() {
        }

        private static Set<OptionName> optionNames(Stream<? extends OptionIdentifier> options) {
            return options
                    .filter(Option.class::isInstance)
                    .map(Option.class::cast)
                    .map(Option::getSpec)
                    .map(OptionSpec::names)
                    .flatMap(Collection::stream)
                    .collect(toSet());
        }

        private static Options copyWithout(Options optionValues, Stream<? extends OptionIdentifier> ids) {
            Objects.requireNonNull(optionValues);
            final var excludes = ids.collect(toSet());
            final var includes = StreamSupport.stream(optionValues.ids().spliterator(), false)
                    .filter(Predicate.not(excludes::contains))
                    .collect(toSet());
            final var optionNames = optionNames(includes.stream());
            return new Options() {
                @Override
                public Optional<Object> find(OptionIdentifier id) {
                    if (includes.contains(Objects.requireNonNull(id))) {
                        return optionValues.find(id);
                    } else {
                        return Optional.empty();
                    }
                }

                @Override
                public boolean contains(OptionName optionName) {
                    if (optionNames.contains(Objects.requireNonNull(optionName))) {
                        return optionValues.contains(optionName);
                    } else {
                        return false;
                    }
                }

                @Override
                public Set<? extends OptionIdentifier> ids() {
                    return includes;
                }
            };
        }
    }
}
