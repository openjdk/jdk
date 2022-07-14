/*
 * Copyright (c) 2022, Red Hat Inc. and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.WindowsHelper;

import java.nio.file.Path;

import static jdk.jpackage.test.WindowsHelper.queryRegistryValue;

/*
 * @test
 * @summary jpackage with --win-registry-file
 * @library ../helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @requires (os.family == "windows")
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @compile WinRegFileTest.java
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=WinRegFileTest
 */
public class WinRegFileTest {
    @Test
    public static void test() {
        Path regFile = Path.of(System.getProperty("test.src"))
                .resolve("regfile/test_success_simple.reg");
        new PackageTest()
                .forTypes(PackageType.WINDOWS)
                .configureHelloApp()
                .addInitializer(cmd -> cmd
                        .addArgument("--win-registry-file")
                        .addArgument(regFile.normalize().toAbsolutePath()))
                .addInstallVerifier(cmd -> {
                    TKit.assertEquals("test_value1",  queryRegistryValue(
                            "HKEY_LOCAL_MACHINE\\SOFTWARE\\test", "test_name1"),
                            "reg value");
                })
                .run();
    }
}
