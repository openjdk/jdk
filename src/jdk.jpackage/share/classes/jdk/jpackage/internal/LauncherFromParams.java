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
import static jdk.jpackage.internal.StandardBundlerParam.ARGUMENTS;
import static jdk.jpackage.internal.StandardBundlerParam.DESCRIPTION;
import static jdk.jpackage.internal.StandardBundlerParam.FA_CONTENT_TYPE;
import static jdk.jpackage.internal.StandardBundlerParam.FA_DESCRIPTION;
import static jdk.jpackage.internal.StandardBundlerParam.FA_EXTENSIONS;
import static jdk.jpackage.internal.StandardBundlerParam.FA_ICON;
import static jdk.jpackage.internal.StandardBundlerParam.FILE_ASSOCIATIONS;
import static jdk.jpackage.internal.StandardBundlerParam.ICON;
import static jdk.jpackage.internal.StandardBundlerParam.JAVA_OPTIONS;
import static jdk.jpackage.internal.StandardBundlerParam.LAUNCHER_AS_SERVICE;
import static jdk.jpackage.internal.StandardBundlerParam.LAUNCHER_DATA;
import static jdk.jpackage.internal.StandardBundlerParam.NAME;
import static jdk.jpackage.internal.StandardBundlerParam.PREDEFINED_APP_IMAGE;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.CustomLauncherIcon;
import jdk.jpackage.internal.model.DefaultLauncherIcon;
import jdk.jpackage.internal.model.FileAssociation;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.LauncherIcon;

record LauncherFromParams(Optional<BiFunction<FileAssociation, Map<String, ? super Object>, FileAssociation>> faExtension) {

    LauncherFromParams {
        Objects.requireNonNull(faExtension);
    }

    LauncherFromParams() {
        this(Optional.empty());
    }

    Launcher create(Map<String, ? super Object> params) throws ConfigException {
        final var builder = new LauncherBuilder().defaultIconResourceName(defaultIconResourceName());

        DESCRIPTION.copyInto(params, builder::description);
        builder.icon(toLauncherIcon(ICON.findIn(params).orElse(null)));
        LAUNCHER_AS_SERVICE.copyInto(params, builder::isService);
        NAME.copyInto(params, builder::name);

        if (PREDEFINED_APP_IMAGE.findIn(params).isEmpty()) {
            final var startupInfoBuilder = new LauncherStartupInfoBuilder();

            startupInfoBuilder.launcherData(LAUNCHER_DATA.fetchFrom(params));
            ARGUMENTS.copyInto(params, startupInfoBuilder::defaultParameters);
            JAVA_OPTIONS.copyInto(params, startupInfoBuilder::javaOptions);

            builder.startupInfo(startupInfoBuilder.create());
        }

        final var faParamsList = FILE_ASSOCIATIONS.findIn(params).orElseGet(List::of);

        final var faGroups = IntStream.range(0, faParamsList.size()).mapToObj(idx -> {
            final var faParams = faParamsList.get(idx);
            return toSupplier(() -> {
                final var faGroupBuilder = FileAssociationGroup.build();

                if (OperatingSystem.current() == OperatingSystem.MACOS) {
                    FA_DESCRIPTION.copyInto(faParams, faGroupBuilder::description);
                } else {
                    faGroupBuilder.description(FA_DESCRIPTION.findIn(faParams).orElseGet(() -> {
                        return String.format("%s association", toSupplier(builder::create).get().name());
                    }));
                }

                FA_ICON.copyInto(faParams, faGroupBuilder::icon);
                FA_EXTENSIONS.copyInto(faParams, faGroupBuilder::extensions);
                FA_CONTENT_TYPE.copyInto(faParams, faGroupBuilder::mimeTypes);

                final var faID = idx + 1;

                final FileAssociationGroup faGroup;
                try {
                    faGroup = faGroupBuilder.create();
                } catch (FileAssociationGroup.FileAssociationNoMimesException ex) {
                    throw buildConfigException()
                            .message("error.no-content-types-for-file-association", faID)
                            .advice("error.no-content-types-for-file-association.advice", faID)
                            .create();
                }

                if (faExtension.isPresent()) {
                    return new FileAssociationGroup(faGroup.items().stream().map(fa -> {
                        return faExtension.get().apply(fa, faParams);
                    }).toList());
                } else {
                    return faGroup;
                }
            }).get();
        }).toList();

        return builder.faGroups(faGroups).create();
    }

    private static LauncherIcon toLauncherIcon(Path launcherIconPath) {
        if (launcherIconPath == null) {
            return DefaultLauncherIcon.INSTANCE;
        } else if (launcherIconPath.toString().isEmpty()) {
            return null;
        } else {
            return CustomLauncherIcon.create(launcherIconPath);
        }
    }

    private static String defaultIconResourceName() {
        switch (OperatingSystem.current()) {
            case WINDOWS -> {
                return "JavaApp.ico";
            }
            case LINUX -> {
                return "JavaApp.png";
            }
            case MACOS -> {
                return "JavaApp.icns";
            }
            default -> {
                throw new UnsupportedOperationException();
            }
        }
    }
}
