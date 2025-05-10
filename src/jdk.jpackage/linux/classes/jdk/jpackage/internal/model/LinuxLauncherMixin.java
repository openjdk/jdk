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

import java.util.Optional;

/**
 * Details of Linux application launcher.
 */
public interface LinuxLauncherMixin {

    /**
     * Gets the start menu shortcut setting of this application launcher.
     * <p>
     * Returns <code>true</code> if this application launcher was requested to have
     * the start menu shortcut.
     * <p>
     * Returns <code>false</code> if this application launcher was requested not to
     * have the start menu shortcut.
     * <p>
     * Returns an empty {@link Optional} instance if there was no request about the
     * start menu shortcut for this application launcher.
     *
     * @return the start menu shortcut setting of this application launcher
     */
    Optional<Boolean> shortcut();

    /**
     * Default implementation of {@link LinuxLauncherMixin} interface.
     */
    record Stub(Optional<Boolean> shortcut) implements LinuxLauncherMixin {
    }
}
