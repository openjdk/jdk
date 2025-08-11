/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
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

import java.lang.classfile.BufWriter;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantPool;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.PoolEntry;
import java.util.Arrays;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.vm.annotation.ForceInline;

import static java.lang.classfile.constantpool.PoolEntry.TAG_UTF8;
import static jdk.internal.util.ModifiedUtf.putChar;
import static jdk.internal.util.ModifiedUtf.utfLen;

public final class BufWriterImpl implements BufWriter {
    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    private final ConstantPoolBuilder constantPool;
    private final ClassFileImpl context;
    private LabelContext labelContext;
    private boolean labelsMatch;
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

    public void setLabelContext(LabelContext labelContext, boolean labelsMatch) {
        this.labelContext = labelContext;
        this.labelsMatch = labelsMatch;
    }

    public boolean labelsMatch(LabelContext lc) {
        return labelsMatch
                && labelContext instanceof DirectCodeBuilder dcb
                && dcb.original == lc;
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
        reserveSpace(1);
        elems[offset++] = (byte) x;
    }

    @ForceInline
    @Override
    public void writeU2(int x) {
        reserveSpace(2);
        byte[] elems = this.elems;
        int offset = this.offset;
        elems[offset    ] = (byte) (x >> 8);
        elems[offset + 1] = (byte) x;
        this.offset = offset + 2;
    }

    @ForceInline
    public void writeU1U1(int x1, int x2) {
        reserveSpace(2);
        byte[] elems = this.elems;
        int offset = this.offset;
        elems[offset    ] = (byte) x1;
        elems[offset + 1] = (byte) x2;
        this.offset = offset + 2;
    }

    public void writeU1U2(int u1, int u2) {
        reserveSpace(3);
        byte[] elems = this.elems;
        int offset = this.offset;
        elems[offset    ] = (byte) u1;
        elems[offset + 1] = (byte) (u2 >> 8);
        elems[offset + 2] = (byte) u2;
        this.offset = offset + 3;
    }

    public void writeU1U1U1(int x1, int x2, int x3) {
        reserveSpace(3);
        byte[] elems = this.elems;
        int offset = this.offset;
        elems[offset    ] = (byte) x1;
        elems[offset + 1] = (byte) x2;
        elems[offset + 2] = (byte) x3;
        this.offset = offset + 3;
    }

    public void writeU1U1U2(int x1, int x2, int x3) {
        reserveSpace(4);
        byte[] elems = this.elems;
        int offset = this.offset;
        elems[offset    ] = (byte) x1;
        elems[offset + 1] = (byte) x2;
        elems[offset + 2] = (byte) (x3 >> 8);
        elems[offset + 3] = (byte) x3;
        this.offset = offset + 4;
    }

    public void writeU1U2U2(int x1, int x2, int x3) {
        reserveSpace(5);
        byte[] elems = this.elems;
        int offset = this.offset;
        elems[offset    ] = (byte) x1;
        elems[offset + 1] = (byte) (x2 >> 8);
        elems[offset + 2] = (byte) x2;
        elems[offset + 3] = (byte) (x3 >> 8);
        elems[offset + 4] = (byte) x3;
        this.offset = offset + 5;
    }

    public void writeU2U1(int x1, int x2) {
        reserveSpace(3);
        byte[] elems = this.elems;
        int offset = this.offset;
        elems[offset    ] = (byte) (x1 >> 8);
        elems[offset + 1] = (byte) x1;
        elems[offset + 2] = (byte) x2;
        this.offset = offset + 3;
    }

    public void writeU2U2(int x1, int x2) {
        reserveSpace(4);
        byte[] elems = this.elems;
        int offset = this.offset;
        elems[offset    ] = (byte) (x1 >> 8);
        elems[offset + 1] = (byte) x1;
        elems[offset + 2] = (byte) (x2 >> 8);
        elems[offset + 3] = (byte) x2;
        this.offset = offset + 4;
    }

    public void writeU2U2U2(int x1, int x2, int x3) {
        reserveSpace(6);
        byte[] elems = this.elems;
        int offset = this.offset;
        elems[offset    ] = (byte) (x1 >> 8);
        elems[offset + 1] = (byte) x1;
        elems[offset + 2] = (byte) (x2 >> 8);
        elems[offset + 3] = (byte) x2;
        elems[offset + 4] = (byte) (x3 >> 8);
        elems[offset + 5] = (byte) x3;
        this.offset = offset + 6;
    }

    @Override
    public void writeInt(int x) {
        reserveSpace(4);
        byte[] elems = this.elems;
        int offset = this.offset;
        elems[offset    ] = (byte) (x >> 24);
        elems[offset + 1] = (byte) (x >> 16);
        elems[offset + 2] = (byte) (x >> 8);
        elems[offset + 3] = (byte)  x;
        this.offset = offset + 4;
    }

    public void writeIntInt(int x1, int x2) {
        reserveSpace(8);
        byte[] elems = this.elems;
        int offset = this.offset;
        elems[offset    ] = (byte) (x1 >> 24);
        elems[offset + 1] = (byte) (x1 >> 16);
        elems[offset + 2] = (byte) (x1 >> 8);
        elems[offset + 3] = (byte)  x1;
        elems[offset + 4] = (byte) (x2 >> 24);
        elems[offset + 5] = (byte) (x2 >> 16);
        elems[offset + 6] = (byte) (x2 >> 8);
        elems[offset + 7] = (byte)  x2;
        this.offset = offset + 8;
    }

    @Override
    public void writeFloat(float x) {
        writeInt(Float.floatToIntBits(x));
    }

    @Override
    public void writeLong(long x) {
        reserveSpace(8);
        byte[] elems = this.elems;
        int offset = this.offset;
        elems[offset    ] = (byte) (x >> 56);
        elems[offset + 1] = (byte) (x >> 48);
        elems[offset + 2] = (byte) (x >> 40);
        elems[offset + 3] = (byte) (x >> 32);
        elems[offset + 4] = (byte) (x >> 24);
        elems[offset + 5] = (byte) (x >> 16);
        elems[offset + 6] = (byte) (x >> 8);
        elems[offset + 7] = (byte)  x;
        this.offset = offset + 8;
    }

    @Override
    public void writeDouble(double x) {
        writeLong(Double.doubleToLongBits(x));
    }

    @Override
    public void writeBytes(byte[] arr) {
        writeBytes(arr, 0, arr.length);
    }

    public void writeBytes(BufWriterImpl other) {
        writeBytes(other.elems, 0, other.offset);
    }

    @SuppressWarnings("deprecation")
    void writeUtfEntry(String str) {
        int strlen = str.length();
        int countNonZeroAscii = JLA.countNonZeroAscii(str);
        int utflen = utfLen(str, countNonZeroAscii);
        if (utflen > 65535) {
            throw new IllegalArgumentException("string too long");
        }
        reserveSpace(utflen + 3);

        int offset = this.offset;
        byte[] elems = this.elems;

        elems[offset    ] = (byte) TAG_UTF8;
        elems[offset + 1] = (byte) (utflen >> 8);
        elems[offset + 2] = (byte)  utflen;
        offset += 3;

        str.getBytes(0, countNonZeroAscii, elems, offset);
        offset += countNonZeroAscii;

        for (int i = countNonZeroAscii; i < strlen; i++) {
            offset = putChar(elems, offset, str.charAt(i));
        }

        this.offset = offset;
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

    public void patchU2(int offset, int x) {
        byte[] elems = this.elems;
        elems[offset    ] = (byte) (x >> 8);
        elems[offset + 1] = (byte)  x;
    }

    public void patchInt(int offset, int x) {
        byte[] elems = this.elems;
        elems[offset    ] = (byte) (x >> 24);
        elems[offset + 1] = (byte) (x >> 16);
        elems[offset + 2] = (byte) (x >> 8);
        elems[offset + 3] = (byte)  x;
    }

    @Override
    public void writeIntBytes(int intSize, long intValue) {
        reserveSpace(intSize);
        for (int i = 0; i < intSize; i++) {
            elems[offset++] = (byte) ((intValue >> 8 * (intSize - i - 1)) & 0xFF);
        }
    }

    /**
     * Skip a few bytes in the output buffer. The skipped area has undefined value.
     * @param bytes number of bytes to skip
     * @return the index, for later patching
     */
    public int skip(int bytes) {
        int now = offset;
        reserveSpace(bytes);
        offset += bytes;
        return now;
    }

    @Override
    public void reserveSpace(int freeBytes) {
        int minCapacity = offset + freeBytes;
        if (minCapacity > elems.length) {
            grow(minCapacity);
        }
    }

    private void grow(int minCapacity) {
        int newsize = elems.length * 2;
        while (minCapacity > newsize) {
            newsize *= 2;
        }
        elems = Arrays.copyOf(elems, newsize);
    }

    @Override
    public int size() {
        return offset;
    }

    public RawBytecodeHelper.CodeRange bytecodeView() {
        return RawBytecodeHelper.of(elems, offset);
    }

    public void copyTo(byte[] array, int bufferOffset) {
        System.arraycopy(elems, 0, array, bufferOffset, size());
    }

    // writeIndex methods ensure that any CP info written
    // is relative to the correct constant pool

    public int cpIndex(PoolEntry entry) {
        int idx = AbstractPoolEntry.maybeClone(constantPool, entry).index();
        if (idx < 1 || idx > Character.MAX_VALUE)
            throw invalidIndex(idx, entry);
        return idx;
    }

    public int cpIndexOrZero(PoolEntry entry) {
        if (entry == null || entry.index() == 0)
            return 0;
        return cpIndex(entry);
    }

    @ForceInline
    @Override
    public void writeIndex(PoolEntry entry) {
        writeU2(cpIndex(entry));
    }

    // Null checks entry
    public void writeIndex(int bytecode, PoolEntry entry) {
        writeU1U2(bytecode, cpIndex(entry));
    }

    static IllegalArgumentException invalidIndex(int idx, PoolEntry entry) {
        return new IllegalArgumentException(idx + " is not a valid index. Entry: " + entry);
    }

    @Override
    public void writeIndexOrZero(PoolEntry entry) {
        writeU2(cpIndexOrZero(entry));
    }

    /**
     * Join head and tail into an exact-size buffer
     */
    static byte[] join(BufWriterImpl head, BufWriterImpl tail) {
        byte[] result = new byte[head.size() + tail.size()];
        head.copyTo(result, 0);
        tail.copyTo(result, head.size());
        return result;
    }
}
