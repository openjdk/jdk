/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.internal.jimage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

class ImageStream {
    private ByteBuffer buffer;

    ImageStream() {
        this(1024, ByteOrder.nativeOrder());
    }

    ImageStream(int size) {
        this(size, ByteOrder.nativeOrder());
    }

    ImageStream(byte[] bytes) {
       this(bytes, ByteOrder.nativeOrder());
    }

    ImageStream(ByteOrder byteOrder) {
        this(1024, byteOrder);
    }

    ImageStream(int size, ByteOrder byteOrder) {
        buffer = ByteBuffer.allocate(size);
        buffer.order(byteOrder);
    }

    ImageStream(byte[] bytes, ByteOrder byteOrder) {
        buffer = ByteBuffer.wrap(bytes);
        buffer.order(byteOrder);
    }

    ImageStream(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    ImageStream align(int alignment) {
        int padding = (getSize() - 1) & ((1 << alignment) - 1);

        for (int i = 0; i < padding; i++) {
            put((byte)0);
        }

        return this;
    }

    private void ensure(int needs) {
        assert 0 <= needs : "Negative needs";

        if (needs > buffer.remaining()) {
            byte[] bytes = buffer.array();
            ByteOrder byteOrder = buffer.order();
            int position = buffer.position();
            int newSize = needs <= bytes.length ? bytes.length << 1 : position + needs;
            buffer = ByteBuffer.allocate(newSize);
            buffer.order(byteOrder);
            buffer.put(bytes, 0, position);
        }
    }

    boolean hasByte() {
        return buffer.remaining() != 0;
    }

    boolean hasBytes(int needs) {
        return needs <= buffer.remaining();
    }

    void skip(int n) {
        assert 0 <= n : "Negative offset";
        buffer.position(buffer.position() + n);
    }

    int get() {
        return buffer.get() & 0xFF;
    }

    void get(byte bytes[], int offset, int size) {
        buffer.get(bytes, offset, size);
    }

    int getShort() {
        return buffer.getShort();
    }

    int getInt() {
        return buffer.getInt();
    }

    long getLong() {
        return buffer.getLong();
    }

    ImageStream put(byte byt) {
        ensure(1);
        buffer.put(byt);

        return this;
    }

    ImageStream put(int byt) {
        return put((byte)byt);
    }

    ImageStream put(byte bytes[], int offset, int size) {
        ensure(size);
        buffer.put(bytes, offset, size);

        return this;
    }

    ImageStream put(ImageStream stream) {
        put(stream.buffer.array(), 0, stream.buffer.position());

        return this;
    }

    ImageStream putShort(short value) {
        ensure(2);
        buffer.putShort(value);

        return this;
    }

    ImageStream putShort(int value) {
        return putShort((short)value);
    }

    ImageStream putInt(int value) {
        ensure(4);
        buffer.putInt(value);

        return this;
    }

    ImageStream putLong(long value) {
        ensure(8);
        buffer.putLong(value);

        return this;
    }

    ByteBuffer getBuffer() {
        return buffer;
    }

    int getPosition() {
        return buffer.position();
    }

    int getSize() {
        return buffer.position();
    }

    byte[] getBytes() {
        return buffer.array();
    }

    void setPosition(int offset) {
        buffer.position(offset);
    }

    byte[] toArray() {
        return Arrays.copyOf(buffer.array(), buffer.position());
    }
}
