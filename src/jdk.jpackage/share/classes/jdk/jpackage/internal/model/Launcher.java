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

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jdk.jpackage.internal.resources.ResourceLocator;

public interface Launcher {

    String name();

    default String executableName() {
        return name();
    }

    default Optional<String> executableSuffix() {
        return Optional.empty();
    }

    default String executableNameWithSuffix() {
        return executableName() + executableSuffix().orElse("");
    }

    Optional<LauncherStartupInfo> startupInfo();

    List<FileAssociation> fileAssociations();

    boolean isService();

    String description();

    default InputStream executableResource() {
        return ResourceLocator.class.getResourceAsStream("jpackageapplauncher");
    }

    default Map<String, String> extraAppImageFileData() {
        return Map.of();
    }

    /**
     * Icon for the launcher.
     */
    Optional<LauncherIcon> icon();

    default boolean hasIcon() {
        return icon().isPresent();
    }

    default boolean hasDefaultIcon() {
        return icon().flatMap(DefaultLauncherIcon::fromLauncherIcon).isPresent();
    }

    default boolean hasCustomIcon() {
        return icon().flatMap(CustomLauncherIcon::fromLauncherIcon).isPresent();
    }

    String defaultIconResourceName();

    record Stub(String name, Optional<LauncherStartupInfo> startupInfo,
            List<FileAssociation> fileAssociations, boolean isService,
            String description, Optional<LauncherIcon> icon,
            String defaultIconResourceName) implements Launcher {
    }

    class Unsupported implements Launcher {

        @Override
        public String name() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<LauncherStartupInfo> startupInfo() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<FileAssociation> fileAssociations() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isService() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String description() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<LauncherIcon> icon() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String defaultIconResourceName() {
            throw new UnsupportedOperationException();
        }
    }
}
