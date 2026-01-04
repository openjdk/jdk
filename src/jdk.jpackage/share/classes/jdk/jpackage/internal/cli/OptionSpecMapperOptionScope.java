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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
 * @param <T> the type of context
 */
interface OptionSpecMapperOptionScope<T, U> extends OptionScope {

    OptionSpec<T> createOptionSpec(U context, boolean createArray);

    Class<? extends U> contextType();

    @SuppressWarnings("unchecked")
    static <T, U> OptionSpec<T> mapOptionSpec(OptionSpec<T> optionSpec, U context) {
        return optionSpec.scope().stream()
                .filter(OptionSpecMapperOptionScope.class::isInstance)
                .map(OptionSpecMapperOptionScope.class::cast)
                .filter(scope -> {
                    return scope.contextType().isInstance(context);
                })
                .findFirst().map(scope -> {
                    return ((OptionSpecMapperOptionScope<T, U>)scope).createOptionSpec(
                            context, optionSpec.arrayValueConverter().isPresent());
                }).orElse(optionSpec);
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
                    .map(AccumulatingContextOptionScope.class::cast)
                    .filter(s -> {
                        return s.contextType().equals(contextType);
                    })
                    .findFirst();
            contextOptionScope.ifPresent(v -> {
                @SuppressWarnings("unchecked")
                var mutators = (AccumulatingContextOptionScope<T, U>)v;
                mutators.addMutator(optionSpecBuilderMutator);
                if (optionSpecBuilder != mutators.optionSpecBuilder) {
                    throw new IllegalArgumentException();
                }
            });

            if (contextOptionScope.isEmpty()) {
                var mutators = new AccumulatingContextOptionScope<T, U>(optionSpecBuilder, contextType);
                mutators.addMutator(optionSpecBuilderMutator);
                scope = SetBuilder.<OptionScope>build().add(scope).add(mutators).create();
            }

            return scope;
        }

        private static final class AccumulatingContextOptionScope<T, U> implements OptionSpecMapperOptionScope<T, U> {

            AccumulatingContextOptionScope(OptionSpecBuilder<T> optionSpecBuilder, Class<? extends U> contextType) {
                this.optionSpecBuilder = Objects.requireNonNull(optionSpecBuilder);
                this.contextType = Objects.requireNonNull(contextType);
            }

            @SuppressWarnings("unchecked")
            @Override
            public OptionSpec<T> createOptionSpec(U context, boolean createArray) {
                var copy = optionSpecBuilder.copy();
                for (var mutator : optionSpecBuilderMutators) {
                    mutator.accept(copy, context);
                }

                if (createArray) {
                    return (OptionSpec<T>)copy.createArrayOptionSpec();
                } else {
                    return copy.createOptionSpec();
                }
            }

            @Override
            public Class<? extends U> contextType() {
                return contextType;
            }

            void addMutator(BiConsumer<OptionSpecBuilder<T>, U> mutator) {
                optionSpecBuilderMutators.add(mutator);
            }

            private final OptionSpecBuilder<T> optionSpecBuilder;
            private final Class<? extends U> contextType;
            private final List<BiConsumer<OptionSpecBuilder<T>, U>> optionSpecBuilderMutators = new ArrayList<>();
        }

    }
}
