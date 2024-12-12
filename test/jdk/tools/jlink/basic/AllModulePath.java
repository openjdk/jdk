/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.tools.jlink.internal.LinkableRuntimeImage;
import tests.Helper;
import tests.Result;

/*
 * @test
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

    @Test
    public void testAllModulePath() throws Throwable {
        if (isExplodedJDKImage()) {
            return;
        }

        // create custom image
        Path image = Paths.get("image");
        createImage(image, "--add-modules", "ALL-MODULE-PATH");

        Set<String> modules = new HashSet<>();
        if (JMODS_EXIST) {
            Files.find(JMODS, 1, (Path p, BasicFileAttributes attr) ->
                                p.toString().endsWith(".jmod"))
                 .map(p -> JMODS.relativize(p).toString())
                 .map(n -> n.substring(0, n.length()-5))
                 .forEach(modules::add);
        } else {
            // java.base is a dependency of external modules
            modules.add("java.base");
        }
        modules.add("m1");
        modules.add("test");
        checkModules(image, modules);
    }

    @Test
    public void testLimitModules() throws Throwable {
        if (isExplodedJDKImage()) {
            return;
        }

        // create custom image
        Path image = Paths.get("image1");
        createImage(image,
                    "--add-modules", "ALL-MODULE-PATH",
                    "--limit-modules", "m1");

        checkModules(image, Set.of("m1", "java.base"));
    }

    @Test
    public void testAddModules() throws Throwable {
        if (isExplodedJDKImage()) {
            return;
        }

        // create custom image
        Path image = Paths.get("image2");
        createImage(image,
                    "--add-modules", "m1,test",
                    "--add-modules", "ALL-MODULE-PATH",
                    "--limit-modules", "java.base");

        checkModules(image, Set.of("m1", "test", "java.base"));
    }

    /*
     * No --module-path with --add-modules ALL-MODULE-PATH is an error.
     */
    @Test
    public void noModulePath() {
        if (isExplodedJDKImage()) {
            return;
        }
        Path targetPath = HELPER.createNewImageDir("all-mod-path-no-mod-path");
        List<String> allArgs = List.of("--add-modules", "ALL-MODULE-PATH",
                                       "--output", targetPath.toString());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(baos);
        ByteArrayOutputStream berrOs = new ByteArrayOutputStream();
        PrintWriter err = new PrintWriter(berrOs);
        int rc = JLINK_TOOL.run(out, err, allArgs.toArray(new String[] {}));
        assertTrue(rc != 0);
        String actual = new String(baos.toByteArray()).trim();
        assertEquals(actual, "Error: --module-path option must be specified with --add-modules ALL-MODULE-PATH");
    }

    /*
     * --module-path not-exist and --add-modules ALL-MODULE-PATH is an error.
     */
    @Test
    public void modulePathEmpty() {
        if (isExplodedJDKImage()) {
            return;
        }
        Path targetPath = HELPER.createNewImageDir("all-mod-path-not-existing");
        String strNotExists = "not-exist";
        Path notExists = Path.of(strNotExists);
        if (Files.exists(notExists)) {
            throw new RuntimeException("Test setup error, path must not exist!");
        }
        List<String> allArgs = List.of("--add-modules", "ALL-MODULE-PATH",
                                       "--module-path", notExists.toString(),
                                       "--output", targetPath.toString());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(baos);
        ByteArrayOutputStream berrOs = new ByteArrayOutputStream();
        PrintWriter err = new PrintWriter(berrOs);
        int rc = JLINK_TOOL.run(out, err, allArgs.toArray(new String[] {}));
        assertTrue(rc != 0);
        String actual = new String(baos.toByteArray()).trim();
        assertTrue(actual.startsWith("Error: No module found in module path"));
        assertTrue(actual.contains(strNotExists));
    }

    /*
     * --add-modules ALL-MODULE-PATH with an existing module path and module
     * limits applied.
     */
    @Test
    public void modulePathWithLimitMods() throws Exception {
        if (isExplodedJDKImage()) {
            return;
        }
        Path targetPath = HELPER.createNewImageDir("all-mods-limit-mods");
        String moduleName = "com.baz.runtime";
        Result result = HELPER.generateDefaultJModule(moduleName, "jdk.jfr");
        Path customModulePath = result.getFile().getParent();
        List<String> allArgs = List.of("--add-modules", "ALL-MODULE-PATH",
                                       "--limit-modules", "jdk.jfr", // A dependency of com.baz.runtime
                                       "--module-path", customModulePath.toString(),
                                       "--output", targetPath.toString());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(baos);
        ByteArrayOutputStream berrOs = new ByteArrayOutputStream();
        PrintWriter err = new PrintWriter(berrOs);
        int rc = JLINK_TOOL.run(out, err, allArgs.toArray(new String[] {}));
        assertTrue(rc == 0);
        String stdOut = new String(baos.toByteArray());
        String stdErr = new String(berrOs.toByteArray());
        assertTrue(stdOut.isEmpty());
        assertTrue(stdErr.isEmpty());
        List<String> expected = List.of("java.base", "jdk.jfr");
        verifyListModules(targetPath, expected);
    }

    /*
     * --add-modules ALL-MODULE-PATH with an existing module-path.
     */
    @Test
    public void modulePath() throws Exception {
        if (isExplodedJDKImage()) {
            return;
        }
        Path targetPath = HELPER.createNewImageDir("all-mod-path-w-mod-path");
        String moduleName = "com.foo.runtime";
        Result result = HELPER.generateDefaultJModule(moduleName, "jdk.jfr");
        Path customModulePath = result.getFile().getParent();
        List<String> allArgs = List.of("--add-modules", "ALL-MODULE-PATH",
                                       "--module-path", customModulePath.toString(),
                                       "--output", targetPath.toString(),
                                       "--verbose");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(baos);
        ByteArrayOutputStream berrOs = new ByteArrayOutputStream();
        PrintWriter err = new PrintWriter(berrOs);
        int rc = JLINK_TOOL.run(out, err, allArgs.toArray(new String[] {}));
        assertTrue(rc == 0);
        String stdOut = new String(baos.toByteArray());
        String stdErr = new String(berrOs.toByteArray());
        assertTrue(stdErr.isEmpty());
        assertTrue(stdOut.contains(moduleName));
        assertTrue(stdOut.contains("java.base"));
        assertTrue(stdOut.contains("jdk.jfr"));
        // Verify the output image's modules
        List<String> expected = List.of(moduleName, "java.base", "jdk.jfr");
        verifyListModules(targetPath, expected);
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

    /*
     * Verify linked modules using java --list-modules
     */
    private void verifyListModules(Path targetPath, List<String> expected) throws Exception {
        Path java = findTool(targetPath, "java");
        List<String> listMods = List.of(java.toString(), "--list-modules");
        OutputAnalyzer out = ProcessTools.executeCommand(listMods.toArray(new String[] {}));
        if (out.getExitValue() != 0) {
            throw new AssertionError("java --list-modules failed");
        }
        List<String> actual = Stream.of(out.getStdout().split(Pattern.quote(System.lineSeparator())))
                                    .map(s -> { return s.split("@")[0]; })
                                    .sorted()
                                    .toList();
        assertEquals(actual, expected);
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

    private void createImage(Path image, String... options) throws IOException {
        String modulepath = (JMODS_EXIST ? JMODS.toString() + File.pathSeparator : "")
                                + MODS.toString();
        List<String> opts = List.of("--module-path", modulepath,
                                    "--output", image.toString());
        String[] args = Stream.concat(opts.stream(), Arrays.stream(options))
                              .toArray(String[]::new);

        System.out.println("jlink " + Arrays.stream(args).collect(Collectors.joining(" ")));
        PrintWriter pw = new PrintWriter(System.out);
        int rc = JLINK_TOOL.run(pw, pw, args);
        assertTrue(rc == 0);
    }
}
