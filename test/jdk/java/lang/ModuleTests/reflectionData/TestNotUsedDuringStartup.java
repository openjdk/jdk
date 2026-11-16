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
 * @bug 8393235
 * @summary Tests command line options and deployments that augment the readability and
 *    export/uses graphs. The test checks that these additions are not recorded as
 *    reflective updates.
 * @modules java.xml
 * @library /test/lib
 * @build java.base/java.lang.ModuleHelper
 * @build p.TestEmpty jdk.test.lib.util.JarUtils
 * @run junit ${test.main.class}
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.util.JarUtils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestNotUsedDuringStartup {
    private static final String TEST_CLASSES = System.getProperty("test.classes");
    private static final String MAIN_CLASS = "p.TestEmpty";

    /**
     * Test --add-exports to export a package to a module or to all unnamed modules.
     */
    @Test
    void testAddExports() throws Exception {
        run("--add-exports", "java.base/jdk.internal.misc=java.xml", MAIN_CLASS);
        run("--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED", MAIN_CLASS);
    }

    /**
     * Test --add-opens to open a package to a module or all unnamed modules.
     */
    @Test
    void tesAddOpens() throws Exception {
        run("--add-opens", "java.base/jdk.internal.misc=java.xml", MAIN_CLASS);
        run("--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED", MAIN_CLASS);
    }

    /**
     * Test executable JAR with Add-Exports attribute to export a package to all unnamed modules.
     */
    @Test
    void testExecutableJarWithAddExports() throws Exception {
        String jarFile = createJar(
                "Main-Class", MAIN_CLASS,
                "Add-Exports", "java.base/jdk.internal.misc"
        );
        run("-jar", jarFile);
    }

    /**
     * Test executable JAR with Add-Opens attribute to open a package to all unnamed modules.
     */
    @Test
    void testExecutableJarWithAddOpens() throws Exception {
        String jarFile = createJar(
                "Main-Class", MAIN_CLASS,
                "Add-Opens", "java.base/jdk.internal.misc"
        );
        run("-jar", jarFile);
    }

    /**
     * Test automatic module.
     */
    @Test
    void testAutomaticModule() throws Exception {
        String jarFile = createJar("Automatic-Module-Name", "app");
        run("--module-path", jarFile, "--module", "app/" + MAIN_CLASS);
    }

    /**
     * Test --add-reads to have java.base read a module or all unnamed modules.
     */
    @Test
    void testAddReads() throws Exception {
        run("--add-reads", "java.base=java.xml", MAIN_CLASS);
        run("--add-reads", "java.base=ALL-UNNAMED", MAIN_CLASS);
    }

    /**
     * Creates executable JAR named helper.jar with ??* classes.
     */
    private String createJar(String... args) throws Exception {
        Path jarFile = Path.of("app.jar");
        var man = new Manifest();
        Attributes attrs = man.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        int i = 0;
        while (i < args.length) {
            var name = new Attributes.Name(args[i++]);
            String value = args[i++];
            attrs.put(name, value);
        }
        Path dir = Path.of(TEST_CLASSES);
        try (Stream<Path> stream = Files.find(dir, Integer.MAX_VALUE,  (p, a) -> a.isRegularFile())) {
            Path[] files = stream.map(p -> dir.relativize(p))
                    .filter(p -> p.startsWith("p"))
                    .toArray(Path[]::new);
            JarUtils.createJarFile(jarFile, man, dir, files);
        }
        return jarFile.toString();
    }

    void run(String... opts) throws Exception {
        Path patches = Path.of(TEST_CLASSES).resolve("patches").resolve("java.base");
        Stream<String> s1 = Stream.of("--patch-module", "java.base=" + patches);
        Stream<String> s2 = Stream.of(opts);
        String[] allOpts = Stream.concat(s1, s2).toArray(String[]::new);
        ProcessTools.executeTestJava(allOpts)
                .outputTo(System.err)
                .errorTo(System.err)
                .shouldHaveExitValue(0);
    }
}
