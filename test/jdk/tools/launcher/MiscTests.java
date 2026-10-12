/*
 * Copyright (c) 2010, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.spi.ToolProvider;

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import org.junit.jupiter.api.Test;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8154212 8154470 8392966
 * @summary Miscellaneous tests for java launcher
 * @library /test/lib
 * @build jdk.test.lib.process.ProcessTools jdk.test.lib.JDKToolFinder
 *        jdk.test.lib.process.OutputAnalyzer
 * @run junit ${test.main.class}
 */
public class MiscTests {

    private static final ToolProvider JAVAC = ToolProvider.findFirst("javac")
            .orElseThrow(() -> new RuntimeException("javac not found"));

    /**
     * Launch "java" with class path set on the command line via -Djava.class.path
     * and verify that the launcher correctly launches the main application
     */
    @Test
    void testWithClassPathSetViaProperty() throws Exception {
        final String mainClass = "Foo";
        // create the .java file
        final Path sourceFile = Path.of(mainClass + ".java");
        final String content = """
                public class Foo {
                    public static void main(String... args) {}
                }
                """;
        Files.writeString(sourceFile, content);
        // compile it
        final int compilationExitVal = JAVAC.run(System.out, System.err, sourceFile.toString());
        assertEquals(0, compilationExitVal, "compilation of " + sourceFile + " failed");
        final Path compiledFile = sourceFile.toAbsolutePath().getParent()
                .resolve(mainClass + ".class");
        assertTrue(Files.isRegularFile(compiledFile), "missing or not a regular" +
                " file " + compiledFile);
        // directory into which the .class was compiled to
        final Path classPathDir = compiledFile.getParent();
        // launch "java" with the classpath system property
        final List<String> javaCmdArgs = List.of(
                "-Djava.class.path=" + classPathDir,
                mainClass
        );
        final OutputAnalyzer oa = ProcessTools.executeTestJava(javaCmdArgs);
        oa.reportDiagnosticSummary();
        oa.shouldHaveExitValue(0);
    }

    /**
     * Verify that the "_JAVA_LAUNCHER_DEBUG" can be configured to enable
     * debug logging for the "java" and "javac" launchers
     */
    @Test
    void testJavaLauncherDebugEnvVar() throws Exception {
        for (String cmd : new String[]{"java", "javac"}) {
            final String toolLocation = JDKToolFinder.getJDKTool(cmd);
            System.err.println("running test against " + toolLocation);
            final ProcessBuilder pb = new ProcessBuilder(List.of(toolLocation, "-version"));
            pb.environment().put("_JAVA_LAUNCHER_DEBUG", "true");
            final OutputAnalyzer oa = ProcessTools.executeCommand(pb);
            oa.reportDiagnosticSummary();
            oa.shouldHaveExitValue(0);
            final String javargs = cmd.equals("javac") ? "on" : "off";
            final String progname = cmd.equals("javac") ? "javac" : "java";
            assertTrue(oa.matches("\\s*debug:on$"), "missing debug:on in output");
            assertTrue(oa.matches("\\s*javargs:" + javargs + "$"), "missing javargs in output");
            assertTrue(oa.matches("\\s*program name:" + progname + "$"),
                    "missing program name in output");
        }
    }

    /**
     * Verify that when a JAR file with no META-INF/MANIFEST.MF file is launched
     * using "java -jar" then the launch fails
     */
    @Test
    void testExecutableJARNoManifest() throws Exception {
        final Path jarFile = Files.createTempFile(Path.of("."), "8392966-", ".jar");
        // create a JAR file without any manifest
        try (JarOutputStream noManifest = new JarOutputStream(Files.newOutputStream(jarFile))) {
            final JarEntry entry = new JarEntry("foo.txt");
            noManifest.putNextEntry(entry);
            noManifest.write("bar".getBytes(US_ASCII));
            noManifest.closeEntry();
        }
        // run "java -jar" against that JAR file and expect the launch to fail
        final OutputAnalyzer oa = ProcessTools.executeTestJava("-jar", jarFile.toString());
        oa.shouldNotHaveExitValue(0); // expected to fail with non-zero exit code
        // verify it failed for the right reason
        oa.shouldContain("Error: No manifest in JAR file");
    }
}
