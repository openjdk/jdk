/*
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

/*
 * @test TestEarlyDynamicLoadJcmd
 * @summary Test that jcmd fails gracefully when the JVM is not in live phase
 * @requires vm.jvmti
 * @library /test/lib
 * @run driver TestEarlyDynamicLoadJcmd
 */

import java.io.File;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.JDKToolFinder;

public class TestEarlyDynamicLoadJcmd {
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                "-XX:+StartAttachListener",
                "-agentpath:" + Utils.TEST_NATIVE_PATH + File.separator + System.mapLibraryName("EarlyDynamicAttach"),
                "-version");
        pb.environment().put("MODE", "jcmd");
        String jcmdPath = JDKToolFinder.getJDKTool("jcmd");
        pb.environment().put("JCMD_PATH", jcmdPath);

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
    }
}
