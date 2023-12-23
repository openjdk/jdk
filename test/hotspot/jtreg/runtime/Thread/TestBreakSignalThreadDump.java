/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, Google and/or its affiliates. All rights reserved.
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
 * @test id=default
 * @bug 8292695
 * @summary Check that Ctrl-\ or Ctrl-Break (on Windows) causes HotSpot VM to print a full thread dump.
 * @library /vmTestbase
 *          /test/lib
 * @run driver TestBreakSignalThreadDump
 */

/*
 * @test id=with_jsig
 * @bug 8292695
 * @summary Check that Ctrl-\ causes HotSpot VM to print a full thread dump when signal chaining is used.
 * @requires os.family != "windows" & os.family != "aix"
 * @library /vmTestbase
 *          /test/lib
 * @run driver TestBreakSignalThreadDump load_libjsig
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import jdk.test.lib.Platform;
import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import vm.share.ProcessUtils;

public class TestBreakSignalThreadDump {

    static class TestProcess {
        static {
            System.loadLibrary("ProcessUtils");
        }

        public static void main(String[] argv) throws Exception {
            ProcessUtils.sendCtrlBreak();
            // Wait a bit, as JVM processes the break signal asynchronously.
            Thread.sleep(1000);
            System.out.println("Done!");
        }
    }

    public static void main(String[] argv) throws Exception {
        String main = "TestBreakSignalThreadDump$TestProcess";
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder("-Djava.library.path=" + Utils.TEST_NATIVE_PATH, main);

        if (argv.length > 0 && argv[0].equals("load_libjsig")) {
            prepend_jsig_lib(pb.environment());
        }

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        output.shouldContain("Full thread dump ");
        output.shouldContain("java.lang.Thread.State: RUNNABLE");
        output.shouldContain("Done!");
    }

    private static void prepend_jsig_lib(Map<String, String> env) {
        Path libjsig = Platform.jvmLibDir().resolve("libjsig." + Platform.sharedLibraryExt());
        if (!Files.exists(libjsig)) {
            throw new RuntimeException("File libjsig not found, path: " + libjsig);
        }
        String env_var = Platform.isOSX() ? "DYLD_INSERT_LIBRARIES" : "LD_PRELOAD";
        env.put(env_var, libjsig.toString());
    }
}
