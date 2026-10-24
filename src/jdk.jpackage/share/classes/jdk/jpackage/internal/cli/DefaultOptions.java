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
import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;


final class DefaultOptions implements Options {

    DefaultOptions(Map<? extends WithOptionIdentifier, Object> values) {
        this(values, Optional.empty());
    }

    DefaultOptions(
            Map<? extends WithOptionIdentifier, Object> values,
            Predicate<OptionName> optionNamesFilter) {

        this(values, Optional.of(optionNamesFilter));
    }

    DefaultOptions(
            Map<? extends WithOptionIdentifier, Object> values,
            Optional<Predicate<OptionName>> optionNamesFilter) {

        map = values.entrySet().stream().collect(toUnmodifiableMap(e -> {
            return e.getKey().id();
        }, e -> {
            return new OptionIdentifierWithValue(e.getKey(), e.getValue());
        }));

        var optionNamesStream = optionNames(values.keySet().stream());
        optionNames = optionNamesFilter.map(optionNamesStream::filter).orElse(optionNamesStream)
                .collect(toUnmodifiableSet());
    }

    private DefaultOptions(Snapshot snapshot) {
        map = snapshot.map();
        optionNames = snapshot.optionNames();
    }

    static DefaultOptions create(Snapshot snapshot) {
        var options = new DefaultOptions(snapshot);

        var mapOptionNames = optionNames(
                options.map.values().stream().map(OptionIdentifierWithValue::withId)
        ).collect(toUnmodifiableSet());

        for (var e : options.map.entrySet()) {
            if (e.getKey() != e.getValue().withId().id()) {
                throw new IllegalArgumentException("Corrupted options map");
            }
        }

        if (!mapOptionNames.containsAll(snapshot.optionNames())) {
            throw new IllegalArgumentException("Unexpected option names");
        }
        return options;
    }

    @Override
    public Optional<Object> find(OptionIdentifier id) {
        return Optional.ofNullable(map.get(Objects.requireNonNull(id))).map(OptionIdentifierWithValue::value);
    }

    @Override
    public boolean contains(OptionName optionName) {
        return optionNames.contains(Objects.requireNonNull(optionName));
    }

    @Override
    public Set<? extends OptionIdentifier> ids() {
        return Collections.unmodifiableSet(map.keySet());
    }

    @Override
    public DefaultOptions copyWithout(Iterable<? extends OptionIdentifier> ids) {
        return copy(StreamSupport.stream(ids.spliterator(), false), false);
    }

    @Override
    public DefaultOptions copyWith(Iterable<? extends OptionIdentifier> ids) {
        return copy(StreamSupport.stream(ids.spliterator(), false), true);
    }

    DefaultOptions add(DefaultOptions other) {
        return new DefaultOptions(new Snapshot(Stream.of(this, other).flatMap(v -> {
            return v.map.values().stream();
        }).collect(toUnmodifiableMap(OptionIdentifierWithValue::id, x -> x, (first, _) -> {
            return first;
        })), Stream.of(this, other)
                .map(DefaultOptions::optionNames)
                .flatMap(Collection::stream)
                .collect(toUnmodifiableSet())));
    }

    Set<OptionName> optionNames() {
        return optionNames;
    }

    Set<WithOptionIdentifier> withOptionIdentifierSet() {
        return map.values().stream()
                .map(OptionIdentifierWithValue::withId)
                .collect(toUnmodifiableSet());
    }

    record Snapshot(Map<OptionIdentifier, OptionIdentifierWithValue> map, Set<OptionName> optionNames) {
        Snapshot {
            Objects.requireNonNull(map);
            Objects.requireNonNull(optionNames);
        }
    }

    record OptionIdentifierWithValue(WithOptionIdentifier withId, Object value) {
        OptionIdentifierWithValue {
            Objects.requireNonNull(withId);
            Objects.requireNonNull(value);
        }

        OptionIdentifier id() {
            return withId.id();
        }

        OptionIdentifierWithValue copyWithValue(Object value) {
            return new OptionIdentifierWithValue(withId, value);
        }
    }

    private DefaultOptions copy(Stream<? extends OptionIdentifier> ids, boolean includes) {
        var includeIds = ids.collect(toUnmodifiableSet());
        return new DefaultOptions(map.values().stream().filter(v -> {
            return includeIds.contains(v.id()) == includes;
        }).collect(toUnmodifiableMap(OptionIdentifierWithValue::withId, OptionIdentifierWithValue::value)));
    }

    private static Stream<OptionName> optionNames(Stream<? extends WithOptionIdentifier> options) {
        return options.map(v -> {
            Optional<? extends OptionSpec<?>> spec;
            switch (v) {
                case Option option -> {
                    spec = Optional.of(option.spec());
                }
                case OptionValue<?> optionValue -> {
                    spec = optionValue.asOption().map(Option::spec);
                }
                default -> {
                    spec = Optional.empty();
                }
            }
            return spec;
        }).filter(Optional::isPresent).map(Optional::get).map(OptionSpec::names).flatMap(Collection::stream);
    }

    static final DefaultOptions EMPTY = new DefaultOptions(Map.of());

    private final Map<OptionIdentifier, OptionIdentifierWithValue> map;
    private final Set<OptionName> optionNames;
}
