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
package jdk.jpackage.internal.model;

import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.IOUtils;
import jdk.jpackage.internal.util.PathUtils;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;

public interface Application {

    String name();

    String description();

    String version();

    String vendor();

    String copyright();

    Path srcDir();

    List<Path> contentDirs();

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

    List<Launcher> launchers();

    default Launcher mainLauncher() {
        return Optional.ofNullable(launchers()).map(launchers -> {
            Launcher launcher;
            if (launchers.isEmpty()) {
                launcher = null;
            } else {
                launcher = launchers.getFirst();
            }
            return launcher;
        }).orElse(null);
    }

    default List<Launcher> additionalLaunchers() {
        return Optional.ofNullable(launchers()).map(launchers -> {
            return launchers.subList(1, launchers.size());
        }).orElseGet(List::of);
    }

    default boolean isRuntime() {
        return mainLauncher() == null;
    }

    default boolean isService() {
        return Optional.ofNullable(launchers()).orElseGet(List::of).stream().filter(
                Launcher::isService).findAny().isPresent();
    }

    default ApplicationLayout appLayout() {
        if (isRuntime()) {
            return ApplicationLayout.javaRuntime();
        } else {
            return ApplicationLayout.platformAppImage();
        }
    }

    default Map<String, String> extraAppImageFileData() {
        return Map.of();
    }

    default OverridableResource createLauncherIconResource(Launcher launcher,
            Function<String, OverridableResource> resourceSupplier) {
        final String defaultIconName = launcher.defaultIconResourceName();
        final String resourcePublicName = launcher.executableName() + PathUtils.getSuffix(Path.of(
                defaultIconName));

        var iconType = IconType.getLauncherIconType(launcher.icon());
        if (iconType == IconType.NO_ICON) {
            return null;
        }

        OverridableResource resource = resourceSupplier.apply(defaultIconName)
                .setCategory("icon")
                .setExternal(launcher.icon())
                .setPublicName(resourcePublicName);

        if (iconType == IconType.DEFAULT_OR_RESOURCEDIR_ICON && mainLauncher() != launcher) {
            // No icon explicitly configured for this launcher.
            // Dry-run resource creation to figure out its source.
            final Path nullPath = null;
            if (toSupplier(() -> resource.saveToFile(nullPath)).get() != OverridableResource.Source.ResourceDir) {
                // No icon in resource dir for this launcher, inherit icon
                // configured for the main launcher.
                return createLauncherIconResource(mainLauncher(),
                        resourceSupplier).setLogPublicName(resourcePublicName);
            }
        }

        return resource;
    }

    static record Impl(String name, String description, String version,
            String vendor, String copyright, Path srcDir, List<Path> contentDirs,
            RuntimeBuilder runtimeBuilder, List<Launcher> launchers) implements Application {
        public Impl {
            name = Optional.ofNullable(name).orElseGet(() -> {
                return mainLauncher().name();
            });
            description = Optional.ofNullable(description).orElseGet(Defaults.INSTANCE::description);
            version = Optional.ofNullable(version).orElseGet(Defaults.INSTANCE::version);
            vendor = Optional.ofNullable(vendor).orElseGet(Defaults.INSTANCE::vendor);
            copyright = Optional.ofNullable(copyright).orElseGet(Defaults.INSTANCE::copyright);
        }
    }

    static record Defaults(String description, String version, String vendor, String copyright) {
        private static final Defaults INSTANCE = new Defaults(
                I18N.getString("param.description.default"),
                "1.0",
                I18N.getString("param.vendor.default"),
                MessageFormat.format(I18N.getString("param.copyright.default"), new Date()));
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
        public Path srcDir() {
            return target.srcDir();
        }

        @Override
        public List<Path> contentDirs() {
            return target.contentDirs();
        }

        @Override
        public RuntimeBuilder runtimeBuilder() {
            return target.runtimeBuilder();
        }

        @Override
        public List<Launcher> launchers() {
            return target.launchers();
        }
    }

    static class Unsupported implements Application {

        @Override
        public String name() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String description() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String version() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String vendor() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String copyright() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path srcDir() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Path> contentDirs() {
            throw new UnsupportedOperationException();
        }

        @Override
        public RuntimeBuilder runtimeBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Launcher> launchers() {
            throw new UnsupportedOperationException();
        }
    }

}
