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

import static jdk.jpackage.internal.I18N.buildConfigException;
import static jdk.jpackage.internal.util.function.ThrowingConsumer.toConsumer;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.CustomLauncherIcon;
import jdk.jpackage.internal.model.FileAssociation;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.Launcher.Stub;
import jdk.jpackage.internal.model.LauncherIcon;
import jdk.jpackage.internal.model.LauncherStartupInfo;

final class LauncherBuilder {

    Launcher create() throws ConfigException {
        CustomLauncherIcon.fromLauncherIcon(icon)
                .map(CustomLauncherIcon::path)
                .ifPresent(toConsumer(LauncherBuilder::validateIcon));

        final var fa = createFileAssociations(faSources, Optional.ofNullable(faTraits).orElse(DEFAULT_FA_TRAITS));

        Objects.requireNonNull(defaultIconResourceName);

        final var nonNullName = deriveNonNullName();

        return new Stub(nonNullName, Optional.ofNullable(startupInfo), fa,
                isService, Optional.ofNullable(description).orElse(nonNullName),
                Optional.ofNullable(icon), defaultIconResourceName,
                Optional.ofNullable(extraAppImageFileData).orElseGet(Map::of));
    }

    LauncherBuilder name(String v) {
        name = v;
        return this;
    }

    LauncherBuilder startupInfo(LauncherStartupInfo v) {
        startupInfo = v;
        return this;
    }

    LauncherBuilder faGroups(List<FileAssociationGroup> v) {
        v.forEach(Objects::requireNonNull);
        faSources = v;
        return this;
    }

    LauncherBuilder faTraits(FileAssociationTraits v) {
        faTraits = v;
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

    LauncherBuilder icon(LauncherIcon v) {
        icon = v;
        return this;
    }

    LauncherBuilder defaultIconResourceName(String v) {
        defaultIconResourceName = v;
        return this;
    }

    LauncherBuilder extraAppImageFileData(Map<String, String> v) {
        extraAppImageFileData = v;
        return this;
    }

    private String deriveNonNullName() {
        return Optional.ofNullable(name).orElseGet(() -> startupInfo.simpleClassName());
    }

    static void validateIcon(Path icon) throws ConfigException {
        switch (OperatingSystem.current()) {
            case WINDOWS -> {
                if (!icon.getFileName().toString().toLowerCase().endsWith(".ico")) {
                    throw buildConfigException().message("message.icon-not-ico", icon).create();
                }
            }
            case LINUX -> {
                if (!icon.getFileName().toString().endsWith(".png")) {
                    throw buildConfigException().message("message.icon-not-png", icon).create();
                }
            }
            case MACOS -> {
                if (!icon.getFileName().toString().endsWith(".icns")) {
                    throw buildConfigException().message("message.icon-not-icns", icon).create();
                }
            }
            default -> {
                throw new UnsupportedOperationException();
            }
        }
    }

    record FileAssociationTraits() {
    }

    private static List<FileAssociation> createFileAssociations(
            List<FileAssociationGroup> groups, FileAssociationTraits faTraits) throws ConfigException {

        Objects.requireNonNull(groups);
        Objects.requireNonNull(faTraits);

        int faID = 1;
        for (var group : groups) {
            final var scanResult = new FileAssociationScaner().scan(group.items());

            if (!scanResult.extensionsWithMultipleMimeTypes().isEmpty()) {
                throw buildConfigException()
                        .message("error.too-many-content-types-for-file-association", faID)
                        .advice("error.too-many-content-types-for-file-association.advice", faID)
                        .create();
            }

            faID++;
        }

        return FileAssociationGroup.flatMap(groups.stream()).toList();
    }

    private String name;
    private LauncherStartupInfo startupInfo;
    private List<FileAssociationGroup> faSources = List.of();
    private FileAssociationTraits faTraits;
    private boolean isService;
    private String description;
    private LauncherIcon icon;
    private String defaultIconResourceName;
    private Map<String, String> extraAppImageFileData;

    private static final FileAssociationTraits DEFAULT_FA_TRAITS = new FileAssociationTraits();
}
