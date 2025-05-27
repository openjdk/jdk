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

import java.util.Map;
import jdk.jpackage.internal.util.CompositeProxy;

/**
 * Linux application launcher.
 * <p>
 * Use {@link #create} method to create objects implementing this interface.
 */
public interface LinuxLauncher extends Launcher, LinuxLauncherMixin {

    @Override
    default Map<String, String> extraAppImageFileData() {
        return shortcut().map(v -> {
            return Map.of("shortcut", Boolean.toString(v));
        }).orElseGet(Map::of);
    }

    /**
     * Constructs {@link LinuxLauncher} instance from the given
     * {@link Launcher} and {@link LinuxLauncherMixin} instances.
     *
     * @param launcher the generic application launcher
     * @param mixin Linux-specific details supplementing the generic application launcher
     * @return the proxy dispatching calls to the given objects
     */
    public static LinuxLauncher create(Launcher launcher, LinuxLauncherMixin mixin) {
        return CompositeProxy.create(LinuxLauncher.class, launcher, mixin);
    }
}
