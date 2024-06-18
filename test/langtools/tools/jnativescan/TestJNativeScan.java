/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib .. ./cases/modules
 * @build JNativeScanTestBase
 *     org.singlejar/* org.lib/* org.myapp/* org.service/*
 *     cases.classpath.singlejar.main.Main
 *     cases.classpath.lib.Lib
 *     cases.classpath.app.App
 *     cases.classpath.unnamed_package.UnnamedPackage
 * @run testng TestJNativeScan
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.util.JarUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static org.testng.Assert.assertTrue;

class TestJNativeScan extends JNativeScanTestBase {

    Path testClasses;

    Path classPathApp;
    Path singleJarClassPath;
    Path singleJarModular;
    Path orgMyapp;
    Path orgLib;
    Path unnamedPackageJar;

    @BeforeClass
    public void before() throws IOException {
        singleJarClassPath = Path.of("singleJar.jar");
        testClasses = Path.of(System.getProperty("test.classes", ""));
        JarUtils.createJarFile(singleJarClassPath, testClasses, Path.of("main", "Main.class"));

        JarUtils.createJarFile(Path.of("lib.jar"), testClasses, Path.of("lib", "Lib.class"));
        Manifest manifest = new Manifest();
        Attributes mainAttrs = manifest.getMainAttributes();
        mainAttrs.put(Attributes.Name.MANIFEST_VERSION, "1.0"); // need version or other attributes will be ignored
        mainAttrs.putValue("Class-Path", "lib.jar non-existent.jar");
        classPathApp = Path.of("app.jar");
        JarUtils.createJarFile(classPathApp, manifest, testClasses, Path.of("app", "App.class"));

        singleJarModular = makeModularJar("org.singlejar");
        orgMyapp = makeModularJar("org.myapp");
        orgLib = makeModularJar("org.lib");
        makeModularJar("org.service");

        unnamedPackageJar = Path.of("unnamed_package.jar");
        JarUtils.createJarFile(unnamedPackageJar, manifest, testClasses, Path.of("UnnamedPackage.class"));
    }

    @Test
    public void testSingleJarClassPath() {
        assertSuccess(jnativescan("--class-path", singleJarClassPath.toString()))
                .stderrShouldBeEmpty()
                .stdoutShouldContain("ALL-UNNAMED")
                .stdoutShouldContain("main.Main")
                .stdoutShouldContain("main.Main::m()void is a native method declaration")
                .stdoutShouldContain("main.Main::main(String[])void references restricted methods")
                .stdoutShouldContain("java.lang.foreign.MemorySegment::reinterpret(long)MemorySegment");
    }

    @Test
    public void testSingleJarModulePath() {
        assertSuccess(jnativescan("--module-path", MODULE_PATH, "--add-modules", "org.singlejar"))
                .stderrShouldBeEmpty()
                .stdoutShouldContain("org.singlejar")
                .stdoutShouldContain("org.singlejar.main.Main")
                .stdoutShouldContain("org.singlejar.main.Main::m()void is a native method declaration")
                .stdoutShouldContain("org.singlejar.main.Main::main(String[])void references restricted methods")
                .stdoutShouldContain("java.lang.foreign.MemorySegment::reinterpret(long)MemorySegment");
    }

    @Test
    public void testWithDepModule() {
        assertSuccess(jnativescan("--module-path", MODULE_PATH, "--add-modules", "org.myapp"))
                .stderrShouldBeEmpty()
                .stdoutShouldContain("org.lib")
                .stdoutShouldContain("org.lib.Lib")
                .stdoutShouldContain("org.lib.Lib::m()void is a native method declaration")
                .stdoutShouldContain("org.lib.Lib::doIt()void references restricted methods")
                .stdoutShouldContain("java.lang.foreign.MemorySegment::reinterpret(long)MemorySegment")
                .stdoutShouldContain("org.service")
                .stdoutShouldContain("org.service.ServiceImpl")
                .stdoutShouldContain("org.service.ServiceImpl::m()void is a native method declaration")
                .stdoutShouldContain("org.service.ServiceImpl::doIt()void references restricted methods")
                .stdoutShouldContain("java.lang.foreign.MemorySegment::reinterpret(long)MemorySegment");
    }

    @Test
    public void testAllModulePath() {
        assertSuccess(jnativescan("--module-path", MODULE_PATH, "--add-modules", "ALL-MODULE-PATH"))
                .stderrShouldBeEmpty()
                .stdoutShouldContain("org.singlejar")
                .stdoutShouldContain("org.lib")
                .stdoutShouldContain("org.service");
    }

    @Test
    public void testClassPathAttribute() {
        assertSuccess(jnativescan("--class-path", classPathApp.toString()))
                .stderrShouldBeEmpty()
                .stdoutShouldContain("ALL-UNNAMED")
                .stdoutShouldContain("lib.Lib")
                .stdoutShouldContain("lib.Lib::m()void is a native method declaration")
                .stdoutShouldContain("lib.Lib::doIt()void references restricted methods")
                .stdoutShouldContain("java.lang.foreign.MemorySegment::reinterpret(long)MemorySegment");
    }

    @Test
    public void testInvalidRelease() {
        assertFailure(jnativescan("--module-path", MODULE_PATH, "--add-modules", "ALL-MODULE-PATH", "--release", "asdf"))
                .stderrShouldContain("Invalid release");
    }

    @Test
    public void testReleaseNotSupported() {
        assertFailure(jnativescan("--module-path", MODULE_PATH, "--add-modules", "ALL-MODULE-PATH", "--release", "9999999"))
                .stderrShouldContain("Release: 9999999 not supported");
    }

    @Test
    public void testFileDoesNotExist() {
        assertFailure(jnativescan("--class-path", "non-existent.jar"))
                .stderrShouldContain("File does not exist, or does not appear to be a regular jar file");
    }

    @Test
    public void testModuleNotAJarFile() {
        String modulePath = moduleRoot("org.myapp").toString() + File.pathSeparator + orgLib.toString();
        assertFailure(jnativescan("--module-path", modulePath,
                        "--add-modules", "ALL-MODULE-PATH"))
                .stderrShouldContain("File does not exist, or does not appear to be a regular jar file");
    }

    @Test
    public void testPrintNativeAccess() {
        assertSuccess(jnativescan("--module-path", MODULE_PATH,
                                  "-add-modules", "org.singlejar,org.myapp",
                                  "--print-native-access"))
                .stdoutShouldContain("org.singlejar")
                .stdoutShouldContain("org.lib")
                .stdoutShouldContain("org.service");
    }

    @Test
    public void testNoDuplicateNames() {
        String classPath = singleJarClassPath + File.pathSeparator + classPathApp;
        OutputAnalyzer output = assertSuccess(jnativescan("--class-path", classPath, "--print-native-access"));
        String[] moduleNames = output.getStdout().split(",");
        Set<String> names = new HashSet<>();
        for (String name : moduleNames) {
            assertTrue(names.add(name.strip()));
        }
    }

    @Test
    public void testUnnamedPackage() {
        assertSuccess(jnativescan("--class-path", unnamedPackageJar.toString()))
                .stderrShouldBeEmpty()
                .stdoutShouldContain("ALL-UNNAMED")
                .stdoutShouldNotContain(".UnnamedPackage")
                .stdoutShouldContain("UnnamedPackage")
                .stdoutShouldContain("UnnamedPackage::m()void is a native method declaration")
                .stdoutShouldContain("UnnamedPackage::main(String[])void references restricted methods")
                .stdoutShouldContain("java.lang.foreign.MemorySegment::reinterpret(long)MemorySegment");
    }
}
