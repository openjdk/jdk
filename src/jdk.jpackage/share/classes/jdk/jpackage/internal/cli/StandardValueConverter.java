/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Function;
import jdk.jpackage.internal.model.LauncherShortcut;
import jdk.jpackage.internal.model.ParseUtils;
import jdk.jpackage.internal.util.RootedPath;


final class StandardValueConverter {

    private StandardValueConverter() {
    }

    static ValueConverter<String, String> identityConv() {
        return IDENTITY_CONV;
    }

    static ValueConverter<String, Path> pathConv() {
        return PATH_CONV;
    }

    static ValueConverter<String, UUID> uuidConv() {
        return UUID_CONV;
    }

    static ValueConverter<String, Boolean> booleanConv() {
        return BOOLEAN_CONV;
    }

    static ValueConverter<String, LauncherShortcut> mainLauncherShortcutConv() {
        return MAIN_LAUNCHER_SHORTCUT_CONV;
    }

    static ValueConverter<String, LauncherShortcut> addLauncherShortcutConv() {
        return ADD_LAUNCHER_SHORTCUT_CONV;
    }

    static ExplodedPathConverterBuilder explodedPathConverter() {
        return new ExplodedPathConverterBuilder();
    }

    static final class ExplodedPathConverterBuilder {
        private ExplodedPathConverterBuilder() {
        }

        ValueConverter<Path, RootedPath[]> create() {
            return ValueConverter.create(path -> {
                return explodePath(path, withPathFileName);
            }, RootedPath[].class);
        }

        ExplodedPathConverterBuilder withPathFileName(boolean v) {
            withPathFileName = v;
            return this;
        }

        ExplodedPathConverterBuilder withPathFileName() {
            return withPathFileName(true);
        }

        private boolean withPathFileName;
    }

    private static final ValueConverter<String, String> IDENTITY_CONV = ValueConverter.create(x -> x, String.class);

    private static final ValueConverter<String, Path> PATH_CONV = ValueConverter.create(str -> {
        try {
            return Path.of(str);
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException(ex);
        }
    }, Path.class);

    private static final ValueConverter<String, UUID> UUID_CONV = ValueConverter.create(UUID::fromString, UUID.class);

    private static final ValueConverter<String, Boolean> BOOLEAN_CONV = ValueConverter.create(Boolean::valueOf, Boolean.class);

    private static final ValueConverter<String, LauncherShortcut> MAIN_LAUNCHER_SHORTCUT_CONV = ValueConverter.create(
            ParseUtils::parseLauncherShortcutForMainLauncher, LauncherShortcut.class);

    private static final ValueConverter<String, LauncherShortcut> ADD_LAUNCHER_SHORTCUT_CONV = ValueConverter.create(
            ParseUtils::parseLauncherShortcutForAddLauncher, LauncherShortcut.class);

    private static RootedPath[] explodePath(Path path, boolean withPathFileName) throws Exception {

        Function<Path, RootedPath> mapper;
        if (withPathFileName) {
            mapper = RootedPath.toRootedPath(path.getParent());
        } else {
            mapper = RootedPath.toRootedPath(path);
        }

        RootedPath[] items;
        try (var walk = Files.walk(path)) {
            items = walk.map(mapper).toArray(RootedPath[]::new);
        } catch (IOException ex) {
            // IOException is not a converter error, it is a converting error, so map it into IAE.
            throw new IllegalArgumentException(ex);
        }

        return items;
    }
}
