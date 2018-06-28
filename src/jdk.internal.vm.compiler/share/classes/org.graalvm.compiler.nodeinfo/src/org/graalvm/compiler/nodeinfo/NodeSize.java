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
 */


package org.graalvm.compiler.nodeinfo;

/**
 * Constants representing an estimation of of the size needed to represent a compiler node in
 * machine code.
 */
public enum NodeSize {

    /**
     * The default value of the {@link NodeInfo#size()} property.
     * <p>
     * For further information about the use of {@code SIZE_UNSET} see {@link NodeInfo#size()}.
     */
    SIZE_UNSET(0),
    /**
     * Nodes for which, due to arbitrary reasons, no estimation can be made either (1) statically
     * without inspecting the properties of a node or (2) at all (like e.g. for an invocation).
     * <p>
     * Nodes annotated with {@code SIZE_UNKNOWN} should specify the {@link NodeInfo#sizeRationale()}
     * property to clarify why an estimation cannot be done.
     */
    SIZE_UNKNOWN(0),
    /**
     * Nodes for which code size information is irrelevant and can be ignored, e.g. for test nodes.
     */
    SIZE_IGNORED(0),
    /**
     * Nodes that do not require any code to be generated in order to be "executed", e.g. a pinode
     * node.
     */
    SIZE_0(0),
    SIZE_1(1),
    SIZE_2(2),
    SIZE_4(4),
    SIZE_8(8),
    SIZE_16(16),
    SIZE_32(32),
    SIZE_64(64),
    SIZE_128(128),
    SIZE_256(256),
    SIZE_512(512),
    SIZE_1024(1024);

    public final int value;

    NodeSize(int value) {
        this.value = value;
    }

    public static final int IGNORE_SIZE_CONTRACT_FACTOR = 0xFFFF;

    public static NodeSize compute(NodeSize base, int opCount) {
        assert opCount >= 0;
        if (opCount == 0) {
            return SIZE_0;
        }
        assert base.ordinal() > SIZE_0.ordinal();
        int log2 = log2(base.value * opCount);
        NodeSize[] values = values();
        for (int i = base.ordinal(); i < values.length; i++) {
            if (log2(values[i].value) == log2) {
                return values[i];
            }
        }
        return SIZE_1024;
    }

    public static NodeSize compute(int rawValue) {
        assert rawValue >= 0;
        if (rawValue == 0) {
            return SIZE_0;
        }
        assert rawValue > 0;
        NodeSize[] values = values();
        for (int i = SIZE_0.ordinal(); i < values.length - 1; i++) {
            if (values[i].value >= rawValue && rawValue <= values[i + 1].value) {
                int r1 = values[i].value;
                int r2 = values[i + 1].value;
                int diff = r2 - r1;
                return rawValue - r1 > diff / 2 ? values[i + 1] : values[i];
            }
        }
        return SIZE_1024;
    }

    private static int log2(int val) {
        return (Integer.SIZE - 1) - Integer.numberOfLeadingZeros(val);
    }
}
