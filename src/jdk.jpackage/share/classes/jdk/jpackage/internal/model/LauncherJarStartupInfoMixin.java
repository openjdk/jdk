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

import java.nio.file.Path;

/**
 * Details of Linux application launcher startup configuration using non-modular jar file.
 */
public interface LauncherJarStartupInfoMixin {

    /**
     * Gets the path to the input jar file.
     * @return the path to the input jar file
     */
    Path jarPath();

    /**
     * Returns <code>true</code> if the input jar file has <code>Main-Class</code> entry in the manifest.
     * @return <code>true</code> if the input jar file has <code>Main-Class</code> entry in the manifest
     */
    boolean isJarWithMainClass();

    /**
     * Default implementation of {@link LauncherJarStartupInfoMixin} interface.
     */
    record Stub(Path jarPath, boolean isJarWithMainClass) implements LauncherJarStartupInfoMixin {}
}
