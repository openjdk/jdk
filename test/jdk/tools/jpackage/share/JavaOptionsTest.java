/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @summary jpackage create image with --java-options test
 * @library ../helpers
 * @build JPackageHelper
 * @build JPackagePath
 * @build JavaOptionsBase
 * @modules jdk.incubator.jpackage
 * @run main/othervm -Xmx512m JavaOptionsTest
 */
public class JavaOptionsTest {
    private static final String OUTPUT = "output";

    private static final String[] CMD = {
        "--type", "app-image",
        "--input", "input",
        "--dest", OUTPUT,
        "--name", "test",
        "--main-jar", "hello.jar",
        "--main-class", "Hello",
        "--java-options", "TBD"};

    private static final String[] CMD2 = {
        "--type", "app-image",
        "--input", "input",
        "--dest", OUTPUT,
        "--name", "test",
        "--main-jar", "hello.jar",
        "--main-class", "Hello",
        "--java-options", "TBD",
        "--java-options", "TBD",
        "--java-options", "TBD"};

    public static void main(String[] args) throws Exception {
        JPackageHelper.createHelloImageJar();
        JavaOptionsBase.testCreateAppImageJavaOptions(CMD);
        JPackageHelper.deleteOutputFolder(OUTPUT);
        JavaOptionsBase.testCreateAppImageJavaOptionsToolProvider(CMD);

        JPackageHelper.deleteOutputFolder(OUTPUT);
        JavaOptionsBase.testCreateAppImageJavaOptions2(CMD2);
        JPackageHelper.deleteOutputFolder(OUTPUT);
        JavaOptionsBase.testCreateAppImageJavaOptions2ToolProvider(CMD2);
    }

}
