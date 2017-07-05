/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8032884 8072579
 * @summary Globalbindings optionalProperty="primitive" does not work when minOccurs=0
 * @library /lib/testlibrary
 * @modules java.xml.bind
 * @run testng/othervm XjcOptionalPropertyTest
 */

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.util.Arrays;
import jdk.testlibrary.JDKToolLauncher;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class XjcOptionalPropertyTest {

    @Test
    public void optionalPropertyTest() throws Exception {
        runXjc();
        compileXjcGeneratedClasses();
        URLClassLoader testClassLoader;
        testClassLoader = URLClassLoader.newInstance(new URL[]{testWorkDirUrl});
        Class fooClass = testClassLoader.loadClass(CLASS_TO_TEST);
        Object foo = fooClass.newInstance();
        Method[] methods = foo.getClass().getMethods();
        System.out.println("Found [" + methods.length + "] methods");
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            if (method.getName().equals("setFoo")) {
                System.out.println("Checking method [" + method.getName() + "]");
                Class[] parameterTypes = method.getParameterTypes();
                Assert.assertEquals(parameterTypes.length, 1);
                Assert.assertTrue(parameterTypes[0].isPrimitive());
                break;
            }
        }
    }

    @BeforeTest
    public void setUp() throws IOException {
        // Create test directory inside scratch
        testWorkDir = Paths.get(System.getProperty("user.dir", "."));
        // Save its URL
        testWorkDirUrl = testWorkDir.toUri().toURL();
        // Get test source directory path
        testSrcDir = Paths.get(System.getProperty("test.src", "."));
        // Get path of xjc result folder
        xjcResultDir = testWorkDir.resolve(TEST_PACKAGE);
        // Copy schema document file to scratch directory
        Files.copy(testSrcDir.resolve(XSD_FILENAME), testWorkDir.resolve(XSD_FILENAME), REPLACE_EXISTING);
    }

    // Compile schema file into java classes definitions
    void runXjc() throws Exception {
        // Prepare process builder to run schemagen tool and save its output
        JDKToolLauncher xjcLauncher = JDKToolLauncher.createUsingTestJDK("xjc");
        xjcLauncher.addToolArg(XSD_FILENAME);
        System.out.println("Executing xjc command: " + Arrays.asList(xjcLauncher.getCommand()));
        ProcessBuilder pb = new ProcessBuilder(xjcLauncher.getCommand());
        // Set xjc work directory with the input java file
        pb.directory(testWorkDir.toFile());
        pb.inheritIO();
        Process p = pb.start();
        p.waitFor();
        p.destroy();
    }

    // Compile java classes with javac tool
    void compileXjcGeneratedClasses() throws Exception {
        JDKToolLauncher javacLauncher = JDKToolLauncher.createUsingTestJDK("javac");
        javacLauncher.addToolArg("--add-modules");
        javacLauncher.addToolArg("java.xml.bind");
        javacLauncher.addToolArg(xjcResultDir.resolve("Foo.java").toString());
        System.out.println("Compiling xjc generated class: " + Arrays.asList(javacLauncher.getCommand()));
        ProcessBuilder pb = new ProcessBuilder(javacLauncher.getCommand());
        pb.inheritIO();
        pb.directory(testWorkDir.toFile());
        Process p = pb.start();
        p.waitFor();
        p.destroy();
    }

    // Test schema filename
    static final String XSD_FILENAME = "optional-property-schema.xsd";
    // Test package with generated class
    static final String TEST_PACKAGE = "anamespace";
    // Name of generated java class
    static final String CLASS_TO_TEST = TEST_PACKAGE+".Foo";
    // Test working directory
    Path testWorkDir;
    // Test working directory URL
    URL testWorkDirUrl;
    // Directory with test src
    Path testSrcDir;
    // Directory with java files generated by xjc
    Path xjcResultDir;
}
