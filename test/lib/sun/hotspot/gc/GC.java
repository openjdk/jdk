/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package sun.hotspot.gc;

import java.util.ArrayList;
import java.util.List;
import sun.hotspot.WhiteBox;

/**
 * API to obtain information about selected and supported Garbage Collectors
 * retrieved from the VM with the WhiteBox API.
 */
public enum GC {
    Serial(1),
    Parallel(2),
    ConcMarkSweep(4),
    G1(8);

    private static final GC CURRENT_GC;
    private static final int ALL_GC_CODES;
    private static final boolean IS_BY_ERGO;
    static {
        WhiteBox WB = WhiteBox.getWhiteBox();
        ALL_GC_CODES = WB.allSupportedGC();
        IS_BY_ERGO = WB.gcSelectedByErgo();

        int currentCode = WB.currentGC();
        GC tmp = null;
        for (GC gc: GC.values()) {
            if (gc.code == currentCode) {
                tmp = gc;
                break;
            }
        }
        if (tmp == null) {
            throw new Error("Unknown current GC code " + currentCode);
        }
        CURRENT_GC = tmp;
    }

    private final int code;
    private GC(int code) {
        this.code = code;
    }

    /**
     * @return true if the collector is supported by the VM, false otherwise.
     */
    public boolean isSupported() {
        return (ALL_GC_CODES & code) != 0;
    }


    /**
     * @return the current collector used by VM.
     */
    public static GC current() {
        return CURRENT_GC;
    }

    /**
     * @return true if GC was selected by ergonomic, false if specified
     * explicitly by the command line flag.
     */
    public static boolean currentSetByErgo() {
        return IS_BY_ERGO;
    }

    /**
     * @return List of collectors supported by the VM.
     */
    public static List<GC> allSupported() {
        List<GC> list = new ArrayList<>();
        for (GC gc: GC.values()) {
            if (gc.isSupported()) {
                list.add(gc);
            }
        }
        return list;
    }
}

