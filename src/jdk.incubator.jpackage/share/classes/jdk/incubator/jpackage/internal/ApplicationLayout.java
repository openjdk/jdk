/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.util.Map;


/**
 * Application directory layout.
 */
public final class ApplicationLayout implements PathGroup.Facade<ApplicationLayout> {
    enum PathRole {
        RUNTIME, APP, LAUNCHERS, DESKTOP, APP_MODS, DLLS, RELEASE
    }

    ApplicationLayout(Map<Object, Path> paths) {
        data = new PathGroup(paths);
    }

    private ApplicationLayout(PathGroup data) {
        this.data = data;
    }

    @Override
    public PathGroup pathGroup() {
        return data;
    }

    @Override
    public ApplicationLayout resolveAt(Path root) {
        return new ApplicationLayout(pathGroup().resolveAt(root));
    }

    /**
     * Path to launchers directory.
     */
    public Path launchersDirectory() {
        return pathGroup().getPath(PathRole.LAUNCHERS);
    }

    /**
     * Path to directory with dynamic libraries.
     */
    public Path dllDirectory() {
        return pathGroup().getPath(PathRole.DLLS);
    }

    /**
     * Path to application data directory.
     */
    public Path appDirectory() {
        return pathGroup().getPath(PathRole.APP);
    }

    /**
     * Path to Java runtime directory.
     */
    public Path runtimeDirectory() {
        return pathGroup().getPath(PathRole.RUNTIME);
    }

    /**
     * Path to application mods directory.
     */
    public Path appModsDirectory() {
        return pathGroup().getPath(PathRole.APP_MODS);
    }

    /**
     * Path to directory with application's desktop integration files.
     */
    public Path destktopIntegrationDirectory() {
        return pathGroup().getPath(PathRole.DESKTOP);
    }

    /**
     * Path to release file in the Java runtime directory.
     */
    public Path runtimeRelease() {
        return pathGroup().getPath(PathRole.RELEASE);
    }

    static ApplicationLayout linuxAppImage() {
        return new ApplicationLayout(Map.of(
                PathRole.LAUNCHERS, Path.of("bin"),
                PathRole.APP, Path.of("lib/app"),
                PathRole.RUNTIME, Path.of("lib/runtime"),
                PathRole.DESKTOP, Path.of("lib"),
                PathRole.DLLS, Path.of("lib"),
                PathRole.APP_MODS, Path.of("lib/app/mods"),
                PathRole.RELEASE, Path.of("lib/runtime/release")
        ));
    }

    static ApplicationLayout windowsAppImage() {
        return new ApplicationLayout(Map.of(
                PathRole.LAUNCHERS, Path.of(""),
                PathRole.APP, Path.of("app"),
                PathRole.RUNTIME, Path.of("runtime"),
                PathRole.DESKTOP, Path.of(""),
                PathRole.DLLS, Path.of(""),
                PathRole.APP_MODS, Path.of("app/mods"),
                PathRole.RELEASE, Path.of("runtime/release")
        ));
    }

    static ApplicationLayout macAppImage() {
        return new ApplicationLayout(Map.of(
                PathRole.LAUNCHERS, Path.of("Contents/MacOS"),
                PathRole.APP, Path.of("Contents/app"),
                PathRole.RUNTIME, Path.of("Contents/runtime"),
                PathRole.DESKTOP, Path.of("Contents/Resources"),
                PathRole.DLLS, Path.of("Contents/MacOS"),
                PathRole.APP_MODS, Path.of("Contents/app/mods"),
                PathRole.RELEASE, Path.of("Contents/runtime/Contents/Home/release")
        ));
    }

    public static ApplicationLayout platformAppImage() {
        if (Platform.isWindows()) {
            return windowsAppImage();
        }

        if (Platform.isLinux()) {
            return linuxAppImage();
        }

        if (Platform.isMac()) {
            return macAppImage();
        }

        throw Platform.throwUnknownPlatformError();
    }

    public static ApplicationLayout javaRuntime() {
        return new ApplicationLayout(Map.of(PathRole.RUNTIME, Path.of("")));
    }

    private final PathGroup data;
}
