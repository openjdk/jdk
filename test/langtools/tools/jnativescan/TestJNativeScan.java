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
 * @run junit TestJNativeScan
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.util.JarUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestJNativeScan extends JNativeScanTestBase {

    static Path TEST_CLASSES;

    static Path CLASS_PATH_APP;
    static Path SINGLE_JAR_CLASS_PATH;
    static Path SINGLE_JAR_MODULAR;
    static Path ORG_MYAPP;
    static Path ORG_LIB;
    static Path UNNAMED_PACKAGE_JAR;
    static Path LIB_JAR;

    @BeforeAll
    public static void before() throws IOException {
        SINGLE_JAR_CLASS_PATH = Path.of("singleJar.jar");
        TEST_CLASSES = Path.of(System.getProperty("test.classes", ""));
        JarUtils.createJarFile(SINGLE_JAR_CLASS_PATH, TEST_CLASSES, Path.of("main", "Main.class"));

        LIB_JAR = Path.of("lib.jar");
        JarUtils.createJarFile(LIB_JAR, TEST_CLASSES, Path.of("lib", "Lib.class"));
        Manifest manifest = new Manifest();
        Attributes mainAttrs = manifest.getMainAttributes();
        mainAttrs.put(Attributes.Name.MANIFEST_VERSION, "1.0"); // need version or other attributes will be ignored
        mainAttrs.putValue("Class-Path", "lib.jar non-existent.jar");
        CLASS_PATH_APP = Path.of("app.jar");
        JarUtils.createJarFile(CLASS_PATH_APP, manifest, TEST_CLASSES, Path.of("app", "App.class"));

        SINGLE_JAR_MODULAR = makeModularJar("org.singlejar");
        ORG_MYAPP = makeModularJar("org.myapp");
        ORG_LIB = makeModularJar("org.lib");
        makeModularJar("org.service");

        UNNAMED_PACKAGE_JAR = Path.of("unnamed_package.jar");
        JarUtils.createJarFile(UNNAMED_PACKAGE_JAR, TEST_CLASSES, Path.of("UnnamedPackage.class"));
    }

    @Test
    public void testSingleJarClassPath() {
        assertSuccess(jnativescan("--class-path", SINGLE_JAR_CLASS_PATH.toString()))
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
        assertSuccess(jnativescan("--class-path", CLASS_PATH_APP.toString()))
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
                .stderrShouldContain("Path does not appear to be a jar file, or directory containing classes");
    }

    @Test
    public void testModuleNotAJarFile() {
        String modulePath = moduleRoot("org.myapp").toString() + File.pathSeparator + ORG_LIB.toString();
        assertSuccess(jnativescan("--module-path", modulePath,
                        "--add-modules", "ALL-MODULE-PATH"))
                .stdoutShouldContain("lib.Lib")
                .stdoutShouldContain("lib.Lib::m()void is a native method declaration")
                .stdoutShouldContain("lib.Lib::doIt()void references restricted methods")
                .stdoutShouldContain("java.lang.foreign.MemorySegment::reinterpret(long)MemorySegment");
    }

    @Test
    public void testPrintNativeAccess() {
        assertSuccess(jnativescan("--module-path", MODULE_PATH,
                                  "-add-modules", "org.singlejar,org.myapp",
                                  "--print-native-access"))
                .stdoutShouldMatch("org.lib,org.service,org.singlejar");
    }

    @Test
    public void testNoDuplicateNames() {
        String classPath = SINGLE_JAR_CLASS_PATH + File.pathSeparator + CLASS_PATH_APP;
        OutputAnalyzer output = assertSuccess(jnativescan("--class-path", classPath, "--print-native-access"));
        String[] moduleNames = output.getStdout().split(",");
        Set<String> names = new HashSet<>();
        for (String name : moduleNames) {
            assertTrue(names.add(name.strip()));
        }
    }

    @Test
    public void testUnnamedPackage() {
        assertSuccess(jnativescan("--class-path", UNNAMED_PACKAGE_JAR.toString()))
                .stderrShouldBeEmpty()
                .stdoutShouldContain("ALL-UNNAMED")
                .stdoutShouldNotContain(".UnnamedPackage")
                .stdoutShouldContain("UnnamedPackage")
                .stdoutShouldContain("UnnamedPackage::m()void is a native method declaration")
                .stdoutShouldContain("UnnamedPackage::main(String[])void references restricted methods")
                .stdoutShouldContain("java.lang.foreign.MemorySegment::reinterpret(long)MemorySegment");
    }

    @Test
    public void testPositionalArguments() {
        assertFailure(jnativescan("foo"))
                .stdoutShouldBeEmpty()
                .stderrShouldContain("jnativescan does not accept positional arguments");
    }

    @Test
    public void testMissingRootModules() {
        assertFailure(jnativescan("--module-path", MODULE_PATH))
                .stdoutShouldBeEmpty()
                .stderrShouldContain("Missing required option(s) [add-modules]");
    }

    @Test
    public void testClassPathDirectory() {
        assertSuccess(jnativescan("--class-path", TEST_CLASSES.toString()))
                .stderrShouldBeEmpty()
                .stdoutShouldContain("ALL-UNNAMED")
                .stdoutShouldContain("UnnamedPackage")
                .stdoutShouldContain("UnnamedPackage::m()void is a native method declaration")
                .stdoutShouldContain("UnnamedPackage::main(String[])void references restricted methods")
                .stdoutShouldContain("main.Main")
                .stdoutShouldContain("main.Main::m()void is a native method declaration")
                .stdoutShouldContain("main.Main::main(String[])void references restricted methods")
                .stdoutShouldContain("lib.Lib")
                .stdoutShouldContain("lib.Lib::m()void is a native method declaration")
                .stdoutShouldContain("lib.Lib::doIt()void references restricted methods")
                .stdoutShouldContain("java.lang.foreign.MemorySegment::reinterpret(long)MemorySegment");
    }

    @Test
    public void testMultipleClassPathJars() {
        // make sure all of these are reported, even when they are all in the ALL-UNNAMED module
        String classPath = UNNAMED_PACKAGE_JAR
                + File.pathSeparator + SINGLE_JAR_CLASS_PATH
                + File.pathSeparator + LIB_JAR;
        assertSuccess(jnativescan("--class-path", classPath))
                .stderrShouldBeEmpty()
                .stdoutShouldContain("ALL-UNNAMED")
                .stdoutShouldContain("UnnamedPackage")
                .stdoutShouldContain(UNNAMED_PACKAGE_JAR.toString())
                .stdoutShouldContain("lib.Lib")
                .stdoutShouldContain(LIB_JAR.toString())
                .stdoutShouldContain("main.Main")
                .stdoutShouldContain(SINGLE_JAR_CLASS_PATH.toString());
    }
}
