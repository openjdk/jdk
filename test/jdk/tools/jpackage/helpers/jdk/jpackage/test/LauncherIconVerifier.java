/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package jdk.jpackage.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LauncherIconVerifier {
    public LauncherIconVerifier() {
    }

    public LauncherIconVerifier setLauncherName(String v) {
        launcherName = v;
        return this;
    }

    public LauncherIconVerifier setExpectedIcon(Path v) {
        expectedIcon = v;
        return this;
    }

    public LauncherIconVerifier setExpectedDefaultIcon() {
        expectedIcon = getDefaultIcon();
        return this;
    }

    public void applyTo(JPackageCommand cmd) throws IOException {
        final String curLauncherName;
        final String label;
        if (launcherName == null) {
            curLauncherName = cmd.name();
            label = "main";
        } else {
            curLauncherName = launcherName;
            label = String.format("[%s]", launcherName);
        }

        Path iconPath = cmd.appLayout().destktopIntegrationDirectory().resolve(
                curLauncherName + TKit.ICON_SUFFIX);

        if (expectedIcon == null) {
            TKit.assertPathExists(iconPath, false);
            return;
        }

        TKit.assertFileExists(iconPath);
        TKit.assertTrue(-1 == Files.mismatch(expectedIcon, iconPath),
                String.format(
                        "Check icon file [%s] of %s launcher is a copy of source icon file [%s]",
                        iconPath, label, expectedIcon));
    }

    public static Path getDefaultIcon() {
        final String[] components;
        if (TKit.isOSX()) {
            components = new String[] { "macosx", "java.icns" };
        } else if (TKit.isLinux()) {
            components = new String[] { "linux", "java32.png" };
        } else if (TKit.isWindows()) {
            components = new String[] { "windows", "java48.ico" };
        } else {
            throw TKit.throwUnknownPlatformError();
        }

        return TKit.SRC_ROOT.resolve(Path.of(components[0],
                "classes/jdk/incubator/jpackage/internal/resources", components[1]));
    }

    private String launcherName;
    private Path expectedIcon;
}
