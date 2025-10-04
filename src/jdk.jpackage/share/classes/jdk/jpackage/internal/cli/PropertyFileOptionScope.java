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

import jdk.jpackage.internal.util.SetBuilder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Scope of options with values of type {@code T} that can be in a property file.
 *
 * @param <T> the type of option value
 */
@FunctionalInterface
interface PropertyFileOptionScope<T> extends OptionScope {

    OptionSpec<T> optionSpecForPropertyFile(Path propertyFile);

    @SuppressWarnings("unchecked")
    static <T> OptionSpec<T> mapOptionSpec(OptionSpec<T> optionSpec, Path propertyFile) {
        return optionSpec.scope().stream()
                .filter(PropertyFileOptionScope.class::isInstance)
                .findFirst().map(scope -> {
                    return ((PropertyFileOptionScope<T>)scope).optionSpecForPropertyFile(propertyFile);
                }).orElse(optionSpec);
    }

    static <T> Consumer<OptionSpecBuilder<T>> createScalarOptionSpecBuilderMutator(
            BiConsumer<OptionSpecBuilder<T>, Path> mutator) {
        Objects.requireNonNull(mutator);

        return builder -> {
            builder.scope(scope -> {
                return addScalarOptionSpecBuilderMutator(scope, mutator, () -> {
                    return builder;
                });
            });
        };
    }

    static <T> Consumer<OptionSpecBuilder<T>.ArrayOptionSpecBuilder> createArrayOptionSpecBuilderMutator(
            BiConsumer<OptionSpecBuilder<T>.ArrayOptionSpecBuilder, Path> mutator) {
        Objects.requireNonNull(mutator);

        return builder -> {
            builder.scope(scope -> {
                return addArrayOptionSpecBuilderMutator(scope, mutator, () -> {
                    return builder;
                });
            });
        };
    }

    private static <T> Set<OptionScope> addScalarOptionSpecBuilderMutator(
            Set<OptionScope> scope,
            BiConsumer<OptionSpecBuilder<T>, Path> optionSpecBuilderMutator,
            Supplier<OptionSpecBuilder<T>> optionSpecBuilderSupplier) {
        return Details.addOptionSpecBuilderMutator(scope, (builder, propertyFile) -> {
            builder.mutate(b -> {
                @SuppressWarnings("unchecked")
                var scalarBuilder = (OptionSpecBuilder<T>)b;
                optionSpecBuilderMutator.accept(scalarBuilder, propertyFile);
            });
        }, () -> {
            return new Details.ScalarOptionSpecBuilder<>(optionSpecBuilderSupplier.get());
        });
    }

    private static <T> Set<OptionScope> addArrayOptionSpecBuilderMutator(
            Set<OptionScope> scope,
            BiConsumer<OptionSpecBuilder<T>.ArrayOptionSpecBuilder, Path> optionSpecBuilderMutator,
            Supplier<OptionSpecBuilder<T>.ArrayOptionSpecBuilder> optionSpecBuilderSupplier) {
        return Details.addOptionSpecBuilderMutator(scope, (builder, propertyFile) -> {
            builder.mutate(b -> {
                @SuppressWarnings("unchecked")
                var arrayBuilder = (OptionSpecBuilder<T>.ArrayOptionSpecBuilder)b;
                optionSpecBuilderMutator.accept(arrayBuilder, propertyFile);
            });
        }, () -> {
            return new Details.ArrayOptionSpecBuilder<>(optionSpecBuilderSupplier.get());
        });
    }

    static final class Details {

        private Details() {
        }

        private static <T> Set<OptionScope> addOptionSpecBuilderMutator(
                Set<OptionScope> scope,
                BiConsumer<InternalOptionSpecBuilder<T>, Path> optionSpecBuilderMutator,
                Supplier<InternalOptionSpecBuilder<T>> optionSpecBuilderSupplier) {

            Objects.requireNonNull(scope);
            Objects.requireNonNull(optionSpecBuilderMutator);
            Objects.requireNonNull(optionSpecBuilderSupplier);

            var propertyFileOptionScope = scope.stream().filter(AccumulatingPropertyFileOptionScope.class::isInstance).findFirst();
            propertyFileOptionScope.ifPresent(v -> {
                @SuppressWarnings("unchecked")
                var mutators = ((AccumulatingPropertyFileOptionScope<T>)v);
                mutators.addMutator(optionSpecBuilderMutator);
            });

            if (propertyFileOptionScope.isEmpty()) {
                var mutators = new AccumulatingPropertyFileOptionScope<>(optionSpecBuilderSupplier.get());
                mutators.addMutator(optionSpecBuilderMutator);
                scope = SetBuilder.build(OptionScope.class).add(scope).add(mutators).create();
            }

            return scope;
        }

        private interface InternalOptionSpecBuilder<T> {
            OptionSpec<T> create();
            InternalOptionSpecBuilder<T> copy();
            void mutate(Consumer<Object> mutator);
        }

        private final record ScalarOptionSpecBuilder<T>(OptionSpecBuilder<T> builder) implements InternalOptionSpecBuilder<T> {

            @Override
            public OptionSpec<T> create() {
                return builder.createOptionSpec();
            }

            @Override
            public InternalOptionSpecBuilder<T> copy() {
                return new ScalarOptionSpecBuilder<>(builder.copy());
            }

            @Override
            public void mutate(Consumer<Object> mutator) {
                mutator.accept(builder);
            }
        }

        private final record ArrayOptionSpecBuilder<T>(OptionSpecBuilder<T>.ArrayOptionSpecBuilder builder) implements InternalOptionSpecBuilder<T[]> {

            @Override
            public OptionSpec<T[]> create() {
                return builder.createOptionSpec();
            }

            @Override
            public InternalOptionSpecBuilder<T[]> copy() {
                return new ArrayOptionSpecBuilder<>(builder.copy());
            }

            @Override
            public void mutate(Consumer<Object> mutator) {
                mutator.accept(builder);
            }
        }

        private final static class AccumulatingPropertyFileOptionScope<T> implements PropertyFileOptionScope<T> {

            AccumulatingPropertyFileOptionScope(InternalOptionSpecBuilder<T> optionSpecBuilder) {
                this.optionSpecBuilder = Objects.requireNonNull(optionSpecBuilder);
            }

            @Override
            public OptionSpec<T> optionSpecForPropertyFile(Path propertyFile) {
                var copy = optionSpecBuilder.copy();
                for (var mutator : optionSpecBuilderMutators) {
                    mutator.accept(copy, propertyFile);
                }
                return copy.create();
            }

            void addMutator(BiConsumer<InternalOptionSpecBuilder<T>, Path> mutator) {
                optionSpecBuilderMutators.add(mutator);
            }

            private final InternalOptionSpecBuilder<T> optionSpecBuilder;
            private final List<BiConsumer<InternalOptionSpecBuilder<T>, Path>> optionSpecBuilderMutators = new ArrayList<>();
        }

    }
}
