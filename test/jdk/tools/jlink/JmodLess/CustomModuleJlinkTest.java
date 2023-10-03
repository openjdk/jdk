/*
 * Copyright (c) 2023, Red Hat, Inc.
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

import tests.Helper;


/*
 * @test
 * @summary Test jmod-less jlink with a custom module
 * @requires (vm.compMode != "Xcomp" & os.maxMemory >= 2g)
 * @library ../../lib /test/lib
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jimage
 * @build tests.* jdk.test.lib.process.OutputAnalyzer
 *        jdk.test.lib.process.ProcessTools
 * @run main/othervm -Xmx1g CustomModuleJlinkTest
 */
public class CustomModuleJlinkTest extends AbstractJmodLessTest {

    public static void main(String[] args) throws Exception {
        CustomModuleJlinkTest test = new CustomModuleJlinkTest();
        test.run();
    }

    @Override
    void runTest(Helper helper) throws Exception {
        String customModule = "leaf1";
        helper.generateDefaultJModule(customModule);

        // create a base image including jdk.jlink and the leaf1 module. This will
        // add the leaf1 module's module path.
        Path jlinkImage = createBaseJlinkImage(new BaseJlinkSpecBuilder()
                                                    .helper(helper)
                                                    .name("cmod-jlink")
                                                    .addModule(customModule)
                                                    .validatingModule("java.base") // not used
                                                    .build());

        // Now that the base image already includes the 'leaf1' module, it should
        // be possible to jlink it again, asking for *only* the 'leaf1' plugin even
        // though we won't have any jmods directories present.
        Path finalImage = jlinkUsingImage(new JlinkSpecBuilder()
                                                .imagePath(jlinkImage)
                                                .helper(helper)
                                                .name(customModule)
                                                .expectedLocation(String.format("/%s/%s/com/foo/bar/X.class", customModule, customModule))
                                                .addModule(customModule)
                                                .validatingModule(customModule)
                                                .build());
        // Expected only the transitive closure of "leaf1" module in the --list-modules
        // output of the java launcher.
        List<String> expectedModules = List.of("java.base", customModule);
        verifyListModules(finalImage, expectedModules);
    }

}
