/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

package gc.metaspace;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Asserts;
import sun.hotspot.WhiteBox;

/* @test TestMetaspaceCMSCancel
 * @bug 8026752
 * @summary Tests cancel of CMS concurrent cycle for Metaspace after a full GC
 * @requires vm.gc.ConcMarkSweep & !vm.graal.enabled
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm gc.metaspace.TestMetaspaceCMSCancel
 */


public class TestMetaspaceCMSCancel {

    public static void main(String[] args) throws Exception {
        // Set a small MetaspaceSize so that a CMS concurrent collection will be
        // scheduled.  Set CMSWaitDuration to 5s so that the concurrent collection
        // start may be delayed.  It does not guarantee 5s before the start of the
        // concurrent collection but does increase the probability that it will
        // be started later.  System.gc() is used to invoke a full collection.  Set
        // ExplicitGCInvokesConcurrent to off so it is a STW collection.
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-Xbootclasspath/a:.",
                                                                  "-XX:+UnlockDiagnosticVMOptions",
                                                                  "-XX:+WhiteBoxAPI",
                                                                  "-XX:+UseConcMarkSweepGC",
                                                                  "-XX:MetaspaceSize=2m",
                                                                  "-XX:CMSWaitDuration=5000",
                                                                  "-XX:-ExplicitGCInvokesConcurrent",
                                                                  "-Xlog:gc*=debug",
                                                                  MetaspaceGCTest.class.getName());

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotContain("Concurrent Reset");
        output.shouldHaveExitValue(0);
    }

    static class MetaspaceGCTest {
        public static void main(String [] args) {
            WhiteBox wb = WhiteBox.getWhiteBox();
            System.gc();
            Asserts.assertFalse(wb.metaspaceShouldConcurrentCollect());
        }
    }
}
