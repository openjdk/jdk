/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
import java.io.File;
import java.io.IOException;

/*
 * @test
 * @bug 6994753
 * @summary tests -XshowSettings options
 * @compile -XDignore.symbol.file Settings.java TestHelper.java
 * @run main Settings
 * @author ksrini
 */
public class Settings {
    private static File testJar = null;

    static void init() throws IOException {
        if  (testJar != null) {
            return;
        }
        testJar = new File("test.jar");
        StringBuilder tsrc = new StringBuilder();
        tsrc.append("public static void main(String... args) {\n");
        tsrc.append("   for (String x : args) {\n");
        tsrc.append("        System.out.println(x);\n");
        tsrc.append("   }\n");
        tsrc.append("}\n");
        TestHelper.createJar(testJar, tsrc.toString());
    }

    static void checkContains(TestHelper.TestResult tr, String str) {
        if (!tr.contains(str)) {
            System.out.println(tr);
            throw new RuntimeException(str + " not found");
        }
    }

    static void checkNoContains(TestHelper.TestResult tr, String str) {
        if (tr.contains(str)) {
            System.out.println(tr.status);
            throw new RuntimeException(str + " found");
        }
    }

    private static final String VM_SETTINGS = "VM settings:";
    private static final String PROP_SETTINGS = "Property settings:";
    private static final String LOCALE_SETTINGS = "Locale settings:";

    static void containsAllOptions(TestHelper.TestResult tr) {
        checkContains(tr, VM_SETTINGS);
        checkContains(tr, PROP_SETTINGS);
        checkContains(tr, LOCALE_SETTINGS);
    }

    static void runTestOptionDefault() throws IOException {
        TestHelper.TestResult tr = null;
        tr = TestHelper.doExec(TestHelper.javaCmd, "-Xmx512m", "-Xss128k",
                "-XshowSettings", "-jar", testJar.getAbsolutePath());
        containsAllOptions(tr);
        if (!tr.isOK()) {
            System.out.println(tr.status);
            throw new RuntimeException("test fails");
        }
    }

    static void runTestOptionAll() throws IOException {
        init();
        TestHelper.TestResult tr = null;
        tr = TestHelper.doExec(TestHelper.javaCmd, "-XshowSettings:all");
        containsAllOptions(tr);
    }

    static void runTestOptionVM() throws IOException {
        TestHelper.TestResult tr = null;
        tr = TestHelper.doExec(TestHelper.javaCmd, "-XshowSettings:vm");
        checkContains(tr, VM_SETTINGS);
        checkNoContains(tr, PROP_SETTINGS);
        checkNoContains(tr, LOCALE_SETTINGS);
    }

    static void runTestOptionProperty() throws IOException {
        TestHelper.TestResult tr = null;
        tr = TestHelper.doExec(TestHelper.javaCmd, "-XshowSettings:properties");
        checkNoContains(tr, VM_SETTINGS);
        checkContains(tr, PROP_SETTINGS);
        checkNoContains(tr, LOCALE_SETTINGS);
    }

    static void runTestOptionLocale() throws IOException {
        TestHelper.TestResult tr = null;
        tr = TestHelper.doExec(TestHelper.javaCmd, "-XshowSettings:locale");
        checkNoContains(tr, VM_SETTINGS);
        checkNoContains(tr, PROP_SETTINGS);
        checkContains(tr, LOCALE_SETTINGS);
    }

    static void runTestBadOptions() throws IOException {
        TestHelper.TestResult tr = null;
        tr = TestHelper.doExec(TestHelper.javaCmd, "-XshowSettingsBadOption");
        checkNoContains(tr, VM_SETTINGS);
        checkNoContains(tr, PROP_SETTINGS);
        checkNoContains(tr, LOCALE_SETTINGS);
        checkContains(tr, "Unrecognized option: -XshowSettingsBadOption");
    }
    public static void main(String... args) {
        try {
            runTestOptionAll();
            runTestOptionDefault();
            runTestOptionVM();
            runTestOptionProperty();
            runTestOptionLocale();
            runTestBadOptions();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }
}
