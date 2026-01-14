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

import static jdk.jpackage.internal.cli.StandardOption.ADD_MODULES;
import static jdk.jpackage.internal.cli.StandardOption.INPUT;
import static jdk.jpackage.internal.cli.StandardOption.JLINK_OPTIONS;
import static jdk.jpackage.internal.cli.StandardOption.MAC_APP_IMAGE_SIGN_IDENTITY;
import static jdk.jpackage.internal.cli.StandardOption.MAC_APP_STORE;
import static jdk.jpackage.internal.cli.StandardOption.MAC_INSTALLER_SIGN_IDENTITY;
import static jdk.jpackage.internal.cli.StandardOption.MAC_SIGN;
import static jdk.jpackage.internal.cli.StandardOption.MAC_SIGNING_KEY_NAME;
import static jdk.jpackage.internal.cli.StandardOption.MAIN_JAR;
import static jdk.jpackage.internal.cli.StandardOption.MODULE;
import static jdk.jpackage.internal.cli.StandardOption.MODULE_PATH;
import static jdk.jpackage.internal.cli.StandardOption.PREDEFINED_APP_IMAGE;
import static jdk.jpackage.internal.cli.StandardOption.PREDEFINED_RUNTIME_IMAGE;
import static jdk.jpackage.internal.cli.StandardOption.TYPE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.model.BundlingEnvironment;
import jdk.jpackage.internal.model.BundlingOperationDescriptor;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.JPackageException;
import jdk.jpackage.internal.model.BundleType;

/**
 * Analyzes jpackage command line structure.
 */
final class OptionsAnalyzer {

    OptionsAnalyzer(Options cmdline, BundlingEnvironment bundlingEnv) {
        this(cmdline, getBundlingOperation(cmdline, OperatingSystem.current(), bundlingEnv), false);
    }

    OptionsAnalyzer(Options cmdline, StandardBundlingOperation bundlingOperation) {
        this(cmdline, bundlingOperation, true);
    }

    private OptionsAnalyzer(Options cmdline, StandardBundlingOperation bundlingOperation, boolean typedOptions) {
        this.cmdline = Objects.requireNonNull(cmdline);
        this.bundlingOperation = Objects.requireNonNull(bundlingOperation);
        this.typedOptions = typedOptions;
        hasAppImage = PREDEFINED_APP_IMAGE.containsIn(cmdline);
        isRuntimeInstaller = isRuntimeInstaller(cmdline, bundlingOperation);
    }

    BundlingOperationDescriptor bundlingOperation() {
        return bundlingOperation.descriptor();
    }

    List<ExceptionWithOrigin> findErrors() {
        if (hasAppImage && PREDEFINED_RUNTIME_IMAGE.containsIn(cmdline)) {
            // Short circuit this erroneous case as bundling operation is ambiguous.
            return List.of(new MutualExclusiveOptions(asOptionList(
                    PREDEFINED_RUNTIME_IMAGE, PREDEFINED_APP_IMAGE)).validate(cmdline).orElseThrow());
        }

        final List<ExceptionWithOrigin> errors = new ArrayList<>();

        StandardOption.options().stream()
                .filter(cmdline::contains)
                .map(Option::spec)
                .filter(matchInScope(bundlingOperation).and(matchInScope(bundlingOperationModifiers())).negate())
                .map(optionSpec -> {
                    var err = onOutOfScopeOption(optionSpec);
                    return errorWithOrigin(err, optionSpec);
                }).forEach(errors::add);

        MUTUAL_EXCLUSIVE_OPTIONS.stream().map(v -> {
            return v.validate(cmdline);
        }).filter(Optional::isPresent).map(Optional::orElseThrow).forEach(errors::add);

        if (isBundlingAppImage() && Stream.of(MODULE, MAIN_JAR).noneMatch(ov -> {
            return ov.containsIn(cmdline);
        })) {
            errors.add(errorWithOrigin(error("ERR_NoEntryPoint")));
        }

        if (bundlingOperation == StandardBundlingOperation.SIGN_MAC_APP_IMAGE && !MAC_SIGN.containsIn(cmdline)) {
            errors.add(errorWithOrigin(error("error.app-image.mac-sign.required")));
        }

        if (isBuildingAppImage()) {
            if (MAIN_JAR.containsIn(cmdline) && !INPUT.containsIn(cmdline)) {
                errors.add(errorWithOrigin(error("error.no-input-parameter"), MAIN_JAR.getSpec()));
            }

            if (MODULE.containsIn(cmdline) && Stream.of(PREDEFINED_RUNTIME_IMAGE, MODULE_PATH).noneMatch(ov -> {
                return ov.containsIn(cmdline);
            })) {
                errors.add(errorWithOrigin(error("ERR_MissingArgument2", PREDEFINED_RUNTIME_IMAGE, MODULE_PATH), MODULE.getSpec()));
            }
        }

        if (typedOptions) {
            errors.addAll(findErrorsInTypedOptions());
        }

        return errors;
    }


    record ExceptionWithOrigin(Exception error, List<? extends OptionSpec<?>> origin) {
        ExceptionWithOrigin {
            Objects.requireNonNull(error);
            Objects.requireNonNull(origin);
        }
    }


    static Stream<ExceptionWithOrigin> orderErrors(List<OptionName> optionNames, Collection<ExceptionWithOrigin> unorderedErrors) {
        Objects.requireNonNull(optionNames);
        Objects.requireNonNull(unorderedErrors);
        return unorderedErrors.stream().sorted(Comparator.<ExceptionWithOrigin, Integer>comparing(err -> {
            // Return minimal index of an option name from the origin of this error in the `optionNames` list.
            return err.origin().stream()
                    .map(OptionSpec::names)
                    .flatMap(Collection::stream)
                    .mapToInt(optionNames::indexOf)
                    .filter(idx -> {
                        return idx >= 0;
                    }).min()
                    // Errors without origin go first.
                    .orElse(-1);
        }));
    }

    private List<ExceptionWithOrigin> findErrorsInTypedOptions() {
        final List<ExceptionWithOrigin> errors = new ArrayList<>();

        if (MAC_APP_STORE.containsIn(cmdline)) {
            JLINK_OPTIONS.ifPresentIn(cmdline, jlinkOptions -> {
                if (!jlinkOptions.contains("--strip-native-commands")) {
                    errors.add(errorWithOrigin(
                            error("ERR_MissingJLinkOptMacAppStore", "--strip-native-commands"),
                            Stream.of(MAC_APP_STORE, JLINK_OPTIONS).map(OptionValue::getSpec).toList()));
                }
            });
        }

        return errors;
    }

    private Set<BundlingOperationModifier> bundlingOperationModifiers() {
        final Set<BundlingOperationModifier> modifiers = new HashSet<>();
        if (isBundlingNativePackage()) {
            if (hasAppImage) {
                modifiers.add(BundlingOperationModifier.BUNDLE_PREDEFINED_APP_IMAGE);
            }
            if (isRuntimeInstaller) {
                modifiers.add(BundlingOperationModifier.BUNDLE_RUNTIME);
            }
        }
        return modifiers;
    }

    /**
     * Returns {@code true} if the output of the bundling operation is a new app
     * image.
     *
     * @return {@code true} if the output of the bundling operation is a new app
     *         image
     */
    private boolean isBundlingAppImage() {
        return StandardBundlingOperation.CREATE_APP_IMAGE.contains(bundlingOperation);
    }

    /**
     * Returns {@code true} if the output of the bundling operation is a new native
     * package.
     *
     * @return {@code true} if the output of the bundling operation is a new native
     *         package
     */
    private boolean isBundlingNativePackage() {
        return StandardBundlingOperation.CREATE_NATIVE.contains(bundlingOperation);
    }

    /**
     * Returns {@code true} if the output of the bundling operation is either a new
     * app image or a new native package.
     *
     * @return {@code true} if the output of the bundling operation is either a new
     *         app image or a new native package
     */
    private boolean isBundling() {
        return StandardBundlingOperation.CREATE_BUNDLE.contains(bundlingOperation);
    }

    /**
     * Returns {@code true} if the bundling operation will create an app image as
     * an intermediate or final step.
     *
     * @return {@code true} if the bundling operation will create an app image as
     *         an intermediate or final step
     */
    private boolean isBuildingAppImage() {
        return bundlingOperationModifiers().isEmpty() && isBundling();
    }

    private RuntimeException onOutOfScopeOption(OptionSpec<?> optionSpec) {
        Objects.requireNonNull(optionSpec);

        if (optionSpec.scope().stream()
                .filter(StandardBundlingOperation.class::isInstance)
                .map(StandardBundlingOperation.class::cast)
                .map(StandardBundlingOperation::os).noneMatch(bundlingOperation.os()::equals)) {
            // The option is for different OS.
            return error("ERR_UnsupportedOption", mapFormatArguments(optionSpec));
        } else if (StandardBundlingOperation.SIGN_MAC_APP_IMAGE.equals(bundlingOperation)) {
            // The option is not applicable when signing a predefined app image.
            return error("ERR_InvalidOptionWithAppImageSigning", mapFormatArguments(optionSpec));
        } else if (StandardBundlingOperation.CREATE_NATIVE.contains(bundlingOperation) && isRuntimeInstaller) {
            // The option is not applicable when packaging of a runtime in a native bundle.
            return error("ERR_NoInstallerEntryPoint", mapFormatArguments(optionSpec));
        } else {
            return error("ERR_InvalidTypeOption", mapFormatArguments(
                    optionSpec, bundlingOperation.bundleTypeValue()));
        }
    }

    private Object[] mapFormatArguments(Object... args) {
        return MessageFormatUtils.mapFormatArguments(cmdline, args);
    }

    private static StandardBundlingOperation getBundlingOperation(Options cmdline,
            OperatingSystem os, BundlingEnvironment env) {
        Objects.requireNonNull(cmdline);
        Objects.requireNonNull(os);
        Objects.requireNonNull(env);

        final var typeOption = TYPE.getOption();

        return cmdline.find(typeOption).map(obj -> {
            if (obj instanceof BundleType bundleType) {
                return bundleType;
            } else {
                var spec = new StandardOptionContext(os).mapOptionSpec(typeOption.spec());
                return spec
                        .converter().orElseThrow()
                        .convert(spec.name(), StringToken.of(((String[])obj)[0]))
                        .orElseThrow();
            }
        }).map(bundleType -> {
            // Find standard bundling operations producing the given bundle type.
            var bundlingOperations = Stream.of(StandardBundlingOperation.values()).filter(op -> {
                return op.bundleType().equals(bundleType);
            }).toList();

            if (bundlingOperations.isEmpty()) {
                // jpackage internal error: none of the standard bundling operations produce
                // bundles of the `bundleType`.
                throw new AssertionError(String.format(
                        "None of the standard bundling operations produce bundles of type [%s]",
                        bundleType));
            } else if (bundlingOperations.size() == 1) {
                return bundlingOperations.getFirst();
            } else {
                // Multiple standard bundling operations produce the `bundleType` bundle type.
                // Filter those that belong to the current OS
                bundlingOperations = bundlingOperations.stream().filter(op -> {
                    return op.os().equals(OperatingSystem.current());
                }).toList();

                if (bundlingOperations.isEmpty()) {
                    // jpackage internal error: none of the standard bundling operations produce
                    // bundles of the `bundleType` on the current OS.
                    throw new AssertionError(String.format(
                            "None of the standard bundling operations produce bundles of type [%s] on %s",
                            bundleType, OperatingSystem.current()));
                } else if (bundlingOperations.size() == 1) {
                    return bundlingOperations.getFirst();
                } else if (StandardBundlingOperation.MACOS_APP_IMAGE.containsAll(bundlingOperations)) {
                    if (PREDEFINED_APP_IMAGE.containsIn(cmdline)) {
                        return StandardBundlingOperation.SIGN_MAC_APP_IMAGE;
                    } else {
                        return StandardBundlingOperation.CREATE_MAC_APP_IMAGE;
                    }
                } else {
                    // Pick the first one.
                    return bundlingOperations.getFirst();
                }
            }
        }).orElseGet(() -> {
            // No bundle type specified, use the default bundling operation in the given environment.
            return env.defaultOperation().map(descriptor -> {
                return Stream.of(StandardBundlingOperation.values()).filter(op -> {
                    return descriptor.equals(op.descriptor());
                }).findFirst().orElseThrow(() -> {
                    // jpackage internal error: none of the standard bundling operations match the
                    // descriptor of the default bundling operation in the given environment.
                    throw new AssertionError(String.format(
                            "None of the standard bundling operations match bundling operation descriptor [%s]",
                            descriptor));
                });
            }).orElseThrow(() -> {
                throw new ConfigException(
                        I18N.format("error.undefined-default-bundling-operation"),
                        I18N.format("error.undefined-default-bundling-operation.advice", TYPE.getSpec().name().formatForCommandLine()));
            });
        });
    }

    private static boolean isRuntimeInstaller(Options cmdline, OptionScope bundlingOperation) {
        return StandardBundlingOperation.CREATE_BUNDLE.contains(bundlingOperation)
                && PREDEFINED_RUNTIME_IMAGE.containsIn(cmdline)
                && !PREDEFINED_APP_IMAGE.containsIn(cmdline)
                && !MAIN_JAR.containsIn(cmdline)
                && !MODULE.containsIn(cmdline);
    }

    private static Predicate<OptionSpec<?>> matchInScope(Collection<? extends OptionScope> scope) {
        Objects.requireNonNull(scope);
        return optionSpec -> {
            return optionSpec.scope().containsAll(scope);
        };
    }

    private static Predicate<OptionSpec<?>> matchInScope(OptionScope... scope) {
        return matchInScope(List.of(scope));
    }

    private static List<Option> asOptionList(OptionValue<?>... options) {
        return Stream.of(options).map(OptionValue::getOption).toList();
    }

    private static RuntimeException error(String formatId, Object ... args) {
        return new JPackageException(MessageFormatUtils.createMessage(formatId, args));
    }

    private static ExceptionWithOrigin errorWithOrigin(Exception error, OptionSpec<?>... origin) {
        return errorWithOrigin(error, List.of(origin));
    }

    private static ExceptionWithOrigin errorWithOrigin(Exception error, List<? extends OptionSpec<?>> origin) {
        return new ExceptionWithOrigin(error, origin);
    }


    private record MutualExclusiveOptions(List<Option> options, Function<Object[], RuntimeException> createException) {
        MutualExclusiveOptions {
            options.forEach(Objects::requireNonNull);
            if (options.size() < 2) {
                throw new IllegalArgumentException();
            }
            Objects.requireNonNull(createException);
        }

        Optional<ExceptionWithOrigin> validate(Options cmdline) {
            final var detectedOptions = options.stream().filter(cmdline::contains).toList();
            if (detectedOptions.size() > 1) {
                final var errMesageformatArgs = detectedOptions.stream().map(Option::spec).map(optionSpec -> {
                    return MessageFormatUtils.mapFormatArguments(cmdline, optionSpec)[0];
                }).toArray();
                return Optional.of(errorWithOrigin(
                        createException.apply(errMesageformatArgs),
                        detectedOptions.stream().map(Option::spec).toList()));
            } else {
                return Optional.empty();
            }
        }

        MutualExclusiveOptions(List<Option> options) {
            this(options, args -> {
                return error("ERR_MutuallyExclusiveOptions", args);
            });
        }
    }


    private final Options cmdline;
    private final StandardBundlingOperation bundlingOperation;
    private final boolean typedOptions;
    private final boolean hasAppImage;
    private final boolean isRuntimeInstaller;

    private static final List<MutualExclusiveOptions> MUTUAL_EXCLUSIVE_OPTIONS;

    static {
        final List<MutualExclusiveOptions> config = new ArrayList<>();

        Stream.of(
                asOptionList(PREDEFINED_RUNTIME_IMAGE, PREDEFINED_APP_IMAGE),
                asOptionList(PREDEFINED_RUNTIME_IMAGE, ADD_MODULES),
                asOptionList(PREDEFINED_RUNTIME_IMAGE, JLINK_OPTIONS),
                asOptionList(MAC_SIGNING_KEY_NAME, MAC_APP_IMAGE_SIGN_IDENTITY),
                asOptionList(MAC_SIGNING_KEY_NAME, MAC_INSTALLER_SIGN_IDENTITY),
                asOptionList(MODULE, MAIN_JAR)
        ).map(MutualExclusiveOptions::new).forEach(config::add);

        MUTUAL_EXCLUSIVE_OPTIONS = List.copyOf(config);
    }
}
