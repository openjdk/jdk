/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.stackslotalloc;

import org.graalvm.compiler.lir.VirtualStackSlot;

import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.ValueKind;

public final class StackInterval {

    private static final int INVALID_START = Integer.MAX_VALUE;
    private static final int INVALID_END = Integer.MIN_VALUE;
    private final VirtualStackSlot operand;
    private StackInterval hint;
    private final ValueKind<?> kind;
    private int from = INVALID_START;
    private int to = INVALID_END;
    private StackSlot location;

    public StackInterval(VirtualStackSlot operand, ValueKind<?> kind) {
        this.operand = operand;
        this.kind = kind;
    }

    public boolean verify(int maxOpId) {
        // maxOpId + 1 is the last position in the last block (i.e. the "write position")
        assert 0 <= from && from <= to && to <= maxOpId + 1 : String.format("from %d, to %d, maxOpId %d", from, to, maxOpId);
        return true;
    }

    public VirtualStackSlot getOperand() {
        return operand;
    }

    public void addTo(int opId) {
        if (opId >= to) {
            to = opId;
        }
    }

    protected void addFrom(int opId) {
        if (from > opId) {
            from = opId;
            // set opId also as to if it has not yet been set
            if (to == INVALID_END) {
                to = opId;
            }
        }
    }

    public ValueKind<?> kind() {
        return kind;
    }

    public StackSlot location() {
        return location;
    }

    public void setLocation(StackSlot location) {
        this.location = location;
    }

    public int from() {
        return from;
    }

    public int to() {
        return to;
    }

    public void fixFrom() {
        if (from == INVALID_START) {
            from = 0;
        }
    }

    public boolean isFixed() {
        return from == 0;
    }

    @Override
    public String toString() {
        return String.format("SI[%d-%d] k=%s o=%s l=%s h=%s", from, to, kind, operand, location, hint != null ? hint.getOperand() : "null");
    }

    public void setLocationHint(StackInterval locationHint) {
        hint = locationHint;
    }

    public StackInterval locationHint() {
        return hint;
    }

}
