/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.classfile.impl;


import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import java.lang.classfile.BufWriter;
import java.lang.classfile.WritableElement;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantPool;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.PoolEntry;

public final class BufWriterImpl implements BufWriter {

    private final ConstantPoolBuilder constantPool;
    private final ClassFileImpl context;
    private LabelContext labelContext;
    private final ClassEntry thisClass;
    private final int majorVersion;
    byte[] elems;
    int offset = 0;

    public BufWriterImpl(ConstantPoolBuilder constantPool, ClassFileImpl context) {
        this(constantPool, context, 64, null, 0);
    }

    public BufWriterImpl(ConstantPoolBuilder constantPool, ClassFileImpl context, int initialSize) {
        this(constantPool, context, initialSize, null, 0);
    }

    public BufWriterImpl(ConstantPoolBuilder constantPool, ClassFileImpl context, int initialSize, ClassEntry thisClass, int majorVersion) {
        this.constantPool = constantPool;
        this.context = context;
        elems = new byte[initialSize];
        this.thisClass = thisClass;
        this.majorVersion = majorVersion;
    }

    @Override
    public ConstantPoolBuilder constantPool() {
        return constantPool;
    }

    public LabelContext labelContext() {
        return labelContext;
    }

    public void setLabelContext(LabelContext labelContext) {
        this.labelContext = labelContext;
    }
    @Override
    public boolean canWriteDirect(ConstantPool other) {
        return constantPool.canWriteDirect(other);
    }

    public ClassEntry thisClass() {
        return thisClass;
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public ClassFileImpl context() {
        return context;
    }

    @Override
    public void writeU1(int x) {
        writeIntBytes(1, x);
    }

    @Override
    public void writeU2(int x) {
        writeIntBytes(2, x);
    }

    @Override
    public void writeInt(int x) {
        writeIntBytes(4, x);
    }

    @Override
    public void writeFloat(float x) {
        writeInt(Float.floatToIntBits(x));
    }

    @Override
    public void writeLong(long x) {
        writeIntBytes(8, x);
    }

    @Override
    public void writeDouble(double x) {
        writeLong(Double.doubleToLongBits(x));
    }

    @Override
    public void writeBytes(byte[] arr) {
        writeBytes(arr, 0, arr.length);
    }

    @Override
    public void writeBytes(BufWriter other) {
        BufWriterImpl o = (BufWriterImpl) other;
        writeBytes(o.elems, 0, o.offset);
    }

    @Override
    public void writeBytes(byte[] arr, int start, int length) {
        reserveSpace(length);
        System.arraycopy(arr, start, elems, offset, length);
        offset += length;
    }

    @Override
    public void patchInt(int offset, int size, int value) {
        int prevOffset = this.offset;
        this.offset = offset;
        writeIntBytes(size, value);
        this.offset = prevOffset;
    }

    @Override
    public void writeIntBytes(int intSize, long intValue) {
        reserveSpace(intSize);
        for (int i = 0; i < intSize; i++) {
            elems[offset++] = (byte) ((intValue >> 8 * (intSize - i - 1)) & 0xFF);
        }
    }

    @Override
    public void reserveSpace(int freeBytes) {
        if (offset + freeBytes > elems.length) {
            int newsize = elems.length * 2;
            while (offset + freeBytes > newsize) {
                newsize *= 2;
            }
            elems = Arrays.copyOf(elems, newsize);
        }
    }

    @Override
    public int size() {
        return offset;
    }

    public ByteBuffer asByteBuffer() {
        return ByteBuffer.wrap(elems, 0, offset).slice();
    }

    @Override
    public void copyTo(byte[] array, int bufferOffset) {
        System.arraycopy(elems, 0, array, bufferOffset, size());
    }

    // writeIndex methods ensure that any CP info written
    // is relative to the correct constant pool

    @Override
    public void writeIndex(PoolEntry entry) {
        int idx = AbstractPoolEntry.maybeClone(constantPool, entry).index();
        if (idx < 1 || idx > Character.MAX_VALUE)
            throw new IllegalArgumentException(idx + " is not a valid index. Entry: " + entry);
        writeU2(idx);
    }

    @Override
    public void writeIndexOrZero(PoolEntry entry) {
        if (entry == null || entry.index() == 0)
            writeU2(0);
        else
            writeIndex(entry);
    }

    @Override
    public<T extends WritableElement<?>> void writeList(List<T> list) {
        writeU2(list.size());
        for (T t : list) {
            t.writeTo(this);
        }
    }

    @Override
    public void writeListIndices(List<? extends PoolEntry> list) {
        writeU2(list.size());
        for (PoolEntry info : list) {
            writeIndex(info);
        }
    }
}
