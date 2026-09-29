/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestMaxRAMPercentage
 * @bug 8222252
 * @summary Verify correct MaxHeapSize and UseCompressedOops when MaxRAMPercentage is specified
 * @library /test/lib
 * @library /
 * @requires vm.bits == "64"
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm
 *      -Xbootclasspath/a:.
 *      -XX:+UnlockDiagnosticVMOptions
 *      -XX:+WhiteBoxAPI
 *      gc.arguments.TestMaxRAMPercentage
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.ArrayList;
import java.util.Arrays;

import jdk.test.whitebox.WhiteBox;
import jdk.test.lib.process.OutputAnalyzer;
import jtreg.SkippedException;

public class TestMaxRAMPercentage {

  private static final WhiteBox wb = WhiteBox.getWhiteBox();

  private static void checkMaxRAMSize(double maxrampercent, boolean forcecoop, long expectheap, boolean expectcoop) throws Exception {

    ArrayList<String> args = new ArrayList<String>();
    args.add("-XX:MaxRAMPercentage=" + maxrampercent);
    if (forcecoop) {
      args.add("-XX:+UseCompressedOops");
    }

    args.add("-XX:+PrintFlagsFinal");
    args.add("-version");

    OutputAnalyzer output = GCArguments.executeLimitedTestJava(args);
    output.shouldHaveExitValue(0);
    String stdout = output.getStdout();

    long actualheap = new Long(getFlagValue("MaxHeapSize", stdout)).longValue();
    if (actualheap != expectheap) {
      throw new RuntimeException("MaxHeapSize value set to " + actualheap +
        ", expected " + expectheap + " when running with the following flags: " + Arrays.asList(args).toString());
    }

    boolean actualcoop = getFlagBoolValue("UseCompressedOops", stdout);
    if (actualcoop != expectcoop) {
      throw new RuntimeException("UseCompressedOops set to " + actualcoop +
        ", expected " + expectcoop + " when running with the following flags: " + Arrays.asList(args).toString());
    }
  }

  private static long getHeapBaseMinAddress() throws Exception {
    ArrayList<String> args = new ArrayList<String>();
    args.add("-XX:+PrintFlagsFinal");
    args.add("-version");

    OutputAnalyzer output = GCArguments.executeLimitedTestJava(args);
    output.shouldHaveExitValue(0);
    String stdout = output.getStdout();
    return (new Long(getFlagValue("HeapBaseMinAddress", stdout)).longValue());
  }

  private static String getFlagValue(String flag, String where) {
    Matcher m = Pattern.compile(flag + "\\s+:?=\\s+\\d+").matcher(where);
    if (!m.find()) {
      throw new RuntimeException("Could not find value for flag " + flag + " in output string");
    }
    String match = m.group();
    return match.substring(match.lastIndexOf(" ") + 1, match.length());
  }

  private static boolean getFlagBoolValue(String flag, String where) {
    Matcher m = Pattern.compile(flag + "\\s+:?= (true|false)").matcher(where);
    if (!m.find()) {
      throw new RuntimeException("Could not find value for flag " + flag + " in output string");
    }
    return m.group(1).equals("true");
  }

  public static void main(String args[]) throws Exception {
    // Hotspot startup logic reduces MaxHeapForCompressedOops by HeapBaseMinAddress
    // in order to get zero based compressed oops offsets.
    long heapbaseminaddr = getHeapBaseMinAddress();
    long maxcoopheap = TestUseCompressedOopsErgoTools.getMaxHeapForCompressedOops(new String [0]) - heapbaseminaddr;

    // The headroom is used to get/not get compressed oops from the maxcoopheap size
    long M = 1L * 1024L * 1024L;
    long headroom = 64 * M;

    long requiredHostMemory = maxcoopheap + headroom;

    // Get host memory
    long hostMemory = wb.hostPhysicalMemory();

    System.out.println("hostMemory: " + hostMemory + ", requiredHostMemory: " + requiredHostMemory);

    if (hostMemory < requiredHostMemory) {
      throw new SkippedException("Not enough RAM on machine to run. Test skipped!");
    }

    double MaxRAMPercentage = ((double)maxcoopheap / hostMemory) * 100.0;
    double headroomPercentage = ((double)headroom / hostMemory) * 100.0;

    // Args: MaxRAMPercentage, forcecoop, expectheap, expectcoop
    checkMaxRAMSize(MaxRAMPercentage - headroomPercentage, false, maxcoopheap - (long)headroom, true);
    checkMaxRAMSize(MaxRAMPercentage + headroomPercentage, false, maxcoopheap + (long)headroom, false);
    checkMaxRAMSize(MaxRAMPercentage, true, maxcoopheap, true);
  }
}
