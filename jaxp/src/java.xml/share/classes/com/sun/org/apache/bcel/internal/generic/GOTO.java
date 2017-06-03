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

/**
 * GOTO - Branch always (to relative offset, not absolute address)
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public class GOTO extends GotoInstruction implements VariableLengthInstruction {
  /**
   * Empty constructor needed for the Class.newInstance() statement in
   * Instruction.readInstruction(). Not to be used otherwise.
   */
  GOTO() {}

  public GOTO(InstructionHandle target) {
    super(com.sun.org.apache.bcel.internal.Constants.GOTO, target);
  }

  /**
   * Dump instruction as byte code to stream out.
   * @param out Output stream
   */
  public void dump(DataOutputStream out) throws IOException {
    index = getTargetOffset();
    if(opcode == com.sun.org.apache.bcel.internal.Constants.GOTO)
      super.dump(out);
    else { // GOTO_W
      index = getTargetOffset();
      out.writeByte(opcode);
      out.writeInt(index);
    }
  }

  /** Called in pass 2 of InstructionList.setPositions() in order to update
   * the branch target, that may shift due to variable length instructions.
   */
  protected int updatePosition(int offset, int max_offset) {
    int i = getTargetOffset(); // Depending on old position value

    position += offset; // Position may be shifted by preceding expansions

    if(Math.abs(i) >= (32767 - max_offset)) { // to large for short (estimate)
      opcode = com.sun.org.apache.bcel.internal.Constants.GOTO_W;
      length = 5;
      return 2; // 5 - 3
    }

    return 0;
  }

  /**
   * Call corresponding visitor method(s). The order is:
   * Call visitor methods of implemented interfaces first, then
   * call methods according to the class hierarchy in descending order,
   * i.e., the most specific visitXXX() call comes last.
   *
   * @param v Visitor object
   */
  public void accept(Visitor v) {
    v.visitVariableLengthInstruction(this);
    v.visitUnconditionalBranch(this);
    v.visitBranchInstruction(this);
    v.visitGotoInstruction(this);
    v.visitGOTO(this);
  }
}
