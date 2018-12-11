/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package sun.hotspot.gc;

import sun.hotspot.WhiteBox;

/**
 * API to obtain information about selected and supported Garbage Collectors
 * retrieved from the VM with the WhiteBox API.
 */
public enum GC {
    /*
     * Enum values must match CollectedHeap::Name
     */
    Serial(1),
    Parallel(2),
    ConcMarkSweep(3),
    G1(4),
    Epsilon(5),
    Z(6),
    Shenandoah(7);

    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    private final int name;

    private GC(int name) {
        this.name = name;
    }

    /**
     * @return true if this GC is supported by the VM, i.e., it is built into the VM.
     */
    public boolean isSupported() {
        return WB.isGCSupported(name);
    }

    /**
     * @return true if this GC is currently selected/used
     */
    public boolean isSelected() {
        return WB.isGCSelected(name);
    }

    /**
     * @return true if GC was selected ergonomically, as opposed
     *         to being explicitly specified on the command line
     */
    public static boolean isSelectedErgonomically() {
        return WB.isGCSelectedErgonomically();
    }
}
