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

package jdk.jpackage.internal.cli;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.UUID;
import jdk.jpackage.internal.model.LauncherShortcut;
import jdk.jpackage.internal.model.ParseUtils;


final class StandardValueConverter {

    private StandardValueConverter() {
    }

    static ValueConverter<String> identityConv() {
        return IDENTITY_CONV;
    }

    static ValueConverter<Path> pathConv() {
        return PATH_CONV;
    }

    static ValueConverter<UUID> uuidConv() {
        return UUID_CONV;
    }

    static ValueConverter<Boolean> booleanConv() {
        return BOOLEAN_CONV;
    }

    static ValueConverter<LauncherShortcut> mainLauncherShortcutConv() {
        return MAIN_LAUNCHER_SHORTCUT_CONV;
    }

    static ValueConverter<LauncherShortcut> addLauncherShortcutConv() {
        return ADD_LAUNCHER_SHORTCUT_CONV;
    }

    private static final ValueConverter<String> IDENTITY_CONV = ValueConverter.create(x -> x, String.class);

    private static final ValueConverter<Path> PATH_CONV = ValueConverter.create(str -> {
        try {
            return Path.of(str);
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException(ex);
        }
    }, Path.class);

    private static final ValueConverter<UUID> UUID_CONV = ValueConverter.create(UUID::fromString, UUID.class);

    private static final ValueConverter<Boolean> BOOLEAN_CONV = ValueConverter.create(Boolean::valueOf, Boolean.class);

    private static final ValueConverter<LauncherShortcut> MAIN_LAUNCHER_SHORTCUT_CONV = ValueConverter.create(
            ParseUtils::parseLauncherShortcutForMainLauncher, LauncherShortcut.class);

    private static final ValueConverter<LauncherShortcut> ADD_LAUNCHER_SHORTCUT_CONV = ValueConverter.create(
            ParseUtils::parseLauncherShortcutForAddLauncher, LauncherShortcut.class);
}
