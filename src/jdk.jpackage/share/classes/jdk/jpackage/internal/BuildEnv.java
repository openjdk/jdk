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
package jdk.jpackage.internal;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import jdk.jpackage.internal.model.AppImageLayout;
import jdk.jpackage.internal.model.ApplicationLayout;

/**
 * Build environment.
 */
interface BuildEnv {

    /**
     * Returns root directory for intermediate build files.
     *
     * @return the root directory for intermediate build files
     */
    Path buildRoot();

    /**
     * Returns <code>true</code> if the build should be verbose output.
     *
     * @return <code>true</code> if the build should be verbose output
     */
    boolean verbose();

    /**
     * Returns the path of the resource directory or an empty {@link Optional}
     * instance if none is configured with the build.
     *
     * @return the path of the resource directory or an empty {@link Optional}
     *         instance if non is configured with the build
     */
    Optional<Path> resourceDir();

    /**
     * Returns the path of the app image directory of this build.
     *
     * @return the path of the app image directory of this build
     */
    default Path appImageDir() {
        return appImageLayout().rootDirectory();
    }

    /**
     * Returns resolved app image layout of the app image directory. The return
     * layout is resolved at {@link #appImageDir()} path.
     *
     * @return the resolved app image layout of the app image directory
     */
    AppImageLayout appImageLayout();

    default Optional<ApplicationLayout> asApplicationLayout() {
        if (appImageLayout() instanceof ApplicationLayout layout) {
            return Optional.of(layout);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Returns a path to a directory for intermediate configuration files.
     * @return the path to the directory for intermediate configuration files
     */
    default Path configDir() {
        return buildRoot().resolve("config");
    }

    /**
     * Creates an {@link OverridableResource} instance for the given resource name.
     *
     * @param defaultName the resource name
     * @return the {@link OverridableResource} instance wrapping a resource with the
     *         given name
     */
    OverridableResource createResource(String defaultName);

    static BuildEnv withAppImageDir(BuildEnv env, Path appImageDir) {
        return ((Internal.DefaultBuildEnv)env).copyWithAppImageDir(appImageDir);
    }

    static BuildEnv withAppImageLayout(BuildEnv env, AppImageLayout appImageLayout) {
        return ((Internal.DefaultBuildEnv)env).copyWithAppImageLayout(appImageLayout);
    }

    static BuildEnv create(Path buildRoot, Optional<Path> resourceDir, boolean verbose,
            Class<?> resourceLocator, AppImageLayout appImageLayout) {
        return new Internal.DefaultBuildEnv(buildRoot, resourceDir, verbose,
                resourceLocator, appImageLayout);
    }

    static final class Internal {
        private record DefaultBuildEnv(Path buildRoot, Optional<Path> resourceDir,
                boolean verbose, Class<?> resourceLocator,
                AppImageLayout appImageLayout) implements BuildEnv {

            DefaultBuildEnv {
                Objects.requireNonNull(buildRoot);
                Objects.requireNonNull(resourceDir);
                Objects.requireNonNull(resourceLocator);
                Objects.requireNonNull(appImageLayout);
            }

            DefaultBuildEnv copyWithAppImageDir(Path v) {
                return copyWithAppImageLayout(appImageLayout.unresolve().resolveAt(v));
            }

            DefaultBuildEnv copyWithAppImageLayout(AppImageLayout v) {
                return new DefaultBuildEnv(buildRoot, resourceDir, verbose, resourceLocator, v);
            }

            @Override
            public OverridableResource createResource(String defaultName) {
                final OverridableResource resource;
                if (defaultName != null) {
                    resource = new OverridableResource(defaultName, resourceLocator);
                } else {
                    resource = new OverridableResource();
                }
                return resourceDir.map(resource::setResourceDir).orElse(resource);
            }
        }
    }
}
