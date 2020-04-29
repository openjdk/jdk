/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019 SAP SE. All rights reserved.
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
 * @bug 8217990
 * @summary With -XX:+UseOptoBiasInlining loading the markword is replaced by 0L if EliminateLocks is disabled. assert(dmw->is_neutral()) failed: invariant fails.
 * @author Richard Reingruber richard DOT reingruber AT sap DOT com
 *
 * @library /test/lib /test/hotspot/jtreg
 *
 * @build sun.hotspot.WhiteBox
 * @build ClassFileInstaller
 *
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 *
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions
 *                   -XX:CompileCommand=compileonly,*.TestUseOptoBiasInliningWithoutEliminateLocks::dontinline_testMethod
 *                   -XX:CompileCommand=dontinline,*::dontinline_*
 *                   -XX:-EliminateLocks
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -Xbatch
 *                   -XX:-TieredCompilation
 *                   compiler.c2.TestUseOptoBiasInliningWithoutEliminateLocks
 */

package compiler.c2;

import sun.hotspot.WhiteBox;

public class TestUseOptoBiasInliningWithoutEliminateLocks {

    public static final WhiteBox WB = WhiteBox.getWhiteBox();

    public static void main(String[] args) {
        new TestUseOptoBiasInliningWithoutEliminateLocks().run();
    }

    public boolean warmupDone;

    public void run() {
        for(int i = 0; i < 30000; i++) {
            dontinline_testMethod();
        }
        warmupDone = true;
        dontinline_testMethod();
    }

    public void dontinline_testMethod() {
        PointXY l1 = new PointXY(4.0f, 2.0f);
        synchronized (l1) {
            dontinline_deopt();
        }
    }

    public void dontinline_deopt() {
        if (warmupDone) {
            WB.deoptimizeFrames(false);
        }
    }

    static class PointXY {

        public float fritz;
        public float felix;

        public PointXY(float fritz_param, float felix_param) {
            this.fritz = fritz_param;
//            this.felix = felix_param;
        }
    }
}
