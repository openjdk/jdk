/*
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
 */

/*
 * @test
 * @bug 8379327
 * @summary Verify x64 backend emits one multiply for low+high 64-bit multiply pattern.
 *
 * @requires vm.compiler2.enabled
 * @requires os.arch=="amd64" | os.arch=="x86_64"
 *
 * @library /test/lib
 * @run driver compiler.c2.TestMultiplyHighLowFusionX64
 */

package compiler.c2;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestMultiplyHighLowFusionX64 {
  private static final Pattern MULHILO = Pattern.compile("#\\s*mulhilo\\b");
  private static final Pattern UMULHILO = Pattern.compile("#\\s*umulhilo\\b");

  public static void main(String[] args) throws Exception {
    verifyMethod("doMath", true);
    verifyMethod("doMathSwapped", true);
    verifyMethod("doUnsignedMath", false);
    verifyMethod("doUnsignedMathSwapped", false);
  }

  private static void verifyMethod(String methodName, boolean signed) throws Exception {
    List<String> command = new ArrayList<>();
    command.add("-XX:+UnlockDiagnosticVMOptions");
    command.add("-XX:-TieredCompilation");
    command.add("-Xbatch");
    command.add("-XX:CompileCommand=compileonly," + Launcher.class.getName() + "::" + methodName);
    command.add("-XX:CompileCommand=print," + Launcher.class.getName() + "::" + methodName);
    command.add(Launcher.class.getName());
    command.add(methodName);

    OutputAnalyzer output = ProcessTools.executeTestJava(command);
    output.shouldHaveExitValue(0);

    int mulCount = countMul(output.getOutput(), signed);
    if (mulCount != 1) {
      throw new RuntimeException("Expected exactly one multiply in " + methodName + ", found " + mulCount +
          "\nFull output:\n" + output.getOutput());
    }
  }

  private static int countMul(String output, boolean signed) {
    String lower = output.toLowerCase(Locale.ROOT);
    Pattern pattern = signed ? MULHILO : UMULHILO;
    int count = 0;
    Matcher matcher = pattern.matcher(lower);
    while (matcher.find()) {
      count++;
    }
    return count;
  }

  static class Launcher {
    static long doMath(long a, long b) {
      long low = a * b;
      long high = Math.multiplyHigh(a, b);
      return low + high;
    }

    static long doMathSwapped(long a, long b) {
      long low = b * a;
      long high = Math.multiplyHigh(b, a);
      return low + high;
    }

    static long doUnsignedMath(long a, long b) {
      long low = a * b;
      long high = Math.unsignedMultiplyHigh(a, b);
      return low + high;
    }

    static long doUnsignedMathSwapped(long a, long b) {
      long low = b * a;
      long high = Math.unsignedMultiplyHigh(b, a);
      return low + high;
    }

    public static void main(String[] args) {
      String mode = args[0];
      long acc = 0;
      long b = 987654321L;
      for (int i = 0; i < 200_000; i++) {
        switch (mode) {
          case "doMath":
            acc += doMath(i, b);
            break;
          case "doMathSwapped":
            acc += doMathSwapped(i, b);
            break;
          case "doUnsignedMath":
            acc += doUnsignedMath(i, b);
            break;
          case "doUnsignedMathSwapped":
            acc += doUnsignedMathSwapped(i, b);
            break;
          default:
            throw new RuntimeException("Unknown mode: " + mode);
        }
      }
      if (acc == 42) {
        System.out.println("Impossible");
      }
    }
  }
}
