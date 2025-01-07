/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.constant.ClassDesc;
import java.nio.file.Path;
import java.util.List;

/**
 * Generic startup configuration of an application launcher.
 *
 * @see Launcher#startupInfo()
 */
public interface LauncherStartupInfo {

    /**
     * Gets the qualified name of the main class of this launcher startup configuration.
     * @return the qualified name of the main class of this launcher startup configuration
     */
    String qualifiedClassName();

    /**
     * Gets the package name of the main class of this launcher startup configuration.
     * @return the package name of the main class of this launcher startup configuration
     */
    default String packageName() {
        return ClassDesc.of(qualifiedClassName()).packageName();
    }

    /**
     * Gets JVM options of this launcher startup configuration.
     * @return the JVM options of this launcher startup configuration
     */
    List<String> javaOptions();

    /**
     * Gets the default parameters for the <code>main(String[] args)</code>
     * method of the main class of this launcher startup configuration.
     *
     * @return the default parameters for the <code>main(String[] args)</code>
     * method of the main class of this launcher startup configuration
     */
    List<String> defaultParameters();

    /**
     * Gets the files and directories that should be put on a classpath for
     * an application launcher this launcher startup configuration applies to.
     * @return the classpath of this launcher startup configuration
     */
    List<Path> classPath();

    /**
     * Default implementation of {@link LauncherStartupInfo} interface.
     */
    record Stub(String qualifiedClassName, List<String> javaOptions,
            List<String> defaultParameters, List<Path> classPath)
            implements LauncherStartupInfo {

    }

    /**
     * Implementation of {@link LauncherStartupInfo} interface in which every method
     * throws {@link UnsupportedOperationException} exception.
     */
    class Unsupported implements LauncherStartupInfo {

        @Override
        public String qualifiedClassName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> javaOptions() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> defaultParameters() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Path> classPath() {
            throw new UnsupportedOperationException();
        }
    }
}
