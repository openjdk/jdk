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

/**
 * The directory in which to run an application launcher when it is started from
 * a shortcut.
 */
public enum LauncherShortcutStartupDirectory {

    /**
     * Platform-specific default value.
     * <p>
     * On Windows, it indicates that the startup directory should be the package's
     * installation directory.
     * <p>
     * On Linux, it indicates that a shortcut doesn't have the startup directory
     * configured explicitly.
     */
    DEFAULT("true"),

    /**
     * The 'app' directory in the installed application app image. This is the
     * directory that is referenced with {@link ApplicationLayout#appDirectory()}
     * method.
     */
    APP_DIR("app-dir");

    LauncherShortcutStartupDirectory(String stringValue) {
        this.stringValue = Objects.requireNonNull(stringValue);
    }

    public String asStringValue() {
        return stringValue;
    }

    private final String stringValue;
}
