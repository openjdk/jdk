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

import com.sun.org.apache.bcel.internal.classfile.Utility;
import java.util.HashSet;
import java.util.Collection;
import java.util.HashMap;

/**
 * Instances of this class give users a handle to the instructions contained in
 * an InstructionList. Instruction objects may be used more than once within a
 * list, this is useful because it saves memory and may be much faster.
 *
 * Within an InstructionList an InstructionHandle object is wrapped
 * around all instructions, i.e., it implements a cell in a
 * doubly-linked list. From the outside only the next and the
 * previous instruction (handle) are accessible. One
 * can traverse the list via an Enumeration returned by
 * InstructionList.elements().
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @see Instruction
 * @see BranchHandle
 * @see InstructionList
 */
public class InstructionHandle implements java.io.Serializable {
  InstructionHandle next, prev;  // Will be set from the outside
  Instruction       instruction;
  protected int     i_position = -1; // byte code offset of instruction
  private HashSet   targeters;
  private HashMap   attributes;

  public final InstructionHandle getNext()        { return next; }
  public final InstructionHandle getPrev()        { return prev; }
  public final Instruction       getInstruction() { return instruction; }

  /**
   * Replace current instruction contained in this handle.
   * Old instruction is disposed using Instruction.dispose().
   */
  public void setInstruction(Instruction i) { // Overridden in BranchHandle
    if(i == null)
      throw new ClassGenException("Assigning null to handle");

    if((this.getClass() != BranchHandle.class) && (i instanceof BranchInstruction))
      throw new ClassGenException("Assigning branch instruction " + i + " to plain handle");

    if(instruction != null)
      instruction.dispose();

    instruction = i;
  }

  /**
   * Temporarily swap the current instruction, without disturbing
   * anything. Meant to be used by a debugger, implementing
   * breakpoints. Current instruction is returned.
   */
  public Instruction swapInstruction(Instruction i) {
    Instruction oldInstruction = instruction;
    instruction = i;
    return oldInstruction;
  }

  /*private*/ protected InstructionHandle(Instruction i) {
    setInstruction(i);
  }

  private static InstructionHandle ih_list = null; // List of reusable handles

  /** Factory method.
   */
  static final InstructionHandle getInstructionHandle(Instruction i) {
    if(ih_list == null)
      return new InstructionHandle(i);
    else {
      InstructionHandle ih = ih_list;
      ih_list = ih.next;

      ih.setInstruction(i);

      return ih;
    }
  }

  /**
   * Called by InstructionList.setPositions when setting the position for every
   * instruction. In the presence of variable length instructions `setPositions()'
   * performs multiple passes over the instruction list to calculate the
   * correct (byte) positions and offsets by calling this function.
   *
   * @param offset additional offset caused by preceding (variable length) instructions
   * @param max_offset the maximum offset that may be caused by these instructions
   * @return additional offset caused by possible change of this instruction's length
   */
  protected int updatePosition(int offset, int max_offset) {
    i_position += offset;
    return 0;
  }

  /** @return the position, i.e., the byte code offset of the contained
   * instruction. This is accurate only after
   * InstructionList.setPositions() has been called.
   */
  public int getPosition() { return i_position; }

  /** Set the position, i.e., the byte code offset of the contained
   * instruction.
   */
  void setPosition(int pos) { i_position = pos; }

  /** Overridden in BranchHandle
   */
  protected void addHandle() {
    next    = ih_list;
    ih_list = this;
  }

  /**
   * Delete contents, i.e., remove user access and make handle reusable.
   */
  void dispose() {
    next = prev = null;
    instruction.dispose();
    instruction = null;
    i_position = -1;
    attributes = null;
    removeAllTargeters();
    addHandle();
  }

  /** Remove all targeters, if any.
   */
  public void removeAllTargeters() {
    if(targeters != null)
      targeters.clear();
  }

  /**
   * Denote this handle isn't referenced anymore by t.
   */
  public void removeTargeter(InstructionTargeter t) {
    targeters.remove(t);
  }

  /**
   * Denote this handle is being referenced by t.
   */
  public void addTargeter(InstructionTargeter t) {
    if(targeters == null)
      targeters = new HashSet();

    //if(!targeters.contains(t))
    targeters.add(t);
  }

  public boolean hasTargeters() {
    return (targeters != null) && (targeters.size() > 0);
  }

  /**
   * @return null, if there are no targeters
   */
  public InstructionTargeter[] getTargeters() {
    if(!hasTargeters())
      return null;

    InstructionTargeter[] t = new InstructionTargeter[targeters.size()];
    targeters.toArray(t);
    return t;
  }

  /** @return a (verbose) string representation of the contained instruction.
   */
  public String toString(boolean verbose) {
    return Utility.format(i_position, 4, false, ' ') + ": " + instruction.toString(verbose);
  }

  /** @return a string representation of the contained instruction.
   */
  public String toString() {
    return toString(true);
  }

  /** Add an attribute to an instruction handle.
   *
   * @param key the key object to store/retrieve the attribute
   * @param attr the attribute to associate with this handle
   */
  public void addAttribute(Object key, Object attr) {
    if(attributes == null)
      attributes = new HashMap(3);

    attributes.put(key, attr);
  }

  /** Delete an attribute of an instruction handle.
   *
   * @param key the key object to retrieve the attribute
   */
  public void removeAttribute(Object key) {
    if(attributes != null)
      attributes.remove(key);
  }

  /** Get attribute of an instruction handle.
   *
   * @param key the key object to store/retrieve the attribute
   */
  public Object getAttribute(Object key) {
    if(attributes != null)
      return attributes.get(key);

    return null;
  }

  /** @return all attributes associated with this handle
   */
  public Collection getAttributes() {
    return attributes.values();
  }

  /** Convenience method, simply calls accept() on the contained instruction.
   *
   * @param v Visitor object
   */
  public void accept(Visitor v) {
    instruction.accept(v);
  }
}
