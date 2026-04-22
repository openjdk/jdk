/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Example test to use the Compile Framework and run the compiled code with additional flags
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver compile_framework.examples.RunWithFlagsExample
 */

package compile_framework.examples;

import compiler.lib.compile_framework.*;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

/**
 * This test shows how the generated code can be compiled and invoked in a new VM. This allows
 * the execution of the code with additional VM flags and options.
 * <p>
 * The new VM must be able to locate the class files of the newly compiled code. For this we
 * set the class path using {@link CompileFramework#getEscapedClassPathOfCompiledClasses}.
 */
public class RunWithFlagsExample {

    private static String generate() {
        return """
               package p.xyz;

               public class X {
                   public static void main(String args[]) {
                       System.out.println("Hello world!");
                       System.out.println(System.getProperty("MyMessage", "fail"));
                       System.err.println(args[0]);
                   }
               }
               """;
    }

    public static void main(String[] args) throws Exception {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a Java source file.
        comp.addJavaSourceCode("p.xyz.X", generate());

        // Compile the source file.
        comp.compile();

        // Build command line.
        String[] command = {
            // Set the classpath to include our newly compiled class.
            "-classpath",
            comp.getEscapedClassPathOfCompiledClasses(),
            // Pass additional flags here.
            // "-Xbatch" is a harmless VM flag, so this example runs everywhere without issues.
            "-Xbatch",
            // We can also pass properties like "MyMessage".
            "-DMyMessage=hello_world",
            "p.xyz.X",
            "hello_arg"
        };

        // Execute the command, and capture the output.
        // The JTREG Java and VM options are automatically passed to the test VM.
        OutputAnalyzer analyzer = ProcessTools.executeTestJava(command);

        // Verify output.
        analyzer.shouldHaveExitValue(0);
        analyzer.stdoutContains("Hello world!");
        analyzer.stdoutContains("hello_world");
        analyzer.stdoutContains("hello_arg");

        // Print output to stderr.
        analyzer.reportDiagnosticSummary();
    }
}
