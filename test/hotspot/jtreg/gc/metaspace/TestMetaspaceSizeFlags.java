/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;
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
  // The minimum MaxMetaspaceSize is specified in globals.hpp:
  public static final long MIN_MAXMETASPACE_SIZE = 128L * K;
  // If we set a MaxMetaspaceSize value less than the default MetaspaceSize
  // value and then set a MaxMetaspaceSize value higher than the default
  // MetaspaceSize, then MetaspaceSize should be unaffected and remain at
  // the default MetaspaceSize.
  public static final long HIGHER_THAN_DEFAULT = 32 * M;
  // If we set a MaxMetaspaceSize value less than the default MetaspaceSize
  // value, then MetaspaceSize should be silently adjusted to match.
  public static final long LOWER_THAN_DEFAULT1 = 12 * M;
  public static final long LOWER_THAN_DEFAULT2 = 8 * M;

  public static void main(String [] args) throws Exception {
    testEQFlagValues(MAX_ALIGNMENT);
    // 8024650: MaxMetaspaceSize was adjusted instead of MetaspaceSize.
    // Specifying MaxMetaspaceSize < MetaspaceSize, both on the
    // cmd line in either order is a mis-configuration.
    testMaxHalfFlagValues(MAX_ALIGNMENT);
    // Specifying MaxMetaspaceSize 2X MetaspaceSize, both on the
    // cmd line in either order is just fine.
    testMax2XFlagValues(MAX_ALIGNMENT);
    // Test MaxMetaspaceSize=0 and verify a range failure.
    testMaxMetaspaceSizeIsOutOfRange(0);
    // Test minimum MaxMetaspaceSize value for both flags in either
    // order.
    testEQFlagValues(MIN_MAXMETASPACE_SIZE);
    // Test MaxMetaspaceSize just below minimum and verify a range failure.
    testMaxMetaspaceSizeIsOutOfRange(MIN_MAXMETASPACE_SIZE - 1);
    // Testing a too small MaxMetaspaceSize and verifying an OOME
    // is done by runtime/Metaspace/MaxMetaspaceSizeTest.java so
    // we don't repeat that test here.
    //
    // Specifying MaxMetaspaceSize < default MetaspaceSize on the
    // cmd line silently lowers MetaspaceSize to MaxMetaspaceSize.
    testMaxMetaspaceSizeLTDefaultMetaspaceSize(LOWER_THAN_DEFAULT1);
    // HotSpot has a last value wins policy for repeated flags.
    // We need to make sure that MaxMetaspaceSize can be lowered
    // twice and that MetaspaceSize follows.
    testMaxMetaspaceSizeLTDefaultMetaspaceSizeTwice(LOWER_THAN_DEFAULT1,
                                                    LOWER_THAN_DEFAULT2);
    // We need to make sure that MaxMetaspaceSize can be lowered once,
    // raised once (still below default MetaspaceSize), and that the
    // resulting MetaspaceSize matches the second MaxMetaspaceSize value.
    testMaxMetaspaceSizeLTDefaultMetaspaceSizeTwice(LOWER_THAN_DEFAULT2,
                                                    LOWER_THAN_DEFAULT1);
    // We need to make sure that MaxMetaspaceSize can be lowered
    // once, raised once above default MetaspaceSize and that the
    // resulting MetaspaceSize matches the default MetaspaceSize.
    testMaxMetaspaceSizeLTDefaultMetaspaceSizeTwice(LOWER_THAN_DEFAULT1,
                                                    HIGHER_THAN_DEFAULT);
  }

  // Test equal flag values for MetaspaceSize and MaxMetaspaceSize
  // in both flag orders.
  private static void testEQFlagValues(long value) throws Exception {
    List<String> command = new ArrayList<String>();
    command.addFirst("-XX:MetaspaceSize=" + value);
    command.addLast("-XX:MaxMetaspaceSize=" + value);
    MetaspaceFlags mf = runAndGetValue(command);
    Asserts.assertEQ(value, mf.maxMetaspaceSize);
    Asserts.assertEQ(value, mf.metaspaceSize);

    // Swap the flag order and test again:
    command = new ArrayList<String>();
    command.addFirst("-XX:MaxMetaspaceSize=" + value);
    command.addLast("-XX:MetaspaceSize=" + value);
    mf = runAndGetValue(command);
    Asserts.assertEQ(value, mf.maxMetaspaceSize);
    Asserts.assertEQ(value, mf.metaspaceSize);
  }

  // Test MaxMetaspaceSize Half of MetaspaceSize in both flag orders.
  private static void testMaxHalfFlagValues(long value) throws Exception {
    List<String> command = new ArrayList<String>();
    command.addFirst("-XX:MetaspaceSize=" + value * 2);
    command.addLast("-XX:MaxMetaspaceSize=" + value);
    // With MetaspaceSize specified first, we'll fail when we try to
    // specify MaxMetaspaceSize with half the value.
    runAndFailWithBadOption(command);

    // Swap the flag order and test again:
    command = new ArrayList<String>();
    command.addFirst("-XX:MaxMetaspaceSize=" + value);
    command.addLast("-XX:MetaspaceSize=" + value * 2);
    // With MaxMetaspaceSize specified first, we'll fail when we try to
    // specify MetaspaceSize with 2X the value.
    runAndFailWithBadOption(command);
  }

  // Test MaxMetaspaceSize 2X MetaspaceSize in both flag orders.
  private static void testMax2XFlagValues(long value) throws Exception {
    List<String> command = new ArrayList<String>();
    command.addFirst("-XX:MetaspaceSize=" + value);
    command.addLast("-XX:MaxMetaspaceSize=" + value * 2);
    MetaspaceFlags mf = runAndGetValue(command);
    Asserts.assertEQ(value * 2, mf.maxMetaspaceSize);
    Asserts.assertEQ(value, mf.metaspaceSize);

    // Swap the flag order and test again:
    command = new ArrayList<String>();
    command.addFirst("-XX:MaxMetaspaceSize=" + value * 2);
    command.addLast("-XX:MetaspaceSize=" + value);
    mf = runAndGetValue(command);
    Asserts.assertEQ(value * 2, mf.maxMetaspaceSize);
    Asserts.assertEQ(value, mf.metaspaceSize);
  }

  private static void testMaxMetaspaceSizeIsOutOfRange(long value) throws Exception {
    List<String> command = new ArrayList<String>();
    command.addFirst("-XX:MaxMetaspaceSize=" + value);
    runAndFailWithRangeFailure(command, value);
  }

  private static void testMaxMetaspaceSizeLTDefaultMetaspaceSize(long maxMetaspaceSize) throws Exception {
    List<String> command = new ArrayList<String>();
    command.addFirst("-XX:MaxMetaspaceSize=" + maxMetaspaceSize);
    MetaspaceFlags mf = runAndGetValue(command);
    Asserts.assertEQ(maxMetaspaceSize, mf.maxMetaspaceSize);
    Asserts.assertEQ(maxMetaspaceSize, mf.metaspaceSize);
  }

  private static void testMaxMetaspaceSizeLTDefaultMetaspaceSizeTwice(long maxMetaspaceSize1, long maxMetaspaceSize2) throws Exception {
    List<String> command = new ArrayList<String>();
    command.addFirst("-XX:MaxMetaspaceSize=" + maxMetaspaceSize1);
    command.addLast("-XX:MaxMetaspaceSize=" + maxMetaspaceSize2);
    MetaspaceFlags mf = runAndGetValue(command);
    Asserts.assertEQ(maxMetaspaceSize2, mf.maxMetaspaceSize);
    if (maxMetaspaceSize2 != HIGHER_THAN_DEFAULT) {
      Asserts.assertEQ(maxMetaspaceSize2, mf.metaspaceSize);
    } else {
      Asserts.assertEQ(DEFAULT_METASPACE_SIZE, mf.metaspaceSize);
    }
  }

  private static void runAndFailWithBadOption(List<String> command) throws Exception {
    OutputAnalyzer output = run(command);
    // We check for the common part to both error paths:
    output.shouldContain("Improperly specified VM option ");
    output.shouldContain("Error: Could not create the Java Virtual Machine.");
    output.shouldNotHaveExitValue(0);
    // With MetaspaceSize specified first, we'll see this:
    // MaxMetaspaceSize (33554432) must be greater than or equal to MetaspaceSize (67108864)
    // Improperly specified VM option 'MaxMetaspaceSize=33554432'
    //
    // With MaxMetaspaceSize specified first, we'll see this:
    // MetaspaceSize (67108864) must be less than or equal to MaxMetaspaceSize (33554432)
    // Improperly specified VM option 'MetaspaceSize=67108864'
  }

  private static void runAndFailWithRangeFailure(List<String> command, long value) throws Exception {
    OutputAnalyzer output = run(command);
    output.shouldContain("size_t MaxMetaspaceSize=" + value + " is outside the allowed range");
    output.shouldContain("Error: Could not create the Java Virtual Machine.");
    output.shouldNotHaveExitValue(0);
  }

  private static MetaspaceFlags runAndGetValue(List<String> command) throws Exception {
    OutputAnalyzer output = run(command);
    output.shouldNotMatch("java.lang.OutOfMemoryError: Metaspace\n.*");
    output.shouldNotMatch("Error occurred during initialization of VM\n.*");
    output.shouldNotMatch("Error: Could not create the Java Virtual Machine.\n.*");
    output.shouldHaveExitValue(0);

    String stringMaxMetaspaceSize = output.firstMatch(".* MaxMetaspaceSize .* = (\\d+).*", 1);
    String stringMetaspaceSize = output.firstMatch(".* MetaspaceSize .* = (\\d+).*", 1);

    return new MetaspaceFlags(Long.parseLong(stringMaxMetaspaceSize),
                              Long.parseLong(stringMetaspaceSize));
  }

  private static OutputAnalyzer run(List<String> command) throws Exception {
    // 'command' already contains the flag value(s) that are specific to
    // the test case so now we add the common flags and run the test:
    command.addLast("-XX:-UseLargePages");  // Prevent us from using 2GB large pages on solaris + sparc.
    command.addLast("-XX:+PrintFlagsFinal");
    command.addLast("-version");
    return ProcessTools.executeLimitedTestJava(command);
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
