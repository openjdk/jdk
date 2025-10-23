/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.internal.cli.TestUtils.assertExceptionListEquals;
import static jdk.jpackage.internal.cli.TestUtils.configureCheckedValidator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import jdk.jpackage.internal.cli.TestUtils.TestException;
import jdk.jpackage.internal.cli.Validator.ParsedValue;
import jdk.jpackage.internal.cli.Validator.ValidatingConsumerException;
import jdk.jpackage.internal.cli.Validator.ValidatorException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

public class ValidatorTest {

    enum ValidatorType {
        PREDICATE,
        CONSUMER
    }


    @ParameterizedTest
    @EnumSource(ValidatorType.class)
    public void test(ValidatorType type) {

        final Validator.Builder<Object, Exception> builder = Validator.build();

        switch (type) {
            case CONSUMER -> builder.consumer(_ -> {});
            case PREDICATE -> builder.predicate(_ -> true);
            default -> {
                throw new UnsupportedOperationException();
            }
        }

        final var validator = builder.mutate(configureCheckedValidator()).create();

        final var optionName = OptionName.of("obj");
        final var optionValue = ParsedValue.create(new Object(), StringToken.of("foo"));
        assertEquals(List.of(), validator.validate(optionName, optionValue));
    }

    @Test
    public void test_predicate_negative() {

        final var validator =  Validator.build().predicate(_ -> false).mutate(configureCheckedValidator()).create();

        final var optionName = OptionName.of("obj");
        final var optionValue = ParsedValue.create(new Object(), StringToken.of("foo"));

        final var exceptions = validator.validate(optionName, optionValue);
        assertExceptionListEquals(List.of(new TestException("Option --obj: bad substring [foo] in string [foo]")), exceptions);
    }


    enum ValidatorConsumerType {
        CONSUMER_IllegalArgumentException,
        CONSUMER_ValidatingConsumerException
    }


    @ParameterizedTest
    @MethodSource
    public void test_consumer_negative(ValidatorConsumerType type, boolean withCause) {

        final Validator.Builder<Object, Exception> builder = Validator.build();

        final Exception cause;
        if (withCause) {
            cause = new FooException("foo cause");
        } else {
            cause = null;
        }

        final RuntimeException validatorException;
        final Exception expectedCause;

        switch (type) {
            case CONSUMER_IllegalArgumentException -> {
                validatorException = new IllegalArgumentException("Invalid value", cause);
                expectedCause = validatorException;
            }

            case CONSUMER_ValidatingConsumerException -> {
                if (cause != null) {
                    validatorException = new ValidatingConsumerException("Invalid value", cause);
                    expectedCause = cause;
                } else {
                    assertThrows(NullPointerException.class, () -> {
                        new ValidatingConsumerException("Invalid value", cause);
                    });
                    return;
                }
            }

            default -> {
                throw new UnsupportedOperationException();
            }
        }

        final var validator = builder.consumer(_ -> {
            throw validatorException;
        }).mutate(configureCheckedValidator()).create();

        final var optionName = OptionName.of("obj");
        final var optionValue = ParsedValue.create(new Object(), StringToken.of("foo"));

        final var exceptions = validator.validate(optionName, optionValue);
        assertExceptionListEquals(List.of(new TestException("Option --obj: bad substring [foo] in string [foo]", expectedCause)), exceptions);
    }

    @Test
    public void testBuilderWithoutExceptionOrFormatStringFactory() {

        Supplier<Validator.Builder<String, RuntimeException>> build = () -> {
            return Validator.<String, RuntimeException>build().predicate(_ -> true);
        };

        build.get().create();
        build.get().formatString(null).create();
        assertThrowsExactly(NullPointerException.class, build.get().formatString("foo")::create);
        assertThrowsExactly(NullPointerException.class, build.get().formatString("")::create);
        assertThrowsExactly(NullPointerException.class, build.get().formatString(null).exceptionFactory(OptionValueExceptionFactory.unreachable())::create);
    }

    @Test
    public void testBuilderCopy() {

        var builder = Validator.<String, RuntimeException>build()
                .predicate(_ -> true)
                .formatString("foo")
                .exceptionFactory(OptionValueExceptionFactory.unreachable());

        var copy = builder.copy();

        assertSame(builder.consumer().orElse(null), copy.consumer().orElse(null));
        assertSame(builder.predicate().orElse(null), copy.predicate().orElse(null));
        assertSame(builder.exceptionFactory().orElse(null), copy.exceptionFactory().orElse(null));
        assertSame(builder.formatString().orElse(null), copy.formatString().orElse(null));

        builder.consumer(_ -> {});

        copy = builder.copy();

        assertSame(builder.consumer().orElse(null), copy.consumer().orElse(null));
        assertSame(builder.predicate(), copy.predicate());
        assertSame(builder.exceptionFactory().orElse(null), copy.exceptionFactory().orElse(null));
        assertSame(builder.formatString().orElse(null), copy.formatString().orElse(null));

        copy.predicate(_ -> false);

        assertNotSame(builder.consumer(), copy.consumer());
        assertNotSame(builder.predicate(), copy.predicate());
    }

    @Test
    public void test_andThen() {

        Function<String, Validator<String, Exception>> createFailingValidator = exceptionMessage -> {
            Objects.requireNonNull(exceptionMessage);
            var exceptionFactory = OptionValueExceptionFactory.build().ctor(TestException::new).messageFormatter((_, _) -> {
                return exceptionMessage;
            }).create();

            return Validator.<String, Exception>build()
                    .predicate(_ -> false)
                    .formatString("")
                    .exceptionFactory(exceptionFactory).create();
        };

        Function<Validator<String, ? extends Exception>, List<? extends Exception>> validate = validator -> {
            return validator.validate(OptionName.of("a"), ParsedValue.create("str", StringToken.of("str")));
        };

        var pass = Validator.<String, RuntimeException>build().predicate(_ -> true).create();

        var foo = createFailingValidator.apply("foo");
        var bar = createFailingValidator.apply("bar");
        var buz = createFailingValidator.apply("buz");

        assertExceptionListEquals(List.of(
                new TestException("foo"),
                new TestException("bar"),
                new TestException("buz")
        ),  validate.apply(foo.andThen(bar).andThen(pass).andThen(buz)));

        assertExceptionListEquals(List.of(
                new TestException("bar"),
                new TestException("buz"),
                new TestException("foo")
        ),  validate.apply(pass.andThen(bar).andThen(buz).andThen(foo)));

        assertExceptionListEquals(List.of(
                new TestException("foo"),
                new TestException("foo")
        ),  validate.apply(foo.andThen(foo)));
    }

    @ParameterizedTest
    @EnumSource(ValidatorType.class)
    public void testValidatorException(ValidatorType type) {

        final Validator.Builder<String, Exception> builder = Validator.build();

        switch (type) {
            case CONSUMER -> builder.consumer(_ -> {
                throw VALITDATOR_EXCEPTION;
            });

            case PREDICATE -> builder.predicate(_ -> {
                throw VALITDATOR_EXCEPTION;
            });

            default -> {
                throw new UnsupportedOperationException();
            }
        }

        final var validator = builder.mutate(configureCheckedValidator()).create();

        final var token = StringToken.of("foo");
        final var ex = assertThrowsExactly(ValidatorException.class, () -> {
            validator.validate(OptionName.of("foo"), ParsedValue.create(token.value(), token));
        });

        assertSame(VALITDATOR_EXCEPTION, ex.getCause());
    }

    private static List<Object[]> test_consumer_negative() {
        final List<Object[]> data = new ArrayList<>();
        for (var type : ValidatorConsumerType.values()) {
            for (var withCause : List.of(true, false)) {
                data.add(new Object[] { type, withCause });
            }
        }
        return data;
    }


    static final class FooException extends Exception {

        FooException(String msg) {
            super(msg);
        }

        private static final long serialVersionUID = 1L;
    }


    static final RuntimeException VALITDATOR_EXCEPTION = new RuntimeException("Always fail");
}
