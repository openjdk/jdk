/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @key regression
 * @key gc
 * @bug 8027756
 * @library /testlibrary /testlibrary/whitebox
 * @build TestHumongousCodeCacheRoots
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @summary Humongous objects may have references from the code cache
 * @run main TestHumongousCodeCacheRoots
*/

import com.oracle.java.testlibrary.*;
import sun.hotspot.WhiteBox;

import java.util.ArrayList;
import java.util.Arrays;

class TestHumongousCodeCacheRootsHelper {

    static final int n = 1000000;
    static final int[] AA = new int[n];
    static final int[] BB = new int[n];

    public static void main(String args[]) throws Exception {
        // do some work so that the compiler compiles this method, inlining the
        // reference to the integer array (which is a humonguous object) into
        // the code cache.
        for(int i = 0; i < n; i++) {
            AA[i] = 0;
            BB[i] = 0;
        }
        // trigger a GC that checks that the verification code allows humongous
        // objects with code cache roots; objects should be all live here.
        System.gc();

        // deoptimize everyhing: this should make all compiled code zombies.
        WhiteBox wb = WhiteBox.getWhiteBox();
        wb.deoptimizeAll();

        // trigger a GC that checks that the verification code allows humongous
        // objects with code cache roots; objects should be all live here.
        System.gc();

        // wait a little for the code cache sweeper to try to clean up zombie nmethods
        // and unregister the code roots.
        try { Thread.sleep(5000); } catch (InterruptedException ex) { }

        // do some work on the arrays to make sure that they need to be live after the GCs
        for(int i = 0; i < n; i++) {
            AA[i] = 1;
            BB[i] = 10;
        }

        System.out.println();
    }
}

public class TestHumongousCodeCacheRoots {

  /**
   * Executes a class in a new VM process with the given parameters.
   * @param vmargs Arguments to the VM to run
   * @param classname Name of the class to run
   * @param arguments Arguments to the class
   * @param useTestDotJavaDotOpts Use test.java.opts as part of the VM argument string
   * @return The OutputAnalyzer with the results for the invocation.
   */
  public static OutputAnalyzer runWhiteBoxTest(String[] vmargs, String classname, String[] arguments, boolean useTestDotJavaDotOpts) throws Exception {
    ArrayList<String> finalargs = new ArrayList<String>();

    String[] whiteboxOpts = new String[] {
      "-Xbootclasspath/a:.",
      "-XX:+UnlockDiagnosticVMOptions", "-XX:+WhiteBoxAPI",
      "-cp", System.getProperty("java.class.path"),
    };

    if (useTestDotJavaDotOpts) {
      // System.getProperty("test.java.opts") is '' if no options is set,
      // we need to skip such a result
      String[] externalVMOpts = new String[0];
      if (System.getProperty("test.java.opts") != null && System.getProperty("test.java.opts").length() != 0) {
        externalVMOpts = System.getProperty("test.java.opts").split(" ");
      }
      finalargs.addAll(Arrays.asList(externalVMOpts));
    }

    finalargs.addAll(Arrays.asList(vmargs));
    finalargs.addAll(Arrays.asList(whiteboxOpts));
    finalargs.add(classname);
    finalargs.addAll(Arrays.asList(arguments));

    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(finalargs.toArray(new String[0]));
    OutputAnalyzer output = new OutputAnalyzer(pb.start());
    output.shouldHaveExitValue(0);

    return output;
  }

  public static void runTest(String compiler, String[] other) throws Exception {
    ArrayList<String> joined = new ArrayList<String>();
    joined.add(compiler);
    joined.addAll(Arrays.asList(other));
    runWhiteBoxTest(joined.toArray(new String[0]), TestHumongousCodeCacheRootsHelper.class.getName(),
      new String[] {}, false);
  }

  public static void main(String[] args) throws Exception {
    final String[] baseArguments = new String[] {
      "-XX:+UseG1GC", "-XX:G1HeapRegionSize=1M", "-Xmx100M", // make sure we get a humongous region
      "-XX:+UnlockDiagnosticVMOptions",
      "-XX:InitiatingHeapOccupancyPercent=1", // strong code root marking
      "-XX:+G1VerifyHeapRegionCodeRoots", "-XX:+VerifyAfterGC", // make sure that verification is run
      "-XX:NmethodSweepFraction=1", "-XX:NmethodSweepCheckInterval=1",  // make the code cache sweep more predictable
    };
    runTest("-client", baseArguments);
    runTest("-server", baseArguments);
  }
}

