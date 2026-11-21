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

package gc.g1.humongousObjects;

import gc.testlibrary.Helpers;
import jdk.test.whitebox.WhiteBox;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @test gc.g1.humongousObjects.TestHumongousMirror
 * @bug 8075229
 * @summary Checks that a humongous mirror is not reclaimed eagerly as long as a CLD has a reference to it.
 * @requires vm.gc.G1
 * @requires vm.opt.G1HeapRegionSize == "null" | vm.opt.G1HeapRegionSize == "1M"
 * @requires vm.opt.ClassUnloading != false
 * @requires vm.opt.ClassUnloadingWithConcurrentMark != false
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 * @modules java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xms64M -Xmx64M -XX:+UseG1GC -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -Xlog:gc,gc+humongous=debug,class+load,class+unload=debug:file=TestHumongousMirror.log
 *                   -XX:G1HeapRegionSize=1M -XX:MaxTenuringThreshold=1 -XX:+VerifyAfterGC
 *                   gc.g1.humongousObjects.TestHumongousMirror
 */

public class TestHumongousMirror {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    private static final String className = "HumongousMirror";

    public static void main(String[] args) throws Exception {
        final Path workDir = Paths.get("");

        // 65523 statics make the mirror of the class humongous on both 32 and 64 bit VMs.
        String c = Helpers.generate(className, null, null, 0, 65523);
        Helpers.compileClass(className, workDir, c);

        Class<?> clazz = Class.forName(className, false, null);
        if (!WB.g1IsHumongous(clazz)) {
            throw new Error("Mirror is not humongous");
        }
        long mirrorAddress = WB.getObjectAddress(clazz);
        // Remove all Java references. The CLD still has a reference to the humongous mirror.
        clazz = null;

        for (int i = 0; i < 5; i++) {
            WB.youngGC();
            if (WB.g1BelongsToFreeRegion(mirrorAddress)) {
                throw new Error("Mirror at " + mirrorAddress + " has been reclaimed");
            }
        }
    }
}
