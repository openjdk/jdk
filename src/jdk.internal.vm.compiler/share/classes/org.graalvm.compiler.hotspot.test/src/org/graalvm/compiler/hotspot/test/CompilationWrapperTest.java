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
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.graalvm.compiler.core.GraalCompilerOptions;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.test.SubprocessUtil;
import org.graalvm.compiler.test.SubprocessUtil.Subprocess;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests support for dumping graphs and other info useful for debugging a compiler crash.
 */
public class CompilationWrapperTest extends GraalCompilerTest {

    /**
     * Tests compilation requested by the VM.
     */
    @Test
    public void testVMCompilation1() throws IOException, InterruptedException {
        testHelper(Collections.emptyList(), Arrays.asList("-XX:+BootstrapJVMCI",
                        "-XX:+UseJVMCICompiler",
                        "-Dgraal.CompilationFailureAction=ExitVM",
                        "-Dgraal.CrashAt=Object.*,String.*",
                        "-version"));
    }

    /**
     * Tests that {@code -Dgraal.ExitVMOnException=true} works as an alias for
     * {@code -Dgraal.CompilationFailureAction=ExitVM}.
     */
    @Test
    public void testVMCompilation2() throws IOException, InterruptedException {
        testHelper(Collections.emptyList(), Arrays.asList("-XX:+BootstrapJVMCI",
                        "-XX:+UseJVMCICompiler",
                        "-Dgraal.ExitVMOnException=true",
                        "-Dgraal.CrashAt=Object.*,String.*",
                        "-version"));
    }

    static class Probe {
        final String substring;
        final int expectedOccurrences;
        int actualOccurrences;
        String lastMatchingLine;

        Probe(String substring, int expectedOccurrences) {
            this.substring = substring;
            this.expectedOccurrences = expectedOccurrences;
        }

        boolean matches(String line) {
            if (line.contains(substring)) {
                actualOccurrences++;
                lastMatchingLine = line;
                return true;
            }
            return false;
        }

        String test() {
            return expectedOccurrences == actualOccurrences ? null : String.format("expected %d, got %d occurrences", expectedOccurrences, actualOccurrences);
        }
    }

    /**
     * Tests {@link GraalCompilerOptions#MaxCompilationProblemsPerAction} in context of a
     * compilation requested by the VM.
     */
    @Test
    public void testVMCompilation3() throws IOException, InterruptedException {
        final int maxProblems = 4;
        Probe[] probes = {
                        new Probe("To capture more information for diagnosing or reporting a compilation", maxProblems),
                        new Probe("Retrying compilation of", maxProblems) {
                            @Override
                            String test() {
                                return actualOccurrences > 0 && actualOccurrences <= maxProblems ? null : String.format("expected occurrences to be in [1 .. %d]", maxProblems);
                            }
                        },
                        new Probe("adjusting CompilationFailureAction from Diagnose to Print", 1),
                        new Probe("adjusting CompilationFailureAction from Print to Silent", 1),
        };
        testHelper(Arrays.asList(probes), Arrays.asList("-XX:+BootstrapJVMCI",
                        "-XX:+UseJVMCICompiler",
                        "-Dgraal.CompilationFailureAction=Diagnose",
                        "-Dgraal.MaxCompilationProblemsPerAction=" + maxProblems,
                        "-Dgraal.CrashAt=Object.*,String.*",
                        "-version"));
    }

    /**
     * Tests compilation requested by Truffle.
     */
    @Test
    public void testTruffleCompilation1() throws IOException, InterruptedException {
        testHelper(Collections.emptyList(),
                        Arrays.asList(
                                        "-Dgraal.CompilationFailureAction=ExitVM",
                                        "-Dgraal.CrashAt=root test1"),
                        "org.graalvm.compiler.truffle.test.SLTruffleGraalTestSuite", "test");
    }

    /**
     * Tests that TruffleCompilationExceptionsAreFatal works as expected.
     */
    @Test
    public void testTruffleCompilation2() throws IOException, InterruptedException {
        Probe[] probes = {
                        new Probe("Exiting VM due to TruffleCompilationExceptionsAreFatal=true", 1),
        };
        testHelper(Arrays.asList(probes),
                        Arrays.asList(
                                        "-Dgraal.CompilationFailureAction=Silent",
                                        "-Dgraal.TruffleCompilationExceptionsAreFatal=true",
                                        "-Dgraal.CrashAt=root test1"),
                        "org.graalvm.compiler.truffle.test.SLTruffleGraalTestSuite", "test");
    }

    private static final boolean VERBOSE = Boolean.getBoolean(CompilationWrapperTest.class.getSimpleName() + ".verbose");

    private static void testHelper(List<Probe> initialProbes, List<String> extraVmArgs, String... mainClassAndArgs) throws IOException, InterruptedException {
        final File dumpPath = new File(CompilationWrapperTest.class.getSimpleName() + "_" + System.currentTimeMillis()).getAbsoluteFile();
        List<String> vmArgs = withoutDebuggerArguments(getVMCommandLine());
        vmArgs.removeIf(a -> a.startsWith("-Dgraal."));
        vmArgs.remove("-esa");
        vmArgs.remove("-ea");
        vmArgs.add("-Dgraal.DumpPath=" + dumpPath);
        // Force output to a file even if there's a running IGV instance available.
        vmArgs.add("-Dgraal.PrintGraphFile=true");
        vmArgs.addAll(extraVmArgs);

        Subprocess proc = SubprocessUtil.java(vmArgs, mainClassAndArgs);
        if (VERBOSE) {
            System.out.println(proc);
        }

        List<Probe> probes = new ArrayList<>(initialProbes);
        Probe diagnosticProbe = null;
        if (!extraVmArgs.contains("-Dgraal.TruffleCompilationExceptionsAreFatal=true")) {
            diagnosticProbe = new Probe("Graal diagnostic output saved in ", 1);
            probes.add(diagnosticProbe);
            probes.add(new Probe("Forced crash after compiling", Integer.MAX_VALUE) {
                @Override
                String test() {
                    return actualOccurrences > 0 ? null : "expected at least 1 occurrence";
                }
            });
        }

        for (String line : proc.output) {
            for (Probe probe : probes) {
                if (probe.matches(line)) {
                    break;
                }
            }
        }
        for (Probe probe : probes) {
            String error = probe.test();
            if (error != null) {
                Assert.fail(String.format("Did not find expected occurences of '%s' in output of command: %s%n%s", probe.substring, error, proc));
            }
        }
        if (diagnosticProbe != null) {
            String line = diagnosticProbe.lastMatchingLine;
            int substringStart = line.indexOf(diagnosticProbe.substring);
            int substringLength = diagnosticProbe.substring.length();
            String diagnosticOutputZip = line.substring(substringStart + substringLength).trim();

            List<String> dumpPathEntries = Arrays.asList(dumpPath.list());

            File zip = new File(diagnosticOutputZip).getAbsoluteFile();
            Assert.assertTrue(zip.toString(), zip.exists());
            Assert.assertTrue(zip + " not in " + dumpPathEntries, dumpPathEntries.contains(zip.getName()));
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
                    Assert.fail(String.format("Expected at least one .bgv file in %s: %s%n%s", diagnosticOutputZip, entries, proc));
                }
                if (cfg == 0) {
                    Assert.fail(String.format("Expected at least one .cfg file in %s: %s", diagnosticOutputZip, entries));
                }
            } finally {
                zip.delete();
                dumpPath.delete();
            }
        }
    }
}
