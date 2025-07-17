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
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jimage
 * @build tests.* jdk.test.lib.process.OutputAnalyzer
 *        jdk.test.lib.process.ProcessTools
 * @run main/othervm -Xmx1g SystemModulesTest
 */
public class SystemModulesTest extends AbstractLinkableRuntimeTest {

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
    void runTest(Helper helper, boolean isLinkableRuntime) throws Exception {
        // create an image with a module containing a main entrypoint (jdk.httpserver),
        // thus producing the SystemModules$0.class. Add jdk.jdwp.agent as a module which
        // isn't resolved by default, so as to generate SystemModules$default.class
        BaseJlinkSpecBuilder builder = new BaseJlinkSpecBuilder()
                .helper(helper)
                .name("httpserver-jlink-jmodless-derived")
                .addModule("jdk.httpserver")
                .addModule("jdk.jdwp.agent")
                .validatingModule("java.base");
        if (isLinkableRuntime) {
            builder.setLinkableRuntime();
        }
        Path javaseJmodless = createJavaImageRuntimeLink(builder.build());
        // Verify that SystemModules$0.class etc. are there, due to httpserver and jdwp.agent
        JImageValidator.validate(javaseJmodless.resolve("lib").resolve("modules"),
                                    List.of("/java.base/jdk/internal/module/SystemModules$default.class",
                                            "/java.base/jdk/internal/module/SystemModules$0.class",
                                            "/java.base/jdk/internal/module/SystemModules$all.class"),
                                    Collections.emptyList());
    }

}
