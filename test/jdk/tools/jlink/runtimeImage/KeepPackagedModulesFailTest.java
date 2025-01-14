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

import java.nio.file.Path;
import java.util.function.Predicate;

import jdk.test.lib.process.OutputAnalyzer;
import tests.Helper;


/*
 * @test
 * @summary Verify that jlink with an empty module path, but trying to use
 *          --keep-packaged-modules fails as expected.
 * @requires (vm.compMode != "Xcomp" & os.maxMemory >= 2g)
 * @library ../../lib /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jimage
 * @build tests.* jdk.test.lib.process.OutputAnalyzer
 *        jdk.test.lib.process.ProcessTools
 * @run main/othervm -Xmx1g KeepPackagedModulesFailTest
 */
public class KeepPackagedModulesFailTest extends AbstractLinkableRuntimeTest {

    public static void main(String[] args) throws Exception {
        KeepPackagedModulesFailTest test = new KeepPackagedModulesFailTest();
        test.run();
    }

    @Override
    void runTest(Helper helper, boolean isLinkableRuntime) throws Exception {
        // create a base image for linking from the run-time image
        BaseJlinkSpecBuilder builder = new BaseJlinkSpecBuilder()
                .helper(helper)
                .name("jlink-fail")
                .addModule("java.base")
                .validatingModule("java.base");
        if (isLinkableRuntime) {
            builder.setLinkableRuntime();
        }
        Path baseImage = createRuntimeLinkImage(builder.build());

        CapturingHandler handler = new CapturingHandler();
        Predicate<OutputAnalyzer> exitFailPred = new Predicate<>() {

            @Override
            public boolean test(OutputAnalyzer t) {
                return t.getExitValue() != 0; // expect failure
            }
        };
        // Attempt a jlink using the run-time image and also using option
        // --keep-packaged-modules, which should fail.
        jlinkUsingImage(new JlinkSpecBuilder()
                                .helper(helper)
                                .imagePath(baseImage)
                                .name("java-base-jlink-keep-packaged-target")
                                .addModule("java.base")
                                .extraJlinkOpt("--keep-packaged-modules=foo")
                                .validatingModule("java.base")
                                .build(), handler, exitFailPred);
        OutputAnalyzer analyzer = handler.analyzer();
        if (analyzer.getExitValue() == 0) {
            throw new AssertionError("Expected jlink to have failed!");
        }
        analyzer.stdoutShouldContain("Error");
        analyzer.stdoutShouldContain("--keep-packaged-modules");
    }

}
