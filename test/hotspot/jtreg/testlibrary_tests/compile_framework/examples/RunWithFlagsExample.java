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

import jdk.test.lib.Utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.List;

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
                   }
               }
               """;
    }

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("p.xyz.X", generate());

        // Compile the source file.
        comp.compile();

        // Build command line.
        List<String> command = new ArrayList<>();
        command.add("%s/bin/java".formatted(System.getProperty("test.jdk")));
        // Pass JVM options from JTREG to our new VM.
        command.addAll(Arrays.asList(Utils.getTestJavaOpts()));
        // Set the classpath to include our newly compiled class.
        command.add("-classpath");
        command.add(comp.getEscapedClassPathOfCompiledClasses());
        // Pass additional flags here.
        // And "-Xbatch" is a harmless VM flag, so this example runs everywhere without issue.
        // We can also pass properties like "MyMessage".
        command.add("-Xbatch");
        command.add("-DMyMessage=hello_world");
        command.add("p.xyz.X");
        System.out.println("Running on command-line: " + String.join(" ", command));

        // Execute command, and capture the output.
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);

        String output;
        int exitCode;
        try {
            Process process = builder.start();
            boolean exited = process.waitFor(60, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
                System.out.println("Timeout: compile command: " + String.join(" ", command));
                throw new RuntimeException("Process timeout: compilation took too long.");
            }
            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            exitCode = process.exitValue();
        } catch (IOException e) {
            throw new RuntimeException("IOException when launching new VM", e);
        } catch (InterruptedException e) {
            throw new RuntimeException("InterruptedException when launching new VM", e);
        }

        // Verify output.
        System.err.println("Exit code: " + exitCode);
        System.err.println("Output: '" + output + "'");
        if (exitCode != 0) {
            throw new RuntimeException("Exit code must be zero!");
        }
        if (!output.contains("Hello world!")) {
            throw new RuntimeException("Did not find 'Hello world!' in output!");
        }
        if (!output.contains("hello_world")) {
            throw new RuntimeException("Did not find 'hello_world' in output!");
        }
    }
}
