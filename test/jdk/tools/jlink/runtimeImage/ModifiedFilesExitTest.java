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
 * @summary Verify jlink fails by default when linking from the run-time image
 *          and files have been modified
 * @requires (vm.compMode != "Xcomp" & os.maxMemory >= 2g)
 * @library ../../lib /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jimage
 * @build tests.* jdk.test.lib.process.OutputAnalyzer
 *        jdk.test.lib.process.ProcessTools
 * @run main/othervm -Xmx1g ModifiedFilesExitTest
 */
public class ModifiedFilesExitTest extends ModifiedFilesTest {

    public static void main(String[] args) throws Exception {
        ModifiedFilesExitTest test = new ModifiedFilesExitTest();
        test.run();
    }

    @Override
    String initialImageName() {
        return "java-base-jlink-with-mod-exit";
    }

    @Override
    void testAndAssert(Path modifiedFile, Helper helper, Path initialImage)
            throws Exception {
        CapturingHandler handler = new CapturingHandler();
        Predicate<OutputAnalyzer> exitFailPred = new Predicate<>() {

            @Override
            public boolean test(OutputAnalyzer t) {
                return t.getExitValue() != 0; // expect failure
            }
        };
        jlinkUsingImage(new JlinkSpecBuilder()
                                .helper(helper)
                                .imagePath(initialImage)
                                .name("java-base-jlink-with-mod-exit-target")
                                .addModule("java.base")
                                .validatingModule("java.base")
                                .build(), handler, exitFailPred);
        OutputAnalyzer analyzer = handler.analyzer();
        if (analyzer.getExitValue() == 0) {
            throw new AssertionError("Expected jlink to fail due to modified file!");
        }
        analyzer.stdoutShouldContain(modifiedFile.toString() + " has been modified");
        // Verify the error message is reasonable
        analyzer.stdoutShouldNotContain("jdk.tools.jlink.internal.RunImageLinkException");
        analyzer.stdoutShouldNotContain("java.lang.IllegalArgumentException");
    }

}
