/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.debug.DebugOptions.Dump;
import static org.graalvm.compiler.debug.DebugOptions.MethodFilter;
import static org.graalvm.compiler.debug.DebugOptions.PrintGraph;
import static org.graalvm.compiler.test.SubprocessUtil.getVMCommandLine;
import static org.graalvm.compiler.test.SubprocessUtil.withoutDebuggerArguments;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.DebugOptions.PrintGraphTarget;
import org.graalvm.compiler.test.SubprocessUtil;
import org.graalvm.compiler.test.SubprocessUtil.Subprocess;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests reading options from a file specified by the {@code graal.options.file}.
 */
public class OptionsInFileTest extends GraalCompilerTest {
    @Test
    public void test() throws IOException, InterruptedException {
        String methodFilterValue = "a very unlikely method name";
        String debugFilterValue = "a very unlikely debug scope";
        File optionsFile = File.createTempFile("options", ".properties").getAbsoluteFile();
        try {
            Assert.assertFalse(methodFilterValue.equals(MethodFilter.getDefaultValue()));
            Assert.assertFalse(debugFilterValue.equals(Dump.getDefaultValue()));
            Assert.assertEquals(PrintGraphTarget.File, PrintGraph.getDefaultValue());

            try (PrintStream out = new PrintStream(new FileOutputStream(optionsFile))) {
                out.println(MethodFilter.getName() + "=" + methodFilterValue);
                out.println(Dump.getName() + "=" + debugFilterValue);
                out.println(PrintGraph.getName() + " = Network");
            }

            List<String> vmArgs = withoutDebuggerArguments(getVMCommandLine());
            vmArgs.removeIf(a -> a.startsWith("-Dgraal."));
            vmArgs.add("-Dgraal.options.file=" + optionsFile);
            vmArgs.add("-XX:+JVMCIPrintProperties");
            Subprocess proc = SubprocessUtil.java(vmArgs);
            String[] expected = {
                            "graal.MethodFilter := \"a very unlikely method name\"",
                            "graal.Dump := \"a very unlikely debug scope\"",
                            "graal.PrintGraph := Network"};
            for (String line : proc.output) {
                for (int i = 0; i < expected.length; i++) {
                    if (expected[i] != null && line.contains(expected[i])) {
                        expected[i] = null;
                    }
                }
            }

            for (int i = 0; i < expected.length; i++) {
                if (expected[i] != null) {
                    Assert.fail(String.format("Did not find '%s' in output of command:%n%s", expected[i], proc));
                }
            }
        } finally {
            optionsFile.delete();
        }
    }
}
