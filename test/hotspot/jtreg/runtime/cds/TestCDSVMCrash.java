/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestCDSVMCrash
 * @summary Verify that an exception is thrown when the VM crashes during executeAndLog
 * @requires vm.cds
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver TestCDSVMCrash
 * @bug 8306583
 */

 import jdk.test.lib.cds.CDSTestUtils;
 import jdk.test.lib.process.OutputAnalyzer;
 import jdk.test.lib.process.ProcessTools;

 public class TestCDSVMCrash {

     public static void main(String[] args) throws Exception {
         if (args.length == 1) {
             // This should guarantee to throw:
             // java.lang.OutOfMemoryError: Requested array size exceeds VM limit
             try {
                 Object[] oa = new Object[Integer.MAX_VALUE];
                 throw new Error("OOME not triggered");
             } catch (OutOfMemoryError err) {
                 throw new Error("OOME didn't abort JVM!");
             }
         }
         // else this is the main test
         ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+CrashOnOutOfMemoryError",
                  "-XX:-CreateCoredumpOnCrash", "-Xmx128m", "-Xshare:on", TestCDSVMCrash.class.getName(),"throwOOME");
         OutputAnalyzer output = new OutputAnalyzer(pb.start());
         // executeAndLog should throw an exception in the VM crashed
         try {
            CDSTestUtils.executeAndLog(pb, "cds_vm_crash");
            throw new Error("Expected VM to crash");
         } catch(RuntimeException e) {
            if (!e.getMessage().equals("Hotspot crashed")) {
              throw new Error("Expected message: Hotspot crashed");
            }
         }
         int exitValue = output.getExitValue();
         if (0 == exitValue) {
             //expecting a non zero value
             throw new Error("Expected to get non zero exit value");
         }
        output.shouldContain("A fatal error has been detected by the Java Runtime Environment");
        System.out.println("PASSED");
     }
 }
