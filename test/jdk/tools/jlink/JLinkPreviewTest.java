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

import jdk.test.lib.compiler.InMemoryJavaCompiler;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.JarBuilder;
import jdk.tools.jlink.internal.LinkableRuntimeImage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tests.Helper;
import tests.JImageGenerator;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.spi.ToolProvider;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @summary Tests preview mode support in JLink.
 * @library /test/jdk/tools/lib
 *          /test/lib
 * @build jdk.test.lib.process.ProcessTools
 *        tests.*
 * @modules jdk.jlink/jdk.tools.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          java.base/jdk.internal.jimage
 * @run junit/othervm JLinkPreviewTest
 */
public class JLinkPreviewTest {
    private static final String TEST_MODULE = "java.test";
    private static final String TEST_PACKAGE = "test";
    private static final String TEST_CLASS = "InjectedTestClass";
    private static final int NORMAL_EXIT_VALUE = 23;
    private static final int PREVIEW_EXIT_VALUE = 42;

    private static final ToolProvider JLINK_TOOL = ToolProvider.findFirst("jlink")
            .orElseThrow(() -> new RuntimeException("jlink tool not found"));

    private static Path customJreRoot;

    @BeforeAll
    static void buildCustomBootImage(@TempDir Path tmp) throws Exception {
        Path jreRoot = tmp.resolve("testjdk");
        if (JLINK_TOOL.run(System.out, System.err,
                "--add-modules", "java.base",
                "--add-modules", "jdk.zipfs",
                "--output", jreRoot.toString()) != 0) {
            throw new RuntimeException("failed to create small boot image");
        }
        Path jimage = jreRoot.resolve("lib", "modules");

        Helper helper = getHelper();
        // Compile into the helper's jar directory so jlink will include it.
        compileTestModule(helper.getJarDir());
        Path customJimage = buildJimage(helper);
        Files.copy(customJimage, jimage, REPLACE_EXISTING);
        customJreRoot = jreRoot;
    }

    @Test
    public void nonPreviewMode() throws Exception {
        runTestClass(false, NORMAL_EXIT_VALUE, TEST_CLASS + ": NORMAL");
    }

    @Test
    public void previewMode() throws Exception {
        runTestClass(true, PREVIEW_EXIT_VALUE, TEST_CLASS + ": PREVIEW");
    }

    @Test
    public void ensureJimageContent() {
        Path jimage = customJreRoot.resolve("lib", "modules");
        // The jimage tool isn't present in the custom JRE, but should
        // have the same version by virtue of coming from the test JVM.
        StringWriter buffer = new StringWriter();
        assertEquals(0, jdk.tools.jimage.Main.run(new String[] { "list", jimage.toString() }, new PrintWriter(buffer)));
        List<String> outLines = buffer.toString().lines().map(String::strip).toList();

        String pkgPath = getPackagePath(TEST_PACKAGE + "." + TEST_CLASS);
        assertTrue(outLines.contains("Module: " + TEST_MODULE));
        assertTrue(outLines.contains(pkgPath));
        assertTrue(outLines.contains("META-INF/preview/" + pkgPath));
    }

    /// Returns the helper for building JAR and jimage files.
    private static Helper getHelper() {
        Helper helper;
        try {
            boolean isLinkableRuntime = LinkableRuntimeImage.isLinkableRuntime();
            helper = Helper.newHelper(isLinkableRuntime);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Assumptions.assumeTrue(helper != null, "Cannot create test helper, skipping test!");
        return helper;
    }

    /// Builds a jimage file with the specified class entries. The classes in
    /// the built image can be loaded and executed to return their names via
    /// `toString()` to confirm the correct bytes were returned.
    private static Path buildJimage(Helper helper) {
        Path outDir = helper.createNewImageDir("test");
        // The default module path contains the directory we compiled the jars into.
        JImageGenerator.JLinkTask jlink = JImageGenerator.getJLinkTask()
                .modulePath(helper.defaultModulePath())
                .output(outDir);
        jlink.addMods(TEST_MODULE);
        return jlink.call().assertSuccess().resolve("lib", "modules");
    }

    /// Compiles a test module containing test classes into a single Jar.
    /// The test class can be instantiated and have their {@code toString()}
    /// method called to return a status string for testing.
    private static void compileTestModule(Path jarDir) throws IOException {
        JarBuilder jar = new JarBuilder(jarDir.resolve(TEST_MODULE + ".jar").toString());
        String moduleInfo = "open module " + TEST_MODULE + " {}";
        jar.addEntry("module-info.class", InMemoryJavaCompiler.compile("module-info", moduleInfo));
        compileTestClass(jar, false);
        compileTestClass(jar, true);
        jar.build();
    }

    /// Compiles a test class into a given single Jar.
    private static void compileTestClass(JarBuilder jar, boolean isPreview) {
        String fqn = TEST_PACKAGE + "." + TEST_CLASS;
        String msg = isPreview ? "PREVIEW" : "NORMAL";
        int exit = isPreview ? PREVIEW_EXIT_VALUE : NORMAL_EXIT_VALUE;
        String testSrc = String.format(
                """
                package %1$s;
                public class %2$s {
                    public static void main(String[] args) {
                        System.out.println("%2$s: %3$s");
                        System.out.flush();
                        System.exit(%4$d);
                    }
                }
                """, TEST_PACKAGE, TEST_CLASS, msg, exit);
        String pkgPath = getPackagePath(fqn);
        String path = (isPreview ? "META-INF/preview/" : "") + pkgPath;
        jar.addEntry(path, InMemoryJavaCompiler.compile(fqn, testSrc));
    }

    private static void runTestClass(boolean isPreviewMode, int expectedExitValue, String expectedMessage) throws Exception {
        List<String> args = new ArrayList<>();
        args.add(customJreRoot.resolve("bin", "java").toString());
        if (isPreviewMode) {
            args.add("--enable-preview");
        }
        args.add("-m");
        args.add(TEST_MODULE + "/" + TEST_PACKAGE + "." + TEST_CLASS);
        ProcessBuilder cmd = new ProcessBuilder(args);
        OutputAnalyzer result = ProcessTools.executeCommand(cmd);
        assertEquals(expectedExitValue, result.getExitValue());
        assertEquals(expectedMessage + System.lineSeparator(), result.getStdout());
    }

    private static String getPackagePath(String fqn) {
        return fqn.replace('.', '/') + ".class";
    }
}
