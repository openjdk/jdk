/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=off
 * @summary Test the NMT statistics with xml format, which is not supported
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main/othervm -XX:NativeMemoryTracking=off JcmdStatisticsXml off
*/

/*
 * @test id=summary
 * @summary Test the NMT statistics with xml format, which is not supported
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main/othervm -XX:NativeMemoryTracking=summary JcmdStatisticsXml summary
*/

/*
 * @test id=detail
 * @summary Test the NMT statistics with xml format, which is not supported
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main/othervm -XX:NativeMemoryTracking=detail JcmdStatisticsXml detail
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.JDKToolFinder;

public class JcmdStatisticsXml {

    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        String pid = Long.toString(jdk.test.lib.process.ProcessTools.getProcessId());
        pb.command(new String[] { JDKToolFinder.getJDKTool("jcmd"), pid, "VM.native_memory", "statistics=true", "format=xml"});
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        if (args.length == 1 && args[0].equals("off")) {
          output.shouldContain("Native memory tracking is not enabled");
        } else {
          output.shouldContain("Statistics cannot be reported in XML format.");
        }
    }
}
