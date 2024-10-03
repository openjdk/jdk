/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Arrays;

import java.io.StringWriter;
import java.util.spi.ToolProvider;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.util.JarUtils;

public class JNativeScanTestBase {

    public static final String MODULE_PATH = "mods";

    private static final ToolProvider JNATIVESCAN_TOOL = ToolProvider.findFirst("jnativescan")
            .orElseThrow(() -> new RuntimeException("jnativescan tool not found"));

    public static OutputAnalyzer jnativescan(String... args) {
        return run(JNATIVESCAN_TOOL, args);
    }

    private static OutputAnalyzer run(ToolProvider tp, String[] commands) {
        int rc;
        StringWriter sw = new StringWriter();
        StringWriter esw = new StringWriter();

        try (PrintWriter pw = new PrintWriter(sw);
             PrintWriter epw = new PrintWriter(esw)) {
            System.out.println("Running " + tp.name() + ", Command: " + Arrays.toString(commands));
            rc = tp.run(pw, epw, commands);
        }
        OutputAnalyzer output = new OutputAnalyzer(sw.toString(), esw.toString(), rc);
        output.outputTo(System.out);
        output.errorTo(System.err);
        return output;
    }

    public static Path makeModularJar(String moduleName) throws IOException {
        Path jarPath = Path.of(MODULE_PATH, moduleName + ".jar");
        Path moduleRoot = moduleRoot(moduleName);
        JarUtils.createJarFile(jarPath, moduleRoot);
        return jarPath;
    }

    public static Path moduleRoot(String name) {
        return Path.of(System.getProperty("test.module.path")).resolve(name);
    }

    public static OutputAnalyzer assertSuccess(OutputAnalyzer output) {
        if (output.getExitValue() != 0) {
            throw new IllegalStateException("tool run failed");
        }
        return output;
    }

    public static OutputAnalyzer assertFailure(OutputAnalyzer output) {
        if (output.getExitValue() == 0) {
            throw new IllegalStateException("tool run succeeded");
        }
        return output;
    }
}
