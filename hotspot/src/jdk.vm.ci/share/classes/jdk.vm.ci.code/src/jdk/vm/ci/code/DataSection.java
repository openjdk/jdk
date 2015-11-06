/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.code;

import static jdk.vm.ci.meta.MetaUtil.identityHashCodeString;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;

import jdk.vm.ci.code.CompilationResult.DataPatch;
import jdk.vm.ci.code.CompilationResult.DataSectionReference;
import jdk.vm.ci.code.DataSection.Data;
import jdk.vm.ci.meta.SerializableConstant;

public final class DataSection implements Iterable<Data> {

    @FunctionalInterface
    public interface DataBuilder {

        void emit(ByteBuffer buffer, Consumer<DataPatch> patch);

        static DataBuilder raw(byte[] data) {
            return (buffer, patch) -> buffer.put(data);
        }

        static DataBuilder serializable(SerializableConstant c) {
            return (buffer, patch) -> c.serialize(buffer);
        }

        static DataBuilder zero(int size) {
            switch (size) {
                case 1:
                    return (buffer, patch) -> buffer.put((byte) 0);
                case 2:
                    return (buffer, patch) -> buffer.putShort((short) 0);
                case 4:
                    return (buffer, patch) -> buffer.putInt(0);
                case 8:
                    return (buffer, patch) -> buffer.putLong(0L);
                default:
                    return (buffer, patch) -> {
                        int rest = size;
                        while (rest > 8) {
                            buffer.putLong(0L);
                            rest -= 8;
                        }
                        while (rest > 0) {
                            buffer.put((byte) 0);
                            rest--;
                        }
                    };
            }
        }
    }

    public static final class Data {

        private int alignment;
        private final int size;
        private final DataBuilder builder;

        private DataSectionReference ref;

        public Data(int alignment, int size, DataBuilder builder) {
            this.alignment = alignment;
            this.size = size;
            this.builder = builder;

            // initialized in DataSection.insertData(Data)
            ref = null;
        }

        public void updateAlignment(int newAlignment) {
            if (newAlignment == alignment) {
                return;
            }
            alignment = lcm(alignment, newAlignment);
        }

        public int getAlignment() {
            return alignment;
        }

        public int getSize() {
            return size;
        }

        public DataBuilder getBuilder() {
            return builder;
        }

        @Override
        public int hashCode() {
            // Data instances should not be used as hash map keys
            throw new UnsupportedOperationException("hashCode");
        }

        @Override
        public String toString() {
            return identityHashCodeString(this);
        }

        @Override
        public boolean equals(Object obj) {
            assert ref != null;
            if (obj == this) {
                return true;
            }
            if (obj instanceof Data) {
                Data that = (Data) obj;
                if (this.alignment == that.alignment && this.size == that.size && this.ref.equals(that.ref)) {
                    return true;
                }
            }
            return false;
        }
    }

    private final ArrayList<Data> dataItems = new ArrayList<>();

    private boolean finalLayout;
    private int sectionAlignment;
    private int sectionSize;

    @Override
    public int hashCode() {
        // DataSection instances should not be used as hash map keys
        throw new UnsupportedOperationException("hashCode");
    }

    @Override
    public String toString() {
        return identityHashCodeString(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof DataSection) {
            DataSection that = (DataSection) obj;
            if (this.finalLayout == that.finalLayout && this.sectionAlignment == that.sectionAlignment && this.sectionSize == that.sectionSize && Objects.equals(this.dataItems, that.dataItems)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Insert a {@link Data} item into the data section. If the item is already in the data section,
     * the same {@link DataSectionReference} is returned.
     *
     * @param data the {@link Data} item to be inserted
     * @return a unique {@link DataSectionReference} identifying the {@link Data} item
     */
    public DataSectionReference insertData(Data data) {
        assert !finalLayout;
        synchronized (data) {
            if (data.ref == null) {
                data.ref = new DataSectionReference();
                dataItems.add(data);
            }
            return data.ref;
        }
    }

    /**
     * Transfers all {@link Data} from the provided other {@link DataSection} to this
     * {@link DataSection}, and empties the other section.
     */
    public void addAll(DataSection other) {
        assert !finalLayout && !other.finalLayout;

        for (Data data : other.dataItems) {
            assert data.ref != null;
            dataItems.add(data);
        }
        other.dataItems.clear();
    }

    /**
     * Compute the layout of the data section. This can be called only once, and after it has been
     * called, the data section can no longer be modified.
     */
    public void finalizeLayout() {
        assert !finalLayout;
        finalLayout = true;

        // simple heuristic: put items with larger alignment requirement first
        dataItems.sort((a, b) -> a.alignment - b.alignment);

        int position = 0;
        int alignment = 1;
        for (Data d : dataItems) {
            alignment = lcm(alignment, d.alignment);
            position = align(position, d.alignment);

            d.ref.setOffset(position);
            position += d.size;
        }

        sectionAlignment = alignment;
        sectionSize = position;
    }

    public boolean isFinalized() {
        return finalLayout;
    }

    /**
     * Get the size of the data section. Can only be called after {@link #finalizeLayout}.
     */
    public int getSectionSize() {
        assert finalLayout;
        return sectionSize;
    }

    /**
     * Get the minimum alignment requirement of the data section. Can only be called after
     * {@link #finalizeLayout}.
     */
    public int getSectionAlignment() {
        assert finalLayout;
        return sectionAlignment;
    }

    /**
     * Build the data section. Can only be called after {@link #finalizeLayout}.
     *
     * @param buffer The {@link ByteBuffer} where the data section should be built. The buffer must
     *            hold at least {@link #getSectionSize()} bytes.
     * @param patch A {@link Consumer} to receive {@link DataPatch data patches} for relocations in
     *            the data section.
     */
    public void buildDataSection(ByteBuffer buffer, Consumer<DataPatch> patch) {
        assert finalLayout;
        for (Data d : dataItems) {
            buffer.position(d.ref.getOffset());
            d.builder.emit(buffer, patch);
        }
    }

    public Data findData(DataSectionReference ref) {
        for (Data d : dataItems) {
            if (d.ref == ref) {
                return d;
            }
        }
        return null;
    }

    public Iterator<Data> iterator() {
        return dataItems.iterator();
    }

    public static int lcm(int x, int y) {
        if (x == 0) {
            return y;
        } else if (y == 0) {
            return x;
        }

        int a = Math.max(x, y);
        int b = Math.min(x, y);
        while (b > 0) {
            int tmp = a % b;
            a = b;
            b = tmp;
        }

        int gcd = a;
        return x * y / gcd;
    }

    private static int align(int position, int alignment) {
        return ((position + alignment - 1) / alignment) * alignment;
    }

    public void clear() {
        assert !finalLayout;
        this.dataItems.clear();
        this.sectionAlignment = 0;
        this.sectionSize = 0;
    }
}
