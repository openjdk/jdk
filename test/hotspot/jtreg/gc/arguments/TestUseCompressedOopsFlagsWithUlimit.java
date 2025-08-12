/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

package gc.arguments;

/*
 * @test TestUseCompressedOopsFlagsWithUlimit
 * @bug 8280761
 * @summary Verify correct UseCompressedOops when MaxRAM and MaxRAMPercentage
 * are specified with ulimit -v.
 * @library /test/lib
 * @library /
 * @requires vm.bits == "64"
 * @comment ulimit clashes with the memory requirements of ASAN
 * @requires !vm.asan
 * @requires os.family == "linux"
 * @requires vm.gc != "Z"
 * @requires vm.opt.UseCompressedOops == null
 * @run driver gc.arguments.TestUseCompressedOopsFlagsWithUlimit
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.ArrayList;
import java.util.Arrays;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestUseCompressedOopsFlagsWithUlimit {

  private static void checkFlag(long ulimit, long maxram, int maxrampercent, boolean expectcoop) throws Exception {

    ArrayList<String> args = new ArrayList<String>();
    args.add("-XX:MaxRAM=" + maxram);
    args.add("-XX:MaxRAMPercentage=" + maxrampercent);
    args.add("-XX:+PrintFlagsFinal");

    // Avoid issues with libjvmci failing to reserve
    // a large virtual address space for its heap
    args.add("-Xint");

    args.add("-version");

    // Convert bytes to kbytes for ulimit -v
    var ulimit_prefix = "ulimit -v " + (ulimit / 1024);

    String cmd = ProcessTools.getCommandLine(ProcessTools.createTestJavaProcessBuilder(args));
    OutputAnalyzer output = ProcessTools.executeProcess("sh", "-c", ulimit_prefix + ";" + cmd);
    output.shouldHaveExitValue(0);
    String stdout = output.getStdout();

    boolean actualcoop = getFlagBoolValue("UseCompressedOops", stdout);
    if (actualcoop != expectcoop) {
      throw new RuntimeException("UseCompressedOops set to " + actualcoop +
        ", expected " + expectcoop + " when running with the following flags: " + Arrays.asList(args).toString());
    }
  }

  private static boolean getFlagBoolValue(String flag, String where) {
    Matcher m = Pattern.compile(flag + "\\s+:?= (true|false)").matcher(where);
    if (!m.find()) {
      throw new RuntimeException("Could not find value for flag " + flag + " in output string");
    }
    return m.group(1).equals("true");
  }

  public static void main(String args[]) throws Exception {
    // Tests
    // Verify that UseCompressedOops Ergo follows ulimit -v setting.

    long oneG = 1L * 1024L * 1024L * 1024L;

    // Args: ulimit, max_ram, max_ram_percent, expected_coop
    // Setting MaxRAMPercentage explicitly to make the test more resilient.
    checkFlag(10 * oneG, 32 * oneG, 100, true);
    checkFlag(10 * oneG, 128 * oneG, 100, true);
  }
}
