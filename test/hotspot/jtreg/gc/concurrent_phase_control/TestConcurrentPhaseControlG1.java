/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

package gc.concurrent_phase_control;

/*
 * @test TestConcurrentPhaseControlG1
 * @bug 8169517
 * @requires vm.gc.G1
 * @summary Test of WhiteBox concurrent GC phase control for G1.
 * @key gc
 * @modules java.base
 * @library /test/lib /
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 *    sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run driver gc.concurrent_phase_control.TestConcurrentPhaseControlG1
 */

import gc.concurrent_phase_control.CheckControl;

public class TestConcurrentPhaseControlG1 {

    // Pairs of phase name and regex to match log stringm for stepping through,
    private static final String[][] g1PhaseInfo = {
        // Step through the phases in order.
        {"IDLE", null},
        {"CONCURRENT_CYCLE", "Concurrent Cycle"},
        {"IDLE", null},  // Resume IDLE before testing subphases
        {"CLEAR_CLAIMED_MARKS", "Concurrent Clear Claimed Marks"},
        {"SCAN_ROOT_REGIONS", "Concurrent Scan Root Regions"},
        // ^F so not "From Roots", ^R so not "Restart"
        {"CONCURRENT_MARK", "Concurrent Mark [^FR]"},
        {"IDLE", null},  // Resume IDLE before testing subphases
        {"MARK_FROM_ROOTS", "Concurrent Mark From Roots"},
        {"PRECLEAN", "Concurrent Preclean"},
        {"BEFORE_REMARK", null},
        {"REMARK", "Pause Remark"},
        {"REBUILD_REMEMBERED_SETS", "Concurrent Rebuild Remembered Sets"},
        // Clear request
        {"IDLE", null},
        {"ANY", null},
        // Request a phase.
        {"MARK_FROM_ROOTS", "Concurrent Mark From Roots"},
        // Request an earlier phase, to ensure loop rather than stuck at idle.
        {"SCAN_ROOT_REGIONS", "Concurrent Scan Root Regions"},
        // Clear request, to unblock service.
        {"IDLE", null},
        {"ANY", null},
    };

    private static final String[] g1Options =
        new String[]{"-XX:+UseG1GC",  "-Xlog:gc,gc+marking"};

    private static final String g1Name = "G1";

    public static void main(String[] args) throws Exception {
        CheckControl.check(g1Name, g1Options, g1PhaseInfo);
    }
}
