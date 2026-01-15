/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

package gc.epsilon;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/* @test id=default
 * @requires vm.gc.Epsilon
 * @summary Stress test that allocation path taken in early JVM phases works
 * @library /test/lib
 * @run driver gc.epsilon.TestInitAllocs
 */

/* @test id=nocoops
 * @requires vm.gc.Epsilon
 * @library /test/lib
 * @run driver gc.epsilon.TestInitAllocs -XX:-UseCompressedOops
 */

/* @test id=notlabs
 * @requires vm.gc.Epsilon
 * @library /test/lib
 * @run driver gc.epsilon.TestInitAllocs -XX:-UseTLAB
 */

/* @test id=notlabs-nocoops
 * @requires vm.gc.Epsilon
 * @library /test/lib
 * @run driver gc.epsilon.TestInitAllocs -XX:-UseCompressedOops -XX:-UseTLAB
 */

/* @test id=coh
 * @requires vm.gc.Epsilon
 * @library /test/lib
 * @run driver gc.epsilon.TestInitAllocs -XX:+UseCompactObjectHeaders
 */

/* @test id=coh-nocoops
 * @requires vm.gc.Epsilon
 * @library /test/lib
 * @run driver gc.epsilon.TestInitAllocs -XX:+UseCompactObjectHeaders -XX:-UseCompressedOops
 */

/* @test id=coh-notlabs
 * @requires vm.gc.Epsilon
 * @library /test/lib
 * @run driver gc.epsilon.TestInitAllocs -XX:+UseCompactObjectHeaders -XX:-UseTLAB
 */

/* @test id=coh-notlabs-nocoops
 * @requires vm.gc.Epsilon
 * @library /test/lib
 * @run driver gc.epsilon.TestInitAllocs -XX:+UseCompactObjectHeaders -XX:-UseCompressedOops -XX:-UseTLAB
 */

public class TestInitAllocs {

  static final Integer TRIES = Integer.getInteger("tries", 1000);

  public static void main(String... args) throws Exception {
    List<String> testArgs = new ArrayList<>();
    testArgs.add("-Xmx256m");
    testArgs.add("-XX:+UnlockExperimentalVMOptions");
    testArgs.add("-XX:+UseEpsilonGC");
    testArgs.add("-XX:EpsilonMinHeapExpand=1024");
    testArgs.add("-XX:EpsilonUpdateCountersStep=1");
    testArgs.add("-XX:EpsilonPrintHeapSteps=1000000");
    testArgs.addAll(Arrays.asList(args));
    testArgs.add(TestInitAllocs.Main.class.getName());
    for (int t = 0; t < TRIES; t++) {
      OutputAnalyzer oa = ProcessTools.executeLimitedTestJava(testArgs);
      oa.shouldHaveExitValue(0);
    }
  }

  static class Main {
    public static void main(String... args) {
      System.out.println("Hello World");
    }
  }

}
