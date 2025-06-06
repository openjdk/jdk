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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Typed getter of option values in {@link Options} objects.
 *
 * @param <T> option value type.
 */
public interface OptionValue<T> {

    OptionIdentifier id();

    default Optional<Option> asOption() {
        if (id() instanceof Option option) {
            return Optional.of(option);
        } else {
            return Optional.empty();
        }
    }

    default Option getOption() {
        return asOption().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    default OptionSpec<T> getSpec() {
        return (OptionSpec<T>)getOption().getSpec();
    }

    @SuppressWarnings("unchecked")
    default Optional<T> findIn(Options cmdline) {
        return (Optional<T>)cmdline.find(id());
    }

    default T getFrom(Options cmdline) {
        return findIn(cmdline).orElseThrow();
    }

    default void ifPresentIn(Options cmdline, Consumer<T> consumer) {
        findIn(cmdline).ifPresent(consumer);
    }

    default boolean containsIn(Options cmdline) {
        return cmdline.find(id()).isPresent();
    }

    default OptionValue<T> copyWithoutDefaultValue() {
        return stripDefaultValue(this);
    }

    static <T> OptionValue<T> stripDefaultValue(OptionValue<T> optionValue) {
        Objects.requireNonNull(optionValue);

        var empty = Options.of(Map.of());

        if (optionValue.findIn(empty).isEmpty()) {
            // No default value
            return optionValue;
        } else {
            return new OptionValue<>() {
                @Override
                public OptionIdentifier id() {
                    return optionValue.id();
                }

                @Override
                public Optional<T> findIn(Options cmdline) {
                    if (cmdline.contains(id())) {
                        return optionValue.findIn(cmdline);
                    } else {
                        return Optional.empty();
                    }
                }
            };
        }
    }

    static <U> OptionValue<U> create() {
        return createWithoutOption(OptionIdentifier.createUnique());
    }

    static <T> OptionValue<T> createWithoutOption(OptionIdentifier id) {
        Objects.requireNonNull(id);
        return new OptionValue<>() {
            @Override
            public OptionIdentifier id() {
                return id;
            }

            @Override
            public Optional<Option> asOption() {
                return Optional.empty();
            }
        };
    }

    static <U> Builder<U> build() {
        return new Builder<>();
    }

    static final class Builder<T> {
        OptionValue<T> create() {
            if (conv != null) {
                return conv.create(Optional.ofNullable(defaultValue));
            } else if (spec != null) {
                return create(spec, Optional.ofNullable(defaultValue));
            } else if (defaultValue != null) {
                return create(OptionValue.<T>create(), x-> x, Optional.ofNullable(defaultValue));
            } else {
                return OptionValue.create();
            }
        }

        Builder<T> defaultValue(T v) {
            defaultValue = v;
            return this;
        }

        Builder<T> spec(OptionSpec<?> v) {
            spec = v;
            conv = null;
            return this;
        }

        <U> Builder<T> from(OptionValue<U> base, Function<U, T> conv) {
            spec = null;
            this.conv = new Conv<>(base, conv);
            return this;
        }

        <U> Builder<U> to(Function<T, U> conv) {
            return OptionValue.<U>build().from(create(), conv);
        }

        private static <U, V> OptionValue<V> create(OptionValue<U> base, Function<U, V> conv, Optional<V> defaultValue) {
            Objects.requireNonNull(base);
            Objects.requireNonNull(conv);
            Objects.requireNonNull(defaultValue);
            return new OptionValue<>() {
                @Override
                public OptionIdentifier id() {
                    return base.id();
                }

                @Override
                public Optional<V> findIn(Options cmdline) {
                    return base.findIn(cmdline).map(conv).or(() -> defaultValue);
                }
            };
        }

        private static <V> OptionValue<V> create(OptionSpec<?> spec, Optional<V> defaultValue) {
            Objects.requireNonNull(spec);
            Objects.requireNonNull(defaultValue);
            return new OptionValue<>() {
                @Override
                public OptionIdentifier id() {
                    return id;
                }

                @Override
                public Optional<V> findIn(Options cmdline) {
                    return OptionValue.super.findIn(cmdline).or(() -> defaultValue);
                }

                private final OptionIdentifier id = Option.create(spec);
            };
        }

        private record Conv<U, V>(OptionValue<U> base, Function<U, V> conv) {
            Conv {
                Objects.requireNonNull(base);
                Objects.requireNonNull(conv);
            }

            OptionValue<V> create(Optional<V> defaultValue) {
                return Builder.create(base, conv, defaultValue);
            }
        }

        private Conv<?, T> conv;
        private T defaultValue;
        private OptionSpec<?> spec;
    }
}
