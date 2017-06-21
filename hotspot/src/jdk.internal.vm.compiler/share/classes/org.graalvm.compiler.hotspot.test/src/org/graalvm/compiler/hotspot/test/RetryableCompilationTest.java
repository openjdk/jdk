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

import static org.graalvm.compiler.test.SubprocessUtil.getVMCommandLine;
import static org.graalvm.compiler.test.SubprocessUtil.withoutDebuggerArguments;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.test.SubprocessUtil;
import org.graalvm.compiler.test.SubprocessUtil.Subprocess;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests support for dumping graphs and other info useful for debugging a compiler crash.
 */
public class RetryableCompilationTest extends GraalCompilerTest {

    /**
     * Tests compilation requested by the VM.
     */
    @Test
    public void testVMCompilation() throws IOException, InterruptedException {
        testHelper(Arrays.asList("-XX:+BootstrapJVMCI", "-XX:+UseJVMCICompiler", "-Dgraal.CrashAt=Object.*,String.*", "-version"));
    }

    /**
     * Tests compilation requested by Truffle.
     */
    @Test
    public void testTruffleCompilation() throws IOException, InterruptedException {
        testHelper(Arrays.asList("-Dgraal.CrashAt=root test1"), "org.graalvm.compiler.truffle.test.SLTruffleGraalTestSuite", "test");
    }

    private static void testHelper(List<String> extraVmArgs, String... mainClassAndArgs) throws IOException, InterruptedException {
        List<String> vmArgs = withoutDebuggerArguments(getVMCommandLine());
        vmArgs.removeIf(a -> a.startsWith("-Dgraal."));
        // Force output to a file even if there's a running IGV instance available.
        vmArgs.add("-Dgraal.PrintGraphFile=true");
        vmArgs.addAll(extraVmArgs);

        Subprocess proc = SubprocessUtil.java(vmArgs, mainClassAndArgs);

        String forcedCrashString = "Forced crash after compiling";
        String diagnosticOutputFilePrefix = "Graal diagnostic output saved in ";

        boolean seenForcedCrashString = false;
        String diagnosticOutputZip = null;

        for (String line : proc.output) {
            if (line.contains(forcedCrashString)) {
                seenForcedCrashString = true;
            } else if (diagnosticOutputZip == null) {
                int index = line.indexOf(diagnosticOutputFilePrefix);
                if (index != -1) {
                    diagnosticOutputZip = line.substring(diagnosticOutputFilePrefix.length()).trim();
                }
            }
        }
        if (!seenForcedCrashString) {
            Assert.fail(String.format("Did not find '%s' in output of command:%n%s", forcedCrashString, proc));
        }
        if (diagnosticOutputZip == null) {
            Assert.fail(String.format("Did not find '%s' in output of command:%n%s", diagnosticOutputFilePrefix, proc));
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
