/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8274397
 * @summary Ensure the app name system property is set on macOS
 * @requires os.family == "mac"
 * @compile MacOSAppNamePropertyTest.java SystemPropertyTest.java
 * @run main MacOSAppNamePropertyTest
 */

import java.util.ArrayList;
import java.util.List;
/*
 * If the system property apple.awt.application.name is unset, it should default
 * to the name of this test class.
 * If it is set, then it should be used instead of the class.
 * The arg. to the test indicates the *expected* name.
 * The test will fail if the property is not set or does not match
 */
public class MacOSAppNamePropertyTest extends TestHelper {

    static final String APPNAME = "SystemPropertyTest";

    public static void main(String[]args) {
        if (!isMacOSX) {
            return;
        }
        execTest(null, APPNAME);
        execTest("-Dapple.awt.application.name=Foo", "Foo");
    }

    static void execTest(String propSetting, String expect) {
        List<String> cmdList = new ArrayList<>();
        cmdList.add(javaCmd);
        cmdList.add("-cp");
        cmdList.add(TEST_CLASSES_DIR.getAbsolutePath());
        if (propSetting != null) {
            cmdList.add(propSetting);
        }
        cmdList.add(APPNAME);
        cmdList.add(expect);
        TestResult tr = doExec(cmdList.toArray(new String[cmdList.size()]));
        if (!tr.isOK()) {
            System.err.println(tr.toString());
            throw new RuntimeException("Test Fails");
        }
    }
}
