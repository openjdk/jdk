/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.util.OperatingSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Runs custom script from resource directory.
 */
class ScriptRunner {
    ScriptRunner() {
        environment = new ProcessBuilder().environment();
    }

    ScriptRunner setResourceCategoryId(String v) {
        resourceCategoryId = v;
        return this;
    }

    ScriptRunner setDirectory(Path v) {
        directory = v;
        return this;
    }

    ScriptRunner setScriptNameSuffix(String v) {
        scriptNameSuffix = v;
        return this;
    }

    ScriptRunner addEnvironment(Map<String, String> v) {
        environment.putAll(v);
        return this;
    }

    ScriptRunner setEnvironmentVariable(String envVarName, String envVarValue) {
        Objects.requireNonNull(envVarName);
        if (envVarValue == null) {
            environment.remove(envVarName);
        } else {
            environment.put(envVarName, envVarValue);
        }
        return this;
    }

    public void run(BuildEnv env, String name) throws IOException {
        String scriptName = String.format("%s-%s%s", name,
                scriptNameSuffix, scriptSuffix());
        Path scriptPath = env.configDir().resolve(scriptName);
        env.createResource(null)
                .setCategory(I18N.getString(resourceCategoryId))
                .saveToFile(scriptPath);
        if (!Files.exists(scriptPath)) {
            return;
        }

        ProcessBuilder pb = new ProcessBuilder(shell(),
                scriptPath.toAbsolutePath().toString());
        Map<String, String> workEnvironment = pb.environment();
        workEnvironment.clear();
        workEnvironment.putAll(environment);

        if (directory != null) {
            pb.directory(directory.toFile());
        }

        Executor.of(pb).executeExpectSuccess();
    }

    private static String shell() {
        if (OperatingSystem.isWindows()) {
            return "cscript";
        }
        return Optional.ofNullable(System.getenv("SHELL")).orElseGet(() -> "sh");
    }

    private static String scriptSuffix() {
        if (OperatingSystem.isWindows()) {
            return ".wsf";
        }
        return ".sh";
    }

    private String scriptNameSuffix;
    private String resourceCategoryId;
    private Path directory;
    private Map<String, String> environment;
}
