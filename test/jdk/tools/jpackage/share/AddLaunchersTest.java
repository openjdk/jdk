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
 * @summary jpackage create image with additional launcher test
 * @library ../helpers
 * @build JPackageHelper
 * @build JPackagePath
 * @build AddLauncherBase
 * @modules jdk.incubator.jpackage
 * @run main/othervm -Xmx512m AddLaunchersTest
 */
public class AddLaunchersTest {
    private static final String OUTPUT = "output";
    private static final String [] CMD1 = {
        "--description", "Test non modular app with multiple add-launchers where one is modular app and other is non modular app",
        "--type", "app-image",
        "--input", "input",
        "--dest", OUTPUT,
        "--name", "test",
        "--main-jar", "hello.jar",
        "--main-class", "Hello",
        "--module-path", "module",
        "--add-modules", "com.hello,java.desktop",
        "--add-launcher", "test3=j1.properties",
        "--add-launcher", "test4=m1.properties"};

    private static final String [] CMD2 = {
        "--description", "Test modular app with multiple add-launchers where one is modular app and other is non modular app",
        "--type", "app-image",
        "--input", "input",
        "--dest", OUTPUT,
        "--name", "test",
        "--module", "com.hello/com.hello.Hello",
        "--module-path", "module",
        "--add-launcher", "test5=jl.properties",
        "--add-launcher", "test6=m1.properties"};

    public static void main(String[] args) throws Exception {
        JPackageHelper.createHelloImageJar();
        JPackageHelper.createHelloModule();
        AddLauncherBase.createSLProperties();

        JPackageHelper.deleteOutputFolder(OUTPUT);
        AddLauncherBase.testCreateAppImageToolProvider(
                CMD1, false, "test3");

        JPackageHelper.deleteOutputFolder(OUTPUT);
        AddLauncherBase.testCreateAppImage(
                CMD1, false, "test4");

        JPackageHelper.deleteOutputFolder(OUTPUT);
        AddLauncherBase.testCreateAppImage(
                CMD2, false, "test5");

        JPackageHelper.deleteOutputFolder(OUTPUT);
        AddLauncherBase.testCreateAppImageToolProvider(
                CMD2, false, "test6");

    }

}
