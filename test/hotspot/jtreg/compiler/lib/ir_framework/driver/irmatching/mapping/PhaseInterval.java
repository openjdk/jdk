/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.irmatching.mapping;

import compiler.lib.ir_framework.CompilePhase;

/**
 * This class represents a compile phase interval/range on the ordering found in {@link CompilePhase}. The interval is
 * defined as [start, end] (including start and end).
 *
 * @see CompilePhase
 */
public class PhaseInterval {
    private final CompilePhase start;
    private final CompilePhase end;

    /**
     * Create an interval that represents a singleton set (i.e. an interval that only contains a single compile phase).
     */
    public PhaseInterval(CompilePhase singlePhase) {
        this.start = singlePhase;
        this.end = singlePhase;
    }

    public PhaseInterval(CompilePhase start, CompilePhase end) {
        this.start = start;
        this.end = end;
    }

    public int startIndex() {
        return start.ordinal();
    }

    public int endIndex() {
        return end.ordinal();
    }

    public CompilePhase start() {
        return start;
    }

    public CompilePhase end() {
        return end;
    }

    public boolean includes(CompilePhase phase) {
        // start <= phase <= end
        return phase.compareTo(start) >= 0 && phase.compareTo(end) <= 0;
    }

    @Override
    public String toString() {
        return "[" + start + ", " + end + "]";
    }
}

