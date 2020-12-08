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

/*
 * @test
 * @library /test/lib
 * @summary Test that agent with non-public premain method is rejected to load
 * @modules java.base/jdk.internal.misc
 * @modules java.instrument
 *          jdk.jartool/sun.tools.jar
 * @run main NonPublicPremainAgent buildagent
 * @run main/othervm/timeout=6000 NonPublicPremainAgent runtest
 */

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.lang.RuntimeException;
import java.lang.instrument.Instrumentation;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class NonPublicPremainAgent {

    // This premain method is intentionally non-public to ensure it is rejected.
    static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("premain: NonPublicPremainAgent was not expected to be loaded");
    }

    private static void buildAgent() {
        try {
            ClassFileInstaller.main("NonPublicPremainAgent");
        } catch (Exception e) {
            throw new RuntimeException("Could not write agent classfile", e);
        }

        try {
            PrintWriter pw = new PrintWriter("MANIFEST.MF");
            pw.println("Premain-Class: NonPublicPremainAgent");
            pw.println("Agent-Class: NonPublicPremainAgent");
            pw.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Could not write manifest file for the agent", e);
        }

        sun.tools.jar.Main jarTool = new sun.tools.jar.Main(System.out, System.err, "jar");
        if (!jarTool.run(new String[] { "-cmf", "MANIFEST.MF", "redefineagent.jar", "NonPublicPremainAgent.class" })) {
            throw new RuntimeException("Could not write the agent jar file");
        }
    }

    public static void main(String argv[]) throws Exception {
        if (argv.length == 1 && argv[0].equals("buildagent")) {
            buildAgent();
            return;
        }
        if (argv.length == 1 && argv[0].equals("runtest")) {
            // run outside of jtreg to not OOM on jtreg classes that are loaded after metaspace is full
            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                    "-javaagent:redefineagent.jar",
                    "NonPublicPremainAgent");
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.shouldContain("IllegalAccessException");
        }
    }
}
