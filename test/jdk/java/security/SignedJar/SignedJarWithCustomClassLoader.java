/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8280890
 * @library /test/lib
 * @build SignedJarWithCustomClassLoader CustomClassLoader
 * @run main/othervm SignedJarWithCustomClassLoader
 * @summary Make sure java.system.class.loader property can be used when custom
 *     class loader is inside signed jar
 */

import java.nio.file.Path;
import java.nio.file.Paths;

import jdk.test.lib.SecurityTools;
import jdk.test.lib.compiler.InMemoryJavaCompiler;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.JarUtils;

public class SignedJarWithCustomClassLoader {

    public static void main(String[] args) throws Exception {

        // compile the Main program
        String main = """
                      public class Main {
                          public static void main(String[] args) {}
                      }
                      """;
        String testClasses = System.getProperty("test.classes", "");
        ClassFileInstaller.writeClassToDisk("Main",
             InMemoryJavaCompiler.compile("Main", main),
             testClasses);

        // create the jar file
        Path classes = Paths.get(testClasses);
        JarUtils.createJarFile(Paths.get("test.jar"), classes,
            classes.resolve("CustomClassLoader.class"),
            classes.resolve("Main.class"));

        // create signer's keypair
        SecurityTools.keytool("-genkeypair -keyalg RSA -keystore ks " +
                              "-storepass changeit -dname CN=test -alias test")
                     .shouldHaveExitValue(0);

        // sign jar
        SecurityTools.jarsigner("-keystore ks -storepass changeit " +
                                "-signedjar signed.jar test.jar test")
                     .shouldHaveExitValue(0);

        // run app with system class loader set to custom classloader
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-cp", "signed.jar",
            "-Djava.system.class.loader=CustomClassLoader", "Main");
        ProcessTools.executeProcess(pb)
                    .shouldHaveExitValue(0);

        // sign jar again, but this time with SHA-1 which is disabled
        SecurityTools.jarsigner("-keystore ks -storepass changeit " +
                                "-digestalg SHA-1 -sigalg SHA1withRSA " +
                                "-signedjar signed.jar test.jar test")
                     .shouldHaveExitValue(0);

        // run app again, should still succeed even though SHA-1 is disabled
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-cp", "signed.jar",
            "-Djava.system.class.loader=CustomClassLoader", "Main");
        ProcessTools.executeProcess(pb)
                    .shouldHaveExitValue(0);
    }
}
