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
package jdk.jpackage.test;

import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Annotations.Parameters;

public class JavaAppDescTest {

    public JavaAppDescTest(JavaAppDesc expectedAppDesc, JavaAppDesc actualAppDesc) {
        this.expectedAppDesc = expectedAppDesc;
        this.actualAppDesc = actualAppDesc;
    }

    @Test
    public void test() {
        TKit.assertEquals(expectedAppDesc.toString(), actualAppDesc.toString(), null);
        TKit.assertTrue(expectedAppDesc.equals(actualAppDesc), null);
    }

    @Test
    @Parameter({"Foo", "Foo.class"})
    @Parameter({"com.bar.A", "com/bar/A.class"})
    @Parameter({"module/com.bar.A", "com/bar/A.class"})
    public static void testClassFilePath(String... args) {
        var appDesc = args[0];
        var expectedClassFilePath = Path.of(args[1]);
        TKit.assertEquals(expectedClassFilePath.toString(), JavaAppDesc.parse(
                appDesc).classFilePath().toString(), null);
    }

    @Parameters
    public static List<Object[]> input() {
        return List.of(new Object[][] {
            createTestCase("", "hello.jar:Hello"),
            createTestCase("foo.jar*", "foo.jar*hello.jar:Hello"),
            createTestCase("Bye", "hello.jar:Bye"),
            createTestCase("bye.jar:", "bye.jar:Hello"),
            createTestCase("duke.jar:com.other/com.other.foo.bar.Buz!@3.7", appDesc -> {
                return appDesc
                        .setBundleFileName("duke.jar")
                        .setModuleName("com.other")
                        .setClassName("com.other.foo.bar.Buz")
                        .setWithMainClass(true)
                        .setModuleVersion("3.7");
            }),
        });
    }

    private static JavaAppDesc[] createTestCase(String inputAppDesc, String expectedAppDescStr) {
        return createTestCase(inputAppDesc, appDesc -> {
            return stripDefaultSrcJavaPath(JavaAppDesc.parse(expectedAppDescStr));
        });
    }

    private static JavaAppDesc stripDefaultSrcJavaPath(JavaAppDesc appDesc) {
        var defaultAppDesc = HelloApp.createDefaltAppDesc();
        if (appDesc.srcJavaPath().equals(defaultAppDesc.srcJavaPath())) {
            appDesc.setSrcJavaPath(null);
        }
        return appDesc;
    }

    private static JavaAppDesc[] createTestCase(String appDesc, UnaryOperator<JavaAppDesc> config) {
        var actualAppDesc = stripDefaultSrcJavaPath(JavaAppDesc.parse(appDesc));

        var expectedAppDesc = config.apply(stripDefaultSrcJavaPath(HelloApp.createDefaltAppDesc()));

        return new JavaAppDesc[] {expectedAppDesc, actualAppDesc};
    }

    private final JavaAppDesc expectedAppDesc;
    private final JavaAppDesc actualAppDesc;
}
