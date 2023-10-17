/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test DoubleFlagWithIntegerValue
 * @bug 8178364
 * @summary Command-line flags of type double should accept integer values
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver DoubleFlagWithIntegerValue
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class DoubleFlagWithIntegerValue {
  public static void testDoubleFlagWithValue(String flag, String value) throws Exception {
    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(flag + "=" + value, "-version");
    OutputAnalyzer output = new OutputAnalyzer(pb.start());
    output.shouldNotContain("Improperly specified VM option");
    output.shouldHaveExitValue(0);
  }

  public static void main(String[] args) throws Exception {
    // Test double format for -XX:SweeperThreshold
    testDoubleFlagWithValue("-XX:SweeperThreshold", "10.0");

    // Test integer format -XX:SweeperThreshold
    testDoubleFlagWithValue("-XX:SweeperThreshold", "10");

    // Test double format for -XX:SafepointTimeoutDelay
    testDoubleFlagWithValue("-XX:SafepointTimeoutDelay", "5.0");

    // Test integer format -XX:SafepointTimeoutDelay
    testDoubleFlagWithValue("-XX:SafepointTimeoutDelay", "5");
  }
}
