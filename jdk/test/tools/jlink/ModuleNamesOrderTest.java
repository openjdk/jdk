/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Properties;
import java.util.spi.ToolProvider;

import tests.Helper;
import tests.JImageGenerator;

/*
 * @test
 * @bug 8168925
 * @summary MODULES property should be topologically ordered and space-separated list
 * @library ../lib
 * @build tests.*
 * @run main ModuleNamesOrderTest
 */
public class ModuleNamesOrderTest {
    static final ToolProvider JLINK_TOOL = ToolProvider.findFirst("jlink")
        .orElseThrow(() ->
            new RuntimeException("jlink tool not found")
        );

    public static void main(String[] args) throws Exception {
        Helper helper = Helper.newHelper();
        if (helper == null) {
            System.err.println("Test not run");
            return;
        }

        Path outputDir = helper.createNewImageDir("image8168925");
        JImageGenerator.getJLinkTask()
                .modulePath(helper.defaultModulePath())
                .output(outputDir)
                .addMods("jdk.scripting.nashorn")
                .call().assertSuccess();

        File release = new File(outputDir.toString(), "release");
        if (!release.exists()) {
            throw new AssertionError("release not generated");
        }

        Properties props = new Properties();
        try (FileReader reader = new FileReader(release)) {
            props.load(reader);
        }

        String modules = props.getProperty("MODULES");
        if (!modules.startsWith("\"java.base ")) {
            throw new AssertionError("MODULES should start with 'java.base'");
        }

        if (!modules.endsWith(" jdk.scripting.nashorn\"")) {
            throw new AssertionError("MODULES end with 'jdk.scripting.nashorn'");
        }

        checkDependency(modules, "java.logging", "java.base");
        checkDependency(modules, "jdk.dynalink", "java.logging");
        checkDependency(modules, "java.scripting", "java.base");
        checkDependency(modules, "jdk.scripting.nashorn", "java.logging");
        checkDependency(modules, "jdk.scripting.nashorn", "jdk.dynalink");
        checkDependency(modules, "jdk.scripting.nashorn", "java.scripting");
    }

    private static void checkDependency(String modules, String fromMod, String toMod) {
        int fromModIdx = modules.indexOf(fromMod);
        if (fromModIdx == -1) {
            throw new AssertionError(fromMod + " is missing in MODULES");
        }
        int toModIdx = modules.indexOf(toMod);
        if (toModIdx == -1) {
            throw new AssertionError(toMod + " is missing in MODULES");
        }

        if (toModIdx > fromModIdx) {
            throw new AssertionError("in MODULES, " + fromMod + " should appear after " + toMod);
        }
    }
}
