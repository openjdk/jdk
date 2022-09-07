/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;

public class VMStorage {
    /**
     * Type of storage. e.g. stack, or which register type (GP, FP, vector)
     */
    private final byte type;

    /**
     * The (on stack) size in bytes when type = stack, a register mask otherwise.
     * The register mask indicates which segments of a register are used.
     */
    private final short segmentMaskOrSize;

    /**
     * The index is either a register number within a type, or
     * a stack offset in bytes if type = stack.
     * (a particular platform might add a bias to this in generate code)
     */
    private final int indexOrOffset;

    private final String debugName;

    private VMStorage(byte type, short segmentMaskOrSize, int indexOrOffset, String debugName) {
        this.type = type;
        this.segmentMaskOrSize = segmentMaskOrSize;
        this.indexOrOffset = indexOrOffset;
        this.debugName = debugName;
    }

    public static VMStorage stackStorage(byte type, short size, int byteOffset) {
        return new VMStorage(type, size, byteOffset, "Stack@" + byteOffset);
    }

    public static VMStorage regStorage(byte type, short segmentMask, int index, String debugName) {
        return new VMStorage(type, segmentMask, index, debugName);
    }

    public byte type() {
        return type;
    }

    public short segmentMaskOrSize() {
        return segmentMaskOrSize;
    }

    public int indexOrOffset() {
        return indexOrOffset;
    }

    public String name() {
        return debugName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return (o instanceof VMStorage vmStorage)
            && type == vmStorage.type
            && segmentMaskOrSize == vmStorage.segmentMaskOrSize
            && indexOrOffset == vmStorage.indexOrOffset
            && Objects.equals(debugName, vmStorage.debugName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, segmentMaskOrSize, indexOrOffset, debugName);
    }

    @Override
    public String toString() {
        return "VMStorage{" +
                "type=" + type +
                ", segmentMaskOrSize=" + segmentMaskOrSize +
                ", indexOrOffset=" + indexOrOffset +
                ", debugName='" + debugName + '\'' +
                '}';
    }
}
