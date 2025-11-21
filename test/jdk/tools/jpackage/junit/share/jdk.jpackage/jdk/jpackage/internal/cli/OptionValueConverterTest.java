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
import static jdk.jpackage.internal.cli.TestUtils.configureConverter;
import static jdk.jpackage.internal.cli.TestUtils.configureValidator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import jdk.jpackage.internal.cli.OptionValueConverter.ConverterException;
import jdk.jpackage.internal.cli.TestUtils.TestException;
import jdk.jpackage.internal.util.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class OptionValueConverterTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test(boolean positive) {

        final var converter = OptionValueConverter.build().converter(ValueConverter.create(str -> {
            return Integer.valueOf(str);
        }, Integer.class)).mutate(configureConverter()).create();

        if (positive) {
            final var token = StringToken.of("758");
            assertEquals(758, converter.convert(OptionName.of("number"), token).orElseThrow());
        } else {
            final var token = StringToken.of("foo");
            final var result = converter.convert(OptionName.of("number"), token);

            assertEquals(1, result.errors().size());

            final var ex = result.firstError().orElseThrow();

            assertNotNull(ex.getCause());
            assertTrue(ex.getCause() instanceof NumberFormatException);
            assertEquals("Option --number: bad substring [foo] in string [foo]", ex.getMessage());
        }
    }

    @Test
    public void test_Builder_defaults() {

        var converter = OptionValueConverter.<Integer>build()
                .converter(ValueConverter.create(Integer::valueOf, Integer.class)).create();

        Function<String, Result<Integer>> convertString = (v) -> {
            return converter.convert(OptionName.of("foo"), StringToken.of(v));
        };

        assertEquals(10, convertString.apply("10").orElseThrow());

        assertThrowsExactly(UnsupportedOperationException.class, () -> convertString.apply(""));
    }

    @Test
    public void test_Builder_invalid() {

        assertThrowsExactly(NullPointerException.class, OptionValueConverter.build()
                .converter(ValueConverter.create(Integer::valueOf, Integer.class))
                .mutate(configureConverter())
                .formatString(null)::create);

        assertThrowsExactly(NullPointerException.class, OptionValueConverter.build()
                .converter(ValueConverter.create(Integer::valueOf, Integer.class))
                .mutate(configureConverter())
                .exceptionFactory(null)::create);
    }

    @Test
    public void test_Builder_copy() {

        Function<String, String[]> tokenizer = _ -> { throw new UnsupportedOperationException(); };

        var builder = OptionValueConverter.<Integer>build()
                .converter(ValueConverter.create(Integer::valueOf, Integer.class))
                .validator(Validator.<Integer, RuntimeException>build().predicate(_ -> true).create())
                .mutate(configureConverter())
                .tokenizer(tokenizer);

        var copy = builder.copy();

        assertNotSame(copy,  builder);

        assertSame(builder.converter().orElse(null), copy.converter().orElse(null));
        assertSame(builder.formatString().orElse(null), copy.formatString().orElse(null));
        assertSame(builder.exceptionFactory().orElse(null), copy.exceptionFactory().orElse(null));
        assertSame(builder.tokenizer().orElse(null), copy.tokenizer().orElse(null));
        assertSame(builder.validator().orElse(null), copy.validator().orElse(null));

        copy.formatString("foo");

        assertNotEquals(builder.formatString().orElse(null), copy.formatString().orElse(null));
    }

    @Test
    public void test_Builder_createArray() {

        var converter = OptionValueConverter.<Integer>build()
                .converter(ValueConverter.create(Integer::valueOf, Integer.class))
                .mutate(configureConverter())
                .tokenizer(str -> str.split(":"))
                .createArray();

        assertNotEquals(List.of(100, 67, 145), List.of(converter.convert(OptionName.of("foo"), StringToken.of("110:67:145")).orElseThrow()));

        assertEquals(Integer[].class, converter.valueType());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_Builder_createArray_exceptions(boolean validateInConveter) {

        var numberFormatException = assertThrowsExactly(NumberFormatException.class, () -> Integer.valueOf("str"));

        var builder = OptionValueConverter.<Integer>build()
                .mutate(configureConverter())
                .formatString("Ops")
                .tokenizer(str -> str.split(":"));

        if (validateInConveter) {
            builder.converter(ValueConverter.create(str -> {
                var i = Integer.valueOf(str);
                if (i < 0) {
                    throw new IllegalArgumentException(str);
                } else {
                    return i;
                }
            }, Integer.class));
        } else {
            builder.converter(ValueConverter.create(Integer::valueOf, Integer.class));
            builder.validator(Validator.<Integer, RuntimeException>build().consumer(i -> {
                if (i < 0) {
                    throw new IllegalArgumentException(i.toString());
                }
            }).mutate(configureValidator()).formatString("Ops").create());
        }

        var converter = builder.createArray();

        var result = converter.convert(OptionName.of("foo"), StringToken.of("100:-10:-10:67:str:145:-7"));

        assertExceptionListEquals(Stream.of(
                new IllegalArgumentException("-10"),
                new IllegalArgumentException("-10"),
                numberFormatException,
                new IllegalArgumentException("-7")
        ).map(ex -> {
            return new TestException("Ops", ex);
        }).toList(), result.errors());
    }

    @Test
    public void testConverterException() {

        final var exception = new RuntimeException("Always fail");

        final var converter = OptionValueConverter.build().converter(ValueConverter.create(str -> {
            throw exception;
        }, Integer.class)).mutate(configureConverter()).create();

        final var token = StringToken.of("foo");
        final var ex = assertThrowsExactly(ConverterException.class, () -> converter.convert(OptionName.of("number"), token));

        assertSame(exception, ex.getCause());
    }

    @Test
    public void testValidatorExceptionTunneling() {

        final var exception = new RuntimeException("Always fail");

        final var converter = OptionValueConverter.build().converter(ValueConverter.create(str -> {
            return Integer.valueOf(str);
        }, Object.class)).mutate(configureConverter()).validator(Validator.<Object, RuntimeException>build().predicate(_ -> {
            throw exception;
        }).mutate(configureValidator()).create()).create();

        final var token = StringToken.of("100");
        final var ex = assertThrowsExactly(ConverterException.class, () -> converter.convert(OptionName.of("number"), token));

        assertSame(exception, ex.getCause());
    }
}
