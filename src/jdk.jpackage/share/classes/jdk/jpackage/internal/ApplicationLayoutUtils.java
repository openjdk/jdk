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

import java.nio.file.Path;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.model.ApplicationLayout;


final class ApplicationLayoutUtils {

    public static final ApplicationLayout PLATFORM_APPLICATION_LAYOUT;

    private static final ApplicationLayout WIN_APPLICATION_LAYOUT = ApplicationLayout.build()
            .setAll("")
            .appDirectory("app")
            .runtimeDirectory("runtime")
            .appModsDirectory(Path.of("app", "mods"))
            .create();

    private static final ApplicationLayout MAC_APPLICATION_LAYOUT = ApplicationLayout.build()
            .launchersDirectory("Contents/MacOS")
            .appDirectory("Contents/app")
            .runtimeDirectory("Contents/runtime/Contents/Home")
            .desktopIntegrationDirectory("Contents/Resources")
            .appModsDirectory("Contents/app/mods")
            .contentDirectory("Contents")
            .create();

    private static final ApplicationLayout LINUX_APPLICATION_LAYOUT = ApplicationLayout.build()
            .launchersDirectory("bin")
            .appDirectory("lib/app")
            .runtimeDirectory("lib/runtime")
            .desktopIntegrationDirectory("lib")
            .appModsDirectory("lib/app/mods")
            .contentDirectory("lib")
            .create();

    static {
        switch (OperatingSystem.current()) {
            case WINDOWS -> PLATFORM_APPLICATION_LAYOUT = WIN_APPLICATION_LAYOUT;
            case MACOS -> PLATFORM_APPLICATION_LAYOUT = MAC_APPLICATION_LAYOUT;
            case LINUX -> PLATFORM_APPLICATION_LAYOUT = LINUX_APPLICATION_LAYOUT;
            default -> {
                throw new UnsupportedOperationException();
            }
        }
    }
}
