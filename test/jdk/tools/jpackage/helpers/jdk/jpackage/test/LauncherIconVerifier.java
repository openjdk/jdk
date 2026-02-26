/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Path;
import java.util.Optional;

public final class LauncherIconVerifier {
    public LauncherIconVerifier() {
    }

    public LauncherIconVerifier setLauncherName(String v) {
        launcherName = v;
        return this;
    }

    public LauncherIconVerifier setExpectedIcon(Path v) {
        expectedIcon = v;
        expectedDefault = false;
        return this;
    }

    public LauncherIconVerifier setExpectedDefaultIcon() {
        expectedIcon = null;
        expectedDefault = true;
        return this;
    }

    public LauncherIconVerifier setExpectedNoIcon() {
        return setExpectedIcon(null);
    }

    public LauncherIconVerifier verifyFileInAppImageOnly(boolean v) {
        verifyFileInAppImageOnly = true;
        return this;
    }

    public boolean expectDefaultIcon() {
        return expectedDefault;
    }

    public Optional<Path> expectIcon() {
        return Optional.ofNullable(expectedIcon);
    }

    public void applyTo(JPackageCommand cmd) throws IOException {
        final String label = Optional.ofNullable(launcherName).map(v -> {
            return String.format("[%s]", v);
        }).orElse("main");

        Path iconPath = cmd.appLayout().desktopIntegrationDirectory().resolve(iconFileName(cmd));

        if (TKit.isWindows()) {
            TKit.assertPathExists(iconPath, false);
            if (!verifyFileInAppImageOnly) {
                WinExecutableIconVerifier.verifyLauncherIcon(cmd, launcherName, expectedIcon, expectedDefault);
            }
        } else if (expectedDefault) {
            TKit.assertFileExists(iconPath);
        } else if (expectedIcon == null) {
            TKit.assertPathExists(iconPath, false);
        } else {
            TKit.assertFileExists(iconPath);
            if (!verifyFileInAppImageOnly) {
                TKit.assertSameFileContent(expectedIcon, iconPath,
                        String.format(
                        "Check icon file [%s] of %s launcher is a copy of source icon file [%s]",
                        iconPath, label, expectedIcon));
            }
        }
    }

    private Path iconFileName(JPackageCommand cmd) {
        if (TKit.isLinux()) {
            return LinuxHelper.getLauncherIconFileName(cmd, launcherName);
        } else {
            return Path.of(Optional.ofNullable(launcherName).orElseGet(cmd::name) + TKit.ICON_SUFFIX);
        }
    }

    private String launcherName;
    private Path expectedIcon;
    private boolean expectedDefault;
    private boolean verifyFileInAppImageOnly;
}
