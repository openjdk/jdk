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
import java.util.ArrayList;
import java.util.Map;

import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Utility class for the call states to capture.
 */
public final class CapturableState {

    public static final StructLayout LAYOUT;
    // Keep in synch with DowncallLinker::capture_state in downcallLinker.cpp
    private static final Map<String, Integer> MASKS;

    static {
        if (OperatingSystem.isWindows()) {
            LAYOUT = MemoryLayout.structLayout(
                    JAVA_INT.withName("GetLastError"),
                    JAVA_INT.withName("WSAGetLastError"),
                    JAVA_INT.withName("errno"));
            MASKS = Map.of(
                    "GetLastError",    1 << 0,
                    "WSAGetLastError", 1 << 1,
                    "errno",           1 << 2
            );
        } else {
            LAYOUT = MemoryLayout.structLayout(
                    JAVA_INT.withName("errno"));
            MASKS = Map.of(
                    "errno",           1 << 2
            );
        }
    }

    private CapturableState() {
    }

    /**
     * Returns the mask for a supported capturable state, or throw an
     * IllegalArgumentException if no supported state with this name exists.
     */
    public static int maskFromName(String name) {
        var ret = MASKS.get(name);
        if (ret == null) {
            throw new IllegalArgumentException(
                    "Unknown name: " + name + ", must be one of: "
                            + MASKS.keySet());
        }
        return ret;
    }

    /**
     * Returns a collection-like display string for a captured state mask.
     * Enclosed with brackets.
     */
    public static String displayString(int mask) {
        var displayList = new ArrayList<>(); // unordered
        for (var e : MASKS.entrySet()) {
            if ((mask & e.getValue()) != 0) {
                displayList.add(e.getKey());
            }
        }
        return displayList.toString();
    }
}
