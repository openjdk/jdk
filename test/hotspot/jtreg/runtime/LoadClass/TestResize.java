/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8184765
 * @summary make sure the SystemDictionary gets resized when load factor is too high
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @compile TriggerResize.java
 * @requires (vm.debug == true)
 * @run driver TestResize
 */

import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.Process;
import java.lang.ProcessBuilder;
import java.util.Scanner;

public class TestResize {

  static double MAX_LOAD_FACTOR = 5.0; // see _resize_load_trigger in dictionary.cpp

  static int getInt(String string) {
    int start = 0;
    for (int i = 0; i < string.length(); i++) {
      if (!Character.isDigit(string.charAt(i))) {
        start++;
      } else {
        break;
      }
    }
    int end = start;
    for (int i = end; i < string.length(); i++) {
      if (Character.isDigit(string.charAt(i))) {
        end++;
      } else {
        break;
      }
    }
    return Integer.parseInt(string.substring(start, end));
  }

  static void analyzeOutputOn(ProcessBuilder pb) throws Exception {
    pb.redirectErrorStream(true);
    Process process = pb.start();
    BufferedReader rd = new BufferedReader(new InputStreamReader(process.getInputStream()));
    String line = rd.readLine();
    while (line != null) {
      if (line.startsWith("Java dictionary (")) {
        // ex. "Java dictionary (table_size=107, classes=6)"
        // ex. "Java dictionary (table_size=20201, classes=50002)"
        Scanner scanner = new Scanner(line);
        scanner.next();
        scanner.next();
        int table_size = getInt(scanner.next());
        int classes = getInt(scanner.next());
        scanner.close();

        double loadFactor = (double)classes / (double)table_size;
        if (loadFactor > MAX_LOAD_FACTOR) {
          throw new RuntimeException("Load factor too high, expected MAX "+MAX_LOAD_FACTOR+", got "+loadFactor);
        } else {
          System.out.println("PASS table_size:"+table_size+", classes:"+classes+" OK");
        }
      }
      line = rd.readLine();
    }
    int retval = process.waitFor();
    if (retval != 0) {
      throw new RuntimeException("Error: test returned non-zero value");
    }
  }

  public static void main(String[] args) throws Exception {
    if (Platform.isDebugBuild()) {
      ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-XX:+PrintSystemDictionaryAtExit",
                                                                "TriggerResize",
                                                                "50000");
      analyzeOutputOn(pb);
    }
  }
}
