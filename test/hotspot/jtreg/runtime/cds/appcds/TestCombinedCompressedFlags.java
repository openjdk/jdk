/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8232069
 * @summary Testing different combination of CompressedOops and CompressedClassPointers
 * @requires vm.cds
 * @requires vm.gc == "null"
 * @requires vm.bits == 64
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @compile test-classes/Hello.java
 * @modules java.base/jdk.internal.misc
 * @run driver TestCombinedCompressedFlags
 */

import jdk.test.lib.process.OutputAnalyzer;
import java.util.List;
import java.util.ArrayList;

public class TestCombinedCompressedFlags {
    public static String HELLO_STRING = "Hello World";
    public static String EXEC_ABNORMAL_MSG = "Unable to use shared archive.";
    public static final int PASS = 0;
    public static final int FAIL = 1;

    static class ConfArg {
        public boolean useCompressedOops;            // UseCompressedOops
        public String  msg;
        public int code;
        public ConfArg(boolean useCompressedOops, String msg, int code) {
            this.useCompressedOops = useCompressedOops;
            this.msg  = msg;
            this.code = code;
        }
    }

    static class RunArg {
        public ConfArg dumpArg;
        public List<ConfArg> execArgs;
        public RunArg(ConfArg arg) {
            dumpArg = arg;
            initExecArgs();
        }
        private void initExecArgs() {
           /* The combinations have four cases.
            *          UseCompressedOops   Result
            *    1.
            *    dump: on
            *    test: on                  Pass
            *          off                 Fail
            *    2.
            *    dump: off
            *    test: off                 Pass
            *          on                  Fail
            **/
            execArgs = new ArrayList<ConfArg>();
            if (dumpArg.useCompressedOops) {
                execArgs
                    .add(new ConfArg(true, HELLO_STRING, PASS));
                execArgs
                    .add(new ConfArg(false, EXEC_ABNORMAL_MSG, FAIL));

            } else if (!dumpArg.useCompressedOops) {
                execArgs
                    .add(new ConfArg(false, HELLO_STRING, PASS));
                execArgs
                    .add(new ConfArg(true, EXEC_ABNORMAL_MSG, FAIL));
            }
        }
    }

    public static String getCompressedOopsArg(boolean on) {
        if (on) return "-XX:+UseCompressedOops";
        else    return "-XX:-UseCompressedOops";
    }

    public static List<RunArg> runList;

    public static void configureRunArgs() {
        runList = new ArrayList<RunArg>();
        runList
            .add(new RunArg(new ConfArg(true, null, PASS)));
        runList
            .add(new RunArg(new ConfArg(false, null, PASS)));
    }

    public static void main(String[] args) throws Exception {
        String helloJar = JarBuilder.build("hello", "Hello");
        configureRunArgs();
        OutputAnalyzer out;
        for (RunArg t: runList) {
            out = TestCommon
                .dump(helloJar,
                      new String[] {"Hello"},
                      getCompressedOopsArg(t.dumpArg.useCompressedOops),
                      "-Xlog:cds",
                      "-XX:NativeMemoryTracking=detail");
            out.shouldContain("Dumping shared data to file:");
            out.shouldHaveExitValue(0);

            for (ConfArg c : t.execArgs) {
                out = TestCommon.exec(helloJar,
                                      "-cp",
                                      helloJar,
                                      "-Xlog:cds",
                                      "-XX:NativeMemoryTracking=detail",
                                      getCompressedOopsArg(c.useCompressedOops),
                                      "Hello");
                out.shouldContain(c.msg);
                out.shouldHaveExitValue(c.code);
            }
        }
    }
}
