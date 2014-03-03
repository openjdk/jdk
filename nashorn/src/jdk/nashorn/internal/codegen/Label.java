/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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
package jdk.nashorn.internal.codegen;

import jdk.nashorn.internal.codegen.types.Type;

/**
 * Abstraction for labels, separating a label from the underlying
 * byte code emitter. Also augmenting label with e.g. a name
 * for easier debugging and reading code
 *
 * see -Dnashorn.codegen.debug, --log=codegen
 */
public final class Label {
    //byte code generation evaluation type stack for consistency check
    //and correct opcode selection. one per label as a label may be a
    //join point
    static final class Stack {
        static final int NON_LOAD = -1;

        Type[] data       = new Type[8];
        int[]  localLoads = new int[8];
        int    sp;

        Stack() {
        }

        private Stack(final Stack original) {
            this();
            this.sp   = original.sp;
            this.data = new Type[original.data.length];
            System.arraycopy(original.data, 0, data, 0, sp);
            this.localLoads = new int[original.localLoads.length];
            System.arraycopy(original.localLoads, 0, localLoads, 0, sp);
        }

        boolean isEmpty() {
            return sp == 0;
        }

        int size() {
            return sp;
        }

        boolean isEquivalentInTypesTo(final Stack other) {
            if (sp != other.sp) {
                return false;
            }
            for (int i = 0; i < sp; i++) {
                if (!data[i].isEquivalentTo(other.data[i])) {
                    return false;
                }
            }
            return true;
        }

        void clear() {
            sp = 0;
        }

        void push(final Type type) {
            if (data.length == sp) {
                final Type[] newData = new Type[sp * 2];
                final int[]  newLocalLoad = new int[sp * 2];
                System.arraycopy(data, 0, newData, 0, sp);
                System.arraycopy(localLoads, 0, newLocalLoad, 0, sp);
                data = newData;
                localLoads = newLocalLoad;
            }
            data[sp] = type;
            localLoads[sp] = NON_LOAD;
            sp++;
        }

        Type peek() {
            return peek(0);
        }

        Type peek(final int n) {
            int pos = sp - 1 - n;
            return pos < 0 ? null : data[pos];
        }

        /**
         * Retrieve the top <tt>count</tt> types on the stack without modifying it.
         *
         * @param count number of types to return
         * @return array of Types
         */
        Type[] getTopTypes(final int count) {
            final Type[] topTypes = new Type[count];
            System.arraycopy(data, sp - count, topTypes, 0, count);
            return topTypes;
        }

        int[] getLocalLoads(final int from, final int to) {
            final int count = to - from;
            final int[] topLocalLoads = new int[count];
            System.arraycopy(localLoads, from, topLocalLoads, 0, count);
            return topLocalLoads;
        }

        /**
         * When joining branches, local loads that differ on different branches are invalidated.
         * @param other the stack from the other branch.
         */
        void mergeLocalLoads(final Stack other) {
            final int[] otherLoads = other.localLoads;
            for(int i = 0; i < sp; ++i) {
                if(localLoads[i] != otherLoads[i]) {
                    localLoads[i] = NON_LOAD;
                }
            }
        }

        Type pop() {
            return data[--sp];
        }

        Stack copy() {
            return new Stack(this);
        }

        int getTopLocalLoad() {
            return localLoads[sp - 1];
        }

        void markLocalLoad(final int slot) {
            localLoads[sp - 1] = slot;
        }

        /**
         * If we store a value in a local slot, it invalidates any on-stack loads from that same slot, as the values
         * could have changed.
         * @param slot the slot written to
         * @param slotCount the size of the value, either 1 or 2 slots
         */
        void markLocalStore(final int slot, final int slotCount) {
            for(int i = 0; i < sp; ++i) {
                final int load = localLoads[i];
                if(load == slot || load == slot + slotCount - 1) {
                    localLoads[i] = NON_LOAD;
                }
            }
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder("[");
            for (int i = 0; i < sp; i++) {
                builder.append(data[i]);
                if (i < sp - 1) {
                    builder.append(", ");
                }
            }
            return builder.append("]").toString();
        }
    }

    /** Name of this label */
    private final String name;

    /** Type stack at this label */
    private Label.Stack stack;

    /** ASM representation of this label */
    private jdk.internal.org.objectweb.asm.Label label;

    /** Id for debugging purposes, remove if footprint becomes unmanageable */
    private final int id;

    /** Next id for debugging purposes, remove if footprint becomes unmanageable */
    private static int nextId = 0;

    /**
     * Constructor
     *
     * @param name name of this label
     */
    public Label(final String name) {
        super();
        this.name = name;
        this.id   = nextId++;
    }

    /**
     * Copy constructor
     *
     * @param label a label to clone
     */
    public Label(final Label label) {
        super();
        this.name = label.name;
        this.id   = label.id;
    }


    jdk.internal.org.objectweb.asm.Label getLabel() {
        if (this.label == null) {
            this.label = new jdk.internal.org.objectweb.asm.Label();
        }
        return label;
    }

    Label.Stack getStack() {
        return stack;
    }

    void setStack(final Label.Stack stack) {
        this.stack = stack;
    }

    @Override
    public String toString() {
        return name + '_' + id;
    }
}
