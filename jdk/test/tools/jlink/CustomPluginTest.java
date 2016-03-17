/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import tests.Helper;
import tests.JImageGenerator;
import tests.Result;

/*
 * @test
 * @summary Test custom plugin
 * @author Jean-Francois Denise
 * @library ../lib
 * @modules java.base/jdk.internal.jimage
 *          jdk.jdeps/com.sun.tools.classfile
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jmod
 *          jdk.jlink/jdk.tools.jimage
 *          jdk.compiler
 * @build tests.*
 * @run main/othervm CustomPluginTest
 */

public class CustomPluginTest {

    public static void main(String[] args) throws Exception {
        new CustomPluginTest().test();
    }

    private void test() throws Exception {
        Helper helper = Helper.newHelper();
        if (helper == null) {
            System.err.println("Test not run");
            return;
        }
        helper.generateDefaultModules();
        Path jmod = registerServices(helper);
        Path pluginModulePath = jmod.getParent();

        testHelloProvider(helper, pluginModulePath);
        testCustomPlugins(helper, pluginModulePath);
    }

    private void testCustomPlugins(Helper helper, Path pluginModulePath) {
        Result result = JImageGenerator.getJLinkTask()
                .option("--list-plugins")
                .pluginModulePath(pluginModulePath)
                .output(helper.createNewImageDir("customplugin"))
                .call();
        if (result.getExitCode() != 0) {
            System.err.println(result.getMessage());
            throw new AssertionError("jlink crashed: " + result.getExitCode());
        }
        List<String> customPlugins = Stream.of(result.getMessage().split("\n"))
                .filter(s -> s.startsWith("Plugin Name:"))
                .filter(s -> s.contains("custom"))
                .collect(Collectors.toList());
        if (customPlugins.size() != 1) {
            System.err.println(result.getMessage());
            throw new AssertionError("Found plugins: " + customPlugins);
        }
    }

    private Path registerServices(Helper helper) throws IOException {
        String name = "customplugin";
        Path src = Paths.get(System.getProperty("test.src")).resolve(name);
        Path classes = helper.getJmodClassesDir().resolve(name);
        JImageGenerator.compile(src, classes, "-XaddExports:jdk.jlink/jdk.tools.jlink.internal=customplugin");
        return JImageGenerator.getJModTask()
                .addClassPath(classes)
                .jmod(helper.getJmodDir().resolve(name + ".jmod"))
                .create().assertSuccess();
    }

    private void testHelloProvider(Helper helper, Path pluginModulePath) throws IOException {
        Path pluginFile = Paths.get("customplugin.txt");
        if (Files.exists(pluginFile)) {
            throw new AssertionError("Custom plugin output file already exists");
        }
        String customplugin = "customplugin";
        {
            // Add the path but not the option, plugin musn't be called
            JImageGenerator.getJLinkTask()
                    .modulePath(helper.defaultModulePath())
                    .pluginModulePath(pluginModulePath)
                    .output(helper.createNewImageDir(customplugin))
                    .addMods(customplugin)
                    .call().assertSuccess();
        }

        if (Files.exists(pluginFile)) {
            throw new AssertionError("Custom plugin output file exists, plugin "
                    + " called although shouldn't have been");
        }

        { // Add the path and the option, plugin should be called.
            JImageGenerator.getJLinkTask()
                    .modulePath(helper.defaultModulePath())
                    .addMods(customplugin)
                    .pluginModulePath(pluginModulePath)
                    .output(helper.createNewImageDir(customplugin))
                    .option("--hello")
                    .call().assertSuccess();
        }

        if (!Files.exists(pluginFile)) {
            throw new AssertionError("Custom plugin not called");
        }
    }
}
