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

import static java.util.stream.Collectors.toMap;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import jdk.jpackage.internal.resources.ResourceLocator;
import jdk.jpackage.internal.util.CompositeProxy;

public interface WinLauncher extends Launcher, WinLauncherMixin {

    @Override
    default Optional<String> executableSuffix() {
        return Optional.of(".exe");
    }

    @Override
    default InputStream executableResource() {
        return ResourceLocator.class.getResourceAsStream(
                isConsole() ? "jpackageapplauncher.exe" : "jpackageapplauncherw.exe");
    }

    @Override
    default Map<String, String> extraAppImageFileData() {
        return shortcuts().stream().collect(toMap(WinShortcut::name, v -> Boolean.toString(true)));
    }

    public static WinLauncher create(Launcher launcher, WinLauncherMixin mixin) {
        return CompositeProxy.create(WinLauncher.class, launcher, mixin);
    }
}
