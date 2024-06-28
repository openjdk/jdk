/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import static jdk.jpackage.internal.Functional.ThrowingSupplier.toSupplier;

interface Application {

    String name();

    String description();

    String version();

    String vendor();

    String copyright();

    Path predefinedRuntimeImage();

    RuntimeBuilder runtimeBuilder();

    default Path appImageDirName() {
        switch (OperatingSystem.current()) {
            case MACOS -> {
                return Path.of(name() + ".app");
            }
            default -> {
                return Path.of(name());
            }
        }
    }

    Launcher mainLauncher();

    default boolean isRuntime() {
        return mainLauncher() == null;
    }

    List<Launcher> additionalLaunchers();

    default boolean isService() {
        return allLaunchers().stream().filter(Launcher::isService).findAny().isPresent();
    }

    default ApplicationLayout appLayout() {
        if (isRuntime()) {
            return ApplicationLayout.javaRuntime();
        } else {
            return ApplicationLayout.platformAppImage();
        }
    }

    default List<Launcher> allLaunchers() {
        return Optional.ofNullable(mainLauncher()).map(main -> {
            return Stream.concat(Stream.of(main), additionalLaunchers().stream()).toList();
        }).orElse(List.of());
    }

    default OverridableResource createLauncherIconResource(Launcher launcher, String defaultIconName,
            Function<String, OverridableResource> resourceSupplier) {
        final String resourcePublicName = launcher.name() + IOUtils.getSuffix(Path.of(
                defaultIconName));

        var iconType = Internal.getLauncherIconType(launcher.icon());
        if (iconType == Internal.IconType.NoIcon) {
            return null;
        }

        OverridableResource resource = resourceSupplier.apply(defaultIconName)
                .setCategory("icon")
                .setExternal(launcher.icon())
                .setPublicName(resourcePublicName);

        if (iconType == Internal.IconType.DefaultOrResourceDirIcon && mainLauncher() != launcher) {
            // No icon explicitly configured for this launcher.
            // Dry-run resource creation to figure out its source.
            final Path nullPath = null;
            if (toSupplier(() -> resource.saveToFile(nullPath)).get() != OverridableResource.Source.ResourceDir) {
                // No icon in resource dir for this launcher, inherit icon
                // configured for the main launcher.
                return createLauncherIconResource(mainLauncher(), defaultIconName, resourceSupplier)
                        .setLogPublicName(resourcePublicName);
            }
        }

        return resource;
    }

    static record Impl(String name, String description, String version, String vendor,
            String copyright, Path predefinedRuntimeImage, RuntimeBuilder runtimeBuilder,
            Launcher mainLauncher, List<Launcher> additionalLaunchers) implements Application {

    }

    static class Proxy<T extends Application> extends ProxyBase<T> implements Application {

        Proxy(T target) {
            super(target);
        }

        @Override
        public String name() {
            return target.name();
        }

        @Override
        public String description() {
            return target.description();
        }

        @Override
        public String version() {
            return target.version();
        }

        @Override
        public String vendor() {
            return target.vendor();
        }

        @Override
        public String copyright() {
            return target.copyright();
        }

        @Override
        public Path predefinedRuntimeImage() {
            return target.predefinedRuntimeImage();
        }

        @Override
        public RuntimeBuilder runtimeBuilder() {
            return target.runtimeBuilder();
        }

        @Override
        public Launcher mainLauncher() {
            return target.mainLauncher();
        }

        @Override
        public List<Launcher> additionalLaunchers() {
            return target.additionalLaunchers();
        }
    }

    static class Internal {

        private enum IconType {
            DefaultOrResourceDirIcon, CustomIcon, NoIcon
        };

        private static IconType getLauncherIconType(Path iconPath) {
            if (iconPath == null) {
                return IconType.DefaultOrResourceDirIcon;
            }

            if (iconPath.toFile().getName().isEmpty()) {
                return IconType.NoIcon;
            }

            return IconType.CustomIcon;
        }
    }
}
