/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.util.Objects;

import jdk.internal.misc.Unsafe;

import static jdk.internal.misc.Unsafe.ARRAY_BYTE_BASE_OFFSET;

/**
 * Provide a simplified ByteBuffer implementation to improve startup performance
 */
public final class ByteBuffer {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    final byte[] hb;
    final int offset;
    final int limit;
    private final int capacity;
    private int position = 0;

    private ByteBuffer(int pos, int lim, int cap, byte[] hb, int offset) {
        this.hb = hb;
        this.offset = offset;
        this.capacity = cap;
        this.limit = lim;
        this.position = pos;
    }

    private ByteBuffer(byte[] buf, int pos, int lim, int cap, int off) {
        this(pos, lim, cap, buf, off);
    }

    private ByteBuffer(byte[] buf, int off, int len) {
        this(off, off + len, buf.length, buf, 0);
    }

    public static ByteBuffer wrap(byte[] array) {
        return wrap(array, 0, array.length);
    }

    public static ByteBuffer wrap(byte[] array, int offset, int length) {
        return new ByteBuffer(array, offset, length);
    }

    public ByteBuffer slice() {
        int pos = this.position;
        int lim = this.limit;
        int rem = (pos <= lim ? lim - pos : 0);
        return new ByteBuffer(hb, 0, rem, rem, pos + offset);
    }

    public ByteBuffer slice(int index, int length) {
        Objects.checkFromIndexSize(index, length, limit());
        return new ByteBuffer(hb, 0, length, length, index + offset);
    }

    public int capacity() {
        return capacity;
    }

    public int limit() {
        return limit;
    }

    public int position() {
        return position;
    }

    public ByteBuffer position(int newPosition) {
        if (newPosition > limit | newPosition < 0)
            throw createPositionException(newPosition);
        position = newPosition;
        return this;
    }

    private IllegalArgumentException createPositionException(int newPosition) {
        String msg = null;

        if (newPosition > limit) {
            msg = "newPosition > limit: (" + newPosition + " > " + limit + ")";
        } else { // assume negative
            assert newPosition < 0 : "newPosition expected to be negative";
            msg = "newPosition < 0: (" + newPosition + " < 0)";
        }

        return new IllegalArgumentException(msg);
    }

    private int checkIndex(int i) {
        if (i < 0 || i >= limit)
            throw new IndexOutOfBoundsException();
        return i;
    }

    private int checkIndex(int i, int nb) {
        if (i < 0 || i >= limit - nb + 1)
            throw new IndexOutOfBoundsException();
        return i;
    }

    private long byteOffset(long i) {
        return ARRAY_BYTE_BASE_OFFSET + i;
    }

    private int ix(int i) {
        return i + offset;
    }

    private int nextGetIndex() {
        int p = position;
        if (p >= limit)
            throw new BufferUnderflowException();
        position = p + 1;
        return p;
    }

    private int nextGetIndex(int nb) {
        int p = position;
        if (limit - p < nb)
            throw new BufferUnderflowException();
        position = p + nb;
        return p;
    }

    private int nextPutIndex() {
        int p = position;
        if (p >= limit)
            throw new BufferOverflowException();
        position = p + 1;
        return p;
    }

    public byte get() {
        return hb[ix(nextGetIndex())];
    }

    public byte get(int i) {
        return hb[ix(checkIndex(i))];
    }

    public int getInt() {
        return UNSAFE.getIntUnaligned(hb, byteOffset(nextGetIndex(4)), true);
    }

    public short getShort(int i) {
        return UNSAFE.getShortUnaligned(hb, byteOffset(checkIndex(i, 2)), true);
    }

    public int getInt(int i) {
        return UNSAFE.getIntUnaligned(hb, byteOffset(checkIndex(i, 2)), true);
    }

    public ByteBuffer put(byte x) {
        hb[ix(nextPutIndex())] = x;
        return this;
    }

    public byte[] array() {
        return hb;
    }

    public ByteBuffer rewind() {
        position = 0;
        return this;
    }
}
