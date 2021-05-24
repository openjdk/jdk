/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, JetBrains s.r.o.. All rights reserved.
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

/* @test
 * @bug 8195129
 * @summary regression test for 8195129,
 *          verifies the ability to System.load() from a location containing non-Latin characters
 * @requires (os.family == "windows") | (os.family == "mac") | (os.family == "linux")
 * @library /test/lib
 * @build LoadLibraryUnicode
 * @run main/native LoadLibraryUnicodeTest
 */

import jdk.test.lib.process.ProcessTools;

public class LoadLibraryUnicodeTest {

    public static void main(String args[]) throws Exception {
        String nativePathSetting = "-Dtest.nativepath=" + getSystemProperty("test.nativepath");
        ProcessBuilder pb = ProcessTools.createTestJvm(nativePathSetting, LoadLibraryUnicode.class.getName());
        pb.environment().put("LC_ALL", "en_US.UTF-8");
        ProcessTools.executeProcess(pb)
                    .outputTo(System.out)
                    .errorTo(System.err)
                    .shouldHaveExitValue(0);
    }

    // Utility method to retrieve and validate system properties
    public static String getSystemProperty(String propertyName) throws Error {
        String systemProperty = System.getProperty(propertyName, "").trim();
        System.out.println(propertyName + " = " + systemProperty);
        if (systemProperty.isEmpty()) {
            throw new Error("TESTBUG: property " + propertyName + " is empty");
        }
        return systemProperty;
    }
}
