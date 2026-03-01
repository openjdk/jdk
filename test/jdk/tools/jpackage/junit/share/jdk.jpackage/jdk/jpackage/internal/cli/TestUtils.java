/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.StreamSupport;
import jdk.jpackage.internal.cli.Validator.ParsedValue;
import jdk.jpackage.internal.cli.Validator.ValidatorException;
import jdk.jpackage.test.JUnitUtils;

final class TestUtils {

    private TestUtils() {
    }

    static UnaryOperator<OptionValueExceptionFactory<? extends RuntimeException>> recordExceptions(Collection<OptionFailure> sink) {
        return exceptionFactory -> {
            return new RecordingExceptionFactory(exceptionFactory, sink::add);
        };
    }

    static <T> Consumer<Validator.Builder<T, RuntimeException>> configureValidator() {
        return builder -> {
            builder.exceptionFactory(DEFAULT_EXCEPTION_FACTORY).formatString(DEFAULT_EXCEPTION_MESSAGE_FORMAT_STRING);
        };
    }

    static <T> Consumer<Validator.Builder<T, Exception>> configureCheckedValidator() {
        return builder -> {
            builder.exceptionFactory(DEFAULT_EXCEPTION_FACTORY).formatString(DEFAULT_EXCEPTION_MESSAGE_FORMAT_STRING);
        };
    }

    static <T> Consumer<OptionValueConverter.Builder<T>> configureConverter() {
        return builder -> {
            builder.exceptionFactory(DEFAULT_EXCEPTION_FACTORY).formatString(DEFAULT_EXCEPTION_MESSAGE_FORMAT_STRING);
        };
    }

    static void assertExceptionEquals(Exception expected, Exception actual) {
        assertEquals(JUnitUtils.exceptionAsPropertyMap(expected), JUnitUtils.exceptionAsPropertyMap(actual));
    }

    static void assertExceptionListEquals(Collection<? extends Exception> expected, Collection<? extends Exception> actual) {
        assertEquals(
                expected.stream().map(JUnitUtils::exceptionAsPropertyMap).toList(),
                actual.stream().map(JUnitUtils::exceptionAsPropertyMap).toList()
        );
    }


    static Function<String, String[]> splitOrEmpty(String splitRegexp) {
        Objects.requireNonNull(splitRegexp);
        return str -> {
            if (str.isEmpty()) {
                return new String[0];
            } else {
                return str.split(splitRegexp);
            }
        };
    }

    static <T> List<T[]> arrayElements(Class<? extends T> elementType, Iterable<T> elements) {
        Objects.requireNonNull(elementType);
        return StreamSupport.stream(elements.spliterator(), false).map(e -> {
            @SuppressWarnings("unchecked")
            final var arr = (T[])Array.newInstance(elementType, e == null ? 0 : 1);
            if (e != null) {
                Array.set(arr, 0, e);
            }
            return arr;
        }).toList();
    }


    record OptionFailure(OptionName optionName, StringToken optionValue, Optional<Exception> exception) {
        OptionFailure {
            Objects.requireNonNull(optionName);
            Objects.requireNonNull(optionValue);
            Objects.requireNonNull(exception);
        }

        OptionFailure(OptionName optionName, StringToken optionValue) {
            this(optionName, optionValue, Optional.empty());
        }

        OptionFailure(String optionName, StringToken optionValue) {
            this(OptionName. of(optionName), optionValue);
        }

        OptionFailure(OptionName optionName, String optionValue) {
            this(optionName, StringToken.of(optionValue));
        }

        OptionFailure(String optionName, String optionValue) {
            this(OptionName. of(optionName), optionValue);
        }

        OptionFailure withoutException() {
            return exception.map(_ -> new OptionFailure(optionName, optionValue, Optional.empty())).orElse(this);
        }

        static Comparator<OptionFailure> compareNameAndValue() {
            return Comparator.comparing(OptionFailure::optionName).thenComparing(v -> {
                return v.optionValue().value();
            }).thenComparing(v -> {
                return v.optionValue().tokenizedString();
            });
        }
    }


    static final class TestException extends RuntimeException {

        TestException(String msg) {
            super(msg);
        }

        TestException(String msg, Throwable cause) {
            super(msg, cause);
        }

        private static final long serialVersionUID = 1L;
    }


    static final class RecordingValidator<T, U extends Exception> implements Validator<T, U> {

        RecordingValidator(Validator<T, U> validator) {
            this.validator = Objects.requireNonNull(validator);
        }

        @Override
        public List<U> validate(OptionName optionName, ParsedValue<T> optionValue) {
            counter++;
            return validator.validate(optionName, optionValue);
        }

        int counter() {
            return counter;
        }

        void resetCounter() {
            counter = 0;
        }

        private final Validator<T, U> validator;
        private int counter;
    }


    private record RecordingExceptionFactory(OptionValueExceptionFactory<? extends RuntimeException> factory,
            Consumer<OptionFailure> sink) implements OptionValueExceptionFactory<RuntimeException> {

        RecordingExceptionFactory {
            Objects.requireNonNull(factory);
            Objects.requireNonNull(sink);
        }

        @Override
        public RuntimeException create(OptionName optionName, StringToken optionValue, String formatString, Optional<Exception> cause) {
            final var ex = factory.create(optionName, optionValue, formatString, cause);
            sink.accept(new OptionFailure(optionName, optionValue, Optional.of(ex)));
            return ex;
        }
    }

    private static final String DEFAULT_EXCEPTION_MESSAGE_FORMAT_STRING = "Option %s: bad substring [%s] in string [%s]";

    private static final OptionValueExceptionFactory<? extends RuntimeException> DEFAULT_EXCEPTION_FACTORY = OptionValueExceptionFactory.build(TestException::new)
            .formatArgumentsTransformer((String formattedOptionName, StringToken optionValue) -> {
                return new String[] { formattedOptionName, optionValue.value(), optionValue.tokenizedString() };
            })
            .messageFormatter(String::format)
            .create();
}
