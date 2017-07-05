/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @test Test8004741.java
 * @bug 8004741
 * @summary Missing compiled exception handle table entry for multidimensional array allocation
 * @run main/othervm -Xmx64m -Xbatch -XX:+IgnoreUnrecognizedVMOptions -XX:-TieredCompilation -XX:+StressCompiledExceptionHandlers Test8004741
 *
 */

import java.util.*;

public class Test8004741 extends Thread {

  static int[][] test(int a, int b) throws Exception {
    int[][] ar = null;
    try {
      ar = new int[a][b];
    } catch (Error e) {
      System.out.println("test got Error");
      passed = true;
      throw(e);
    } catch (Exception e) {
      System.out.println("test got Exception");
      throw(e);
    }
    return ar;
  }

  static boolean passed = false;

  public void run() {
      System.out.println("test started");
      try {
        while(true) {
          test(2,20000);
        }
      } catch (ThreadDeath e) {
        System.out.println("test got ThreadDeath");
        passed = true;
      } catch (Error e) {
        e.printStackTrace();
        System.out.println("test got Error");
      } catch (Exception e) {
        e.printStackTrace();
        System.out.println("test got Exception");
      }
  }

  public static void main(String[] args) throws Exception {
    for (int n = 0; n < 11000; n++) {
      test(2, 20);
    }

    // First test exception catch
    Test8004741 t = new Test8004741();

    passed = false;
    t.start();
    Thread.sleep(1000);
    t.stop();

    Thread.sleep(5000);
    t.join();
    if (passed) {
      System.out.println("PASSED");
    } else {
      System.out.println("FAILED");
      System.exit(97);
    }
  }

};
