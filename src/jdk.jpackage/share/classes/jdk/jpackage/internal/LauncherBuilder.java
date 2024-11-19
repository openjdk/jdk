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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import static jdk.internal.util.OperatingSystem.LINUX;
import static jdk.internal.util.OperatingSystem.MACOS;
import static jdk.internal.util.OperatingSystem.WINDOWS;
import static jdk.jpackage.internal.I18N.buildConfigException;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.FileAssociation;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.Launcher.Stub;
import jdk.jpackage.internal.model.LauncherStartupInfo;
import jdk.jpackage.internal.util.function.ExceptionBox;
import static jdk.jpackage.internal.util.function.ExceptionBox.rethrowUnchecked;
import static jdk.jpackage.internal.util.function.ThrowingConsumer.toConsumer;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;
import static jdk.jpackage.internal.model.ConfigException.rethrowConfigException;

final class LauncherBuilder {

    Launcher create() throws ConfigException {
        validateIcon(icon);
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

    LauncherBuilder faSources(List<FileAssociationGroup> v) {
        faSources = v;
        return this;
    }

    LauncherBuilder faPrediacate(Predicate<FileAssociation> v) {
        faPrediacate = v;
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

    static boolean isFileAssociationValid(FileAssociation src) {
        toConsumer(LauncherBuilder::verifyFileAssociation).accept(src);

        return Optional.ofNullable(src.extension()).isPresent();
    }

    static void validateIcon(Path icon) throws ConfigException {
        if (icon == null || icon.toString().isEmpty()) {
            return;
        }

        switch (OperatingSystem.current()) {
            case WINDOWS -> {
                if (!icon.getFileName().toString().toLowerCase().endsWith(".ico")) {
                    throw buildConfigException()
                            .message("message.icon-not-ico", icon).create();
                }
            }
            case LINUX -> {
                if (!icon.getFileName().toString().endsWith(".png")) {
                    throw buildConfigException()
                            .message("message.icon-not-png", icon).create();
                }
            }
            case MACOS -> {
                if (!icon.getFileName().toString().endsWith(".icns")) {
                    throw buildConfigException()
                            .message("message.icon-not-icns", icon).create();
                }
            }
        }
    }

    private Stream<FileAssociation> createFileAssociations(
            Stream<FileAssociationGroup> sources) throws ConfigException {

        var sourcesAsArray = sources.toArray(FileAssociationGroup[]::new);

        var stream = IntStream.range(0, sourcesAsArray.length).mapToObj(idx -> {
            return Map.entry(idx + 1, sourcesAsArray[idx]);
        }).map(entry -> {
            try {
                var faGroup = FileAssociationGroup.filter(faPrediacate).apply(entry.getValue());
                if (faGroup.isEmpty()) {
                    return null;
                } else {
                    return Map.entry(entry.getKey(), faGroup);
                }
            } catch (ExceptionBox ex) {
                if (ex.getCause() instanceof ConfigException cfgException) {
                    throw rethrowUnchecked(buildConfigException()
                            .cause(cfgException.getCause())
                            .message(cfgException.getMessage(), entry.getKey())
                            .advice(cfgException.getAdvice(), entry.getKey())
                            .create());
                } else {
                    throw ex;
                }
            }
        }).filter(Objects::nonNull).peek(entry -> {
            var faGroup = entry.getValue();
            if (faGroup.items().size() != faGroup.items().stream().map(FileAssociation::extension).distinct().count()) {
                throw rethrowUnchecked(buildConfigException()
                        .message("error.too-many-content-types-for-file-association", entry.getKey())
                        .advice("error.too-many-content-types-for-file-association.advice", entry.getKey())
                        .create());
            }
        }).map(entry -> {
            return entry.getValue().items().stream();
        }).flatMap(x -> x);

        try {
            return stream.toList().stream();
        } catch (RuntimeException ex) {
            throw rethrowConfigException(ex);
        }
    }

    private static void verifyFileAssociation(FileAssociation src) throws ConfigException {
        if (Optional.ofNullable(src.mimeType()).isEmpty()) {
            throw buildConfigException()
                    .noformat()
                    .message("error.no-content-types-for-file-association")
                    .advice("error.no-content-types-for-file-association.advice")
                    .create();
        }
    }

    private String name;
    private LauncherStartupInfo startupInfo;
    private List<FileAssociationGroup> faSources;
    private Predicate<FileAssociation> faPrediacate = LauncherBuilder::isFileAssociationValid;
    private boolean isService;
    private String description;
    private Path icon;
}
