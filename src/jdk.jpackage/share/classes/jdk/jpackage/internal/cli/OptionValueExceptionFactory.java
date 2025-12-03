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

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * Factory producing exception objects for option value processing failures.
 * <p>
 * Errors in converting option string values into objects or validating objects
 * created from option string values are typical option value processing
 * failures.
 *
 * @param T type of produced exceptions
 */
@FunctionalInterface
interface OptionValueExceptionFactory<T extends Exception> {

    /**
     * Create an exception object for the specified option name, value and optional
     * cause.
     *
     * @param optionName   the name of the option
     * @param optionValue  the value of the option
     * @param formatString the format string for formatting the exception message
     * @param cause        the cause if any, an empty {@ @link Optional} instance
     *                     otherwise
     * @return exception object
     */
    T create(OptionName optionName, StringToken optionValue, String formatString, Optional<Exception> cause);

    static <T extends Exception> Builder<T> build() {
        return new Builder<>();
    }

    static <T extends Exception> Builder<T> build(BiFunction<String, Throwable, T> ctor) {
        final Builder<T> builder = build();
        return builder.ctor(ctor);
    }

    static <T extends Exception> OptionValueExceptionFactory<T> unreachable() {
        return (_, _, _, _) -> {
            throw new UnsupportedOperationException();
        };
    }


    @FunctionalInterface
    interface ArgumentsMapper {
        String[] apply(String formattedOptionName, StringToken optionValue);

        static ArgumentsMapper appendArguments(ArgumentsMapper target, Object... args) {
            return (optionName, optionValue) -> {
                var arr = target.apply(optionName, optionValue);
                return Stream.concat(Stream.of(arr), Stream.of(args).map(Object::toString)).toArray(String[]::new);
            };
        }
    }


    enum StandardArgumentsMapper implements ArgumentsMapper {
        NAME_AND_VALUE((formattedOptionName, optionValue) -> {
            return new String[] { formattedOptionName, optionValue.tokenizedString() };
        }),
        VALUE_AND_NAME((formattedOptionName, optionValue) -> {
            return new String[] { optionValue.tokenizedString(), formattedOptionName };
        }),
        VALUE((formattedOptionName, optionValue) -> {
            return new String[] { optionValue.tokenizedString() };
        }),
        NONE((formattedOptionName, optionValue) -> {
            return new String[] {};
        });

        StandardArgumentsMapper(ArgumentsMapper impl) {
            this.impl = Objects.requireNonNull(impl);
        }

        @Override
        public String[] apply(String formattedOptionName, StringToken optionValue) {
            return impl.apply(formattedOptionName, optionValue);
        }

        private final ArgumentsMapper impl;
    }


    static final class Builder<T extends Exception> {

        OptionValueExceptionFactory<T> create() {
            return OptionValueExceptionFactory.create(ctor,
                    Optional.ofNullable(formatArgumentsTransformer).orElse(StandardArgumentsMapper.NAME_AND_VALUE),
                    Optional.ofNullable(messageFormatter).orElse(I18N::format),
                    printOptionPrefix);
        }

        Builder<T> messageFormatter(BiFunction<String, Object[], String> v) {
            messageFormatter = v;
            return this;
        }

        Builder<T> ctor(BiFunction<String, Throwable, T> v) {
            ctor = v;
            return this;
        }

        Builder<T> formatArgumentsTransformer(ArgumentsMapper v) {
            formatArgumentsTransformer = v;
            return this;
        }

        Builder<T> printOptionPrefix(boolean v) {
            printOptionPrefix = v;
            return this;
        }

        private BiFunction<String, Throwable, T> ctor;
        private ArgumentsMapper formatArgumentsTransformer;
        private BiFunction<String, Object[], String> messageFormatter;
        private boolean printOptionPrefix = true;
    }


    private static <T extends Exception> OptionValueExceptionFactory<T> create(
            BiFunction<String, Throwable, T> ctor,
            ArgumentsMapper formatArgumentsTransformer,
            BiFunction<String, Object[], String> messageFormatter,
            boolean printOptionPrefix) {
        Objects.requireNonNull(ctor);
        Objects.requireNonNull(formatArgumentsTransformer);
        Objects.requireNonNull(messageFormatter);

        return new OptionValueExceptionFactory<>() {

            @Override
            public T create(OptionName optionName, StringToken optionValue, String formatString, Optional<Exception> cause) {
                return Objects.requireNonNull(ctor.apply(createMessage(optionName, optionValue, formatString), cause.orElse(null)));
            }

            private String createMessage(OptionName optionName, StringToken optionValue, String formatString) {
                Objects.requireNonNull(optionName);
                Objects.requireNonNull(optionValue);
                Objects.requireNonNull(formatString);

                final String formattedOptionName;
                if (printOptionPrefix) {
                    formattedOptionName = optionName.formatForCommandLine();
                } else {
                    formattedOptionName = optionName.name();
                }

                final var args = Stream.of(formatArgumentsTransformer.apply(formattedOptionName, optionValue)).toArray();
                return Objects.requireNonNull(messageFormatter.apply(formatString, args));
            }
        };
    }
}
