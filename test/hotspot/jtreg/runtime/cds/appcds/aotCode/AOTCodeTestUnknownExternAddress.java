/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/**
 * @test
 * @bug 8391718
 * @summary Test AOTAssertOnUnknownExternalAddress flag with unknown external address
 * @requires vm.flagless
 * @requires vm.debug
 * @comment the flag is debug
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler2.enabled
 * @requires vm.simpleArch == "x64" | vm.simpleArch == "aarch64"
 * @modules java.base/jdk.internal.misc:+open
 * @library /test/lib /test/setup_aot
 * @build ${test.main.class}
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 TestUnknownAddress
 * @run driver/timeout=1500 ${test.main.class}
 */

import java.lang.reflect.*;
import sun.misc.*;

import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;


public class AOTCodeTestUnknownExternAddress {

    public static void main(String... args) throws Exception {
        String appJar = ClassFileInstaller.getJarPath("app.jar");
        String aotConfigFile = "app.aotconfig";
        String aotCacheFile = "app.aot";
        String appClass = "TestUnknownAddress";

        ProcessBuilder pb;
        OutputAnalyzer out;

        // first make sure we have a valid aotConfigFile
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:CompileCommand=compileonly,TestUnknownAddress::test*",
            "-Xcomp", "-XX:-TieredCompilation",
            "-Xlog:aot",
            "-XX:AOTMode=record",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-cp", appJar, appClass);

        out = CDSTestUtils.executeAndLog(pb, "train");
        out.shouldHaveExitValue(0);

        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:CompileCommand=compileonly,TestUnknownAddress::test*",
            "-Xcomp", "-XX:-TieredCompilation",
            "-Xlog:aot+codecache=debug",
            "-Xlog:aot+codecache+exit=debug",
            "-Xlog:aot+codecache+nmethod",
            "-XX:-AOTAssertOnUnknownExternalAddress",
            "-XX:AOTMode=create",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-XX:AOTCache=" + aotCacheFile,
            "-cp", appJar);

        out = CDSTestUtils.executeAndLog(pb, "assemble skip assert");
        out.shouldNotContain("Address 0xffffffffffffffff is missing in AOT Code Cache addresses table");
        out.shouldNotMatch("'TestUnknownAddress\\.test1\\(Z\\)V' AOT.*is skipped: store to AOT Code Cache failed");
        out.shouldMatch("'TestUnknownAddress\\.test1\\(Z\\)V' AOT.*: wrote to AOT Code Cache");
        out.shouldMatch("'TestUnknownAddress\\.test2\\(Z\\)V' AOT.*is skipped: store to AOT Code Cache failed");
        out.shouldMatch("aot,codecache,exit.*Wrote [1-9]\\d* AOT code entries to AOT Code Cache");
        out.shouldHaveExitValue(0);

        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:CompileCommand=compileonly,TestUnknownAddress::test*",
            "-Xcomp", "-XX:-TieredCompilation",
            "-Xlog:aot+codecache+init=debug",
            "-XX:AOTMode=on",
            "-XX:AOTCache=" + aotCacheFile,
            "-cp", appJar, appClass);

        out = CDSTestUtils.executeAndLog(pb, "production");
        out.shouldMatch("aot,codecache,init.*\\s+Loaded [1-9]\\d* AOT code entries from AOT Code Cache");
        out.shouldHaveExitValue(0);

        // Assembly run with assert
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:CompileCommand=compileonly,TestUnknownAddress::test*",
            "-Xcomp", "-XX:-TieredCompilation",
            "-XX:-CreateCoredumpOnCrash",
            "-Xlog:aot+codecache=debug",
            "-Xlog:aot+codecache+exit=debug",
            "-Xlog:aot+codecache+nmethod",
            "-XX:+AOTAssertOnUnknownExternalAddress",
            "-XX:AOTMode=create",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-XX:AOTCache=" + aotCacheFile,
            "-cp", appJar);

        out = ProcessTools.executeProcess(pb);
        out.shouldContain("for <unknown> is missing in AOT Code Cache addresses table");
        out.shouldNotHaveExitValue(0);
    }
}

class TestUnknownAddress {
    private static Unsafe unsafe;
    private static final long UNKNOWN_ADDRESS = 0x1234_5678_9000L;
    private static final long MINUS_1_ADDRESS = -1L;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe)field.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void test1(boolean b) {
        if (b) {
            unsafe.putLong(MINUS_1_ADDRESS, 42);
        }
    }

    public static void test2(boolean b) {
        if (b) {
            unsafe.putLong(UNKNOWN_ADDRESS, 42);
        }
    }

    public static void main(String[] args) {
        test1(false);
        test2(false);
    }
}

