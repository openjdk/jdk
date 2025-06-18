/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 *
 * @bug 8316694
 * @summary Verify that nmethod relocation posts the correct JVMTI events
 *
 * @library /vmTestbase /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/native TestDriver
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Asserts;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class TestDriver {
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                "-agentlib:agentnmethodrelocation001=-waittime=5",
                "--enable-native-access=ALL-UNNAMED",
                "-Xbootclasspath/a:.",
                "-XX:+UseG1GC",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+WhiteBoxAPI",
                "-XX:+SegmentedCodeCache",
                "-XX:-TieredCompilation",
                "-Xbatch",
                nsk.jvmti.NMethodRelocation.nmethodrelocation.class.getName());

        OutputAnalyzer oa = new OutputAnalyzer(pb.start());
        String output = oa.getOutput();
        if (oa.getExitValue() != 0) {
            System.err.println(oa.getOutput());
            throw new RuntimeException("Non-zero exit code returned from the test");
        }
        Asserts.assertTrue(oa.getExitValue() == 0);

        Pattern pattern = Pattern.compile("(?m)^Relocated nmethod from (0x[0-9a-f]{16}) to (0x[0-9a-f]{16})$");
        Matcher matcher = pattern.matcher(output);

        if (matcher.find()) {
            String fromAddr = matcher.group(1);
            String toAddr = matcher.group(2);

            // Confirm events sent for both original and relocated nmethod
            oa.shouldContain("<COMPILED_METHOD_LOAD>:   name: compiledMethod, code: " + fromAddr);
            oa.shouldContain("<COMPILED_METHOD_LOAD>:   name: compiledMethod, code: " + toAddr);
            oa.shouldContain("<COMPILED_METHOD_UNLOAD>:   name: compiledMethod, code: " + fromAddr);
            oa.shouldContain("<COMPILED_METHOD_UNLOAD>:   name: compiledMethod, code: " + toAddr);
        } else {
            System.err.println(oa.getOutput());
            throw new RuntimeException("Unable to find relocation information");
        }
    }
}

