/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test TestLargePagesFlags
 * @summary Tests how large pages are chosen depending on the given large pages flag combinations.
 * @requires vm.gc != "Z"
 * @requires os.family == "linux"
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver TestLargePagesFlags
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;

import java.util.ArrayList;
import java.util.Arrays;

public class TestLargePagesFlags {

  public static void main(String [] args) throws Exception {
    testUseTransparentHugePages();
  }

  public static void testUseTransparentHugePages() throws Exception {
    if (!canUse(UseTransparentHugePages(true))) {
      System.out.println("Skipping testUseTransparentHugePages");
      return;
    }

    // -XX:-UseLargePages overrides all other flags.
    new FlagTester()
      .use(UseLargePages(false),
           UseTransparentHugePages(true))
      .expect(
           UseLargePages(false),
           UseTransparentHugePages(false));

    // Explicitly turn on UseTransparentHugePages.
    new FlagTester()
      .use(UseTransparentHugePages(true))
      .expect(
           UseLargePages(true),
           UseTransparentHugePages(true));

    new FlagTester()
      .use(UseLargePages(true),
           UseTransparentHugePages(true))
      .expect(
           UseLargePages(true),
           UseTransparentHugePages(true));

    // Setting a specific large pages flag will turn
    // off heuristics to choose large pages type.
    new FlagTester()
      .use(UseLargePages(true),
           UseTransparentHugePages(false))
      .expect(
           UseLargePages(false),
           UseTransparentHugePages(false));

    // Don't turn on UseTransparentHugePages
    // unless the user explicitly asks for them.
    new FlagTester()
      .use(UseLargePages(true))
      .expect(
           UseTransparentHugePages(false));
  }

  private static class FlagTester {
    private Flag [] useFlags;

    public FlagTester use(Flag... useFlags) {
      this.useFlags = useFlags;
      return this;
    }

    public void expect(Flag... expectedFlags) throws Exception {
      if (useFlags == null) {
        throw new IllegalStateException("Must run use() before expect()");
      }

      System.out.println("Using: " + Arrays.toString(useFlags));
      System.out.println("Expecting: " + Arrays.toString(expectedFlags));

      OutputAnalyzer output = executeNewJVM(useFlags);
      output.reportDiagnosticSummary();

      for (Flag flag : expectedFlags) {
        System.out.println("Looking for: " + flag.flagString());
        String strValue = output.firstMatch(".* " + flag.name() +  " .* :?= (\\S+).*", 1);

        if (strValue == null) {
          throw new RuntimeException("Flag " + flag.name() + " couldn't be found");
        }

        if (!flag.value().equals(strValue)) {
          throw new RuntimeException("Wrong value for: " + flag.name()
                                     + " expected: " + flag.value()
                                     + " got: " + strValue);
        }
      }

      output.shouldHaveExitValue(0);
    }
  }

  private static OutputAnalyzer executeNewJVM(Flag... flags) throws Exception {
    ArrayList<String> args = new ArrayList<>();
    for (Flag flag : flags) {
      args.add(flag.flagString());
    }
    args.add("-Xlog:pagesize");
    args.add("-version");

    ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(args);
    OutputAnalyzer output = new OutputAnalyzer(pb.start());
    output.shouldHaveExitValue(0);

    return output;
  }

  private static boolean canUse(Flag flag) {
    try {
      new FlagTester().use(flag).expect(flag);
    } catch (Exception e) {
      return false;
    }

    return true;
  }

  private static Flag UseLargePages(boolean value) {
    return new BooleanFlag("UseLargePages", value);
  }

  private static Flag UseTransparentHugePages(boolean value) {
    return new BooleanFlag("UseTransparentHugePages", value);
  }

  private static class BooleanFlag implements Flag {
    private String name;
    private boolean value;

    BooleanFlag(String name, boolean value) {
      this.name = name;
      this.value = value;
    }

    public String flagString() {
      return "-XX:" + (value ? "+" : "-") + name;
    }

    public String name() {
      return name;
    }

    public String value() {
      return Boolean.toString(value);
    }

    @Override
    public String toString() { return flagString(); }
  }

  private static interface Flag {
    public String flagString();
    public String name();
    public String value();
  }
}
