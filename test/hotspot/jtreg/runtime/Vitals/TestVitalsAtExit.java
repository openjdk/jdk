/*
 * Copyright (c) 2021, SAP SE. All rights reserved.
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
 * @test TestVitalsAtExit
 * @summary Test verifies that -XX:+PrintVitalsAtExit prints vitals at exit.
 * @library /test/lib
 * @run driver TestVitalsAtExit run print
 */

/*
 * @test TestVitalsAtExit
 * @summary Test verifies that -XX:+DumpVitalsAtExit works
 * @library /test/lib
 * @run driver TestVitalsAtExit run dump
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import java.io.File;

public class TestVitalsAtExit {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            try {
		Thread.sleep(2000);
            } catch (InterruptedException err) {
            }
            return;
        }
        if (args[0].equals("print")) {
            testPrint();
        } else {
            testDump();
        }
    }

    static void testPrint() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-XX:+EnableVitals",
                "-XX:+PrintVitalsAtExit",
                "-XX:MaxMetaspaceSize=16m",
                "-Xmx128m",
                TestVitalsAtExit.class.getName());

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.stdoutShouldNotBeEmpty();
        output.shouldContain("--jvm--");
    }

    static void testDump() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-XX:+EnableVitals",
                "-XX:+DumpVitalsAtExit",
                "-XX:VitalsFile=abcd",
                "-XX:MaxMetaspaceSize=16m",
                "-Xmx128m",
                TestVitalsAtExit.class.getName());

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.stdoutShouldNotBeEmpty();
        output.shouldContain("Dumping Vitals to abcd.txt");
        output.shouldContain("Dumping Vitals csv to abcd.csv");
        File dump = new File("abcd.txt");
        Asserts.assertTrue(dump.exists() && dump.isFile(),
                "Could not find abcd.txt");
        File dump2 = new File("abcd.csv");
        Asserts.assertTrue(dump2.exists() && dump2.isFile(),
                "Could not find abcd.csv");
    }

}
