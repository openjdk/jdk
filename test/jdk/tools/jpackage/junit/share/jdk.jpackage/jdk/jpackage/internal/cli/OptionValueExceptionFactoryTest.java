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

import static jdk.jpackage.internal.cli.TestUtils.assertExceptionEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import jdk.jpackage.internal.cli.OptionValueExceptionFactory.StandardArgumentsMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

public class OptionValueExceptionFactoryTest {

    @Test
    public void test_unreachable() {

        var factory = OptionValueExceptionFactory.unreachable();

        assertThrowsExactly(UnsupportedOperationException.class, () -> {
            factory.create(OptionName.of("foo"), StringToken.of("str"), "", Optional.empty());
        });
    }

    @ParameterizedTest
    @EnumSource(StandardArgumentsMapper.class)
    public void testStandardArgumentsMapper(StandardArgumentsMapper mapper) {

        final Supplier<List<String>> arguments = () -> {
            return List.of(mapper.apply(TEST_OPTION_NAME.name(), TEST_OPTION_VALUE));
        };

        switch (mapper) {
            case NAME_AND_VALUE -> {
                assertEquals(List.of("foo", "str"), arguments.get());
            }
            case VALUE_AND_NAME -> {
                assertEquals(List.of("str", "foo"), arguments.get());
            }
            case NONE -> {
                assertEquals(List.of(), arguments.get());
            }
            case VALUE -> {
                assertEquals(List.of("str"), arguments.get());
            }
            default -> {
                throw new AssertionError();
            }
        }
    }

    @Test
    public void test_Builder_defaults() {

        var factory = buildTestFactory().create();

        var ex = factory.create(TEST_OPTION_NAME, TEST_OPTION_VALUE, "error.parameter-not-file", Optional.empty());

        assertExceptionEquals(new Exception(I18N.format("error.parameter-not-file", "--foo", "str")), ex);
    }

    @Test
    public void test_Builder_formatArgumentsTransformer() {

        var factory = buildTestFactory().formatArgumentsTransformer(StandardArgumentsMapper.VALUE_AND_NAME).create();

        var ex = factory.create(TEST_OPTION_NAME, TEST_OPTION_VALUE, "error.parameter-not-file", Optional.empty());

        assertExceptionEquals(new Exception(I18N.format("error.parameter-not-file", "str", "--foo")), ex);
    }

    @Test
    public void test_Builder_messageFormatter() {

        var factory = buildTestFactory().messageFormatter(String::format).create();

        var ex = factory.create(TEST_OPTION_NAME, TEST_OPTION_VALUE, "The value of option %s is [%s]", Optional.empty());

        assertExceptionEquals(new Exception("The value of option --foo is [str]"), ex);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_Builder_printOptionPrefix(boolean printOptionPrefix) {

        var factory = buildTestFactory().printOptionPrefix(printOptionPrefix).create();

        var ex = factory.create(TEST_OPTION_NAME, TEST_OPTION_VALUE, "error.parameter-not-file", Optional.empty());

        String formattedOptionName;
        if (printOptionPrefix) {
            formattedOptionName = "--foo";
        } else {
            formattedOptionName = "foo";
        }
        assertExceptionEquals(new Exception(I18N.format("error.parameter-not-file", formattedOptionName, "str")), ex);
    }

    @Test
    public void testWithCause() {

        var factory = buildTestFactory().messageFormatter(String::format).create();

        var ex = factory.create(TEST_OPTION_NAME, TEST_OPTION_VALUE, "The value of option %s is [%s]", Optional.of(new IllegalArgumentException("Ops")));

        assertExceptionEquals(new Exception("The value of option --foo is [str]", new IllegalArgumentException("Ops")), ex);
    }

    @Test
    public void test_ArgumentsMapper_appendArguments() {

        var maper = OptionValueExceptionFactory.ArgumentsMapper.appendArguments((formattedOptionName, optionValue) -> {
            return new String[] { optionValue.value(), formattedOptionName, optionValue.tokenizedString() };
        }, 100, "foo");

        List<String> args = List.of(maper.apply("--bar", StringToken.of("strrr", "tr")));

        assertEquals(List.of("tr", "--bar", "strrr", "100", "foo"), args);
    }

    private static OptionValueExceptionFactory.Builder<Exception> buildTestFactory() {
        return OptionValueExceptionFactory.build(Exception::new);
    }

    private static final OptionName TEST_OPTION_NAME = OptionName.of("foo");
    private static final StringToken TEST_OPTION_VALUE = StringToken.of("str", "s");
}
