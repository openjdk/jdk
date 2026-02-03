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

import static jdk.jpackage.internal.I18N.buildConfigException;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import jdk.jpackage.internal.model.AppImageLayout;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLaunchers;
import jdk.jpackage.internal.model.ExternalApplication;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.LauncherIcon;
import jdk.jpackage.internal.model.LauncherStartupInfo;
import jdk.jpackage.internal.model.ResourceDirLauncherIcon;
import jdk.jpackage.internal.model.RuntimeBuilder;
import jdk.jpackage.internal.util.RootedPath;

final class ApplicationBuilder {

    Application create() {
        Objects.requireNonNull(appImageLayout);

        final var launchersAsList = Optional.ofNullable(launchers).map(
                ApplicationLaunchers::asList).orElseGet(List::of);

        final String effectiveName = Optional.ofNullable(name).or(() -> {
            return Optional.ofNullable(launchers).map(ApplicationLaunchers::mainLauncher).map(Launcher::name);
        }).orElseThrow(() -> {
            return buildConfigException("error.no.name").advice("error.no.name.advice").create();
        });

        return new Application.Stub(
                effectiveName,
                Optional.ofNullable(description).orElse(effectiveName),
                Optional.ofNullable(version).orElseGet(DEFAULTS::version),
                Optional.ofNullable(vendor).orElseGet(DEFAULTS::vendor),
                Optional.ofNullable(copyright).orElseGet(DEFAULTS::copyright),
                Optional.ofNullable(appDirSources).orElseGet(List::of),
                Optional.ofNullable(contentDirSources).orElseGet(List::of),
                appImageLayout,
                Optional.ofNullable(runtimeBuilder),
                launchersAsList,
                Map.of());
    }

    ApplicationBuilder runtimeBuilder(RuntimeBuilder v) {
        runtimeBuilder = v;
        return this;
    }

    ApplicationBuilder externalApplication(ExternalApplication v) {
        externalApp = v;
        return this;
    }

    ApplicationBuilder launchers(ApplicationLaunchers v) {
        launchers = v;
        return this;
    }

    Optional<ApplicationLaunchers> launchers() {
        return Optional.ofNullable(launchers);
    }

    Optional<ExternalApplication> externalApplication() {
        return Optional.ofNullable(externalApp);
    }

    ApplicationBuilder appImageLayout(AppImageLayout v) {
        appImageLayout = v;
        return this;
    }

    ApplicationBuilder name(String v) {
        name = v;
        return this;
    }

    ApplicationBuilder description(String v) {
        description = v;
        return this;
    }

    ApplicationBuilder version(String v) {
        version = v;
        return this;
    }

    ApplicationBuilder vendor(String v) {
        vendor = v;
        return this;
    }

    ApplicationBuilder copyright(String v) {
        copyright = v;
        return this;
    }

    ApplicationBuilder appDirSources(Collection<RootedPath> v) {
        appDirSources = v;
        return this;
    }

    ApplicationBuilder contentDirSources(Collection<RootedPath> v) {
        contentDirSources = v;
        return this;
    }

    static <T extends Launcher> ApplicationLaunchers normalizeIcons(
            ApplicationLaunchers appLaunchers, Optional<Path> resourceDir, BiFunction<T, Launcher, T> launcherOverrideCtor) {

        Objects.requireNonNull(resourceDir);

        return normalizeLauncherProperty(appLaunchers, Launcher::hasDefaultIcon, (T launcher) -> {
            return resourceDir.<LauncherIcon>flatMap(dir -> {
                var resource = LauncherBuilder.createLauncherIconResource(launcher, _ -> {
                    return new OverridableResource()
                            .setResourceDir(dir)
                            .setSourceOrder(OverridableResource.Source.ResourceDir);
                });
                if (resource.probe() == OverridableResource.Source.ResourceDir) {
                    return Optional.of(ResourceDirLauncherIcon.create(resource.getPublicName().toString()));
                } else {
                    return Optional.empty();
                }
            });
        }, launcher -> {
            return launcher.icon().orElseThrow();
        }, (launcher, icon) -> {
            return launcherOverrideCtor.apply(launcher, overrideIcon(launcher, icon));
        });
    }

    static <T, U extends Launcher> ApplicationLaunchers normalizeLauncherProperty(
            ApplicationLaunchers appLaunchers,
            Predicate<U> needsNormalization,
            Function<U, Optional<T>> normalizedPropertyValueFinder,
            BiFunction<U, T, U> propertyOverrider) {

        return normalizeLauncherProperty(
                appLaunchers,
                needsNormalization,
                normalizedPropertyValueFinder,
                launcher -> {
                    return normalizedPropertyValueFinder.apply(launcher).orElseThrow();
                },
                propertyOverrider);
    }

    static <T, U extends Launcher> ApplicationLaunchers normalizeLauncherProperty(
            ApplicationLaunchers appLaunchers,
            Predicate<U> needsNormalization,
            Function<U, Optional<T>> normalizedPropertyValueFinder,
            Function<U, T> normalizedPropertyValueGetter,
            BiFunction<U, T, U> propertyOverrider) {

        Objects.requireNonNull(appLaunchers);
        Objects.requireNonNull(needsNormalization);
        Objects.requireNonNull(normalizedPropertyValueFinder);
        Objects.requireNonNull(normalizedPropertyValueGetter);
        Objects.requireNonNull(propertyOverrider);

        boolean[] modified = new boolean[1];

        @SuppressWarnings("unchecked")
        var newLaunchers = appLaunchers.asList().stream().map(launcher -> {
            return (U)launcher;
        }).map(launcher -> {
            if (needsNormalization.test(launcher)) {
                return normalizedPropertyValueFinder.apply(launcher).map(normalizedPropertyValue -> {
                    modified[0] = true;
                    return propertyOverrider.apply(launcher, normalizedPropertyValue);
                }).orElse(launcher);
            } else {
                return launcher;
            }
        }).toList();

        var newMainLauncher = newLaunchers.getFirst();
        if (!needsNormalization.test(newMainLauncher)) {
            // The main launcher doesn't require normalization.
            newLaunchers = newLaunchers.stream().map(launcher -> {
                if (needsNormalization.test(launcher)) {
                    var normalizedPropertyValue = normalizedPropertyValueGetter.apply(newMainLauncher);
                    modified[0] = true;
                    return propertyOverrider.apply(launcher, normalizedPropertyValue);
                } else {
                    return launcher;
                }
            }).toList();
        }

        if (modified[0]) {
            return ApplicationLaunchers.fromList(newLaunchers).orElseThrow();
        } else {
            return appLaunchers;
        }
    }

    static Launcher overrideLauncherStartupInfo(Launcher launcher, LauncherStartupInfo startupInfo) {
        return new Launcher.Stub(
                launcher.name(),
                Optional.of(startupInfo),
                launcher.fileAssociations(),
                launcher.isService(),
                launcher.description(),
                launcher.icon(),
                launcher.defaultIconResourceName(),
                launcher.extraAppImageFileData());
    }

    static Application overrideAppImageLayout(Application app, AppImageLayout appImageLayout) {
        return new Application.Stub(
                app.name(),
                app.description(),
                app.version(),
                app.vendor(),
                app.copyright(),
                app.appDirSources(),
                app.contentDirSources(),
                Objects.requireNonNull(appImageLayout),
                app.runtimeBuilder(),
                app.launchers(),
                app.extraAppImageFileData());
    }

    private static Launcher overrideIcon(Launcher launcher, LauncherIcon icon) {
        return new Launcher.Stub(
                launcher.name(),
                launcher.startupInfo(),
                launcher.fileAssociations(),
                launcher.isService(),
                launcher.description(),
                Optional.of(icon),
                launcher.defaultIconResourceName(),
                launcher.extraAppImageFileData());
    }

    record MainLauncherStartupInfo(String qualifiedClassName) implements LauncherStartupInfo {
        @Override
        public List<String> javaOptions() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> defaultParameters() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Path> classPath() {
            throw new UnsupportedOperationException();
        }
    }

    private record Defaults(String version, String vendor) {
        String copyright() {
            return I18N.format("param.copyright.default", new Date());
        }
    }

    private String name;
    private String description;
    private String version;
    private String vendor;
    private String copyright;
    private Collection<RootedPath> appDirSources;
    private ExternalApplication externalApp;
    private Collection<RootedPath> contentDirSources;
    private AppImageLayout appImageLayout;
    private RuntimeBuilder runtimeBuilder;
    private ApplicationLaunchers launchers;

    private static final Defaults DEFAULTS = new Defaults(
            "1.0",
            I18N.getString("param.vendor.default"));
}
