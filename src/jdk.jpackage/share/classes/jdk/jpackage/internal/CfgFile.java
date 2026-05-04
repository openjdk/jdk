/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.LauncherJarStartupInfo;
import jdk.jpackage.internal.model.LauncherModularStartupInfo;
import jdk.jpackage.internal.model.LauncherStartupInfo;


/**
 * App launcher's config file.
 */
final class CfgFile {
    CfgFile(Application app, Launcher launcher) {
        startupInfo = launcher.startupInfo().orElseThrow();
        outputFileName = launcher.executableName() + ".cfg";
        version = Objects.requireNonNull(app.version());
    }

    void create(ApplicationLayout appLayout) throws IOException {
        Objects.requireNonNull(appLayout);

        Objects.requireNonNull(startupInfo.qualifiedClassName());

        List<Map.Entry<String, Object>> content = new ArrayList<>();

        final var refs = new Referencies(appLayout);

        content.add(Map.entry("[Application]", SECTION_TAG));

        if (startupInfo instanceof LauncherModularStartupInfo modularStartupInfo) {
            content.add(Map.entry("app.mainmodule", Objects.requireNonNull(modularStartupInfo.moduleName())
                    + "/" + startupInfo.qualifiedClassName()));
        } else if (startupInfo instanceof LauncherJarStartupInfo jarStartupInfo) {
            Path mainJarPath = refs.appDirectory().resolve(jarStartupInfo.jarPath());

            if (jarStartupInfo.isJarWithMainClass()) {
                content.add(Map.entry("app.mainjar", mainJarPath));
            } else {
                content.add(Map.entry("app.classpath", mainJarPath));
                content.add(Map.entry("app.mainclass", startupInfo.qualifiedClassName()));
            }
        } else {
            throw new UnsupportedOperationException();
        }

        for (var value : startupInfo.classPath()) {
            content.add(Map.entry("app.classpath",
                    refs.appDirectory().resolve(value).toString()));
        }

        content.add(Map.entry("[JavaOptions]", SECTION_TAG));

        // always let app know it's version
        content.add(Map.entry(
                "java-options", "-Djpackage.app-version=" + version));

        // add user supplied java options if there are any
        for (var value : startupInfo.javaOptions()) {
            content.add(Map.entry("java-options", value));
        }

        // add module path if there is one
        if (Files.isDirectory(appLayout.appModsDirectory())) {
            content.add(Map.entry("java-options", "--module-path"));
            content.add(Map.entry("java-options", refs.appModsDirectory()));
        }

        var arguments = startupInfo.defaultParameters();
        if (!arguments.isEmpty()) {
            content.add(Map.entry("[ArgOptions]", SECTION_TAG));
            for (var value : arguments) {
                content.add(Map.entry("arguments", value));
            }
        }

        Path cfgFile = appLayout.appDirectory().resolve(outputFileName);
        Files.createDirectories(cfgFile.getParent());

        boolean[] addLineBreakAtSection = new boolean[1];
        Stream<String> lines = content.stream().map(entry -> {
            if (entry.getValue() == SECTION_TAG) {
                if (!addLineBreakAtSection[0]) {
                    addLineBreakAtSection[0] = true;
                    return entry.getKey();
                }
                return "\n" + entry.getKey();
            }
            return entry.getKey() + "=" + entry.getValue();
        });
        Files.write(cfgFile, (Iterable<String>) lines::iterator);
    }

    private record Referencies(Path appModsDirectory, Path appDirectory) {

        Referencies {
            if (!appModsDirectory.startsWith(appDirectory)) {
                throw new IllegalArgumentException();
            }
        }

        Referencies(ApplicationLayout appLayout) {
            this(BASEDIR.resolve(appLayout.appModsDirectory().getFileName()), BASEDIR);
        }

        private static final Path BASEDIR = Path.of("$APPDIR");
    }

    private final LauncherStartupInfo startupInfo;
    private final String version;
    private final String outputFileName;

    private static final Object SECTION_TAG = new Object();
}
