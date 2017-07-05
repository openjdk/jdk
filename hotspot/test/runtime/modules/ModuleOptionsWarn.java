/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8162415
 * @summary Test warnings for ignored properties.
 * @modules java.base/jdk.internal.misc
 * @library /testlibrary
 */

import jdk.test.lib.*;

// Test that the VM behaves correctly when processing command line module system properties.
public class ModuleOptionsWarn {

    public static void main(String[] args) throws Exception {

        // Test that a warning is issued for module related properties that get ignored.
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+PrintWarnings", "-Djdk.module.ignored", "-version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Ignoring system property option");
        output.shouldHaveExitValue(0);

        // Test that a warning can be suppressed for module related properties that get ignored.
        pb = ProcessTools.createJavaProcessBuilder(
            "-Djdk.module.ignored", "-XX:-PrintWarnings", "-version");
        output = new OutputAnalyzer(pb.start());
        output.shouldNotContain("Ignoring system property option");
        output.shouldHaveExitValue(0);

        // Test that a warning is not issued for properties of the form "jdk.module.main"
        pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+PrintWarnings", "-Djdk.module.main.ignored", "-version");
        output = new OutputAnalyzer(pb.start());
        output.shouldNotContain("Ignoring system property option");
        output.shouldHaveExitValue(0);
    }
}
