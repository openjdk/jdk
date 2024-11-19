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

import jdk.jpackage.internal.model.Package;
import jdk.jpackage.internal.model.PackagerException;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.FileUtils;
import jdk.jpackage.internal.util.PathUtils;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;


final class AppImageBuilder {

    static final class Builder {

        Builder addItem(AppImageItem v) {
            Objects.requireNonNull(v);
            Optional.ofNullable(customAppImageItemGroups.get(curGroup)).orElseGet(() -> {
                List<AppImageItem> items = new ArrayList<>();
                customAppImageItemGroups.put(curGroup, items);
                return items;
            }).add(v);
            return this;
        }

        Builder itemGroup(AppImageItemGroup v) {
            Objects.requireNonNull(v);
            curGroup = v;
            return this;
        }

        Builder excludeDirFromCopying(Path path) {
            Objects.requireNonNull(path);

            if (excludeCopyDirs == null) {
                excludeCopyDirs = new ArrayList<>();
            }
            excludeCopyDirs.add(path);
            return this;
        }

        AppImageBuilder create(Application app) {
            return new AppImageBuilder(app, excludeCopyDirs, customAppImageItemGroups);
        }

        AppImageBuilder create(Package pkg) {
            return new AppImageBuilder(pkg, excludeCopyDirs, customAppImageItemGroups);
        }

        private List<Path> excludeCopyDirs;
        private AppImageItemGroup curGroup = AppImageItemGroup.END;
        private Map<AppImageItemGroup, List<AppImageItem>> customAppImageItemGroups = new HashMap<>();
    }

    static Builder build() {
        return new Builder();
    }

    enum AppImageItemGroup {
        BEGIN,
        RUNTIME,
        CONTENT,
        LAUNCHERS,
        APP_IMAGE_FILE,
        END
    }

    @FunctionalInterface
    interface AppImageItem {
        void write(BuildEnv env, Application app, ApplicationLayout appLayout) throws IOException, PackagerException;
    }

    private AppImageBuilder(Application app, ApplicationLayout appLayout,
            List<Path> excludeCopyDirs, boolean withAppImageFile,
            Map<AppImageItemGroup, List<AppImageItem>> customAppImageItemGroups) {
        this.app = Objects.requireNonNull(app);
        this.appLayout = Objects.requireNonNull(appLayout);

        appImageItemGroups = new HashMap<>();
        appImageItemGroups.put(AppImageItemGroup.RUNTIME, List.of(
                createRuntimeAppImageItem()));
        appImageItemGroups.put(AppImageItemGroup.CONTENT, List.of(
                createContentAppImageItem(
                        Optional.ofNullable(excludeCopyDirs).orElseGet(List::of))));
        appImageItemGroups.put(AppImageItemGroup.LAUNCHERS, List.of(
                createLaunchersAppImageItem()));
        if (withAppImageFile) {
            appImageItemGroups.put(AppImageItemGroup.APP_IMAGE_FILE, List.of(
                    createAppImageFileAppImageItem()));
        }

        for (var e : customAppImageItemGroups.entrySet()) {
            var group = e.getKey();
            var mutableItems = Optional.ofNullable(appImageItemGroups.get(group)).map(items -> {
                return new ArrayList<>(items);
            }).orElseGet(() -> {
                return new ArrayList<>();
            });
            mutableItems.addAll(e.getValue());
            appImageItemGroups.put(group, mutableItems);
        }
    }

    private AppImageBuilder(Application app, List<Path> excludeCopyDirs,
            Map<AppImageItemGroup, List<AppImageItem>> customAppImageItemGroups) {
        this(app, app.asApplicationLayout(), excludeCopyDirs, true,
                customAppImageItemGroups);
    }

    private AppImageBuilder(Package pkg, List<Path> excludeCopyDirs,
            Map<AppImageItemGroup, List<AppImageItem>> customAppImageItemGroups) {
        this(pkg.app(), pkg.asPackageApplicationLayout(), excludeCopyDirs, false,
                customAppImageItemGroups);
    }

    private static void copyRecursive(Path srcDir, Path dstDir,
            List<Path> excludeDirs) throws IOException {
        srcDir = srcDir.toAbsolutePath();

        List<Path> excludes = new ArrayList<>();

        for (var path : excludeDirs) {
            if (Files.isDirectory(path)) {
                if (path.startsWith(srcDir) && !Files.isSameFile(path, srcDir)) {
                    excludes.add(path);
                }
            }
        }

        FileUtils.copyRecursive(srcDir, dstDir.toAbsolutePath(), excludes);
    }

    void execute(BuildEnv env) throws IOException, PackagerException {
        var resolvedAppLayout = appLayout.resolveAt(env.appImageDir());
        for (var group : AppImageItemGroup.values()) {
            for (var appImageItem : Optional.ofNullable(appImageItemGroups.get(group)).orElseGet(List::of)) {
                appImageItem.write(env, app, resolvedAppLayout);
            }
        }
    }

    static OverridableResource createLauncherIconResource(Application app,
            Launcher launcher,
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

        if (iconType == IconType.DEFAULT_OR_RESOURCEDIR_ICON && app.mainLauncher() != launcher) {
            // No icon explicitly configured for this launcher.
            // Dry-run resource creation to figure out its source.
            final Path nullPath = null;
            if (toSupplier(() -> resource.saveToFile(nullPath)).get() != OverridableResource.Source.ResourceDir) {
                // No icon in resource dir for this launcher, inherit icon
                // configured for the main launcher.
                return createLauncherIconResource(app, app.mainLauncher(),
                        resourceSupplier).setLogPublicName(resourcePublicName);
            }
        }

        return resource;
    }

    private static AppImageItem createRuntimeAppImageItem() {
        return (env, app, appLayout) -> {
            app.runtimeBuilder().createRuntime(appLayout);
        };
    }

    private static AppImageItem createAppImageFileAppImageItem() {
        return (env, app, appLayout) -> {
            new AppImageFile2(app).save(appLayout);
        };
    }

    private static AppImageItem createContentAppImageItem(List<Path> excludeCopyDirs) {
        return (env, app, appLayout) -> {
            var excludeCandidates = Stream.concat(
                    excludeCopyDirs.stream(),
                    Stream.of(env.buildRoot(), env.appImageDir())
            ).map(Path::toAbsolutePath).toList();

            if (app.srcDir() != null) {
                copyRecursive(app.srcDir(), appLayout.appDirectory(), excludeCandidates);
            }

            for (var srcDir : Optional.ofNullable(app.contentDirs()).orElseGet(List::of)) {
                copyRecursive(srcDir,
                        appLayout.contentDirectory().resolve(srcDir.getFileName()),
                        excludeCandidates);
            }
        };
    }

    private static AppImageItem createLaunchersAppImageItem() {
        return (env, app, appLayout) -> {
            for (var launcher : app.launchers()) {
                // Create corresponding .cfg file
                new CfgFile(app, launcher).create(appLayout, appLayout);

                // Copy executable to launchers folder
                Path executableFile = appLayout.launchersDirectory().resolve(
                        launcher.executableNameWithSuffix());
                try (var in = launcher.executableResource()) {
                    Files.createDirectories(executableFile.getParent());
                    Files.copy(in, executableFile);
                }

                executableFile.toFile().setExecutable(true);
            }
        };
    }

    private enum IconType {
        DEFAULT_OR_RESOURCEDIR_ICON, CUSTOM_ICON, NO_ICON;

        public static IconType getLauncherIconType(Path iconPath) {
            if (iconPath == null) {
                return DEFAULT_OR_RESOURCEDIR_ICON;
            }
            if (iconPath.toFile().getName().isEmpty()) {
                return NO_ICON;
            }
            return CUSTOM_ICON;
        }
    }

    private final Application app;
    private final ApplicationLayout appLayout;
    private final Map<AppImageItemGroup, List<AppImageItem>> appImageItemGroups;
}
