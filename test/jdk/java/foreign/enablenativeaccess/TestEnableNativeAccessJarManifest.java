/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary Basic test for Enable-Native-Access attribute in the
 *          manifest of a main application JAR
 * @library /test/lib
 * @requires jdk.foreign.linker != "UNSUPPORTED"
 * @requires !vm.musl
 *
 * @enablePreview
 * @build TestEnableNativeAccessJarManifest
 *        panama_module/*
 *        org.openjdk.foreigntest.unnamed.PanamaMainUnnamedModule
 * @run testng TestEnableNativeAccessJarManifest
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.JarUtils;

import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;

public class TestEnableNativeAccessJarManifest extends TestEnableNativeAccessBase {

    private static final String REINVOKER = "TestEnableNativeAccessJarManifest$Reinvoker";

    static record Attribute(String name, String value) {}

    @Test(dataProvider = "cases")
    public void testEnableNativeAccessInJarManifest(String action, String cls, Result expectedResult,
                                                    List<Attribute> attributes, List<String> vmArgs, List<String> programArgs) throws Exception {
        Manifest man = new Manifest();
        Attributes attrs = man.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.put(Attributes.Name.MAIN_CLASS, cls);

        for (Attribute attrib : attributes) {
            attrs.put(new Attributes.Name(attrib.name()), attrib.value());
        }

        // create the JAR file with Test1 and Test2
        Path jarfile = Paths.get(action + ".jar");
        Files.deleteIfExists(jarfile);

        Path classes = Paths.get(System.getProperty("test.classes", ""));
        JarUtils.createJarFile(jarfile, man, classes, Paths.get(cls.replace('.', '/') + ".class"));

        // java -jar test.jar
        List<String> command = new ArrayList<>(List.of(
            "--enable-preview",
            "-Djava.library.path=" + System.getProperty("java.library.path")
        ));
        command.addAll(vmArgs);
        command.add("-jar");
        command.add(jarfile.toString());
        command.addAll(programArgs);
        OutputAnalyzer outputAnalyzer = ProcessTools.executeTestJava(command.toArray(String[]::new))
                .outputTo(System.out)
                .errorTo(System.out);
        checkResult(expectedResult, outputAnalyzer);
    }

    @DataProvider
    public Object[][] cases() {
        return new Object[][] {
            // simple cases where a jar contains a single main class with no dependencies
            { "panama_no_unnamed_module_native_access", UNNAMED, successWithWarning("ALL-UNNAMED"),
                    List.of(), List.of(), List.of() },
            { "panama_unnamed_module_native_access", UNNAMED, successNoWarning(),
                    List.of(new Attribute("Enable-Native-Access", "ALL-UNNAMED")), List.of(), List.of() },
            { "panama_unnamed_module_native_access_invalid", UNNAMED,
                    failWithError("Error: illegal value \"asdf\" for Enable-Native-Access manifest attribute. Only ALL-UNNAMED is allowed"),
                    List.of(new Attribute("Enable-Native-Access", "asdf")), List.of(), List.of() },

            // more complex cases where a jar invokes a module on the module path that does native access
            { "panama_enable_native_access_false", REINVOKER, successWithWarning("panama_module"),
                    List.of(new Attribute("Enable-Native-Access", "ALL-UNNAMED")),
                    List.of("-p", MODULE_PATH, "--add-modules=panama_module"),
                    List.of(PANAMA_MAIN_CLS) },
            { "panama_enable_native_access_reflection_false", REINVOKER, successWithWarning("panama_module"),
                    List.of(new Attribute("Enable-Native-Access", "ALL-UNNAMED")),
                    List.of("-p", MODULE_PATH, "--add-modules=panama_module"),
                    List.of(PANAMA_REFLECTION_CLS) },
            { "panama_enable_native_access_invoke_false", REINVOKER, successWithWarning("panama_module"),
                    List.of(new Attribute("Enable-Native-Access", "ALL-UNNAMED")),
                    List.of("-p", MODULE_PATH, "--add-modules=panama_module"),
                    List.of(PANAMA_INVOKE_CLS) },

            { "panama_enable_native_access_true", REINVOKER, successNoWarning(),
                    List.of(new Attribute("Enable-Native-Access", "ALL-UNNAMED")),
                    List.of("-p", MODULE_PATH, "--add-modules=panama_module", "--enable-native-access=panama_module"),
                    List.of(PANAMA_MAIN_CLS) },
            { "panama_enable_native_access_reflection_true", REINVOKER, successNoWarning(),
                    List.of(new Attribute("Enable-Native-Access", "ALL-UNNAMED")),
                    List.of("-p", MODULE_PATH, "--add-modules=panama_module", "--enable-native-access=panama_module"),
                    List.of(PANAMA_REFLECTION_CLS) },
            { "panama_enable_native_access_invoke_true", REINVOKER, successNoWarning(),
                    List.of(new Attribute("Enable-Native-Access", "ALL-UNNAMED")),
                    List.of("-p", MODULE_PATH, "--add-modules=panama_module", "--enable-native-access=panama_module"),
                    List.of(PANAMA_INVOKE_CLS) }
        };
    }

    public class Reinvoker {
        public static void main(String[] args) throws Throwable {
            Class<?> realMainClass = Class.forName(args[0]);
            realMainClass.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        }
    }
}
