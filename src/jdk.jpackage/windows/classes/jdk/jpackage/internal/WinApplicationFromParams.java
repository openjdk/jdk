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

import jdk.jpackage.internal.model.WinLauncher;
import jdk.jpackage.internal.model.WinApplication;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import static jdk.jpackage.internal.ApplicationFromParams.createBundlerParam;
import static jdk.jpackage.internal.StandardBundlerParam.MENU_HINT;
import static jdk.jpackage.internal.StandardBundlerParam.SHORTCUT_HINT;
import jdk.jpackage.internal.model.WinApplication.Impl;
import static jdk.jpackage.internal.model.WinLauncher.WinShortcut.WIN_SHORTCUT_DESKTOP;
import static jdk.jpackage.internal.model.WinLauncher.WinShortcut.WIN_SHORTCUT_START_MENU;

final class WinApplicationFromParams {

    private static WinLauncher createLauncher(Map<String, ? super Object> params) {
        var launcher = new LauncherFromParams().create(params);

        boolean isConsole = CONSOLE_HINT.fetchFrom(params);

        var shortcuts = Map.of(WIN_SHORTCUT_DESKTOP, List.of(SHORTCUT_HINT,
                WIN_SHORTCUT_HINT), WIN_SHORTCUT_START_MENU, List.of(MENU_HINT,
                        WIN_MENU_HINT)).entrySet().stream().filter(e -> {
                    var shortcutParams = e.getValue();
                    if (params.containsKey(shortcutParams.get(0).getID())) {
                        // This is an explicit shortcut configuration for an addition launcher
                        return shortcutParams.get(0).fetchFrom(params);
                    } else {
                        return shortcutParams.get(1).fetchFrom(params);
                    }
                }).map(Map.Entry::getKey).collect(Collectors.toSet());

        return new WinLauncher.Impl(launcher, isConsole, shortcuts);
    }

    static final BundlerParamInfo<WinApplication> APPLICATION = createBundlerParam(params -> {
        var app = new ApplicationFromParams(WinApplicationFromParams::createLauncher).create(params);
        return new Impl(app);
    });

    private static final BundlerParamInfo<Boolean> WIN_MENU_HINT = new BundlerParamInfo<>(
            Arguments.CLIOptions.WIN_MENU_HINT.getId(),
            Boolean.class,
            p -> false,
            // valueOf(null) is false,
            // and we actually do want null in some cases
            (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ? false : Boolean.valueOf(s));

    private static final BundlerParamInfo<Boolean> WIN_SHORTCUT_HINT = new BundlerParamInfo<>(
            Arguments.CLIOptions.WIN_SHORTCUT_HINT.getId(),
            Boolean.class,
            p -> false,
            // valueOf(null) is false,
            // and we actually do want null in some cases
            (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ? false : Boolean.valueOf(s));

    public static final BundlerParamInfo<Boolean> CONSOLE_HINT = new BundlerParamInfo<>(
            Arguments.CLIOptions.WIN_CONSOLE_HINT.getId(),
            Boolean.class,
            params -> false,
            // valueOf(null) is false,
            // and we actually do want null in some cases
            (s, p) -> (s == null
            || "null".equalsIgnoreCase(s)) ? true : Boolean.valueOf(s));
}
