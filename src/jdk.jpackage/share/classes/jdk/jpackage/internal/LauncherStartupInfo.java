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
import java.util.Map;
import static jdk.jpackage.internal.StandardBundlerParam.ARGUMENTS;
import static jdk.jpackage.internal.StandardBundlerParam.JAVA_OPTIONS;

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

    List<Path> classPath();

    static LauncherStartupInfo createFromParams(Map<String, ? super Object> params) {
        var inputDir = StandardBundlerParam.SOURCE_DIR.fetchFrom(params);
        var launcherData = StandardBundlerParam.LAUNCHER_DATA.fetchFrom(params);
        var javaOptions = JAVA_OPTIONS.fetchFrom(params);
        var arguments = ARGUMENTS.fetchFrom(params);
        var classpath = launcherData.classPath().stream().map(p -> {
            return inputDir.resolve(p).toAbsolutePath();
        }).toList();

        if (launcherData.isModular()) {
            return new LauncherModularStartupInfo() {
                @Override
                public String moduleName() {
                    return launcherData.moduleName();
                }

                @Override
                public List<Path> modulePath() {
                    return launcherData.modulePath().stream().map(Path::toAbsolutePath).toList();
                }

                @Override
                public String qualifiedClassName() {
                    return launcherData.qualifiedClassName();
                }

                @Override
                public List<String> javaOptions() {
                    return javaOptions;
                }

                @Override
                public List<String> defaultParameters() {
                    return arguments;
                }

                @Override
                public List<Path> classPath() {
                    return classpath;
                }
            };
        } else {
            return new LauncherJarStartupInfo() {
                @Override
                public Path jarPath() {
                    return inputDir.resolve(launcherData.mainJarName());
                }

                @Override
                public String qualifiedClassName() {
                    return launcherData.qualifiedClassName();
                }

                @Override
                public List<String> javaOptions() {
                    return javaOptions;
                }

                @Override
                public List<String> defaultParameters() {
                    return arguments;
                }

                @Override
                public List<Path> classPath() {
                    return classpath;
                }
            };
        }
    }
}
