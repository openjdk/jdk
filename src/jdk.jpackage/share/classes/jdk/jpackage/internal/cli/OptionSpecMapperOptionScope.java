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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.IdentityWrapper;
import jdk.jpackage.internal.util.SetBuilder;

/**
 * Option scope facilitating mapping of {@link OptionSpec} instances based on
 * the given context.
 *
 * <p>
 * Use with option specs whose behavior is a function of a context. E.g.: if
 * option value validation depends on the current OS; if option description
 * varies depending on the current OS.
 *
 * @param <T> the type of option value
 * @param <U> the type of context
 */
sealed interface OptionSpecMapperOptionScope<T, U> extends OptionScope {

    OptionSpec<T> createOptionSpec(U context, boolean createArray);

    Class<? extends U> contextType();

    @SuppressWarnings("unchecked")
    static <T, U> OptionSpec<T> mapOptionSpec(OptionSpec<T> optionSpec, U context) {
        var mappedOptionSpec = optionSpec.scope().stream()
                .filter(OptionSpecMapperOptionScope.class::isInstance)
                .map(OptionSpecMapperOptionScope.class::cast)
                .filter(scope -> {
                    return scope.contextType().isInstance(context);
                })
                .findFirst().map(scope -> {
                    return ((OptionSpecMapperOptionScope<T, U>)scope).createOptionSpec(
                            context, optionSpec.arrayValueConverter().isPresent());
                }).orElse(optionSpec);

        Optional<?> valueType = optionSpec.converter().map(OptionValueConverter::valueType);
        Optional<?> mappedValueType = mappedOptionSpec.converter().map(OptionValueConverter::valueType);

        while (!mappedValueType.equals(valueType)) {
            // Source and mapped option specs have different option value types.
            if (Stream.of(valueType, mappedValueType).anyMatch(Optional::isEmpty) &&
                    Stream.of(valueType, mappedValueType)
                    .filter(Optional::isPresent).map(Optional::get)
                    .anyMatch(Predicate.isEqual(Boolean.class))) {
                // One option spec doesn't have a converter and another has a converter of type `Boolean`.
                // They are compatible, let it pass.
                break;
            }

            throw new IllegalStateException(String.format("Bad option spec mapping from %s to %s", valueType, mappedValueType));
        }

        return mappedOptionSpec;
    }

    static <T, U> Consumer<OptionSpecBuilder<T>> createOptionSpecBuilderMutator(
            Class<? extends U> contextType,
            BiConsumer<OptionSpecBuilder<T>, U> mutator) {
        Objects.requireNonNull(mutator);

        return builder -> {
            builder.scope(scope -> {
                return Details.<T, U>addOptionSpecBuilderMutator(contextType, scope, mutator, builder);
            });
        };
    }

    static final class Details {

        private Details() {
        }

        private static <T, U> Set<OptionScope> addOptionSpecBuilderMutator(
                Class<? extends U> contextType,
                Set<OptionScope> scope,
                BiConsumer<OptionSpecBuilder<T>, U> optionSpecBuilderMutator,
                OptionSpecBuilder<T> optionSpecBuilder) {

            Objects.requireNonNull(contextType);
            Objects.requireNonNull(scope);
            Objects.requireNonNull(optionSpecBuilderMutator);
            Objects.requireNonNull(optionSpecBuilder);

            var contextOptionScope = scope.stream()
                    .filter(AccumulatingContextOptionScope.class::isInstance)
                    .map(v -> {
                        @SuppressWarnings("unchecked")
                        var tv = (AccumulatingContextOptionScope<T, U>)v;
                        return tv;
                    })
                    .filter(s -> {
                        return s.contextType().equals(contextType);
                    })
                    .findFirst();

            contextOptionScope.ifPresent(v -> {
                v.addMutator(optionSpecBuilder, optionSpecBuilderMutator);
            });

            if (contextOptionScope.isEmpty()) {
                var mutators = new AccumulatingContextOptionScope<T, U>(contextType);
                mutators.addMutator(optionSpecBuilder, optionSpecBuilderMutator);
                scope = SetBuilder.<OptionScope>build().add(scope).add(mutators).create();
            }

            return scope;
        }

        private static final class AccumulatingContextOptionScope<T, U> implements OptionSpecMapperOptionScope<T, U> {

            AccumulatingContextOptionScope(Class<? extends U> contextType) {
                this.contextType = Objects.requireNonNull(contextType);
            }

            @SuppressWarnings("unchecked")
            @Override
            public OptionSpec<T> createOptionSpec(U context, boolean createArray) {
                var it = builders.values().iterator();

                var builder = it.next().initBuilder(context, Optional.empty());
                while (it.hasNext()) {
                    builder = it.next().initBuilder(context, Optional.of(builder));
                }

                if (createArray) {
                    return (OptionSpec<T>)builder.createArrayOptionSpec();
                } else {
                    return (OptionSpec<T>)builder.createOptionSpec();
                }
            }

            @Override
            public Class<? extends U> contextType() {
                return contextType;
            }

            <V> void addMutator(OptionSpecBuilder<V> builder, BiConsumer<OptionSpecBuilder<V>, U> mutator) {
                @SuppressWarnings("unchecked")
                var builderWithMutators = ((OptionSpecBuilderWithMutators<V, U>)builders.computeIfAbsent(new IdentityWrapper<>(builder), _ -> {
                    return new OptionSpecBuilderWithMutators<V, U>(builder);
                }));

                builderWithMutators.addMutator(mutator);
            }

            private final Class<? extends U> contextType;
            private final SequencedMap<IdentityWrapper<OptionSpecBuilder<?>>, OptionSpecBuilderWithMutators<?, U>> builders = new LinkedHashMap<>();
        }

        private static final class OptionSpecBuilderWithMutators<T, U> {

            OptionSpecBuilderWithMutators(OptionSpecBuilder<T> builder) {
                this.builder = Objects.requireNonNull(builder);
            }

            OptionSpecBuilder<T> initBuilder(U context, Optional<OptionSpecBuilder<?>> other) {
                Objects.requireNonNull(context);
                Objects.requireNonNull(other);

                var copy = builder.copy();
                other.ifPresent(copy::interimConverter);
                for (var mutator : mutators) {
                    mutator.accept(copy, context);
                }
                return copy;
            }

            void addMutator(BiConsumer<OptionSpecBuilder<T>, U> mutator) {
                mutators.add(Objects.requireNonNull(mutator));
            }

            private final OptionSpecBuilder<T> builder;
            private final List<BiConsumer<OptionSpecBuilder<T>, U>> mutators = new ArrayList<>();
        }
    }
}
