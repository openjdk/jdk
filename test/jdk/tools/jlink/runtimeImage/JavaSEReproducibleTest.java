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

import java.nio.file.Files;
import java.nio.file.Path;

import tests.Helper;


/*
 * @test
 * @summary Test reproducibility of linking an java.se image using the run-time
 *          image.
 * @requires (vm.compMode != "Xcomp" & os.maxMemory >= 2g)
 * @library ../../lib /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jimage
 * @build tests.* jdk.test.lib.process.OutputAnalyzer
 *        jdk.test.lib.process.ProcessTools
 * @run main/othervm -Xmx1g JavaSEReproducibleTest
 */
public class JavaSEReproducibleTest extends AbstractLinkableRuntimeTest {

    public static void main(String[] args) throws Exception {
        JavaSEReproducibleTest test = new JavaSEReproducibleTest();
        test.run();
    }

    @Override
    void runTest(Helper helper, boolean isLinkableRuntime) throws Exception {
        String javaSeModule = "java.se";
        // create a java.se using jmod-less approach
        BaseJlinkSpecBuilder builder = new BaseJlinkSpecBuilder()
                .helper(helper)
                .addModule(javaSeModule)
                .validatingModule(javaSeModule);
        if (isLinkableRuntime) {
            builder.setLinkableRuntime();
        }
        builder.name("java-se-repro1");
        Path javaSEJmodLess1 = createJavaImageRuntimeLink(builder.build());

        // create another java.se version using jmod-less approach
        builder.name("java-se-repro2");
        Path javaSEJmodLess2 = createJavaImageRuntimeLink(builder.build());
        if (Files.mismatch(javaSEJmodLess1.resolve("lib").resolve("modules"),
                           javaSEJmodLess2.resolve("lib").resolve("modules")) != -1L) {
            throw new RuntimeException("jlink producing inconsistent result for " + javaSeModule + " (jmod-less)");
        }
    }

}
