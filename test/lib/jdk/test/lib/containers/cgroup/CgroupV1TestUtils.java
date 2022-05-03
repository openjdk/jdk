/*
 * Copyright (C) 2022 THL A29 Limited, a Tencent company. All rights reserved.
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

package jdk.test.lib.containers.cgroup;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;


public class CgroupV1TestUtils {


    // Specifies how many lines to copy from child STDOUT to main test output.
    // Having too many lines in the main test output will result
    // in JT harness trimming the output, and can lead to loss of useful
    // diagnostic information.
    private static final int MAX_LINES_TO_COPY_FOR_CHILD_STDOUT = 100;

    // Path to a JDK under test.
    // This may be useful when developing tests on non-Linux platforms.
    public static final String JDK_UNDER_TEST =
            System.getProperty("jdk.test.cgroupv1.jdk", Utils.TEST_JDK);

    /**
     * Execute a specified command in a process, report diagnostic info.
     *
     * @param command to be executed
     * @return The output from the process
     * @throws Exception
     */
    public static OutputAnalyzer execute(String... command) throws Exception {
        return CommandUtils.execute("cgroupv1-stdout-%d.log", MAX_LINES_TO_COPY_FOR_CHILD_STDOUT, command);
    }


    public static void createSubSystem(String subSystemName) throws Exception {
        execute("cgcreate", "-g", subSystemName)
                .shouldHaveExitValue(0);
    }


    public static void initSubSystem(String subSystemName,String info) throws Exception {
        OutputAnalyzer outputAnalyzer = execute("cgset", "-r", info, subSystemName);
        System.out.println(outputAnalyzer.getOutput());

    }

    public static void deleteSubSystem(String subSystemName) throws Exception {
        execute("cgdelete", subSystemName)
                .shouldHaveExitValue(0);
    }

    public static OutputAnalyzer runJavaApp(List<String> subSystemList, List<String> jvmOps, String command)
            throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("cgexec");
        for (String subSystemName : subSystemList) {
            cmd.add("-g");
            cmd.add(subSystemName);
        }
        Path jdkSrcDir = Paths.get(JDK_UNDER_TEST);
        cmd.add(jdkSrcDir.toString() + "/bin/java");
        cmd.addAll(jvmOps);
        cmd.add(command);

        return execute(cmd.toArray(new String[0]));
    }
}
