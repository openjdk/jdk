/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Fuzzer based on Template Framework
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

package compiler.c2;

import java.util.ArrayList;
import java.util.List;

import compiler.lib.compile_framework.*;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

/**
 * TODO: desc
 */
public class TemplateFuzzer {

    private static String generate() {
        return """
               package compiler.c2.templated;

               public class Generated {
                   public static void main(String args[]) {
                       System.out.println("Hello world!");
                   }
               }
               """;
    }
    // TODO: design the mechanics!
    // - Statement
    //   - can check if applicable in context
    //   - can instantiate the template, and recurse
    // - Context
    //   - Has list of available statements
    //   - Can dispatch the nested statements

    public static void main(String[] args) throws Exception {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a Java source file.
        comp.addJavaSourceCode("compiler.c2.templated.Generated", generate());

        // Compile the source file.
        comp.compile();

        List<String> cmd_common = List.of(
            "-classpath",
            comp.getEscapedClassPathOfCompiledClasses(),
            "-Xbatch",
            "compiler.c2.templated.Generated"
        );

        List<String> cmd1 = new ArrayList<>();
        List<String> cmd2 = new ArrayList<>();
        // TODO: set different flags, and execute cmd2 as well
        cmd1.addAll(cmd_common);
        cmd2.addAll(cmd_common);

        // Execute the command, and capture the output.
        // The JTREG Java and VM options are automatically passed to the test VM.
        OutputAnalyzer analyzer = ProcessTools.executeTestJava(cmd1);
        analyzer.reportDiagnosticSummary();
        analyzer.shouldHaveExitValue(0);

        String stdout = analyzer.getStdout();
        System.out.println("stdout: " + stdout);
    }
}
