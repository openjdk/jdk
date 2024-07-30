/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test NestedCgroup
 * @key cgroups
 * @requires os.family == "linux"
 * @requires vm.flagless
 * @library /testlibrary /test/lib
 * @run main/othervm NestedCgroup
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Platform;
import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.Asserts;
import java.util.List;
import java.util.ArrayList;
import java.nio.file.Files;
import jtreg.SkippedException;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.nio.file.NoSuchFileException;
import java.io.IOException;
import java.lang.IllegalArgumentException;

public class NestedCgroup {
    private static abstract class Test {
        public static final String CGROUP_OUTER = "jdktest" + ProcessHandle.current().pid();
        public static final String CGROUP_INNER = "inner";
        public static final String CONTROLLERS_PATH_OUTER = "memory:" + CGROUP_OUTER;
        public static final String CONTROLLERS_PATH_INNER = CONTROLLERS_PATH_OUTER + "/" + CGROUP_INNER;
        public static final String LINE_DELIM = "-".repeat(80);
        public static final String MOUNTINFO = "/proc/self/mountinfo";

        // A real usage on x86_64 fits in 39 MiB.
        public static final int MEMORY_MAX_OUTER = 500 * 1024 * 1024;
        public static final int MEMORY_MAX_INNER = MEMORY_MAX_OUTER * 2;

        class Limits {
            int integer;
        };

        public static String sysFsCgroup;
        public String memory_max_filename;
        public static boolean isCgroup2;

        public static void lineDelim(String str, String label) {
            System.err.print(LINE_DELIM + " " + label + "\n" + str);
            if (!str.isEmpty() && !str.endsWith("\n")) {
                System.err.println();
            }
        }

        public static OutputAnalyzer pSystem(List<String> args, String failStderr, String failExplanation, String ignoreStderr) throws Exception {
            System.err.println(LINE_DELIM + " command: " + String.join(" ",args));
            ProcessBuilder pb = new ProcessBuilder(args);
            Process process = pb.start();
            OutputAnalyzer output = new OutputAnalyzer(process);
            int exitValue = process.waitFor();
            lineDelim(output.getStdout(), "stdout");
            lineDelim(output.getStderr(), "stderr");
            System.err.println(LINE_DELIM);
            if (!failStderr.isEmpty() && output.getStderr().equals(failStderr + "\n")) {
                throw new SkippedException(failExplanation + ": " + failStderr);
            }
            if (!ignoreStderr.isEmpty() && output.getStderr().equals(ignoreStderr + "\n")) {
                return output;
            }
            Asserts.assertEQ(0, exitValue, "Process returned unexpected exit code: " + exitValue);
            return output;
        }

        public static OutputAnalyzer pSystem(List<String> args) throws Exception {
            return pSystem(args, "", "", "");
        }

        public static void args_add_cgexec(List<String> args) {
            args.add("cgexec");
            args.add("-g");
            args.add(CONTROLLERS_PATH_INNER);
        }

        public static String jdkTool;

        public static void args_add_self(List<String> args) {
            args.add(jdkTool);
            args.add("-cp");
            args.add(System.getProperty("java.class.path"));
        }

        public static void args_add_self_verbose(List<String> args) {
            args_add_self(args);
            args.add("-XshowSettings:system");
            args.add("-Xlog:os+container=trace");
        }

        public Test() throws Exception {
            List<String> cgdelete = new ArrayList<>();
            cgdelete.add("cgdelete");
            cgdelete.add("-r");
            cgdelete.add("-g");
            cgdelete.add(CONTROLLERS_PATH_OUTER);
            try {
                pSystem(cgdelete,
                    "cgdelete: libcgroup initialization failed: Cgroup is not mounted", "cgroup/cgroup2 is not mounted",
                    "cgdelete: cannot remove group '" + CGROUP_OUTER + "': No such file or directory");
            } catch (IOException e) {
                if (e.toString().equals("java.io.IOException: Cannot run program \"cgdelete\": error=2, No such file or directory")) {
                    throw new SkippedException("libcgroup-tools is not installed");
                }
                throw e;
            }

            List<String> cgcreate = new ArrayList<>();
            cgcreate.add("cgcreate");
            cgcreate.add("-g");
            cgcreate.add(CONTROLLERS_PATH_INNER);
            pSystem(cgcreate, "cgcreate: can't create cgroup " + CGROUP_OUTER + "/" + CGROUP_INNER + ": Cgroup, operation not allowed", "Missing root permission", "");

            String mountInfo;
            try {
                mountInfo = Files.readString(Path.of(MOUNTINFO));
            } catch (NoSuchFileException e) {
                throw new SkippedException("Cannot open " + MOUNTINFO);
            }

            Matcher matcher = Pattern.compile("^(?:\\S+\\s+){4}(\\S+)\\s.*\\scgroup(?:(2)(?:\\s+\\S+){2}|\\s+\\S+\\s+(?:\\S*,)?memory(?:,\\S*)?)$", Pattern.MULTILINE).matcher(mountInfo);
            if (!matcher.find()) {
                System.err.println(mountInfo);
                throw new SkippedException("cgroup/cgroup2 filesystem mount point not found");
            }
            sysFsCgroup = matcher.group(1);
            isCgroup2 = matcher.group(2) != null;
            System.err.println("isCgroup2 = " + isCgroup2);

            System.err.println(LINE_DELIM + " " + (isCgroup2 ? "cgroup2" : "cgroup1") + " mount point: " + sysFsCgroup);
            memory_max_filename = isCgroup2 ? "memory.max" : "memory.limit_in_bytes";
            Files.writeString(Path.of(sysFsCgroup + "/" + CGROUP_OUTER + "/" + memory_max_filename), "" + MEMORY_MAX_OUTER);

            // Here starts a copy of ProcessTools.createJavaProcessBuilder.
            List<String> cgexec = new ArrayList<>();
            Limits limits = hook(cgexec);
            OutputAnalyzer output = pSystem(cgexec);
            // C++ CgroupController
            output.shouldMatch("\\[trace\\]\\[os,container\\] Memory Limit is: " + limits.integer + "$");

            pSystem(cgdelete);
        }

        public abstract Limits hook(List<String> cgexec) throws IOException;
    }
    private static class TestTwoLimits extends Test {
        public Limits hook(List<String> cgexec) throws IOException {
            // CgroupV1Subsystem::read_memory_limit_in_bytes considered hierarchical_memory_limit only when inner memory.limit_in_bytes is unlimited.
            Files.writeString(Path.of(sysFsCgroup + "/" + CGROUP_OUTER + "/" + CGROUP_INNER + "/" + memory_max_filename), "" + MEMORY_MAX_INNER);

            args_add_cgexec(cgexec);
            args_add_self_verbose(cgexec);
            cgexec.add("-version");

            // KFAIL - verify the CgroupSubsystem::initialize_hierarchy() and jdk.internal.platform.CgroupSubsystem.initializeHierarchy() bug
            // TestTwoLimits does not see the lower MEMORY_MAX_OUTER limit.
            Limits limits = new Limits();
            limits.integer = MEMORY_MAX_INNER;
            return limits;
        }
        public TestTwoLimits() throws Exception {
        }
    }
    private static class TestNoController extends Test {
        public Limits hook(List<String> cgexec) throws IOException {
            args_add_cgexec(cgexec);
            args_add_self(cgexec);
            cgexec.add("NestedCgroup");
            cgexec.add("TestNoController");
            cgexec.add(Test.jdkTool);
            cgexec.add(sysFsCgroup + "/" + CGROUP_OUTER + "/cgroup.subtree_control");

            Limits limits = new Limits();
            limits.integer = MEMORY_MAX_OUTER;
            return limits;
        }
        public TestNoController() throws Exception {
        }
        public static void child(String arg) throws Exception {
            Files.writeString(Path.of(arg), "-memory");

            List<String> self_verbose = new ArrayList<>();
            args_add_self_verbose(self_verbose);
            self_verbose.add("-version");
            pSystem(self_verbose);
        }
    }
    public static void main(String[] args) throws Exception {
        switch (args.length) {
            case 0:
                Test.jdkTool = JDKToolFinder.getJDKTool("java");
                new TestTwoLimits();
                if (Test.isCgroup2) {
                    new TestNoController();
                }
                return;
            case 3:
                switch (args[0]) {
                    case "TestNoController":
                        Test.jdkTool = args[1];
                        TestNoController.child(args[2]);
                        return;
                }
        }
        throw new IllegalArgumentException();
    }
}
