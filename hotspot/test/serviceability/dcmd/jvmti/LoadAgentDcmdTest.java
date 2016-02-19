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
import java.io.*;
import java.nio.file.*;
import jdk.test.lib.*;
import jdk.test.lib.dcmd.*;
import org.testng.annotations.Test;

/*
 * Test to attach JVMTI java agent.
 *
 * @test
 * @bug 8147388
 * @library /testlibrary
 * @modules java.base/sun.misc
 *          java.compiler
 *          java.instrument
 *          java.management
 *          jdk.jvmstat/sun.jvmstat.monitor
 * @build ClassFileInstaller jdk.test.lib.* SimpleJvmtiAgent
 * @run main ClassFileInstaller SimpleJvmtiAgent
 * @run testng LoadAgentDcmdTest
 */
public class LoadAgentDcmdTest {

    public String getLibInstrumentPath() throws FileNotFoundException {
        String jdkPath = System.getProperty("test.jdk");

        if (jdkPath == null) {
            throw new RuntimeException(
                      "System property 'test.jdk' not set. " +
                      "This property is normally set by jtreg. " +
                      "When running test separately, set this property using " +
                      "'-Dtest.jdk=/path/to/jdk'.");
        }

        Path libpath;
        if (Platform.isWindows()) {
            libpath = Paths.get(jdkPath, "bin", "instrument.dll");
        } else {
            libpath = Paths.get(jdkPath, "lib", Platform.getOsArch(), "libinstrument.so");
        }

        if (!libpath.toFile().exists()) {
            throw new FileNotFoundException(
                      "Could not find " + libpath.toAbsolutePath());
        }

        return libpath.toAbsolutePath().toString();
    }

    public void run(CommandExecutor executor)  {
        try{
            PrintWriter pw = new PrintWriter("MANIFEST.MF");
            pw.println("Agent-Class: SimpleJvmtiAgent");
            pw.close();

            ProcessBuilder pb = new ProcessBuilder();
            pb.command(new String[] { JDKToolFinder.getJDKTool("jar"),
                                      "cmf",
                                      "MANIFEST.MF",
                                      "agent.jar",
                                      "SimpleJvmtiAgent.class"});
            pb.start().waitFor();

            String libpath = getLibInstrumentPath();

            // Test 1: No argument
            OutputAnalyzer output = executor.execute("JVMTI.agent_load " +
                                                     libpath + " agent.jar");
            output.stderrShouldBeEmpty();

            // Test 2: With argument
            output = executor.execute("JVMTI.agent_load " +
                                      libpath + " \"agent.jar=foo=bar\"");
            output.stderrShouldBeEmpty();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void jmx() throws Throwable {
        run(new JMXExecutor());
    }

    @Test
    public void cli() throws Throwable {
        run(new PidJcmdExecutor());
    }
}
