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

/*
 * @test
 * @summary Test Multi-Release jar usage in runtime
 * @library /test/lib
 * @library /lib/testlibrary
 * @modules jdk.compiler
 * @build jdk.test.lib.JDKToolFinder jdk.test.lib.JDKToolLauncher
 *        jdk.test.lib.process.OutputAnalyzer
 *        jdk.test.lib.process.ProcessTools
 *        CompilerUtils RuntimeTest
 * @run testng RuntimeTest
 */

import static org.testng.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class RuntimeTest {
    public static final int SUCCESS = 0;
    private final String src = System.getProperty("test.src", ".");
    private final String usr = System.getProperty("user.dir", ".");

    @DataProvider(name = "jarFiles")
    Object[][] jarFiles() {
        return new Object[][] { { "MV_BOTH.jar", 9, 9, 9 },
                { "MV_ONLY_9.jar", 9, 9, 9 },
                { "NON_MV.jar", 8, 8, 8 } };
    }

    @BeforeClass
    protected void setUpTest() throws Throwable {
        compile();
        Path classes = Paths.get("classes");
        jar("cfm", "MV_BOTH.jar", "manifest.txt",
                "-C", classes.resolve("base").toString(), ".",
                "--release", "9", "-C", classes.resolve("v9").toString(), ".",
                "--release", "10", "-C", classes.resolve("v10").toString(), ".")
                .shouldHaveExitValue(0);

        jar("cfm", "MV_ONLY_9.jar", "manifest.txt",
                "-C", classes.resolve("base").toString(), ".",
                "--release", "9", "-C", classes.resolve("v9").toString(), ".")
                .shouldHaveExitValue(0);
        jar("cfm", "NON_MV.jar", "manifest.txt",
                "-C", classes.resolve("base").toString(), ".")
                .shouldHaveExitValue(0);
    }

    @Test(dataProvider = "jarFiles")
    public void testClasspath(String jar, int mainVer, int helperVer,
            int resVer) throws Throwable {
        String[] command = { "-cp", jar, "testpackage.Main" };
        System.out.println("Command arguments:" + Arrays.asList(command));
        System.out.println();
        java(command).shouldHaveExitValue(SUCCESS)
                .shouldContain("Main version: " + mainVer)
                .shouldContain("Helpers version: " + helperVer)
                .shouldContain("Resource version: " + resVer);
    }

    @Test(dataProvider = "jarFiles")
    void testMVJarAsLib(String jar, int mainVer, int helperVer, int resVer)
            throws Throwable {
        String[] apps = { "UseByImport", "UseByReflection" };
        for (String app : apps) {
            String[] command = {"-cp",
                    jar + File.pathSeparatorChar + "classes/test/", app };
            System.out.println("Command arguments:" + Arrays.asList(command));
            System.out.println();
            java(command).shouldHaveExitValue(SUCCESS)
                    .shouldContain("Main version: " + mainVer)
                    .shouldContain("Helpers version: " + helperVer)
                    .shouldContain("Resource version: " + resVer);
        }
    }

    @Test(dataProvider = "jarFiles")
    void testJavaJar(String jar, int mainVer, int helperVer, int resVer)
            throws Throwable {
        String[] command = { "-jar", jar };
        System.out.println("Command arguments:" + Arrays.asList(command));
        System.out.println();
        java(command).shouldHaveExitValue(SUCCESS)
                .shouldContain("Main version: " + mainVer)
                .shouldContain("Helpers version: " + helperVer)
                .shouldContain("Resource version: " + resVer);
    }

    @Test(dataProvider = "jarFiles")
    void testURLClassLoader(String jarName, int mainVer, int helperVer,
            int resVer) throws ClassNotFoundException, NoSuchMethodException,
            IllegalAccessException, IllegalArgumentException,
            InvocationTargetException, IOException {
        Path pathToJAR = Paths.get(jarName).toAbsolutePath();
        URL jarURL1 = new URL("jar:file:" + pathToJAR + "!/");
        URL jarURL2 = new URL("file:///" + pathToJAR);
        testURLClassLoaderURL(jarURL1, mainVer, helperVer, resVer);
        testURLClassLoaderURL(jarURL2, mainVer, helperVer, resVer);
    }

    private static void testURLClassLoaderURL(URL jarURL,
            int mainVersionExpected, int helperVersionExpected,
            int resourceVersionExpected) throws ClassNotFoundException,
            NoSuchMethodException, IllegalAccessException,
            IllegalArgumentException, InvocationTargetException, IOException {
        System.out.println(
                "Testing URLClassLoader MV JAR support for URL: " + jarURL);
        URL[] urls = { jarURL };
        int mainVersionActual;
        int helperVersionActual;
        int resourceVersionActual;
        try (URLClassLoader cl = URLClassLoader.newInstance(urls)) {
            Class c = cl.loadClass("testpackage.Main");
            Method getMainVersion = c.getMethod("getMainVersion");
            mainVersionActual = (int) getMainVersion.invoke(null);
            Method getHelperVersion = c.getMethod("getHelperVersion");
            helperVersionActual = (int) getHelperVersion.invoke(null);
            try (InputStream ris = cl.getResourceAsStream("versionResource");
                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(ris))) {
                resourceVersionActual = Integer.parseInt(br.readLine());
            }
        }

        assertEquals(mainVersionActual, mainVersionExpected,
                         "Test failed: Expected Main class version: "
                         + mainVersionExpected + " Actual version: "
                         + mainVersionActual);
        assertEquals(helperVersionActual, helperVersionExpected,
                         "Test failed: Expected Helper class version: "
                         + helperVersionExpected + " Actual version: "
                         + helperVersionActual);
        assertEquals(resourceVersionActual, resourceVersionExpected,
                         "Test failed: Expected resource version: "
                         + resourceVersionExpected + " Actual version: "
                         + resourceVersionActual);
    }

    @Test(dataProvider = "jarFiles")
    void testJjs(String jar, int mainVer, int helperVer, int resVer)
            throws Throwable {
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jjs");
        launcher.addToolArg("-cp").addToolArg(jar)
                .addToolArg(src + "/data/runtimetest/MVJarJJSTestScript.js");
        ProcessTools.executeCommand(launcher.getCommand())
                .shouldHaveExitValue(SUCCESS)
                .shouldContain("Main version: " + mainVer)
                .shouldContain("Helpers version: " + helperVer)
                .shouldContain("Resource version: " + resVer);
    }

    private static OutputAnalyzer jar(String... args) throws Throwable {
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jar");
        Stream.of(args).forEach(launcher::addToolArg);
        return ProcessTools.executeCommand(launcher.getCommand());
    }

    private void compile() throws Throwable {
        String[] vers = { "base", "v9", "v10" };
        for (String ver : vers) {
            Path classes = Paths.get(usr, "classes", ver);
            Files.createDirectories(classes);
            Path source = Paths.get(src, "data", "runtimetest", ver);
            assertTrue(CompilerUtils.compile(source, classes));
            Files.copy(source.resolve("versionResource"),
                    classes.resolve("versionResource"),
                    StandardCopyOption.REPLACE_EXISTING);
        }

        Path classes = Paths.get(usr, "classes", "test");
        Files.createDirectory(classes);
        Path source = Paths.get(src, "data", "runtimetest", "test");
        assertTrue(
                CompilerUtils.compile(source, classes, "-cp", "classes/base/"));
        Files.copy(Paths.get(src, "data", "runtimetest", "manifest.txt"),
                Paths.get(usr, "manifest.txt"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    OutputAnalyzer java(String... args) throws Throwable {
        String java = JDKToolFinder.getJDKTool("java");

        List<String> commands = new ArrayList<>();
        commands.add(java);
        Stream.of(args).forEach(x -> commands.add(x));
        return ProcessTools.executeCommand(new ProcessBuilder(commands));
    }
}
