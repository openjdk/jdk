/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestMaxRAMFlags
 * @bug 8278492
 * @summary Verify correct MinHeapSize when MinRAMPercentage is specified.
 * @library /test/lib
 * @library /
 * @requires vm.bits == "64"
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build sun.hotspot.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run driver gc.arguments.TestMinRAMFlag
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.ArrayList;
import java.util.Arrays;

import jdk.test.lib.process.OutputAnalyzer;

public class TestMinRAMFlag {

  private static void checkMinRAMSize(long maxram, double maxrampercent, double minrampercent, long expectheap) throws Exception {

    ArrayList<String> args = new ArrayList<String>();
    args.add("-Xlog:gc+heap=trace");
    args.add("-XX:MaxRAM=" + maxram);
    args.add("-XX:MaxRAMPercentage=" + maxrampercent);
    args.add("-XX:MinRAMPercentage=" + minrampercent);

    args.add("-XX:+PrintFlagsFinal");
    args.add("-version");

    ProcessBuilder pb = GCArguments.createJavaProcessBuilder(args);
    OutputAnalyzer output = new OutputAnalyzer(pb.start());
    output.shouldHaveExitValue(0);
    String stdout = output.getStdout();

    System.out.println(stdout);

    long actualheap = Long.parseLong(getFlagValue("MinHeapSize", stdout));
    if (actualheap != expectheap) {
      throw new RuntimeException("MinHeapSize value set to " + actualheap +
        ", expected " + expectheap + " when running with the following flags: " + Arrays.asList(args).toString());
    }
  }

  private static String getFlagValue(String flag, String where) {
    Matcher m = Pattern.compile(flag + "\\s+:?=\\s+\\d+").matcher(where);
    if (!m.find()) {
      throw new RuntimeException("Could not find value for flag " + flag + " in output string");
    }
    String match = m.group();
    return match.substring(match.lastIndexOf(" ") + 1, match.length());
  }

  public static void main(String args[]) throws Exception {
    // Verify that MinRAMPercentage correctly sets MinHeapSize for a few values of MinRAMPercentage

    long oneG = 1L * 1024L * 1024L * 1024L;

    // Args: MaxRAM , MaxRAMPercentage, MinRAMPercentage, expected min heap
    checkMinRAMSize(oneG, 100,  100, oneG);
    checkMinRAMSize(oneG, 100,   50, oneG / 2);
    checkMinRAMSize(oneG,  50,   25, oneG / 4);
    checkMinRAMSize(oneG,  12.5, 25, oneG / 8); // MaxRAMPercentage overrides
  }
}
