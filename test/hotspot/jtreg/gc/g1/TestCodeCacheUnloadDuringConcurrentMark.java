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
 * @test TestCodeCacheUnloadDuringConcurrentMark
 * @summary Tests that G1 concurrent marking unloads a freshly not-entrant nmethod.
 * @requires vm.gc.G1
 * @requires vm.flagless
 * @requires vm.compiler1.enabled
 * @requires vm.opt.ClassUnloading != false
 * @requires vm.opt.ClassUnloadingWithConcurrentMark != false
 * @requires vm.opt.MethodFlushing != false
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -Xbatch -XX:-BackgroundCompilation
 *                   -XX:+UseG1GC
 *                   -XX:+ClassUnloadingWithConcurrentMark
 *                   gc.g1.TestCodeCacheUnloadDuringConcurrentMark
 */

import java.lang.reflect.Method;

import gc.testlibrary.CodeCacheUtils;
import jdk.test.whitebox.WhiteBox;

public class TestCodeCacheUnloadDuringConcurrentMark {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    public static class Target {
        public static int test() {
            return 1;
        }
    }

    public static void main(String[] args) throws Exception {
        // Keep an automatic cycle from reclaiming the target before the
        // test-owned concurrent mark reaches it.
        WB.concurrentGCAcquireControl();
        try {
            Method method = Target.class.getDeclaredMethod("test");
            int compileId = CodeCacheUtils.compileAndMakeNotEntrant(method);
            if (!CodeCacheUtils.codelistContains(compileId, method, 1, 1 /* not_entrant */)) {
                throw new AssertionError("Expected a not-entrant target nmethod before concurrent mark");
            }

            int completedCycles = WB.g1CompletedConcurrentMarkCycles();
            WB.concurrentGCRunTo(WB.AFTER_MARKING_STARTED);
            WB.concurrentGCRunToIdle();
            if (completedCycles >= WB.g1CompletedConcurrentMarkCycles()) {
                throw new AssertionError("Concurrent GC aborted");
            }

            if (CodeCacheUtils.codelistContains(compileId, method, 1, 1 /* not_entrant */)) {
                throw new AssertionError("Expected concurrent mark to unload the not-entrant target nmethod");
            }
        } finally {
            WB.concurrentGCReleaseControl();
        }
    }
}
