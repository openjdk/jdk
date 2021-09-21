/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4504355 4744260
 * @summary problems if signed crypto provider is the most preferred provider
 * @modules java.base/sun.security.tools.keytool
 *          jdk.jartool/sun.security.tools.jarsigner
 * @library /test/lib
 * @run main/othervm DynStatic
 */

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.JarUtils;

public class DynStatic {

    private static final String TEST_SRC =
        Paths.get(System.getProperty("test.src")).toString();
    private static final Path TEST_CLASSES =
        Paths.get(System.getProperty("test.classes"));

    private static final Path EXP_SRC_DIR = Paths.get(TEST_SRC, "com");
    private static final Path EXP_DEST_DIR = Paths.get("build");
    private static final Path DYN_SRC =
        Paths.get(TEST_SRC, "DynSignedProvFirst.java");
    private static final Path STATIC_SRC =
        Paths.get(TEST_SRC, "StaticSignedProvFirst.java");

    private static final String STATIC_PROPS =
        Paths.get(TEST_SRC, "Static.props").toString();

    public static void main(String[] args) throws Exception {

        // Compile the provider
        CompilerUtils.compile(EXP_SRC_DIR, EXP_DEST_DIR);

        // Create a jar file containing the provider
        JarUtils.createJarFile(Path.of("exp.jar"), EXP_DEST_DIR, "com");

        // Create a keystore
        sun.security.tools.keytool.Main.main(
            ("-genkeypair -dname CN=Signer -keystore exp.ks -storepass "
                + "changeit -keypass changeit -keyalg rsa").split(" "));

        // Sign jar
        sun.security.tools.jarsigner.Main.main(
                "-storepass changeit -keystore exp.ks exp.jar mykey"
                        .split(" "));

        // Compile the DynSignedProvFirst test program
        CompilerUtils.compile(DYN_SRC, TEST_CLASSES, "-classpath", "exp.jar");

        // Run the DynSignedProvFirst test program
        ProcessTools.executeTestJvm("-classpath",
            TEST_CLASSES.toString() + File.pathSeparator + "exp.jar",
            "DynSignedProvFirst")
            .shouldContain("test passed");

        // Compile the StaticSignedProvFirst test program
        CompilerUtils.compile(STATIC_SRC, TEST_CLASSES, "-classpath", "exp.jar");

        // Run the StaticSignedProvFirst test program
        ProcessTools.executeTestJvm("-classpath",
            TEST_CLASSES.toString() + File.pathSeparator + "exp.jar",
            "-Djava.security.properties=file:" + STATIC_PROPS,
            "StaticSignedProvFirst")
            .shouldContain("test passed");
    }
}
