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

 /*
 * @test
 * @summary Test image creation
 * @author Jean-Francois Denise
 * @library ../lib
 * @modules java.base/jdk.internal.jimage
 *          jdk.jdeps/com.sun.tools.classfile
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jmod
 *          jdk.jlink/jdk.tools.jimage
 *          jdk.compiler
 * @build tests.*
 * @run main/othervm -verbose:gc -Xmx1g JLink2Test
 */
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Layer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import jdk.tools.jlink.internal.PluginRepository;
import jdk.tools.jlink.plugin.Plugin;

import tests.Helper;
import tests.JImageGenerator;
import tests.JImageValidator;

public class JLink2Test {

    public static void main(String[] args) throws Exception {
        Helper helper = Helper.newHelper();
        if (helper == null) {
            System.err.println("Test not run");
            return;
        }
        helper.generateDefaultModules();

        // This test case must be first one, the JlinkTask is clean
        // and reveals possible bug related to plugin options in defaults
        // e. g.: --genbom
        testBomFile(helper);
        testSameNames(helper);
        testModulePath(helper);
        testOptions();
    }

    private static void testModulePath(Helper helper) throws IOException {
        Path doesNotExist = helper.createNewImageDir("doesnotexist");
        Path jar = helper.getJarDir().resolve("bad.jar");
        JImageGenerator.getJLinkTask()
                .pluginModulePath(doesNotExist)
                .option("--help")
                .call().assertSuccess();
        Files.createFile(jar);
        JImageGenerator.getJLinkTask()
                .pluginModulePath(jar)
                .option("--help")
                .call().assertFailure("(\n|\r|.)*Error: Invalid modules in the plugins path: (\n|\r|.)*");
        JImageGenerator.getJLinkTask()
                .pluginModulePath(jar.getParent())
                .option("--help")
                .call().assertFailure("Error: Invalid modules in the plugins path: .*zip file is empty(\n|\r|.)*");
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar.toFile()))) {
            JarEntry entry = new JarEntry("class");
            out.putNextEntry(entry);
            out.write("AAAA".getBytes());
            out.closeEntry();
        }
        JImageGenerator.getJLinkTask()
                .pluginModulePath(jar.getParent())
                .output(helper.createNewImageDir("crash"))
                .addJmods(helper.getStdJmodsDir())
                .addJmods(jar.getParent())
                .addMods("bad")
                .call().assertFailure("(\n|\r|.)*Error: jdk.tools.jlink.plugin.PluginException: module-info.class not found for bad module(\n|\r|.)*");
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar.toFile()))) {
            JarEntry entry = new JarEntry("classes");
            out.putNextEntry(entry);
            out.closeEntry();

            entry = new JarEntry("classes/class");
            out.putNextEntry(entry);
            out.write("AAAA".getBytes());
            out.closeEntry();
        }
        JImageGenerator.getJLinkTask()
                .pluginModulePath(jar.getParent())
                .output(helper.createNewImageDir("bad"))
                .addJmods(jar.getParent())
                .addJars(helper.getStdJmodsDir())
                .addMods("bad")
                .call().assertFailure("(\n|\r|.)*Error: jdk.tools.jlink.plugin.PluginException: module-info.class not found for bad module(\n|\r|.)*");
    }

    private static void testSameNames(Helper helper) throws Exception {
        // Multiple modules with the same name in modulepath, take the first one in the path.
        // First jmods then jars. So jmods are found, jars are hidden.
        String[] jarClasses = {"amodule.jar.Main"};
        String[] jmodsClasses = {"amodule.jmods.Main"};
        helper.generateDefaultJarModule("amodule", Arrays.asList(jarClasses));
        helper.generateDefaultJModule("amodule", Arrays.asList(jmodsClasses));
        List<String> okLocations = new ArrayList<>();
        okLocations.addAll(Helper.toLocation("amodule", Arrays.asList(jmodsClasses)));
        Path image = helper.generateDefaultImage(new String[0], "amodule").assertSuccess();
        JImageValidator validator = new JImageValidator("amodule", okLocations,
                image.toFile(), Collections.emptyList(), Collections.emptyList());
        validator.validate();
    }

    private static void testBomFile(Helper helper) throws Exception {
        String[] userOptions = {
            "--compress",
            "2",
            "--addmods",
            "bomzip",
            "--strip-debug",
            "--genbom",
            "--exclude-resources",
            "*.jcov,*/META-INF/*"};
        String moduleName = "bomzip";
        helper.generateDefaultJModule(moduleName, "composite2");
        Path imgDir = helper.generateDefaultImage(userOptions, moduleName).assertSuccess();
        helper.checkImage(imgDir, moduleName, userOptions, null, null);
        File bom = new File(imgDir.toFile(), "bom");
        if (!bom.exists()) {
            throw new RuntimeException(bom.getAbsolutePath() + " not generated");
        }
        String bomcontent = new String(Files.readAllBytes(bom.toPath()));
        if (!bomcontent.contains("--strip-debug")
                || !bomcontent.contains("--compress")
                || !bomcontent.contains("--genbom")
                || !bomcontent.contains("--exclude-resources *.jcov,"
                        + "*/META-INF/*")
                || !bomcontent.contains("--addmods bomzip")) {
            throw new Exception("Not expected content in " + bom);
        }
    }

    private static void testOptions() throws Exception {
        List<Plugin> builtInPlugins = new ArrayList<>();
        builtInPlugins.addAll(PluginRepository.getPlugins(Layer.boot()));
        if(builtInPlugins.isEmpty()) {
            throw new Exception("No builtin plugins");
        }
        List<String> options = new ArrayList<>();
        for (Plugin p : builtInPlugins) {
            if (p.getOption() == null) {
                throw new Exception("Null option for " + p.getName());
            }
            if (options.contains(p.getName())) {
                throw new Exception("Option " + p.getOption() + " used more than once");
            }
            options.add(p.getName());
        }
    }
}
