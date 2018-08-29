/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8174749
 * @summary MemberNameTable should reuse entries
 * @library /test/lib
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. MemberNameLeak
 */

import java.lang.invoke.*;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import sun.hotspot.WhiteBox;
import sun.hotspot.code.Compiler;

public class MemberNameLeak {
    static class Leak {
      public void callMe() {
      }

      public static void main(String[] args) throws Throwable {
        Leak leak = new Leak();
        WhiteBox wb = WhiteBox.getWhiteBox();
        int removedCountOrig =  wb.resolvedMethodRemovedCount();
        int removedCount;

        for (int i = 0; i < 10; i++) {
          MethodHandles.Lookup lookup = MethodHandles.lookup();
          MethodType mt = MethodType.fromMethodDescriptorString("()V", Leak.class.getClassLoader());
          // findSpecial leaks some native mem
          MethodHandle mh = lookup.findSpecial(Leak.class, "callMe", mt, Leak.class);
          mh.invokeExact(leak);
        }

        System.gc();  // make mh unused

        // Wait until ServiceThread cleans ResolvedMethod table
        do {
          removedCount = wb.resolvedMethodRemovedCount();
        } while (removedCountOrig == removedCount);
      }
    }

    public static void test(String gc) throws Throwable {
       // Run this Leak class with logging
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                                      "-Xlog:membername+table=trace",
                                      "-XX:+WhiteBoxAPI",
                                      "-Xbootclasspath/a:.",
                                      gc, Leak.class.getName());
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("ResolvedMethod entry added for MemberNameLeak$Leak.callMe()V");
        output.shouldContain("ResolvedMethod entry found for MemberNameLeak$Leak.callMe()V");
        output.shouldContain("ResolvedMethod entry removed");
        output.shouldHaveExitValue(0);
    }

    public static void main(java.lang.String[] unused) throws Throwable {
        test("-XX:+UseG1GC");
        test("-XX:+UseParallelGC");
        test("-XX:+UseSerialGC");
        if (!Compiler.isGraalEnabled()) { // Graal does not support CMS
            test("-XX:+UseConcMarkSweepGC");
        }
    }
}
