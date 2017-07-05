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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
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
    static final class Stack implements Cloneable {
        static final int NON_LOAD = -1;

        Type[] data;
        int[]  localLoads;
        int    sp;

        List<Type> localVariableTypes;
        int firstTemp; // index of the first temporary local variable
        // Bitmap marking last slot belonging to a single symbol.
        BitSet symbolBoundary;

        Stack() {
            data = new Type[8];
            localLoads = new int[8];
            localVariableTypes = new ArrayList<>(8);
            symbolBoundary = new BitSet();
        }

        boolean isEmpty() {
            return sp == 0;
        }

        int size() {
            return sp;
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
            final int pos = sp - 1 - n;
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
         * Returns the number of used local variable slots, including all live stack-store temporaries.
         * @return the number of used local variable slots, including all live stack-store temporaries.
         */
        int getUsedSlotsWithLiveTemporaries() {
            // There are at least as many as are declared by the current blocks.
            int usedSlots = firstTemp;
            // Look at every load on the stack, and bump the number of used slots up by the temporaries seen there.
            for(int i = sp; i-->0;) {
                final int slot = localLoads[i];
                if(slot != Label.Stack.NON_LOAD) {
                    final int afterSlot = slot + localVariableTypes.get(slot).getSlots();
                    if(afterSlot > usedSlots) {
                        usedSlots = afterSlot;
                    }
                }
            }
            return usedSlots;
        }

        /**
         *
         * @param joinOrigin the stack from the other branch.
         */
        void joinFrom(final Stack joinOrigin, final boolean breakTarget) {
            assert isStackCompatible(joinOrigin);
            if(breakTarget) {
                // As we're joining labels that can jump across block boundaries, the number of local variables can
                // differ, and we should always respect the one having less variables.
                firstTemp = Math.min(firstTemp, joinOrigin.firstTemp);
            } else {
                assert firstTemp == joinOrigin.firstTemp;
            }
            final int[] otherLoads = joinOrigin.localLoads;
            int firstDeadTemp = firstTemp;
            for(int i = 0; i < sp; ++i) {
                final int localLoad = localLoads[i];
                if(localLoad != otherLoads[i]) {
                    localLoads[i] = NON_LOAD;
                } else if(localLoad >= firstDeadTemp) {
                    firstDeadTemp = localLoad + localVariableTypes.get(localLoad).getSlots();
                }
            }
            // Eliminate dead temporaries
            undefineLocalVariables(firstDeadTemp, false);
            assert isVariablePartitioningEqual(joinOrigin, firstDeadTemp);
            mergeVariableTypes(joinOrigin, firstDeadTemp);
        }

        private void mergeVariableTypes(final Stack joinOrigin, final int toSlot) {
            final ListIterator<Type> it1 = localVariableTypes.listIterator();
            final Iterator<Type> it2 = joinOrigin.localVariableTypes.iterator();

            for(int i = 0; i < toSlot; ++i) {
                final Type thisType = it1.next();
                final Type otherType = it2.next();
                if(otherType == Type.UNKNOWN) {
                    // Variables that are <unknown> on the other branch will become <unknown> here too.
                    it1.set(Type.UNKNOWN);
                } else if (thisType != otherType) {
                    if(thisType.isObject() && otherType.isObject()) {
                        // different object types are merged into Object.
                        // TODO: maybe find most common superclass?
                        it1.set(Type.OBJECT);
                    } else {
                        assert thisType == Type.UNKNOWN;
                    }
                }
            }
        }

        void joinFromTry(final Stack joinOrigin) {
            // As we're joining labels that can jump across block boundaries, the number of local variables can
            // differ, and we should always respect the one having less variables.
            firstTemp = Math.min(firstTemp, joinOrigin.firstTemp);
            assert isVariablePartitioningEqual(joinOrigin, firstTemp);
            mergeVariableTypes(joinOrigin, firstTemp);
        }

        private int getFirstDeadLocal(final List<Type> types) {
            int i = types.size();
            for(final ListIterator<Type> it = types.listIterator(i);
                it.hasPrevious() && it.previous() == Type.UNKNOWN;
                --i) {
                // no body
            }

            // Respect symbol boundaries; we never chop off half a symbol's storage
            while(!symbolBoundary.get(i - 1)) {
                ++i;
            }
            return i;
        }

        private boolean isStackCompatible(final Stack other) {
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

        private boolean isVariablePartitioningEqual(final Stack other, final int toSlot) {
            // No difference in the symbol boundaries before the toSlot
            final BitSet diff = other.getSymbolBoundaryCopy();
            diff.xor(symbolBoundary);
            return diff.previousSetBit(toSlot - 1) == -1;
        }

        void markDeadLocalVariables(final int fromSlot, final int slotCount) {
            final int localCount = localVariableTypes.size();
            if(fromSlot >= localCount) {
                return;
            }
            final int toSlot = Math.min(fromSlot + slotCount, localCount);
            invalidateLocalLoadsOnStack(fromSlot, toSlot);
            for(int i = fromSlot; i < toSlot; ++i) {
                localVariableTypes.set(i, Type.UNKNOWN);
            }
        }

        @SuppressWarnings("unchecked")
        List<Type> getLocalVariableTypesCopy() {
            return (List<Type>)((ArrayList<Type>)localVariableTypes).clone();
        }

        BitSet getSymbolBoundaryCopy() {
            return (BitSet)symbolBoundary.clone();
        }

        /**
         * Returns a list of local variable slot types, but for those symbols that have multiple values, only the slot
         * holding the widest type is marked as live.
         * @return a list of widest local variable slot types.
         */
        List<Type> getWidestLiveLocals(final List<Type> lvarTypes) {
            final List<Type> widestLiveLocals = new ArrayList<>(lvarTypes);
            boolean keepNextValue = true;
            final int size = widestLiveLocals.size();
            for(int i = size - 1; i-- > 0;) {
                if(symbolBoundary.get(i)) {
                    keepNextValue = true;
                }
                final Type t = widestLiveLocals.get(i);
                if(t != Type.UNKNOWN) {
                    if(keepNextValue) {
                        if(t != Type.SLOT_2) {
                            keepNextValue = false;
                        }
                    } else {
                        widestLiveLocals.set(i, Type.UNKNOWN);
                    }
                }
            }
            widestLiveLocals.subList(Math.max(getFirstDeadLocal(widestLiveLocals), firstTemp), widestLiveLocals.size()).clear();
            return widestLiveLocals;
        }

        String markSymbolBoundariesInLvarTypesDescriptor(final String lvarDescriptor) {
            final char[] chars = lvarDescriptor.toCharArray();
            int j = 0;
            for(int i = 0; i < chars.length; ++i) {
                final char c = chars[i];
                final int nextj = j + CodeGeneratorLexicalContext.getTypeForSlotDescriptor(c).getSlots();
                if(!symbolBoundary.get(nextj - 1)) {
                    chars[i] = Character.toLowerCase(c);
                }
                j = nextj;
            }
            return new String(chars);
        }

        Type pop() {
            assert sp > 0;
            return data[--sp];
        }

        @Override
        public Stack clone() {
            try {
                final Stack clone = (Stack)super.clone();
                clone.data = data.clone();
                clone.localLoads = localLoads.clone();
                clone.symbolBoundary = getSymbolBoundaryCopy();
                clone.localVariableTypes = getLocalVariableTypesCopy();
                return clone;
            } catch(final CloneNotSupportedException e) {
                throw new AssertionError("", e);
            }
        }

        private Stack cloneWithEmptyStack() {
            final Stack stack = clone();
            stack.sp = 0;
            return stack;
        }

        int getTopLocalLoad() {
            return localLoads[sp - 1];
        }

        void markLocalLoad(final int slot) {
            localLoads[sp - 1] = slot;
        }

        /**
         * Performs various bookeeping when a value is stored in a local variable slot.
         * @param slot the slot written to
         * @param onlySymbolLiveValue if true, this is the symbol's only live value, and other values of the symbol
         * should be marked dead
         * @param Type the type written to the slot
         */
        void onLocalStore(final Type type, final int slot, final boolean onlySymbolLiveValue) {
            if(onlySymbolLiveValue) {
                final int fromSlot = slot == 0 ? 0 : (symbolBoundary.previousSetBit(slot - 1) + 1);
                final int toSlot = symbolBoundary.nextSetBit(slot) + 1;
                for(int i = fromSlot; i < toSlot; ++i) {
                    localVariableTypes.set(i, Type.UNKNOWN);
                }
                invalidateLocalLoadsOnStack(fromSlot, toSlot);
            } else {
                invalidateLocalLoadsOnStack(slot, slot + type.getSlots());
            }

            localVariableTypes.set(slot, type);
            if(type.isCategory2()) {
                localVariableTypes.set(slot + 1, Type.SLOT_2);
            }
        }

        /**
         * Given a slot range, invalidate knowledge about local loads on stack from these slots (because they're being
         * killed).
         * @param fromSlot first slot, inclusive.
         * @param toSlot last slot, exclusive.
         */
        private void invalidateLocalLoadsOnStack(final int fromSlot, final int toSlot) {
            for(int i = 0; i < sp; ++i) {
                final int localLoad = localLoads[i];
                if(localLoad >= fromSlot && localLoad < toSlot) {
                    localLoads[i] = NON_LOAD;
                }
            }
        }

        /**
         * Marks a range of slots as belonging to a defined local variable. The slots will start out with no live value
         * in them.
         * @param fromSlot first slot, inclusive.
         * @param toSlot last slot, exclusive.
         */
        void defineBlockLocalVariable(final int fromSlot, final int toSlot) {
            defineLocalVariable(fromSlot, toSlot);
            assert firstTemp < toSlot;
            firstTemp = toSlot;
        }

        /**
         * Defines a new temporary local variable and returns its allocated index.
         * @param width the required width (in slots) for the new variable.
         * @return the bytecode slot index where the newly allocated local begins.
         */
        int defineTemporaryLocalVariable(final int width) {
            final int fromSlot = getUsedSlotsWithLiveTemporaries();
            defineLocalVariable(fromSlot, fromSlot + width);
            return fromSlot;
        }

        /**
         * Marks a range of slots as belonging to a defined temporary local variable. The slots will start out with no
         * live value in them.
         * @param fromSlot first slot, inclusive.
         * @param toSlot last slot, exclusive.
         */
        void defineTemporaryLocalVariable(final int fromSlot, final int toSlot) {
            defineLocalVariable(fromSlot, toSlot);
        }

        private void defineLocalVariable(final int fromSlot, final int toSlot) {
            assert !hasLoadsOnStack(fromSlot, toSlot);
            assert fromSlot < toSlot;
            symbolBoundary.clear(fromSlot, toSlot - 1);
            symbolBoundary.set(toSlot - 1);
            final int lastExisting = Math.min(toSlot, localVariableTypes.size());
            for(int i = fromSlot; i < lastExisting; ++i) {
                localVariableTypes.set(i, Type.UNKNOWN);
            }
            for(int i = lastExisting; i < toSlot; ++i) {
                localVariableTypes.add(i, Type.UNKNOWN);
            }
        }

        /**
         * Undefines all local variables past the specified slot.
         * @param fromSlot the first slot to be undefined
         * @param canTruncateSymbol if false, the fromSlot must be either the first slot of a symbol, or the first slot
         * after the last symbol. If true, the fromSlot can be in the middle of the storage area for a symbol. This
         * should be used with care - it is only meant for use in optimism exception handlers.
         */
        void undefineLocalVariables(final int fromSlot, final boolean canTruncateSymbol) {
            final int lvarCount = localVariableTypes.size();
            assert lvarCount == symbolBoundary.length();
            assert !hasLoadsOnStack(fromSlot, lvarCount);
            if(canTruncateSymbol) {
                if(fromSlot > 0) {
                    symbolBoundary.set(fromSlot - 1);
                }
            } else {
                assert fromSlot == 0 || symbolBoundary.get(fromSlot - 1);
            }
            if(fromSlot < lvarCount) {
                symbolBoundary.clear(fromSlot, lvarCount);
                localVariableTypes.subList(fromSlot, lvarCount).clear();
            }
            firstTemp = Math.min(fromSlot, firstTemp);
            assert symbolBoundary.length() == localVariableTypes.size();
            assert symbolBoundary.length() == fromSlot;
        }

        private void markAsOptimisticCatchHandler(final int liveLocalCount) {
            // Live temporaries that are no longer on stack are undefined
            undefineLocalVariables(liveLocalCount, true);
            // Temporaries are promoted
            firstTemp = liveLocalCount;
            // No trailing undefineds
            localVariableTypes.subList(firstTemp, localVariableTypes.size()).clear();
            assert symbolBoundary.length() == firstTemp;
            // Generalize all reference types to Object, and promote boolean to int
            for(final ListIterator<Type> it = localVariableTypes.listIterator(); it.hasNext();) {
                final Type type = it.next();
                if(type == Type.BOOLEAN) {
                    it.set(Type.INT);
                } else if(type.isObject() && type != Type.OBJECT) {
                    it.set(Type.OBJECT);
                }
            }
        }

        /**
         * Returns true if any loads on the stack come from the specified slot range.
         * @param fromSlot start of the range (inclusive)
         * @param toSlot end of the range (exclusive)
         * @return true if any loads on the stack come from the specified slot range.
         */
        boolean hasLoadsOnStack(final int fromSlot, final int toSlot) {
            for(int i = 0; i < sp; ++i) {
                final int load = localLoads[i];
                if(load >= fromSlot && load < toSlot) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return "stack=" + Arrays.toString(Arrays.copyOf(data, sp))
                 + ", symbolBoundaries=" + String.valueOf(symbolBoundary)
                 + ", firstTemp=" + firstTemp
                 + ", localTypes=" + String.valueOf(localVariableTypes)
                 ;
        }
    }

    /** Next id for debugging purposes, remove if footprint becomes unmanageable */
    private static int nextId = 0;

    /** Name of this label */
    private final String name;

    /** Type stack at this label */
    private Label.Stack stack;

    /** ASM representation of this label */
    private jdk.internal.org.objectweb.asm.Label label;

    /** Id for debugging purposes, remove if footprint becomes unmanageable */
    private final int id;

    /** Is this label reachable (anything ever jumped to it)? */
    private boolean reachable;

    private boolean breakTarget;

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

    void joinFrom(final Label.Stack joinOrigin) {
        this.reachable = true;
        if(stack == null) {
            stack = joinOrigin.clone();
        } else {
            stack.joinFrom(joinOrigin, breakTarget);
        }
    }

    void joinFromTry(final Label.Stack joinOrigin, final boolean isOptimismHandler) {
        this.reachable = true;
        if (stack == null) {
            if(!isOptimismHandler) {
                stack = joinOrigin.cloneWithEmptyStack();
                // Optimism handler needs temporaries to remain live, others don't.
                stack.undefineLocalVariables(stack.firstTemp, false);
            }
        } else {
            assert !isOptimismHandler;
            stack.joinFromTry(joinOrigin);
        }
    }

    void markAsBreakTarget() {
        breakTarget = true;
    }

    boolean isBreakTarget() {
        return breakTarget;
    }

    void onCatch() {
        if(stack != null) {
            stack = stack.cloneWithEmptyStack();
        }
    }
    void markAsOptimisticCatchHandler(final Label.Stack currentStack, final int liveLocalCount) {
        stack = currentStack.cloneWithEmptyStack();
        stack.markAsOptimisticCatchHandler(liveLocalCount);
    }

    void markAsOptimisticContinuationHandlerFor(final Label afterConsumeStackLabel) {
        stack = afterConsumeStackLabel.stack.cloneWithEmptyStack();
    }

    boolean isReachable() {
        return reachable;
    }

    boolean isAfter(final Label other) {
        return label.getOffset() > other.label.getOffset();
    }

    @Override
    public String toString() {
        return name + '_' + id;
    }
}
