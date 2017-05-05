/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static jdk.testlibrary.Asserts.assertTrue;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * @test
 * @bug 8174826
 * @library /lib/testlibrary
 * @modules jdk.charsets jdk.compiler jdk.jlink
 * @build SuggestProviders CompilerUtils
 * @run testng SuggestProviders
 */

public class SuggestProviders {
    private static final String JAVA_HOME = System.getProperty("java.home");
    private static final String TEST_SRC = System.getProperty("test.src");

    private static final Path SRC_DIR = Paths.get(TEST_SRC, "src");
    private static final Path MODS_DIR = Paths.get("mods");

    private static final String MODULE_PATH =
        Paths.get(JAVA_HOME, "jmods").toString() +
        File.pathSeparator + MODS_DIR.toString();

    // the names of the modules in this test
    private static String[] modules = new String[] {"m1", "m2", "m3"};


    private static boolean hasJmods() {
        if (!Files.exists(Paths.get(JAVA_HOME, "jmods"))) {
            System.err.println("Test skipped. NO jmods directory");
            return false;
        }
        return true;
    }

    /*
     * Compiles all modules used by the test
     */
    @BeforeTest
    public void compileAll() throws Throwable {
        if (!hasJmods()) return;

        for (String mn : modules) {
            Path msrc = SRC_DIR.resolve(mn);
            assertTrue(CompilerUtils.compile(msrc, MODS_DIR,
                "--module-source-path", SRC_DIR.toString()));
        }
    }

    @Test
    public void suggestProviders() throws Throwable {
        if (!hasJmods()) return;

        List<String> output = JLink.run("--module-path", MODULE_PATH,
                                        "--add-modules", "m1",
                                        "--suggest-providers").output();
        // check a subset of services used by java.base
        List<String> expected = List.of(
            "uses java.lang.System$LoggerFinder",
            "uses java.net.ContentHandlerFactory",
            "uses java.net.spi.URLStreamHandlerProvider",
            "uses java.nio.channels.spi.AsynchronousChannelProvider",
            "uses java.nio.channels.spi.SelectorProvider",
            "uses java.nio.charset.spi.CharsetProvider",
            "uses java.nio.file.spi.FileSystemProvider",
            "uses java.nio.file.spi.FileTypeDetector",
            "uses java.security.Provider",
            "uses java.util.spi.ToolProvider",
            "uses p1.S",
            "module jdk.charsets provides java.nio.charset.spi.CharsetProvider, used by java.base",
            "module jdk.compiler provides java.util.spi.ToolProvider, used by java.base",
            "module jdk.jlink provides java.util.spi.ToolProvider, used by java.base",
            "module m1 provides p1.S, used by m1",
            "module m2 provides p1.S, used by m1"
        );

        assertTrue(output.containsAll(expected));
    }

    @Test
    public void providersForServices() throws Throwable {
        if (!hasJmods()) return;

        List<String> output =
            JLink.run("--module-path", MODULE_PATH,
                      "--add-modules", "m1",
                      "--suggest-providers",
                      "java.nio.charset.spi.CharsetProvider,p1.S,p2.T").output();

        System.out.println(output);
        List<String> expected = List.of(
            "module jdk.charsets provides java.nio.charset.spi.CharsetProvider, used by java.base",
            "module m1 provides p1.S, used by m1",
            "module m2 provides p1.S, used by m1",
            "module m2 provides p2.T, used by m2",
            "module m3 provides p2.T, used by m2"
        );

        assertTrue(output.containsAll(expected));
    }

    @Test
    public void unusedService() throws Throwable {
        if (!hasJmods()) return;

        List<String> output =
            JLink.run("--module-path", MODULE_PATH,
                "--add-modules", "m1",
                "--suggest-providers",
                "nonExistentType").output();

        System.out.println(output);
        List<String> expected = List.of(
            "Services specified in --suggest-providers not used: nonExistentType"
        );

        assertTrue(output.containsAll(expected));
    }

    @Test
    public void noSuggestProviders() throws Throwable {
        if (!hasJmods()) return;

        List<String> output =
            JLink.run("--module-path", MODULE_PATH,
                      "--add-modules", "m1",
                      "--bind-services",
                      "--limit-modules", "m1,m2,m3,java.base",
                      "--suggest-providers").output();

        String expected = "--bind-services option is specified. No additional providers suggested.";
        assertTrue(output.contains(expected));

    }

    static class JLink {
        static final ToolProvider JLINK_TOOL = ToolProvider.findFirst("jlink")
            .orElseThrow(() ->
                new RuntimeException("jlink tool not found")
            );

        static JLink run(String... options) {
            JLink jlink = new JLink();
            assertTrue(jlink.execute(options) == 0);
            return jlink;
        }

        final List<String> output = new ArrayList<>();
        private int execute(String... options) {
            System.out.println("jlink " +
                Stream.of(options).collect(Collectors.joining(" ")));

            StringWriter writer = new StringWriter();
            PrintWriter pw = new PrintWriter(writer);
            int rc = JLINK_TOOL.run(pw, pw, options);
            System.out.println(writer.toString());
            Stream.of(writer.toString().split("\\v"))
                  .map(String::trim)
                  .forEach(output::add);
            return rc;
        }

        boolean contains(String s) {
            return output.contains(s);
        }

        List<String> output() {
            return output;
        }
    }
}
