/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
# @bug 8296329
* @summary Tests for version validator.
* @library /test/lib
* @modules java.base/jdk.internal.misc
*          jdk.compiler
*          jdk.jartool
* @build jdk.test.lib.Utils
*        jdk.test.lib.Asserts
*        jdk.test.lib.JDKToolFinder
*        jdk.test.lib.JDKToolLauncher
*        jdk.test.lib.Platform
*        jdk.test.lib.process.*
*        MRTestBase
* @run testng/timeout=1200 VersionValidatorTest
*/

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class VersionValidatorTest extends MRTestBase {
    private Path root;

    @BeforeMethod
    void testInit(Method method) {
        root = Paths.get(method.getName());
    }

    @Test(dataProvider = "differentMajorVersions")
    public void onlyCompatibleVersionIsAllowedInMultiReleaseJar(String baseMajorVersion, String otherMajorVersion,
            boolean enablePreviewForBaseVersion, boolean enablePreviewForOtherVersion, boolean isAcceptable)
            throws Throwable {
        Path baseVersionClassesDir = compileLibClass(baseMajorVersion, enablePreviewForBaseVersion);
        Path otherVersionClassesDir = compileLibClass(otherMajorVersion, enablePreviewForOtherVersion);

        var result = jar("--create", "--file", "lib.jar", "-C", baseVersionClassesDir.toString(), "Lib.class",
                "--release", otherMajorVersion, "-C", otherVersionClassesDir.toString(), "Lib.class");

        if (isAcceptable) {
            result.shouldHaveExitValue(SUCCESS)
                    .shouldBeEmptyIgnoreVMWarnings();
        } else {
            result.shouldNotHaveExitValue(SUCCESS)
                    .shouldContain("has a class version incompatible with an earlier version");
        }
    }

    private Path compileLibClass(String majorVersion, boolean enablePreview) throws Throwable {
        String classTemplate = """
                public class Lib {
                    public static int version = $VERSION;
                }
                    """;

        Path sourceFile = Files.createDirectories(root.resolve("src").resolve(majorVersion)).resolve("Lib.java");
        Files.write(sourceFile, classTemplate.replace("$VERSION", majorVersion).getBytes());

        Path classesDir = root.resolve("classes").resolve(majorVersion);

        javac(classesDir, List.of("--release", majorVersion), sourceFile);
        if (enablePreview) {
            rewriteMinorVersionForEnablePreviewClass(classesDir.resolve("Lib.class"));
        }
        return classesDir;
    }

    private void rewriteMinorVersionForEnablePreviewClass(Path classFile) throws Throwable {
        byte[] classBytes = Files.readAllBytes(classFile);
        classBytes[4] = -1;
        classBytes[5] = -1;
        Files.write(classFile, classBytes);
    }

    @DataProvider
    Object[][] differentMajorVersions() {
        return new Object[][] {
                { "16", "17", false, true, true },
                { "16", "17", false, false, true },
                { "16", "17", true, true, true },
                { "16", "17", true, false, true },
                { "17", "16", false, true, false },
                { "17", "16", false, false, false },
                { "17", "16", true, true, false },
                { "17", "16", true, false, false },
        };
    }
}
