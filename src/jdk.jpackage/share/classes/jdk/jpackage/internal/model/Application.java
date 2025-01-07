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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A generic application for packaging.
 *
 * @apiNote All methods return non-null values.
 *
 * @see Package
 */
public interface Application {

    /**
     * Returns application's name.
     * @return application name
     */
    String name();

    /**
     * Returns application's description.
     * @return application description
     */
    String description();

    /**
     * Returns the application's version.
     * @return application name
     */
    String version();

    /**
     * Returns application's vendor.
     * @return application vendor
     */
    String vendor();

    /**
     * Returns application's copyright.
     * @return application copyright
     */
    String copyright();

    /**
     * Returns the application's source directory if available or an empty {@link Optional} instance.
     * <p>
     * Source directory is a directory with the applications's classes and other resources.
     *
     * @return application source directory
     */
    Optional<Path> srcDir();

    /**
     * Returns the application's input content directories.
     * <p>
     * Contents of the content directories will be copied as-is into the dedicated location of the application image.
     *
     * @see ApplicationLayout#contentDirectory
     *
     * @return application content directories
     */
    List<Path> contentDirs();

    /**
     * Returns app image layout.
     * @return app image layout
     */
    AppImageLayout imageLayout();

    /**
     * Returns app image layout as {@link ApplicationLayout} type or an empty {@link Optional} instance
     * if the return value of {@link #imageLayout()} call is not instance of {@link ApplicationLayout}.
     * <p>
     * Returns an empty {@link Optional} instance if {@link #isRuntime()} returns <code>true</code>.
     *
     * @see #isRuntime
     *
     * @return app image as {@link ApplicationLayout}
     */
    default Optional<ApplicationLayout> asApplicationLayout() {
        if (imageLayout() instanceof ApplicationLayout layout) {
            return Optional.of(layout);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Returns runtime builder if available or an empty {@link Optional} instance.
     * @return runtime builder
     */
    Optional<RuntimeBuilder> runtimeBuilder();

    /**
     * Returns the name of the root app image directory.
     * @return name of the root app image directory.
     */
    default Path appImageDirName() {
        return Path.of(name());
    }

    /**
     * Returns application launchers.
     * <p>
     * If the returned list is not empty, the first element in the list is the main launcher.
     * <p>
     * Returns an empty list if {@link #isRuntime()} returns <code>true</code>.
     *
     * @see #mainLauncher()
     * @see #additionalLaunchers()
     * @see #isRuntime()
     *
     * @return application launchers
     */
    List<Launcher> launchers();

    /**
     * Returns the main application launcher or an empty {@link Optional} instance if the application doesn't have launchers.
     * <p>
     * Returns an empty {@link Optional} instance if {@link #isRuntime()} returns <code>true</code>.
     *
     * @see #launchers()
     * @see #additionalLaunchers()
     * @see #isRuntime()
     *
     * @return main application launcher
     */
    default Optional<Launcher> mainLauncher() {
        return ApplicationLaunchers.fromList(launchers()).map(ApplicationLaunchers::mainLauncher);
    }

    /**
     * Returns additional application launchers.
     * <p>
     * Returns an empty list if there are no additional application launchers.
     * <p>
     * Returns an empty list if {@link #isRuntime()} returns <code>true</code>.
     *
     * @see #launchers()
     * @see #mainLauncher()
     * @see #isRuntime()
     *
     * @return additional application launchers
     */
    default List<Launcher> additionalLaunchers() {
        return ApplicationLaunchers.fromList(launchers()).map(ApplicationLaunchers::additionalLaunchers).orElseGet(Collections::emptyList);
    }

    /**
     * Returns <code>true</code> if the application for packaging is Java runtime.
     * @return <code>true</code> if the application for packaging is Java runtime.
     */
    default boolean isRuntime() {
        return imageLayout() instanceof RuntimeLayout;
    }

    /**
     * Returns <code>true</code> if any of application launchers are configured as services.
     *
     * @see Launcher#isService
     *
     * @return <code>true</code> if any of application launchers are configured as services
     */
    default boolean isService() {
        return Optional.ofNullable(launchers()).orElseGet(List::of).stream().filter(
                Launcher::isService).findAny().isPresent();
    }

    /**
     * Returns additional properties for application launcher entries in the app image (".jpackage") file.
     *
     * @see Launcher#isService
     *
     * @return additional properties for application launcher entries in ".jpackage" file
     */
    default Map<String, String> extraAppImageFileData() {
        return Map.of();
    }

    /**
     * Returns file associations of all application launchers.
     *
     * @return file associations of all application launchers
     *
     * @see Launcher#fileAssociations
     */
    default Stream<FileAssociation> fileAssociations() {
        return launchers().stream().map(Launcher::fileAssociations).flatMap(List::stream);
    }

    /**
     * Default implementation of {@link Application} interface.
     */
    record Stub(String name, String description, String version, String vendor,
            String copyright, Optional<Path> srcDir, List<Path> contentDirs,
            AppImageLayout imageLayout, Optional<RuntimeBuilder> runtimeBuilder,
            List<Launcher> launchers) implements Application {
    }

    /**
     * Implementation of {@link Application} interface in which every method
     * throws {@link UnsupportedOperationException} exception.
     */
    class Unsupported implements Application {

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
        public Optional<Path> srcDir() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Path> contentDirs() {
            throw new UnsupportedOperationException();
        }

        @Override
        public AppImageLayout imageLayout() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<RuntimeBuilder> runtimeBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Launcher> launchers() {
            throw new UnsupportedOperationException();
        }
    }

}
