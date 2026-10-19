/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8059510 8213445
 * @summary Test jcmd VM.symboltable and VM.stringtable
 * @library /test/lib
 * @run main/othervm DumpSymbolAndStringTable
 */
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.process.OutputAnalyzer;

public class DumpSymbolAndStringTable {
    public static final String s = "MY_INTERNED_STRING";

    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();

        pb.command(new PidJcmdExecutor().getCommandLine("VM.symboltable", "-verbose"));
        OutputAnalyzer output = CDSTestUtils.executeAndLog(pb, "jcmd-symboltable");
        output.shouldContain("18 1: MY_INTERNED_STRING\n"); // This symbol should have been interned

        pb.command(new PidJcmdExecutor().getCommandLine("VM.stringtable", "-verbose"));
        output = CDSTestUtils.executeAndLog(pb, "jcmd-stringtable");
        output.shouldContain("18: MY_INTERNED_STRING\n"); // This string should have been interned
    }
}
