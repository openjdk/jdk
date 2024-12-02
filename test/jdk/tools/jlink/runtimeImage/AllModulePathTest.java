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

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Path;
import java.util.List;

import tests.Helper;
import tests.Result;


/*
 * @test id=default
 * @summary Test basic linking from the run-time image using ALL-MODULE-PATH
 * @requires (vm.compMode != "Xcomp" & os.maxMemory >= 2g)
 * @library ../../lib /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jimage
 * @build tests.* jdk.test.lib.process.OutputAnalyzer
 *        jdk.test.lib.process.ProcessTools
 * @run main/othervm -Xmx1400m AllModulePathTest true
 */

/*
 * @test id=with-module-path
 * @summary Test basic linking from the run-time image using ALL-MODULE-PATH and
 *          a specifically set module path
 * @requires (vm.compMode != "Xcomp" & os.maxMemory >= 2g)
 * @library ../../lib /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jimage
 * @build tests.* jdk.test.lib.process.OutputAnalyzer
 *        jdk.test.lib.process.ProcessTools
 * @run main/othervm -Xmx1400m AllModulePathTest false
 */
public class AllModulePathTest extends AbstractLinkableRuntimeTest {

    private static boolean IS_DEFAULT_TEST = true;

    @Override
    public void runTest(Helper helper, boolean isLinkableRuntime) throws Exception {
        if (IS_DEFAULT_TEST) {
            defaultTest(helper, isLinkableRuntime);
        } else {
            allModulePathNonDefault(helper, isLinkableRuntime);
        }
    }

    /**
     * Tests linking from the run-time image with {@code --add-modules ALL-MODULE-PATH}
     * but no module path otherwise given. This needs to special case the restriction
     * of the jdk.jlink module being disallowed from being on the list of included
     * modules in the output image.
     *
     * @param helper
     * @param isLinkableRuntime
     * @throws Exception
     */
    private void defaultTest(Helper helper, boolean isLinkableRuntime) throws Exception {
        Path finalImage = createAllModulesRuntimeLink(helper,
                                                      "all-module-path-default",
                                                      isLinkableRuntime,
                                                      List.of() /* no custom args */);
        List<String> expected = ModuleFinder.ofSystem()
                                            .findAll()
                                            .stream()
                                            .map(ModuleReference::descriptor)
                                            // linking from run-time image doesn't yet
                                            // allow jdk.jlink, or any module that
                                            // depends on it.
                                            .filter(a -> {
                                                return !("jdk.jlink".equals(a.name()) ||
                                                         a.requires().stream()
                                                          .anyMatch(r -> "jdk.jlink".equals(r.name())));
                                                })
                                            .map(ModuleDescriptor::name)
                                            .sorted()
                                            .toList();
        verifyListModules(finalImage, expected);
    }

    /**
     * Tests linking from the run-time image with {@code --add-modules ALL-MODULE-PATH}
     * with a module path including only a single module.
     *
     * @param helper
     * @param isLinkableRuntime
     * @throws Exception
     */
    private void allModulePathNonDefault(Helper helper, boolean isLinkableRuntime) throws Exception {
        String moduleName = "com.foo.runtime";
        Result result = helper.generateDefaultJModule(moduleName, "jdk.jfr");
        Path customModulePath = result.getFile().getParent();
        List<String> extraArgs = List.of("--module-path", customModulePath.toString());
        Path finalImage = createAllModulesRuntimeLink(helper,
                                                      "all-module-path-cust-mod",
                                                      isLinkableRuntime,
                                                      extraArgs);
        List<String> expected = List.of("com.foo.runtime", "java.base", "jdk.jfr");
        verifyListModules(finalImage, expected);
    }

    private Path createAllModulesRuntimeLink(Helper helper,
                                             String name,
                                             boolean isLinkableRuntime,
                                             List<String> extraArgs) throws Exception {
        BaseJlinkSpecBuilder builder = new BaseJlinkSpecBuilder();
        builder.helper(helper)
               .name(name)
               .addModule("ALL-MODULE-PATH")
               .validatingModule("java.base");
        for (String arg: extraArgs) {
            builder.addExtraOption(arg);
        }
        if (isLinkableRuntime) {
            builder.setLinkableRuntime();
        }
        return createJavaImageRuntimeLink(builder.build());
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            AllModulePathTest.IS_DEFAULT_TEST = Boolean.parseBoolean(args[0]);
        } else {
            throw new AssertionError("Illegal number of arguments: " + args);
        }
        AllModulePathTest test = new AllModulePathTest();
        test.run();
    }

}
