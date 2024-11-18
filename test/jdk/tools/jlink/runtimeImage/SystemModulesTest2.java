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
 * @summary Test disabled SystemModulesPlugin in run-time image link mode. Expect
 *          generated classes to not be there.
 * @requires (vm.compMode != "Xcomp" & os.maxMemory >= 2g)
 * @library ../../lib /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jimage
 * @build tests.* jdk.test.lib.process.OutputAnalyzer
 *        jdk.test.lib.process.ProcessTools
 * @run main/othervm -Xmx1400m SystemModulesTest2
 */
public class SystemModulesTest2 extends AbstractLinkableRuntimeTest {

    public static void main(String[] args) throws Exception {
        SystemModulesTest2 test = new SystemModulesTest2();
        test.run();
    }

    @Override
    void runTest(Helper helper, boolean isLinkableRuntime) throws Exception {
        // See SystemModulesTest which enables the system-modules plugin. With
        // it disabled, we expect for the generated classes to not be there.
        BaseJlinkSpecBuilder builder = new BaseJlinkSpecBuilder()
                .helper(helper)
                .name("jlink-jmodless-sysmod2")
                .addModule("jdk.httpserver")
                .validatingModule("java.base")
                .addExtraOption("--disable-plugin")
                .addExtraOption("system-modules");
        if (isLinkableRuntime) {
            builder.setLinkableRuntime();
        }
        Path runtimeImageLinkTarget = createJavaImageRuntimeLink(builder.build());
        JImageValidator.validate(runtimeImageLinkTarget.resolve("lib").resolve("modules"),
                                    Collections.emptyList(),
                                    List.of("/java.base/jdk/internal/module/SystemModules$all.class",
                                            "/java.base/jdk/internal/module/SystemModules$default.class",
                                            "/java.base/jdk/internal/module/SystemModules$0.class"));
    }

}
