/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.spi.ToolProvider;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;


/*
 * @test
 * @bug 8206890
 * @summary Tests that a jlink image created using --endian option works fine when used to
 *          launch applications
 * @library /test/lib
 *
 * @run main JLinkEndianTest little
 * @run main JLinkEndianTest big
 * @run main JLinkEndianTest
 */
public class JLinkEndianTest {

    private static final ToolProvider JLINK_TOOL = ToolProvider.findFirst("jlink")
            .orElseThrow(() -> new RuntimeException("jlink tool not found"));

    private static final ToolProvider JAVAC_TOOL = ToolProvider.findFirst("javac")
            .orElseThrow(() -> new RuntimeException("javac tool not found"));

    private static final String HELLO_WORLD_APP = """
            public class Hello {
            	public static void main(final String[] args) throws Exception {
            		System.out.print("Hello world");
            	}
            }
            """;

    /**
     * Launches {@code jlink} command with different {@code --endian} options and then launches
     * the {@code java} command from the newly created image and verifies that the launched
     * java process works fine without running into errors.
     */
    public static void main(final String[] args) throws Exception {
        final String endian = args.length > 0 ? args[0] : "";
        final Path imageDir = Path.of(".", "8206890", System.currentTimeMillis() + endian);
        // invoke jlink:
        // jlink --add-modules java.base --add-modules jdk.compiler --output <path> --endian <endian>
        System.out.println("Creating image at " + imageDir + " with --endian=" + endian);
        final String[] commonArgs = new String[]{
                "--add-modules", "java.base",
                "--add-modules", "jdk.compiler",
                "--output", imageDir.toAbsolutePath().toString()
        };
        final String[] jlinkArgs;
        if (!endian.isEmpty()) {
            jlinkArgs = Arrays.copyOf(commonArgs, commonArgs.length + 2);
            jlinkArgs[jlinkArgs.length - 2] = "--endian";
            jlinkArgs[jlinkArgs.length - 1] = endian;
        } else {
            jlinkArgs = commonArgs;
        }
        System.out.println("Launching jlink with args: " + Arrays.toString(jlinkArgs));
        final int jlinkExitCode = JLINK_TOOL.run(System.out, System.err, jlinkArgs);
        if (jlinkExitCode != 0) {
            throw new AssertionError("jlink execution failed with exit code " + jlinkExitCode);
        }
        // verify the newly created image file is present
        final Path imageFile = Path.of(imageDir.toAbsolutePath().toString(), "lib", "modules");
        if (!Files.exists(imageFile)) {
            throw new AssertionError(imageFile + " is missing");
        }
        if (!Files.isRegularFile(imageFile)) {
            throw new AssertionError(imageFile + " is not a file");
        }
        // compile a trivial Java class which we will then launch using the newly generated image
        final Path helloWorldClassFile = compileApp();
        // now launch java from the created image and verify it launches correctly.
        launchJava(imageDir, helloWorldClassFile, false); // launch without security manager
        launchJava(imageDir, helloWorldClassFile, true); // launch with security manager
    }

    private static Path compileApp() throws Exception {
        final Path tmpDir = Files.createTempDirectory("8206890");
        final Path helloWorldJavaFile = Path.of(tmpDir.toAbsolutePath().toString(), "Hello.java");
        // write out the .java file
        Files.writeString(helloWorldJavaFile, HELLO_WORLD_APP);
        // now compile it
        final String[] javacArgs = new String[]{"-d", tmpDir.toAbsolutePath().toString(),
                helloWorldJavaFile.toAbsolutePath().toString()};
        final int exitCode = JAVAC_TOOL.run(System.out, System.err, javacArgs);
        if (exitCode != 0) {
            throw new AssertionError("Failed to compile hello world app");
        }
        final Path helloClassFile = Path.of(tmpDir.toAbsolutePath().toString(), "Hello.class");
        if (!Files.exists(helloClassFile)) {
            throw new AssertionError("Compiled class file is missing at " + helloClassFile);
        }
        System.out.println("Compiled Hello.class to " + helloClassFile);
        // return the Path to the Hello.class file
        return Path.of(tmpDir.toAbsolutePath().toString(), "Hello.class");
    }

    private static void launchJava(final Path imageDir, final Path helloWorldClassFile,
                                   final boolean withSecurityManager)
            throws Exception {
        // first try "java --version" from that created image
        final Path java = Path.of(imageDir.toAbsolutePath().toString(), "bin", "java");
        final String[] javaVersionCmd = new String[]{java.toAbsolutePath().toString(), "-version"};
        final String versionOutput = runProcess(javaVersionCmd, null).getStderr();
        System.out.println("java --version from newly created image returned: " + versionOutput);

        // now try launching a Java application from the newly created image
        final String[] helloWorldProcessCmd;
        if (withSecurityManager) {
            helloWorldProcessCmd = new String[]{
                    java.toAbsolutePath().toString(),
                    "-cp", ".",
                    "-Djava.security.manager=default",
                    "Hello"
            };
        } else {
            helloWorldProcessCmd = new String[]{
                    java.toAbsolutePath().toString(),
                    "-cp", ".",
                    "Hello"
            };
        }
        final String helloWorldOutput = runProcess(helloWorldProcessCmd,
                helloWorldClassFile.getParent().toFile()).getStdout();
        if (helloWorldOutput == null || !helloWorldOutput.equals("Hello world")) {
            throw new AssertionError("Unexpected output from hello world application: "
                    + helloWorldOutput);
        }
    }

    // launches a process and asserts that the process exits with exit code 0.
    // returns the OutputAnalyzer instance of the completed process
    private static OutputAnalyzer runProcess(final String[] processCmd, final File workingDir)
            throws Exception {
        System.out.println("Launching process: " + Arrays.toString(processCmd));
        final ProcessBuilder pb = new ProcessBuilder(processCmd).directory(workingDir);
        final OutputAnalyzer oa = ProcessTools.executeProcess(pb);
        final int exitCode = oa.getExitValue();
        if (exitCode != 0) {
            // dump the stdout and err of the completed process, for debugging
            oa.reportDiagnosticSummary();
            throw new AssertionError("Process execution failed with exit code: " + exitCode);
        }
        return oa;
    }
}
