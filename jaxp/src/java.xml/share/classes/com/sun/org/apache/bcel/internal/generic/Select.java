/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
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

import java.io.*;
import com.sun.org.apache.bcel.internal.util.ByteSequence;

/**
 * Select - Abstract super class for LOOKUPSWITCH and TABLESWITCH instructions.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @see LOOKUPSWITCH
 * @see TABLESWITCH
 * @see InstructionList
 */
public abstract class Select extends BranchInstruction
  implements VariableLengthInstruction, StackProducer
{
  protected int[]               match;        // matches, i.e., case 1: ...
  protected int[]               indices;      // target offsets
  protected InstructionHandle[] targets;      // target objects in instruction list
  protected int                 fixed_length; // fixed length defined by subclasses
  protected int                 match_length; // number of cases
  protected int                 padding = 0;  // number of pad bytes for alignment

  /**
   * Empty constructor needed for the Class.newInstance() statement in
   * Instruction.readInstruction(). Not to be used otherwise.
   */
  Select() {}

  /**
   * (Match, target) pairs for switch.
   * `Match' and `targets' must have the same length of course.
   *
   * @param match array of matching values
   * @param targets instruction targets
   * @param target default instruction target
   */
  Select(short opcode, int[] match, InstructionHandle[] targets,
         InstructionHandle target) {
    super(opcode, target);

    this.targets = targets;
    for(int i=0; i < targets.length; i++) {
      BranchInstruction.notifyTargetChanged(targets[i], this);
    }

    this.match = match;

    if((match_length = match.length) != targets.length)
      throw new ClassGenException("Match and target array have not the same length");

    indices = new int[match_length];
  }

  /**
   * Since this is a variable length instruction, it may shift the following
   * instructions which then need to update their position.
   *
   * Called by InstructionList.setPositions when setting the position for every
   * instruction. In the presence of variable length instructions `setPositions'
   * performs multiple passes over the instruction list to calculate the
   * correct (byte) positions and offsets by calling this function.
   *
   * @param offset additional offset caused by preceding (variable length) instructions
   * @param max_offset the maximum offset that may be caused by these instructions
   * @return additional offset caused by possible change of this instruction's length
   */
  @Override
  protected int updatePosition(int offset, int max_offset) {
    position += offset; // Additional offset caused by preceding SWITCHs, GOTOs, etc.

    short old_length = length;

    /* Alignment on 4-byte-boundary, + 1, because of tag byte.
     */
    padding = (4 - ((position + 1) % 4)) % 4;
    length  = (short)(fixed_length + padding); // Update length

    return length - old_length;
  }

  /**
   * Dump instruction as byte code to stream out.
   * @param out Output stream
   */
  @Override
  public void dump(DataOutputStream out) throws IOException {
    out.writeByte(opcode);

    for(int i=0; i < padding; i++) // Padding bytes
      out.writeByte(0);

    index = getTargetOffset();     // Write default target offset
    out.writeInt(index);
  }

  /**
   * Read needed data (e.g. index) from file.
   */
  @Override
  protected void initFromFile(ByteSequence bytes, boolean wide) throws IOException
  {
    padding = (4 - (bytes.getIndex() % 4)) % 4; // Compute number of pad bytes

    for(int i=0; i < padding; i++) {
      bytes.readByte();
    }

    // Default branch target common for both cases (TABLESWITCH, LOOKUPSWITCH)
    index = bytes.readInt();
  }

  /**
   * @return mnemonic for instruction
   */
  @Override
  public String toString(boolean verbose) {
    final StringBuilder buf = new StringBuilder(super.toString(verbose));

    if(verbose) {
      for(int i=0; i < match_length; i++) {
        String s = "null";

        if(targets[i] != null)
          s = targets[i].getInstruction().toString();

          buf.append("(").append(match[i]).append(", ")
             .append(s).append(" = {").append(indices[i]).append("})");
      }
    }
    else
      buf.append(" ...");

    return buf.toString();
  }

  /**
   * Set branch target for `i'th case
   */
  public final void setTarget(int i, InstructionHandle target) {
    notifyTargetChanging(targets[i], this);
    targets[i] = target;
    notifyTargetChanged(targets[i], this);
  }

  /**
   * @param old_ih old target
   * @param new_ih new target
   */
  @Override
  public void updateTarget(InstructionHandle old_ih, InstructionHandle new_ih) {
    boolean targeted = false;

    if(target == old_ih) {
      targeted = true;
      setTarget(new_ih);
    }

    for(int i=0; i < targets.length; i++) {
      if(targets[i] == old_ih) {
        targeted = true;
        setTarget(i, new_ih);
      }
    }

    if(!targeted)
      throw new ClassGenException("Not targeting " + old_ih);
  }

  /**
   * @return true, if ih is target of this instruction
   */
  @Override
  public boolean containsTarget(InstructionHandle ih) {
    if(target == ih)
      return true;

    for(int i=0; i < targets.length; i++)
      if(targets[i] == ih)
        return true;

    return false;
  }

  /**
   * Inform targets that they're not targeted anymore.
   */
  @Override
  void dispose() {
    super.dispose();

    for(int i=0; i < targets.length; i++)
      targets[i].removeTargeter(this);
  }

  /**
   * @return array of match indices
   */
  public int[] getMatchs() { return match; }

  /**
   * @return array of match target offsets
   */
  public int[] getIndices() { return indices; }

  /**
   * @return array of match targets
   */
  public InstructionHandle[] getTargets() { return targets; }
}
