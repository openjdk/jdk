/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test JsrRewriting
 * @summary JSR (jump local subroutine)
 *      rewriting can overflow memory address size variables
 * @bug 7020373
 * @bug 7055247
 * @bug 7053586
 * @bug 7185550
 * @bug 7149464
 * @requires vm.flagless
 * @library /test/lib
 * @library /testlibrary/asm
 * @modules java.base/jdk.internal.misc
 *          java.desktop
 *          java.management
 * @run driver JsrRewriting
 */

import java.io.File;
import java.nio.file.Files;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class JsrRewriting {

    public static void main(String[] args) throws Exception {

        // create a file in the scratch dir
        String className = "OOMCrashClass4000_1";
        File classFile = new File(className + ".class");
        classFile.createNewFile();

        // fill it with the binary data of the class file
        byte[] bytes = OOMCrashClass4000_1.dump();
        Files.write(classFile.toPath(), bytes);

        // ======= execute the test
        // We run the test with MallocLimit set to 768m in oom mode,
        // in order to trigger and observe a fake os::malloc oom. This needs NMT.
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-cp", ".",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:NativeMemoryTracking=summary",
            "-XX:MallocLimit=768m:oom",
            className);

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotHaveExitValue(0);
        String[] expectedMsgs = {
            "java.lang.LinkageError",
            "java.lang.NoSuchMethodError",
            "Main method not found in class " + className,
            "insufficient memory"
        };

        MultipleOrMatch(output, expectedMsgs);
    }

    private static void
        MultipleOrMatch(OutputAnalyzer analyzer, String[] whatToMatch) {
            String output = analyzer.getOutput();

            for (String expected : whatToMatch)
                if (output.contains(expected))
                    return;

            String err =
                " stdout: [" + analyzer.getOutput() + "];\n" +
                " exitValue = " + analyzer.getExitValue() + "\n";
            System.err.println(err);

            StringBuilder msg = new StringBuilder("Output did not contain " +
                "any of the following expected messages: \n");
            for (String expected : whatToMatch)
                msg.append(expected).append(System.lineSeparator());
            throw new RuntimeException(msg.toString());
    }
}

