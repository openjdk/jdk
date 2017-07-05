/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.util.stream.Collectors;

import jdk.tools.jlink.internal.plugins.GenerateJLIClassesPlugin;

import tests.Helper;
import tests.JImageGenerator;
import tests.JImageValidator;
import tests.Result;

 /*
 * @test
 * @library ../../lib
 * @summary Test --generate-jli-classes plugin
 * @modules java.base/jdk.internal.jimage
 *          jdk.jdeps/com.sun.tools.classfile
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.internal.plugins
 *          jdk.jlink/jdk.tools.jmod
 *          jdk.jlink/jdk.tools.jimage
 * @build tests.*
 * @run main/othervm GenerateJLIClassesPluginTest
 */
public class GenerateJLIClassesPluginTest {

    private static Helper helper;

    public static void main(String[] args) throws Exception {
        helper = Helper.newHelper();
        if (helper == null) {
            System.err.println("Test not run");
            return;
        }

        helper.generateDefaultModules();


        // Test that generate-jli is enabled by default
        Result result = JImageGenerator.getJLinkTask()
                .modulePath(helper.defaultModulePath())
                .output(helper.createNewImageDir("generate-jli"))
                .addMods("java.base")
                .call();

        Path image = result.assertSuccess();

        JImageValidator.validate(
            image.resolve("lib").resolve("modules"),
                    classFilesForSpecies(GenerateJLIClassesPlugin.defaultSpecies()),
                    List.of());


        // Test a valid set of options
        result = JImageGenerator.getJLinkTask()
                .modulePath(helper.defaultModulePath())
                .output(helper.createNewImageDir("generate-jli"))
                .option("--generate-jli-classes=bmh:bmh-species=LL,L3")
                .addMods("java.base")
                .call();

        image = result.assertSuccess();

        JImageValidator.validate(
                image.resolve("lib").resolve("modules"),
                classFilesForSpecies(List.of("LL", "L3")),
                classFilesForSpecies(List.of("L4")));


        // Test disabling BMH species generation
        result = JImageGenerator.getJLinkTask()
                .modulePath(helper.defaultModulePath())
                .output(helper.createNewImageDir("generate-jli"))
                .option("--generate-jli-classes=not-bmh:bmh-species=LL,L3")
                .addMods("java.base")
                .call();

        image = result.assertSuccess();
        JImageValidator.validate(
            image.resolve("lib").resolve("modules"),
            List.of(),
            classFilesForSpecies(List.of("LL", "L3", "L4")));


        // Test an invalid set of options
        result = JImageGenerator.getJLinkTask()
                .modulePath(helper.defaultModulePath())
                .output(helper.createNewImageDir("generate-jli"))
                .option("--generate-jli-classes=bmh:bmh-species=LL,L7V")
                .addMods("java.base")
                .call();

        result.assertFailure();
    }

    private static List<String> classFilesForSpecies(List<String> species) {
        return species.stream()
                .map(s -> "/java.base/java/lang/invoke/BoundMethodHandle$Species_" + s + ".class")
                .collect(Collectors.toList());
    }
}
