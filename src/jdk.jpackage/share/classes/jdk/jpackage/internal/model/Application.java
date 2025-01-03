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

public interface Application {

    String name();

    String description();

    String version();

    String vendor();

    String copyright();

    Optional<Path> srcDir();

    List<Path> contentDirs();

    AppImageLayout imageLayout();

    default Optional<ApplicationLayout> asApplicationLayout() {
        if (imageLayout() instanceof ApplicationLayout layout) {
            return Optional.of(layout);
        } else {
            return Optional.empty();
        }
    }

    Optional<RuntimeBuilder> runtimeBuilder();

    default Path appImageDirName() {
        return Path.of(name());
    }

    List<Launcher> launchers();

    default Optional<Launcher> mainLauncher() {
        return ApplicationLaunchers.fromList(launchers()).map(ApplicationLaunchers::mainLauncher);
    }

    default List<Launcher> additionalLaunchers() {
        return ApplicationLaunchers.fromList(launchers()).map(ApplicationLaunchers::additionalLaunchers).orElseGet(Collections::emptyList);
    }

    default boolean isRuntime() {
        return imageLayout() instanceof RuntimeLayout;
    }

    default boolean isService() {
        return Optional.ofNullable(launchers()).orElseGet(List::of).stream().filter(
                Launcher::isService).findAny().isPresent();
    }

    default Map<String, String> extraAppImageFileData() {
        return Map.of();
    }

    default Stream<FileAssociation> fileAssociations() {
        return launchers().stream().map(Launcher::fileAssociations).flatMap(List::stream);
    }

    record Stub(String name, String description, String version, String vendor,
            String copyright, Optional<Path> srcDir, List<Path> contentDirs,
            AppImageLayout imageLayout, Optional<RuntimeBuilder> runtimeBuilder,
            List<Launcher> launchers) implements Application {
    }

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
