/*
* Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestG1HeapRegionSize
 * @key gc
 * @bug 8021879
 * @requires vm.gc=="G1" | vm.gc=="null"
 * @summary Verify that the flag G1HeapRegionSize is updated properly
 * @run main/othervm -Xmx64m TestG1HeapRegionSize 1048576
 * @run main/othervm -XX:G1HeapRegionSize=2m -Xmx64m -XX:+UseG1GC TestG1HeapRegionSize 2097152
 * @run main/othervm -XX:G1HeapRegionSize=3m -Xmx64m -XX:+UseG1GC TestG1HeapRegionSize 2097152
 * @run main/othervm -XX:G1HeapRegionSize=64m -Xmx256m -XX:+UseG1GC TestG1HeapRegionSize 33554432
 */

import sun.management.ManagementFactoryHelper;
import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.VMOption;

public class TestG1HeapRegionSize {

  public static void main(String[] args) {
    HotSpotDiagnosticMXBean diagnostic = ManagementFactoryHelper.getDiagnosticMXBean();

    String expectedValue = getExpectedValue(args);
    VMOption option = diagnostic.getVMOption("G1HeapRegionSize");
    if (!expectedValue.equals(option.getValue())) {
      throw new RuntimeException("Wrong value for G1HeapRegionSize. Expected " + expectedValue + " but got " + option.getValue());
    }
  }

  private static String getExpectedValue(String[] args) {
    if (args.length != 1) {
      throw new RuntimeException("Wrong number of arguments. Expected 1 but got " + args.length);
    }
    return args[0];
  }

}
