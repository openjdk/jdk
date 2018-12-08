/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.amd64;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import jdk.internal.vm.compiler.word.Pointer;

import static org.graalvm.compiler.graph.Node.NodeIntrinsic;

public class AMD64ArrayIndexOf {

    public static final ForeignCallDescriptor STUB_INDEX_OF_TWO_CONSECUTIVE_BYTES = new ForeignCallDescriptor(
                    "indexOfTwoConsecutiveBytes", int.class, Pointer.class, int.class, int.class);
    public static final ForeignCallDescriptor STUB_INDEX_OF_TWO_CONSECUTIVE_CHARS = new ForeignCallDescriptor(
                    "indexOfTwoConsecutiveChars", int.class, Pointer.class, int.class, int.class);
    public static final ForeignCallDescriptor STUB_INDEX_OF_1_BYTE = new ForeignCallDescriptor(
                    "indexOf1Byte", int.class, Pointer.class, int.class, byte.class);
    public static final ForeignCallDescriptor STUB_INDEX_OF_2_BYTES = new ForeignCallDescriptor(
                    "indexOf2Bytes", int.class, Pointer.class, int.class, byte.class, byte.class);
    public static final ForeignCallDescriptor STUB_INDEX_OF_3_BYTES = new ForeignCallDescriptor(
                    "indexOf3Bytes", int.class, Pointer.class, int.class, byte.class, byte.class, byte.class);
    public static final ForeignCallDescriptor STUB_INDEX_OF_4_BYTES = new ForeignCallDescriptor(
                    "indexOf4Bytes", int.class, Pointer.class, int.class, byte.class, byte.class, byte.class, byte.class);
    public static final ForeignCallDescriptor STUB_INDEX_OF_1_CHAR = new ForeignCallDescriptor(
                    "indexOf1Char", int.class, Pointer.class, int.class, char.class);
    public static final ForeignCallDescriptor STUB_INDEX_OF_2_CHARS = new ForeignCallDescriptor(
                    "indexOf2Chars", int.class, Pointer.class, int.class, char.class, char.class);
    public static final ForeignCallDescriptor STUB_INDEX_OF_3_CHARS = new ForeignCallDescriptor(
                    "indexOf3Chars", int.class, Pointer.class, int.class, char.class, char.class, char.class);
    public static final ForeignCallDescriptor STUB_INDEX_OF_4_CHARS = new ForeignCallDescriptor(
                    "indexOf4Chars", int.class, Pointer.class, int.class, char.class, char.class, char.class, char.class);

    public static int indexOfTwoConsecutiveBytes(Pointer arrayPointer, int arrayLength, byte b1, byte b2) {
        int searchValue = (Byte.toUnsignedInt(b2) << Byte.SIZE) | Byte.toUnsignedInt(b1);
        return callInt(STUB_INDEX_OF_TWO_CONSECUTIVE_BYTES, arrayPointer, arrayLength, searchValue);
    }

    public static int indexOfTwoConsecutiveChars(Pointer arrayPointer, int arrayLength, char c1, char c2) {
        int searchValue = (c2 << Character.SIZE) | c1;
        return callInt(STUB_INDEX_OF_TWO_CONSECUTIVE_CHARS, arrayPointer, arrayLength, searchValue);
    }

    public static int indexOf1Byte(Pointer arrayPointer, int arrayLength, byte b) {
        return callByte1(STUB_INDEX_OF_1_BYTE, arrayPointer, arrayLength, b);
    }

    public static int indexOf2Bytes(Pointer arrayPointer, int arrayLength, byte b1, byte b2) {
        return callByte2(STUB_INDEX_OF_2_BYTES, arrayPointer, arrayLength, b1, b2);
    }

    public static int indexOf3Bytes(Pointer arrayPointer, int arrayLength, byte b1, byte b2, byte b3) {
        return callByte3(STUB_INDEX_OF_3_BYTES, arrayPointer, arrayLength, b1, b2, b3);
    }

    public static int indexOf4Bytes(Pointer arrayPointer, int arrayLength, byte b1, byte b2, byte b3, byte b4) {
        return callByte4(STUB_INDEX_OF_4_BYTES, arrayPointer, arrayLength, b1, b2, b3, b4);
    }

    public static int indexOf1Char(Pointer arrayPointer, int arrayLength, char c) {
        return callChar1(STUB_INDEX_OF_1_CHAR, arrayPointer, arrayLength, c);
    }

    public static int indexOf2Chars(Pointer arrayPointer, int arrayLength, char c1, char c2) {
        return callChar2(STUB_INDEX_OF_2_CHARS, arrayPointer, arrayLength, c1, c2);
    }

    public static int indexOf3Chars(Pointer arrayPointer, int arrayLength, char c1, char c2, char c3) {
        return callChar3(STUB_INDEX_OF_3_CHARS, arrayPointer, arrayLength, c1, c2, c3);
    }

    public static int indexOf4Chars(Pointer arrayPointer, int arrayLength, char c1, char c2, char c3, char c4) {
        return callChar4(STUB_INDEX_OF_4_CHARS, arrayPointer, arrayLength, c1, c2, c3, c4);
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native int callInt(@ConstantNodeParameter ForeignCallDescriptor descriptor, Pointer arrayPointer, int arrayLength, int v1);

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native int callByte1(@ConstantNodeParameter ForeignCallDescriptor descriptor, Pointer arrayPointer, int arrayLength, byte v1);

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native int callByte2(@ConstantNodeParameter ForeignCallDescriptor descriptor, Pointer arrayPointer, int arrayLength, byte v1, byte v2);

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native int callByte3(@ConstantNodeParameter ForeignCallDescriptor descriptor, Pointer arrayPointer, int arrayLength, byte v1, byte v2, byte v3);

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native int callByte4(@ConstantNodeParameter ForeignCallDescriptor descriptor, Pointer arrayPointer, int arrayLength, byte v1, byte v2, byte v3, byte v4);

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native int callChar1(@ConstantNodeParameter ForeignCallDescriptor descriptor, Pointer arrayPointer, int arrayLength, char v1);

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native int callChar2(@ConstantNodeParameter ForeignCallDescriptor descriptor, Pointer arrayPointer, int arrayLength, char v1, char v2);

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native int callChar3(@ConstantNodeParameter ForeignCallDescriptor descriptor, Pointer arrayPointer, int arrayLength, char v1, char v2, char v3);

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native int callChar4(@ConstantNodeParameter ForeignCallDescriptor descriptor, Pointer arrayPointer, int arrayLength, char v1, char v2, char v3, char v4);
}
