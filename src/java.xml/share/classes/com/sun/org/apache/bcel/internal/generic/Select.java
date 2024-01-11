/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.DataOutputStream;
import java.io.IOException;

import com.sun.org.apache.bcel.internal.util.ByteSequence;

/**
 * Select - Abstract super class for LOOKUPSWITCH and TABLESWITCH instructions.
 *
 * <p>
 * We use our super's {@code target} property as the default target.
 *
 * @see LOOKUPSWITCH
 * @see TABLESWITCH
 * @see InstructionList
 * @LastModified: May 2021
 */
public abstract class Select extends BranchInstruction implements VariableLengthInstruction, StackConsumer /* @since 6.0 */, StackProducer {

    /**
     * @deprecated (since 6.0) will be made private; do not access directly, use getter/setter
     */
    @Deprecated
    protected int[] match; // matches, i.e., case 1: ... TODO could be package-protected?

    /**
     * @deprecated (since 6.0) will be made private; do not access directly, use getter/setter
     */
    @Deprecated
    protected int[] indices; // target offsets TODO could be package-protected?

    /**
     * @deprecated (since 6.0) will be made private; do not access directly, use getter/setter
     */
    @Deprecated
    protected InstructionHandle[] targets; // target objects in instruction list TODO could be package-protected?

    /**
     * @deprecated (since 6.0) will be made private; do not access directly, use getter/setter
     */
    @Deprecated
    protected int fixed_length; // fixed length defined by subclasses TODO could be package-protected?

    /**
     * @deprecated (since 6.0) will be made private; do not access directly, use getter/setter
     */
    @Deprecated
    protected int match_length; // number of cases TODO could be package-protected?

    /**
     * @deprecated (since 6.0) will be made private; do not access directly, use getter/setter
     */
    @Deprecated
    protected int padding; // number of pad bytes for alignment TODO could be package-protected?

    /**
     * Empty constructor needed for Instruction.readInstruction. Not to be used otherwise.
     */
    Select() {
    }

    /**
     * (Match, target) pairs for switch. 'Match' and 'targets' must have the same length of course.
     *
     * @param match array of matching values
     * @param targets instruction targets
     * @param defaultTarget default instruction target
     */
    Select(final short opcode, final int[] match, final InstructionHandle[] targets, final InstructionHandle defaultTarget) {
        // don't set default target before instuction is built
        super(opcode, null);
        this.match = match;
        this.targets = targets;
        // now it's safe to set default target
        setTarget(defaultTarget);
        for (final InstructionHandle target2 : targets) {
            notifyTarget(null, target2, this);
        }
        if ((match_length = match.length) != targets.length) {
            throw new ClassGenException("Match and target array have not the same length: Match length: " + match.length + " Target length: " + targets.length);
        }
        indices = new int[match_length];
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        final Select copy = (Select) super.clone();
        copy.match = match.clone();
        copy.indices = indices.clone();
        copy.targets = targets.clone();
        return copy;
    }

    /**
     * @return true, if ih is target of this instruction
     */
    @Override
    public boolean containsTarget(final InstructionHandle ih) {
        if (super.getTarget() == ih) {
            return true;
        }
        for (final InstructionHandle target2 : targets) {
            if (target2 == ih) {
                return true;
            }
        }
        return false;
    }

    /**
     * Inform targets that they're not targeted anymore.
     */
    @Override
    void dispose() {
        super.dispose();
        for (final InstructionHandle target2 : targets) {
            target2.removeTargeter(this);
        }
    }

    /**
     * Dump instruction as byte code to stream out.
     *
     * @param out Output stream
     */
    @Override
    public void dump(final DataOutputStream out) throws IOException {
        out.writeByte(super.getOpcode());
        for (int i = 0; i < padding; i++) {
            out.writeByte(0);
        }
        super.setIndex(getTargetOffset()); // Write default target offset
        out.writeInt(super.getIndex());
    }

    /**
     * @return the fixed_length
     * @since 6.0
     */
    final int getFixedLength() {
        return fixed_length;
    }

    /**
     * @return array of match target offsets
     */
    public int[] getIndices() {
        return indices;
    }

    /**
     * @return index entry from indices
     * @since 6.0
     */
    final int getIndices(final int index) {
        return indices[index];
    }

    /**
     * @return match entry
     * @since 6.0
     */
    final int getMatch(final int index) {
        return match[index];
    }

    /**
     * @return the match_length
     * @since 6.0
     */
    final int getMatchLength() {
        return match_length;
    }

    /**
     * @return array of match indices
     */
    public int[] getMatchs() {
        return match;
    }

    /**
     *
     * @return the padding
     * @since 6.0
     */
    final int getPadding() {
        return padding;
    }

    /**
     * @return target entry
     * @since 6.0
     */
    final InstructionHandle getTarget(final int index) {
        return targets[index];
    }

    /**
     * @return array of match targets
     */
    public InstructionHandle[] getTargets() {
        return targets;
    }

    /**
     * Read needed data (e.g. index) from file.
     */
    @Override
    protected void initFromFile(final ByteSequence bytes, final boolean wide) throws IOException {
        padding = (4 - bytes.getIndex() % 4) % 4; // Compute number of pad bytes
        for (int i = 0; i < padding; i++) {
            bytes.readByte();
        }
        // Default branch target common for both cases (TABLESWITCH, LOOKUPSWITCH)
        super.setIndex(bytes.readInt());
    }

    /**
     * @param fixedLength the fixed_length to set
     * @since 6.0
     */
    final void setFixedLength(final int fixedLength) {
        this.fixed_length = fixedLength;
    }

    /** @since 6.0 */
    final int setIndices(final int i, final int value) {
        indices[i] = value;
        return value; // Allow use in nested calls
    }

    /**
     *
     * @param array
     * @since 6.0
     */
    final void setIndices(final int[] array) {
        indices = array;
    }

    /**
     *
     * @param index
     * @param value
     * @since 6.0
     */
    final void setMatch(final int index, final int value) {
        match[index] = value;
    }

    /**
     *
     * @param array
     * @since 6.0
     */
    final void setMatches(final int[] array) {
        match = array;
    }

    /**
     * @param matchLength the match_length to set
     * @since 6.0
     */
    final int setMatchLength(final int matchLength) {
        this.match_length = matchLength;
        return matchLength;
    }

    /**
     * Set branch target for 'i'th case
     */
    public void setTarget(final int i, final InstructionHandle target) { // TODO could be package-protected?
        notifyTarget(targets[i], target, this);
        targets[i] = target;
    }

    /**
     *
     * @param array
     * @since 6.0
     */
    final void setTargets(final InstructionHandle[] array) {
        targets = array;
    }

    /**
     * @return mnemonic for instruction
     */
    @Override
    public String toString(final boolean verbose) {
        final StringBuilder buf = new StringBuilder(super.toString(verbose));
        if (verbose) {
            for (int i = 0; i < match_length; i++) {
                String s = "null";
                if (targets[i] != null) {
                    s = targets[i].getInstruction().toString();
                }
                buf.append("(").append(match[i]).append(", ").append(s).append(" = {").append(indices[i]).append("})");
            }
        } else {
            buf.append(" ...");
        }
        return buf.toString();
    }

    /**
     * Since this is a variable length instruction, it may shift the following instructions which then need to update their
     * position.
     *
     * Called by InstructionList.setPositions when setting the position for every instruction. In the presence of variable
     * length instructions 'setPositions' performs multiple passes over the instruction list to calculate the correct (byte)
     * positions and offsets by calling this function.
     *
     * @param offset additional offset caused by preceding (variable length) instructions
     * @param maxOffset the maximum offset that may be caused by these instructions
     * @return additional offset caused by possible change of this instruction's length
     */
    @Override
    protected int updatePosition(final int offset, final int maxOffset) {
        setPosition(getPosition() + offset); // Additional offset caused by preceding SWITCHs, GOTOs, etc.
        final short oldLength = (short) super.getLength();
        /*
         * Alignment on 4-byte-boundary, + 1, because of tag byte.
         */
        padding = (4 - (getPosition() + 1) % 4) % 4;
        super.setLength((short) (fixed_length + padding)); // Update length
        return super.getLength() - oldLength;
    }

    /**
     * @param oldIh old target
     * @param newIh new target
     */
    @Override
    public void updateTarget(final InstructionHandle oldIh, final InstructionHandle newIh) {
        boolean targeted = false;
        if (super.getTarget() == oldIh) {
            targeted = true;
            setTarget(newIh);
        }
        for (int i = 0; i < targets.length; i++) {
            if (targets[i] == oldIh) {
                targeted = true;
                setTarget(i, newIh);
            }
        }
        if (!targeted) {
            throw new ClassGenException("Not targeting " + oldIh);
        }
    }
}
