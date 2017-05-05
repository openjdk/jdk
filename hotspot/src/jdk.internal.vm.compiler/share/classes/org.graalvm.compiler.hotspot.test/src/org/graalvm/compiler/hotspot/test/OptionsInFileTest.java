/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.test;

import static org.graalvm.compiler.debug.GraalDebugConfig.Options.Dump;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.MethodFilter;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.PrintGraph;
import static org.graalvm.compiler.test.SubprocessUtil.formatExecutedCommand;
import static org.graalvm.compiler.test.SubprocessUtil.getVMCommandLine;
import static org.graalvm.compiler.test.SubprocessUtil.withoutDebuggerArguments;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests reading options from a file specified by the {@code graal.options.file}.
 */
public class OptionsInFileTest extends GraalCompilerTest {
    @Test
    public void test() throws IOException {
        List<String> args = withoutDebuggerArguments(getVMCommandLine());

        String methodFilterValue = "a very unlikely method name";
        String debugFilterValue = "a very unlikely debug scope";
        File optionsFile = File.createTempFile("options", ".properties").getAbsoluteFile();
        try {
            Assert.assertFalse(methodFilterValue.equals(MethodFilter.getDefaultValue()));
            Assert.assertFalse(debugFilterValue.equals(PrintGraph.getDefaultValue()));
            Assert.assertTrue(PrintGraph.getDefaultValue());

            try (PrintStream out = new PrintStream(new FileOutputStream(optionsFile))) {
                out.println(MethodFilter.getName() + "=" + methodFilterValue);
                out.println(Dump.getName() + "=" + debugFilterValue);
                out.println(PrintGraph.getName() + " = false");
            }

            args.add("-Dgraal.options.file=" + optionsFile);
            args.add("-XX:+JVMCIPrintProperties");

            ProcessBuilder processBuilder = new ProcessBuilder(args);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String[] expected = {
                            "graal.MethodFilter := \"a very unlikely method name\"",
                            "graal.Dump := \"a very unlikely debug scope\"",
                            "graal.PrintGraph := false"};

            List<String> outputLines = new ArrayList<>();

            String line;
            while ((line = stdout.readLine()) != null) {
                outputLines.add(line);
                for (int i = 0; i < expected.length; i++) {
                    if (expected[i] != null && line.contains(expected[i])) {
                        expected[i] = null;
                    }
                }
            }
            String dashes = "-------------------------------------------------------";
            for (int i = 0; i < expected.length; i++) {
                if (expected[i] != null) {
                    Assert.fail(String.format("Did not find '%s' in output of command:%n%s", expected[i], formatExecutedCommand(args, outputLines, dashes, dashes)));
                }
            }
        } finally {
            optionsFile.delete();
        }
    }
}
