/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package sun.java2d.marlin;

/**
 * DPQS Sorter context
 */
final class DPQSSorterContext {

    static final boolean LOG_ALLOC = false;
    static final boolean CHECK_ALLOC = false && LOG_ALLOC;

    /**
     * Max capacity of the index array for tracking runs.
     */
    static final int MAX_RUN_CAPACITY = DualPivotQuicksort20191112Ext.MAX_RUN_CAPACITY;

    /* members */
    final int[] run;
    int[] auxA;
    int[] auxB;
    boolean runInit;

    DPQSSorterContext() {
        // preallocate max runs:
        if (LOG_ALLOC) {
            MarlinUtils.logInfo("alloc run: " + MAX_RUN_CAPACITY);
        }
        run = new int[MAX_RUN_CAPACITY];
    }

    void initBuffers(final int length, final int[] a, final int[] b) {
        auxA = a;
        if (CHECK_ALLOC && (a.length < length)) {
            if (LOG_ALLOC) {
                MarlinUtils.logInfo("alloc auxA: " + length);
            }
            auxA = new int[length];
        }
        auxB = b;
        if (CHECK_ALLOC && (b.length < length)) {
            if (LOG_ALLOC) {
                MarlinUtils.logInfo("alloc auxB: " + length);
            }
            auxB = new int[length];
        }
        runInit = true;
    }

}
