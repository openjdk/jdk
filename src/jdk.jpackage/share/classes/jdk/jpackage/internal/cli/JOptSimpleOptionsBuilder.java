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

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jdk.internal.joptsimple.ArgumentAcceptingOptionSpec;
import jdk.internal.joptsimple.OptionParser;
import jdk.internal.joptsimple.OptionSet;
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

    JOptSimpleOptionsBuilder options(Collection<Option> v) {
        options.addAll(v);
        return this;
    }

    JOptSimpleOptionsBuilder options(Option... v) {
        return options(List.of(v));
    }

    JOptSimpleOptionsBuilder optionValues(Collection<? extends OptionValue<?>> v) {
        return options(Optional.ofNullable(v).map(x -> {
            return x.stream().map(OptionValue::getOption).toList();
        }).orElse((List<Option>)null));
    }

    JOptSimpleOptionsBuilder optionValues(OptionValue<?>... v) {
        return optionValues(List.of(v));
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

        private ConvertedOptionsBuilder(RedirectedOptions<TypedOptions> redirected) {
            impl = Objects.requireNonNull(redirected);
        }

        Options create() {
            return impl;
        }

        ConvertedOptionsBuilder copyWithExcludes(Collection<Option> v) {
            return new ConvertedOptionsBuilder(impl.copyWithExcludes(v));
        }

        List<String> nonOptionArguments() {
            return impl.nonOptionArguments();
        }

        List<OptionName> detectedOptions() {
            return impl.detectedOptions();
        }

        private final RedirectedOptions<TypedOptions> impl;
    }


    static final class OptionsBuilder {

        private OptionsBuilder(RedirectedOptions<UntypedOptions> redirected) {
            impl = Objects.requireNonNull(redirected);
        }

        Result<ConvertedOptionsBuilder> convertedOptions() {
            return impl.options().toTypedOptions().map(typedOptions -> {
                return new RedirectedOptions<>(typedOptions, impl.redirects());
            }).map(ConvertedOptionsBuilder::new);
        }

        Options create() {
            return impl;
        }

        OptionsBuilder copyWithExcludes(Collection<Option> v) {
            return new OptionsBuilder(impl.copyWithExcludes(v));
        }

        List<String> nonOptionArguments() {
            return impl.nonOptionArguments();
        }

        List<OptionName> detectedOptions() {
            return impl.detectedOptions();
        }

        private final RedirectedOptions<UntypedOptions> impl;
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
            Map<Option, List<? extends OptionSpec<?>>> optionMap,
            Map<Option, Option> redirects,
            Optional<Function<JOptSimpleError, ? extends Exception>> jOptSimpleParserErrorHandler) {

        private JOptSimpleParser {
            Objects.requireNonNull(parser);
            Objects.requireNonNull(optionMap);
            Objects.requireNonNull(redirects);
            Objects.requireNonNull(jOptSimpleParserErrorHandler);
        }

        Result<OptionsBuilder> parse(String... args) {
            return applyParser(parser, args).map(optionSet -> {
                final OptionSet mergerOptionSet;
                if (optionMap.values().stream().allMatch(list -> list.size() == 1)) {
                    // No specs with multiple names, merger not needed.
                    mergerOptionSet = optionSet;
                } else {
                    final var parser2 = createOptionParser();
                    final var optionSpecApplier = new OptionSpecApplier();
                    for (final var option : optionMap.keySet()) {
                        optionSpecApplier.applyToParser(parser2, option.getSpec());
                    }

                    mergerOptionSet = parser2.parse(args);
                }
                return new OptionsBuilder(new RedirectedOptions<>(
                        new UntypedOptions(optionSet, mergerOptionSet, optionMap), redirects));
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

            final Map<Option, Option> redirects;
            final Map<Option, List<? extends OptionSpec<?>>> optionMap;

            if (optionSpecMapper.isEmpty()) {
                redirects = Map.of();
                optionMap = optionStream.collect(toMap(x -> x, option -> {
                    return optionSpecApplier.applyToParser(parser, option.getSpec());
                }));
            } else {
                redirects = optionStream.collect(toMap(x -> x, option -> {
                    return Option.create(getMappedOptionSpec(option, optionSpecMapper.orElseThrow()));
                }));
                optionMap = redirects.values().stream().collect(toMap(x -> x, option -> {
                    return optionSpecApplier.applyToParser(parser, option.getSpec());
                }));
            }

            return new JOptSimpleParser(parser, optionMap, redirects, jOptSimpleParserErrorHandler);
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

        private static <T> OptionSpec<?> getMappedOptionSpec(Option option, UnaryOperator<OptionSpec<?>> optionSpecMapper) {
            Objects.requireNonNull(optionSpecMapper);
            @SuppressWarnings("unchecked")
            final OptionSpec<T> optionSpec = (OptionSpec<T>)option.getSpec();
            return optionSpecMapper.apply(optionSpec);
        }
    }


    private static final class OptionSpecApplier {

        <T> List<OptionSpec<T>> applyToParser(OptionParser parser, OptionSpec<T> spec) {
            final Stream<OptionSpec<T>> optionSpecs;
            if (generateForEveryName) {
                optionSpecs = spec.copyForEveryName();
            } else {
                optionSpecs = Stream.of(spec);
            }
            return optionSpecs.peek(v -> {
                final var specBuilder = parser.acceptsAll(v.names().stream().map(OptionName::name).toList());
                if (v.hasValue()) {
                    if (v.isValueOptional()) {
                        specBuilder.withOptionalArg();
                    } else {
                        specBuilder.withRequiredArg();
                    }
                }
            }).toList();
        }

        OptionSpecApplier generateForEveryName(boolean v) {
            generateForEveryName = v;
            return this;
        }

        private boolean generateForEveryName;
    }


    sealed interface ExtendedOptions<T extends ExtendedOptions<T>> extends Options {
        T copyWithExcludes(Collection<Option> excludes);
        List<String> nonOptionArguments();
        List<OptionName> detectedOptions();
    }


    private record RedirectedOptions<T extends ExtendedOptions<T>>(T options,
            Map<Option, Option> redirects) implements ExtendedOptions<RedirectedOptions<T>> {

        RedirectedOptions {
            Objects.requireNonNull(options);
            Objects.requireNonNull(redirects);

            if (!redirects.isEmpty()) {
                var detectedOptions = options.ids();

                if (redirects.size() > detectedOptions.size()) {
                    // Trim excessive redirects
                    redirects = redirects.entrySet().stream().filter(e -> {
                        return detectedOptions.contains(e.getValue());
                    }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                }

                if (!redirects.values().containsAll(detectedOptions)) {
                    throw new IllegalArgumentException();
                }
            }

            for (var r : redirects.entrySet()) {
                var from = r.getKey().getSpec();
                var to = r.getValue().getSpec();
                if (!from.names().equals(to.names())) {
                    throw new IllegalArgumentException();
                }
            }
        }

        @Override
        public List<String> nonOptionArguments() {
            return options.nonOptionArguments();
        }

        @Override
        public List<OptionName> detectedOptions() {
            return options.detectedOptions();
        }

        @Override
        public Optional<Object> find(OptionIdentifier id) {
            if (redirects.isEmpty()) {
                return options.find(id);
            } else {
                return Optional.ofNullable(redirects.get(id)).flatMap(options::find);
            }
        }

        @Override
        public boolean contains(OptionName optionName) {
            return options.contains(optionName);
        }

        @Override
        public RedirectedOptions<T> copyWithExcludes(Collection<Option> excludes) {
            if (redirects.isEmpty()) {
                return new RedirectedOptions<>(options.copyWithExcludes(excludes), redirects);
            } else {
                var redirectedExcludes = redirects.entrySet().stream().filter(e -> {
                    return excludes.contains(e.getKey());
                }).map(Map.Entry::getValue).toList();

                Map<Option, Option> newRedirects = new HashMap<>(redirects);
                excludes.forEach(newRedirects::remove);
                return new RedirectedOptions<>(options.copyWithExcludes(redirectedExcludes), newRedirects);
            }
        }

        @Override
        public Set<? extends OptionIdentifier> ids() {
            if (!redirects.isEmpty()) {
                return redirects.keySet();
            } else {
                return options.ids();
            }
        }
    }


    private static final class UntypedOptions implements ExtendedOptions<UntypedOptions> {

        UntypedOptions(OptionSet optionSet, OptionSet mergerOptionSet, Map<Option, List<? extends OptionSpec<?>>> optionMap) {
            this.optionSet = Objects.requireNonNull(optionSet);
            this.mergerOptionSet = Objects.requireNonNull(mergerOptionSet);
            optionNames = optionMap.keySet().stream().map(Option::getSpec).map(OptionSpec::names).flatMap(Collection::stream).filter(optionName -> {
                return optionSet.has(optionName.name());
            }).collect(toSet());
            this.optionMap = optionMap.entrySet().stream().filter(e -> {
                return !Collections.disjoint(optionNames, e.getKey().getSpec().names());
            }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            assertNoUnexpectedOptionNames(optionMap, optionNames);
        }

        private UntypedOptions(UntypedOptions other, Collection<Option> excludes) {
            this(other.optionSet, other.mergerOptionSet, other.optionMap.entrySet().stream().filter(e -> {
                return !excludes.contains(e.getKey());
            }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }

        @Override
        public UntypedOptions copyWithExcludes(Collection<Option> excludes) {
            return new UntypedOptions(this, excludes);
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

        Result<TypedOptions> toTypedOptions() {

            final List<Exception> errors = new ArrayList<>();

            final var optionNameToOption = optionMap.keySet().stream().map(option -> {
                return option.getSpec().names().stream().map(optionName -> {
                    return Map.entry(optionName, option);
                });
            }).flatMap(x -> x).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            var entries = OptionSpecWithValue.create(optionSet, mergerOptionSet, optionMap).stream().map(oswv -> {
                var option = optionNameToOption.get(oswv.optionName());
                if (!oswv.optionSpec().hasValue()) {
                    return Map.entry(option, Boolean.TRUE);
                } else {
                    return oswv.convertValue().map(v -> {
                        if (oswv.optionSpec().arrayValueConverter().isEmpty()) {
                            return Map.entry(option, v);
                        } else {
                            return Map.entry(option, new ArrayList<>(List.of(v)));
                        }
                    }).peekErrors(errors::addAll).value().orElse(null);
                }
            }).filter(Objects::nonNull).toList();

            if (!errors.isEmpty()) {
                return Result.ofErrors(errors);
            } else {
                final Map<Option, Object> value = new HashMap<>();

                for (var e : entries) {
                    var option = e.getKey();
                    value.merge(option, e.getValue(), (a, b) -> {
                        if (option.getSpec().arrayValueConverter().isEmpty()) {
                            throw new AssertionError();
                        } else {
                            @SuppressWarnings("unchecked")
                            var ar = ((List<Object>)a);

                            @SuppressWarnings("unchecked")
                            var br = ((List<Object>)b);

                            ar.addAll(br);
                            return ar;
                        }
                    });
                }

                for (var e : value.entrySet()) {
                    var option = e.getKey();
                    if (option.getSpec().arrayValueConverter().isPresent()) {
                        var arr = mergeArrayValues(option.getSpec().mergePolicy(), (List<?>)e.getValue());
                        value.put(e.getKey(), arr);
                    }
                }

                return Result.ofValue(new TypedOptions(value, optionNames, detectedOptions(), nonOptionArguments()));
            }
        }

        @Override
        public Optional<Object> find(OptionIdentifier id) {
            return findValues(id).map(value -> {
                final var option = (Option)id;
                final var optionSpec = option.getSpec();
                if (optionSpec.hasValue()) {
                    return value.toArray(String[]::new);
                } else {
                    return new String[0];
                }
            });
        }

        @Override
        public boolean contains(OptionName optionName) {
            return optionNames.contains(optionName);
        }

        private Optional<List<String>> findValues(OptionIdentifier id) {
            Objects.requireNonNull(id);
            if (optionMap.containsKey(id)) {
                return Optional.of(getValues(((Option)id).getSpec()));
            } else {
                return Optional.empty();
            }
        }

        private List<String> getValues(OptionSpec<?> mainSpec) {
            Objects.requireNonNull(mainSpec);

            final var values = optionValues(mergerOptionSet, mainSpec.name());

            if (!values.isEmpty()) {
                return getOptionValue(values, mainSpec.mergePolicy());
            } else if (mainSpec.names().stream().anyMatch(this::contains)) {
                // The spec belongs to an option detected on the command line.
                if (!mainSpec.hasValue()) {
                    // The spec belongs to an option without a value.
                    return List.of();
                } else if (mainSpec.isValueOptional()) {
                    // The spec belongs to an option with an optional value.
                    return List.of(mainSpec.name().formatForCommandLine());
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
                    return converter.convert(optionName(), StringToken.of(v));
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
                            final var singleTokenConversionResult = converter.convert(optionName(), StringToken.of(str));
                            if (singleTokenConversionResult.hasValue()) {
                                return singleTokenConversionResult;
                            }
                        }
                    }
                }

                return conversionResult;
            }

            static List<? extends OptionSpecWithValue<?>> create(OptionSet optionSet,
                    OptionSet mergerOptionSet, Map<Option, List<? extends OptionSpec<?>>> optionMap) {

                Objects.requireNonNull(optionSet);
                Objects.requireNonNull(mergerOptionSet);
                Objects.requireNonNull(optionMap);

                final var optionsWithOptionalValues = optionsWithOptionalValues(mergerOptionSet);

                final var nameToOptionValues = optionMap.keySet().stream().map(Option::getSpec).map(optionSpec -> {
                    return optionSpec.names().stream();
                }).flatMap(x -> x).collect(Collectors.toMap(OptionName::name, optionName -> {
                    if (optionSet.has(optionName.name())) {
                        return Optional.ofNullable(optionsWithOptionalValues.get(optionName)).orElseGet(() -> {
                            return optionValues(optionSet, optionName).iterator();
                        });
                    } else {
                        return List.<String>of().iterator();
                    }
                }));

                final var nameToOptionSpec = optionMap.values().stream().flatMap(Collection::stream).map(spec -> {
                    return spec.names().stream().map(name -> {
                        return Map.entry(name.name(), spec);
                    });
                }).flatMap(x -> x).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                final var nameGroups = optionMap.entrySet().stream().map(e -> {
                    return e.getValue().stream().map(optionSpec -> {
                        return Map.entry(optionSpec.name(), e.getKey().getSpec().names());
                    });
                }).flatMap(x -> x).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

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
                }).flatMap(x -> x).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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
        private final Map<Option, List<? extends OptionSpec<?>>> optionMap;
        private final Set<OptionName> optionNames;
    }


    private static final class TypedOptions implements ExtendedOptions<TypedOptions> {

        TypedOptions(
                Map<Option, Object> values,
                Set<OptionName> optionNames,
                List<OptionName> detectedOptions,
                List<String> nonOptionArguments) {

            this.values = Objects.requireNonNull(values);
            this.optionNames = Objects.requireNonNull(optionNames);
            this.nonOptionArguments = Objects.requireNonNull(nonOptionArguments);
            this.detectedOptions = Objects.requireNonNull(detectedOptions);
            assertNoUnexpectedOptionNames(values, optionNames);
            assertNoUnexpectedOptionNames(values, detectedOptions);
        }

        private TypedOptions(TypedOptions other, Collection<Option> excludes) {
            this(other.values.entrySet().stream().filter(e -> {
                return !excludes.contains(e.getKey());
            }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
                    applyExcludes(other.optionNames, excludes, HashSet::new),
                    applyExcludes(other.detectedOptions, excludes, ArrayList::new),
                    other.nonOptionArguments());
        }

        @Override
        public TypedOptions copyWithExcludes(Collection<Option> excludes) {
            return new TypedOptions(this, excludes);
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
            return values.keySet();
        }

        @Override
        public Optional<Object> find(OptionIdentifier id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public boolean contains(OptionName optionName) {
            return optionNames.contains(optionName);
        }

        private final Map<Option, Object> values;
        private final Set<OptionName> optionNames;
        private final List<String> nonOptionArguments;
        private final List<OptionName> detectedOptions;
    }


    private static <T extends Collection<OptionName>> T applyExcludes(
            T optionNames, Collection<Option> excludes, UnaryOperator<T> copyFactory) {

        final var newOptionNames = copyFactory.apply(optionNames);
        excludes.stream().map(Option::getSpec).map(OptionSpec::names).forEach(newOptionNames::removeAll);
        return newOptionNames;
    }

    private static void assertNoUnexpectedOptionNames(Map<Option, ?> optionMap, Collection<OptionName> optionNames) {
        final var allowedOptionNames = optionMap.keySet().stream()
                .map(Option::getSpec)
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
