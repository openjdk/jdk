/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sun.org.apache.bcel.internal.generic;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.sun.org.apache.bcel.internal.classfile.Utility;

/**
 * Instances of this class give users a handle to the instructions contained in an InstructionList. Instruction objects
 * may be used more than once within a list, this is useful because it saves memory and may be much faster.
 *
 * Within an InstructionList an InstructionHandle object is wrapped around all instructions, i.e., it implements a cell
 * in a doubly-linked list. From the outside only the next and the previous instruction (handle) are accessible. One can
 * traverse the list via an Enumeration returned by InstructionList.elements().
 *
 * @see Instruction
 * @see BranchHandle
 * @see InstructionList
 * @LastModified: Sept 2025
 */
public class InstructionHandle {

    /**
     * Empty array.
     *
     * @since 6.6.0
     */
    public static final InstructionHandle[] EMPTY_ARRAY = {};

    /**
     * Empty array.
     */
    static final InstructionTargeter[] EMPTY_INSTRUCTION_TARGETER_ARRAY = {};

    /**
     * Factory method.
     */
    static InstructionHandle getInstructionHandle(final Instruction i) {
        return new InstructionHandle(i);
    }

    private InstructionHandle next;
    private InstructionHandle prev;

    private Instruction instruction;

    /**
     * @deprecated (since 6.0) will be made private; do not access directly, use getter/setter
     */
    @Deprecated
    protected int i_position = -1; // byte code offset of instruction
    private Set<InstructionTargeter> targeters;

    private Map<Object, Object> attributes;

    protected InstructionHandle(final Instruction i) {
        setInstruction(i);
    }

    /**
     * Convenience method, simply calls accept() on the contained instruction.
     *
     * @param v Visitor object
     */
    public void accept(final Visitor v) {
        instruction.accept(v);
    }

    /**
     * Add an attribute to an instruction handle.
     *
     * @param key the key object to store/retrieve the attribute
     * @param attr the attribute to associate with this handle
     */
    public void addAttribute(final Object key, final Object attr) {
        if (attributes == null) {
            attributes = new HashMap<>(3);
        }
        attributes.put(key, attr);
    }

    /**
     * Does nothing.
     *
     * @deprecated Does nothing as of 6.3.1.
     */
    @Deprecated
    protected void addHandle() {
        // noop
    }

    /**
     * Denote this handle is being referenced by t.
     */
    public void addTargeter(final InstructionTargeter t) {
        if (targeters == null) {
            targeters = new HashSet<>();
        }
        // if (!targeters.contains(t))
        targeters.add(t);
    }

    /**
     * Delete contents, i.e., remove user access.
     */
    void dispose() {
        next = prev = null;
        instruction.dispose();
        instruction = null;
        i_position = -1;
        attributes = null;
        removeAllTargeters();
    }

    /**
     * Gets attribute of an instruction handle.
     *
     * @param key the key object to store/retrieve the attribute
     */
    public Object getAttribute(final Object key) {
        return attributes != null ? attributes.get(key) : null;
    }

    /**
     * @return all attributes associated with this handle
     */
    public Collection<Object> getAttributes() {
        if (attributes == null) {
            attributes = new HashMap<>(3);
        }
        return attributes.values();
    }

    public final Instruction getInstruction() {
        return instruction;
    }

    public final InstructionHandle getNext() {
        return next;
    }

    /**
     * @return the position, i.e., the byte code offset of the contained instruction. This is accurate only after
     *         InstructionList.setPositions() has been called.
     */
    public int getPosition() {
        return i_position;
    }

    public final InstructionHandle getPrev() {
        return prev;
    }

    /**
     * @return null, if there are no targeters
     */
    public InstructionTargeter[] getTargeters() {
        if (!hasTargeters()) {
            return EMPTY_INSTRUCTION_TARGETER_ARRAY;
        }
        final InstructionTargeter[] t = new InstructionTargeter[targeters.size()];
        targeters.toArray(t);
        return t;
    }

    public boolean hasTargeters() {
        return targeters != null && !targeters.isEmpty();
    }

    /**
     * Remove all targeters, if any.
     */
    public void removeAllTargeters() {
        if (targeters != null) {
            targeters.clear();
        }
    }

    /**
     * Delete an attribute of an instruction handle.
     *
     * @param key the key object to retrieve the attribute
     */
    public void removeAttribute(final Object key) {
        if (attributes != null) {
            attributes.remove(key);
        }
    }

    /**
     * Denote this handle isn't referenced anymore by t.
     */
    public void removeTargeter(final InstructionTargeter t) {
        if (targeters != null) {
            targeters.remove(t);
        }
    }

    /**
     * Replace current instruction contained in this handle. Old instruction is disposed using Instruction.dispose().
     */
    public void setInstruction(final Instruction i) { // Overridden in BranchHandle TODO could be package-protected?
        if (i == null) {
            throw new ClassGenException("Assigning null to handle");
        }
        if (this.getClass() != BranchHandle.class && i instanceof BranchInstruction) {
            throw new ClassGenException("Assigning branch instruction " + i + " to plain handle");
        }
        if (instruction != null) {
            instruction.dispose();
        }
        instruction = i;
    }

    /**
     * @param next the next to set
     * @since 6.0
     */
    final InstructionHandle setNext(final InstructionHandle next) {
        this.next = next;
        return next;
    }

    /**
     * Sets the position, i.e., the byte code offset of the contained instruction.
     */
    void setPosition(final int pos) {
        i_position = pos;
    }

    /**
     * @param prev the prev to set
     * @since 6.0
     */
    final InstructionHandle setPrev(final InstructionHandle prev) {
        this.prev = prev;
        return prev;
    }

    /**
     * Temporarily swap the current instruction, without disturbing anything. Meant to be used by a debugger, implementing
     * breakpoints. Current instruction is returned.
     * <p>
     * Warning: if this is used on a BranchHandle then some methods such as getPosition() will still refer to the original
     * cached instruction, whereas other BH methods may affect the cache and the replacement instruction.
     */
    // See BCEL-273
    // TODO remove this method in any redesign of BCEL
    public Instruction swapInstruction(final Instruction i) {
        final Instruction oldInstruction = instruction;
        instruction = i;
        return oldInstruction;
    }

    /**
     * @return a string representation of the contained instruction.
     */
    @Override
    public String toString() {
        return toString(true);
    }

    /**
     * @return a (verbose) string representation of the contained instruction.
     */
    public String toString(final boolean verbose) {
        return Utility.format(i_position, 4, false, ' ') + ": " + instruction.toString(verbose);
    }

    /**
     * Called by InstructionList.setPositions when setting the position for every instruction. In the presence of variable
     * length instructions 'setPositions()' performs multiple passes over the instruction list to calculate the correct
     * (byte) positions and offsets by calling this function.
     *
     * @param offset additional offset caused by preceding (variable length) instructions
     * @param maxOffset the maximum offset that may be caused by these instructions
     * @return additional offset caused by possible change of this instruction's length
     */
    protected int updatePosition(final int offset, final int maxOffset) {
        i_position += offset;
        return 0;
    }
}
