/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8059510
 * @summary Test jcmd VM.symboltable and VM.stringtable options
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions DumpSymbolAndStringTable
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.JDKToolFinder;

public class DumpSymbolAndStringTable {
    public static void main(String[] args) throws Exception {
        // Grab my own PID
        String pid = Long.toString(ProcessTools.getProcessId());

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(new String[] {JDKToolFinder.getJDKTool("jcmd"), pid, "VM.symboltable", "-verbose"});
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        try {
            output.shouldContain("24 2: DumpSymbolAndStringTable\n");
        } catch (RuntimeException e) {
            output.shouldContain("Unknown diagnostic command");
        }

        pb.command(new String[] {JDKToolFinder.getJDKTool("jcmd"), pid, "VM.stringtable", "-verbose"});
        output = new OutputAnalyzer(pb.start());
        try {
            output.shouldContain("16: java.lang.String\n");
        } catch (RuntimeException e) {
            output.shouldContain("Unknown diagnostic command");
        }
    }
}
