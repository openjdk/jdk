/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, NTT DATA
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

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.SA.SATestUtils;
import jdk.test.lib.Utils;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.process.OutputAnalyzer;

import jtreg.SkippedException;

/**
 * @test
 * @bug 8370176
 * @requires vm.hasSA
 * @requires os.family == "linux"
 * @requires (os.arch == "amd64" | os.arch == "aarch64" | os.arch == "riscv64")
 * @library /test/lib
 * @run main/othervm --enable-native-access=ALL-UNNAMED TestJhsdbJstackMixedWithXComp
 */
public class TestJhsdbJstackMixedWithXComp {

    /**
     * On Linux, check glibc version before the test and return true if it is
     * 2.39 or later. The test which needs to unwind native call stacks like
     * "jhsdb jstack --mixed" should be skip the test when this checker method
     * returns false.
     * The problem is not to unwind all of call stacks the process is running on
     * older glibc. It happens Debian 12, Ubuntu 22.04 (glibc 2.35) and
     * Ubuntu 23.04 (glibc 2.37) at least. It works on Ubuntu 24.04 (glibc 2.39).
     * The problem happenes both AMD64 and AArch64.
     */
    private static boolean canAttachLinuxOnCurrentGLIBC() {
        var linker = Linker.nativeLinker();
        var lookup = linker.defaultLookup();
        var sym = lookup.find("gnu_get_libc_version");
        if (sym.isEmpty()) {
            // Maybe the platform is not on glibc (Windows, Mac, musl on Alpine).
            // Go ahead.
            return true;
        }

        // Call gnu_get_libc_version()
        var desc = FunctionDescriptor.of(ValueLayout.ADDRESS);
        var func = linker.downcallHandle(sym.get(), desc);
        MemorySegment result;
        try {
            result = (MemorySegment)func.invoke();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

        // Set the length of glibc version because FFM does not know memory size
        // returned by gnu_get_libc_version().
        var strlenSym = lookup.find("strlen");
        var strlenDesc = FunctionDescriptor.of(linker.canonicalLayouts().get("size_t"), ValueLayout.ADDRESS);
        var strlenFunc = linker.downcallHandle(strlenSym.get(), strlenDesc);
        long len;
        try {
            len = (long)strlenFunc.invoke(result);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

        result = result.reinterpret(len + 1); // includes NUL
        String[] ver = result.getString(0, StandardCharsets.US_ASCII).split("\\.");
        int major = Integer.parseInt(ver[0]);
        int minor = Integer.parseInt(ver[1]);

        return major > 2 || (major == 2 && minor >= 39);
    }

    private static void runJstack(LingeredApp app) throws Exception {
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jhsdb");
        launcher.addVMArgs(Utils.getFilteredTestJavaOpts("-showversion"));
        launcher.addToolArg("jstack");
        launcher.addToolArg("--mixed");
        launcher.addToolArg("--pid");
        launcher.addToolArg(Long.toString(app.getPid()));

        ProcessBuilder pb = SATestUtils.createProcessBuilder(launcher);
        Process jhsdb = pb.start();
        OutputAnalyzer out = new OutputAnalyzer(jhsdb);

        jhsdb.waitFor();

        String stdout = out.getStdout();
        System.out.println(stdout);
        System.err.println(out.getStderr());

        out.stderrShouldBeEmptyIgnoreDeprecatedWarnings();

        List<String> targetStackTrace = new ArrayList<>();
        boolean inStack = false;
        for (String line : stdout.split("\n")) {
            if (line.contains("<nep_invoker_blob>")) {
                inStack = true;
            } else if (inStack && line.contains("-----------------")) {
                inStack = false;
                break;
            }

            if (inStack) {
                targetStackTrace.add(line);
            }
        }

        boolean found = targetStackTrace.stream()
                                        .anyMatch(l -> l.contains("thread_native_entry"));
        if (!found) {
            throw new RuntimeException("Test failed!");
        }
    }

    public static void main(String... args) throws Exception {
        SATestUtils.skipIfCannotAttach(); // throws SkippedException if attach not expected to work.
        if (!canAttachLinuxOnCurrentGLIBC()) {
            throw new SkippedException("SA Attach not expected to work. glibc is 2.38 or earlier.");
        }

        LingeredApp app = null;

        try {
            app = new LingeredAppWithVirtualThread();
            LingeredApp.startApp(app, "-Xcomp");
            System.out.println("Started LingeredApp with pid " + app.getPid());
            runJstack(app);
            System.out.println("Test Completed");
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        } finally {
            LingeredApp.stopApp(app);
        }
    }
}
