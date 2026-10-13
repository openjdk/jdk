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

package gc.g1;

/*
 * @test id=256-128
 * @bug 8392820
 * @requires vm.gc.G1
 * @requires vm.bits != "32"
 * @summary Make sure that G1 does not hang with some combinations of -XX:G1HeapRegionSize and -XX:GCCardSizeInBytes
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseG1GC -Xmx2g -XX:G1HeapRegionSize=256m -XX:GCCardSizeInBytes=128 gc.g1.TestCardClearHang
 */

/*
 * @test id=512-128
 * @bug 8392820
 * @requires vm.gc.G1
 * @requires vm.bits != "32"
 * @summary Make sure that G1 does not hang with some combinations of -XX:G1HeapRegionSize and -XX:GCCardSizeInBytes
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseG1GC -Xmx2g -XX:G1HeapRegionSize=512m -XX:GCCardSizeInBytes=128 gc.g1.TestCardClearHang
 */

/*
 * @test id=512-256
 * @bug 8392820
 * @requires vm.gc.G1
 * @requires vm.bits != "32"
 * @summary Make sure that G1 does not hang with some combinations of -XX:G1HeapRegionSize and -XX:GCCardSizeInBytes
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseG1GC -Xmx2g -XX:G1HeapRegionSize=512m -XX:GCCardSizeInBytes=256 gc.g1.TestCardClearHang
 */

import jdk.test.whitebox.WhiteBox;

public class TestCardClearHang {

  public static void main(String[] args) throws InterruptedException {
      WhiteBox wb = WhiteBox.getWhiteBox();
      // Run a young gc.
      wb.youngGC();
  }
}
