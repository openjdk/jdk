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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.spi.ToolProvider;

import jdk.test.lib.process.OutputAnalyzer;
import tests.Helper;


/*
 * @test
 * @summary Test run-time link with --patch-module. Expect failure.
 * @requires (vm.compMode != "Xcomp" & os.maxMemory >= 2g)
 * @library ../../lib /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jimage
 * @build tests.* jdk.test.lib.process.OutputAnalyzer
 *        jdk.test.lib.process.ProcessTools
 * @run main/othervm -Xmx1400m PatchedJDKModuleJlinkTest
 */
public class PatchedJDKModuleJlinkTest extends AbstractLinkableRuntimeTest {

    @Override
    public void runTest(Helper helper, boolean isLinkableRuntime) throws Exception {
        String imageName = "java-base-patched";
        Path runtimeLinkImage = createRuntimeLinkImage(helper, imageName + "-base", isLinkableRuntime);

        // Prepare patched module content
        Path patchSource = Path.of("java-base-patch-src");
        Path pkg = patchSource.resolve("java", "lang");
        Path extraClass = pkg.resolve("MyJlinkPatchInteger.java");
        String source = """
                package java.lang;
                public class MyJlinkPatchInteger {
                    public int add(int a, int b) {
                        return a + b;
                    }
                }
                """;
        Files.createDirectories(pkg);
        Files.writeString(extraClass, source);
        Path patchClasses = Path.of("java-base-patch-classes");
        Files.createDirectories(patchClasses);
        ToolProvider javac = ToolProvider.findFirst("javac")
                                         .orElseThrow(() -> new AssertionError("javac not found"));
        javac.run(System.out, System.err, new String[] {
                "-d", patchClasses.toString(),
                "--patch-module=java.base=" + patchSource.toAbsolutePath().toString(),
                extraClass.toAbsolutePath().toString()
        });

        // Perform a run-time image link expecting a failure
        CapturingHandler handler = new CapturingHandler();
        Predicate<OutputAnalyzer> exitFailPred = new Predicate<>() {

            @Override
            public boolean test(OutputAnalyzer t) {
                return t.getExitValue() != 0; // expect failure
            }
        };
        jlinkUsingImage(new JlinkSpecBuilder()
                                .helper(helper)
                                .imagePath(runtimeLinkImage)
                                .name(imageName + "-derived")
                                .addModule("java.base")
                                .validatingModule("java.base")
                                .extraJlinkOpt("-J--patch-module=java.base=" +
                                               patchClasses.toAbsolutePath().toString())
                                .build(), handler, exitFailPred);
        OutputAnalyzer analyzer = handler.analyzer();
        if (analyzer.getExitValue() == 0) {
            throw new AssertionError("Expected jlink to fail due to patched module!");
        }
        analyzer.stdoutShouldContain("jlink does not support linking from the run-time image");
        analyzer.stdoutShouldContain(" when running on a patched runtime with --patch-module");
        // Verify the error message is reasonable
        analyzer.stdoutShouldNotContain("IOException");
        analyzer.stdoutShouldNotContain("java.lang.IllegalArgumentException");
    }

    private Path createRuntimeLinkImage(Helper helper, String name, boolean isLinkableRuntime) throws Exception {
        BaseJlinkSpecBuilder builder = new BaseJlinkSpecBuilder()
                .name(name)
                .addModule("java.base")
                .validatingModule("java.base")
                .helper(helper);
        if (isLinkableRuntime) {
            builder.setLinkableRuntime();
        }
        return createRuntimeLinkImage(builder.build());
    }

    public static void main(String[] args) throws Exception {
        PatchedJDKModuleJlinkTest test = new PatchedJDKModuleJlinkTest();
        test.run();
    }

}
