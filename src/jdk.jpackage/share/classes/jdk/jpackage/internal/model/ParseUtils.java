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
package jdk.jpackage.internal.model;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Collection of functions to create instances of types defined in this package from strings.
 */
public final class ParseUtils {

    private ParseUtils() {
    }

    public static LauncherShortcut parseLauncherShortcutForMainLauncher(String str) {
        return parse(str, LauncherShortcutStartupDirectory.APP_DIR).map(LauncherShortcut::new).orElseThrow(IllegalArgumentException::new);
    }

    public static LauncherShortcut parseLauncherShortcutForAddLauncher(String str) {
        return parse(str, LauncherShortcutStartupDirectory.values()).map(LauncherShortcut::new).orElseGet(() -> {
            if (Boolean.valueOf(str)) {
                return new LauncherShortcut(LauncherShortcutStartupDirectory.DEFAULT);
            } else {
                return new LauncherShortcut();
            }
        });
    }

    private static Optional<LauncherShortcutStartupDirectory> parse(String str, LauncherShortcutStartupDirectory... recognizedValues) {
        Objects.requireNonNull(str);
        return Stream.of(recognizedValues).filter(v -> {
            return str.equals(v.asStringValue());
        }).findFirst();
    }
}
