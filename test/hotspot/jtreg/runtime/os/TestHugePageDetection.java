/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Red Hat Inc.
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
 * @summary Test that the JVM detects the OS hugepage/THP settings correctly.
 * @library /test/lib
 * @requires vm.flagless
 * @requires os.family == "linux"
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver TestHugePageDetection
 */

import java.util.*;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestHugePageDetection {

    public static void main(String[] args) throws Exception {

        ArrayList<String> finalargs = new ArrayList<String>();
        String[] defaultArgs = {
            "-Xlog:pagesize", "-Xmx64M", "-XX:-CreateCoredumpOnCrash"
        };
        finalargs.addAll(Arrays.asList(defaultArgs));
        finalargs.add("-version");

        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                new String[] {"-Xlog:pagesize", "-Xmx64M", "-version"});

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.reportDiagnosticSummary();
        output.shouldHaveExitValue(0);

        // The configuration detected by the JVM should match the OS settings

        HugePageConfiguration configurationFromOS = HugePageConfiguration.readFromOS();
        System.out.println("Configuration read from OS: " + configurationFromOS);

        HugePageConfiguration configurationFromLog = HugePageConfiguration.readFromJVMLog(output);
        System.out.println("Configuration read from JVM log: " + configurationFromLog);

        if (configurationFromOS.equals(configurationFromLog)) {
            System.out.println("Okay");
        } else {
            throw new RuntimeException("Configurations differ");
        }

        // If we want to run

    }

}
