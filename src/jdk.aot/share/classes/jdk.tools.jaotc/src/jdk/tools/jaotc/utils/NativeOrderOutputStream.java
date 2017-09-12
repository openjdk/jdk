/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.tools.jaotc.utils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public final class NativeOrderOutputStream {
    private final PatchableByteOutputStream os = new PatchableByteOutputStream();
    private final byte[] backingArray = new byte[8];
    private final ByteBuffer buffer;
    private final List<Patchable> patches = new ArrayList<>();
    private int size;

    public NativeOrderOutputStream() {
        buffer = ByteBuffer.wrap(backingArray);
        buffer.order(ByteOrder.nativeOrder());
    }

    public static int alignUp(int value, int alignment) {
        if (Integer.bitCount(alignment) != 1) {
            throw new IllegalArgumentException("Must be a power of 2");
        }

        int aligned = (value + (alignment - 1)) & -alignment;

        if (aligned < value || (aligned & (alignment - 1)) != 0) {
            throw new RuntimeException("Error aligning: " + value + " -> " + aligned);
        }
        return aligned;
    }

    public NativeOrderOutputStream putLong(long value) {
        fillLong(value);
        os.write(backingArray, 0, 8);
        size += 8;
        return this;
    }

    public NativeOrderOutputStream putInt(int value) {
        fillInt(value);
        os.write(backingArray, 0, 4);
        size += 4;
        return this;
    }

    public NativeOrderOutputStream align(int alignment) {
        int aligned = alignUp(position(), alignment);

        int diff = aligned - position();
        if (diff > 0) {
            byte[] b = new byte[diff];
            os.write(b, 0, b.length);
            size += diff;
        }

        assert aligned == position();
        return this;
    }

    public int position() {
        return os.size();
    }

    private void fillInt(int value) {
        buffer.putInt(0, value);
    }

    private void fillLong(long value) {
        buffer.putLong(0, value);
    }

    private NativeOrderOutputStream putAt(byte[] data, int length, int position) {
        os.writeAt(data, length, position);
        return this;
    }

    public NativeOrderOutputStream put(byte[] data) {
        os.write(data, 0, data.length);
        size += data.length;
        return this;
    }

    public byte[] array() {
        checkPatches();
        byte[] bytes = os.toByteArray();
        assert size == bytes.length;
        return bytes;
    }

    private void checkPatches() {
        for (Patchable patch : patches) {
            if (!patch.patched()) {
                throw new RuntimeException("Not all patches patched, missing at offset: " + patch);
            }
        }
    }

    public PatchableInt patchableInt() {
        int position = os.size();
        PatchableInt patchableInt = new PatchableInt(position);
        putInt(0);
        patches.add(patchableInt);
        return patchableInt;
    }

    public abstract class Patchable {
        private final int position;
        private boolean patched = false;

        Patchable(int position) {
            this.position = position;
        }

        protected boolean patched() {
            return patched;
        }

        protected void patch(int length) {
            putAt(backingArray, length, position);
            patched = true;
        }

        public int position() {
            return position;
        }
    }

    public class PatchableInt extends Patchable {
        private int value = 0;

        public PatchableInt(int position) {
            super(position);
        }

        public void set(int value) {
            this.value = value;
            fillInt(value);
            patch(4);
        }

        public int value() {
            return value;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("PatchableInt{");
            sb.append("position=").append(super.position()).append(", ");
            sb.append("patched=").append(patched()).append(", ");
            sb.append("value=").append(value);
            sb.append('}');
            return sb.toString();
        }
    }

    private static class PatchableByteOutputStream extends ByteArrayOutputStream {

        public void writeAt(byte[] data, int length, int position) {
            long end = (long) position + (long) length;
            if (buf.length < end) {
                throw new IllegalArgumentException("Array not properly sized");
            }
            System.arraycopy(data, 0, buf, position, length);
        }
    }
}
