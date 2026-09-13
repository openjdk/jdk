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

/*
 * @test id=AbortOnException_Other
 * @summary Test process revival for serviceability: jcmd on a core file (AbortVMOnException).
 * @requires os.family == "linux" | os.family == "windows"
 * @library /test/lib
 *
 * @run main/othervm JCmdRevival abortvmonexception help VM.version_UNEXPECTED_ARG VM.unknowncommand VM.flags_UNKNOWN_ARG
 *
 * @comment Test all jcmds using the AbortOnException crash method, split across several test definitions
 * to avoid overflowing the log.
 */

/*
 * @test id=AbortOnException_Compiler_1
 * @summary Test process revival for serviceability: jcmd on a core file (AbortVMOnException).
 * @requires os.family == "linux" | os.family == "windows"
 * @library /test/lib
 *
 * @run main/othervm JCmdRevival abortvmonexception Compiler.CodeHeap_Analytics
 *
 * @comment Compiler.CodeHeap_Analytics alone is likely to overrun the log.
 */

/*
 * @test id=AbortOnException_Compiler_2
 * @summary Test process revival for serviceability: jcmd on a core file (AbortVMOnException).
 * @requires os.family == "linux" | os.family == "windows"
 * @library /test/lib
 *
 * @run main/othervm JCmdRevival abortvmonexception Compiler.codecache Compiler.codelist Compiler.memory
 */

/*
 * @test id=AbortOnException_GC
 * @summary Test process revival for serviceability: jcmd on a core file (AbortVMOnException).
 * @requires os.family == "linux" | os.family == "windows"
 * @library /test/lib
 *
 * @run main/othervm JCmdRevival abortvmonexception GC.class_histogram GC.heap_dump
 */

/*
 * @test id=AbortOnException_Thread
 * @summary Test process revival for serviceability: jcmd on a core file (AbortVMOnException).
 * @requires os.family == "linux" | os.family == "windows"
 * @library /test/lib
 *
 * @run main/othervm JCmdRevival abortvmonexception Thread.print
 */

/*
 * @test id=AbortOnException_VM_1
 * @summary Test process revival for serviceability: jcmd on a core file (AbortVMOnException).
 * @requires os.family == "linux" | os.family == "windows"
 * @library /test/lib
 *
 * @run main/othervm JCmdRevival abortvmonexception VM.version VM.class_hierarchy VM.classloader_stats
 */

/*
 * @test id=AbortOnException_VM_2
 * @summary Test process revival for serviceability: jcmd on a core file (AbortVMOnException).
 * @requires os.family == "linux" | os.family == "windows"
 * @library /test/lib
 *
 * @run main/othervm JCmdRevival abortvmonexception VM.classloaders VM.command_line VM.events
 */

/*
 * @test id=AbortOnException_VM_3
 * @summary Test process revival for serviceability: jcmd on a core file (AbortVMOnException).
 * @requires os.family == "linux" | os.family == "windows"
 * @library /test/lib
 *
 * @run main/othervm JCmdRevival abortvmonexception VM.events VM.flags VM.metaspace VM.stringtable VM.symboltable VM.systemdictionary
 */

/*
 * @test id=AbortOnException_VM_4
 * @summary Test process revival for serviceability: jcmd on a core file (AbortVMOnException).
 * @requires os.family == "linux" | os.family == "windows"
 * @library /test/lib
 *
 * @run main/othervm JCmdRevival abortvmonexception VM.classes
 */

/*
 * @test id=OOM
 * @summary Test process revival for serviceability: jcmd on a core file (OOM crash).
 * @requires os.family == "linux" | os.family == "windows"
 * @library /test/lib
 *
 * @run main/othervm JCmdRevival oom VM.version Thread.print GC.heap_dump GC.heap_info VM.command_line VM.events VM.flags VM.metaspace
 *
 * @comment Test a subset with the OOM crash method.
 */

/*
 * @test id=CICrash
 * @summary Test process revival for serviceability: jcmd on a core file (CI crash, debug VM).
 * @requires os.family == "linux" | os.family == "windows"
 * @library /test/lib
 * @requires vm.debug == true
 *
 * @run main/othervm JCmdRevival cicrash VM.version help Thread.print GC.heap_dump Compiler.CodeHeap_Analytics
 *
 * @comment Test a subset with the CICrash crash method.
 * @comment Compiler.CodeHeap_Analytics alone is likely to overrun the log.
 */

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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

public class JCmdRevival {

    private static String MAJOR = System.getProperty("java.vm.version").substring(0, 2);

    public static void main(String[] args) throws Throwable {
        if (args.length > 1) {
            // We are the initial test invocation.
            // Will re-invoke ourself to cause crash and core, wait for that process,
            // then run "jcmd command" on the core.
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
        System.out.println("JCmdRevival in main for test type '" + args[0] + "'");

        if (args[0].equals("abortvmonexception")) {
            try { Thread.sleep(3000); } catch (InterruptedException e) { } // Early MiniDump writing risks deadlock on Windows
            throw new NullPointerException("testing NPE");
        } else if (args[0].equals("oom")) {
            Object[] oa = new Object[Integer.MAX_VALUE / 2];
            for(int i = 0; i < oa.length; i++) {
                oa[i] = new Object[Integer.MAX_VALUE / 2];
            }
        } else if (args[0].equals("oom2")) {
            List<Object> list = new LinkedList<>();
            while (true) {
                list.add("string");
                list.add(123);
            }
        } else {
            throw new RuntimeException("Unknown argument for test: " + args[0]);
        }
        System.out.println("JCmdRevival: should not reach here.");
        System.exit(1); // Should not reach here.
    }

    public static ProcessBuilder getProcessBuilder(String type) {

        switch (type) {
            case ("cicrash"): {
                return ProcessTools.createTestJavaProcessBuilder("-XX:+CreateCoredumpOnCrash",
                       "-Xmx128m", "-XX:CICrashAt=1", "-XX:CompileThreshold=1",
                       JCmdRevival.class.getName(), type);
            }
            case ("oom"): {
                return ProcessTools.createTestJavaProcessBuilder("-XX:+CreateCoredumpOnCrash",
                       "-Xmx128m", "-XX:MaxMetaspaceSize=64m", "-XX:+CrashOnOutOfMemoryError",
                       JCmdRevival.class.getName(), type);
            }
            case ("oom2"): {
                return ProcessTools.createTestJavaProcessBuilder("-XX:+CreateCoredumpOnCrash",
                       "-Xmx1g", "-XX:+CrashOnOutOfMemoryError",
                       JCmdRevival.class.getName(), type);
            }
            case ("abortvmonexception"): {
                return ProcessTools.createTestJavaProcessBuilder("-XX:+CreateCoredumpOnCrash",
                       "-XX:+UnlockDiagnosticVMOptions", "-XX:AbortVMOnException=java.lang.NullPointerException",
                       "-Xmx128m", "-XX:MaxMetaspaceSize=64m",
                       JCmdRevival.class.getName(), type);
            }
            default: {
                throw new RuntimeException("unknown test type");
            }
        }
    }

    /**
      * Test a type of crashing test process, and a list of jcmd commands.
      * e.g. oom VM.version Thread.print
      */
    static void test(String [] args) throws Throwable {
        String type = args[0];
        ProcessBuilder pb = getProcessBuilder(type);

        // For a core dump, apply "ulimit -c unlimited" if we can.
        pb = CoreUtils.addCoreUlimitCommand(pb);
        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        System.out.println(output);
        // Find core, filename found is reported:
        String coreFileName = CoreUtils.getCoreFileLocation(output.getStdout(), output.pid());

        // Run jcmd(s) on core: collect failures.
        List<Object[]> failures = new ArrayList<>();
        int tested = 0;
        for (int i = 1; i < args.length; i++) {
            try {
                tested++;
                testJCmd(coreFileName, type, args[i]);
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

    static void testJCmd(String coreFileName, String type, String command) throws Throwable {
        System.out.println("TEST: core: " + coreFileName + " Test type: " + type + " Command: " + command);

        String coreBaseName = coreFileName;
        int index = coreFileName.lastIndexOf(File.separator);
        if (index >= 0) {
            coreBaseName= coreFileName.substring(index + 1);
        }
        String heapDumpName = null;

        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jcmd");
        // Run jcmd with -J-Dprop=value to pass any System property we care about: the log level setting.
        String logLevel = System.getProperty("jdk.attach.core.log");
        if (logLevel != null) {
            launcher.addVMArg("-Djdk.attach.core.log=" + logLevel);
        }
        launcher.addVMArgs(Utils.getTestJavaOpts()); // People do not generally run jcmd itself with other options.
        launcher.addToolArg(coreFileName);

        // This method just takes a command name to test.  For some commands add a further argument:
        if (command.equals("GC.heap_dump")) {
            launcher.addToolArg(command);
            heapDumpName = "heapdump_" + coreBaseName + ".hprof";
            launcher.addToolArg(heapDumpName);
        } else if (command.equals("VM.version_UNEXPECTED_ARG")) {
            // Test additional args when none are expected:
            launcher.addToolArg("VM.version");
            launcher.addToolArg("badarg1");
        } else if (command.equals("VM.flags_UNKNOWN_ARG")) {
            // Test additional unknown args:
            launcher.addToolArg("VM.flags");
            launcher.addToolArg("badarg1");
            launcher.addToolArg("badarg2");
        } else {
            // All other jcmds just need the command name:
            launcher.addToolArg(command);
        }

        ProcessBuilder jcmdpb = new ProcessBuilder();
        System.out.println("Will run command:" + Arrays.toString(launcher.getCommand()));
        jcmdpb.command(launcher.getCommand());
        // Process revival is currently assisted by some pre-work in a separate Java tool:
        Map<String, String> env = jcmdpb.environment();

        Process jcmd = jcmdpb.start();
        long t1 = System.currentTimeMillis();
        long jcmdPid = jcmd.pid();
        System.out.println("jcmd running, pid " + jcmdPid);
        OutputAnalyzer out = new OutputAnalyzer(jcmd);
        int e = jcmd.waitFor();
        long t2 = System.currentTimeMillis();
        System.out.println("jcmd completed, return code " + e + " duration: " + (t2 - t1) + " ms");
        System.out.println("STDOUT>>");
        System.out.println(out.getStdout());
        System.out.println("<<STDOUT");
        System.out.println("STDERR>>");
        System.out.println(out.getStderr());
        System.out.println("<<STDERR");
        int expectedExit = 0;

        // Verify specific jcmd output:
        try {
            switch (command) {
                case "Compiler.CodeHeap_Analytics": {
                    // out.shouldContain("buffer blob         flush_icache_stub");
                    out.shouldContain("__ CodeHeapStateAnalytics total duration ");
                    break;
                }
                case "Compiler.codecache": {
                    out.shouldContain("CodeHeap 'non-profiled nmethods': size=");
                    break;
                }
                case "Compiler.codelist": {
                    out.shouldContain("jdk.internal.misc.Unsafe.getReferenceVolatile(Ljava/lang/Object;J)Ljava/lang/Object; [0x");
                    break;
                }
                case "Compiler.memory": {
                    // Compiler memory stats not usually enabled.
                    if (Platform.isDebugBuild()) {
                        out.shouldContain("ctyp  total     ra        node      comp      idealloop type      states    reglive"); // Not matching the whole header line.
                    }
                    break;
                }
                case "GC.class_histogram": {
                    out.shouldContain("num     #instances         #bytes  class name (module)");
                    out.shouldContain("java.lang.String (java.base@" + MAJOR);
                    break;
                }
                case "GC.heap_dump": {
                    File dumpFile = new File(heapDumpName);
                    if (dumpFile.exists() && dumpFile.isFile()) {
                        System.out.println("Reading dump file '" + heapDumpName + "' size " + dumpFile.length());
                        try {
                            HprofParser.parse(dumpFile);
                        } catch (java.lang.OutOfMemoryError oom) {
                            // We have read as much as testlib parser can handle.
                            System.out.println("HprofParser in testlib hits OOM (not considered a failure): " + oom);
                        }
                        break;
                    } else {
                        throw new RuntimeException("Could not find dump file '" + heapDumpName + "'");
                    }
                }
                case "GC.heap_info": {
                    out.shouldMatch("total reserved ");
                    break;
                }
                case "Thread.print": {
                    out.shouldContain("Full thread dump");
                    out.shouldContain("_java_thread_list=0x");
                    // OOM crash will contain the stack of the test app, but not likely for cicrash.
                    if (!(type.equals("cicrash"))) {
                        out.shouldContain("at JCmdRevival.main(JCmdRevival.java:");
                    }
                    out.shouldContain("VM Thread");
                    out.shouldContain("JNI global refs:");
                    break;
                }
                case "VM.class_hierarchy": {
                    out.shouldContain("java.lang.Object/null");
                    out.shouldContain("|--java.lang.String/null");
                    break;
                }
                case "VM.classes": {
                    out.shouldContain("fully_initialized     WS       java.lang.String");
                    break;
                }
                case "VM.classloader_stats": {
                    out.shouldContain("<boot class loader>");
                    break;
                }
                case "VM.classloaders": {
                    out.shouldContain("jdk.internal.loader.ClassLoaders$AppClassLoader");
                    break;
                }
                case "VM.command_line": {
                    out.shouldContain("Launcher Type: SUN_STANDARD");
                    break;
                }
                case "VM.events": {
                    out.shouldContain("Events");
                    break;
                }
                case "VM.flags": {
                    out.shouldContain("-XX:+CreateCoredumpOnCrash");
                    out.shouldContain("-XX:ReservedCodeCacheSize=");
                    // Would be good to find a flag with a value we can verify in this test.
                    // ConcGCThreads can be set, but is changed on some test machines, e.g. to 2.
                    // out.shouldContain("-XX:ConcGCThreads=3"); // Recognise value set in test header
                    break;
                }
                case "VM.metaspace": {
                    out.shouldContain("Narrow klass pointer bits ");
                    break;
                }
                case "VM.stringtable": {
                    out.shouldContain("Maximum bucket size     : ");
                    break;
                }
                case "VM.symboltable": {
                    out.shouldContain("Maximum bucket size     : ");
                    break;
                }
                case "VM.systemdictionary": {
                    // out.shouldContain("System Dictionary for 'bootstrap' class loader statistics:");
                    out.shouldContain("Number of buckets       :");
                    break;
                }
                case "VM.unknowncommand": {
                    out.stdoutShouldContain("Unknown diagnostic command");
                    expectedExit = 1;
                    break;
                }
                case "VM.version": {
                    // e.g.
                    // Java HotSpot(TM) 64-Bit Server VM version 27-...
                    // JDK 27.0.0
                    out.shouldContain("VM version " + MAJOR + "-");
                    out.shouldContain("JDK " + MAJOR + ".");
                    break;
                }
                case "VM.version_UNEXPECTED_ARG": {
                    out.stdoutShouldContain("The argument list of this diagnostic command should be empty.");
                    expectedExit = 1;
                    break;
                }
                case "VM.flags_UNKNOWN_ARG": {
                    out.stdoutShouldContain("Unknown argument 'badarg1' in diagnostic command.");
                    expectedExit = 1;
                    break;
                }
                case "help": {
                    out.shouldContain("The following commands are available:");
                    out.shouldContain("VM.version");
                    out.shouldContain("help");
                    out.shouldNotContain("GC.run");
                    out.shouldNotContain("VM.set_flag");
                    break;
                }
                default: {
                    throw new RuntimeException("Unknown command being tested: '" + command + "'");
                }
            }
        } finally {
            // Show an unusual jcmd exit code in finally, in case a check above throws (and skips exit value assert below).
            if (e != expectedExit) {
                System.err.println("Test '" + command + "' exits with: " + e);
            }
        }
        Asserts.assertEquals(expectedExit, e, "Unexpected jcmd return code");
    }
}
