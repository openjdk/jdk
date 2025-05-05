/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.internal.foreign.abi;

import jdk.internal.util.OperatingSystem;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.lang.foreign.ValueLayout.JAVA_INT;

public final class CapturableState {

    public static final StructLayout LAYOUT;
    private static final Map<String, CapturableState> LOOKUP;

    static {
        List<CapturableState> supported;

        if (OperatingSystem.isWindows()) {
            supported = List.of(
                    new CapturableState("GetLastError",    JAVA_INT, 1 << 0),
                    new CapturableState("WSAGetLastError", JAVA_INT, 1 << 1),
                    new CapturableState("errno",           JAVA_INT, 1 << 2)
            );
        } else {
            supported = List.of(new CapturableState("errno", JAVA_INT, 1 << 2));
        }

        MemoryLayout[] stateLayouts = new MemoryLayout[supported.size()];
        @SuppressWarnings({"unchecked", "rawtypes"})
        Map.Entry<String, CapturableState>[] entries = new Map.Entry[supported.size()];
        int i = 0;
        for (var each : supported) {
            stateLayouts[i] = each.layout;
            entries[i] = Map.entry(each.stateName, each);
            i++;
        }
        LAYOUT = MemoryLayout.structLayout(stateLayouts);
        LOOKUP = Map.ofEntries(entries);
    }

    public final String stateName;
    public final ValueLayout layout;
    public final int mask;

    private CapturableState(String stateName, ValueLayout layout, int mask) {
        this.stateName = stateName;
        this.layout = layout.withName(stateName);
        this.mask = mask;
    }

    public static CapturableState forName(String name) {
        var ret = LOOKUP.get(name);
        if (ret == null) {
            throw new IllegalArgumentException(
                    "Unknown name: " + name +", must be one of: "
                            + LOOKUP.keySet());
        }
        return ret;
    }

    /**
     * Returns a list-like display string for a captured state mask.
     * Enclosed with brackets.
     */
    public static String displayString(int mask) {
        var displayList = new ArrayList<>(); // unordered
        for (var e : LOOKUP.values()) {
            if ((mask & e.mask) != 0) {
                displayList.add(e.stateName);
            }
        }
        return displayList.toString();
    }

    @Override
    public String toString() {
        return stateName;
    }
}
