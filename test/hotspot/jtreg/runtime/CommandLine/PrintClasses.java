/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, Alibaba Group Holding Limited. All rights reserved.
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
 * @bug 8275775
 * @summary Test jcmd VM.classes
 * @library /test/lib
 * @run main/othervm PrintClasses
 */

/*
 * @test
 * @bug 8298162
 * @summary Test jcmd VM.classes with JFR
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm -XX:StartFlightRecording PrintClasses
 */

import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.process.OutputAnalyzer;

public class PrintClasses {
  public static void main(String args[]) throws Exception {
    var pb = new ProcessBuilder();

    pb.command(new PidJcmdExecutor().getCommandLine("VM.classes"));
    var output = new OutputAnalyzer(pb.start());
    output.shouldNotContain("instance size");
    output.shouldContain(PrintClasses.class.getSimpleName());

    pb.command(new PidJcmdExecutor().getCommandLine("VM.classes", "-verbose"));
    output = new OutputAnalyzer(pb.start());
    output.shouldContain("instance size");
    output.shouldContain(PrintClasses.class.getSimpleName());

    // Test for previous bug in misc flags printing
    output.shouldNotContain("##name");
  }
}
