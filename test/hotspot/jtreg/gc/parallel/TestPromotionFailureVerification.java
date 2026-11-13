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

package gc.parallel;

/*
 * @test
 * @bug 8396417
 * @requires vm.gc.Parallel
 * @requires vm.debug
 * @requires vm.flagless
 * @requires vm.bits != "32"
 * @summary Make sure that Parallel GC does not crash when verifying after promotion failure
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -Xlog:gc -XX:MaxTenuringThreshold=1 -XX:+UseCompactObjectHeaders -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseParallelGC -XX:+PromotionFailureALot -XX:PromotionFailureALotInterval=1 -XX:PromotionFailureALotCount=1 -XX:+VerifyAfterGC -Xmx16m gc.parallel.TestPromotionFailureVerification
 */

import jdk.test.whitebox.WhiteBox;

public class TestPromotionFailureVerification {
    public static void main(String[] args) {
        WhiteBox wb = WhiteBox.getWhiteBox();

        Object sink = new Object();
        // Run two young gcs that guarantee a promotion failure of our sink object (if not other objects during startup)
        wb.youngGC();
        wb.youngGC();
        java.lang.ref.Reference.reachabilityFence(sink);
  }
}
