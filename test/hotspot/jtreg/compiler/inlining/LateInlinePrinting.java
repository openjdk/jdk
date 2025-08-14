/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8319850
 * @summary PrintInlining should print which methods are late inlines
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @requires vm.flagless
 * @requires vm.debug == true
 *
 * @run driver compiler.inlining.LateInlinePrinting
 */

package compiler.inlining;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class LateInlinePrinting {
    public static class TestLateInlining {
          public static void main(String[] args) {
              for (int i = 0; i < 20_000; i++) {
                  test1();
                  test2();
              }
          }

          private static void test1() {
              test3();
              testFailInline();
              testFailInline();
              test2();
          }

          private static void test2() {
              inlined1();
              inlined2();
          }

          private static void test3() {}

          private static void testFailInline() {}

          private static void inlined1() {}

          private static void inlined2() {}
      }


    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:-TieredCompilation", "-XX:-UseOnStackReplacement", "-XX:-BackgroundCompilation",
            "-XX:+PrintCompilation",
            "-XX:CompileCommand=compileonly,compiler.inlining.LateInlinePrinting$TestLateInlining::test1",
            "-XX:CompileCommand=compileonly,compiler.inlining.LateInlinePrinting$TestLateInlining::test2",
            "-XX:CompileCommand=quiet", "-XX:+PrintInlining", "-XX:+AlwaysIncrementalInline",
            "-XX:CompileCommand=dontinline,compiler.inlining.LateInlinePrinting$TestLateInlining::testFailInline",
            TestLateInlining.class.getName()
        );

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());
        analyzer.shouldHaveExitValue(0);

        analyzer.shouldContain("""
compiler.inlining.LateInlinePrinting$TestLateInlining::test2 (7 bytes)
                            @ 0   compiler.inlining.LateInlinePrinting$TestLateInlining::inlined1 (1 bytes)   inline (hot)   late inline succeeded
                            @ 3   compiler.inlining.LateInlinePrinting$TestLateInlining::inlined2 (1 bytes)   inline (hot)   late inline succeeded
                            """);
        analyzer.shouldContain("""
compiler.inlining.LateInlinePrinting$TestLateInlining::test1 (13 bytes)
                            @ 0   compiler.inlining.LateInlinePrinting$TestLateInlining::test3 (1 bytes)   inline (hot)   late inline succeeded
                            @ 3   compiler.inlining.LateInlinePrinting$TestLateInlining::testFailInline (1 bytes)   failed to inline: disallowed by CompileCommand
                            @ 6   compiler.inlining.LateInlinePrinting$TestLateInlining::testFailInline (1 bytes)   failed to inline: disallowed by CompileCommand
                            @ 9   compiler.inlining.LateInlinePrinting$TestLateInlining::test2 (7 bytes)   inline (hot)   late inline succeeded
                              @ 0   compiler.inlining.LateInlinePrinting$TestLateInlining::inlined1 (1 bytes)   inline (hot)   late inline succeeded
                              @ 3   compiler.inlining.LateInlinePrinting$TestLateInlining::inlined2 (1 bytes)   inline (hot)   late inline succeeded
                              """);
    }
}
