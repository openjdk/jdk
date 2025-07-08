/*
 * Copyright (c) 2015, 2021 SAP SE. All rights reserved.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jtreg.SkippedException;

/*
 * @test
 * @bug 8132374 8359830
 * @summary Check that the value of the os.version property is equal
 *          to the value of the corresponding OS provided tools.
 * @library /test/lib
 * @build jtreg.SkippedException
 * @run main OsVersionTest
 * @author Volker Simonis
 */
public class OsVersionTest {

    public static void main(String[] args) throws Throwable {
        final String osVersion = System.getProperty("os.version");
        if (osVersion == null) {
            throw new Error("Missing value for os.version system property");
        }
        if (Platform.isLinux()) {
            OutputAnalyzer output = ProcessTools.executeProcess("uname", "-r");
            output.shouldHaveExitValue(0);
            if (!osVersion.equals(output.getOutput().trim())) {
                throw new Error(osVersion + " != " + output.getOutput().trim());
            }
        } else if (Platform.isOSX()) {
            testMacOS(osVersion);
        } else if (Platform.isAix()) {
            OutputAnalyzer output1 = ProcessTools.executeProcess("uname", "-v");
            output1.shouldHaveExitValue(0);
            OutputAnalyzer output2 = ProcessTools.executeProcess("uname", "-r");
            output2.shouldHaveExitValue(0);
            String version = output1.getOutput().trim() + "." + output2.getOutput().trim();
            if (!osVersion.equals(version)) {
                throw new Error(osVersion + " != " + version);
            }
        } else if (Platform.isWindows()) {
            OutputAnalyzer output = ProcessTools.executeProcess("cmd", "/c", "ver");
            output.shouldHaveExitValue(0);
            String version = output.firstMatch(".+\\[Version ([0-9.]+)\\]", 1);
            if (version == null || !version.startsWith(osVersion)) {
                throw new Error(osVersion + " != " + version);
            }
        } else {
            throw new jtreg.SkippedException("This test is currently not supported on " +
                               Platform.getOsName());
        }
    }

    private static void testMacOS(final String sysPropOsVersion) throws Exception {
        final ProcessBuilder pb = new ProcessBuilder("sw_vers", "-productVersion");
        // if the test was launched with SYSTEM_VERSION_COMPAT environment variable set,
        // then propagate that to the sw_vers too
        final String versionCompat = System.getenv().get("SYSTEM_VERSION_COMPAT");
        if (versionCompat != null) {
            pb.environment().put("SYSTEM_VERSION_COMPAT", versionCompat);
        }
        final OutputAnalyzer output = ProcessTools.executeCommand(pb);
        output.shouldHaveExitValue(0);
        final String swVersOutput = output.getOutput().trim();
        if (!sysPropOsVersion.equals(swVersOutput)) {
            throw new Error("sw_vers reports macOS version: " + swVersOutput
                    + " but os.version system property reports version: " + sysPropOsVersion);
        }
    }
}
