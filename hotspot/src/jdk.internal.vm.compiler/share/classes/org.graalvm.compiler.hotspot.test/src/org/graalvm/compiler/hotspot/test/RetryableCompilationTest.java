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

import static org.graalvm.compiler.test.SubprocessUtil.formatExecutedCommand;
import static org.graalvm.compiler.test.SubprocessUtil.getVMCommandLine;
import static org.graalvm.compiler.test.SubprocessUtil.withoutDebuggerArguments;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.hotspot.CompilationTask;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests {@link CompilationTask} support for dumping graphs and other info useful for debugging a
 * compiler crash.
 */
public class RetryableCompilationTest extends GraalCompilerTest {
    @Test
    public void test() throws IOException {
        List<String> args = withoutDebuggerArguments(getVMCommandLine());

        args.add("-XX:+BootstrapJVMCI");
        args.add("-XX:+UseJVMCICompiler");
        args.add("-Dgraal.CrashAt=Object.*,String.*");
        args.add("-version");

        ProcessBuilder processBuilder = new ProcessBuilder(args);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));

        String forcedCrashString = "Forced crash after compiling";
        String diagnosticOutputFilePrefix = "Graal diagnostic output saved in ";

        boolean seenForcedCrashString = false;
        String diagnosticOutputZip = null;

        List<String> outputLines = new ArrayList<>();

        String line;
        while ((line = stdout.readLine()) != null) {
            outputLines.add(line);
            if (line.contains(forcedCrashString)) {
                seenForcedCrashString = true;
            } else if (diagnosticOutputZip == null) {
                int index = line.indexOf(diagnosticOutputFilePrefix);
                if (index != -1) {
                    diagnosticOutputZip = line.substring(diagnosticOutputFilePrefix.length()).trim();
                }
            }
        }
        String dashes = "-------------------------------------------------------";
        if (!seenForcedCrashString) {
            Assert.fail(String.format("Did not find '%s' in output of command:%n%s", forcedCrashString, formatExecutedCommand(args, outputLines, dashes, dashes)));
        }
        if (diagnosticOutputZip == null) {
            Assert.fail(String.format("Did not find '%s' in output of command:%n%s", diagnosticOutputFilePrefix, formatExecutedCommand(args, outputLines, dashes, dashes)));
        }

        File zip = new File(diagnosticOutputZip).getAbsoluteFile();
        Assert.assertTrue(zip.toString(), zip.exists());
        try {
            int bgv = 0;
            int cfg = 0;
            ZipFile dd = new ZipFile(diagnosticOutputZip);
            List<String> entries = new ArrayList<>();
            for (Enumeration<? extends ZipEntry> e = dd.entries(); e.hasMoreElements();) {
                ZipEntry ze = e.nextElement();
                String name = ze.getName();
                entries.add(name);
                if (name.endsWith(".bgv")) {
                    bgv++;
                } else if (name.endsWith(".cfg")) {
                    cfg++;
                }
            }
            if (bgv == 0) {
                Assert.fail(String.format("Expected at least one .bgv file in %s: %s", diagnosticOutputZip, entries));
            }
            if (cfg == 0) {
                Assert.fail(String.format("Expected at least one .cfg file in %s: %s", diagnosticOutputZip, entries));
            }
        } finally {
            zip.delete();
        }
    }
}
