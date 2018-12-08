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


package org.graalvm.compiler.lir.asm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import org.graalvm.compiler.core.common.type.DataPointerConstant;

/**
 * Class for chunks of data that go into the data section.
 */
public class ArrayDataPointerConstant extends DataPointerConstant {

    private final byte[] data;

    public ArrayDataPointerConstant(byte[] array, int alignment) {
        super(alignment);
        data = array.clone();
    }

    public ArrayDataPointerConstant(short[] array, int alignment) {
        super(alignment);
        ByteBuffer byteBuffer = ByteBuffer.allocate(array.length * 2);
        byteBuffer.order(ByteOrder.nativeOrder());
        byteBuffer.asShortBuffer().put(array);
        data = byteBuffer.array();
    }

    public ArrayDataPointerConstant(int[] array, int alignment) {
        super(alignment);
        ByteBuffer byteBuffer = ByteBuffer.allocate(array.length * 4);
        byteBuffer.order(ByteOrder.nativeOrder());
        byteBuffer.asIntBuffer().put(array);
        data = byteBuffer.array();
    }

    public ArrayDataPointerConstant(float[] array, int alignment) {
        super(alignment);
        ByteBuffer byteBuffer = ByteBuffer.allocate(array.length * 4);
        byteBuffer.order(ByteOrder.nativeOrder());
        byteBuffer.asFloatBuffer().put(array);
        data = byteBuffer.array();
    }

    public ArrayDataPointerConstant(double[] array, int alignment) {
        super(alignment);
        ByteBuffer byteBuffer = ByteBuffer.allocate(array.length * 8);
        byteBuffer.order(ByteOrder.nativeOrder());
        byteBuffer.asDoubleBuffer().put(array);
        data = byteBuffer.array();
    }

    public ArrayDataPointerConstant(long[] array, int alignment) {
        super(alignment);
        ByteBuffer byteBuffer = ByteBuffer.allocate(array.length * 8);
        byteBuffer.order(ByteOrder.nativeOrder());
        byteBuffer.asLongBuffer().put(array);
        data = byteBuffer.array();
    }

    @Override
    public boolean isDefaultForKind() {
        return false;
    }

    @Override
    public void serialize(ByteBuffer buffer) {
        buffer.put(data);
    }

    @Override
    public int getSerializedSize() {
        return data.length;
    }

    @Override
    public String toValueString() {
        return "ArrayDataPointerConstant" + Arrays.toString(data);
    }
}
