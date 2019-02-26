/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test TestJmapCore
 * @summary Test verifies that jhsdb jmap could generate heap dump from core when heap is full
 * @requires vm.hasSA
 * @library /test/lib
 * @run driver/timeout=240 TestJmapCore run heap
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.Platform;
import jdk.test.lib.classloader.GeneratingClassLoader;
import jdk.test.lib.hprof.HprofParser;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Utils;
import jtreg.SkippedException;

import java.io.File;

public class TestJmapCore {
    static final String pidSeparator = ":KILLED_PID";

    public static final String HEAP_OOME = "heap";
    public static final String METASPACE_OOME = "metaspace";


    public static void main(String[] args) throws Throwable {
        if (args.length == 1) {
            // If 1 argument is set prints pid so main process could find corefile
            System.out.println(ProcessHandle.current().pid() + pidSeparator);
            try {
                if (args[0].equals(HEAP_OOME)) {
                    Object[] oa = new Object[Integer.MAX_VALUE / 2];
                    for(int i = 0; i < oa.length; i++) {
                        oa[i] = new Object[Integer.MAX_VALUE / 2];
                    }
                } else {
                    GeneratingClassLoader loader = new GeneratingClassLoader();
                    for (int i = 0; ; i++) {
                        loader.loadClass(loader.getClassName(i));
                    }
                }
                throw new Error("OOME not triggered");
            } catch (OutOfMemoryError err) {
                return;
            }
        }
        test(args[1]);
    }

    // Test tries to run java with ulimit unlimited if it is possible
    static boolean useDefaultUlimit() {
        if (Platform.isWindows()) {
            return true;
        }
        try {
            OutputAnalyzer output = ProcessTools.executeProcess("sh", "-c", "ulimit -c unlimited && ulimit -c");
            return !(output.getExitValue() == 0 && output.getStdout().contains("unlimited"));
        } catch (Throwable t) {
            return true;
        }
    }

    static void test(String type) throws Throwable {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(true, "-XX:+CreateCoredumpOnCrash",
                "-Xmx512m", "-XX:MaxMetaspaceSize=64m", "-XX:+CrashOnOutOfMemoryError",
                TestJmapCore.class.getName(), type);

        boolean useDefaultUlimit = useDefaultUlimit();
        System.out.println("Run test with ulimit: " + (useDefaultUlimit ? "default" : "unlimited"));
        OutputAnalyzer output = useDefaultUlimit
            ? ProcessTools.executeProcess(pb)
            : ProcessTools.executeProcess("sh", "-c", "ulimit -c unlimited && "
                    + ProcessTools.getCommandLine(pb));
        File core;
        String pattern = Platform.isWindows() ? "mdmp" : "core";
        File[] cores = new File(".").listFiles((dir, name) -> name.contains(pattern));
        if (cores.length == 0) {
            // /cores/core.$pid might be generated on macosx by default
            String pid = output.firstMatch("^(\\d+)" + pidSeparator, 1);
            core = new File("cores/core." + pid);
            if (!core.exists()) {
                throw new SkippedException("Has not been able to find coredump");
            }
        } else {
            Asserts.assertTrue(cores.length == 1,
                    "There are unexpected files containing core "
                    + ": " + String.join(",", new File(".").list()) + ".");
            core = cores[0];
        }
        System.out.println("Found corefile: " + core.getAbsolutePath());

        File dumpFile = new File("heap.hprof");
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jhsdb");
        launcher.addToolArg("jmap");
        launcher.addToolArg("--binaryheap");
        launcher.addToolArg("--dumpfile=" + dumpFile);
        launcher.addToolArg("--exe");
        launcher.addToolArg(JDKToolFinder.getTestJDKTool("java"));
        launcher.addToolArg("--core");
        launcher.addToolArg(core.getPath());

        ProcessBuilder jhsdpb = new ProcessBuilder();
        jhsdpb.command(launcher.getCommand());
        Process jhsdb = jhsdpb.start();
        OutputAnalyzer out = new OutputAnalyzer(jhsdb);

        jhsdb.waitFor();

        System.out.println(out.getStdout());
        System.err.println(out.getStderr());

        if (dumpFile.exists() && dumpFile.isFile()) {
            HprofParser.parse(dumpFile);
        } else {
          throw new RuntimeException(
            "Could not find dump file " + dumpFile.getAbsolutePath());
        }

        System.out.println("PASSED");
    }
}
