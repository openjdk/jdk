/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.jpackage.internal;

import java.text.MessageFormat;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.BiFunction;
import java.util.function.Function;

class WindowsBundlerParam<T> extends StandardBundlerParam<T> {

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.incubator.jpackage.internal.resources.WinResources");

    WindowsBundlerParam(String id, Class<T> valueType,
            Function<Map<String, ? super Object>, T> defaultValueFunction,
            BiFunction<String,
            Map<String, ? super Object>, T> stringConverter) {
        super(id, valueType, defaultValueFunction, stringConverter);
    }

    static final BundlerParamInfo<String> INSTALLER_FILE_NAME =
            new StandardBundlerParam<> (
            "win.installerName",
            String.class,
            params -> {
                String nm = APP_NAME.fetchFrom(params);
                if (nm == null) return null;

                String version = VERSION.fetchFrom(params);
                if (version == null) {
                    return nm;
                } else {
                    return nm + "-" + version;
                }
            },
            (s, p) -> s);

    static final StandardBundlerParam<String> MENU_GROUP =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.WIN_MENU_GROUP.getId(),
                    String.class,
                    params -> I18N.getString("param.menu-group.default"),
                    (s, p) -> s
            );

    static final BundlerParamInfo<Boolean> INSTALLDIR_CHOOSER =
            new StandardBundlerParam<> (
            Arguments.CLIOptions.WIN_DIR_CHOOSER.getId(),
            Boolean.class,
            params -> Boolean.FALSE,
            (s, p) -> Boolean.valueOf(s)
    );

    static final BundlerParamInfo<String> WINDOWS_INSTALL_DIR =
            new StandardBundlerParam<>(
            "windows-install-dir",
            String.class,
            params -> {
                 String dir = INSTALL_DIR.fetchFrom(params);
                 if (dir != null) {
                     if (dir.contains(":") || dir.contains("..")) {
                         Log.error(MessageFormat.format(I18N.getString(
                                "message.invalid.install.dir"), dir,
                                APP_NAME.fetchFrom(params)));
                     } else {
                        if (dir.startsWith("\\")) {
                             dir = dir.substring(1);
                        }
                        if (dir.endsWith("\\")) {
                             dir = dir.substring(0, dir.length() - 1);
                        }
                        return dir;
                     }
                 }
                 return APP_NAME.fetchFrom(params); // Default to app name
             },
            (s, p) -> s
    );
}
