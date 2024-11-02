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
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import static jdk.internal.util.OperatingSystem.LINUX;
import static jdk.internal.util.OperatingSystem.MACOS;
import static jdk.internal.util.OperatingSystem.WINDOWS;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.FileAssociation;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.Launcher.Stub;
import jdk.jpackage.internal.model.LauncherStartupInfo;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;

final class LauncherBuilder {

    Launcher create() throws ConfigException {
        if (icon != null) {
            validateIcon(icon);
        }
        var fa = toFunction(this::createFileAssociations).apply(faSources.stream()).toList();
        return new Stub(name, startupInfo, fa, isService, description, icon);
    }

    LauncherBuilder name(String v) {
        name = v;
        return this;
    }

    LauncherBuilder startupInfo(LauncherStartupInfo v) {
        startupInfo = v;
        return this;
    }

    LauncherBuilder faSources(List<FileAssociation> v) {
        faSources = v;
        return this;
    }

    LauncherBuilder faMapper(Function<FileAssociation, Optional<FileAssociation>> v) {
        faMapper = v;
        return this;
    }

    LauncherBuilder isService(boolean v) {
        isService = v;
        return this;
    }

    LauncherBuilder description(String v) {
        description = v;
        return this;
    }

    LauncherBuilder icon(Path v) {
        icon = v;
        return this;
    }

    static Optional<FileAssociation> mapFileAssociation(FileAssociation src) {
        var mimeType = src.mimeType();
        if (mimeType == null) {
            return Optional.empty();
        }

        var extension = src.extension();
        if (extension == null) {
            return Optional.empty();
        }

        return Optional.of(new FileAssociation.Stub(src.description(), src.icon(), mimeType, extension));
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

    private Stream<FileAssociation> createFileAssociations(
            Stream<FileAssociation> sources) throws ConfigException {
        var fas = sources.map(faMapper).filter(Optional::isPresent).map(Optional::get).toList();

        // Check extension to mime type relationship is 1:1
        var mimeTypeToExtension = fas.stream().collect(groupingBy(
                FileAssociation::extension, mapping(FileAssociation::mimeType,
                        toList())));
        for (var entry : mimeTypeToExtension.entrySet()) {
            if (entry.getValue().size() != 1) {
                var extension = entry.getKey();
                throw ConfigException.build().message(
                        "error.fa-extension-with-multiple-mime-types", extension).create();
            }
        }

        return fas.stream();
    }

    private String name;
    private LauncherStartupInfo startupInfo;
    private List<FileAssociation> faSources;
    private Function<FileAssociation, Optional<FileAssociation>> faMapper = LauncherBuilder::mapFileAssociation;
    private boolean isService;
    private String description;
    private Path icon;
}
