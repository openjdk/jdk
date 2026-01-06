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

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static jdk.jpackage.internal.cli.Option.fromOptionSpecPredicate;
import static jdk.jpackage.internal.cli.StandardOption.ADDITIONAL_LAUNCHERS;
import static jdk.jpackage.internal.cli.StandardOption.platformOption;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.cli.JOptSimpleOptionsBuilder.ConvertedOptionsBuilder;
import jdk.jpackage.internal.cli.JOptSimpleOptionsBuilder.OptionsBuilder;
import jdk.jpackage.internal.model.BundlingEnvironment;
import jdk.jpackage.internal.model.BundlingOperationDescriptor;
import jdk.jpackage.internal.model.JPackageException;
import jdk.jpackage.internal.util.Result;

/**
 * Processes jpackage command line.
 */
final class OptionsProcessor {

    OptionsProcessor(OptionsBuilder optionsBuilder, CliBundlingEnvironment bundlingEnv) {
        this.optionsBuilder = Objects.requireNonNull(optionsBuilder);
        this.bundlingEnv = Objects.requireNonNull(bundlingEnv);
    }

    record ValidatedOptions(Options options, BundlingOperationDescriptor bundlingOperation) {
        ValidatedOptions {
            Objects.requireNonNull(options);
            Objects.requireNonNull(bundlingOperation);
        }

        String bundleTypeName() {
            return bundlingOperation.bundleType();
        }
    }

    Result<ValidatedOptions> validate() {
        final Collection<Exception> allErrors = new ArrayList<>();

        // Check for non-option arguments.
        validateNonOptionArguments(optionsBuilder).ifPresent(allErrors::add);

        // Parse the command line. The result is Options container of strings.
        final var untypedOptions = optionsBuilder.create();

        // Create command line structure analyzer.
        final var analyzerResult = Result.of(() -> new OptionsAnalyzer(untypedOptions, bundlingEnv));
        if (analyzerResult.hasErrors()) {
            // Failed to derive the bundling operation from the command line.
            allErrors.addAll(analyzerResult.mapErrors().errors());
            return Result.ofErrors(allErrors);
        }

        final var analyzer = analyzerResult.orElseThrow();

        // Validate the bundling operation.
        final var bundlingOperationResult = validateBundlingOperation(analyzer.bundlingOperation()).map(op -> {
            return Map.entry(StandardOption.BUNDLING_OPERATION_DESCRIPTOR, op);
        });

        bundlingOperationResult.peekErrors(allErrors::addAll);

        // Validate command line structure.
        final var structureErrors = analyzer.findErrors();
        if (!structureErrors.isEmpty()) {
            OptionsAnalyzer.orderErrors(
                    optionsBuilder.detectedOptions(),
                    structureErrors
            ).map(err -> err.error()).forEach(allErrors::add);
            return Result.ofErrors(allErrors);
        }

        final Result<ValidatedOptions> validatedOptionsResult = optionsBuilder
                // Command line structure is valid.
                // Run value converters that will convert strings into objects (e.g.: String -> Path)
                .convertedOptions().map(ConvertedOptionsBuilder::create).map(convertedOptions -> {
                    return new ValidatedOptions(convertedOptions, analyzer.bundlingOperation());
                });

        validatedOptionsResult.peekErrors(allErrors::addAll);

        if (validatedOptionsResult.hasErrors()) {
            // There are errors in the command line.
            // Inspect additional launcher names to see if there are duplicates.
            final var addLaunchers = untypedOptions.find(StandardOption.ADD_LAUNCHER_INTERNAL.id())
                    .map(String[].class::cast)
                    .map(Stream::of)
                    .orElseGet(Stream::of).map(addLauncherStr -> {
                        return addLauncherStr.split("=", 2)[0];
                    }).map(addLauncherName -> {
                        return Options.of(Map.of(StandardOption.NAME, addLauncherName));
                    }).toArray(Options[]::new);

            Map<WithOptionIdentifier, Object> options = new HashMap<>();
            options.put(ADD_LAUNCHER_INTERNAL, addLaunchers);
            untypedOptions.find(StandardOption.NAME.id()).ifPresent(strArray -> {
                options.put(StandardOption.NAME, ((String[])strArray)[0]);
            });

            var result = validateAdditionalLaunchers(Options.of(options));
            result.peekErrors(allErrors::addAll);
        }

        final var validatedAddLaunchersResult = validatedOptionsResult.value()
                .map(ValidatedOptions::options)
                .map(this::validateAdditionalLaunchers).map(result -> {
                    return result.map(addLaunchers -> {
                        return Map.entry(ADDITIONAL_LAUNCHERS, addLaunchers);
                    });
                }).orElseGet(() -> {
                    return Result.ofValue(Map.entry(ADDITIONAL_LAUNCHERS, List.of()));
                });

        validatedAddLaunchersResult.peekErrors(allErrors::addAll);

        final var validatedFaResult = Result.ofValue(Map.entry(
                StandardOption.FILE_ASSOCIATIONS,
                validatedOptionsResult.value()
                        .map(ValidatedOptions::options)
                        .map(FILE_ASSOCIATIONS_INTERNAL::getFrom)
                        .orElseGet(List::of)
        ));

        validatedOptionsResult.value().ifPresent(validatedOptions -> {
            // Second pass: analyze command line options with values converted from strings.
            var errors = new OptionsAnalyzer(validatedOptions.options(),
                    StandardBundlingOperation.valueOf(validatedOptions.bundlingOperation()).orElseThrow(() -> {
                        // The bundle operation descriptor should correspond to one of the standard bundling operations.
                        throw new AssertionError();
                    })).findErrors();
            OptionsAnalyzer.orderErrors(
                    optionsBuilder.detectedOptions(),
                    errors
            ).map(err -> err.error()).forEach(allErrors::add);
        });

        if (!allErrors.isEmpty()) {
            return Result.ofErrors(allErrors);
        } else {
            // No errors.
            // Add synthesized option values and return the result.
            final Map<WithOptionIdentifier, Object> extra = Stream.of(
                    bundlingOperationResult,
                    validatedAddLaunchersResult,
                    validatedFaResult
            ).map(Result::orElseThrow).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

            return validatedOptionsResult.map(validatedOptions -> {
                return new ValidatedOptions(
                        Options.concat(
                                Options.of(extra),
                                validatedOptions.options().copyWithout(ADD_LAUNCHER_INTERNAL.id(), FILE_ASSOCIATIONS_INTERNAL.id())),
                        validatedOptions.bundlingOperation());
            });
        }
    }

    private static Optional<? extends Exception> validateNonOptionArguments(OptionsBuilder optionsBuilder) {
        final var nonOptionArguments = optionsBuilder.nonOptionArguments();
        if (nonOptionArguments.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(new JPackageException(I18N.format("error.non-option-arguments", nonOptionArguments.size())));
        }
    }

    private Result<BundlingOperationDescriptor> validateBundlingOperation(BundlingOperationDescriptor bundlingOperation) {
        Objects.requireNonNull(bundlingOperation);
        try {
            var errors = bundlingEnv.configurationErrors(bundlingOperation);
            if (errors.isEmpty()) {
                return Result.ofValue(bundlingOperation);
            } else {
                return Result.ofErrors(errors);
            }
        } catch (NoSuchElementException ex) {
            // Bundling environment doesn't recognize the descriptor of a bundling operation.
            return Result.ofError(new JPackageException(I18N.format("ERR_InvalidInstallerType", bundlingOperation.bundleType())));
        }
    }

    Collection<? extends Exception> runBundling(ValidatedOptions validatedOptions) {
        try {
            bundlingEnv.createBundle(validatedOptions.bundlingOperation(), validatedOptions.options());
            return List.of();
        } catch (Exception ex) {
            return List.of(ex);
        }
    }

    /**
     * Loads property file and processes properties as a command line.
     * <p>
     * Unrecognized options will be silently ignored.
     *
     * @param file             the source property file
     * @param options          the recognized options
     * @param optionSpecMapper optional option spec mapper
     * @return {@link Options} instance containing validated property values or the list
     *         of errors occured during option values processing
     * @throws UncheckedIOException if an I/O error occurs
     */
    static Result<Options> processPropertyFile(Path file, Collection<Option> options,
            Optional<UnaryOperator<OptionSpec<?>>> optionSpecMapper) {
        final var props = new Properties();
        try (var in = Files.newBufferedReader(file)) {
            props.load(in);
        } catch (IOException ex) {
            return Result.ofError(ex);
        }

        // Convert the property file into command line arguments.
        // Silently ignore unknown properties.
        final var args = options.stream().map(option -> {
            return option.spec().names().stream().map(optionName -> {
                return Optional.ofNullable(props.getProperty(optionName.name())).map(stringOptionValue -> {
                    return Stream.of(optionName.formatForCommandLine(), stringOptionValue);
                }).orElse(null);
            }).flatMap(x -> x);
        }).flatMap(x -> x).filter(Objects::nonNull).toArray(String[]::new);

        // Feed the contents of the property file as a command line arguments to the command line parser.
        final var builder = new JOptSimpleOptionsBuilder().options(options);

        optionSpecMapper.ifPresent(builder::optionSpecMapper);

        final var result = builder.create().apply(args)
                .flatMap(OptionsBuilder::convertedOptions)
                .map(ConvertedOptionsBuilder::create);

        return result.map(cmdline -> {
            return cmdline.copyWithDefaultValue(StandardOption.SOURCE_PROPERY_FILE, file);
        });
    }

    static UnaryOperator<OptionSpec<?>> optionSpecMapper(OperatingSystem os, BundlingEnvironment bundlingEnv) {
        Objects.requireNonNull(os);
        Objects.requireNonNull(bundlingEnv);

        var context = new StandardOptionContext(os);

        return optionSpec -> {
            if (optionSpec.name().equals(StandardOption.ADD_LAUNCHER_INTERNAL.getSpec().name())) {
                final var options = filterForPlatform(os, StandardOption.launcherOptions());
                return optionSpec.copyWithConverter(new OptionsConverter<>(addLauncher -> {
                    var localContext = context.forFile(addLauncher.propertyFile());
                    var optionValues = processPropertyFile(addLauncher.propertyFile(), options, Optional.of(localContext::mapOptionSpec));
                    return optionValues.map(o -> {
                        return o.copyWithParent(Options.of(Map.of(StandardOption.NAME, addLauncher.name())));
                    });
                }, StandardOption.ADD_LAUNCHER_INTERNAL.getSpec()));
            } else if (optionSpec.name().equals(StandardOption.FILE_ASSOCIATIONS_INTERNAL.getSpec().name())) {
                final var options = filterForPlatform(os, StandardFaOption.options());
                return optionSpec.copyWithConverter(new OptionsConverter<>(fa -> {
                    var localContext = context.forFile(fa);
                    return processPropertyFile(fa, options, Optional.of(localContext::mapOptionSpec));
                }, StandardOption.FILE_ASSOCIATIONS_INTERNAL.getSpec()));
            } else {
                return context.mapOptionSpec(optionSpec);
            }
        };
    }

    private Result<List<Options>> validateAdditionalLaunchers(Options cmdline) {

        final var addLaunchers = ADD_LAUNCHER_INTERNAL.getFrom(cmdline);

        final List<Exception> errors = new ArrayList<>();

        // Count launcher names.
        final var names = Stream.concat(
                StandardOption.NAME.findIn(cmdline).stream(),
                addLaunchers.stream().map(StandardOption.NAME::getFrom)
        ).collect(groupingBy(x -> x, counting()));

        // Sort duplicated names alphabetically
        names.entrySet().stream().filter(e -> {
            // Filter duplicated names.
            return e.getValue() > 1;
        }).map(Map.Entry::getKey).sorted().map(name -> {
            return new JPackageException(I18N.format("error.launcher-duplicate-name", name));
        }).forEach(errors::add);

        if (!errors.isEmpty()) {
            return Result.ofErrors(errors);
        } else {
            return Result.ofValue(addLaunchers.stream().map(addLauncherOptionValues -> {

                //
                // For additional launcher:
                //  - Override name.
                //  - Ignore icon configured for the app/main launcher.
                //  - Ignore shortcuts configured for the app/main launcher.
                //  - If the additional launcher is modular, delete non-modular options of the main launcher.
                //  - If the additional launcher is non-modular, delete modular options of the main launcher.
                //  - Combine other option values with the main option values.
                //

                List<OptionValue<?>> excludes = new ArrayList<>();
                excludes.add(StandardOption.ICON);
                excludes.add(StandardOption.LINUX_SHORTCUT_HINT);
                excludes.add(StandardOption.WIN_MENU_HINT);
                excludes.add(StandardOption.WIN_SHORTCUT_HINT);
                if (StandardOption.MODULE.containsIn(addLauncherOptionValues)) {
                    excludes.add(StandardOption.MAIN_JAR);
                    excludes.add(StandardOption.APPCLASS);
                }
                if (StandardOption.MAIN_JAR.containsIn(addLauncherOptionValues)) {
                    excludes.add(StandardOption.MODULE);
                }

                return Options.concat(
                        addLauncherOptionValues,
                        cmdline.copyWithout(excludes.stream().map(WithOptionIdentifier::id).toList()));
            }).toList());
        }
    }

    private static Collection<Option> filterForPlatform(OperatingSystem os, Collection<Option> options) {
        return options.stream().filter(fromOptionSpecPredicate(platformOption(os))).toList();
    }


    private static final class OptionsConverter<T> implements OptionArrayValueConverter<Options> {

        OptionsConverter(Function<T, Result<Options>> mapper, OptionSpec<T[]> optionSpec) {
            this(mapper, optionSpec.<T>arrayValueConverter().orElseThrow());
        }

        OptionsConverter(Function<T, Result<Options>> mapper, OptionArrayValueConverter<T> converter) {
            this.mapper = Objects.requireNonNull(mapper);
            this.converter = Objects.requireNonNull(converter);
        }

        static <T> OptionValue<List<Options>> optionValue(OptionIdentifier id) {
            return OptionValue.<Options[]>build().id(id).to(List::of).defaultValue(List.of()).create();
        }

        @Override
        public Result<Options[]> convert(OptionName optionName, StringToken optionValue) {
            return converter.convert(optionName, optionValue).flatMap(arr -> {
                return Stream.of(arr).map(mapper).reduce(Result.<List<Options>>ofValue(new ArrayList<>()), (result, o) -> {
                    if (Result.allHaveValues(result, o)) {
                        return result.map(v -> {
                            v.add(o.orElseThrow());
                            return v;
                        });
                    } else {
                        return o.mapErrors();
                    }
                }, (x, y) -> {
                    var errors = Stream.of(x, y).map(Result::errors).flatMap(Collection::stream).toList();
                    if (errors.isEmpty()) {
                        return Result.ofValue(Stream.of(x, y).map(Result::orElseThrow).flatMap(Collection::stream).toList());
                    } else {
                        return Result.ofErrors(errors);
                    }
                }).map(v -> v.toArray(Options[]::new));
            });
        }

        @Override
        public Class<Options[]> valueType() {
            return Options[].class;
        }

        @Override
        public String[] tokenize(String str) {
            return converter.tokenize(str);
        }

        private final OptionArrayValueConverter<T> converter;
        private final Function<T, Result<Options>> mapper;
    }


    private final JOptSimpleOptionsBuilder.OptionsBuilder optionsBuilder;
    private final CliBundlingEnvironment bundlingEnv;

    private static final OptionValue<List<Options>> ADD_LAUNCHER_INTERNAL =
            OptionsConverter.optionValue(StandardOption.ADD_LAUNCHER_INTERNAL.id());

    private static final OptionValue<List<Options>> FILE_ASSOCIATIONS_INTERNAL =
            OptionsConverter.optionValue(StandardOption.FILE_ASSOCIATIONS_INTERNAL.id());
}
