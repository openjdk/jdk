import java.util.HashSet;
import java.util.Set;
import com.oracle.java.testlibrary.Platform;

/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test of VM.dynlib diagnostic command via MBean
 * @library /testlibrary
 * @compile DcmdUtil.java
 * @run main DynLibDcmdTest
 */

public class DynLibDcmdTest {

    public static void main(String[] args) throws Exception {
        String result = DcmdUtil.executeDcmd("VM.dynlibs");

        String osDependentBaseString = null;
        if (Platform.isSolaris()) {
            osDependentBaseString = "lib%s.so";
        } else if (Platform.isWindows()) {
            osDependentBaseString = "%s.dll";
        } else if (Platform.isOSX()) {
            osDependentBaseString = "lib%s.dylib";
        } else if (Platform.isLinux()) {
            osDependentBaseString = "lib%s.so";
        }

        if (osDependentBaseString == null) {
            throw new Exception("Unsupported OS");
        }

        Set<String> expectedContent = new HashSet<>();
        expectedContent.add(String.format(osDependentBaseString, "jvm"));
        expectedContent.add(String.format(osDependentBaseString, "java"));
        expectedContent.add(String.format(osDependentBaseString, "management"));

        for(String expected : expectedContent) {
            if (!result.contains(expected)) {
                throw new Exception("Dynamic library list output did not contain the expected string: '" + expected + "'");
            }
        }
    }
}
