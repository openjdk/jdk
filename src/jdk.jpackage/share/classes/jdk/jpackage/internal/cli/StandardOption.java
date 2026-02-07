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

import static java.util.stream.Collectors.toUnmodifiableSet;
import static jdk.jpackage.internal.cli.OptionSpecBuilder.pathSeparator;
import static jdk.jpackage.internal.cli.OptionSpecBuilder.toList;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.CREATE_MAC_PKG;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.CREATE_NATIVE;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.SIGN_MAC_APP_IMAGE;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.fromOptionName;
import static jdk.jpackage.internal.cli.StandardOptionContext.createOptionSpecBuilderMutator;
import static jdk.jpackage.internal.cli.StandardOptionValueExceptionFactory.ERROR_WITH_VALUE;
import static jdk.jpackage.internal.cli.StandardOptionValueExceptionFactory.ERROR_WITH_VALUE_AND_OPTION_NAME;
import static jdk.jpackage.internal.cli.StandardOptionValueExceptionFactory.forMessageWithOptionValueAndName;
import static jdk.jpackage.internal.cli.StandardValueConverter.addLauncherShortcutConv;
import static jdk.jpackage.internal.cli.StandardValueConverter.booleanConv;
import static jdk.jpackage.internal.cli.StandardValueConverter.explodedPathConverter;
import static jdk.jpackage.internal.cli.StandardValueConverter.identityConv;
import static jdk.jpackage.internal.cli.StandardValueConverter.mainLauncherShortcutConv;
import static jdk.jpackage.internal.cli.StandardValueConverter.pathConv;
import static jdk.jpackage.internal.cli.StandardValueConverter.uuidConv;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.model.AppImageBundleType;
import jdk.jpackage.internal.model.BundleType;
import jdk.jpackage.internal.model.BundlingOperationDescriptor;
import jdk.jpackage.internal.model.JPackageException;
import jdk.jpackage.internal.model.LauncherShortcut;
import jdk.jpackage.internal.model.LauncherShortcutStartupDirectory;
import jdk.jpackage.internal.util.RootedPath;
import jdk.jpackage.internal.model.SelfContainedException;
import jdk.jpackage.internal.util.SetBuilder;

/**
 * jpackage command line options
 */
public final class StandardOption {

    private StandardOption() {
    }

    private static final Set<OperatingSystem> SUPPORTED_OS = Set.of(
            OperatingSystem.LINUX, OperatingSystem.WINDOWS, OperatingSystem.MACOS);


    /**
     * Scope of options configuring a launcher.
     */
    enum LauncherProperty implements OptionScope {
        VALUE
    }


    /**
     * Modes in which bundling operations don't involve building of an app image.
     */
    private static final Set<BundlingOperationModifier> NOT_BUILDING_APP_IMAGE = Set.of(
            // jpackage will not build an app image when bundling runtime native package
            BundlingOperationModifier.BUNDLE_RUNTIME,
            // jpackage will not build an app image if predefined app image is supplied
            BundlingOperationModifier.BUNDLE_PREDEFINED_APP_IMAGE);

    private static final Set<OptionScope> MAC_SIGNING = new SetBuilder<OptionScope>()
            .add(StandardBundlingOperation.MAC_SIGNING)
            .add(NOT_BUILDING_APP_IMAGE)
            .create();


    static final OptionValue<Boolean> HELP = auxilaryOption("help").addAliases("h", "?").create();

    static final OptionValue<Boolean> VERSION = auxilaryOption("version").create();

    public static final OptionValue<Boolean> VERBOSE = auxilaryOption("verbose").create();

    public static final OptionValue<BundleType> TYPE = option("type", BundleType.class).addAliases("t")
            .scope(StandardBundlingOperation.values()).inScope(NOT_BUILDING_APP_IMAGE)
            .converterExceptionFactory(ERROR_WITH_VALUE).converterExceptionFormatString("ERR_InvalidInstallerType")
            .converter(str -> {
                return parseBundleType(str, OperatingSystem.current());
            })
            .description("help.option.type" + resourceKeySuffix(OperatingSystem.current()))
            .mutate(createOptionSpecBuilderMutator((b, context) -> {
                b.description("help.option.type" + resourceKeySuffix(context.os()));
                b.converter(str -> {
                    return parseBundleType(str, context.os());
                });
            })).create();

    public static final OptionValue<? extends Collection<RootedPath>> INPUT = directoryOption("input").addAliases("i")
            .outOfScope(NOT_BUILDING_APP_IMAGE)
            .map(explodedPathOptionMapper(explodedPathConverter().create()))
            .create(optionValueBuilder -> {
                return optionValueBuilder.to(List::of).create();
            });

    public static final OptionValue<Path> DEST = directoryOption("dest").addAliases("d")
            .valuePattern("destination path")
            .validator(StandardValidator.IS_DIRECTORY_OR_NON_EXISTENT)
            .defaultValue(Path.of("").toAbsolutePath())
            .create();

    public static final OptionValue<String> DESCRIPTION = stringOption("description")
            .inScope(LauncherProperty.VALUE)
            .valuePattern("description string")
            .create();

    public static final OptionValue<String> VENDOR = stringOption("vendor").valuePattern("vendor string").create();

    public static final OptionValue<String> APPCLASS = stringOption("main-class")
            .valuePattern("class name")
            .outOfScope(NOT_BUILDING_APP_IMAGE)
            .inScope(LauncherProperty.VALUE)
            .create();

    public static final OptionValue<String> NAME = stringOption("name").addAliases("n")
            .validator(StandardValidator.IS_NAME_VALID)
            .validatorExceptionFactory(ERROR_WITH_VALUE).validatorExceptionFormatString("ERR_InvalidAppName")
            .create();

    public static final OptionValue<Path> RESOURCE_DIR = directoryOption("resource-dir")
            .scope(StandardBundlingOperation.values()).inScope(NOT_BUILDING_APP_IMAGE)
            .create();

    public static final OptionValue<List<String>> ARGUMENTS = escapedStringListOption("arguments")
            .valuePattern("main class arguments")
            .outOfScope(NOT_BUILDING_APP_IMAGE)
            .inScope(LauncherProperty.VALUE)
            .createArray(toList());

    public static final OptionValue<List<String>> JLINK_OPTIONS = escapedStringListOption("jlink-options")
            .valuePattern("jlink options")
            .outOfScope(NOT_BUILDING_APP_IMAGE)
            .createArray(toList());

    public static final OptionValue<Path> ICON = fileOption("icon")
            .validator(new Predicate<>() {
                @Override
                public boolean test(Path path) {
                    if (!path.toString().isEmpty()) {
                        return StandardValidator.IS_FILE_OR_SYMLINK.test(path);
                    } else {
                        return true;
                    }
                }
            })
            .inScope(LauncherProperty.VALUE)
            .create();

    public static final OptionValue<String> COPYRIGHT = stringOption("copyright").valuePattern("copyright string").create();

    public static final OptionValue<Path> LICENSE_FILE = fileOption("license-file").create();

    public static final OptionValue<String> APP_VERSION = stringOption("app-version").create();

    public static final OptionValue<String> ABOUT_URL = urlOption("about-url")
            .scope(CREATE_NATIVE).inScope(NOT_BUILDING_APP_IMAGE)
            .create();

    public static final OptionValue<List<String>> JAVA_OPTIONS = escapedStringListOption("java-options")
            .valuePattern("java options")
            .outOfScope(NOT_BUILDING_APP_IMAGE)
            .inScope(LauncherProperty.VALUE)
            .createArray(toList());

    public static final OptionValue<List<Collection<RootedPath>>> APP_CONTENT = existingPathOption("app-content")
            .tokenizer(",")
            .valuePattern("additional content")
            .outOfScope(NOT_BUILDING_APP_IMAGE)
            .map(explodedPathOptionMapper(explodedPathConverter().withPathFileName().create()))
            .mutate(createOptionSpecBuilderMutator((b, context) -> {
                if (context.os() == OperatingSystem.MACOS) {
                    b.description("help.option.app-content" + resourceKeySuffix(context.os()));
                }
            }))
            .createArray(toExplodedPathList());

    static final OptionValue<Path[]> FILE_ASSOCIATIONS_INTERNAL = fileOption("file-associations")
            .tokenizer(pathSeparator())
            .outOfScope(BundlingOperationModifier.BUNDLE_RUNTIME)
            .createArray();

    static final OptionValue<AdditionalLauncher[]> ADD_LAUNCHER_INTERNAL = createAddLauncherOption("add-launcher");

    public static final OptionValue<Path> TEMP_ROOT = directoryOption("temp")
            .validatorExceptionFactory((optionName, optionValue, formatString, cause) -> {
                if (cause.orElseThrow() instanceof StandardValidator.DirectoryListingIOException) {
                    formatString = "error.path-parameter-ioexception";
                }
                return ERROR_WITH_VALUE_AND_OPTION_NAME.create(optionName, optionValue, formatString, cause);
            })
            .validatorExceptionFormatString("error.parameter-not-empty-directory")
            .validator(StandardValidator.IS_DIRECTORY_EMPTY_OR_NON_EXISTENT)
            .create();

    public static final OptionValue<Path> INSTALL_DIR = pathOption("install-dir")
            .valuePattern("directory path")
            .mutate(createOptionSpecBuilderMutator((b, context) -> {
                if (context.os() == OperatingSystem.WINDOWS) {
                    b.description("help.option.install-dir" + resourceKeySuffix(context.os()));
                }
            }))
            .create();

    public static final OptionValue<Path> PREDEFINED_APP_IMAGE = directoryOption("app-image")
            .scope(CREATE_NATIVE).inScope(SIGN_MAC_APP_IMAGE).inScope(BundlingOperationModifier.BUNDLE_PREDEFINED_APP_IMAGE)
            .mutate(createOptionSpecBuilderMutator((b, context) -> {
                if (context.os() == OperatingSystem.MACOS) {
                    b.description("help.option.app-image" + resourceKeySuffix(context.os()));
                    var directoryValidator = b.createValidator().orElseThrow();
                    var macBundleValidator = b
                            .validatorExceptionFormatString("error.parameter-not-mac-bundle")
                            .validator(StandardValidator.IS_VALID_MAC_BUNDLE)
                            .createValidator().orElseThrow();
                    // Use "lazy and" validator composition.
                    // If the value of the option is not a directory, we want only one error reported, not two:
                    // one that the value is not a directory and another that it is not a valid macOS bundle.
                    b.validator(Validator.andLazy(directoryValidator, macBundleValidator));
                }
            }))
            .create();

    public static final OptionValue<Path> PREDEFINED_RUNTIME_IMAGE = directoryOption("runtime-image")
            .outOfScope(BundlingOperationModifier.BUNDLE_PREDEFINED_APP_IMAGE)
            .create();

    static final OptionSpec<Path> RUNTIME_INSTALLER_RUNTIME_IMAGE = directoryOption("runtime-image")
            .outOfScope(BundlingOperationModifier.BUNDLE_PREDEFINED_APP_IMAGE)
            .description("help.option.installer-runtime-image")
            .createOptionSpec();

    public static final OptionValue<Path> MAIN_JAR = pathOption("main-jar")
            .valuePattern("main jar file")
            .outOfScope(NOT_BUILDING_APP_IMAGE)
            .inScope(LauncherProperty.VALUE)
            .create();

    public static final OptionValue<String> MODULE = stringOption("module").addAliases("m")
            .valuePattern("<module name>[/<main class>]")
            .outOfScope(NOT_BUILDING_APP_IMAGE)
            .inScope(LauncherProperty.VALUE)
            .create();

    public static final OptionValue<List<String>> ADD_MODULES = stringOption("add-modules").tokenizer(",")
            .valuePattern("module name")
            .outOfScope(NOT_BUILDING_APP_IMAGE)
            .createArray(toList());

    public static final OptionValue<List<Path>> MODULE_PATH = pathOption("module-path").addAliases("p").tokenizer(pathSeparator())
            .valuePattern("module path")
            .outOfScope(NOT_BUILDING_APP_IMAGE)
            .mutate(createOptionSpecBuilderMutator((b, context) -> {
                if (context.os() == OperatingSystem.WINDOWS) {
                    b.description("help.option.module-path" + resourceKeySuffix(context.os()));
                }
            }))
            .createArray(toList());

    public static final OptionValue<Boolean> LAUNCHER_AS_SERVICE = booleanOption("launcher-as-service")
            .scope(nativeBundling())
            .inScope(LauncherProperty.VALUE)
            .create();

    //
    // Linux-specific
    //

    public static final OptionValue<String> LINUX_RELEASE = stringOption("linux-app-release").scope(nativeBundling()).create();

    public static final OptionValue<String> LINUX_PACKAGE_NAME = stringOption("linux-package-name")
            .valuePattern("package name")
            .scope(nativeBundling()).create();

    public static final OptionValue<String> LINUX_DEB_MAINTAINER_EMAIL = stringOption("linux-deb-maintainer")
            .valuePattern("email address")
            .create();

    public static final OptionValue<String> LINUX_APP_CATEGORY = stringOption("linux-app-category").scope(nativeBundling()).create();

    public static final OptionValue<String> LINUX_RPM_LICENSE_TYPE = stringOption("linux-rpm-license-type")
            .valuePattern("license type")
            .scope(nativeBundling()).create();

    public static final OptionValue<String> LINUX_PACKAGE_DEPENDENCIES = stringOption("linux-package-deps")
            .valuePattern("package-dep-string")
            .scope(nativeBundling()).create();

    public static final OptionValue<LauncherShortcut> LINUX_SHORTCUT_HINT = launcherShortcutOption("linux-shortcut")
            .scope(nativeBundling())
            .inScope(LauncherProperty.VALUE)
            .create();

    public static final OptionValue<String> LINUX_MENU_GROUP = stringOption("linux-menu-group")
            .valuePattern("menu-group-name")
            .scope(nativeBundling()).create();

    //
    // MacOS-specific
    //

    public static final OptionValue<List<Collection<RootedPath>>> MAC_DMG_CONTENT = existingPathOption("mac-dmg-content")
            .valuePattern("additional content path")
            .tokenizer(",")
            .map(explodedPathOptionMapper(explodedPathConverter().withPathFileName().create()))
            .createArray(toExplodedPathList());

    public static final OptionValue<Boolean> MAC_SIGN = booleanOption("mac-sign").scope(MAC_SIGNING).addAliases("s").create();

    public static final OptionValue<Boolean> MAC_APP_STORE = booleanOption("mac-app-store").create();

    public static final OptionValue<String> MAC_APP_CATEGORY = stringOption("mac-app-category").create();

    public static final OptionValue<String> MAC_BUNDLE_NAME = stringOption("mac-package-name")
            .valuePattern("package name")
            .create();

    public static final OptionValue<String> MAC_BUNDLE_IDENTIFIER = stringOption("mac-package-identifier")
            .valuePattern("package identifier")
            .create();

    public static final OptionValue<String> MAC_BUNDLE_SIGNING_PREFIX = stringOption("mac-package-signing-prefix").scope(MAC_SIGNING).create();

    public static final OptionValue<String> MAC_SIGNING_KEY_NAME = stringOption("mac-signing-key-user-name").scope(MAC_SIGNING).create();

    public static final OptionValue<String> MAC_APP_IMAGE_SIGN_IDENTITY = stringOption("mac-app-image-sign-identity").scope(MAC_SIGNING).create();

    public static final OptionValue<String> MAC_INSTALLER_SIGN_IDENTITY = stringOption("mac-installer-sign-identity")
            .scope(CREATE_MAC_PKG).inScope(NOT_BUILDING_APP_IMAGE)
            .create();

    public static final OptionValue<Path> MAC_SIGNING_KEYCHAIN = pathOption("mac-signing-keychain")
            .valuePattern("keychain name")
            .scope(MAC_SIGNING).create();

    public static final OptionValue<Path> MAC_ENTITLEMENTS = fileOption("mac-entitlements")
            .valuePattern("file path")
            .scope(MAC_SIGNING).create();

    //
    // Windows-specific
    //

    public static final OptionValue<String> WIN_HELP_URL = urlOption("win-help-url").scope(nativeBundling()).create();

    public static final OptionValue<String> WIN_UPDATE_URL = urlOption("win-update-url").scope(nativeBundling()).create();

    public static final OptionValue<LauncherShortcut> WIN_MENU_HINT = launcherShortcutOption("win-menu")
            .scope(nativeBundling())
            .inScope(LauncherProperty.VALUE)
            .create();

    public static final OptionValue<String> WIN_MENU_GROUP = stringOption("win-menu-group")
            .valuePattern("menu group name")
            .scope(nativeBundling()).create();

    public static final OptionValue<LauncherShortcut> WIN_SHORTCUT_HINT = launcherShortcutOption("win-shortcut")
            .scope(nativeBundling())
            .inScope(LauncherProperty.VALUE)
            .create();

    public static final OptionValue<Boolean> WIN_SHORTCUT_PROMPT = booleanOption("win-shortcut-prompt").scope(nativeBundling()).create();

    public static final OptionValue<Boolean> WIN_PER_USER_INSTALLATION = booleanOption("win-per-user-install").scope(nativeBundling()).create();

    public static final OptionValue<Boolean> WIN_INSTALLDIR_CHOOSER = booleanOption("win-dir-chooser").scope(nativeBundling()).create();

    public static final OptionValue<UUID> WIN_UPGRADE_UUID = uuidOption("win-upgrade-uuid").scope(nativeBundling()).create();

    public static final OptionValue<Boolean> WIN_CONSOLE_HINT = booleanOption("win-console")
            .outOfScope(NOT_BUILDING_APP_IMAGE)
            .inScope(LauncherProperty.VALUE)
            .create();

    //
    // Synthetic options
    //

    /**
     * Processed additional launcher property files.
     * <p>
     * Items in the list are in the order "--add-launcher" options appeared on the
     * command line. Every item in the list has {@link #SOURCE_PROPERY_FILE} option
     * with the value set to the source property file and {@link #NAME} option with
     * the value set to the additional launcher name.
     */
    public static final OptionValue<List<Options>> ADDITIONAL_LAUNCHERS = OptionValue.create();

    /**
     * Processed file association property files.
     * <p>
     * Items in the list are in the order "--file-associations" options appeared on
     * the command line. Every item in the list has {@link #SOURCE_PROPERY_FILE}
     * option with the value set to the source property file.
     */
    public static final OptionValue<List<Options>> FILE_ASSOCIATIONS = OptionValue.create();

    public static final OptionValue<Path> SOURCE_PROPERY_FILE = OptionValue.create();

    public static final OptionValue<BundlingOperationDescriptor> BUNDLING_OPERATION_DESCRIPTOR = OptionValue.create();

    /**
     * Returns options configuring a launcher.
     *
     * @return the options configuring a launcher
     */
    static Set<Option> launcherOptions() {
        return options().stream().filter(option -> {
            return option.spec().scope().stream().anyMatch(LauncherProperty.class::isInstance);
        }).collect(toUnmodifiableSet());
    }

    /**
     * Returns public and package-private options with option specs defined in
     * {@link StandardOption} class.
     *
     * @return public and package-private options defined in
     *         {@link StandardOption} class
     */
    static Set<Option> options() {
        return Utils.getOptionsWithSpecs(StandardOption.class).map(OptionValue::getOption).collect(toUnmodifiableSet());
    }

    /**
     * Returns a {@link Predicate} that returns {@code true} if the given option
     * spec denotes an option supported on all platforms.
     *
     * @return the predicate
     */
    static Predicate<OptionSpec<?>> sharedOption() {
        return optionSpec -> {
            final var optionSupportedOSs = StandardBundlingOperation.narrow(optionSpec.scope().stream())
                    .map(StandardBundlingOperation::os).collect(toUnmodifiableSet());
            return optionSupportedOSs.equals(SUPPORTED_OS);
        };
    }

    /**
     * Returns a {@link Predicate} that returns {@code true} if the given option
     * spec denotes an option supported on the given platform.
     *
     * @param os the platform
     * @return the predicate
     */
    static Predicate<OptionSpec<?>> platformOption(OperatingSystem os) {
        Objects.requireNonNull(os);
        return optionSpec -> {
            return StandardBundlingOperation.narrow(optionSpec.scope().stream()).filter(op -> {
                return op.os().equals(os);
            }).findFirst().isPresent();
        };
    }

    static Consumer<OptionSpecBuilder<String>> stringOptionMutator() {
        return builder -> {
            builder.converter(identityConv());
        };
    }

    static Consumer<OptionSpecBuilder<Path>> pathOptionMutator() {
        return builder -> {
            builder.converter(pathConv())
            .mutate(createOptionSpecBuilderMutator((b, context) -> {
                context.asFileSource().ifPresent(propertyFile -> {
                    b.converterExceptionFactory(forMessageWithOptionValueAndName(propertyFile));
                    b.converterExceptionFormatString("error.properties-parameter-not-path");
                });
            }))
            .converterExceptionFactory(ERROR_WITH_VALUE_AND_OPTION_NAME)
            .converterExceptionFormatString("error.parameter-not-path");
        };
    }

    static Consumer<OptionSpecBuilder<Path>> fileOptionMutator() {
        return builder -> {
            builder.mutate(pathOptionMutator())
            .mutate(createOptionSpecBuilderMutator((b, context) -> {
                context.asFileSource().ifPresent(propertyFile -> {
                    b.validatorExceptionFactory(forMessageWithOptionValueAndName(propertyFile));
                    b.validatorExceptionFormatString("error.properties-parameter-not-file");
                });
            }))
            .validator(StandardValidator.IS_FILE_OR_SYMLINK)
            .validatorExceptionFactory(ERROR_WITH_VALUE_AND_OPTION_NAME)
            .validatorExceptionFormatString("error.parameter-not-file");
        };
    }

    static Consumer<OptionSpecBuilder<Path>> directoryOptionMutator() {
        return builder -> {
            builder.mutate(pathOptionMutator())
            .mutate(createOptionSpecBuilderMutator((b, context) -> {
                context.asFileSource().ifPresent(propertyFile -> {
                    b.validatorExceptionFactory(forMessageWithOptionValueAndName(propertyFile));
                    b.validatorExceptionFormatString("error.properties-parameter-not-directory");
                });
            }))
            .validator(StandardValidator.IS_DIRECTORY)
            .validatorExceptionFactory(ERROR_WITH_VALUE_AND_OPTION_NAME)
            .validatorExceptionFormatString("error.parameter-not-directory");
        };
    }

    static Consumer<OptionSpecBuilder<Path>> existingPathOptionMutator() {

        return builder -> {
            builder.mutate(pathOptionMutator())
            .validator(createExistingPathValidator(Validator.<Path, RuntimeException>build().exceptionFactory(ERROR_WITH_VALUE_AND_OPTION_NAME), true))
            .mutate(createOptionSpecBuilderMutator((b, context) -> {
                context.asFileSource().ifPresent(propertyFile -> {
                    var validatorBuilder = Validator.<Path, RuntimeException>build();
                    validatorBuilder.exceptionFactory(forMessageWithOptionValueAndName(propertyFile));
                    b.validator(createExistingPathValidator(validatorBuilder, false));
                });
            }));
        };
    }

    private static Validator<Path, RuntimeException> createExistingPathValidator(Validator.Builder<Path, RuntimeException> builder, boolean cmdline) {

        if (cmdline) {
            builder.formatString("error.parameter-not-directory");
        } else {
            builder.formatString("error.properties-parameter-not-directory");
        }

        var isDirectoryValidator = builder.predicate(StandardValidator.IS_DIRECTORY).create();

        if (cmdline) {
            builder.formatString("error.parameter-not-file");
        } else {
            builder.formatString("error.properties-parameter-not-file");
        }

        var isFileValidator = builder.predicate(StandardValidator.IS_FILE_OR_SYMLINK).create();

        @SuppressWarnings("unchecked")
        var validator = (Validator<Path, RuntimeException>)isDirectoryValidator.or(isFileValidator);

        return validator;
    }

    static Consumer<OptionSpecBuilder<Boolean>> booleanOptionMutator() {
        return builder -> {
            builder.mutate(createOptionSpecBuilderMutator((b, context) -> {
                context.asFileSource().ifPresent(propertyFile -> {
                    b.converter(booleanConv());
                });
            })).valuePattern(null).defaultValue(Boolean.FALSE);
        };
    }

    static Consumer<OptionSpecBuilder<LauncherShortcut>> launcherShortcutOptionMutator() {
        return builder -> {
            builder.mutate(createOptionSpecBuilderMutator((b, context) -> {
                context.asFileSource().ifPresent(propertyFile -> {
                    b.converter(addLauncherShortcutConv()).defaultOptionalValue(null);
                    b.converterExceptionFactory(forMessageWithOptionValueAndName(propertyFile));
                    b.converterExceptionFormatString("error.properties-parameter-not-launcher-shortcut-dir");
                });
            }))
            .converterExceptionFactory(ERROR_WITH_VALUE_AND_OPTION_NAME)
            .converterExceptionFormatString("error.parameter-not-launcher-shortcut-dir")
            .converter(mainLauncherShortcutConv())
            .defaultOptionalValue(new LauncherShortcut(LauncherShortcutStartupDirectory.DEFAULT))
            .valuePattern("shortcut startup directory");
        };
    }

    static Function<OptionSpecBuilder<Path>, OptionSpecBuilder<RootedPath[]>> explodedPathOptionMapper(ValueConverter<Path, RootedPath[]> conv) {
        Objects.requireNonNull(conv);
        return builder -> {
            return builder.map(conv)
                    .converterExceptionFactory(ERROR_WITH_VALUE_AND_OPTION_NAME)
                    .converterExceptionFormatString("error.path-parameter-ioexception")
                    // Add empty mutator to OptionSpecMapperOptionScope to make
                    // mapped option spec have `RootedPath[]` type.
                    // Otherwise, it will have `Path` type.
                    .mutate(createOptionSpecBuilderMutator((b, context) -> {
                    }));
        };
    }

    private static <T> Function<OptionValue.Builder<RootedPath[][]>, OptionValue<List<Collection<RootedPath>>>> toExplodedPathList() {
        return builder -> {
            return builder.to((RootedPath[][] v) -> {
                return Stream.of(v).map(arr -> {
                    return (Collection<RootedPath>)List.of(arr);
                }).toList();
            }).create();
        };
    }

    private static <T> OptionSpecBuilder<T> option(String name, Class<? extends T> valueType) {
        return OptionSpecBuilder.<T>create(valueType)
                .name(Objects.requireNonNull(name))
                .description("help.option." + name)
                .scope(fromOptionName(name))
                .scope(scope -> {
                    return SetBuilder.<OptionScope>build()
                            .add(scope)
                            .add(BundlingOperationModifier.values())
                            .create();
                });
    }

    private static OptionSpecBuilder<String> stringOption(String name) {
        return option(name, String.class).mutate(stringOptionMutator());
    }

    private static OptionSpecBuilder<UUID> uuidOption(String name) {
        return option(name, UUID.class)
                .converter(uuidConv())
                .converterExceptionFactory(ERROR_WITH_VALUE_AND_OPTION_NAME)
                .converterExceptionFormatString("error.parameter-not-uuid");
    }

    private static OptionSpecBuilder<Path> pathOption(String name) {
        return option(name, Path.class).mutate(pathOptionMutator());
    }

    private static OptionSpecBuilder<Path> existingPathOption(String name) {
        return option(name, Path.class).mutate(existingPathOptionMutator());
    }

    private static OptionSpecBuilder<Path> fileOption(String name) {
        return option(name, Path.class)
                .valuePattern("file path")
                .mutate(fileOptionMutator());
    }

    private static OptionSpecBuilder<Path> directoryOption(String name) {
        return option(name, Path.class)
                .valuePattern("directory path")
                .mutate(directoryOptionMutator());
    }

    private static OptionSpecBuilder<String> urlOption(String name) {
        return stringOption(name)
                .valuePattern("url")
                .validator(StandardValidator.IS_URL)
                .validatorExceptionFactory(ERROR_WITH_VALUE_AND_OPTION_NAME)
                .validatorExceptionFormatString("error.parameter-not-url");
    }

    private static OptionSpecBuilder<Boolean> booleanOption(String name) {
        return option(name, Boolean.class).mutate(booleanOptionMutator());
    }

    private static OptionSpecBuilder<LauncherShortcut> launcherShortcutOption(String name) {
        return option(name, LauncherShortcut.class).mutate(launcherShortcutOptionMutator());
    }

    private static OptionSpecBuilder<Boolean> auxilaryOption(String name) {
        return booleanOption(name)
                .scope(StandardBundlingOperation.values())
                .inScope(NOT_BUILDING_APP_IMAGE);
    }

    private static OptionSpecBuilder<String> escapedStringListOption(String name) {
        return stringOption(name).tokenizer(str -> {
            return Arguments.getArgumentList(str).toArray(String[]::new);
        }).converter(Arguments::unquoteIfNeeded);
    }

    private static UnaryOperator<Set<OptionScope>> nativeBundling() {
        return scope -> {
            return new SetBuilder<OptionScope>()
                    .set(scope)
                    .remove(new SetBuilder<OptionScope>().set(StandardBundlingOperation.values()).remove(CREATE_NATIVE).create())
                    .create();
        };
    }

    private static OptionValue<AdditionalLauncher[]> createAddLauncherOption(String name) {
        var propertyFileSpec = fileOption(name).create().getSpec();

        return option(name, AdditionalLauncher.class)
                .valuePattern("<launcher name>=<file path>")
                .description("help.option.add-launcher" + resourceKeySuffix(OperatingSystem.current()))
                .mutate(createOptionSpecBuilderMutator((b, context) -> {
                    b.description("help.option.add-launcher" + resourceKeySuffix(context.os()));
                }))
                .outOfScope(NOT_BUILDING_APP_IMAGE)
                .converterExceptionFormatString("")
                .converterExceptionFactory((optionName, optionValue, formatString, cause) -> {
                    final var theCause = cause.orElseThrow();
                    if (theCause instanceof AddLauncherSyntaxException) {
                        return ERROR_WITH_VALUE_AND_OPTION_NAME.create(optionName,
                                optionValue, "error.parameter-add-launcher-malformed", cause);
                    } else {
                        return (RuntimeException)theCause;
                    }
                }).converter(value -> {
                    var components = value.split("=", 2);
                    if (components.length != 2) {
                        throw new AddLauncherSyntaxException();
                    }

                    final var launcherName = components[0];

                    if (!StandardValidator.IS_NAME_VALID.test(launcherName)) {
                        throw new AddLauncherInvalidNameException(I18N.format("ERR_InvalidSLName", launcherName));
                    }

                    final Path propertyFile;
                    try {
                        propertyFile = propertyFileSpec.convert(OptionName.of(name),
                                StringToken.of(value, components[1])).orElseThrow();
                    } catch (JPackageException ex) {
                        throw new AddLauncherInvalidPropertyFileException(I18N.format(
                                "error.parameter-add-launcher-not-file", components[1], launcherName));
                    }

                    return new AdditionalLauncher(launcherName, propertyFile);
                }).defaultArrayValue(new AdditionalLauncher[0]).createArray();
    }

    private static BundleType parseBundleType(String str, OperatingSystem appImageOS) {
        Objects.requireNonNull(str);
        Objects.requireNonNull(appImageOS);

        return Stream.of(StandardBundlingOperation.values()).filter(bundlingOperation -> {
            return bundlingOperation.bundleTypeValue().equals(str);
        })
        .filter(bundlingOperation -> {
            // Skip app image bundle type if it is from another platform.
            return !(bundlingOperation.bundleType() instanceof AppImageBundleType)
                    || (bundlingOperation.os() == appImageOS);
        })
        .map(StandardBundlingOperation::bundleType)
        .findFirst()
        .orElseThrow(IllegalArgumentException::new);
    }

    private static String resourceKeySuffix(OperatingSystem os) {
        switch (os) {
            case LINUX -> {
                return ".linux";
            }
            case MACOS -> {
                return ".mac";
            }
            case WINDOWS -> {
                return ".win";
            }
            default -> {
                throw new IllegalArgumentException();
            }
        }
    }


    @SelfContainedException
    static class AddLauncherIllegalArgumentException extends IllegalArgumentException {

        AddLauncherIllegalArgumentException(String message) {
            super(message);
        }

        private static final long serialVersionUID = 1L;
    }


    static final class AddLauncherInvalidNameException extends AddLauncherIllegalArgumentException {

        AddLauncherInvalidNameException(String message) {
            super(message);
        }

        private static final long serialVersionUID = 1L;
    }


    static final class AddLauncherInvalidPropertyFileException extends AddLauncherIllegalArgumentException {

        AddLauncherInvalidPropertyFileException(String msg) {
            super(msg);
        }

        private static final long serialVersionUID = 1L;
    }


    private static final class AddLauncherSyntaxException extends IllegalArgumentException {

        AddLauncherSyntaxException() {
        }

        private static final long serialVersionUID = 1L;
    }


    private static final class Arguments {

        //
        // This is a an extract copied from jdk.jpackage.internal.Arguments class with the following changes:
        //  - Don't call unquoteIfNeeded() from getArgumentList().
        //  - Edit a comment in getArgumentList().
        //  - Insert Objects.requireNonNull() calls.
        //  - throw IllegalArgumentException from unquoteIfNeeded() if the input string is empty.
        //

        // regexp for parsing args (for example, for additional launchers)
        private static Pattern PATTERN = Pattern.compile(String.format(
                "(?:(?:%s|%s)|(?:\\\\[\"'\\s]|\\S))++",
                createPatternComponent('\''),
                createPatternComponent('\"')));

        private static String createPatternComponent(char quoteChar) {
            var str = Character.toString(quoteChar);
            return String.format("(?:%s(?:\\\\%s|[^%s])*+(?:%s|$))", str, str, str, str);
        }

        static List<String> getArgumentList(String inputString) {
            Objects.requireNonNull(inputString);

            List<String> list = new ArrayList<>();
            if (inputString.isEmpty()) {
                 return list;
            }

            // The "pattern" regexp attempts to abide to the rule that
            // strings are delimited by whitespace unless surrounded by
            // quotes, then it is anything (including spaces) in the quotes.
            Matcher m = PATTERN.matcher(inputString);
            while (m.find()) {
                String s = inputString.substring(m.start(), m.end()).trim();
                // Ensure we do not have an empty string. trim() will take care of
                // whitespace only strings. The regex preserves quotes and escaped
                // chars.
                if (!s.isEmpty()) {
                    list.add(s);
                }
            }
            return list;
        }

        static String unquoteIfNeeded(String in) {
            Objects.requireNonNull(in);
            if (in.isEmpty()) {
                throw new IllegalArgumentException();
            }

            // Use code points to preserve non-ASCII chars
            StringBuilder sb = new StringBuilder();
            int codeLen = in.codePointCount(0, in.length());
            int quoteChar = -1;
            for (int i = 0; i < codeLen; i++) {
                int code = in.codePointAt(i);
                if (code == '"' || code == '\'') {
                    // If quote is escaped make sure to copy it
                    if (i > 0 && in.codePointAt(i - 1) == '\\') {
                        sb.deleteCharAt(sb.length() - 1);
                        sb.appendCodePoint(code);
                        continue;
                    }
                    if (quoteChar != -1) {
                        if (code == quoteChar) {
                            // close quote, skip char
                            quoteChar = -1;
                        } else {
                            sb.appendCodePoint(code);
                        }
                    } else {
                        // opening quote, skip char
                        quoteChar = code;
                    }
                } else {
                    sb.appendCodePoint(code);
                }
            }
            return sb.toString();
        }
    }
}
