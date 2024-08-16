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
 * @requires cgroup.tools
 * @modules java.base/jdk.internal.platform
 * @library /testlibrary /test/lib
 * @run main/othervm NestedCgroup
 */

import java.io.IOException;
import java.lang.IllegalArgumentException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.internal.platform.Metrics;
import jdk.test.lib.Asserts;
import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jtreg.SkippedException;

public class NestedCgroup {
    private static abstract class Test {
        public static final String CGROUP_OUTER = "jdktest" + ProcessHandle.current().pid();
        public static final String CGROUP_INNER = "inner";

        // A real usage on x86_64 fits in 39 MiB.
        public static final int MEMORY_MAX_OUTER = 500 * 1024 * 1024;
        public static final int MEMORY_MAX_INNER = MEMORY_MAX_OUTER * 2;

        class HookResult {
            int integerLimit;
            OutputAnalyzer output;
        };

        public static String memory_max_filename;
        public static boolean isCgroup2;

        public static void args_add_cgexec(String controller, List<String> args) {
            args.add("cgexec");
            args.add("-g");
            args.add(controller + ":" + CGROUP_OUTER + "/" + CGROUP_INNER);
        }

        public static String jdkTool;

        public static void args_add_self(List<String> args) {
            args.add(jdkTool);
            args.add("-cp");
            args.add(System.getProperty("test.classes"));
        }

        public static void args_add_self_verbose(List<String> args) {
            args_add_self(args);
            args.add("-XshowSettings:system");
            args.add("-Xlog:os+container=trace");
        }

        public Test(String controller) throws Exception {
            OutputAnalyzer output;

            List<String> cgdelete = new ArrayList<>();
            cgdelete.add("cgdelete");
            cgdelete.add("-r");
            cgdelete.add("-g");
            cgdelete.add(controller + ":" + CGROUP_OUTER);
            output = ProcessTools.executeProcess(new ProcessBuilder(cgdelete));
            if (output.contains("cgdelete: libcgroup initialization failed: Cgroup is not mounted")) {
                throw new SkippedException("cgroup/cgroup2 is not mounted: " + output.getStderr());
            }
            output.stdoutShouldBeEmpty();
            if (!output.contains("cgdelete: cannot remove group '" + CGROUP_OUTER + "': No such file or directory")) {
                output.stderrShouldBeEmpty();
            }

            // Alpine Linux 3.20.1 needs cgcreate1 otherwise:
            // cgcreate: can't create cgroup [...]: No such file or directory
            List<String> cgcreate1 = new ArrayList<>();
            cgcreate1.add("cgcreate");
            cgcreate1.add("-g");
            cgcreate1.add(controller + ":" + CGROUP_OUTER);
            output = ProcessTools.executeProcess(new ProcessBuilder(cgcreate1));
            output.stdoutShouldBeEmpty();
            output.stderrShouldBeEmpty();

            List<String> cgcreate2 = new ArrayList<>();
            cgcreate2.add("cgcreate");
            cgcreate2.add("-g");
            cgcreate2.add(controller + ":" + CGROUP_OUTER + "/" + CGROUP_INNER);
            output = ProcessTools.executeProcess(new ProcessBuilder(cgcreate2));
            output.stdoutShouldBeEmpty();
            output.stderrShouldBeEmpty();

            memory_max_filename = isCgroup2 ? "memory.max" : "memory.limit_in_bytes";
            ProcessTools.executeProcess("cgset", "-r", memory_max_filename + "=" + MEMORY_MAX_OUTER, "/" + CGROUP_OUTER);

            HookResult hookResult = hook(controller);
            hookResult.output.shouldMatch("\\[trace\\]\\[os,container\\] Memory Limit is: " + hookResult.integerLimit + "$");

            output = ProcessTools.executeProcess(new ProcessBuilder(cgdelete));
            output.stdoutShouldBeEmpty();
            output.stderrShouldBeEmpty();
        }

        public abstract HookResult hook(String controller) throws Exception;
    }
    private static class TestTwoLimits extends Test {
        public HookResult hook(String controller) throws Exception {
            HookResult hookResult = new HookResult();

            ProcessTools.executeProcess("cgset", "-r", memory_max_filename + "=" + MEMORY_MAX_INNER, "/" + CGROUP_OUTER + "/" + CGROUP_INNER);

            List<String> cgexec = new ArrayList<>();
            args_add_cgexec(controller, cgexec);
            args_add_self_verbose(cgexec);
            cgexec.add("-version");
            hookResult.output = ProcessTools.executeProcess(new ProcessBuilder(cgexec));

            // KFAIL - verify the CgroupSubsystem::initialize_hierarchy() and jdk.internal.platform.CgroupSubsystem.initializeHierarchy() bug
            // TestTwoLimits does not see the lower MEMORY_MAX_OUTER limit.
            hookResult.integerLimit = MEMORY_MAX_INNER;
            return hookResult;
        }
        public TestTwoLimits() throws Exception {
            super("memory");
        }
    }
    private static class TestNoController extends Test {
        public HookResult hook(String controller) throws Exception {
            HookResult hookResult = new HookResult();

            List<String> cgexec = new ArrayList<>();
            args_add_cgexec(controller, cgexec);
            args_add_self(cgexec);
            cgexec.add("NestedCgroup");
            cgexec.add("TestNoController");
            cgexec.add(Test.jdkTool);
            hookResult.output = ProcessTools.executeProcess(new ProcessBuilder(cgexec));

            hookResult.integerLimit = MEMORY_MAX_OUTER;
            return hookResult;
        }
        public TestNoController() throws Exception {
            super("cpu");
        }
        public static void child() throws Exception {
            List<String> self_verbose = new ArrayList<>();
            args_add_self_verbose(self_verbose);
            self_verbose.add("-version");
            ProcessTools.executeProcess(new ProcessBuilder(self_verbose));
        }
    }
    public static void main(String[] args) throws Exception {
        if (!Platform.isRoot()) {
            throw new SkippedException("Missing root permission");
        }

        String provider = Metrics.systemMetrics().getProvider();
        System.err.println("Metrics.systemMetrics().getProvider() = " + provider);
        if ("cgroupv1".equals(provider)) {
          Test.isCgroup2 = false;
        } else if ("cgroupv2".equals(provider)) {
          Test.isCgroup2 = true;
        } else {
          throw new IllegalArgumentException();
        }
        System.err.println("isCgroup2 = " + Test.isCgroup2);

        switch (args.length) {
            case 0:
                Test.jdkTool = JDKToolFinder.getJDKTool("java");
                new TestTwoLimits();
                if (Test.isCgroup2) {
                    new TestNoController();
                }
                return;
            case 2:
                switch (args[0]) {
                    case "TestNoController":
                        Test.jdkTool = args[1];
                        TestNoController.child();
                        return;
                }
        }
        throw new IllegalArgumentException();
    }
}
