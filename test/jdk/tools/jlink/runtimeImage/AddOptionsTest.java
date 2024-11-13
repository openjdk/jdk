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
import java.util.List;
import java.util.Scanner;

import jdk.test.lib.process.OutputAnalyzer;
import tests.Helper;

/*
 * @test
 * @summary Test --add-options jlink plugin when linking from the run-time image
 * @requires (vm.compMode != "Xcomp" & os.maxMemory >= 2g)
 * @library ../../lib /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jimage
 * @build tests.* jdk.test.lib.process.OutputAnalyzer
 *        jdk.test.lib.process.ProcessTools
 * @run main/othervm -Xmx1g AddOptionsTest
 */
public class AddOptionsTest extends AbstractLinkableRuntimeTest {

    public static void main(String[] args) throws Exception {
        AddOptionsTest test = new AddOptionsTest();
        test.run();
    }

    @Override
    void runTest(Helper helper, boolean isLinkableRuntime) throws Exception {
        BaseJlinkSpecBuilder builder = new BaseJlinkSpecBuilder()
                .addExtraOption("--add-options")
                .addExtraOption("-Xlog:gc=info:stderr -XX:+UseParallelGC")
                .name("java-base-with-opts")
                .addModule("java.base")
                .validatingModule("java.base")
                .helper(helper);
        if (isLinkableRuntime) {
            builder.setLinkableRuntime();
        }
        Path finalImage = createJavaImageRuntimeLink(builder.build());
        verifyListModules(finalImage, List.of("java.base"));
        verifyParallelGCInUse(finalImage);
    }

    private void verifyParallelGCInUse(Path finalImage) throws Exception {
        OutputAnalyzer analyzer = runJavaCmd(finalImage, List.of("--version"));
        boolean foundMatch = false;
        try (Scanner lineScan = new Scanner(analyzer.getStderr())) {
            while (lineScan.hasNextLine()) {
                String line = lineScan.nextLine();
                if (line.endsWith("Using Parallel")) {
                    foundMatch = true;
                    break;
                }
            }
        }
        if (!foundMatch) {
            throw new AssertionError("Expected Parallel GC in place for jlinked image");
        }
    }

}
