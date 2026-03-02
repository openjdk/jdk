/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug     4530538
 * @summary Basic unit test of RuntimeMXBean.getInputArguments().
 *
 * @author  Mandy Chung
 *
 * @run main InputArgument
 */

/*
 * @test
 * @bug     4530538
 * @summary Basic unit test of RuntimeMXBean.getInputArguments().
 *
 * @author  Mandy Chung
 *
 * @run main/othervm -XX:+UseFastJNIAccessors -Xlog:gc*=debug InputArgument
 */

/*
 * @test
 * @bug     4530538
 * @summary Basic unit test of RuntimeMXBean.getInputArguments().
 *
 * @author  Mandy Chung
 *
 * @run main/othervm -XX:+UseFastJNIAccessors -Xlog:gc*=debug InputArgument
 * -XX:+UseFastJNIAccessors
 */

/*
 * @test
 * @bug     4530538
 * @summary Basic unit test of RuntimeMXBean.getInputArguments().
 *
 * @author  Mandy Chung
 *
 * @run main/othervm -Dprops=something InputArgument -Dprops=something
 */

/*
 * @test
 * @bug     8378110
 * @summary RuntimeMXBean.getInputArguments() handling of flags from settings file.
 *
 * @run driver InputArgument generateFlagsFile
 * @run main/othervm -XX:+UseFastJNIAccessors -XX:Flags=InputArgument.flags InputArgument
 * -XX:+UseFastJNIAccessors -XX:-UseG1GC -XX:+UseParallelGC -XX:MaxHeapSize=100M
 */

import java.lang.management.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.ListIterator;

public class InputArgument {
    private static RuntimeMXBean rm = ManagementFactory.getRuntimeMXBean();

    public static void main(String args[]) throws Exception {
        if (args.length == 1 && "generateFlagsFile".equals(args[0])) {
            generateFlagsFile();
            return;
        }

        String[] vmOptions = args;
        List<String> options = rm.getInputArguments();
        System.out.println("Number of arguments = " + options.size());
        int i = 0;
        for (String arg : options) {
            System.out.println("Input arg " + i + " = " + arg);
            i++;
        }

        for (String expected : vmOptions) {
            boolean found = false;
            for (String arg : options) {
                if (arg.equals(expected)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new RuntimeException("TEST FAILED: " +
                    "VM option " + expected + " not found");
            }
        }

        System.out.println("Test passed.");
    }

    private static void generateFlagsFile() throws Exception {
        // 3 types of flag; both boolean cases and 1 numeric
        Files.writeString(Paths.get("", "InputArgument.flags"),
            String.format("-UseG1GC%n+UseParallelGC%nMaxHeapSize=100M%n"));
    }
}
