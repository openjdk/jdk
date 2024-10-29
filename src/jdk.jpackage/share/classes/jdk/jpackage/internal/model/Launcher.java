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
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jdk.internal.util.OperatingSystem;
import static jdk.internal.util.OperatingSystem.LINUX;
import static jdk.internal.util.OperatingSystem.WINDOWS;
import static jdk.jpackage.internal.util.function.ThrowingConsumer.toConsumer;
import jdk.jpackage.internal.resources.ResourceLocator;

public interface Launcher {

    String name();

    default String executableName() {
        return name();
    }

    default String executableSuffix() {
        return null;
    }

    default String executableNameWithSuffix() {
        return executableName() + Optional.ofNullable(executableSuffix()).orElse("");
    }

    LauncherStartupInfo startupInfo();

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
     * Returns path to icon to assign for the launcher.
     *
     * Null for the default or resource directory icon, empty path for no icon,
     * other value for custom icon.
     */
    Path icon();
    
    default String defaultIconResourceName() {
        return null;
    }

    static record Impl(String name, LauncherStartupInfo startupInfo,
            List<FileAssociation> fileAssociations, boolean isService,
            String description, Path icon) implements Launcher {
        public Impl {
            if (icon != null && !icon.toString().isEmpty()) {
                toConsumer(Launcher::validateIcon).accept(icon);
            }
        }
    }
    
    static void validateIcon(Path icon) throws ConfigException {
        switch (OperatingSystem.current()) {
            case WINDOWS -> {
                if (!icon.getFileName().toString().toLowerCase().endsWith(".ico")) {
                    throw ConfigException.build().message("message.icon-not-ico", icon).create();
                }
            }
            case LINUX -> {
                if (!icon.getFileName().toString().endsWith(".png")) {
                    throw ConfigException.build().message("message.icon-not-png", icon).create();
                }
            }
            case MACOS -> {
                if (!icon.getFileName().toString().endsWith(".icns")) {
                    throw ConfigException.build().message("message.icon-not-icns", icon).create();
                }
            }
        }
    }

    static class Proxy<T extends Launcher> extends ProxyBase<T> implements Launcher {

        Proxy(T target) {
            super(target);
        }

        @Override
        public String name() {
            return target.name();
        }

        @Override
        public LauncherStartupInfo startupInfo() {
            return target.startupInfo();
        }

        @Override
        public List<FileAssociation> fileAssociations() {
            return target.fileAssociations();
        }

        @Override
        public boolean isService() {
            return target.isService();
        }

        @Override
        public String description() {
            return target.description();
        }

        @Override
        public Path icon() {
            return target.icon();
        }
    }

    static class Unsupported implements Launcher {

        @Override
        public String name() {
            throw new UnsupportedOperationException();
        }

        @Override
        public LauncherStartupInfo startupInfo() {
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
        public Path icon() {
            throw new UnsupportedOperationException();
        }
    }
}
