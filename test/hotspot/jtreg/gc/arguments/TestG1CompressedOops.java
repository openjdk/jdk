/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (C) 2025 THL A29 Limited, a Tencent company. All rights reserved.
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
 * @test TestG1CompressedOops
 * @bug 8354145
 * @requires vm.flagless
 * @summary Verify that the flag UseCompressedOops is updated properly
 * @library /test/lib
 * @library /
 * @run driver gc.arguments.TestG1CompressedOops
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.ArrayList;
import java.util.Arrays;

import jdk.test.lib.process.OutputAnalyzer;

public class TestG1CompressedOops {

  private static void checkG1CompressedOops(String[] flags, boolean expectedValue, int exitValue) throws Exception {
    ArrayList<String> flagList = new ArrayList<String>();
    flagList.addAll(Arrays.asList(flags));
    flagList.add("-XX:+UseG1GC");
    flagList.add("-XX:+PrintFlagsFinal");
    flagList.add("-version");

    OutputAnalyzer output = GCArguments.executeTestJava(flagList);
    output.shouldHaveExitValue(exitValue);

    if (exitValue == 0) {
      String stdout = output.getStdout();
      boolean flagValue = getFlagValue("UseCompressedOops", stdout);
      if (flagValue != expectedValue) {
        throw new RuntimeException("Wrong value for UseCompressedOops. Expected " + expectedValue + " but got " + flagValue);
      }
    }
  }

  private static boolean getFlagValue(String flag, String where) {
      Matcher m = Pattern.compile(flag + "\\s+:?=\\s+\\D+").matcher(where);
      if (!m.find()) {
          throw new RuntimeException("Could not find value for flag " + flag + " in output string");
      }
      String match = m.group();
      return match.contains("true");
  }

  public static void main(String args[]) throws Exception {
    checkG1CompressedOops(new String[] { "-Xmx64m"   /* default is 1m */        }, true, 0);
    checkG1CompressedOops(new String[] { "-Xmx64m",  "-XX:G1HeapRegionSize=2m"  }, true, 0);
    checkG1CompressedOops(new String[] { "-Xmx32768m" /* 32g will turn off the usecompressedoops */  }, false, 0);
    checkG1CompressedOops(new String[] { "-Xmx32760m" }, false, 0);
    checkG1CompressedOops(new String[] { "-Xmx32736m", /* 32g - 32m will turn on the usecomppressedoops */ }, true, 0);

    // if set G1HeapRegionSize explicitly with -Xmx32736m will turn off the UseCompressedOops
    checkG1CompressedOops(new String[] { "-Xmx32736m", "-XX:G1HeapRegionSize=1m" }, false, 0);
    checkG1CompressedOops(new String[] { "-Xmx32256m", "-XX:G1HeapRegionSize=512m" }, true, 0);
  }
}
