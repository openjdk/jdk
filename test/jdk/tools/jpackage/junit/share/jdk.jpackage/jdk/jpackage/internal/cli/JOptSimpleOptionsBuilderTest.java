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

import static jdk.jpackage.internal.cli.ExpectedOptions.expectOptions;
import static jdk.jpackage.internal.cli.OptionSpecBuilder.pathSeparator;
import static jdk.jpackage.internal.cli.OptionSpecBuilder.toList;
import static jdk.jpackage.internal.cli.StandardValueConverter.identityConv;
import static jdk.jpackage.internal.cli.StandardValueConverter.pathConv;
import static jdk.jpackage.internal.cli.TestUtils.arrayElements;
import static jdk.jpackage.test.JUnitUtils.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jdk.jpackage.internal.cli.JOptSimpleOptionsBuilder.JOptSimpleErrorType;
import jdk.jpackage.internal.cli.OptionSpec.MergePolicy;
import jdk.jpackage.internal.cli.OptionValueConverter.ConverterException;
import jdk.jpackage.internal.cli.OptionValueExceptionFactory.StandardArgumentsMapper;
import jdk.jpackage.internal.cli.TestUtils.OptionFailure;
import jdk.jpackage.internal.cli.TestUtils.TestException;
import jdk.jpackage.internal.model.BundlingOperationDescriptor;
import jdk.jpackage.internal.util.Result;
import jdk.jpackage.test.JUnitUtils.ExceptionPattern;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class JOptSimpleOptionsBuilderTest {

    enum ParserMode {
        PARSE,
        CONVERT
    }

    public record TestSpec(Map<OptionValue<?>, ExpectedValue<?>> options, List<String> args) {
        public TestSpec {
            Objects.requireNonNull(options);
            Objects.requireNonNull(args);
        }

        void test(ParserMode parserMode) {
            final var parser = createParser(parserMode, options.keySet());

            final var cmdline = parser.apply(args);

            for (final var e : options.entrySet()) {
                final var optionValue = e.getKey();
                final var expectedValue = e.getValue();
                final Object actualValue;
                if (parserMode.equals(ParserMode.PARSE)) {
                    actualValue = cmdline.find(optionValue.getOption()).orElseThrow();
                } else {
                    actualValue = optionValue.getFrom(cmdline);
                }
                expectedValue.assertIt(actualValue);
            }
        }


        static final class Builder {

            TestSpec create() {
                return new TestSpec(options, args);
            }

            final class OptionValueBuilder<T> {

                OptionValueBuilder<T> expectParse(String... expectedValue) {
                    expectedValues.put(ParserMode.PARSE, new ExpectedValue<>(expectedValue, Assertions::assertArrayEquals));
                    return this;
                 }

                OptionValueBuilder<T> expect(T expectedValue) {
                   return expect(expectedValue, defaultAsserter());
                }

                OptionValueBuilder<T> expect(T expectedValue, BiConsumer<T, T> asserter) {
                    expectedValues.put(ParserMode.CONVERT, new ExpectedValue<>(expectedValue, asserter));
                    return this;
                }

                Builder commit() {
                    if (expectedValues.isEmpty()) {
                        throw new UnsupportedOperationException("Missing expected value");
                    }

                    if (parserMode == null) {
                        if (expectedValues.size() > 1) {
                            throw new UnsupportedOperationException("Ambigous expected value");
                        }
                        options.put(option, expectedValues.values().iterator().next());
                    } else {
                        options.put(option, Optional.ofNullable(expectedValues.get(parserMode)).orElseThrow(() -> {
                            return new UnsupportedOperationException("Mismatched expected value");
                        }));
                    }

                    return Builder.this;
                }

                private OptionValueBuilder(OptionValue<T> option) {
                    this.option = Objects.requireNonNull(option);
                }

                private final OptionValue<T> option;
                private final Map<ParserMode, ExpectedValue<?>> expectedValues = new HashMap<>();
            }

            <T> OptionValueBuilder<T> optionValue(OptionValue<T> option) {
                return new OptionValueBuilder<>(option);
            }

            Builder optionUntypedValue(OptionValue<?> option, String... expectedValue) {
                return new OptionValueBuilder<>(option).expectParse(expectedValue).commit();
             }

            <T> Builder optionValue(OptionValue<T> option, T expectedValue) {
               return optionValue(option, expectedValue, defaultAsserter());
            }

            <T> Builder optionValue(OptionValue<T> option, T expectedValue, BiConsumer<T, T> asserter) {
                return new OptionValueBuilder<>(option).expect(expectedValue, asserter).commit();
            }

            Builder args(String...v) {
                return args(List.of(v));
            }

            Builder args(Collection<String> v) {
                args.addAll(v);
                return this;
            }

            Builder mode(ParserMode v) {
                parserMode = v;
                return this;
            }

            private static <T> BiConsumer<T, T> defaultAsserter() {
                return (expected, actual) -> {
                    if (expected.getClass().isArray()) {
                        assertArrayEquals(expected, actual);
                    } else {
                        assertEquals(expected, actual);
                    }
                };
            }

            private ParserMode parserMode;
            private final Map<OptionValue<?>, ExpectedValue<?>> options = new HashMap<>();
            private final List<String> args = new ArrayList<>();
        }


        private record ExpectedValue<T>(T value, BiConsumer<T, T> asserter) {
            ExpectedValue {
                Objects.requireNonNull(value);
                Objects.requireNonNull(asserter);
            }

            @SuppressWarnings("unchecked")
            void assertIt(Object actual) {
                asserter.accept(value, (T)actual);
            }
        }
    }

    enum ShortNameTestCase {
        LONG(hasLongOption().and(hasShortOption().negate()), addLongOption()),
        SHORT(hasLongOption().negate().and(hasShortOption()), addShortOption()),
        LONG_AND_SHORT(hasLongOption().and(hasShortOption()), addLongOption(), addShortOption()),
        SHORT_AND_LONG(hasLongOption().and(hasShortOption()), addShortOption(), addLongOption()),
        NONE(hasLongOption().negate().and(hasShortOption().negate()))
        ;

        @SafeVarargs
        ShortNameTestCase(Predicate<Options> validator, BiConsumer<Type, List<String>>... optionInitializers) {
            this.optionInitializer = (type, args) -> {
                for (final var optionInitializer : optionInitializers) {
                    optionInitializer.accept(type, args);
                }
            };
            this.validator = validator;
        }

        void run(Type type, ParserMode parserMode) {
            final var parser = createParser(parserMode, type.optionValue);
            final List<String> args = new ArrayList<>();
            optionInitializer.accept(type, args);
            assertTrue(validator.test(parser.apply(args)));
        }

        private static Predicate<Options> hasLongOption() {
            return cmdline -> {
                return cmdline.contains(LONG_NAME);
            };
        }

        private static Predicate<Options> hasShortOption() {
            return cmdline -> {
                return cmdline.contains(SHORT_NAME);
            };
        }

        private static BiConsumer<Type, List<String>> addLongOption() {
            return (type, args) -> {
                args.add(LONG_NAME.formatForCommandLine());
                type.valueInitializer.accept(FOO, args);
            };
        }

        private static BiConsumer<Type, List<String>> addShortOption() {
            return (type, args) -> {
                args.add(SHORT_NAME.formatForCommandLine());
                type.valueInitializer.accept(BAR, args);
            };
        }

        private enum Type {
            STRING(stringOption(LONG_NAME.name()).addAliases(SHORT_NAME.name()).create(), (optionValue, args) -> {
                args.add(optionValue);
            }),
            BOOLEAN(booleanOption(LONG_NAME.name()).addAliases(SHORT_NAME.name()).create(), (optionValue, args) -> {}),
            ;

            Type(OptionValue<?> optionValue, BiConsumer<String, List<String>> valueInitializer) {
                this.optionValue = Objects.requireNonNull(optionValue);
                this.valueInitializer = Objects.requireNonNull(valueInitializer);
            }

            final OptionValue<?> optionValue;
            final BiConsumer<String, List<String>> valueInitializer;
        }

        private final BiConsumer<Type, List<String>> optionInitializer;
        private final Predicate<Options> validator;

        private static final OptionName LONG_NAME = OptionName.of("input");
        private static final OptionName SHORT_NAME = OptionName.of("i");

        private static final String FOO = "foo";
        private static final String BAR = "bar";
    }

    @ParameterizedTest
    @EnumSource(ShortNameTestCase.class)
    public void testShortNameString(ShortNameTestCase testCase) {
        for (final var parserMode : ParserMode.values()) {
            testCase.run(ShortNameTestCase.Type.STRING, parserMode);
        }
    }

    @ParameterizedTest
    @EnumSource(ShortNameTestCase.class)
    public void testShortNameBoolean(ShortNameTestCase testCase) {
        for (final var parserMode : List.of(ParserMode.CONVERT)) {
            testCase.run(ShortNameTestCase.Type.BOOLEAN, parserMode);
        }
    }

    @ParameterizedTest
    @MethodSource
    public void test(TestSpec spec) {
        spec.test(ParserMode.CONVERT);
    }

    @ParameterizedTest
    @MethodSource
    public void testStringVector(TestSpec spec) {
        spec.test(ParserMode.CONVERT);
    }

    @ParameterizedTest
    @MethodSource
    public void testOptionalValueParse(TestSpec spec) {
        spec.test(ParserMode.PARSE);
    }

    @ParameterizedTest
    @MethodSource
    public void testOptionalValueConvert(TestSpec spec) {
        spec.test(ParserMode.CONVERT);
    }

    @ParameterizedTest
    @EnumSource(ParserMode.class)
    public void testOptionalValueNoDefault(ParserMode parserMode) {
        var option = option("x", Integer.class).converter(Integer::valueOf).defaultOptionalValue(17).create();
        var option2 = option("y", Integer.class).converter(Integer::valueOf).create();

        var cmdline = createParser(parserMode, option, option2).apply(List.of("-y", "77"));

        assertTrue(option.findIn(cmdline).isEmpty());
        assertTrue(option2.findIn(cmdline).isPresent());
    }

    @Test
    public void testConversionErrors(@TempDir Path tmpDir) {

        final var dirOption = pathOption("dir").addAliases("r").tokenizer(",");

        final var urlOption = stringOption("url").converter(str -> {
            try {
                new URI(str);
                return str;
            } catch (URISyntaxException ex) {
                throw new IllegalArgumentException(ex);
            }
        });

        final var lruOption = option("lru", URI.class).converter(str -> {
            try {
                return new URI(str);
            } catch (URISyntaxException ex) {
                throw new IllegalArgumentException(ex);
            }
        }).mergePolicy(MergePolicy.USE_FIRST);

        new FaultyParserArgsConfig()
                .options(urlOption, lruOption)
                .arrayOptions(dirOption)
                .args("--dir=*,foo,,bar", "-r", "file", "-r", "file,*")
                .args("--url=http://foo", "--url=:foo")
                .args("--lru=:bar", "--lru=http://bar")
                .expectError("dir", StringToken.of("*,foo,,bar", "*"))
                .expectError("r", StringToken.of("file,*", "*"))
                .expectError("url", ":foo")
                .expectError("lru", ":bar")
                .test();

        // Test errors are recorded in the order they appear on the command line.
        new FaultyParserArgsConfig()
                .options(urlOption, lruOption)
                .arrayOptions(dirOption)
                .args("--lru=:bar", "--lru=http://bar")
                .args("--dir=*,foo,,bar", "-r", "file")
                .args("--url=http://foo", "--url=:foo")
                .args("-r", "file,*")
                .expectError("lru", ":bar")
                .expectError("dir", StringToken.of("*,foo,,bar", "*"))
                .expectError("url", ":foo")
                .expectError("r", StringToken.of("file,*", "*"))
                .test();
    }

    @Test
    public void testValidationErrors() {

        final var numberArrayOption = option("number", Integer.class)
                .addAliases("n")
                .validator((Predicate<Integer>)(v -> v > 0))
                .tokenizer(",")
                .converter(Integer::valueOf);

        new FaultyParserArgsConfig()
                .arrayOptions(numberArrayOption)
                .args("--number=56,23", "--number=2,-34,-45", "-n", "2,-17,0,56")
                .expectError("number", StringToken.of("2,-34,-45", "-34"))
                .expectError("number", StringToken.of("2,-34,-45", "-45"))
                .expectError("n", StringToken.of("2,-17,0,56", "-17"))
                .expectError("n", StringToken.of("2,-17,0,56", "0"))
                .test();

        // Test errors are recorded in the order they appear on the command line.
        new FaultyParserArgsConfig()
                .arrayOptions(numberArrayOption)
                .args("--number=56,23", "-n", "2,-17,0,56", "--number=2,-45,-34")
                .expectError("n", StringToken.of("2,-17,0,56", "-17"))
                .expectError("n", StringToken.of("2,-17,0,56", "0"))
                .expectError("number", StringToken.of("2,-45,-34", "-45"))
                .expectError("number", StringToken.of("2,-45,-34", "-34"))
                .test();
    }

    enum ConverterErrorOrigin {
        CONVERTER,
        VALIDATOR
    }

    @ParameterizedTest
    @EnumSource(ConverterErrorOrigin.class)
    public void testConverterError(ConverterErrorOrigin exceptionOrigin) {

        final var scalarException = new RuntimeException("Scalar error");
        final var arrayException = new RuntimeException("Array error");

        final Function<RuntimeException, Consumer<OptionSpecBuilder<String>>> mutatorCreator = ex -> {
            return builder -> {
                switch (exceptionOrigin) {
                    case CONVERTER -> {
                        builder.converter(_ -> {
                            throw ex;
                        });
                    }

                    case VALIDATOR -> {
                        builder.validator(new Consumer<>() {

                            @Override
                            public void accept(String t) {
                                throw ex;
                            }

                        });
                    }
                }
            };
        };

        final var scalarOption = stringOption("val").mutate(mutatorCreator.apply(scalarException));

        final var arrayOption = stringOption("arr").mutate(mutatorCreator.apply(arrayException));

        final var cfg = new FaultyParserArgsConfig()
                .options(scalarOption, stringOption("good"))
                .arrayOptions(arrayOption);

        cfg.clearArgs()
                .args("--val=10")
                .expectConverterException(scalarException)
                .test();

        cfg.clearArgs()
                .args("--arr=foo")
                .expectConverterException(arrayException)
                .test();

        cfg.clearArgs()
                .args("--arr=bar", "--val=57")
                .expectConverterException(arrayException)
                .test();

        cfg.clearArgs()
                .args("--val=57", "--arr=bar")
                .expectConverterException(scalarException)
                .test();
    }

    @Test
    public void testArrayUnrecoverableMergeFailure() {

        new FaultyParserArgsConfig()
                .arrayOptions(pathOption("path").tokenizer(",").mergePolicy(MergePolicy.USE_FIRST))
                .args("--path=*,foo", "--path=bar")
                .expectError("path", StringToken.of("*,foo", "*"))
                .test();

        new FaultyParserArgsConfig()
                .arrayOptions(pathOption("path").tokenizer(",").mergePolicy(MergePolicy.USE_LAST))
                .args("--path=bar", "--path=foo,*")
                .expectError("path", StringToken.of("foo,*", "*"))
                .test();
    }

    @Test
    public void testScalarUnrecoverableMergeFailure() {

        new FaultyParserArgsConfig()
                .options(pathOption("path").mergePolicy(MergePolicy.USE_FIRST))
                .args("--path=*", "--path=bar")
                .expectError("path", StringToken.of("*", "*"))
                .test();

        new FaultyParserArgsConfig()
                .options(pathOption("path").mergePolicy(MergePolicy.USE_LAST))
                .args("--path=bar", "--path=*")
                .expectError("path", StringToken.of("*", "*"))
                .test();
    }

    @ParameterizedTest
    @MethodSource("testMergePolicy")
    public void testMergePolicyScalar(MergePolicy mergePolicy, ParserMode parserMode) {

        if (mergePolicy == MergePolicy.CONCATENATE) {
            assertThrowsExactly(IllegalArgumentException.class,
                    option("foo", Object.class).mergePolicy(mergePolicy)::create);
            return;
        }

        final var strOption = stringOption("str").mergePolicy(mergePolicy).create();

        final var monthOption = stringOption("month").addAliases("m")
                .mergePolicy(mergePolicy).create();

        final var intOption = option("int", Integer.class).addAliases("i")
                .converter(Integer::valueOf).mergePolicy(mergePolicy).create();

        final var floatOption = option("d", Double.class)
                .converter(Double::valueOf).mergePolicy(mergePolicy).create();

        final var builder = build()
                .args("-d", "1000", "--month=June", "--int=10", "-m", "July", "-i", "45", "-m", "August")
                .args("--str=", "--str=A");

        if (parserMode.equals(ParserMode.PARSE)) {
            builder.optionUntypedValue(strOption, mergeUntypedValues(List.of("", "A"), mergePolicy));
            builder.optionUntypedValue(monthOption, mergeUntypedValues(List.of("June", "July", "August"), mergePolicy));
            builder.optionUntypedValue(intOption, mergeUntypedValues(List.of("10", "45"), mergePolicy));
            builder.optionUntypedValue(floatOption, new String[] {"1000"});
        } else {
            builder.optionValue(strOption, mergeScalarValues(List.of("", "A"), mergePolicy));
            builder.optionValue(monthOption, mergeScalarValues(List.of("June", "July", "August"), mergePolicy));
            builder.optionValue(intOption, mergeScalarValues(List.of(10, 45), mergePolicy));
            builder.optionValue(floatOption, Double.valueOf(1000));
        }

        builder.create().test(parserMode);
    }

    @ParameterizedTest
    @MethodSource("testMergePolicy")
    public void testMergePolicyArray(MergePolicy mergePolicy, ParserMode parserMode) {

        final var monthOption = stringOption("month").addAliases("m")
                .mergePolicy(mergePolicy)
                .tokenizer(Pattern.quote("+"))
                .createArray();

        final var intOption = option("int", Integer.class).addAliases("i")
                .converter(Integer::valueOf)
                .mergePolicy(mergePolicy)
                .tokenizer(TestUtils.splitOrEmpty(":"))
                .createArray();

        final var floatOption = option("d", Double.class)
                .converter(Double::valueOf)
                .mergePolicy(mergePolicy)
                .tokenizer(Pattern.quote("|"))
                .createArray();

        final var builder = build().args("-d", "1000", "--int=", "--month=June", "--int=10:333", "-m", "July+July", "-i", "45", "-m", "August", "--int=");

        if (parserMode.equals(ParserMode.PARSE)) {
            builder.optionUntypedValue(monthOption,
                    mergeUntypedValues(List.of("June", "July+July", "August"), mergePolicy));
            builder.optionUntypedValue(intOption,
                    mergeUntypedValues(List.of("", "10:333", "45", ""), mergePolicy));
            builder.optionUntypedValue(floatOption, new String[] {"1000"});
        } else {
            builder.optionValue(monthOption,
                    mergeArrayValues(arrayElements(String.class, List.of("June", "July", "July", "August")), mergePolicy));
            builder.optionValue(intOption,
                    mergeArrayValues(arrayElements(Integer.class, Arrays.asList(null, 10, 333, 45, null)), mergePolicy));
            builder.optionValue(floatOption, new Double[] {Double.valueOf(1000)});
        }

        builder.create().test(parserMode);
    }

    @ParameterizedTest
    @MethodSource("testMergePolicy")
    public void testMergePolicyNoValue(MergePolicy mergePolicy, ParserMode parserMode) {

        if (mergePolicy == MergePolicy.CONCATENATE) {
            assertThrowsExactly(IllegalArgumentException.class, booleanOption("foo").mergePolicy(mergePolicy)::create);
            return;
        }

        final var aOption = booleanOption("a").mergePolicy(mergePolicy).create();
        final var bOption = booleanOption("boo").addAliases("b").mergePolicy(mergePolicy).create();

        final var builder = build().args("-a", "-boo", "-b");

        if (parserMode.equals(ParserMode.PARSE)) {
            builder.optionUntypedValue(aOption, new String[0]);
            builder.optionUntypedValue(bOption, new String[0]);
        } else {
            builder.optionValue(aOption, mergeScalarValues(List.of(true), mergePolicy));
            builder.optionValue(bOption, mergeScalarValues(List.of(true, true), mergePolicy));
        }

        builder.create().test(parserMode);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testUnrecognizedOptionMapping(boolean onlyUnrecognizedOption) {

        final var option = option("x", Object.class).converter(_ -> {
            throw new RuntimeException();
        }).create();

        final List<String> args = new ArrayList<>();

        if (!onlyUnrecognizedOption) {
            args.addAll(List.of("-x", "foo"));
        }
        args.add("--unrecognized");

        final var result = new JOptSimpleOptionsBuilder().options(option).create().apply(args.toArray(String[]::new));

        assertFalse(result.hasValue());
        assertEquals(1, result.errors().size());

        assertTrue(new ExceptionPattern()
                .isInstanceOf(jdk.internal.joptsimple.OptionException.class)
                .match(result.errors().iterator().next()));
    }

    @ParameterizedTest
    @MethodSource
    public void testErrorMapping(String expectedErrorMessage, String[] args) {

        var booleanOption = booleanOption("bool").addAliases("b").create();
        var stringOption = stringOption("str").addAliases("s").create();

        final var parse = new JOptSimpleOptionsBuilder()
                .options(booleanOption, stringOption)
                .jOptSimpleParserErrorHandler(err -> {
                    return new TestException(String.format("%s:%s", err.type(), err.optionName().formatForCommandLine()));
                })
                .create();

        final var expectedExceptions = List.of(new TestException(expectedErrorMessage));
        final var actualExceptions = parse.apply(args).errors();

        assertEquals(
                expectedExceptions.stream().map(Exception::getMessage).toList(),
                actualExceptions.stream().map(Exception::getMessage).toList());
    }

    @Test
    public void testNullErrorMapping() {
        final var parse = new JOptSimpleOptionsBuilder().jOptSimpleParserErrorHandler(_ -> {
            return null;
        }).create();

        assertThrowsExactly(NullPointerException.class, () -> parse.apply(new String[] {"--foo"}));
    }

    @Test
    public void testNoErrorMapping() {
        var errors = new JOptSimpleOptionsBuilder()
                .options(stringOption("str").create())
                .create()
                .apply(new String[] {"--str"}).errors();

        assertEquals(1, errors.size());
        assertTrue(errors.iterator().next() instanceof jdk.internal.joptsimple.OptionException);
    }

    @Test
    public void test_optionSpecMapper() {
        var mappedOption = stringOption("foo").create();
        var unmappedOption = stringOption("bar").create();
        var unusedOption = stringOption("baz").create();

        final var parse = new JOptSimpleOptionsBuilder().optionSpecMapper(optionSpec -> {
            if (optionSpec.name().equals(mappedOption.getSpec().name())) {
                return optionSpec.copyWithConverter(OptionValueConverter.<String>build()
                        .converter(ValueConverter.create(str -> {
                            return str.toUpperCase();
                        }, String.class)).create());
            } else {
                return optionSpec;
            }
        }).options(mappedOption, unmappedOption, unusedOption).create();

        var optionValues = parse.apply(new String[] {"--foo=Value", "--bar", "Value"})
                .orElseThrow().convertedOptions().orElseThrow().create();

        assertEquals("VALUE", mappedOption.getFrom(optionValues));
        assertEquals("Value", unmappedOption.getFrom(optionValues));
        assertFalse(unusedOption.containsIn(optionValues));
    }

    @ParameterizedTest
    @EnumSource(ParserMode.class)
    public void test_Options_find_negative(ParserMode mode) {
        final var booleanOption = booleanOption("b").create();
        final var booleanOption2 = booleanOption("b2").create();

        final var stringOption = stringOption("str").create();
        final var stringOption2 = stringOption("str2").create();

        final List<OptionValue<?>> allOptions = List.of(booleanOption, booleanOption2, stringOption, stringOption2);

        final BiFunction<Collection<OptionValue<?>>, List<String>, Options> parse = (options, args) -> {
            var theParse = new JOptSimpleOptionsBuilder().options(options).create();
            var builder = theParse.apply(args.toArray(String[]::new)).orElseThrow();

            switch (mode) {
                case PARSE -> {
                    return builder.create();
                }
                case CONVERT -> {
                    return builder.convertedOptions().orElseThrow().create();
                }
                default -> {
                    throw new AssertionError();
                }
            }
        };

        var optionValues = parse.apply(List.of(booleanOption, stringOption), List.of());

        assertTrue(optionValues.find(booleanOption.id()).isEmpty());
        assertTrue(optionValues.find(booleanOption2.id()).isEmpty());
        assertTrue(optionValues.find(stringOption.id()).isEmpty());
        assertTrue(optionValues.find(stringOption2.id()).isEmpty());

        optionValues = parse.apply(allOptions, List.of());

        assertTrue(optionValues.find(booleanOption.id()).isEmpty());
        assertTrue(optionValues.find(booleanOption2.id()).isEmpty());
        assertTrue(optionValues.find(stringOption.id()).isEmpty());
        assertTrue(optionValues.find(stringOption2.id()).isEmpty());

        optionValues = parse.apply(allOptions, List.of("--b2"));

        assertTrue(optionValues.find(booleanOption.id()).isEmpty());
        assertTrue(optionValues.find(booleanOption2.id()).isPresent());
        assertTrue(optionValues.find(stringOption.id()).isEmpty());
        assertTrue(optionValues.find(stringOption2.id()).isEmpty());

        optionValues = parse.apply(allOptions, List.of("--str="));

        assertTrue(optionValues.find(booleanOption.id()).isEmpty());
        assertTrue(optionValues.find(booleanOption2.id()).isEmpty());
        assertTrue(optionValues.find(stringOption.id()).isPresent());
        assertTrue(optionValues.find(stringOption2.id()).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_copyWithExcludes(boolean withRedirects) {
        final var aOption = stringOption("astr").createArray();
        final var bOption = stringOption("bstr").tokenizeOne().createArray();
        final var cOption = stringOption("cstr").tokenizeOne().converter(str -> {
            throw new RuntimeException();
        }).createArray();

        var builder = new JOptSimpleOptionsBuilder().options(aOption, bOption, cOption);
        if (withRedirects) {
            builder.optionSpecMapper(spec -> {
                return spec.copyWithDescription("Foo");
            });
        }

        var vanillaParsedOptionBuilder = builder.create().apply(new String[] {
                "--astr", "1", "--bstr", "2", "--astr", "3", "--bstr", "4", "--cstr", "foo"
        }).orElseThrow();

        var parsedOptionBuilder = vanillaParsedOptionBuilder;
        var options = parsedOptionBuilder.create();

        assertEquals(Set.of(aOption.id(), bOption.id(), cOption.id()), options.ids());
        assertEquals(List.of("1", "3"), List.of(aOption.getFrom(options)));
        assertEquals(List.of("2", "4"), List.of(bOption.getFrom(options)));
        assertEquals(List.of("foo"), List.of(cOption.getFrom(options)));

        parsedOptionBuilder = parsedOptionBuilder.copyWithExcludes(List.of(cOption.id()));
        options = parsedOptionBuilder.create();

        assertEquals(Set.of(aOption.id(), bOption.id()), options.ids());
        assertEquals(List.of("1", "3"), List.of(aOption.getFrom(options)));
        assertEquals(List.of("2", "4"), List.of(bOption.getFrom(options)));
        assertFalse(cOption.containsIn(options));

        parsedOptionBuilder = parsedOptionBuilder.copyWithExcludes(List.of(bOption.id()));
        options = parsedOptionBuilder.create();

        assertEquals(Set.of(aOption.id()), options.ids());
        assertEquals(List.of("1", "3"), List.of(aOption.getFrom(options)));
        assertFalse(bOption.containsIn(options));
        assertFalse(cOption.containsIn(options));

        var optionBuilder = vanillaParsedOptionBuilder.copyWithExcludes(List.of(cOption.id())).convertedOptions().orElseThrow();
        options = optionBuilder.create();

        assertEquals(Set.of(aOption.id(), bOption.id()), options.ids());
        assertEquals(List.of("1", "3"), List.of(aOption.getFrom(options)));
        assertEquals(List.of("2", "4"), List.of(bOption.getFrom(options)));
        assertFalse(cOption.containsIn(options));

        optionBuilder = optionBuilder.copyWithExcludes(List.of(aOption.id()));
        options = optionBuilder.create();

        assertEquals(Set.of(bOption.id()), options.ids());
        assertFalse(aOption.containsIn(options));
        assertEquals(List.of("2", "4"), List.of(bOption.getFrom(options)));
        assertFalse(cOption.containsIn(options));
    }

    @ParameterizedTest
    @EnumSource(ParserMode.class)
    public void test_Options_concat(ParserMode mode) {
        final var a = stringOption("a").create();
        final var b = stringOption("b").create();
        final var c = stringOption("c").create();

        var builder = new JOptSimpleOptionsBuilder().options(a, b, c);

        var parsedOptionBuilder = builder.create().apply(new String[] {
                "-a", "foo", "-b", "bar"
        }).orElseThrow();

        Options optionsFromJoptSimple;
        var expectedOptions = expectOptions();
        switch (mode) {
            case PARSE -> {
                optionsFromJoptSimple = parsedOptionBuilder.create();
                expectedOptions.add(a, new String[] {"foo"});
            }
            case CONVERT -> {
                optionsFromJoptSimple = parsedOptionBuilder.convertedOptions().orElseThrow().create();
                expectedOptions.add(a, "foo");
            }
            default -> {
                throw new AssertionError();
            }
        }

        var options = Options.concat(Options.of(Map.of(b, "buz")), optionsFromJoptSimple, Options.of(Map.of(c, 100)));

        expectedOptions.add(c, 100).add(b, "buz").apply(options);
    }

    @ParameterizedTest
    @EnumSource(ParserMode.class)
    public void test_Options_copyWith(ParserMode mode) {
        final var a = stringOption("a").create();
        final var b = stringOption("b").create();
        final var c = stringOption("c").create();

        var builder = new JOptSimpleOptionsBuilder().options(a, b, c);

        var parsedOptionBuilder = builder.create().apply(new String[] {
                "-a", "foo", "-b", "bar"
        }).orElseThrow();

        Options optionsFromJoptSimple;
        var expectedOptions = expectOptions();
        switch (mode) {
            case PARSE -> {
                optionsFromJoptSimple = parsedOptionBuilder.create();
                expectedOptions.add(a, new String[] {"foo"});
            }
            case CONVERT -> {
                optionsFromJoptSimple = parsedOptionBuilder.convertedOptions().orElseThrow().create();
                expectedOptions.add(a, "foo");
            }
            default -> {
                throw new AssertionError();
            }
        }

        var options = optionsFromJoptSimple.copyWith(a.id(), c.id());

        expectedOptions.apply(options);

        options = Options.concat(optionsFromJoptSimple.copyWith(a.id(), c.id()), Options.of(Map.of(c, 100)), Options.of(Map.of(b, "buz")));

        expectedOptions.add(c, 100).add(b, "buz").apply(options);
    }

    @Test
    public void test_detectedOptions() {
        final var aOption = stringOption("astr").addAliases("a").createArray();
        final var bOption = stringOption("bstr").addAliases("b").defaultOptionalValue("empty").create();
        final var cOption = stringOption("cstr").addAliases("c").create();
        final var dOption = booleanOption("dbool").addAliases("d").create();
        final var eOption = booleanOption("ebool").addAliases("e").create();

        var builder = new JOptSimpleOptionsBuilder().options(aOption, bOption, cOption, dOption, eOption);

        assertEquals(List.of(), builder.create().apply(new String[] {}).orElseThrow().detectedOptions());

        var vanillaParsedOptionBuilder = builder.create().apply(new String[] {
                "-ddededa", "1", "--bstr", "--astr", "3", "--bstr", "4", "--ebool", "--cstr", "foo", "-b"
        }).orElseThrow();

        var parsedOptionBuilder = vanillaParsedOptionBuilder;
        var detectedOptions = parsedOptionBuilder.detectedOptions();

        assertEquals(
                List.of("d", "d", "e", "d", "e", "d", "a", "bstr", "astr", "bstr", "ebool", "cstr", "b"),
                detectedOptions.stream().map(OptionName::name).toList());

        var convertedDetectedOptions = parsedOptionBuilder.convertedOptions().orElseThrow().detectedOptions();
        assertEquals(detectedOptions, convertedDetectedOptions);

        parsedOptionBuilder = vanillaParsedOptionBuilder.copyWithExcludes(List.of(eOption.id()));
        detectedOptions = parsedOptionBuilder.detectedOptions();

        assertEquals(
                List.of("d", "d", "d", "d", "a", "bstr", "astr", "bstr", "cstr", "b"),
                detectedOptions.stream().map(OptionName::name).toList());

        convertedDetectedOptions = parsedOptionBuilder.convertedOptions().orElseThrow().detectedOptions();
        assertEquals(detectedOptions, convertedDetectedOptions);

        convertedDetectedOptions = parsedOptionBuilder.convertedOptions().orElseThrow().copyWithExcludes(List.of(aOption.id())).detectedOptions();
        assertEquals(
                List.of("d", "d", "d", "d", "bstr", "bstr", "cstr", "b"),
                convertedDetectedOptions.stream().map(OptionName::name).toList());
    }

    private static <T> T mergeScalarValues(List<T> values, MergePolicy mergePolicy) {
        switch (mergePolicy) {
            case USE_FIRST -> {
                return values.getFirst();
            }
            case USE_LAST -> {
                return values.getLast();
            }
            default -> {
                throw new IllegalArgumentException();
            }
        }
    }

    private static <T> T[] mergeArrayValues(List<T[]> values, MergePolicy mergePolicy) {
        switch (mergePolicy) {
            case USE_FIRST -> {
                return values.getFirst();
            }
            case USE_LAST -> {
                return values.getLast();
            }
            case CONCATENATE -> {
                return values.stream().map(Stream::of).flatMap(x -> x).toList().toArray(values.getFirst());
            }
            default -> {
                throw new IllegalArgumentException();
            }
        }
    }

    private static String[] mergeUntypedValues(List<String> values, MergePolicy mergePolicy) {
        switch (mergePolicy) {
            case USE_FIRST -> {
                return values.subList(0, 1).toArray(String[]::new);
            }
            case USE_LAST -> {
                return values.subList(values.size() - 1, values.size()).toArray(String[]::new);
            }
            case CONCATENATE -> {
                return values.toArray(String[]::new);
            }
            default -> {
                throw new IllegalArgumentException();
            }
        }
    }

    private static List<Object[]> testMergePolicy() {
        final List<Object[]> data = new ArrayList<>();
        for (var mergePolicy : MergePolicy.values()) {
            for (var parserMode : ParserMode.values()) {
                data.add(new Object[] { mergePolicy, parserMode });
            }
        }
        return data;
    }

    private static Collection<Object[]> testErrorMapping() {
        return List.<Object[]>of(
                testErrorMappingTestCase(JOptSimpleErrorType.UNRECOGNIZED_OPTION, "-a", "-a"),
                testErrorMappingTestCase(JOptSimpleErrorType.UNRECOGNIZED_OPTION, "-a", "--a"),
                testErrorMappingTestCase(JOptSimpleErrorType.UNRECOGNIZED_OPTION, "-f", "-foo"),
                // Two unrecognizable options, only the first one will be reported
                testErrorMappingTestCase(JOptSimpleErrorType.UNRECOGNIZED_OPTION, "--foo", "--foo", "-z"),
                testErrorMappingTestCase(JOptSimpleErrorType.UNRECOGNIZED_OPTION, "-z", "-z", "--foo"),

                testErrorMappingTestCase(JOptSimpleErrorType.OPTION_MISSING_REQUIRED_ARGUMENT, "--str", "--str")
        );
    }

    private static Object[] testErrorMappingTestCase(JOptSimpleErrorType type, String optionName, String... args) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(optionName);
        return new Object[] { String.format("%s:%s", type, optionName), args };
    }

    private static Collection<TestSpec> testOptionalValueParse() {
        return testOptionalValue(ParserMode.PARSE);
    }

    private static Collection<TestSpec> testOptionalValueConvert() {
        return testOptionalValue(ParserMode.CONVERT);
    }

    private static Collection<TestSpec> testOptionalValue(ParserMode parserMode) {
        return Stream.of(
                build().mode(parserMode).args("-x")
                .optionValue(option("x", Integer.class).converter(Integer::valueOf).defaultOptionalValue(17).create())
                .expectParse("-x").expect(17).commit(),

                build().mode(parserMode).args("-x")
                .optionValue(option("yy", Integer.class).addAliases("x").converter(Integer::valueOf).defaultOptionalValue(17).create())
                .expectParse("--yy").expect(17).commit(),

                build().mode(parserMode).args("--yy")
                .optionValue(option("yy", Integer.class).addAliases("x").converter(Integer::valueOf).defaultOptionalValue(17).create())
                .expectParse("--yy").expect(17).commit(),

                build().mode(parserMode).args("-x", "23")
                .optionValue(option("x", Integer.class).converter(Integer::valueOf).defaultOptionalValue(17).create())
                .expectParse("23").expect(23).commit(),

                //
                // joptsimple discards instances of an option without values if there is an instance of the option with a value
                //

                build().mode(parserMode).args("-x", "-x", "23")
                .optionValue(option("x", Integer.class).converter(Integer::valueOf).defaultOptionalValue(17).create())
                .expectParse("23").expect(23).commit(),

                build().mode(parserMode).args("-x", "-x", "23:17", "-x", "56", "-x")
                .optionValue(option("x", Integer.class).converter(Integer::valueOf).defaultOptionalValue(17).tokenizer(":").createArray())
                .expectParse("23:17", "56").expect(new Integer[] {23, 17, 56}).commit(),

                build().mode(parserMode).args("-x", "-x", "-x")
                .optionValue(option("x", Integer.class).converter(Integer::valueOf).defaultOptionalValue(17).tokenizer(":").createArray())
                .expectParse("-x").expect(new Integer[] {17}).commit(),

                build().mode(parserMode).args("-x", "--yy")
                .optionValue(option("yy", Integer.class).addAliases("x").converter(Integer::valueOf).defaultOptionalValue(17).tokenizer(":").createArray())
                .expectParse("--yy").expect(new Integer[] {17}).commit(),

                build().mode(parserMode).args("-x", "--yy", "-x", "--yy", "-x", "--yy")
                .optionValue(option("yy", Integer.class).addAliases("x").converter(Integer::valueOf).defaultOptionalValue(17).tokenizer(":").createArray())
                .expectParse("--yy").expect(new Integer[] {17}).commit(),

                build().mode(parserMode).args("-x", "--yy", "23:17", "-x", "56", "-x")
                .optionValue(option("yy", Integer.class).addAliases("x").converter(Integer::valueOf).defaultOptionalValue(17).tokenizer(":").createArray())
                .expectParse("23:17", "56").expect(new Integer[] {23, 17, 56}).commit(),

                build().mode(parserMode).args("-x", "--yy", "23", "-x", "56", "-x")
                .optionValue(option("yy", Integer.class).addAliases("x").converter(Integer::valueOf).defaultOptionalValue(17).create())
                .expectParse("56").expect(56).commit()
        ).map(TestSpec.Builder::create).toList();
    }

    private static Collection<TestSpec> test() {
        final var pwd = Path.of("").toAbsolutePath();
        return Stream.of(
                build().optionValue(
                        directoryOption("input").addAliases("i").create(),
                        pwd
                ).args("--input", "", "-i", pwd.toString()),

                build().optionValue(
                        directoryOption("dir").tokenizer(pathSeparator()).createArray(),
                        new Path[] { pwd, Path.of(".") }
                ).args("--dir=" + pwd.toString() + pathSeparator() + "."),

                build().optionValue(
                        stringOption("arguments").tokenizer("\\s+").createArray(toList()),
                        List.of("", "a", "b", "c", "", "de")
                ).args("--arguments", " a b  c", "--arguments", " de"),

                build().optionValue(
                        stringOption("arguments").tokenizer(";+").createArray(toList()),
                        List.of("a b", "c", "de")
                ).args("--arguments", "a b;;c", "--arguments", "de;"),

                build().optionValue(stringOption("foo").create(), "--foo").args("--foo", "--foo"),

                build().args("--foo")
                        .optionValue(booleanOption("foo").create(), true)
                        .optionValue(booleanOption("bar").create(), false),

                build().args("-x", "").optionValue(stringOption("x").create(), ""),
                build().args("-x", "").optionValue(stringOption("x").createArray(), new String[] {""}),
                build().args("-x", "", "-x", "").optionValue(stringOption("x").createArray(), new String[] {"", ""}),

                // Test merging order.
                build().optionValue(
                        stringOption("x").addAliases("y").tokenizeOne().createArray(toList()),
                        List.of("10", "RR", "P", "Z")
                ).args("-x", "10", "-y", "RR", "-x", "P", "-y", "Z"),

                // Test converters are not executed on discarded invalid values (recoverable conversion errors).
                build().optionValue(
                        option("x", Integer.class).converter(Integer::valueOf).create(),
                        100
                ).args("-x", "a", "-x", "100"),
                build().optionValue(
                        option("x", Integer.class).mergePolicy(MergePolicy.USE_FIRST)
                                .exceptionFormatString("")
                                .exceptionFactory(ERROR_WITH_VALUE_AND_OPTION_NAME)
                                .converter(Integer::valueOf).tokenizer(",").createArray(),
                        new Integer[] {34}
                ).args("-x", "34,A", "-x", "f"),

                // Test the last array element (recoverable conversion errors).
                build().optionValue(
                        option("x", Integer.class).mergePolicy(MergePolicy.USE_LAST)
                                .converter(Integer::valueOf).tokenizer(",").createArray(toList()),
                        List.of(78)
                ).args("-x", "1,3,78"),
                build().optionValue(
                        option("x", Integer.class).mergePolicy(MergePolicy.USE_LAST)
                                .exceptionFormatString("")
                                .exceptionFactory(ERROR_WITH_VALUE_AND_OPTION_NAME)
                                .converter(Integer::valueOf).tokenizer(",").createArray(toList()),
                        List.of(78)
                ).args("-x", "1,ZZZ,78"),
                build().optionValue(
                        option("x", Integer.class).mergePolicy(MergePolicy.USE_LAST)
                                .converter(Integer::valueOf).tokenizer(",").createArray(toList()),
                        List.of(35)
                ).args("-x", "1,3,78", "-x", "a", "-x", "35"),
                build().optionValue(
                        option("x", Integer.class).mergePolicy(MergePolicy.USE_LAST)
                                .converter(Integer::valueOf).tokenizer(TestUtils.splitOrEmpty(",")).createArray(toList()),
                        List.of()
                ).args("-x", "1,3,78", "-x", ""),

                // Test the first array element (recoverable conversion errors).
                build().optionValue(
                        option("x", Integer.class).mergePolicy(MergePolicy.USE_FIRST)
                                .exceptionFormatString("")
                                .exceptionFactory(ERROR_WITH_VALUE_AND_OPTION_NAME)
                                .converter(Integer::valueOf).tokenizer(",").createArray(toList()),
                        List.of(1)
                ).args("-x", "1,ZZZ,78"),
                build().optionValue(
                        option("x", Integer.class).mergePolicy(MergePolicy.USE_FIRST)
                                .converter(Integer::valueOf).tokenizer(",").createArray(toList()),
                        List.of(1)
                ).args("-x", "1,3,78", "-x", "a", "-x", "35"),
                build().optionValue(
                        option("x", Integer.class).mergePolicy(MergePolicy.USE_FIRST)
                                .converter(Integer::valueOf).tokenizer(TestUtils.splitOrEmpty(",")).createArray(toList()),
                        List.of()
                ).args("-x", "", "-x", "1,23,78"),

                // Test array value is a scalar for parser.
                build().optionValue(
                        option("arr", int[].class).converter(str -> {
                            return Stream.of(str.split(",")).map(Integer::valueOf).mapToInt(Integer::intValue).toArray();
                        }).create(),
                        new int[] {1, 45, 67}
                ).args("--arr=1,45,67"),

                // Test that parser can handle multi-dimensional arrays.
                build().optionValue(
                        option("arr", int[].class).converter(str -> {
                            if (str.isEmpty()) {
                                return new int[0];
                            } else {
                                return Stream.of(str.split(",")).map(Integer::valueOf).mapToInt(Integer::intValue).toArray();
                            }
                        }).tokenizer(":").createArray(toList()),
                        List.of(new int[] {1, 45, 67}, new int[0], new int[] {3}, new int[] {56}, new int[] {77, 82}),
                        (expected, actual) -> {
                            assertEquals(expected.size(), actual.size());
                            for (int i = 0; i != expected.size(); i++) {
                                assertArrayEquals(expected.get(i), actual.get(i));
                            }
                        }
                ).args("--arr=1,45,67::3:56", "--arr=77,82")

        ).map(TestSpec.Builder::create).toList();
    }

    private static Collection<TestSpec> testStringVector() {
        final var args = List.of("--foo", "1 22 333", "--foo", "44 44");
        return Stream.of(
                build().optionValue(
                        stringOption("foo").createArray(),
                        new String[] { "1 22 333", "44 44" }
                ).args(args),

                build().optionValue(
                        stringOption("foo").tokenizeOne().createArray(toList()),
                        List.of("1 22 333", "44 44")
                ).args(args),

                build().optionValue(
                        stringOption("foo").tokenizer("\\s+").createArray(),
                        new String[] { "1", "22", "333", "44", "44" }
                ).args(args),

                build().optionValue(
                        stringOption("foo").tokenizer("\\s+").createArray(toList()),
                        List.of("1", "22", "333", "44", "44")
                ).args(args)
        ).map(TestSpec.Builder::create).toList();
    }

    private static <T> OptionSpecBuilder<T> option(String name, Class<? extends T> valueType) {
        return OptionSpecBuilder.<T>create(valueType)
                .name(Objects.requireNonNull(name))
                .scope(new BundlingOperationOptionScope() {
                    @Override
                    public BundlingOperationDescriptor descriptor() {
                        throw new AssertionError();
                    }});
    }

    private static OptionSpecBuilder<String> stringOption(String name) {
        return option(name, String.class).converter(identityConv());
    }

    private static OptionSpecBuilder<Path> pathOption(String name) {
        return option(name, Path.class)
                .converter(pathConv())
                // "*" is an invalid symbol in Windows paths and valid in Linux paths.
                // Add a validator to make it invalid on all platforms to simplify testing.
                .validator(new Predicate<Path>() {
                    @Override
                    public boolean test(Path path) {
                        return path.toString().indexOf("*") == -1;
                    }
                })
                .converterExceptionFactory(ERROR_WITH_VALUE_AND_OPTION_NAME)
                .converterExceptionFormatString(FORMAT_STRING_ILLEGAL_PATH);
    }

    private static OptionSpecBuilder<Path> directoryOption(String name) {
        return pathOption(name)
                .validator(StandardValidator.IS_DIRECTORY)
                .validatorExceptionFactory(ERROR_WITH_VALUE_AND_OPTION_NAME)
                .validatorExceptionFormatString(FORMAT_STRING_NOT_DIRECTORY);
    }

    private static OptionSpecBuilder<Boolean> booleanOption(String name) {
        return option(name, Boolean.class).defaultValue(Boolean.FALSE);
    }

    private static Function<List<String>, Options> createParser(ParserMode mode, OptionValue<?>... options) {
        return createParser(mode, List.of(options));
    }

    private static Function<List<String>, Options> createParser(ParserMode mode, Iterable<OptionValue<?>> options) {
        Objects.requireNonNull(mode);
        final var parse = new JOptSimpleOptionsBuilder().options(StreamSupport.stream(options.spliterator(), false)
                .map(OptionValue::getOption).toList()).create();
        return args -> {
            final var builder = parse.apply(args.toArray(String[]::new)).orElseThrow();
            switch (mode) {
                case PARSE -> {
                    return builder.create();
                }
                case CONVERT -> {
                    return builder.convertedOptions().orElseThrow().create();
                }
                default -> {
                    throw new IllegalArgumentException();
                }
            }
        };
    }

    private static TestSpec.Builder build() {
        return new TestSpec.Builder();
    }


    private static final class FaultyParserArgsConfig {

        void test() {

            final Collection<OptionFailure> recordedErrors = new ArrayList<>();

            final List<OptionValue<?>> optionValues = new ArrayList<>();

            optionSpecBuilders.stream().map(builder -> {
                configureExceptions(builder);
                return builder.exceptionFactory(TestUtils.recordExceptions(recordedErrors));
            }).map(OptionSpecBuilder::create).forEach(optionValues::add);

            arrayOptionSpecBuilders.stream().map(builder -> {
                configureExceptions(builder);
                return builder.exceptionFactory(TestUtils.recordExceptions(recordedErrors));
            }).map(OptionSpecBuilder::createArray).forEach(optionValues::add);

            final var parser = new JOptSimpleOptionsBuilder().options(optionValues).create();

            final Supplier<Result<?>> createCmdline = parser.apply(args.toArray(String[]::new)).orElseThrow()::convertedOptions;

            if (expectedConverterException == null) {
                final var result = createCmdline.get();

                assertFalse(result.hasValue());

                // Not all exceptions recorded by TestUtils.recordExceptions() facility
                // will be returned by JOptSimpleOptionsBuilder.OptionsBuilder.convertedOptions()
                // because it may run a converter multiple times on the same option value,
                // attempting to recover from a previous error.
                var actualErrors = result.errors().stream().map(exception -> {
                    return recordedErrors.stream().filter(recorderdFailure -> {
                        return (recorderdFailure.exception().orElseThrow() == exception);
                    }).findFirst().map(OptionFailure::withoutException).orElseThrow();
                }).toList();

                assertEquals(expectedErrors, actualErrors);

            } else {
                final var ex = assertThrowsExactly(ConverterException.class, () -> createCmdline.get());
                assertSame(expectedConverterException, ex.getCause());
            }
        }

        FaultyParserArgsConfig args(Collection<String> v) {
            args.addAll(v);
            return this;
        }

        FaultyParserArgsConfig args(String... v) {
            return args(List.of(v));
        }

        FaultyParserArgsConfig clearArgs() {
            args.clear();
            return this;
        }

        FaultyParserArgsConfig options(Collection<OptionSpecBuilder<?>> v) {
            optionSpecBuilders.addAll(v);
            return this;
        }

        FaultyParserArgsConfig options(OptionSpecBuilder<?>... v) {
            return options(List.of(v));
        }

        FaultyParserArgsConfig arrayOptions(Collection<OptionSpecBuilder<?>> v) {
            arrayOptionSpecBuilders.addAll(v);
            return this;
        }

        FaultyParserArgsConfig arrayOptions(OptionSpecBuilder<?>... v) {
            return arrayOptions(List.of(v));
        }

        FaultyParserArgsConfig expectErrors(Collection<OptionFailure> v) {
            expectedErrors.addAll(v);
            return this;
        }

        FaultyParserArgsConfig expectErrors(OptionFailure... v) {
            return expectErrors(List.of(v));
        }

        FaultyParserArgsConfig expectError(String optionName, String optionValue) {
            return expectError(optionName, StringToken.of(optionValue));
        }

        FaultyParserArgsConfig expectError(String optionName, StringToken optionValue) {
            return expectErrors(new OptionFailure(optionName, optionValue));
        }

        FaultyParserArgsConfig expectConverterException(Exception v) {
            expectedConverterException = v;
            return this;
        }

        private static void configureExceptions(OptionSpecBuilder<?> builder) {
            builder.exceptionFactory(factory -> {
                if (factory == null) {
                    builder.exceptionFormatString("Option value [%s] of option %s");
                    factory = ERROR_WITH_VALUE_AND_OPTION_NAME;
                }
                return factory;
            });
        }

        private final List<String> args = new ArrayList<>();
        private final Collection<OptionSpecBuilder<?>> optionSpecBuilders = new ArrayList<>();
        private final Collection<OptionSpecBuilder<?>> arrayOptionSpecBuilders = new ArrayList<>();
        private final List<OptionFailure> expectedErrors = new ArrayList<>();
        private Exception expectedConverterException;
    }


    private static final String FORMAT_STRING_ILLEGAL_PATH = "The value '%s' provided for parameter %s is not a valid path";

    private static final String FORMAT_STRING_NOT_DIRECTORY = "The value '%s' provided for parameter %s is not a directory path";

    private static final OptionValueExceptionFactory<? extends RuntimeException> ERROR_WITH_VALUE_AND_OPTION_NAME = OptionValueExceptionFactory.build(TestException::new)
            .formatArgumentsTransformer(StandardArgumentsMapper.VALUE_AND_NAME)
            .messageFormatter(String::format)
            .create();
}
