/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
package com.sun.org.apache.bcel.internal.generic;

/* ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2001 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Apache" and "Apache Software Foundation" and
 *    "Apache BCEL" must not be used to endorse or promote products
 *    derived from this software without prior written permission. For
 *    written permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    "Apache BCEL", nor may "Apache" appear in their name, without
 *    prior written permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

/**
 * BranchHandle is returned by specialized InstructionList.append() whenever a
 * BranchInstruction is appended. This is useful when the target of this
 * instruction is not known at time of creation and must be set later
 * via setTarget().
 *
 * @see InstructionHandle
 * @see Instruction
 * @see InstructionList
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public final class BranchHandle extends InstructionHandle {
  private BranchInstruction bi; // An alias in fact, but saves lots of casts

  private BranchHandle(BranchInstruction i) {
    super(i);
    bi = i;
  }

  /** Factory methods.
   */
  private static BranchHandle bh_list = null; // List of reusable handles

  static final BranchHandle getBranchHandle(BranchInstruction i) {
    if(bh_list == null)
      return new BranchHandle(i);
    else {
      BranchHandle bh = bh_list;
      bh_list = (BranchHandle)bh.next;

      bh.setInstruction(i);

      return bh;
    }
  }

  /** Handle adds itself to the list of resuable handles.
   */
  protected void addHandle() {
    next    = bh_list;
    bh_list = this;
  }

  /* Override InstructionHandle methods: delegate to branch instruction.
   * Through this overriding all access to the private i_position field should
   * be prevented.
   */
  public int getPosition() { return bi.position; }

  void setPosition(int pos) {
    i_position = bi.position = pos;
  }

  protected int updatePosition(int offset, int max_offset) {
    int x = bi.updatePosition(offset, max_offset);
    i_position = bi.position;
    return x;
  }

  /**
   * Pass new target to instruction.
   */
  public void setTarget(InstructionHandle ih) {
    bi.setTarget(ih);
  }

  /**
   * Update target of instruction.
   */
  public void updateTarget(InstructionHandle old_ih, InstructionHandle new_ih) {
    bi.updateTarget(old_ih, new_ih);
  }

  /**
   * @return target of instruction.
   */
  public InstructionHandle getTarget() {
    return bi.getTarget();
  }

  /**
   * Set new contents. Old instruction is disposed and may not be used anymore.
   */
  public void setInstruction(Instruction i) {
    super.setInstruction(i);

    if(!(i instanceof BranchInstruction))
      throw new ClassGenException("Assigning " + i +
                                  " to branch handle which is not a branch instruction");

    bi = (BranchInstruction)i;
  }
}
