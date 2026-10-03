/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test id=generational
 * @summary Test that scanning of the remembered set is well balanced
 * @requires vm.gc.Shenandoah
 * @requires vm.flagless
 * @requires os.maxMemory >= 10g
 * @library /test/lib
 * @run driver/manual TestRemSetBalance
 */

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class TestRemSetBalance {

  private static boolean parse_input(List<String> stdout) {
    Object[] lines = stdout.toArray();
    for (int index = 0; index < lines.length; index++) {
      String line = (String) lines[index];
      int line_no = index + 1;
      String[] tokens = line.split("\\s+");
      if (tokens.length > 8) {
        if (tokens[1].equals("RS:") && tokens[2].equals("Work") && tokens[4].equals("us")
            && tokens[5].equals("total,") && tokens[6].equals("per") && tokens[7].equals("worker:")) {
          int min_work = 0x7fff_ffff;
          int max_work = 0;
          int total_work = 0;
          int num_workers = 0;
          for (int i = 8; i < tokens.length; i++) {
            if (!tokens[i].equals("---,")) {
              num_workers++;
              // Need to strip the "," from end of tokens[i]
              int value = Integer.parseInt(tokens[i].substring(0, tokens[i].length() - 1));
              total_work += value;
              if (value > max_work) {
                max_work = value;
              }
              if (value < min_work) {
                min_work = value;
              }
            }
          }
          if (min_work < 10) {
            if (max_work > 100) {
              System.out.println("Out of balance: max work: " + Integer.toString(max_work) +
                                 " is greater than 100 when min work: " + Integer.toString(min_work) +
                                 " is smaller than 10 at line " + Integer.toString(line_no));
              System.out.println("  line: " + line);
              return false;
            }
          } else {
            int average = total_work / num_workers;
            // Allow deviation from average plus or minus 6.25%)
            int deviation_bound = average / 16;
            if (max_work > average + deviation_bound) {
              System.out.println("Out of balance: max work: " + Integer.toString(max_work) +
                                 " is greater than average work: " + Integer.toString(average) +
                                 " plus bound: " + Integer.toString(deviation_bound) + " at line " +
                                 Integer.toString(line_no));
              System.out.println("  line: " + line);
              return false;
            } else if (min_work < average - deviation_bound) {
              System.out.println("Out of balance: min work: " + Integer.toString(min_work) +
                                 " is less than average work: " + Integer.toString(average) +
                                 " minus bound: " + Integer.toString(deviation_bound) + " at line " +
                                 Integer.toString(line_no));
              System.out.println("  line: " + line);
              return false;
            }
          }
        }
      }
    }
    return true;
  }

  static int counter = 1_000_000_000;

  private static String newString() {
    counter++;
    if (counter >= 1_000_010_000) counter = 1_000_000_000;
    return "str" + counter;
  }

  static final Random r = new Random();
  static Object[] retain;

  // Runs the allocation workload if args[0] matches "test", returning true.  args[1], args[2], and args[3] are assumed
  // to represent integers.
  // Otherwise, returns false.
  static boolean run_workload(String[] args) throws Exception {
    if (args[0].equals("test")) {
      int total = 1000 * Integer.parseInt(args[1]);
      int rewrite = total / 100 * Integer.parseInt(args[2]);
      long overwrites = 1L * total * Integer.parseInt(args[3]);
      if (rewrite <= 0) {
        rewrite = 1;
      }
      retain = new Object[total];
      for (int c = 0; c < total; c++) {
        retain[c] = newString();
      }
      for (long c = 0; c < overwrites; c++) {
        int i = r.nextInt(total) % rewrite;
        retain[i] = newString();
      }
      return true;
    } else {
      return false;
    }
  }

  public static void testRemSet(String... args) throws Exception {
    String[] cmds = Arrays.copyOf(args, args.length + 5);
    cmds[args.length] = TestOldGrowthTriggers.class.getName();
    cmds[args.length + 1] = "test";
    cmds[args.length + 2] = "3000";
    cmds[args.length + 3] = "50";
    cmds[args.length + 4] = "1000";
    ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(cmds);
    OutputAnalyzer output = new OutputAnalyzer(pb.start());
    output.shouldHaveExitValue(0);
    List<String> stdout = output.asLines();
    Asserts.assertTrue(parse_input(stdout), "Log has too much variation in remembered set scan times");
  }

  public static void main(String[] args) throws Exception {
    if (args.length > 0 && args[0].equals("test")) {
      run_workload(args);
      return;
    }

    testRemSet("-Xlog:gc",
               "-Xms8g",
               "-Xmx8g",
               "-XX:+UseShenandoahGC",
               "-XX:ShenandoahGCMode=generational",
               "-Xlog:gc+stats",
               "-XX:+UseCompactObjectHeaders"
               );

    testRemSet("-Xlog:gc",
               "-Xms8g",
               "-Xmx8g",
               "-XX:+UseShenandoahGC",
               "-XX:ShenandoahGCMode=generational",
               "-Xlog:gc+stats",
               "-XX:-UseCompactObjectHeaders"
               );
  }
}
