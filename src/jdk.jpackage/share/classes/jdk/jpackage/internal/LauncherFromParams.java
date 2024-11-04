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

import java.util.List;
import jdk.jpackage.internal.model.Launcher;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import static jdk.jpackage.internal.StandardBundlerParam.APP_NAME;
import static jdk.jpackage.internal.StandardBundlerParam.ARGUMENTS;
import static jdk.jpackage.internal.StandardBundlerParam.DESCRIPTION;
import static jdk.jpackage.internal.StandardBundlerParam.FA_CONTENT_TYPE;
import static jdk.jpackage.internal.StandardBundlerParam.FA_DESCRIPTION;
import static jdk.jpackage.internal.StandardBundlerParam.FA_EXTENSIONS;
import static jdk.jpackage.internal.StandardBundlerParam.FA_ICON;
import static jdk.jpackage.internal.StandardBundlerParam.FILE_ASSOCIATIONS;
import static jdk.jpackage.internal.StandardBundlerParam.LAUNCHER_AS_SERVICE;
import static jdk.jpackage.internal.StandardBundlerParam.PREDEFINED_APP_IMAGE;
import static jdk.jpackage.internal.StandardBundlerParam.ICON;
import static jdk.jpackage.internal.StandardBundlerParam.JAVA_OPTIONS;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.FileAssociation;

record LauncherFromParams(Predicate<FileAssociation> faPredicate) {

    LauncherFromParams {
        Objects.requireNonNull(faPredicate);
    }

    LauncherFromParams() {
        this(LauncherBuilder::isFileAssociationValid);
    }

    Launcher create(Map<String, ? super Object> params) throws ConfigException {
        var builder = new LauncherBuilder()
                .description(DESCRIPTION.fetchFrom(params))
                .icon(ICON.fetchFrom(params))
                .isService(LAUNCHER_AS_SERVICE.fetchFrom(params))
                .name(APP_NAME.fetchFrom(params))
                .faPrediacate(faPredicate);

        if (PREDEFINED_APP_IMAGE.fetchFrom(params) == null) {
            builder.startupInfo(new LauncherStartupInfoBuilder()
                    .launcherData(StandardBundlerParam.LAUNCHER_DATA.fetchFrom(params))
                    .defaultParameters(ARGUMENTS.fetchFrom(params))
                    .javaOptions(JAVA_OPTIONS.fetchFrom(params))
                    .create());
        }

        var faSources = Optional.ofNullable(
                FILE_ASSOCIATIONS.fetchFrom(params)).orElseGet(List::of).stream().map(faParams -> {
            return FileAssociationGroup.build()
                    .description(FA_DESCRIPTION.fetchFrom(faParams))
                    .icon(FA_ICON.fetchFrom(faParams))
                    .extensions(FA_EXTENSIONS.fetchFrom(faParams))
                    .mimeTypes(FA_CONTENT_TYPE.fetchFrom(faParams))
                    .create();
        }).toList();

        return builder.faSources(faSources).create();
    }
}
