/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @summary Test attach API on a core file
 * @requires os.family == "linux" | os.family == "windows"
 * @library /test/lib
 * @modules jdk.attach/sun.tools.attach
 *          jdk.jcmd/sun.tools.common
 *
 * @run main/othervm AttachRevival oom attach
 */

import com.sun.tools.attach.*;
import sun.tools.attach.*;
import sun.tools.common.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;


import jdk.test.lib.Asserts;
import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.Platform;
import jdk.test.lib.Utils;
import jdk.test.lib.classloader.GeneratingClassLoader;
import jdk.test.lib.hprof.HprofParser;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.util.CoreUtils;
import jtreg.SkippedException;

public class AttachRevival {

    public static void main(String[] args) throws Throwable {
        if (args.length > 1) {
            // We are the initial test invocation.
            // Will re-invoke ourself to cause crash and core, wait for that process,
            // then attach to the core.
            test(args);
        } else if (args.length == 1) {
            // One argument is a re-invocation to run, with a crash and core.
            testAndCrash(args);
        } else {
            throw new RuntimeException("Missing test arguments");
        }
    }

    public static void testAndCrash(String[] args) {
        // Type of run (abortvmonexception, oom, cicrash) is passed as arg.
        // Do the thing that will cause a crash (if cicrash, then any activity will trigger crash).
        System.out.println("AttachRevival in main for test type '" + args[0] + "'");

        if (args[0].equals("abortvmonexception")) {
            try { Thread.sleep(3000); } catch (InterruptedException e) { } // Early MiniDump writing risks deadlock on Windows
            throw new NullPointerException("testing NPE");
        } else if (args[0].equals("oom")) {
            // Cause OOM:
            Object[] oa = new Object[Integer.MAX_VALUE / 2];
            for(int i = 0; i < oa.length; i++) {
                oa[i] = new Object[Integer.MAX_VALUE / 2];
            }
        } else {
            throw new RuntimeException("Unknown argument for test: " + args[0]);
        }
        System.out.println("AttachRevival: should not reach here.");
        System.exit(1); // Should not reach here.
    }

    public static ProcessBuilder getProcessBuilder(String type) {
        switch (type) {
            case ("cicrash"): {
                return ProcessTools.createTestJavaProcessBuilder("-XX:+CreateCoredumpOnCrash",
                       "-Xmx128m", "-XX:CICrashAt=1", "-XX:CompileThreshold=1",
                       AttachRevival.class.getName(), type);
            }
            case ("oom"): {
                return ProcessTools.createTestJavaProcessBuilder("-XX:+CreateCoredumpOnCrash",
                       "-Xmx128m", "-XX:MaxMetaspaceSize=64m", "-XX:+CrashOnOutOfMemoryError",
                       AttachRevival.class.getName(), type);
            }
            case ("abortvmonexception"): {
                return ProcessTools.createTestJavaProcessBuilder("-XX:+CreateCoredumpOnCrash",
                       "-XX:+UnlockDiagnosticVMOptions", "-XX:AbortVMOnException=java.lang.NullPointerException",
                       "-Xmx128m", "-XX:MaxMetaspaceSize=64m",
                       AttachRevival.class.getName(), type);
            }
            default: {
                throw new RuntimeException("unknown test type");
            }
        }
    }

    static void test(String [] args) throws Throwable {
        String type = args[0];
        ProcessBuilder pb = getProcessBuilder(type);

        // For a core dump, apply "ulimit -c unlimited" if we can.
        pb = CoreUtils.addCoreUlimitCommand(pb);
        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        System.out.println(output);
        // Find core, filename found is reported:
        String coreFileName = CoreUtils.getCoreFileLocation(output.getStdout(), output.pid());

        // Attach to core: collect failures.
        List<Object[]> failures = new ArrayList<>();
        int tested = 0;
        for (int i = 1; i < args.length; i++) {
            try {
                tested++;
                testAttach(coreFileName, type, args[i]);
            } catch (Throwable thr) {
                failures.add(new Object[] { args[i], thr});
            }
        }
        if (!failures.isEmpty()) {
            System.err.println("FAILURE(s): ");
            for (Object [] f: failures) {
                System.err.println(f[0]);
                ((Throwable) f[1]).printStackTrace(System.err);
            }
            throw new RuntimeException("FAILED tests: " + failures.size() + " out of " + tested);
        }
        System.out.println("PASSED");
    }

    static void testAttach(String coreFileName, String type, String command) throws Throwable {
        System.out.println("TEST: core: " + coreFileName + " Test type: " + type + " Command: " + command);

        VirtualMachine vm = VirtualMachine.attach(coreFileName, Map.of());
        System.out.println("vm = '" + vm.toString() + "'");
        String id = vm.id();
        System.out.println("VM id = '" + id + "'");

        try {
            vm.startLocalManagementAgent();
            throw new RuntimeException("startLocalManagementAgent should not succeed");
        } catch (IOException e1) {
            System.out.println("Expected Exception from startLocalManagementAgent:");
            e1.printStackTrace(System.out);
        }

        try {
            vm.loadAgent("noAgent");
            throw new RuntimeException("loadAgent should not succeed");
        } catch (IOException e2) {
            System.out.println("Expected Exception from loadAgent:");
            e2.printStackTrace(System.out);
        }

        try {
            Properties props = vm.getSystemProperties();
            System.out.println(props);
            throw new RuntimeException("getSystemProperties should not succeed");
        } catch (IOException e3) {
            // java.io.IOException: command 'properties' not implemented
            System.out.println("Expected Exception from getSystemProperties:");
            e3.printStackTrace(System.out);
        }

        // For jcmd we would cast to HotSpotVirtualMachine as executeJCmd is an implementation specific method.
        HotSpotVirtualMachine hvm = (HotSpotVirtualMachine) vm;

        // Separate JCmdRevival test covers jcmd in detail.
        // Test IOException is thrown after detaching:
        vm.detach();
        try {
            InputStream is = hvm.executeJCmd("help");
            PrintStreamPrinter.drainUTF8(is, System.out);
            throw new RuntimeException("jcmd should not succeed after detach");
        } catch (IOException e4) {
            System.out.println("Expected Exception from jcmd after detach:");
            e4.printStackTrace(System.out);
        }
    }
}

