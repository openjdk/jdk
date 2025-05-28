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
import java.util.List;
import java.util.function.UnaryOperator;
import jdk.jpackage.internal.model.LauncherJarStartupInfo;
import jdk.jpackage.internal.model.LauncherJarStartupInfoMixin;
import jdk.jpackage.internal.model.LauncherModularStartupInfo;
import jdk.jpackage.internal.model.LauncherModularStartupInfoMixin;
import jdk.jpackage.internal.model.LauncherStartupInfo.Stub;
import jdk.jpackage.internal.model.LauncherStartupInfo;

final class LauncherStartupInfoBuilder {

    LauncherStartupInfo create() {
        return decorator.apply(new Stub(qualifiedClassName, javaOptions,
                defaultParameters, classPath));
    }

    LauncherStartupInfoBuilder launcherData(LauncherData launcherData) {
        if (launcherData.isModular()) {
            decorator = new ModuleStartupInfo(launcherData.moduleName());
        } else {
            decorator = new JarStartupInfo(launcherData.mainJarName(),
                    launcherData.isClassNameFromMainJar());
        }
        classPath = launcherData.classPath();
        qualifiedClassName = launcherData.qualifiedClassName();
        return this;
    }

    LauncherStartupInfoBuilder javaOptions(List<String> v) {
        javaOptions = v;
        return this;
    }

    LauncherStartupInfoBuilder defaultParameters(List<String> v) {
        defaultParameters = v;
        return this;
    }

    private static record ModuleStartupInfo(String moduleName) implements UnaryOperator<LauncherStartupInfo> {

        @Override
        public LauncherStartupInfo apply(LauncherStartupInfo base) {
            return LauncherModularStartupInfo.create(base,
                    new LauncherModularStartupInfoMixin.Stub(moduleName));
        }
    }

    private static record JarStartupInfo(Path jarPath,
            boolean isClassNameFromMainJar) implements
            UnaryOperator<LauncherStartupInfo> {

        @Override
        public LauncherStartupInfo apply(LauncherStartupInfo base) {
            return LauncherJarStartupInfo.create(base,
                    new LauncherJarStartupInfoMixin.Stub(jarPath,
                            isClassNameFromMainJar));
        }
    }

    private String qualifiedClassName;
    private List<String> javaOptions;
    private List<String> defaultParameters;
    private List<Path> classPath;
    private UnaryOperator<LauncherStartupInfo> decorator;
}
