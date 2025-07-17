/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.process.ProcessTools;
import jdk.tools.jlink.internal.LinkableRuntimeImage;
import tests.Helper;
import tests.Result;

/*
 * @test
 * @bug 8345259
 * @summary jlink test of --add-module ALL-MODULE-PATH
 * @library ../../lib /test/lib
 * @modules jdk.compiler
 *          java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jimage
 * @build jdk.test.lib.process.ProcessTools
 *        jdk.test.lib.process.OutputAnalyzer
 *        jdk.test.lib.compiler.CompilerUtils
 * @run testng/othervm -Duser.language=en -Duser.country=US AllModulePath
 */
public class AllModulePath {

    private static final Path JMODS = Paths.get(System.getProperty("test.jdk")).resolve("jmods");
    private static final Path SRC = Paths.get(System.getProperty("test.src")).resolve("src");
    private static final Path MODS = Paths.get("mods");
    private static final boolean LINKABLE_RUNTIME = LinkableRuntimeImage.isLinkableRuntime();
    private static final boolean JMODS_EXIST = Files.exists(JMODS);

    private final static Set<String> MODULES = Set.of("test", "m1");

    static final ToolProvider JLINK_TOOL = ToolProvider.findFirst("jlink")
        .orElseThrow(() ->
            new RuntimeException("jlink tool not found")
        );
    private static Helper HELPER;

    private static boolean isExplodedJDKImage() {
        if (!JMODS_EXIST && !LINKABLE_RUNTIME) {
            System.err.println("Test skipped. Not a linkable runtime and no JMODs");
            return true;
        }
        return false;
    }

    @BeforeClass
    public void setup() throws Throwable {
        if (isExplodedJDKImage()) {
            return;
        }
        HELPER = Helper.newHelper(LINKABLE_RUNTIME);

        Files.createDirectories(MODS);

        for (String mn : MODULES) {
            Path mod = MODS.resolve(mn);
            if (!CompilerUtils.compile(SRC.resolve(mn), mod)) {
                throw new AssertionError("Compilation failure. See log.");
            }
        }
    }

    /*
     * --add-modules ALL-MODULE-PATH with an existing module-path.
     */
    @Test
    public void testAllModulePath() throws Throwable {
        if (isExplodedJDKImage()) {
            return;
        }

        Path image = HELPER.createNewImageDir("image");
        List<String> opts = List.of("--module-path", MODS.toString(),
                                    "--output", image.toString(),
                                    "--add-modules", "ALL-MODULE-PATH");
        createImage(image, opts, true /* success */);

        Set<String> modules = new HashSet<>();
        // java.base is a dependency of any external module
        modules.add("java.base");
        modules.add("m1");
        modules.add("test");
        checkModules(image, modules);
    }

    /*
     * --add-modules ALL-MODULE-PATH with --limit-modules is an error
     */
    @Test
    public void testLimitModules() throws Throwable {
        if (isExplodedJDKImage()) {
            return;
        }
        Path targetPath = HELPER.createNewImageDir("all-mods-limit-mods");
        String moduleName = "com.baz.runtime";
        Result result = HELPER.generateDefaultJModule(moduleName, "jdk.jfr");
        Path customModulePath = result.getFile().getParent();
        List<String> allArgs = List.of("--add-modules", "ALL-MODULE-PATH",
                                       "--limit-modules", "jdk.jfr",
                                       "--module-path", customModulePath.toString(),
                                       "--output", targetPath.toString());
        JlinkOutput allOut = createImage(targetPath, allArgs, false /* success */);
        String actual = allOut.stdout.trim();
        String expected = "Error: --limit-modules not allowed with --add-modules ALL-MODULE-PATH";
        assertEquals(actual, expected);
    }


    /*
     * --add-modules *includes* ALL-MODULE-PATH with an existing module path
     */
    @Test
    public void testAddModules() throws Throwable {
        if (isExplodedJDKImage()) {
            return;
        }

        // create custom image
        Path image = HELPER.createNewImageDir("image2");
        List<String> opts = List.of("--module-path", MODS.toString(),
                                    "--output", image.toString(),
                                    "--add-modules", "m1",
                                    "--add-modules", "ALL-MODULE-PATH");
        createImage(image, opts, true /* success */);

        checkModules(image, Set.of("m1", "test", "java.base"));
    }

    /*
     * No --module-path with --add-modules ALL-MODULE-PATH is an error.
     */
    @Test
    public void noModulePath() throws IOException {
        if (isExplodedJDKImage()) {
            return;
        }
        Path targetPath = HELPER.createNewImageDir("all-mod-path-no-mod-path");
        List<String> allArgs = List.of("--add-modules", "ALL-MODULE-PATH",
                                       "--output", targetPath.toString());
        JlinkOutput allOut = createImage(targetPath, allArgs, false /* expect failure */);
        String expected = "Error: --module-path option must be specified with --add-modules ALL-MODULE-PATH";
        assertEquals(allOut.stdout.trim(), expected);
    }

    /*
     * --module-path not-exist and --add-modules ALL-MODULE-PATH is an error.
     */
    @Test
    public void modulePathEmpty() throws IOException {
        if (isExplodedJDKImage()) {
            return;
        }
        Path targetPath = HELPER.createNewImageDir("all-mod-path-not-existing");
        String strNotExists = "not-exist";
        Path notExists = Path.of(strNotExists);
        if (Files.exists(notExists)) {
            throw new AssertionError("Test setup error, path must not exist!");
        }
        List<String> allArgs = List.of("--add-modules", "ALL-MODULE-PATH",
                                       "--module-path", notExists.toString(),
                                       "--output", targetPath.toString());

        JlinkOutput allOut = createImage(targetPath, allArgs, false /* expect failure */);
        String actual = allOut.stdout.trim();
        assertTrue(actual.startsWith("Error: No module found in module path"));
        assertTrue(actual.contains(strNotExists));
    }

    /*
     * check the modules linked in the image using m1/p.ListModules
     */
    private void checkModules(Path image, Set<String> modules) throws Throwable {
        Path cmd = findTool(image, "java");

        List<String> options = new ArrayList<>();
        options.add(cmd.toString());
        options.add("-m");
        options.add("m1/p.ListModules");
        options.addAll(modules);

        ProcessBuilder pb = new ProcessBuilder(options);
        ProcessTools.executeCommand(pb)
                    .shouldHaveExitValue(0);
    }

    private Path findTool(Path image, String tool)  {
        String suffix = System.getProperty("os.name").startsWith("Windows")
                            ? ".exe" : "";

        Path cmd = image.resolve("bin").resolve(tool + suffix);
        if (Files.notExists(cmd)) {
            throw new RuntimeException(cmd + " not found");
        }
        return cmd;
    }

    private JlinkOutput createImage(Path image, List<String> args, boolean success) throws IOException {
        System.out.println("jlink " + args.stream().collect(Collectors.joining(" ")));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(baos);
        ByteArrayOutputStream berrOs = new ByteArrayOutputStream();
        PrintWriter err = new PrintWriter(berrOs);
        int rc = JLINK_TOOL.run(out, err, args.toArray(String[]::new));
        String stdOut = new String(baos.toByteArray());
        String stdErr = new String(berrOs.toByteArray());
        assertEquals(rc == 0, success, String.format("Output was: %nstdout: %s%nstderr: %s%n", stdOut, stdErr));
        return new JlinkOutput(stdErr, stdOut);
    }

    private static record JlinkOutput(String stderr, String stdout) {};
}
