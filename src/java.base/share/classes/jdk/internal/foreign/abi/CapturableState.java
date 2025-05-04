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
import java.util.Map;
import java.util.stream.Stream;

import static java.lang.foreign.ValueLayout.JAVA_INT;

public enum CapturableState {
    GET_LAST_ERROR    ("GetLastError",    JAVA_INT, 1 << 0),
    WSA_GET_LAST_ERROR("WSAGetLastError", JAVA_INT, 1 << 1),
    ERRNO             ("errno",           JAVA_INT, 1 << 2);

    public static final StructLayout LAYOUT;
    public static final Map<String, CapturableState> SUPPORTED;

    static {
        var values = values();

        if (OperatingSystem.isWindows()) {
            SUPPORTED = Map.of(GET_LAST_ERROR.stateName, GET_LAST_ERROR,
                               WSA_GET_LAST_ERROR.stateName, WSA_GET_LAST_ERROR,
                               ERRNO.stateName, ERRNO);
        } else {
            SUPPORTED = Map.of(ERRNO.stateName, ERRNO);
        }

        MemoryLayout[] stateLayouts = new MemoryLayout[SUPPORTED.size()];
        int i = 0;
        for (var supported : SUPPORTED.values()) {
            stateLayouts[i++] = supported.layout;
        }
        LAYOUT = MemoryLayout.structLayout(stateLayouts);
    }

    public final String stateName;
    public final ValueLayout layout;
    public final int mask;

    CapturableState(String stateName, ValueLayout layout, int mask) {
        this.stateName = stateName;
        this.layout = layout.withName(stateName);
        this.mask = mask;
    }

    public static CapturableState forName(String name) {
        var ret = SUPPORTED.get(name);
        if (ret == null) {
            throw new IllegalArgumentException(
                    "Unknown name: " + name +", must be one of: "
                            + SUPPORTED.keySet());
        }
        return ret;
    }

    /**
     * Returns a list-like display string for a captured state mask.
     * Enclosed with brackets.
     */
    public static String displayString(int mask) {
        var displayList = new ArrayList<>();
        for (var e : SUPPORTED.values()) {
            if ((mask & e.mask) != 0) {
                displayList.add(e.stateName);
            }
        }
        return displayList.toString();
    }
}
