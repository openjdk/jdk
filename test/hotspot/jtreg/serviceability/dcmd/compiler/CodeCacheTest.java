/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @test CodeCacheTest
 * @bug 8054889
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 *          java.management
 *          jdk.internal.jvmstat/sun.jvmstat.monitor
 * @run testng/othervm -XX:+SegmentedCodeCache CodeCacheTest
 * @run testng/othervm -XX:-SegmentedCodeCache CodeCacheTest
 * @run testng/othervm -Xint -XX:+SegmentedCodeCache CodeCacheTest
 * @summary Test of diagnostic command Compiler.codecache
 */

import org.testng.annotations.Test;
import org.testng.Assert;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.dcmd.CommandExecutor;
import jdk.test.lib.dcmd.JMXExecutor;

import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeCacheTest {

    /**
     * This test calls Jcmd (diagnostic command tool) Compiler.codecache and then parses the output,
     * making sure that all numbers look ok
     *
     *
     * Expected output without code cache segmentation:
     *
     * CodeCache: size=245760Kb used=1366Kb max_used=1935Kb free=244393Kb
     *  bounds [0x00007ff4d89f2000, 0x00007ff4d8c62000, 0x00007ff4e79f2000]
     *  total_blobs=474, nmethods=87, adapters=293, full_count=0
     * Compilation: enabled, stopped_count=0, restarted_count=0
     *
     * Expected output with code cache segmentation (number of segments may change):
     *
     * CodeHeap 'non-profiled nmethods': size=118592Kb used=29Kb max_used=29Kb free=118562Kb
     *  bounds [0x00007f09f8622000, 0x00007f09f8892000, 0x00007f09ff9f2000]
     * CodeHeap 'profiled nmethods': size=118588Kb used=80Kb max_used=80Kb free=118507Kb
     *  bounds [0x00007f09f09f2000, 0x00007f09f0c62000, 0x00007f09f7dc1000]
     * CodeHeap 'non-nmethods': size=8580Kb used=1257Kb max_used=1833Kb free=7323Kb
     *  bounds [0x00007f09f7dc1000, 0x00007f09f8031000, 0x00007f09f8622000]
     * CodeCache: size=245760Kb, used=1366Kb, max_used=1942Kb, free=244392Kb
     *  total_blobs=474, nmethods=87, adapters=293, full_count=0
     * Compilation: enabled, stopped_count=0, restarted_count=0
     */

    static Pattern line1 = Pattern.compile("(CodeCache|CodeHeap.*): size=(\\p{Digit}*)Kb used=(\\p{Digit}*)Kb max_used=(\\p{Digit}*)Kb free=(\\p{Digit}*)Kb");
    static Pattern line2 = Pattern.compile(" bounds \\[0x(\\p{XDigit}*), 0x(\\p{XDigit}*), 0x(\\p{XDigit}*)\\]");
    static Pattern line3 = Pattern.compile(" total_blobs=(\\p{Digit}*), nmethods=(\\p{Digit}*), adapters=(\\p{Digit}*), full_count=(\\p{Digit}*)");
    static Pattern line4 = Pattern.compile("Compilation: (.*?), stopped_count=(\\p{Digit}*), restarted_count=(\\p{Digit}*)");

    private static boolean getFlagBool(String flag, String where) {
      Matcher m = Pattern.compile(flag + "\\s+:?= (true|false)").matcher(where);
      if (!m.find()) {
        Assert.fail("Could not find value for flag " + flag + " in output string");
      }
      return m.group(1).equals("true");
    }

    private static int getFlagInt(String flag, String where) {
      Matcher m = Pattern.compile(flag + "\\s+:?=\\s+\\d+").matcher(where);
      if (!m.find()) {
        Assert.fail("Could not find value for flag " + flag + " in output string");
      }
      String match = m.group();
      return Integer.parseInt(match.substring(match.lastIndexOf(" ") + 1, match.length()));
    }

    public void run(CommandExecutor executor) {
        // Get number of code cache segments
        int segmentsCount = 0;
        String flags = executor.execute("VM.flags -all").getOutput();
        if (!getFlagBool("SegmentedCodeCache", flags) || !getFlagBool("UseCompiler", flags)) {
          // No segmentation
          segmentsCount = 1;
        } else if (getFlagBool("TieredCompilation", flags) && getFlagInt("TieredStopAtLevel", flags) > 1) {
          // Tiered compilation: use all segments
          segmentsCount = 3;
        } else {
          // No TieredCompilation: only non-nmethod and non-profiled segment
          segmentsCount = 2;
        }

        // Get output from dcmd (diagnostic command)
        OutputAnalyzer output = executor.execute("Compiler.codecache");
        Iterator<String> lines = output.asLines().iterator();

        // Validate code cache segments
        String line;
        Matcher m;
        int matchedCount = 0;
        while (true) {
          // Validate first line
          line = lines.next();
          m = line1.matcher(line);
          if (m.matches()) {
              for (int i = 2; i <= 5; i++) {
                  int val = Integer.parseInt(m.group(i));
                  if (val < 0) {
                      Assert.fail("Failed parsing dcmd codecache output");
                  }
              }
          } else {
              break;
          }

          // Validate second line
          line = lines.next();
          m = line2.matcher(line);
          if (m.matches()) {
              String start = m.group(1);
              String mark  = m.group(2);
              String top   = m.group(3);

              // Lexical compare of hex numbers to check that they look sane.
              if (start.compareTo(mark) > 1) {
                  Assert.fail("Failed parsing dcmd codecache output");
              }
              if (mark.compareTo(top) > 1) {
                  Assert.fail("Failed parsing dcmd codecache output");
              }
          } else {
              Assert.fail("Regexp 2 failed to match line: " + line);
          }
          ++matchedCount;
        }
        // Because of CodeCacheExtensions, we could match more than expected
        if (matchedCount < segmentsCount) {
            Assert.fail("Fewer segments matched (" + matchedCount + ") than expected (" + segmentsCount + ")");
        }

        if (segmentsCount != 1) {
            // Skip this line CodeCache: size=245760Kb, used=5698Kb, max_used=5735Kb, free=240059Kb
            line = lines.next();
        }
        // Validate third line
        m = line3.matcher(line);
        if (m.matches()) {
            int blobs = Integer.parseInt(m.group(1));
            if (blobs <= 0) {
                Assert.fail("Failed parsing dcmd codecache output");
            }
            int nmethods = Integer.parseInt(m.group(2));
            if (nmethods < 0) {
                Assert.fail("Failed parsing dcmd codecache output");
            }
            int adapters = Integer.parseInt(m.group(3));
            if (adapters <= 0) {
                Assert.fail("Failed parsing dcmd codecache output");
            }
            if (blobs < (nmethods + adapters)) {
                Assert.fail("Failed parsing dcmd codecache output");
            }
        } else {
            Assert.fail("Regexp 3 failed to match line: " + line);
        }

        // Validate fourth line
        line = lines.next();
        m = line4.matcher(line);
        if (m.matches()) {
            if (!m.group(1).contains("enabled") && !m.group(1).contains("disabled")) {
                Assert.fail("Failed parsing dcmd codecache output");
            }
            int stopped = Integer.parseInt(m.group(2));
            if (stopped < 0) {
                Assert.fail("Failed parsing dcmd codecache output");
            }
            int restarted = Integer.parseInt(m.group(3));
            if (restarted < 0) {
                Assert.fail("Failed parsing dcmd codecache output");
            }
        } else {
            Assert.fail("Regexp 4 failed to match line: " + line);
        }
    }

    @Test
    public void jmx() {
        run(new JMXExecutor());
    }
}
