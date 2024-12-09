/*
 * Copyright (c) 2024, Red Hat, Inc.
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

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.tools.jlink.internal.LinkableRuntimeImage;
import tests.Helper;
import tests.Result;

/*
 * @test
 * @summary Test ALL-MODULE-PATH option
 * @bug 8345259 8345573
 * @requires (vm.compMode != "Xcomp" & os.maxMemory >= 2g)
 * @library ../lib /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jimage
 * @run main/othervm -Xmx1g AllModulePathTest
 */
public class AllModulePathTest {
    private static final ToolProvider JLINK_TOOL = ToolProvider.findFirst("jlink")
        .orElseThrow(() ->
            new RuntimeException("jlink tool not found")
        );

    private final Helper helper;

    public AllModulePathTest(Helper helper) {
        this.helper = helper;
    }

    private void noModulePath() {
        Path targetPath = helper.createNewImageDir("all-mod-path-no-mod-path");
        List<String> allArgs = List.of("--add-modules", "ALL-MODULE-PATH",
                                       "--output", targetPath.toString());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(baos);
        ByteArrayOutputStream berrOs = new ByteArrayOutputStream();
        PrintWriter err = new PrintWriter(berrOs);
        JLINK_TOOL.run(out, err, allArgs.toArray(new String[] {}));
        OutputAnalyzer analyzer = new OutputAnalyzer(new String(baos.toByteArray(), StandardCharsets.UTF_8),
                                                     new String(berrOs.toByteArray(), StandardCharsets.UTF_8));
        analyzer.stdoutShouldContain("Error");
        analyzer.stdoutShouldContain("ALL-MODULE-PATH requires --module-path option");
    }

    private void modulePathWithLimitMods() throws Exception {
        Path targetPath = helper.createNewImageDir("all-mods-limit-mods");
        String moduleName = "com.baz.runtime";
        Result result = helper.generateDefaultJModule(moduleName, "jdk.jfr");
        Path customModulePath = result.getFile().getParent();
        List<String> allArgs = List.of("--add-modules", "ALL-MODULE-PATH",
                                       "--limit-modules", "jdk.jfr", // A dependency of com.baz.runtime
                                       "--module-path", customModulePath.toString(),
                                       "--output", targetPath.toString());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(baos);
        ByteArrayOutputStream berrOs = new ByteArrayOutputStream();
        PrintWriter err = new PrintWriter(berrOs);
        JLINK_TOOL.run(out, err, allArgs.toArray(new String[] {}));
        OutputAnalyzer analyzer = new OutputAnalyzer(new String(baos.toByteArray(), StandardCharsets.UTF_8),
                                                     new String(berrOs.toByteArray(), StandardCharsets.UTF_8));
        analyzer.shouldBeEmpty();
        List<String> expected = List.of("java.base", "jdk.jfr");
        verifyListModules(targetPath, expected);
    }

    private void modulePath() throws Exception {
        Path targetPath = helper.createNewImageDir("all-mod-path-w-mod-path");
        String moduleName = "com.foo.runtime";
        Result result = helper.generateDefaultJModule(moduleName, "jdk.jfr");
        Path customModulePath = result.getFile().getParent();
        List<String> allArgs = List.of("--add-modules", "ALL-MODULE-PATH",
                                       "--module-path", customModulePath.toString(),
                                       "--output", targetPath.toString(),
                                       "--verbose");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(baos);
        ByteArrayOutputStream berrOs = new ByteArrayOutputStream();
        PrintWriter err = new PrintWriter(berrOs);
        JLINK_TOOL.run(out, err, allArgs.toArray(new String[] {}));
        OutputAnalyzer analyzer = new OutputAnalyzer(new String(baos.toByteArray(), StandardCharsets.UTF_8),
                                                     new String(berrOs.toByteArray(), StandardCharsets.UTF_8));
        analyzer.stderrShouldBeEmpty();
        analyzer.stdoutShouldContain(moduleName);
        analyzer.stdoutShouldContain("java.base");
        analyzer.stdoutShouldContain("jdk.jfr");
        // Verify the output image's modules
        List<String> expected = List.of(moduleName, "java.base", "jdk.jfr");
        verifyListModules(targetPath, expected);
    }

    private void verifyListModules(Path targetPath, List<String> expected) throws Exception {
        String jlink = "java" + (Platform.isWindows() ? ".exe" : "");
        Path javaExe = targetPath.resolve(Path.of("bin"), Path.of(jlink));
        List<String> listMods = List.of(javaExe.toString(), "--list-modules");
        OutputAnalyzer out = ProcessTools.executeCommand(listMods.toArray(new String[] {}));
        if (out.getExitValue() != 0) {
            throw new AssertionError("java --list-modules failed");
        }
        List<String> actual = Stream.of(out.getStdout().split(Pattern.quote(System.lineSeparator())))
                                    .map(s -> { return s.split("@")[0]; })
                                    .sorted()
                                    .toList();
        if (!expected.equals(actual)) {
            throw new RuntimeException("Unexpected list of modules: " + actual + " expected: " + expected);
        }
    }

    public static void main(String[] args) throws Exception {
        boolean linkableRuntime = LinkableRuntimeImage.isLinkableRuntime();
        Helper helper = Helper.newHelper(linkableRuntime);
        if (helper == null) {
            System.err.println("Test not run");
            return;
        }
        AllModulePathTest test = new AllModulePathTest(helper);
        test.noModulePath();
        test.modulePath();
        test.modulePathWithLimitMods();
    }
}
