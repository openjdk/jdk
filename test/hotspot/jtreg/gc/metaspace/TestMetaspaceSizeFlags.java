/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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

package gc.metaspace;

import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

/*
 * @test TestMetaspaceSizeFlags
 * @bug 8024650 8395825
 * @requires vm.flagless
 * @summary Test that metaspace size flags can be set correctly
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver gc.metaspace.TestMetaspaceSizeFlags
 */
public class TestMetaspaceSizeFlags {
  public static final long K = 1024L;
  public static final long M = 1024L * K;

  // HotSpot uses a number of different values to align memory size flags.
  // This is currently the largest alignment (unless huge large pages are used).
  public static final long MAX_ALIGNMENT = 32 * M;
  // The default MetaspaceSize is specified in globals.hpp:
  public static final long DEFAULT_METASPACE_SIZE = (Platform.is64bit() ? 21 : 16) * M;
  // If we set a MaxMetaspaceSize value less than the default MetaspaceSize
  // value and then set a MaxMetaspaceSize value higher than the default
  // MetaspaceSize, then MetaspaceSize should be silently adjusted to match
  // the first lower value and then silently adjusted back to the default
  // MetaspaceSize for the second higher value.
  public static final long HIGHER_THAN_DEFAULT = 32 * M;
  // If we set a MaxMetaspaceSize value less than the default MetaspaceSize
  // value, then MetaspaceSize should be silently adjusted to match.
  public static final long LOWER_THAN_DEFAULT1 = 12 * M;
  public static final long LOWER_THAN_DEFAULT2 = 8 * M;

  public static void main(String [] args) throws Exception {
    testMaxMetaspaceSizeEQMetaspaceSize(MAX_ALIGNMENT, MAX_ALIGNMENT);
    // 8024650: MaxMetaspaceSize was adjusted instead of MetaspaceSize.
    // 8395825: Specifying MaxMetaspaceSize < MetaspaceSize, both on the
    //          cmd line is now an error.
    testMaxMetaspaceSizeLTMetaspaceSize(MAX_ALIGNMENT, MAX_ALIGNMENT * 2);
    // 8395825: Test a too small MaxMetaspaceSize and verify an OOM.
    testMaxMetaspaceSizeIsTooSmall(4 * K);
    // 8395825: Specifying MaxMetaspaceSize < default MetaspaceSize on the
    //          cmd line silently lowers MetaspaceSize to MaxMetaspaceSize.
    testMaxMetaspaceSizeLTDefaultMetaspaceSize(LOWER_THAN_DEFAULT1);
    // 8395825: HotSpot has a last value wins policy for repeated flags.
    //          We need to make sure that MaxMetaspaceSize can be lowered
    //          twice and that MetaspaceSize follows.
    testMaxMetaspaceSizeLTDefaultMetaspaceSizeTwice(LOWER_THAN_DEFAULT1,
                                                    LOWER_THAN_DEFAULT2);
    // 8395825: HotSpot has a last value wins policy for repeated flags.
    //          We need to make sure that MaxMetaspaceSize can be lowered
    //          once, raised once (still below default MetaspaceSize), and
    //          that MetaspaceSize follows.
    testMaxMetaspaceSizeLTDefaultMetaspaceSizeTwice(LOWER_THAN_DEFAULT2,
                                                    LOWER_THAN_DEFAULT1);
    // 8395825: HotSpot has a last value wins policy for repeated flags.
    //          We need to make sure that MaxMetaspaceSize can be lowered
    //          once, raised once above default MetaspaceSize and that
    //          MetaspaceSize follows to the first lower value and then
    //          is adjust back to the default MetaspaceSize for the higher
    //          value.
    testMaxMetaspaceSizeLTDefaultMetaspaceSizeTwice(LOWER_THAN_DEFAULT1,
                                                    HIGHER_THAN_DEFAULT);
    testMaxMetaspaceSizeGTMetaspaceSize(MAX_ALIGNMENT * 2, MAX_ALIGNMENT);
  }

  private static void testMaxMetaspaceSizeEQMetaspaceSize(long maxMetaspaceSize, long metaspaceSize) throws Exception {
    MetaspaceFlags mf = runAndGetValue(maxMetaspaceSize, metaspaceSize);
    Asserts.assertEQ(maxMetaspaceSize, metaspaceSize);
    Asserts.assertEQ(mf.maxMetaspaceSize, maxMetaspaceSize);
    Asserts.assertEQ(mf.metaspaceSize, metaspaceSize);
  }

  private static void testMaxMetaspaceSizeLTMetaspaceSize(long maxMetaspaceSize, long metaspaceSize) throws Exception {
    runAndFail(maxMetaspaceSize, metaspaceSize);
  }

  private static void testMaxMetaspaceSizeIsTooSmall(long maxMetaspaceSize) throws Exception {
    runAndFailWithOOM(maxMetaspaceSize);
  }

  private static void testMaxMetaspaceSizeLTDefaultMetaspaceSize(long maxMetaspaceSize) throws Exception {
    MetaspaceFlags mf = runAndGetValue(maxMetaspaceSize);
    Asserts.assertEQ(maxMetaspaceSize, mf.maxMetaspaceSize);
    Asserts.assertEQ(maxMetaspaceSize, mf.metaspaceSize);
  }

  private static void testMaxMetaspaceSizeLTDefaultMetaspaceSizeTwice(long maxMetaspaceSize1, long maxMetaspaceSize2) throws Exception {
    MetaspaceFlags mf = runWithTwoMaxAndGetValue(maxMetaspaceSize1,
                                                 maxMetaspaceSize2);
    Asserts.assertEQ(maxMetaspaceSize2, mf.maxMetaspaceSize);
    if (maxMetaspaceSize2 != HIGHER_THAN_DEFAULT) {
      Asserts.assertEQ(maxMetaspaceSize2, mf.metaspaceSize);
    } else {
      Asserts.assertEQ(DEFAULT_METASPACE_SIZE, mf.metaspaceSize);
    }
  }

  private static void testMaxMetaspaceSizeGTMetaspaceSize(long maxMetaspaceSize, long metaspaceSize) throws Exception {
    MetaspaceFlags mf = runAndGetValue(maxMetaspaceSize, metaspaceSize);
    Asserts.assertGT(maxMetaspaceSize, metaspaceSize);
    Asserts.assertGT(mf.maxMetaspaceSize, mf.metaspaceSize);
    Asserts.assertEQ(mf.maxMetaspaceSize, maxMetaspaceSize);
    Asserts.assertEQ(mf.metaspaceSize, metaspaceSize);
  }

  private static void runAndFail(long maxMetaspaceSize, long metaspaceSize) throws Exception {
    OutputAnalyzer output = run(maxMetaspaceSize, metaspaceSize);
    output.shouldContain("MetaspaceSize (" + metaspaceSize +
                         ") must be less than or equal to MaxMetaspaceSize (" +
                         maxMetaspaceSize + ")");
  }

  private static void runAndFailWithOOM(long maxMetaspaceSize) throws Exception {
    OutputAnalyzer output = runWithHeapDumpOnOOM(maxMetaspaceSize);
    output.shouldContain("java.lang.OutOfMemoryError: Metaspace");

    // The original test case for 8395825 is this java cmd:
    //   java -XX:MaxMetaspaceSize=0 -XX:+HeapDumpOnOutOfMemoryError -version
    // We try to reproduce that failure mode with this java cmd:
    //   java -XX:MaxMetaspaceSize=4k -XX:+HeapDumpOnOutOfMemoryError -version
    System.err.println("START 8395825 DEBUG OUTPUT");
    output.reportDiagnosticSummary();
    System.err.println("END 8395825 DEBUG OUTPUT");
  }

  private static MetaspaceFlags runAndGetValue(long maxMetaspaceSize) throws Exception {
    OutputAnalyzer output = run(maxMetaspaceSize);
    output.shouldNotMatch("Error occurred during initialization of VM\n.*");

    String stringMaxMetaspaceSize = output.firstMatch(".* MaxMetaspaceSize .* = (\\d+).*", 1);
    String stringMetaspaceSize = output.firstMatch(".* MetaspaceSize .* = (\\d+).*", 1);

    return new MetaspaceFlags(Long.parseLong(stringMaxMetaspaceSize),
                              Long.parseLong(stringMetaspaceSize));
  }

  private static MetaspaceFlags runAndGetValue(long maxMetaspaceSize, long metaspaceSize) throws Exception {
    OutputAnalyzer output = run(maxMetaspaceSize, metaspaceSize);
    output.shouldNotMatch("Error occurred during initialization of VM\n.*");

    String stringMaxMetaspaceSize = output.firstMatch(".* MaxMetaspaceSize .* = (\\d+).*", 1);
    String stringMetaspaceSize = output.firstMatch(".* MetaspaceSize .* = (\\d+).*", 1);

    return new MetaspaceFlags(Long.parseLong(stringMaxMetaspaceSize),
                              Long.parseLong(stringMetaspaceSize));
  }

  private static MetaspaceFlags runWithTwoMaxAndGetValue(long maxMetaspaceSize1, long maxMetaspaceSize2) throws Exception {
    OutputAnalyzer output = runWithTwoMax(maxMetaspaceSize1, maxMetaspaceSize2);
    output.shouldNotMatch("Error occurred during initialization of VM\n.*");

    String stringMaxMetaspaceSize = output.firstMatch(".* MaxMetaspaceSize .* = (\\d+).*", 1);
    String stringMetaspaceSize = output.firstMatch(".* MetaspaceSize .* = (\\d+).*", 1);

    return new MetaspaceFlags(Long.parseLong(stringMaxMetaspaceSize),
                              Long.parseLong(stringMetaspaceSize));
  }

  private static OutputAnalyzer runWithHeapDumpOnOOM(long maxMetaspaceSize) throws Exception {
    return ProcessTools.executeLimitedTestJava(
        "-XX:MaxMetaspaceSize=" + maxMetaspaceSize,
        "-XX:-UseLargePages", // Prevent us from using 2GB large pages on solaris + sparc.
        "-XX:+HeapDumpOnOutOfMemoryError",
        "-version");
  }

  private static OutputAnalyzer run(long maxMetaspaceSize) throws Exception {
    return ProcessTools.executeLimitedTestJava(
        "-XX:MaxMetaspaceSize=" + maxMetaspaceSize,
        "-XX:-UseLargePages", // Prevent us from using 2GB large pages on solaris + sparc.
        "-XX:+PrintFlagsFinal",
        "-version");
  }

  private static OutputAnalyzer run(long maxMetaspaceSize, long metaspaceSize) throws Exception {
    return ProcessTools.executeLimitedTestJava(
        "-XX:MaxMetaspaceSize=" + maxMetaspaceSize,
        "-XX:MetaspaceSize=" + metaspaceSize,
        "-XX:-UseLargePages", // Prevent us from using 2GB large pages on solaris + sparc.
        "-XX:+PrintFlagsFinal",
        "-version");
  }

  private static OutputAnalyzer runWithTwoMax(long maxMetaspaceSize1, long maxMetaspaceSize2) throws Exception {
    return ProcessTools.executeLimitedTestJava(
        "-XX:MaxMetaspaceSize=" + maxMetaspaceSize1,
        "-XX:MaxMetaspaceSize=" + maxMetaspaceSize2,
        "-XX:-UseLargePages", // Prevent us from using 2GB large pages on solaris + sparc.
        "-XX:+PrintFlagsFinal",
        "-version");
  }

  private static class MetaspaceFlags {
    public long maxMetaspaceSize;
    public long metaspaceSize;
    public MetaspaceFlags(long maxMetaspaceSize, long metaspaceSize) {
      this.maxMetaspaceSize = maxMetaspaceSize;
      this.metaspaceSize = metaspaceSize;
    }
  }
}
