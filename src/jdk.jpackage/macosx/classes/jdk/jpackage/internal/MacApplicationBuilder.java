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
package jdk.jpackage.internal;

import static jdk.jpackage.internal.cli.StandardValidator.IS_VALID_MAC_BUNDLE_IDENTIFIER;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.model.AppImageLayout;
import jdk.jpackage.internal.model.AppImageSigningConfig;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLaunchers;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.ExternalApplication;
import jdk.jpackage.internal.model.JPackageException;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.MacApplication;
import jdk.jpackage.internal.model.MacApplicationMixin;
import jdk.jpackage.internal.summary.StandardWarning;
import jdk.jpackage.internal.summary.StandardProperty;
import jdk.jpackage.internal.summary.SummaryAccumulator;
import jdk.jpackage.internal.util.PListReader;
import jdk.jpackage.internal.util.Result;
import jdk.jpackage.internal.util.RootedPath;

final class MacApplicationBuilder {

    MacApplicationBuilder(ApplicationBuilder appBuilder) {
        this.superBuilder = Objects.requireNonNull(appBuilder);
    }

    private MacApplicationBuilder(MacApplicationBuilder other) {
        this(other.superBuilder.copy());
        icon = other.icon;
        bundleName = other.bundleName;
        bundleIdentifier = other.bundleIdentifier;
        category = other.category;
        appStore = other.appStore;
        externalInfoPlistFile = other.externalInfoPlistFile;
        signingBuilder = other.signingBuilder;
        summary = other.summary;
    }

    MacApplicationBuilder icon(Path v) {
        icon = v;
        return this;
    }

    MacApplicationBuilder bundleName(String v) {
        bundleName = v;
        return this;
    }

    MacApplicationBuilder bundleIdentifier(String v) {
        bundleIdentifier = v;
        return this;
    }

    MacApplicationBuilder category(String v) {
        category = v;
        return this;
    }

    MacApplicationBuilder appStore(boolean v) {
        appStore = v;
        return this;
    }

    MacApplicationBuilder externalInfoPlistFile(Path v) {
        externalInfoPlistFile = v;
        return this;
    }

    MacApplicationBuilder signingBuilder(AppImageSigningConfigBuilder v) {
        signingBuilder = v;
        return this;
    }

    MacApplicationBuilder summary(SummaryAccumulator v) {
        summary = v;
        return this;
    }

    Optional<ExternalApplication> externalApplication() {
        return superBuilder.externalApplication();
    }

    Optional<ApplicationLaunchers> launchers() {
        return superBuilder.launchers();
    }

    MacApplication create() {
        if (externalInfoPlistFile != null) {
            return createCopyForExternalInfoPlistFile().create();
        }

        var app = superBuilder.create();

        validateAppVersion(app);
        summary().ifPresent(s -> {
            validateAppContentDirs(s, app);
        });

        final var mixin = new MacApplicationMixin.Stub(
                validatedIcon(),
                validatedBundleName(app),
                validatedBundleIdentifier(app),
                validatedCategory(),
                appStore,
                createSigningConfig());

        var macApp = MacApplication.create(app, mixin);

        summary().ifPresent(s -> {
            s.put(StandardProperty.MAC_BUNDLE_IDENTIFIER, macApp.bundleIdentifier());
            s.put(StandardProperty.MAC_BUNDLE_NAME, macApp.bundleName());
        });

        return macApp;
    }

    static MacApplication overrideAppImageLayout(MacApplication app, AppImageLayout appImageLayout) {
        final var mixin = new MacApplicationMixin.Stub(
                app.icon(),
                app.bundleName(),
                app.bundleIdentifier(),
                app.category(),
                app.appStore(),
                app.signingConfig());
        return MacApplication.create(ApplicationBuilder.overrideAppImageLayout(app, appImageLayout), mixin);
    }

    private static void validateAppVersion(Application app) {
        try {
            CFBundleVersion.of(app.version());
        } catch (IllegalArgumentException ex) {
            throw I18N.buildConfigException(ex).advice("error.invalid-cfbundle-version.advice").create();
        }
    }

    private static Stream<Path> appContentTopPaths(Application app) {
        return app.contentDirSources().stream().filter(rootedPath -> {
            return rootedPath.branch().getNameCount() == 1;
        }).map(RootedPath::fullPath);
    }

    private static void validateAppContentDirs(SummaryAccumulator summary, Application app) {
        var warnings = appContentTopPaths(app)
                .map(NonStandardAppContentWarning::createMapEntry)
                .flatMap(Optional::stream)
                .collect(groupingBy(
                        Map.Entry::getKey,
                        mapping(Map.Entry::getValue, toList())
                )).entrySet().stream()
                .sorted(Comparator.comparing(
                        Map.Entry::getKey,
                        Comparator.comparing(Enum::ordinal)
                ))
                .map(Map.Entry::getValue)
                .flatMap(Collection::stream)
                .toList();
        if (!warnings.isEmpty()) {
            summary.putMultiValue(StandardWarning.MAC_NON_STANDARD_APP_CONTENT, warnings);
        }
    }

    private enum NonStandardAppContentWarning {
        NOT_DIRECTORY("warning.non-standard-app-content.not-dir"),
        NON_STANDARD_DIRECTOTY_NAME("warning.non-standard-app-content.non-standard-dir-name"),
        ;

        NonStandardAppContentWarning(String formatKey) {
            this.formatKey = Objects.requireNonNull(formatKey);
        }

        static Optional<Map.Entry<NonStandardAppContentWarning, String>> createMapEntry(Path contentDir) {
            if (!Files.isDirectory(contentDir)) {
                return Optional.of(Map.entry(NOT_DIRECTORY, NOT_DIRECTORY.format(contentDir)));
            } else if (!CONTENTS_SUB_DIRS.contains(contentDir.getFileName())) {
                return Optional.of(Map.entry(
                        NON_STANDARD_DIRECTOTY_NAME,
                        NON_STANDARD_DIRECTOTY_NAME.format(contentDir.getFileName(), contentDir)
                ));
            } else {
                return Optional.empty();
            }
        }

        private String format(Object... formatArgs) {
            return I18N.format(formatKey, formatArgs);
        }

        private final String formatKey;
    }

    private MacApplicationBuilder createCopyForExternalInfoPlistFile() {
        final var builder = new MacApplicationBuilder(this);

        builder.externalInfoPlistFile(null);

        Result<PListReader> plistResult = Result.of(() -> {
            return new PListReader(Files.readAllBytes(externalInfoPlistFile));
        }, Exception.class);

        plistResult.value().ifPresent(plist -> {
            if (builder.bundleName == null) {
                plist.findValue("CFBundleName").ifPresent(builder::bundleName);
            }

            if (builder.bundleIdentifier == null) {
                plist.findValue("CFBundleIdentifier").ifPresent(builder::bundleIdentifier);
            }

            if (builder.category == null) {
                plist.findValue("LSApplicationCategoryType").ifPresent(builder::category);
            }

            if (builder.superBuilder.version().isEmpty()) {
                plist.findValue("CFBundleVersion").ifPresent(ver -> {
                    Log.trace("Derive bundle version [%s] from [%s] file", ver, externalInfoPlistFile);
                    builder.superBuilder.version(ver);
                });
            }
        });

        plistResult.firstError().filter(_ -> {
            // If we are building a runtime and the Info.plist file of the predefined
            // runtime bundle is malformed or unavailable, ignore it.
            return !superBuilder.isRuntime();
        }).ifPresent(ex -> {
            // We are building an application from the predefined app image and
            // the Info.plist file in the predefined app image bundle is malformed or unavailable. Bail out.
            switch (ex) {
                case IOException ioex -> {
                    throw new UncheckedIOException(ioex);
                }
                default -> {
                    throw new JPackageException(
                            I18N.format("error.invalid-app-image-plist-file", externalInfoPlistFile), ex);
                }
            }
        });

        return builder;
    }

    private Optional<AppImageSigningConfig> createSigningConfig() {
        return Optional.ofNullable(signingBuilder).map(AppImageSigningConfigBuilder::create);
    }

    private String validatedBundleName(Application app) {
        final var value = Optional.ofNullable(bundleName).orElseGet(() -> {
            final var appName = app.name();
// Commented out for backward compatibility
//            if (appName.length() > MAX_BUNDLE_NAME_LENGTH) {
//                return appName.substring(0, MAX_BUNDLE_NAME_LENGTH);
//            } else {
//                return appName;
//            }
            return appName;
        });

        summary().ifPresent(s -> {
            if (value.length() > MAX_BUNDLE_NAME_LENGTH && (bundleName != null)) {
                s.put(StandardWarning.MAC_BUNDLE_NAME_TOO_LONG, (Object)value);
            }
        });

        return value;
    }

    private String validatedBundleIdentifier(Application app) {
        return Optional.ofNullable(bundleIdentifier).orElseGet(() -> {
            var derivedValue = app.mainLauncher()
                    .flatMap(Launcher::startupInfo)
                    .map(li -> {
                        final var packageName = li.packageName();
                        if (packageName.isEmpty()) {
                            return li.simpleClassName();
                        } else {
                            return packageName;
                        }
                    })
                    .orElseGet(app::name);

            if (!IS_VALID_MAC_BUNDLE_IDENTIFIER.test(derivedValue)) {
                // Derived bundle identifier is invalid. Try to adjust it by dropping all invalid characters.
                derivedValue = derivedValue.codePoints()
                        .mapToObj(Character::toString)
                        .filter(IS_VALID_MAC_BUNDLE_IDENTIFIER)
                        .collect(Collectors.joining(""));
                if (!IS_VALID_MAC_BUNDLE_IDENTIFIER.test(derivedValue)) {
                    throw new ConfigException(
                            I18N.format("error.invalid-derived-bundle-identifier"),
                            I18N.format("error.invalid-derived-bundle-identifier.advice"));
                }
            }

            Log.trace("Derived bundle identifier: %s", derivedValue);
            return derivedValue;
        });
    }

    private String validatedCategory() {
        return "public.app-category." + Optional.ofNullable(category).orElseGet(DEFAULTS::category);
    }

    private Optional<Path> validatedIcon() {
        return Optional.ofNullable(icon).map(LauncherBuilder::validateIcon);
    }

    private Optional<SummaryAccumulator> summary() {
        return Optional.ofNullable(summary);
    }

    private record Defaults(String category) {
    }

    private Path icon;
    private String bundleName;
    private String bundleIdentifier;
    private String category;
    private boolean appStore;
    private Path externalInfoPlistFile;
    private AppImageSigningConfigBuilder signingBuilder;
    private SummaryAccumulator summary;

    private final ApplicationBuilder superBuilder;

    private static final Defaults DEFAULTS = new Defaults("utilities");

    private static final int MAX_BUNDLE_NAME_LENGTH = 16;

    // List of standard subdirectories of the "Contents" directory
    private static final Set<Path> CONTENTS_SUB_DIRS = Stream.of(
            "MacOS",
            "Resources",
            "Frameworks",
            "PlugIns",
            "SharedSupport"
    ).map(Path::of).collect(Collectors.toUnmodifiableSet());
}
