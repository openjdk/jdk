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


package org.graalvm.compiler.nodeinfo;

/**
 * Constants representing an estimation of the number of CPU cycles needed to execute a certain
 * compiler node.
 */
public enum NodeCycles {

    /**
     * The default value of the {@link NodeInfo#cycles()} property.
     * <p>
     * For further information about the use of {@code CYCLES_UNSET} see {@link NodeInfo#cycles()}.
     */
    CYCLES_UNSET(0),
    /**
     * Nodes for which, due to arbitrary reasons, no estimation can be made either (1) statically
     * without inspecting the properties of a node or (2) at all (like e.g. for an invocation).
     * <p>
     * Nodes annotated with {@code CYCLES_UNKNOWN} should specify the
     * {@link NodeInfo#cyclesRationale()} property to clarify why an estimation cannot be done.
     */
    CYCLES_UNKNOWN(0),
    /**
     * Nodes for which runtime information is irrelevant and can be ignored, e.g. for test nodes.
     */
    CYCLES_IGNORED(0),
    /**
     * Nodes that do not consume any CPU time during the "execution", e.g. Constants.
     */
    CYCLES_0(0),
    CYCLES_1(1),
    CYCLES_2(2),
    CYCLES_4(4),
    CYCLES_8(8),
    CYCLES_16(16),
    CYCLES_32(32),
    CYCLES_64(64),
    CYCLES_128(128),
    CYCLES_256(256),
    CYCLES_512(512),
    CYCLES_1024(1024);

    private static final NodeCycles[] VALUES = values();

    public final int value;

    NodeCycles(int value) {
        this.value = value;
    }

    public boolean isValueKnown() {
        return this != NodeCycles.CYCLES_UNKNOWN && this != NodeCycles.CYCLES_UNSET;
    }

    public static final int IGNORE_CYCLES_CONTRACT_FACTOR = 0xFFFF;

    public static NodeCycles compute(NodeCycles base, int opCount) {
        assert opCount >= 0;
        if (opCount == 0) {
            return CYCLES_0;
        }
        assert base.ordinal() > CYCLES_0.ordinal();
        int log2 = log2(base.value * opCount);
        for (int i = base.ordinal(); i < VALUES.length; i++) {
            if (log2(VALUES[i].value) == log2) {
                return VALUES[i];
            }
        }
        return CYCLES_1024;
    }

    public static NodeCycles compute(int rawValue) {
        assert rawValue >= 0;
        if (rawValue == 0) {
            return CYCLES_0;
        }
        for (int i = CYCLES_0.ordinal(); i < VALUES.length - 1; i++) {
            if (VALUES[i].value >= rawValue && rawValue <= VALUES[i + 1].value) {
                int r1 = VALUES[i].value;
                int r2 = VALUES[i + 1].value;
                int diff = r2 - r1;
                return rawValue - r1 > diff / 2 ? VALUES[i + 1] : VALUES[i];
            }
        }
        return CYCLES_1024;
    }

    private static int log2(int val) {
        return (Integer.SIZE - 1) - Integer.numberOfLeadingZeros(val);
    }
}
