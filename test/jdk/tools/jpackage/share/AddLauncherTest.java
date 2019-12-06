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

import java.util.ArrayList;

/*
 * @test
 * @summary jpackage create image with additional launcher test
 * @library ../helpers
 * @build JPackageHelper
 * @build JPackagePath
 * @build AddLauncherBase
 * @modules jdk.incubator.jpackage
 * @run main/othervm -Xmx512m AddLauncherTest
 */
public class AddLauncherTest {
    private static final String OUTPUT = "output";
    private static final String [] CMD = {
        "--type", "app-image",
        "--input", "input",
        "--dest", OUTPUT,
        "--name", "test",
        "--main-jar", "hello.jar",
        "--main-class", "Hello",
        "--add-launcher", "test2=sl.properties"};

    private final static String OPT1 = "-Dparam1=xxx";
    private final static String OPT2 = "-Dparam2=yyy";
    private final static String OPT3 = "-Dparam3=zzz";
    private final static String ARG1 = "original-argument";

    private static final String [] CMD1 = {
        "--type", "app-image",
        "--input", "input",
        "--dest", OUTPUT,
        "--name", "test",
        "--main-jar", "hello.jar",
        "--main-class", "Hello",
        "--java-options", OPT1,
        "--java-options", OPT2,
        "--java-options", OPT3,
        "--arguments", ARG1,
        "--add-launcher", "test4=sl.properties"};


    public static void main(String[] args) throws Exception {
        JPackageHelper.createHelloImageJar();
        AddLauncherBase.createSLProperties();
        AddLauncherBase.testCreateAppImage(CMD);

        ArrayList <String> argList = new ArrayList <String> ();
        argList.add(ARG1);

        ArrayList <String> optList = new ArrayList <String> ();
        optList.add(OPT1);
        optList.add(OPT2);
        optList.add(OPT3);

        JPackageHelper.deleteOutputFolder(OUTPUT);
        AddLauncherBase.testCreateAppImage(CMD1, argList, optList);
    }

}
