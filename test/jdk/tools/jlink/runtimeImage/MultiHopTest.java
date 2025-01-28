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
 * @summary Verify that a jlink using the run-time image cannot include jdk.jlink
 * @requires (vm.compMode != "Xcomp" & os.maxMemory >= 2g)
 * @library ../../lib /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jimage
 * @build tests.* jdk.test.lib.process.OutputAnalyzer
 *        jdk.test.lib.process.ProcessTools
 * @run main/othervm -Xmx1g MultiHopTest
 */
public class MultiHopTest extends AbstractLinkableRuntimeTest {

    @Override
    void runTest(Helper helper, boolean isLinkableRuntime) throws Exception {
        Path jdkJlinkJmodless = createJDKJlinkJmodLess(helper, "jdk.jlink-multi-hop1", isLinkableRuntime);
        CapturingHandler handler = new CapturingHandler();
        Predicate<OutputAnalyzer> exitFailPred = new Predicate<>() {

            @Override
            public boolean test(OutputAnalyzer a) {
                return a.getExitValue() != 0; // expect failure
            }
        };
        jlinkUsingImage(new JlinkSpecBuilder()
                                .helper(helper)
                                .imagePath(jdkJlinkJmodless)
                                .name("jdk-jlink-multi-hop1-target")
                                .addModule("jdk.jlink")
                                .validatingModule("java.base")
                                .build(), handler, exitFailPred);
        OutputAnalyzer analyzer = handler.analyzer();
        if (analyzer.getExitValue() == 0) {
            throw new AssertionError("Expected jlink to fail due to including jdk.jlink");
        }
        String expectedMsg = "This JDK does not contain packaged modules " +
                             "and cannot be used to create another image with " +
                             "the jdk.jlink module";
        analyzer.stdoutShouldContain(expectedMsg);
        analyzer.stdoutShouldNotContain("Exception"); // ensure error message is sane
    }

    private Path createJDKJlinkJmodLess(Helper helper, String name, boolean isLinkableRuntime) throws Exception {
        BaseJlinkSpecBuilder builder = new BaseJlinkSpecBuilder();
        builder.helper(helper)
               .name(name)
               .addModule("jdk.jlink")
               .validatingModule("java.base");
        if (isLinkableRuntime) {
            builder.setLinkableRuntime();
        }
        return createRuntimeLinkImage(builder.build());
    }

    public static void main(String[] args) throws Exception {
        MultiHopTest test = new MultiHopTest();
        test.run();
    }

}
