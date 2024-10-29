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
import jdk.jpackage.internal.model.LauncherStartupInfo;
import jdk.jpackage.internal.model.Launcher;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import jdk.jpackage.internal.model.Launcher.Impl;
import static jdk.jpackage.internal.StandardBundlerParam.APP_NAME;
import static jdk.jpackage.internal.StandardBundlerParam.DESCRIPTION;
import static jdk.jpackage.internal.StandardBundlerParam.FA_CONTENT_TYPE;
import static jdk.jpackage.internal.StandardBundlerParam.FA_DESCRIPTION;
import static jdk.jpackage.internal.StandardBundlerParam.FA_EXTENSIONS;
import static jdk.jpackage.internal.StandardBundlerParam.FA_ICON;
import static jdk.jpackage.internal.StandardBundlerParam.FILE_ASSOCIATIONS;
import static jdk.jpackage.internal.StandardBundlerParam.LAUNCHER_AS_SERVICE;
import static jdk.jpackage.internal.StandardBundlerParam.PREDEFINED_APP_IMAGE;
import static jdk.jpackage.internal.StandardBundlerParam.ICON;
import jdk.jpackage.internal.model.FileAssociation;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;

record LauncherFromParams(Function<FileAssociation, Optional<FileAssociation>> faSupplier) {

    LauncherFromParams {
        Objects.requireNonNull(faSupplier);
    }
    
    LauncherFromParams() {
        this(FileAssociation::create);
    }

    Launcher create(Map<String, ? super Object> params) {
        var name = APP_NAME.fetchFrom(params);

        LauncherStartupInfo startupInfo = null;
        if (PREDEFINED_APP_IMAGE.fetchFrom(params) == null) {
            startupInfo = LauncherStartupInfoFromParams.create(params);
        }

        var isService = LAUNCHER_AS_SERVICE.fetchFrom(params);
        var desc = DESCRIPTION.fetchFrom(params);
        var icon = ICON.fetchFrom(params);

        var faStream = Optional.ofNullable(
                FILE_ASSOCIATIONS.fetchFrom(params)).orElseGet(List::of).stream().map(faParams -> {
            var faDesc = FA_DESCRIPTION.fetchFrom(faParams);
            var faIcon = FA_ICON.fetchFrom(faParams);
            var faExtensions = FA_EXTENSIONS.fetchFrom(faParams);
            var faMimeTypes = FA_CONTENT_TYPE.fetchFrom(faParams);

            return faExtensions.stream().map(faExtension -> {
                return faMimeTypes.stream().map(faMimeType -> {
                    return faSupplier.apply(new FileAssociation() {
                        @Override
                        public String description() {
                            return faDesc;
                        }

                        @Override
                        public Path icon() {
                            return faIcon;
                        }

                        @Override
                        public String mimeType() {
                            return faMimeType;
                        }

                        @Override
                        public String extension() {
                            return faExtension;
                        }
                    });
                });
            }).flatMap(x -> x);
        }).flatMap(x -> x).filter(Optional::isPresent).map(Optional::get);

        var fa = toSupplier(() -> FileAssociation.create(faStream)).get().toList();

        return new Impl(name, startupInfo, fa, isService, desc, icon);
    }
}
