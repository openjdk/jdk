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
import java.util.Collections;
import java.util.List;

import tests.Helper;
import tests.JImageValidator;

/*
 * @test
 * @summary Test appropriate handling of generated SystemModules* classes in run-time image link mode
 * @requires (vm.compMode != "Xcomp" & os.maxMemory >= 2g)
 * @library ../../lib /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jimage
 * @build tests.* jdk.test.lib.process.OutputAnalyzer
 *        jdk.test.lib.process.ProcessTools
 * @run main/othervm -Xmx1g SystemModulesTest
 */
public class SystemModulesTest extends AbstractJmodLessTest {

    public static void main(String[] args) throws Exception {
        SystemModulesTest test = new SystemModulesTest();
        test.run();
    }

    /*
     * SystemModule classes are module specific. If the jlink is based on the
     * modules image, then earlier generated SystemModule classes shall not get
     * propagated.
     */
    @Override
    void runTest(Helper helper) throws Exception {
        // create an image with a module containing a main entrypoint (jdk.httpserver),
        // thus producing the SystemModules$0.class. Add jdk.jdwp.agent as a module which
        // isn't resolved by default, so as to generate SystemModules$default.class
        Path javaseJmodless = createJavaImageJmodLess(new BaseJlinkSpecBuilder()
                                                            .helper(helper)
                                                            .name("httpserver-jlink-jmodless-derived")
                                                            .addModule("jdk.httpserver")
                                                            .addModule("jdk.jdwp.agent")
                                                            .addModule("jdk.jlink")
                                                            .validatingModule("java.base")
                                                            .addExtraOption("--exclude-resources")
                                                            .addExtraOption(EXCLUDE_RESOURCE_GLOB_STAMP)
                                                            .build());
        // Verify that SystemModules$0.class etc. are there
        JImageValidator.validate(javaseJmodless.resolve("lib").resolve("modules"),
                                    List.of("/java.base/jdk/internal/module/SystemModules$default.class",
                                            "/java.base/jdk/internal/module/SystemModules$0.class"), Collections.emptyList());
        // Finally attempt another jmodless link reducing modules to java.base only,
        // no longer expecting SystemModules$0.class
        jlinkUsingImage(new JlinkSpecBuilder()
                                .helper(helper)
                                .imagePath(javaseJmodless)
                                .name("java.base-from-jdk-httpserver-derived")
                                .addModule("java.base")
                                .expectedLocation("/java.base/jdk/internal/module/SystemModulesMap.class")
                                .expectedLocation("/java.base/jdk/internal/module/SystemModules.class")
                                .expectedLocation("/java.base/jdk/internal/module/SystemModules$all.class")
                                .unexpectedLocation("/java.base/jdk/internal/module/SystemModules$0.class")
                                .unexpectedLocation("/java.base/jdk/internal/module/SystemModules$default.class")
                                .validatingModule("java.base")
                                .build());
    }

}
