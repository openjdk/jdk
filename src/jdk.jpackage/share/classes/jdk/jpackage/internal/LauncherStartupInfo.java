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
package jdk.jpackage.internal;

import java.nio.file.Path;
import java.util.List;

interface LauncherStartupInfo {

    String qualifiedClassName();

    default String packageName() {
        int sepIdx = qualifiedClassName().lastIndexOf('.');
        if (sepIdx < 0) {
            return "";
        }
        return qualifiedClassName().substring(sepIdx + 1);
    }

    List<String> javaOptions();

    List<String> defaultParameters();

    List<InstallableFile> classPath();

    record Impl(String qualifiedClassName, List<String> javaOptions,
            List<String> defaultParameters, List<InstallableFile> classPath)
            implements LauncherStartupInfo {

    }

    static class Proxy<T extends LauncherStartupInfo> extends ProxyBase<T>
            implements LauncherStartupInfo {

        Proxy(T target) {
            super(target);
        }

        @Override
        public String qualifiedClassName() {
            return target.qualifiedClassName();
        }

        @Override
        public List<String> javaOptions() {
            return target.javaOptions();
        }

        @Override
        public List<String> defaultParameters() {
            return target.defaultParameters();
        }

        @Override
        public List<InstallableFile> classPath() {
            return target.classPath();
        }
    }

    static class Unsupported implements LauncherStartupInfo {

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
        public List<InstallableFile> classPath() {
            throw new UnsupportedOperationException();
        }
    }
}
