/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8276455
 * @summary Test C2 iterative Escape Analysis
 * @library /test/lib /
 *
 * @requires vm.flagless
 * @requires vm.compiler2.enabled & vm.debug == true
 *
 * @run driver TestIterativeEA
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class TestIterativeEA {

  public static void main(String[] args) throws Exception {
    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-server", "-XX:-TieredCompilation", "-Xbatch", "-XX:+PrintEliminateAllocations",
                 Launcher.class.getName());

    OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());

    System.out.println(analyzer.getOutput());

    analyzer.shouldHaveExitValue(0);
    analyzer.shouldContain("++++ Eliminated: 26 Allocate");
    analyzer.shouldContain("++++ Eliminated: 48 Allocate");
    analyzer.shouldContain("++++ Eliminated: 78 Allocate");
  }

  static class A {
    int i;

    public A(int i) {
      this.i = i;
    }
  }

  static class B {
    A a;

    public B(A a) {
      this.a = a;
    }
  }

  static class C {
    B b;

    public C(B b) {
      this.b = b;
    }
  }

  static int test(int i) {
    C c = new C(new B(new A(i)));
    return c.b.a.i;
  }

  static class Launcher {
    public static void main(String[] args) {
      for (int i = 0; i < 12000; ++i) {
        int j = test(i);
      }
    }
  }

}
