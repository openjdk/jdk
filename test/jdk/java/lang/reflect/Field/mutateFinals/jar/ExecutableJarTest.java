/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8353835
 * @summary Test the executable JAR file attribute Enable-Final-Field-Mutation
 * @library /test/lib
 * @build m/*
 * @build ExecutableJarTestHelper jdk.test.lib.util.JarUtils
 * @run junit ExecutableJarTest
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.util.JarUtils;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExecutableJarTest {

    /**
     * Test executable JAR with code that uses Field.set to mutate a final field.
     * A warning should be printed.
     */
    @Test
    void testFieldSetExpectingWarning() throws Exception {
        String jarFile = createExecutableJar(Map.of());
        testExecutableJar(jarFile, "testFieldSetInt")
                .shouldContain("WARNING: Final field value in class ExecutableJarTestHelper")
                .shouldContain(" has been mutated reflectively by class ExecutableJarTestHelper in unnamed module")
                .shouldHaveExitValue(0);
    }

    /**
     * Test executable JAR with Enable-Final-Field-Mutation attribute and code that uses
     * Field.set to mutate a final field. No warning should be printed.
     */
    @Test
    void testFieldSetExpectingAllow() throws Exception {
        String jarFile = createExecutableJar(Map.of("Enable-Final-Field-Mutation", "ALL-UNNAMED"));
        testExecutableJar(jarFile, "testFieldSetInt")
                .shouldNotContain("WARNING: Final field value in class ExecutableJarTestHelper")
                .shouldNotContain(" has been mutated reflectively by class ExecutableJarTestHelper in unnamed module")
                .shouldHaveExitValue(0);
    }

    /**
     * Test executable JAR with Enable-Final-Field-Mutation attribute and code that uses
     * Lookup.unreflectSetter to get MH to a final field. No warning should be printed.
     */
    @Test
    void testUnreflectExpectingAllow() throws Exception {
        String jarFile = createExecutableJar(Map.of("Enable-Final-Field-Mutation", "ALL-UNNAMED"));
        testExecutableJar(jarFile, "testUnreflectSetter")
                .shouldNotContain("WARNING: Final field value in class ExecutableJarTestHelper")
                .shouldNotContain(" has been unreflected for mutation by class ExecutableJarTestHelper in unnamed module")
                .shouldHaveExitValue(0);
    }

    /**
     * Test executable JAR with Enable-Final-Field-Mutation attribute and code that uses
     * Field.set to mutate a final field of class in a named module. The package is opened
     * with --add-open.
     */
    @Test
    void testFieldSetWithAddOpens1() throws Exception {
        String jarFile = createExecutableJar(Map.of(
                "Enable-Final-Field-Mutation", "ALL-UNNAMED"));
        testExecutableJar(jarFile, "testFieldInNamedModule",
                "--illegal-final-field-mutation=deny",
                "--module-path", modulePath(),
                "--add-modules", "m",
                "--add-opens", "m/p=ALL-UNNAMED")
                .shouldHaveExitValue(0);
    }

    /**
     * Test executable JAR with Enable-Final-Field-Mutation attribute and code that uses
     * Field.set to mutate a final field of class in a named module. The package is opened
     * with with the Add-Opens attribute.
     */
    @Test
    void testFieldSetWithAddOpens2() throws Exception {
        String jarFile = createExecutableJar(Map.of(
                "Enable-Final-Field-Mutation", "ALL-UNNAMED",
                "Add-Opens", "m/p"));
        testExecutableJar(jarFile, "testFieldInNamedModule",
                "--illegal-final-field-mutation=deny",
                "--module-path", modulePath(),
                "--add-modules", "m")
                .shouldHaveExitValue(0);
    }

    /**
     * Test executable JAR with Enable-Final-Field-Mutation set to a bad value in the manifest.
     */
     @Test
     void testFinalFieldMutationBadValue() throws Exception {
         String jarFile = createExecutableJar(Map.of("Enable-Final-Field-Mutation", "BadValue"));
         testExecutableJar(jarFile, "testFieldSetInt")
                 .shouldContain("Error: illegal value \"BadValue\" for Enable-Final-Field-Mutation" +
                         " manifest attribute. Only ALL-UNNAMED is allowed")
                 .shouldNotHaveExitValue(0);
     }

    /**
     * Launch ExecutableJarTestHelper with the given arguments and VM options.
     */
    private OutputAnalyzer test(String action, String... vmopts) throws Exception {
        Stream<String> s1 = Stream.of(vmopts);
        Stream<String> s2 = Stream.of("ExecutableJarTestHelper", action);
        String[] opts = Stream.concat(s1, s2).toArray(String[]::new);
        var outputAnalyzer = ProcessTools
                .executeTestJava(opts)
                .outputTo(System.err)
                .errorTo(System.err);
        return outputAnalyzer;
    }

    /**
     * Launch ExecutableJarTestHelper with the given arguments and VM options.
     */
    private OutputAnalyzer testExecutableJar(String jarFile,
                                             String action,
                                             String... vmopts) throws Exception {
        Stream<String> s1 = Stream.of(vmopts);
        Stream<String> s2 = Stream.of("-jar", jarFile, action);
        String[] opts = Stream.concat(s1, s2).toArray(String[]::new);
        var outputAnalyzer = ProcessTools
                .executeTestJava(opts)
                .outputTo(System.err)
                .errorTo(System.err);
        return outputAnalyzer;
    }

    /**
     *  Creates executable JAR named helper.jar with ExecutableJarTestHelper* classes.
     */
    private String createExecutableJar(Map<String, String> map) throws Exception {
        Path jarFile = Path.of("helper.jar");
        var man = new Manifest();
        Attributes attrs = man.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.put(Attributes.Name.MAIN_CLASS, "ExecutableJarTestHelper");
        map.entrySet().forEach(e -> {
            var name = new Attributes.Name(e.getKey());
            attrs.put(name, e.getValue());
        });
        Path dir = Path.of(System.getProperty("test.classes"));
        try (Stream<Path> stream = Files.list(dir)) {
            Path[] files = Files.list(dir).filter(p -> {
                        String fn = p.getFileName().toString();
                        return fn.startsWith("ExecutableJarTestHelper") && fn.endsWith(".class");
                    })
                    .toArray(Path[]::new);
            JarUtils.createJarFile(jarFile, man, dir, files);
        }
        return jarFile.toString();
    }

    /**
     * Return the module path for the modules used by this test.
     */
    private String modulePath() {
        return Path.of(System.getProperty("test.classes"), "modules").toString();
    }
}