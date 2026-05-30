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

/**
 * @test
 * @summary Verify that specialized profiles don`t trigger decompile
 *          when call sites use different branches of unstable_if
 *          and that triggered decompile affects only one site.
 *
 * @requires vm.flavor == "server" & vm.compMode == "Xmixed"
 * @requires vm.compiler2.enabled
 *
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+UnlockExperimentalVMOptions -XX:+SpecializedMethodData
 *                   -XX:-BackgroundCompilation -XX:-UseOnStackReplacement
 *                   compiler.profiling.specialized.PerSiteUnstableIf
 */

package compiler.profiling.specialized;

import jdk.test.whitebox.WhiteBox;
import java.lang.reflect.Method;

public class PerSiteUnstableIf {

    static final WhiteBox WB = WhiteBox.getWhiteBox();
    static final int C2 = 4;

    static int inlinee(int v) { return v > 0 ? v * 2 : -v; }
    static int site1(int v) { return inlinee(v); }
    static int site2(int v) { return inlinee(v); }

    public static void main(String[] args) throws Exception {
        Class<?> self = PerSiteUnstableIf.class;
        Method m1 = self.getDeclaredMethod("site1", int.class);
        Method m2 = self.getDeclaredMethod("site2", int.class);
        Method inl = self.getDeclaredMethod("inlinee", int.class);

        int size = 100000;
        int[] thenData = new int[size];
        int[] elseData = new int[size];
        for (int i = 0; i < size; i++) {
            thenData[i] = i + 1;
            elseData[i] = -i;
        }

        for (int i = 0; i < size; i++) {
            site1(thenData[i]);
            site2(elseData[i]);
        }
        WB.enqueueMethodForCompilation(m1, C2);
        WB.enqueueMethodForCompilation(m2, C2);
        if (!WB.isMethodCompiled(m1) || !WB.isMethodCompiled(m2)) {
            throw new RuntimeException("Methods not compiled by C2");
        }

        int dc1Before = WB.getMethodDecompileCount(m1);
        int dc2Before = WB.getMethodDecompileCount(m2);
        if (dc1Before != 0 || dc2Before != 0) {
            throw new RuntimeException("Unexpected method decompiles");
        }

        int deopt = site1(elseData[0]);
        int nodeopt = site2(elseData[0]);

        int dc1After = WB.getMethodDecompileCount(m1);
        int dc2After = WB.getMethodDecompileCount(m2);

        if (dc1Before == dc1After) {
            throw new RuntimeException("No expected decompile");
        }
        if (dc2Before != dc2After) {
            throw new RuntimeException("Unexpected decompile");
        }
    }
}
