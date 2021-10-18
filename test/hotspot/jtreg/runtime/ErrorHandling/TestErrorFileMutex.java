/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8274794
 * @summary Test that locks are printed in the Error file.
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @requires (vm.debug == true)
 * @run driver TestErrorFileMutex
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.*;
import java.util.regex.Pattern;

public class TestErrorFileMutex {

  public static void do_test() throws Exception {

    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-Xmx64M",
            "-XX:-CreateCoredumpOnCrash",
            "-XX:ErrorHandlerTest=3",
            "-version");

    OutputAnalyzer output_detail = new OutputAnalyzer(pb.start());
    output_detail.shouldMatch("# A fatal error has been detected by the Java Runtime Environment:.*");

    File f = ErrorFileScanner.findHsErrorFileInOutput(output_detail);
    System.out.println("Found hs error file at " + f.getAbsolutePath());

    ErrorFileScanner.scanHsErrorFileForContent(f, new Pattern[] {
            Pattern.compile("# *Internal Error.*"),
            Pattern.compile(".*VM Mutexes/Monitors currently owned by a thread:.*"),
            Pattern.compile(".*ErrorTest_lock - owner thread:.*"),
            Pattern.compile(".*Threads_lock - owner thread:.*")
    });
  }

  public static void main(String[] args) throws Exception {
    do_test();
  }

}



