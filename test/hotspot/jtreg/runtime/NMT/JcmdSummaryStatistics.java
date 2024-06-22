/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Red Hat Inc.
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
 * @summary Test the NMT scale parameter
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main/othervm -XX:NativeMemoryTracking=summary JcmdSummaryStatistics
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.JDKToolFinder;

public class JcmdSummaryStatistics {

    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        OutputAnalyzer output;
        // Grab my own PID
        String pid = Long.toString(jdk.test.lib.process.ProcessTools.getProcessId());

        // Run 'jcmd <pid> VM.native_memory statistics=true'
        pb.command(new String[] { JDKToolFinder.getJDKTool("jcmd"), pid, "VM.native_memory", "statistics=true"});
        output = new OutputAnalyzer(pb.start());

        output.shouldContainMultiLinePattern(
                "Native Memory Tracking Statistics:",
                "State: summary",
                "Preinit state:",
                "entries:",
                "pre-init mallocs:",
                "MallocLimit:");
    }
}
