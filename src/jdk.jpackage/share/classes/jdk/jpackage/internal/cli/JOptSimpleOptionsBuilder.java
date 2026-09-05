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

import static java.util.stream.Collectors.toUnmodifiableMap;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static jdk.jpackage.internal.cli.OptionValueConverter.convertString;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jdk.internal.joptsimple.ArgumentAcceptingOptionSpec;
import jdk.internal.joptsimple.OptionParser;
import jdk.internal.joptsimple.OptionSet;
import jdk.jpackage.internal.cli.DefaultOptions.OptionIdentifierWithValue;
import jdk.jpackage.internal.cli.DefaultOptions.Snapshot;
import jdk.jpackage.internal.cli.OptionSpec.MergePolicy;
import jdk.jpackage.internal.util.Result;


/**
 * Builds an instance of {@link Options} interface backed with joptsimple command
 * line parser.
 *
 * Two types of command line argument processing are supported:
 * <ol>
 * <li>Parse command line. Parsed data is stored as a map of strings.
 * <li>Convert strings to objects. Parsed data is stored as a map of objects.
 * </ol>
 */
final class JOptSimpleOptionsBuilder {

    Function<String[], Result<OptionsBuilder>> create() {
        return createJOptSimpleParser()::parse;
    }

    JOptSimpleOptionsBuilder options(Collection<? extends WithOptionIdentifier> v) {
        v.stream().map(u -> {
            switch (u) {
                case Option o -> {
                    return o;
                }
                case OptionValue<?> ov -> {
                    return ov.getOption();
                }
                default -> {
                    throw new IllegalArgumentException();
                }
            }
        }).forEach(options::add);
        return this;
    }

    JOptSimpleOptionsBuilder options(WithOptionIdentifier... v) {
        return options(List.of(v));
    }

    JOptSimpleOptionsBuilder optionSpecMapper(UnaryOperator<OptionSpec<?>> v) {
        optionSpecMapper = v;
        return this;
    }

    JOptSimpleOptionsBuilder jOptSimpleParserErrorHandler(Function<JOptSimpleError, ? extends Exception> v) {
        jOptSimpleParserErrorHandler = v;
        return this;
    }

    private JOptSimpleParser createJOptSimpleParser() {
        return JOptSimpleParser.create(options, Optional.ofNullable(optionSpecMapper),
                Optional.ofNullable(jOptSimpleParserErrorHandler));
    }


    static final class ConvertedOptionsBuilder {

        private ConvertedOptionsBuilder(TypedOptions options) {
            impl = Objects.requireNonNull(options);
        }

        Options create() {
            return impl;
        }

        ConvertedOptionsBuilder copyWithExcludes(Collection<? extends OptionIdentifier> v) {
            return new ConvertedOptionsBuilder(impl.copyWithout(v));
        }

        List<String> nonOptionArguments() {
            return impl.nonOptionArguments();
        }

        List<OptionName> detectedOptions() {
            return impl.detectedOptions();
        }

        private final TypedOptions impl;
    }


    static final class OptionsBuilder {

        private OptionsBuilder(UntypedOptions options) {
            impl = Objects.requireNonNull(options);
        }

        Result<ConvertedOptionsBuilder> convertedOptions() {
            return impl.toTypedOptions().map(ConvertedOptionsBuilder::new);
        }

        Options create() {
            return impl;
        }

        OptionsBuilder copyWithExcludes(Collection<? extends OptionIdentifier> v) {
            return new OptionsBuilder(impl.copyWithout(v));
        }

        List<String> nonOptionArguments() {
            return impl.nonOptionArguments();
        }

        List<OptionName> detectedOptions() {
            return impl.detectedOptions();
        }

        private final UntypedOptions impl;
    }


    enum JOptSimpleErrorType {

        // jdk.internal.joptsimple.UnrecognizedOptionException
        UNRECOGNIZED_OPTION(() -> {
            new OptionParser(false).parse("--foo");
        }),

        // jdk.internal.joptsimple.OptionMissingRequiredArgumentException
        OPTION_MISSING_REQUIRED_ARGUMENT(() -> {
            var parser = new OptionParser(false);
            parser.accepts("foo").withRequiredArg();
            parser.parse("--foo");
        }),
        ;

        JOptSimpleErrorType(Runnable initializer) {
            try {
                initializer.run();
                // Should never get to this point as the above line is expected to throw
                // an exception of type `jdk.internal.joptsimple.OptionException`.
                throw new AssertionError();
            } catch (jdk.internal.joptsimple.OptionException ex) {
                type = ex.getClass();
            }
        }

        private final Class<? extends jdk.internal.joptsimple.OptionException> type;
    }


    record JOptSimpleError(JOptSimpleErrorType type, OptionName optionName) {

        JOptSimpleError {
            Objects.requireNonNull(type);
            Objects.requireNonNull(optionName);
        }

        static JOptSimpleError create(jdk.internal.joptsimple.OptionException ex) {
            var optionName = OptionName.of(ex.options().getFirst());
            return Stream.of(JOptSimpleErrorType.values()).filter(v -> {
                return v.type.isInstance(ex);
            }).findFirst().map(v -> {
                return new JOptSimpleError(v, optionName);
            }).orElseThrow();
        }
    }


    private record JOptSimpleParser(
            OptionParser parser,
            Map<OptionIdentifier, ? extends OptionSpec<?>> optionMap,
            Optional<Function<JOptSimpleError, ? extends Exception>> jOptSimpleParserErrorHandler) {

        private JOptSimpleParser {
            Objects.requireNonNull(parser);
            Objects.requireNonNull(optionMap);
            Objects.requireNonNull(jOptSimpleParserErrorHandler);
        }

        Result<OptionsBuilder> parse(String... args) {
            return applyParser(parser, args).map(optionSet -> {
                final OptionSet mergerOptionSet;
                if (optionMap.values().stream().allMatch(spec -> spec.names().size() == 1)) {
                    // No specs with multiple names, merger not needed.
                    mergerOptionSet = optionSet;
                } else {
                    final var parser2 = createOptionParser();
                    final var optionSpecApplier = new OptionSpecApplier();
                    for (final var spec : optionMap.values()) {
                        optionSpecApplier.applyToParser(parser2, spec);
                    }

                    mergerOptionSet = parser2.parse(args);
                }
                return new OptionsBuilder(new UntypedOptions(optionSet, mergerOptionSet, optionMap));
            });
        }

        static JOptSimpleParser create(Iterable<Option> options,
                Optional<UnaryOperator<OptionSpec<?>>> optionSpecMapper,
                Optional<Function<JOptSimpleError, ? extends Exception>> jOptSimpleParserErrorHandler) {
            final var parser = createOptionParser();

            // Create joptsimple option specs for distinct option names,
            // i.e., individual joptsimple option spec for every name of jpackage option spec.
            // This is needed to accurately detect what option names were passed.
            final var optionSpecApplier = new OptionSpecApplier().generateForEveryName(true);

            final var optionStream = StreamSupport.stream(options.spliterator(), false);

            final var optionMap = optionSpecMapper.map(mapper -> {
                return optionStream.map(option -> {
                    return option.copyWithSpec(mapper.apply(option.spec()));
                });
            }).orElse(optionStream).peek(option -> {
                optionSpecApplier.applyToParser(parser, option.spec());
            }).collect(toUnmodifiableMap(Option::id, Option::spec));

            return new JOptSimpleParser(parser, optionMap, jOptSimpleParserErrorHandler);
        }

        private Result<OptionSet> applyParser(OptionParser parser, String[] args) {
            try {
                return Result.ofValue(parser.parse(args));
            } catch (jdk.internal.joptsimple.OptionException ex) {
                return Result.ofError(jOptSimpleParserErrorHandler.map(handler -> {
                    var err = handler.apply(JOptSimpleError.create(ex));
                    return Objects.requireNonNull(err);
                }).orElse(ex));
            }
        }

        private static OptionParser createOptionParser() {
            // No abbreviations!
            // Otherwise for the configured option "foo" it will recognize "f" as its abbreviation.
            return new OptionParser(false);
        }
    }


    private static final class OptionSpecApplier {

        <T> void applyToParser(OptionParser parser, OptionSpec<T> spec) {
            final Stream<OptionSpec<T>> optionSpecs;
            if (generateForEveryName) {
                optionSpecs = spec.copyForEveryName();
            } else {
                optionSpecs = Stream.of(spec);
            }

            optionSpecs.forEach(v -> {
                final var specBuilder = parser.acceptsAll(v.names().stream().map(OptionName::name).toList());
                if (v.hasValue()) {
                    if (v.isValueOptional()) {
                        specBuilder.withOptionalArg();
                    } else {
                        specBuilder.withRequiredArg();
                    }
                }
            });
        }

        OptionSpecApplier generateForEveryName(boolean v) {
            generateForEveryName = v;
            return this;
        }

        private boolean generateForEveryName;
    }


    sealed interface ExtendedOptions<T extends ExtendedOptions<T>> extends Options {

        /**
         * Gets the list of command line tokens not linked to any option in the order
         * they appear on the command line.
         *
         * @return Gets the list of command line tokens not linked to any option in the
         *         order they appear on the command line
         */
        List<String> nonOptionArguments();

        /**
         * Gets the list of names of detected options in the order they appear on the
         * command line. A name will appear in the list as many times as the
         * corresponding option on the command line.
         *
         * @return the list of names of detected options in the order they appear on the
         *         command line
         */
        List<OptionName> detectedOptions();

        DefaultOptions toDefaultOptions();
    }


    private static final class UntypedOptions implements ExtendedOptions<UntypedOptions> {

        UntypedOptions(OptionSet optionSet, OptionSet mergerOptionSet, Map<OptionIdentifier, ? extends OptionSpec<?>> optionMap) {
            this.optionSet = Objects.requireNonNull(optionSet);
            this.mergerOptionSet = Objects.requireNonNull(mergerOptionSet);
            optionNames = optionMap.values().stream()
                    .map(OptionSpec::names)
                    .flatMap(Collection::stream)
                    .filter(optionName -> {
                        return optionSet.has(optionName.name());
                    }).collect(toUnmodifiableSet());
            this.optionMap = optionMap.entrySet().stream().filter(e -> {
                return !Collections.disjoint(optionNames, e.getValue().names());
            }).collect(toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        private UntypedOptions(UntypedOptions other, Collection<? extends OptionIdentifier> excludes) {
            this(other.optionSet, other.mergerOptionSet, other.optionMap.entrySet().stream().filter(e -> {
                return !excludes.contains(e.getKey());
            }).collect(toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)));
        }

        @Override
        public UntypedOptions copyWithout(Iterable<? extends OptionIdentifier> ids) {
            return new UntypedOptions(this, StreamSupport.stream(ids.spliterator(), false).toList());
        }

        @Override
        public Set<? extends OptionIdentifier> ids() {
            return optionMap.keySet();
        }

        @Override
        public List<String> nonOptionArguments() {
            return optionSet.nonOptionArguments().stream().map(String.class::cast).toList();
        }

        @Override
        public List<OptionName> detectedOptions() {
            return optionSet.specs().stream().flatMap(joptSpec -> {
                return joptSpec.options().stream().map(OptionName::new);
            }).filter(optionNames::contains).toList();
        }

        @Override
        public DefaultOptions toDefaultOptions() {
            return new DefaultOptions(optionMap.entrySet().stream().collect(toUnmodifiableMap(e -> {
                return new Option(e.getKey(), e.getValue());
            }, e -> {
                return find(e.getKey()).orElseThrow();
            })), optionNames::contains);
        }

        Result<TypedOptions> toTypedOptions() {

            final List<Exception> errors = new ArrayList<>();

            final var optionNameToOption = optionMap.entrySet().stream().map(e -> {
                return e.getValue().names().stream().map(optionName -> {
                    return Map.entry(optionName, new Option(e.getKey(), e.getValue()));
                });
            }).flatMap(x -> x).collect(toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

            var entries = OptionSpecWithValue.create(optionSet, mergerOptionSet, optionMap).stream().map(oswv -> {
                var option = optionNameToOption.get(oswv.optionName());
                if (!oswv.optionSpec().hasValue()) {
                    return new OptionIdentifierWithValue(option, Boolean.TRUE);
                } else {
                    return oswv.convertValue().map(v -> {
                        if (oswv.optionSpec().arrayValueConverter().isEmpty()) {
                            return new OptionIdentifierWithValue(option, v);
                        } else {
                            return new OptionIdentifierWithValue(option, new ArrayList<>(List.of(v)));
                        }
                    }).peekErrors(errors::addAll).value().orElse(null);
                }
            }).filter(Objects::nonNull).toList();

            if (!errors.isEmpty()) {
                return Result.ofErrors(errors);
            } else {
                final Map<OptionIdentifier, OptionIdentifierWithValue> map = new HashMap<>();

                for (var e : entries) {
                    var option = (Option)e.withId();
                    map.merge(option.id(), e, (a, b) -> {
                        if (option.spec().arrayValueConverter().isEmpty()) {
                            throw new AssertionError();
                        } else {
                            @SuppressWarnings("unchecked")
                            var ar = ((List<Object>)a.value());

                            @SuppressWarnings("unchecked")
                            var br = ((List<Object>)b.value());

                            ar.addAll(br);
                            return e.copyWithValue(ar);
                        }
                    });
                }

                for (var value : map.values()) {
                    var option = (Option)value.withId();
                    if (option.spec().arrayValueConverter().isPresent()) {
                        var arr = mergeArrayValues(option.spec().mergePolicy(), (List<?>)value.value());
                        map.put(value.id(), value.copyWithValue(arr));
                    }
                }

                return Result.ofValue(new TypedOptions(
                        DefaultOptions.create(new Snapshot(map, optionNames)),
                        detectedOptions(),
                        nonOptionArguments()));
            }
        }

        @Override
        public Optional<Object> find(OptionIdentifier id) {
            Objects.requireNonNull(id);
            return Optional.ofNullable(optionMap.get(id)).map(spec -> {
                var values = getValues(spec);
                if (spec.hasValue()) {
                    return values.toArray(String[]::new);
                } else {
                    return new String[0];
                }
            });
        }

        @Override
        public boolean contains(OptionName optionName) {
            return optionNames.contains(optionName);
        }

        private List<String> getValues(OptionSpec<?> spec) {
            Objects.requireNonNull(spec);

            final var values = optionValues(mergerOptionSet, spec.name());

            if (!values.isEmpty()) {
                return getOptionValue(values, spec.mergePolicy());
            } else if (spec.names().stream().anyMatch(this::contains)) {
                // The spec belongs to an option detected on the command line.
                if (!spec.hasValue()) {
                    // The spec belongs to an option without a value.
                    return List.of();
                } else if (spec.isValueOptional()) {
                    // The spec belongs to an option with an optional value.
                    return List.of(spec.name().formatForCommandLine());
                }
            }

            throw new AssertionError();
        }

        private static Object mergeArrayValues(OptionSpec.MergePolicy mergePolicy, List<?> value) {
            switch (mergePolicy) {
                case USE_FIRST -> {
                    // Find the first non-empty array, get its first element and wrap it into one-element array.
                    return value.stream().filter(arr -> {
                        return Array.getLength(arr) > 0;
                    }).findFirst().map(arr -> {
                        return asArray(Array.get(arr, 0));
                    }).orElseGet(value::getFirst);
                }
                case USE_LAST -> {
                    // Find the last non-empty array, get its last element and wrap it into one-element array.
                    return value.reversed().stream().filter(arr -> {
                        return Array.getLength(arr) > 0;
                    }).findFirst().map(arr -> {
                        return asArray(Array.get(arr, Array.getLength(arr) - 1));
                    }).orElseGet(value::getFirst);
                }
                case CONCATENATE -> {
                    return value.stream().filter(arr -> {
                        return Array.getLength(arr) > 0;
                    }).map(Object.class::cast).reduce((a, b) -> {
                        final var al = Array.getLength(a);
                        final var bl = Array.getLength(b);
                        final var arr = Array.newInstance(a.getClass().componentType(), al + bl);
                        System.arraycopy(a, 0, arr, 0, al);
                        System.arraycopy(b, 0, arr, al, bl);
                        return arr;
                    }).orElseGet(value::getFirst);
                }
                default -> {
                    throw new AssertionError();
                }
            }
        }

        private static Object asArray(Object v) {
            final var arr = Array.newInstance(v.getClass(), 1);
            Array.set(arr, 0, v);
            return arr;
        }

        @SuppressWarnings("unchecked")
        private static List<String> optionValues(OptionSet optionSet, OptionName optionName) {
            return (List<String>)optionSet.valuesOf(optionName.name());
        }


        private record OptionSpecWithValue<T>(OptionSpec<T> optionSpec, Optional<String> optionValue) {

            OptionSpecWithValue {
                Objects.requireNonNull(optionSpec);
                Objects.requireNonNull(optionValue);

                if (optionSpec.names().size() != 1) {
                    throw new IllegalArgumentException();
                }

                if (optionValue.isEmpty() && optionSpec.hasValue() && !optionSpec.isValueOptional()) {
                    throw new IllegalArgumentException();
                }
            }

            OptionName optionName() {
                return optionSpec.name();
            }

            Result<T> convertValue() {
                final var converter = optionSpec.converter().orElseThrow();

                final Result<T> conversionResult = optionValue.map(v -> {
                    return convertString(converter, optionName(), StringToken.of(v));
                }).orElseGet(() -> {
                    return Result.ofValue(optionSpec.defaultOptionalValue().orElseThrow());
                });

                if (conversionResult.hasErrors()) {
                    final var arrConverter = optionSpec.arrayValueConverter().orElse(null);

                    if (arrConverter != null && optionSpec.mergePolicy() != MergePolicy.CONCATENATE) {
                        // Maybe recoverable array conversion error
                        final var tokens = arrConverter.tokenize(optionValue.orElseThrow());
                        final String str = getOptionValue(List.of(tokens), optionSpec.mergePolicy()).getFirst();
                        final String[] token = arrConverter.tokenize(str);
                        if (token.length == 1 && str.equals(token[0])) {
                            final var singleTokenConversionResult = convertString(converter, optionName(), StringToken.of(str));
                            if (singleTokenConversionResult.hasValue()) {
                                return singleTokenConversionResult;
                            }
                        }
                    }
                }

                return conversionResult;
            }

            static List<? extends OptionSpecWithValue<?>> create(OptionSet optionSet,
                    OptionSet mergerOptionSet, Map<OptionIdentifier, ? extends OptionSpec<?>> optionMap) {

                Objects.requireNonNull(optionSet);
                Objects.requireNonNull(mergerOptionSet);
                Objects.requireNonNull(optionMap);

                final var optionsWithOptionalValues = optionsWithOptionalValues(mergerOptionSet);

                final var nameToOptionValues = optionMap.values().stream().map(optionSpec -> {
                    return optionSpec.names().stream();
                }).flatMap(x -> x).collect(toUnmodifiableMap(OptionName::name, optionName -> {
                    if (optionSet.has(optionName.name())) {
                        return Optional.ofNullable(optionsWithOptionalValues.get(optionName)).orElseGet(() -> {
                            return optionValues(optionSet, optionName).iterator();
                        });
                    } else {
                        return List.<String>of().iterator();
                    }
                }));

                final var nameToOptionSpec = optionMap.values().stream().map(spec -> {
                    return spec.names().stream().map(name -> {
                        return Map.entry(name.name(), spec.copyWithName(name));
                    });
                }).flatMap(x -> x).collect(toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

                final var nameGroups = optionMap.values().stream().map(optionSpec -> {
                    return optionSpec.names().stream().map(optionName -> {
                        return Map.entry(optionName, optionSpec.names());
                    });
                }).flatMap(x -> x).collect(toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

                // Iterate option values in the order they appear on the command line.
                // OptionSet.specs() guarantees such order of option values.
                try {
                    return filterUnusedValues(nameGroups, optionSet.specs().stream().map(joptSpec -> {
                        var optionNames = joptSpec.options();
                        if (optionNames.size() != 1) {
                            throw new AssertionError();
                        }
                        return optionNames.getFirst();
                    }).filter(nameToOptionSpec::containsKey).map(name -> {
                        var optionSpec = nameToOptionSpec.get(name);
                        String optionValue;
                        if (!optionSpec.hasValue()) {
                            optionValue = null;
                        } else {
                            var it = nameToOptionValues.get(name);
                            if (it.hasNext()) {
                                optionValue = it.next();
                            } else if (optionSpec.isValueOptional()) {
                                return null;
                            } else {
                                throw new AssertionError();
                            }
                        }
                        OptionSpecWithValue<?> oswv = new OptionSpecWithValue<>(optionSpec, Optional.ofNullable(optionValue));
                        return oswv;
                    }).filter(Objects::nonNull).toList());
                } finally {
                    if (nameToOptionValues.values().stream().anyMatch(Iterator::hasNext)) {
                        throw new AssertionError("Unfetched option values detected");
                    }
                }
            }

            private static Map<OptionName, Iterator<String>> optionsWithOptionalValues(OptionSet optionSet) {
                Objects.requireNonNull(optionSet);
                return optionSet.specs().stream().distinct().filter(joptSpec -> {
                    if (joptSpec instanceof ArgumentAcceptingOptionSpec<?> v) {
                        return !v.requiresArgument();
                    } else {
                        return false;
                    }
                }).map(joptSpec -> {
                    @SuppressWarnings("unchecked")
                    List<String> optionValues = ((List<String>)optionSet.valuesOf(joptSpec));
                    if (optionValues.isEmpty()) {
                        // `joptSpec` is detected on the command line without a value.
                        optionValues = Arrays.asList((String)null);
                    }
                    final var it = optionValues.iterator();
                    return joptSpec.options().stream().map(OptionName::of).map(optionName -> {
                        return Map.entry(optionName, it);
                    });
                }).flatMap(x -> x).collect(toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
            }

            private static List<? extends OptionSpecWithValue<?>> filterUnusedValues(
                    Map<OptionName, List<OptionName>> nameGroups, List<? extends OptionSpecWithValue<?>> items) {

                items = filterFirstValues(nameGroups, items.reversed(), OptionSpec.MergePolicy.USE_LAST);
                items = filterFirstValues(nameGroups, items.reversed(), OptionSpec.MergePolicy.USE_FIRST);

                return items;
            }

            private static List<? extends OptionSpecWithValue<?>> filterFirstValues(
                    Map<OptionName, List<OptionName>> nameGroups,
                    List<? extends OptionSpecWithValue<?>> items,
                    OptionSpec.MergePolicy mergePolicy) {

                Objects.requireNonNull(nameGroups);
                Objects.requireNonNull(items);
                Objects.requireNonNull(mergePolicy);

                Set<OptionName> encounteredNames = new HashSet<>();

                return items.stream().filter(item -> {
                    if (item.optionSpec().mergePolicy() != mergePolicy) {
                        return true;
                    } else if (!encounteredNames.contains(item.optionSpec().name())) {
                        encounteredNames.addAll(Objects.requireNonNull(nameGroups.get(item.optionSpec().name())));
                        return true;
                    } else {
                        return false;
                    }
                }).toList();
            }
        }

        private final OptionSet optionSet;
        private final OptionSet mergerOptionSet;
        private final Map<OptionIdentifier, ? extends OptionSpec<?>> optionMap;
        private final Set<OptionName> optionNames;
    }


    private static final class TypedOptions implements ExtendedOptions<TypedOptions> {

        TypedOptions(
                DefaultOptions options,
                List<OptionName> detectedOptions,
                List<String> nonOptionArguments) {

            this.options = Objects.requireNonNull(options);
            this.nonOptionArguments = Objects.requireNonNull(nonOptionArguments);
            this.detectedOptions = Objects.requireNonNull(detectedOptions);
            assertNoUnexpectedOptionNames(options.withOptionIdentifierSet(), detectedOptions);
        }

        @Override
        public TypedOptions copyWithout(Iterable<? extends OptionIdentifier> ids) {
            var newOptions = options.copyWithout(ids);
            var newDetectedOptions = detectedOptions.stream().filter(newOptions::contains).toList();
            return new TypedOptions(newOptions, newDetectedOptions, nonOptionArguments);
        }

        @Override
        public List<String> nonOptionArguments() {
            return nonOptionArguments;
        }

        @Override
        public List<OptionName> detectedOptions() {
            return detectedOptions;
        }

        @Override
        public Set<? extends OptionIdentifier> ids() {
            return options.ids();
        }

        @Override
        public Optional<Object> find(OptionIdentifier id) {
            return options.find(id);
        }

        @Override
        public boolean contains(OptionName optionName) {
            return options.contains(optionName);
        }

        @Override
        public DefaultOptions toDefaultOptions() {
            return options;
        }

        private final DefaultOptions options;
        private final List<String> nonOptionArguments;
        private final List<OptionName> detectedOptions;
    }


    private static void assertNoUnexpectedOptionNames(Set<? extends WithOptionIdentifier> options, Collection<OptionName> optionNames) {
        final var allowedOptionNames = options.stream()
                .map(Option.class::cast)
                .map(Option::spec)
                .map(OptionSpec::names)
                .flatMap(Collection::stream)
                .toList();
        if (!allowedOptionNames.containsAll(optionNames)) {
            final var diff = new HashSet<>(optionNames);
            diff.removeAll(allowedOptionNames);
            throw new AssertionError(String.format("Unexpected option names: %s", diff.stream().map(OptionName::name).sorted().toList()));
        }
    }

    private static <T> List<T> getOptionValue(List<T> values, OptionSpec.MergePolicy mergePolicy) {
        Objects.requireNonNull(mergePolicy);
        if (values.size() == 1) {
            return values;
        } else {
            switch (mergePolicy) {
                case USE_LAST -> {
                    return List.of(values.getLast());
                }
                case USE_FIRST -> {
                    return List.of(values.getFirst());
                }
                case CONCATENATE -> {
                    return values;
                }
                default -> {
                    throw new AssertionError();
                }
            }
        }
    }

    private Collection<Option> options = new ArrayList<>();
    private UnaryOperator<OptionSpec<?>> optionSpecMapper;
    private Function<JOptSimpleError, ? extends Exception> jOptSimpleParserErrorHandler;
}
