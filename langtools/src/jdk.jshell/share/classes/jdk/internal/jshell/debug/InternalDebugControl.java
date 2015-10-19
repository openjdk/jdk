/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.jshell.debug;

import java.util.HashMap;
import java.util.Map;
import jdk.jshell.JShell;

/**
 * Used to externally control output messages for debugging the implementation
 * of the JShell API.  This is NOT a supported interface,
 * @author Robert Field
 */
public class InternalDebugControl {
    public static final int DBG_GEN   = 0b0000001;
    public static final int DBG_FMGR  = 0b0000010;
    public static final int DBG_COMPA = 0b0000100;
    public static final int DBG_DEP   = 0b0001000;
    public static final int DBG_EVNT  = 0b0010000;

    private static Map<JShell, Integer> debugMap = null;

    public static void setDebugFlags(JShell state, int flags) {
        if (debugMap == null) {
            debugMap = new HashMap<>();
        }
        debugMap.put(state, flags);
    }

    public static boolean debugEnabled(JShell state, int flag) {
        if (debugMap == null) {
            return false;
        }
        Integer flags = debugMap.get(state);
        if (flags == null) {
            return false;
        }
        return (flags & flag) != 0;
    }
}
