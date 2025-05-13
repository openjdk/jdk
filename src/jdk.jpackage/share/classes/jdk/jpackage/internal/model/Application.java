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
 * @apiNote All paths of startup configurations of application launchers
 *          returned by {@link #launchers()} call must be relative to the path
 *          returned by {@link #srcDir()} call.
 *
 * @see Package
 */
public interface Application extends BundleSpec {

    /**
     * Gets the name of this application.
     *
     * @return the name of this application
     */
    String name();

    /**
     * Gets the description of this application.
     *
     * @return the description of this application
     */
    String description();

    /**
     * Gets the version of this application.
     *
     * @return the version of this application
     */
    String version();

    /**
     * Gets the vendor of this application.
     *
     * @return the vendor of this application
     */
    String vendor();

    /**
     * Gets the copyright of this application.
     *
     * @return the copyright of this application
     */
    String copyright();

    /**
     * Gets the source directory of this application if available or an empty
     * {@link Optional} instance.
     * <p>
     * Source directory is a directory with the applications's classes and other
     * resources.
     *
     * @return the source directory of this application
     */
    Optional<Path> srcDir();

    /**
     * Gets the input content directories of this application.
     * <p>
     * Contents of the content directories will be copied as-is into the dedicated
     * location of the application image.
     *
     * @see ApplicationLayout#contentDirectory
     *
     * @return the input content directories of this application
     */
    List<Path> contentDirs();

    /**
     * Gets the unresolved app image layout of this application.
     *
     * @return the unresolved app image layout of this application
     */
    AppImageLayout imageLayout();

    /**
     * Gets the unresolved app image layout of this application as
     * {@link ApplicationLayout} type or an empty {@link Optional} instance if the
     * return value of {@link #imageLayout()} call is not an instance of
     * {@link ApplicationLayout} type.
     * <p>
     * Returns an empty {@link Optional} instance if {@link #isRuntime()} returns
     * <code>true</code>.
     *
     * @see #isRuntime
     *
     * @return the unresolved app image layout of this application as
     *         {@link ApplicationLayout}
     */
    default Optional<ApplicationLayout> asApplicationLayout() {
        if (imageLayout() instanceof ApplicationLayout layout) {
            return Optional.of(layout);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Gets the runtime builder of this application if available or an empty
     * {@link Optional} instance.
     *
     * @return the runtime builder of this application
     */
    Optional<RuntimeBuilder> runtimeBuilder();

    /**
     * Gets the name of the root app image directory of this application.
     *
     * @return the name of the root app image directory of this application
     */
    default Path appImageDirName() {
        return Path.of(name());
    }

    /**
     * Gets the application launchers of this application.
     * <p>
     * If the returned list is not empty, the first element in the list is the main
     * launcher.
     * <p>
     * Returns an empty list if {@link #isRuntime()} returns <code>true</code>.
     *
     * @see #mainLauncher()
     * @see #additionalLaunchers()
     * @see #isRuntime()
     *
     * @return the application launchers of this application
     */
    List<Launcher> launchers();

    /**
     * Returns the main application launcher of this application or an empty
     * {@link Optional} instance if the application doesn't have launchers.
     * <p>
     * Returns an empty {@link Optional} instance if {@link #isRuntime()} returns
     * <code>true</code>.
     *
     * @see #launchers()
     * @see #additionalLaunchers()
     * @see #isRuntime()
     *
     * @return the main application launcher of this application
     */
    default Optional<Launcher> mainLauncher() {
        return ApplicationLaunchers.fromList(launchers()).map(ApplicationLaunchers::mainLauncher);
    }

    /**
     * Gets the additional application launchers of this application.
     * <p>
     * Returns an empty list if this application doesn't have additional launchers.
     * <p>
     * Returns an empty list if {@link #isRuntime()} returns <code>true</code>.
     *
     * @see #launchers()
     * @see #mainLauncher()
     * @see #isRuntime()
     *
     * @return the additional application launchers of this application
     */
    default List<Launcher> additionalLaunchers() {
        return ApplicationLaunchers.fromList(launchers()).map(ApplicationLaunchers::additionalLaunchers)
                .orElseGet(Collections::emptyList);
    }

    /**
     * Returns <code>true</code> if this application is Java runtime.
     *
     * @return <code>true</code> if this application is Java runtime
     */
    default boolean isRuntime() {
        return imageLayout() instanceof RuntimeLayout;
    }

    /**
     * Returns <code>true</code> if any of application launchers of this application
     * are configured as services.
     *
     * @see Launcher#isService
     *
     * @return <code>true</code> if any of application launchers of this application
     *         are configured as services
     */
    default boolean isService() {
        return Optional.ofNullable(launchers()).orElseGet(List::of).stream()
                .filter(Launcher::isService).findAny().isPresent();
    }

    /**
     * Gets the additional properties of this application for the application entry
     * in the app image (".jpackage") file.
     *
     * @return the additional properties of this application for the application
     *         entry in ".jpackage" file
     */
    Map<String, String> extraAppImageFileData();

    /**
     * Gets the file associations of all application launchers of this application.
     *
     * @return the file associations of all application launchers of this
     *         application
     *
     * @see Launcher#fileAssociations
     */
    default Stream<FileAssociation> fileAssociations() {
        return launchers().stream().map(Launcher::fileAssociations).flatMap(List::stream);
    }

    /**
     * Default implementation of {@link Application} interface.
     */
    record Stub(String name, String description, String version, String vendor, String copyright, Optional<Path> srcDir,
            List<Path> contentDirs, AppImageLayout imageLayout, Optional<RuntimeBuilder> runtimeBuilder,
            List<Launcher> launchers,  Map<String, String> extraAppImageFileData) implements Application {
    }
}
