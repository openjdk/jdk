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
 * @summary Test SystemModules handling of java --list-modules with system modules
 *          not consistently enabled/disabled.
 * @requires (vm.compMode != "Xcomp" & os.maxMemory >= 2g)
 * @library ../../lib /test/lib
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jimage
 * @build tests.* jdk.test.lib.process.OutputAnalyzer
 *        jdk.test.lib.process.ProcessTools
 * @run main/othervm -Xmx1g SystemModulesTest2
 */
public class SystemModulesTest2 extends AbstractJmodLessTest {

    public static void main(String[] args) throws Exception {
        SystemModulesTest2 test = new SystemModulesTest2();
        test.run();
    }

    /*
     * SystemModule classes are module specific and SystemModulesMap gets generated
     * for each link. This turns out to be a problem if we perform an initial
     * jmod-full link with system-modules plugin enabled, which in turn would
     * change the SystemModulesMap in the run time image that the final run-time
     * based link will then use to generate a link only using java.base. In that
     * case, we cannot use the fast path and we ought to use the slow path in
     * order to avoid CNFEs.
     */
    @Override
    void runTest(Helper helper) throws Exception {
        // Create an image with two modules, so that SystemModulesMap gets generated
        // for it as system-modules plugin is auto-enabled. Later, reduce the set
        // of modules to only java.base with system-modules plugin disabled.
        Path javaJmodless = createJavaImageJmodLess(new BaseJlinkSpecBuilder()
                                                            .helper(helper)
                                                            .name("jlink-jmodless-sysmod2")
                                                            .addModule("jdk.httpserver")
                                                            .addModule("jdk.jlink")
                                                            .validatingModule("java.base")
                                                            .addExtraOption("--unlock-run-image")
                                                            .build());
        // Verify that SystemModules$all.class is there
        JImageValidator.validate(javaJmodless.resolve("lib").resolve("modules"),
                                    List.of("/java.base/jdk/internal/module/SystemModules$all.class"), Collections.emptyList());
        // Attempt another run-time image link reducing modules to java.base only,
        // but we don't rewrite SystemModulesMap.
        Path finalResult = jlinkUsingImage(new JlinkSpecBuilder()
                                .helper(helper)
                                .imagePath(javaJmodless)
                                .name("java.base-from-sysmod2-derived")
                                .addModule("java.base")
                                .extraJlinkOpt("--disable-plugin")
                                .extraJlinkOpt("system-modules") // disable sysmods
                                .expectedLocation("/java.base/jdk/internal/module/SystemModulesMap.class")
                                .expectedLocation("/java.base/jdk/internal/module/SystemModules.class")
                                .unexpectedLocation("/java.base/jdk/internal/module/SystemModules$0.class")
                                .validatingModule("java.base")
                                .build());
        // Finally run --list-modules so as to verify it does not thow CNFE
        List<String> expectedModules = List.of("java.base");
        verifyListModules(finalResult, expectedModules);
    }

}
