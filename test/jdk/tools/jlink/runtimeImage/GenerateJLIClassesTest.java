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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import tests.Helper;

/*
 * @test
 * @summary Verify JLI class generation in run-time image link mode
 * @requires (vm.compMode != "Xcomp" & os.maxMemory >= 2g)
 * @library ../../lib /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jimage
 * @build tests.* jdk.test.lib.process.OutputAnalyzer
 *        jdk.test.lib.process.ProcessTools
 * @run main/othervm -Xmx1g GenerateJLIClassesTest
 */
public class GenerateJLIClassesTest extends AbstractLinkableRuntimeTest {

    public static void main(String[] args) throws Exception {
        GenerateJLIClassesTest test = new GenerateJLIClassesTest();
        test.run();
    }

    /*
     * java.lang.invoke.BoundMethodHandle$Species_* classes get generated
     * by the GenerateJLiClassesPlugin. This test ensures that potentially
     * generated JLI classes from the run-time image don't populate to the
     * target image in the run-time image based link mode.
     */
    @Override
    void runTest(Helper helper, boolean isLinkableRuntime) throws Exception {
        Path baseFile = Files.createTempFile("base", "trace");
        String species = "LLLLLLLLLLLLLLLLLLL";
        String fileString = "[SPECIES_RESOLVE] java.lang.invoke.BoundMethodHandle$Species_" + species + " (salvaged)\n";
        Files.write(baseFile, fileString.getBytes(StandardCharsets.UTF_8));
        BaseJlinkSpecBuilder builder = new BaseJlinkSpecBuilder()
                .helper(helper)
                .addModule("java.base")
                .name("jlink.jli-jmodless")
                .validatingModule("java.base");
        if (isLinkableRuntime) {
            builder.setLinkableRuntime();
        }

        Path runtimeLinkableImage = createRuntimeLinkImage(builder.build());
        // Finally attempt another jmodless link reducing modules to java.base only,
        // and asking for specific jli classes.
        jlinkUsingImage(new JlinkSpecBuilder()
                                .helper(helper)
                                .imagePath(runtimeLinkableImage)
                                .name("java.base-jli-derived")
                                .addModule("java.base")
                                .extraJlinkOpt("--generate-jli-classes=@" + baseFile.toString())
                                .expectedLocation("/java.base/java/lang/invoke/BoundMethodHandle$Species_" + species + ".class")
                                .expectedLocation("/java.base/java/lang/invoke/BoundMethodHandle$Species_L.class")
                                .unexpectedLocation("/java.base/java/lang/invoke/BoundMethodHandle$Species_" + species.substring(1) + ".class")
                                .unexpectedLocation("/java.base/java/lang/invoke/BoundMethodHandle$Species_LL.class")
                                .validatingModule("java.base")
                                .build());
    }

}
