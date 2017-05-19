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


import com.sun.org.apache.bcel.internal.Constants;
import com.sun.org.apache.bcel.internal.classfile.Constant;
import com.sun.org.apache.bcel.internal.util.ByteSequence;
import java.io.*;
import java.util.Iterator;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * This class is a container for a list of <a
 * href="Instruction.html">Instruction</a> objects. Instructions can
 * be appended, inserted, moved, deleted, etc.. Instructions are being
 * wrapped into <a
 * href="InstructionHandle.html">InstructionHandles</a> objects that
 * are returned upon append/insert operations. They give the user
 * (read only) access to the list structure, such that it can be traversed and
 * manipulated in a controlled way.
 *
 * A list is finally dumped to a byte code array with <a
 * href="#getByteCode()">getByteCode</a>.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @see     Instruction
 * @see     InstructionHandle
 * @see BranchHandle
 */
public class InstructionList implements Serializable {
  private InstructionHandle start  = null, end = null;
  private int               length = 0; // number of elements in list
  private int[]             byte_positions; // byte code offsets corresponding to instructions

  /**
   * Create (empty) instruction list.
   */
  public InstructionList() {}

  /**
   * Create instruction list containing one instruction.
   * @param i initial instruction
   */
  public InstructionList(Instruction i) {
    append(i);
  }

  /**
   * Create instruction list containing one instruction.
   * @param i initial instruction
   */
  public InstructionList(BranchInstruction i) {
    append(i);
  }

  /**
   * Initialize list with (nonnull) compound instruction. Consumes argument
   * list, i.e., it becomes empty.
   *
   * @param c compound instruction (list)
   */
  public InstructionList(CompoundInstruction c) {
    append(c.getInstructionList());
  }

  /**
   * Test for empty list.
   */
  public boolean isEmpty() { return start == null; } // && end == null

  /**
   * Find the target instruction (handle) that corresponds to the given target
   * position (byte code offset).
   *
   * @param ihs array of instruction handles, i.e. il.getInstructionHandles()
   * @param pos array of positions corresponding to ihs, i.e. il.getInstructionPositions()
   * @param count length of arrays
   * @param target target position to search for
   * @return target position's instruction handle if available
   */
  public static InstructionHandle findHandle(InstructionHandle[] ihs,
                                             int[] pos, int count,
                                             int target) {
    int l=0, r = count - 1;

    /* Do a binary search since the pos array is orderd.
     */
    do {
      int i = (l + r) / 2;
      int j = pos[i];

      if(j == target) // target found
        return ihs[i];
      else if(target < j) // else constrain search area
        r = i - 1;
      else // target > j
        l = i + 1;
    } while(l <= r);

    return null;
  }

  /**
   * Get instruction handle for instruction at byte code position pos.
   * This only works properly, if the list is freshly initialized from a byte array or
   * setPositions() has been called before this method.
   *
   * @param pos byte code position to search for
   * @return target position's instruction handle if available
   */
  public InstructionHandle findHandle(int pos) {
    InstructionHandle[] ihs = getInstructionHandles();
    return findHandle(ihs, byte_positions, length, pos);
  }

  /**
   * Initialize instruction list from byte array.
   *
   * @param code byte array containing the instructions
   */
  public InstructionList(byte[] code) {
    ByteSequence        bytes = new ByteSequence(code);
    InstructionHandle[] ihs   = new InstructionHandle[code.length];
    int[]               pos   = new int[code.length]; // Can't be more than that
    int                 count = 0; // Contains actual length

    /* Pass 1: Create an object for each byte code and append them
     * to the list.
     */
    try {
      while(bytes.available() > 0) {
        // Remember byte offset and associate it with the instruction
        int off =  bytes.getIndex();
        pos[count] = off;

        /* Read one instruction from the byte stream, the byte position is set
         * accordingly.
         */
        Instruction       i = Instruction.readInstruction(bytes);
        InstructionHandle ih;
        if(i instanceof BranchInstruction) // Use proper append() method
          ih = append((BranchInstruction)i);
        else
          ih = append(i);

        ih.setPosition(off);
        ihs[count] = ih;

        count++;
      }
    } catch(IOException e) { throw new ClassGenException(e.toString()); }

    byte_positions = new int[count]; // Trim to proper size
    System.arraycopy(pos, 0, byte_positions, 0, count);

    /* Pass 2: Look for BranchInstruction and update their targets, i.e.,
     * convert offsets to instruction handles.
     */
    for(int i=0; i < count; i++) {
      if(ihs[i] instanceof BranchHandle) {
        BranchInstruction bi = (BranchInstruction)ihs[i].instruction;
        int target = bi.position + bi.getIndex(); /* Byte code position:
                                                   * relative -> absolute. */
        // Search for target position
        InstructionHandle ih = findHandle(ihs, pos, count, target);

        if(ih == null) // Search failed
          throw new ClassGenException("Couldn't find target for branch: " + bi);

        bi.setTarget(ih); // Update target

        // If it is a Select instruction, update all branch targets
        if(bi instanceof Select) { // Either LOOKUPSWITCH or TABLESWITCH
          Select s       = (Select)bi;
          int[]  indices = s.getIndices();

          for(int j=0; j < indices.length; j++) {
            target = bi.position + indices[j];
            ih     = findHandle(ihs, pos, count, target);

            if(ih == null) // Search failed
              throw new ClassGenException("Couldn't find target for switch: " + bi);

            s.setTarget(j, ih); // Update target
          }
        }
      }
    }
  }

  /**
   * Append another list after instruction (handle) ih contained in this list.
   * Consumes argument list, i.e., it becomes empty.
   *
   * @param ih where to append the instruction list
   * @param il Instruction list to append to this one
   * @return instruction handle pointing to the <B>first</B> appended instruction
   */
  public InstructionHandle append(InstructionHandle ih, InstructionList il) {
    if(il == null)
      throw new ClassGenException("Appending null InstructionList");

    if(il.isEmpty()) // Nothing to do
      return ih;

    InstructionHandle next = ih.next, ret = il.start;

    ih.next = il.start;
    il.start.prev = ih;

    il.end.next = next;

    if(next != null) // i == end ?
      next.prev = il.end;
    else
      end = il.end; // Update end ...

    length += il.length; // Update length

    il.clear();

    return ret;
  }

  /**
   * Append another list after instruction i contained in this list.
   * Consumes argument list, i.e., it becomes empty.
   *
   * @param i  where to append the instruction list
   * @param il Instruction list to append to this one
   * @return instruction handle pointing to the <B>first</B> appended instruction
   */
  public InstructionHandle append(Instruction i, InstructionList il) {
    InstructionHandle ih;

    if((ih = findInstruction2(i)) == null) // Also applies for empty list
      throw new ClassGenException("Instruction " + i +
                                  " is not contained in this list.");

    return append(ih, il);
  }

  /**
   * Append another list to this one.
   * Consumes argument list, i.e., it becomes empty.
   *
   * @param il list to append to end of this list
   * @return instruction handle of the <B>first</B> appended instruction
   */
  public InstructionHandle append(InstructionList il) {
    if(il == null)
      throw new ClassGenException("Appending null InstructionList");

    if(il.isEmpty()) // Nothing to do
      return null;

    if(isEmpty()) {
      start  = il.start;
      end    = il.end;
      length = il.length;

      il.clear();

      return start;
    } else
      return append(end, il);  // was end.instruction
  }

  /**
   * Append an instruction to the end of this list.
   *
   * @param ih instruction to append
   */
  private void append(InstructionHandle ih) {
    if(isEmpty()) {
      start = end = ih;
      ih.next = ih.prev = null;
    }
    else {
      end.next = ih;
      ih.prev  = end;
      ih.next  = null;
      end      = ih;
    }

    length++; // Update length
  }

  /**
   * Append an instruction to the end of this list.
   *
   * @param i instruction to append
   * @return instruction handle of the appended instruction
   */
  public InstructionHandle append(Instruction i) {
    InstructionHandle ih = InstructionHandle.getInstructionHandle(i);
    append(ih);

    return ih;
  }

  /**
   * Append a branch instruction to the end of this list.
   *
   * @param i branch instruction to append
   * @return branch instruction handle of the appended instruction
   */
  public BranchHandle append(BranchInstruction i) {
    BranchHandle ih = BranchHandle.getBranchHandle(i);
    append(ih);

    return ih;
  }

  /**
   * Append a single instruction j after another instruction i, which
   * must be in this list of course!
   *
   * @param i Instruction in list
   * @param j Instruction to append after i in list
   * @return instruction handle of the first appended instruction
   */
  public InstructionHandle append(Instruction i, Instruction j) {
    return append(i, new InstructionList(j));
  }

  /**
   * Append a compound instruction, after instruction i.
   *
   * @param i Instruction in list
   * @param c The composite instruction (containing an InstructionList)
   * @return instruction handle of the first appended instruction
   */
  public InstructionHandle append(Instruction i, CompoundInstruction c) {
    return append(i, c.getInstructionList());
  }

  /**
   * Append a compound instruction.
   *
   * @param c The composite instruction (containing an InstructionList)
   * @return instruction handle of the first appended instruction
   */
  public InstructionHandle append(CompoundInstruction c) {
    return append(c.getInstructionList());
  }

  /**
   * Append a compound instruction.
   *
   * @param ih where to append the instruction list
   * @param c The composite instruction (containing an InstructionList)
   * @return instruction handle of the first appended instruction
   */
  public InstructionHandle append(InstructionHandle ih, CompoundInstruction c) {
    return append(ih, c.getInstructionList());
  }

  /**
   * Append an instruction after instruction (handle) ih contained in this list.
   *
   * @param ih where to append the instruction list
   * @param i Instruction to append
   * @return instruction handle pointing to the <B>first</B> appended instruction
   */
  public InstructionHandle append(InstructionHandle ih, Instruction i) {
    return append(ih, new InstructionList(i));
  }

  /**
   * Append an instruction after instruction (handle) ih contained in this list.
   *
   * @param ih where to append the instruction list
   * @param i Instruction to append
   * @return instruction handle pointing to the <B>first</B> appended instruction
   */
  public BranchHandle append(InstructionHandle ih, BranchInstruction i) {
    BranchHandle    bh = BranchHandle.getBranchHandle(i);
    InstructionList il = new InstructionList();
    il.append(bh);

    append(ih, il);

    return bh;
  }

  /**
   * Insert another list before Instruction handle ih contained in this list.
   * Consumes argument list, i.e., it becomes empty.
   *
   * @param i  where to append the instruction list
   * @param il Instruction list to insert
   * @return instruction handle of the first inserted instruction
   */
  public InstructionHandle insert(InstructionHandle ih, InstructionList il) {
    if(il == null)
      throw new ClassGenException("Inserting null InstructionList");

    if(il.isEmpty()) // Nothing to do
      return ih;

    InstructionHandle prev = ih.prev, ret = il.start;

    ih.prev = il.end;
    il.end.next = ih;

    il.start.prev = prev;

    if(prev != null) // ih == start ?
      prev.next = il.start;
    else
      start = il.start; // Update start ...

    length += il.length; // Update length

    il.clear();

    return ret;
  }

  /**
   * Insert another list.
   *
   * @param il list to insert before start of this list
   * @return instruction handle of the first inserted instruction
   */
  public InstructionHandle insert(InstructionList il) {
    if(isEmpty()) {
      append(il); // Code is identical for this case
      return start;
    }
    else
      return insert(start, il);
  }

  /**
   * Insert an instruction at start of this list.
   *
   * @param ih instruction to insert
   */
  private void insert(InstructionHandle ih) {
    if(isEmpty()) {
      start = end = ih;
      ih.next = ih.prev = null;
    } else {
      start.prev = ih;
      ih.next    = start;
      ih.prev    = null;
      start      = ih;
    }

    length++;
  }

  /**
   * Insert another list before Instruction i contained in this list.
   * Consumes argument list, i.e., it becomes empty.
   *
   * @param i  where to append the instruction list
   * @param il Instruction list to insert
   * @return instruction handle pointing to the first inserted instruction,
   * i.e., il.getStart()
   */
  public InstructionHandle insert(Instruction i, InstructionList il) {
    InstructionHandle ih;

    if((ih = findInstruction1(i)) == null)
      throw new ClassGenException("Instruction " + i +
                                  " is not contained in this list.");

    return insert(ih, il);
  }

  /**
   * Insert an instruction at start of this list.
   *
   * @param i instruction to insert
   * @return instruction handle of the inserted instruction
   */
  public InstructionHandle insert(Instruction i) {
    InstructionHandle ih = InstructionHandle.getInstructionHandle(i);
    insert(ih);

    return ih;
  }

  /**
   * Insert a branch instruction at start of this list.
   *
   * @param i branch instruction to insert
   * @return branch instruction handle of the appended instruction
   */
  public BranchHandle insert(BranchInstruction i) {
    BranchHandle ih = BranchHandle.getBranchHandle(i);
    insert(ih);
    return ih;
  }

  /**
   * Insert a single instruction j before another instruction i, which
   * must be in this list of course!
   *
   * @param i Instruction in list
   * @param j Instruction to insert before i in list
   * @return instruction handle of the first inserted instruction
   */
  public InstructionHandle insert(Instruction i, Instruction j) {
    return insert(i, new InstructionList(j));
  }

  /**
   * Insert a compound instruction before instruction i.
   *
   * @param i Instruction in list
   * @param c The composite instruction (containing an InstructionList)
   * @return instruction handle of the first inserted instruction
   */
  public InstructionHandle insert(Instruction i, CompoundInstruction c) {
    return insert(i, c.getInstructionList());
  }

  /**
   * Insert a compound instruction.
   *
   * @param c The composite instruction (containing an InstructionList)
   * @return instruction handle of the first inserted instruction
   */
  public InstructionHandle insert(CompoundInstruction c) {
    return insert(c.getInstructionList());
  }

  /**
   * Insert an instruction before instruction (handle) ih contained in this list.
   *
   * @param ih where to insert to the instruction list
   * @param i Instruction to insert
   * @return instruction handle of the first inserted instruction
   */
  public InstructionHandle insert(InstructionHandle ih, Instruction i) {
    return insert(ih, new InstructionList(i));
  }

  /**
   * Insert a compound instruction.
   *
   * @param ih where to insert the instruction list
   * @param c The composite instruction (containing an InstructionList)
   * @return instruction handle of the first inserted instruction
   */
  public InstructionHandle insert(InstructionHandle ih, CompoundInstruction c) {
    return insert(ih, c.getInstructionList());
  }

  /**
   * Insert an instruction before instruction (handle) ih contained in this list.
   *
   * @param ih where to insert to the instruction list
   * @param i Instruction to insert
   * @return instruction handle of the first inserted instruction
   */
  public BranchHandle insert(InstructionHandle ih, BranchInstruction i) {
    BranchHandle    bh = BranchHandle.getBranchHandle(i);
    InstructionList il = new InstructionList();
    il.append(bh);

    insert(ih, il);

    return bh;
  }

  /**
   * Take all instructions (handles) from "start" to "end" and append them after the
   * new location "target". Of course, "end" must be after "start" and target must
   * not be located withing this range. If you want to move something to the start of
   * the list use null as value for target.<br>
   * Any instruction targeters pointing to handles within the block, keep their targets.
   *
   * @param start  of moved block
   * @param end    of moved block
   * @param target of moved block
   */
  public void move(InstructionHandle start, InstructionHandle end, InstructionHandle target) {
    // Step 1: Check constraints

    if((start == null) || (end == null))
      throw new ClassGenException("Invalid null handle: From " + start + " to " + end);

    if((target == start) || (target == end))
      throw new ClassGenException("Invalid range: From " + start + " to " + end +
                                  " contains target " + target);

    for(InstructionHandle ih = start; ih != end.next; ih = ih.next) {
      if(ih == null) // At end of list, end not found yet
        throw new ClassGenException("Invalid range: From " + start + " to " + end);
      else if(ih == target) // target may be null
        throw new ClassGenException("Invalid range: From " + start + " to " + end +
                                    " contains target " + target);
    }

    // Step 2: Temporarily remove the given instructions from the list

    InstructionHandle prev = start.prev, next = end.next;

    if(prev != null)
      prev.next = next;
    else // start == this.start!
      this.start = next;

    if(next != null)
      next.prev = prev;
    else // end == this.end!
      this.end = prev;

    start.prev = end.next = null;

    // Step 3: append after target

    if(target == null) { // append to start of list
      end.next = this.start;
      this.start = start;
    } else {
      next = target.next;

      target.next = start;
      start.prev  = target;
      end.next    = next;

      if(next != null)
        next.prev = end;
    }
  }

  /**
   * Move a single instruction (handle) to a new location.
   *
   * @param ih     moved instruction
   * @param target new location of moved instruction
   */
  public void move(InstructionHandle ih, InstructionHandle target) {
    move(ih, ih, target);
  }

  /**
   * Remove from instruction `prev' to instruction `next' both contained
   * in this list. Throws TargetLostException when one of the removed instruction handles
   * is still being targeted.
   *
   * @param prev where to start deleting (predecessor, exclusive)
   * @param next where to end deleting (successor, exclusive)
   */
  private void remove(InstructionHandle prev, InstructionHandle next)
    throws TargetLostException
  {
    InstructionHandle first, last; // First and last deleted instruction

    if((prev == null) && (next == null)) { // singleton list
      first = last = start;
      start = end = null;
    } else {
      if(prev == null) { // At start of list
        first = start;
        start = next;
      } else {
        first     = prev.next;
        prev.next = next;
      }

      if(next == null) { // At end of list
        last = end;
        end  = prev;
      } else {
        last      = next.prev;
        next.prev = prev;
      }
    }

    first.prev = null; // Completely separated from rest of list
    last.next  = null;

    ArrayList target_vec = new ArrayList();

    for(InstructionHandle ih=first; ih != null; ih = ih.next)
      ih.getInstruction().dispose(); // e.g. BranchInstructions release their targets

    StringBuffer buf = new StringBuffer("{ ");
    for(InstructionHandle ih=first; ih != null; ih = next) {
      next = ih.next;
      length--;

      if(ih.hasTargeters()) { // Still got targeters?
        target_vec.add(ih);
        buf.append(ih.toString(true) + " ");
        ih.next = ih.prev = null;
      } else
        ih.dispose();
    }

    buf.append("}");

    if(!target_vec.isEmpty()) {
      InstructionHandle[] targeted = new InstructionHandle[target_vec.size()];
      target_vec.toArray(targeted);
      throw new TargetLostException(targeted, buf.toString());
    }
  }

  /**
   * Remove instruction from this list. The corresponding Instruction
   * handles must not be reused!
   *
   * @param ih instruction (handle) to remove
   */
  public void delete(InstructionHandle ih) throws TargetLostException {
    remove(ih.prev, ih.next);
  }

  /**
   * Remove instruction from this list. The corresponding Instruction
   * handles must not be reused!
   *
   * @param i instruction to remove
   */
  public void delete(Instruction i) throws TargetLostException {
    InstructionHandle ih;

    if((ih = findInstruction1(i)) == null)
      throw new ClassGenException("Instruction " + i +
                                  " is not contained in this list.");
    delete(ih);
  }

  /**
   * Remove instructions from instruction `from' to instruction `to' contained
   * in this list. The user must ensure that `from' is an instruction before
   * `to', or risk havoc. The corresponding Instruction handles must not be reused!
   *
   * @param from where to start deleting (inclusive)
   * @param to   where to end deleting (inclusive)
   */
  public void delete(InstructionHandle from, InstructionHandle to)
    throws TargetLostException
  {
    remove(from.prev, to.next);
  }

  /**
   * Remove instructions from instruction `from' to instruction `to' contained
   * in this list. The user must ensure that `from' is an instruction before
   * `to', or risk havoc. The corresponding Instruction handles must not be reused!
   *
   * @param from where to start deleting (inclusive)
   * @param to   where to end deleting (inclusive)
   */
  public void delete(Instruction from, Instruction to) throws TargetLostException {
    InstructionHandle from_ih, to_ih;

    if((from_ih = findInstruction1(from)) == null)
      throw new ClassGenException("Instruction " + from +
                                  " is not contained in this list.");

    if((to_ih = findInstruction2(to)) == null)
      throw new ClassGenException("Instruction " + to +
                                  " is not contained in this list.");
    delete(from_ih, to_ih);
  }

  /**
   * Search for given Instruction reference, start at beginning of list.
   *
   * @param i instruction to search for
   * @return instruction found on success, null otherwise
   */
  private InstructionHandle findInstruction1(Instruction i) {
    for(InstructionHandle ih=start; ih != null; ih = ih.next)
      if(ih.instruction == i)
        return ih;

    return null;
  }

  /**
   * Search for given Instruction reference, start at end of list
   *
   * @param i instruction to search for
   * @return instruction found on success, null otherwise
   */
  private InstructionHandle findInstruction2(Instruction i) {
    for(InstructionHandle ih=end; ih != null; ih = ih.prev)
      if(ih.instruction == i)
        return ih;

    return null;
  }

  public boolean contains(InstructionHandle i) {
    if(i == null)
      return false;

    for(InstructionHandle ih=start; ih != null; ih = ih.next)
      if(ih == i)
        return true;

    return false;
  }

  public boolean contains(Instruction i) {
    return findInstruction1(i) != null;
  }

  public void setPositions() {
    setPositions(false);
  }

  /**
   * Give all instructions their position number (offset in byte stream), i.e.,
   * make the list ready to be dumped.
   *
   * @param check Perform sanity checks, e.g. if all targeted instructions really belong
   * to this list
   */
  public void setPositions(boolean check) {
    int max_additional_bytes = 0, additional_bytes = 0;
    int index = 0, count = 0;
    int[] pos = new int[length];

    /* Pass 0: Sanity checks
     */
    if(check) {
      for(InstructionHandle ih=start; ih != null; ih = ih.next) {
        Instruction i = ih.instruction;

        if(i instanceof BranchInstruction) { // target instruction within list?
          Instruction inst = ((BranchInstruction)i).getTarget().instruction;
          if(!contains(inst))
            throw new ClassGenException("Branch target of " +
                                        Constants.OPCODE_NAMES[i.opcode] + ":" +
                                        inst + " not in instruction list");

          if(i instanceof Select) {
            InstructionHandle[] targets = ((Select)i).getTargets();

            for(int j=0; j < targets.length; j++) {
              inst = targets[j].instruction;
              if(!contains(inst))
                throw new ClassGenException("Branch target of " +
                                            Constants.OPCODE_NAMES[i.opcode] + ":" +
                                            inst + " not in instruction list");
            }
          }

          if(!(ih instanceof BranchHandle))
            throw new ClassGenException("Branch instruction " +
                                        Constants.OPCODE_NAMES[i.opcode] + ":" +
                                        inst + " not contained in BranchHandle.");

        }
      }
    }

    /* Pass 1: Set position numbers and sum up the maximum number of bytes an
     * instruction may be shifted.
     */
    for(InstructionHandle ih=start; ih != null; ih = ih.next) {
      Instruction i = ih.instruction;

      ih.setPosition(index);
      pos[count++] = index;

      /* Get an estimate about how many additional bytes may be added, because
       * BranchInstructions may have variable length depending on the target
       * offset (short vs. int) or alignment issues (TABLESWITCH and
       * LOOKUPSWITCH).
       */
      switch(i.getOpcode()) {
      case Constants.JSR: case Constants.GOTO:
        max_additional_bytes += 2;
        break;

      case Constants.TABLESWITCH: case Constants.LOOKUPSWITCH:
        max_additional_bytes += 3;
        break;
      }

      index += i.getLength();
    }

    /* Pass 2: Expand the variable-length (Branch)Instructions depending on
     * the target offset (short or int) and ensure that branch targets are
     * within this list.
     */
    for(InstructionHandle ih=start; ih != null; ih = ih.next)
      additional_bytes += ih.updatePosition(additional_bytes, max_additional_bytes);

    /* Pass 3: Update position numbers (which may have changed due to the
     * preceding expansions), like pass 1.
     */
    index=count=0;
    for(InstructionHandle ih=start; ih != null; ih = ih.next) {
      Instruction i = ih.instruction;

      ih.setPosition(index);
      pos[count++] = index;
      index += i.getLength();
    }

    byte_positions = new int[count]; // Trim to proper size
    System.arraycopy(pos, 0, byte_positions, 0, count);
  }

  /**
   * When everything is finished, use this method to convert the instruction
   * list into an array of bytes.
   *
   * @return the byte code ready to be dumped
   */
  public byte[] getByteCode() {
    // Update position indices of instructions
    setPositions();

    ByteArrayOutputStream b   = new ByteArrayOutputStream();
    DataOutputStream      out = new DataOutputStream(b);

    try {
      for(InstructionHandle ih=start; ih != null; ih = ih.next) {
        Instruction i = ih.instruction;
        i.dump(out); // Traverse list
      }
    } catch(IOException e) {
      System.err.println(e);
      return null;
    }

    return b.toByteArray();
  }

  /**
   * @return an array of instructions without target information for branch instructions.
   */
  public Instruction[] getInstructions() {
    ByteSequence  bytes        = new ByteSequence(getByteCode());
    ArrayList     instructions = new ArrayList();

    try {
      while(bytes.available() > 0) {
        instructions.add(Instruction.readInstruction(bytes));
      }
    } catch(IOException e) { throw new ClassGenException(e.toString()); }

    Instruction[] result = new Instruction[instructions.size()];
    instructions.toArray(result);
    return result;
  }

  public String toString() {
    return toString(true);
  }

  /**
   * @param verbose toggle output format
   * @return String containing all instructions in this list.
   */
  public String toString(boolean verbose) {
    StringBuffer buf = new StringBuffer();

    for(InstructionHandle ih=start; ih != null; ih = ih.next) {
      buf.append(ih.toString(verbose) + "\n");
    }

    return buf.toString();
  }

  /**
   * @return Enumeration that lists all instructions (handles)
   */
  public Iterator iterator() {
    return new Iterator() {
      private InstructionHandle ih = start;

      public Object next() {
        InstructionHandle i = ih;
        ih = ih.next;
        return i;
      }

      public void remove() {
        throw new UnsupportedOperationException();
      }

      public boolean hasNext() { return ih != null; }
    };
  }

  /**
   * @return array containing all instructions (handles)
   */
  public InstructionHandle[] getInstructionHandles() {
    InstructionHandle[] ihs = new InstructionHandle[length];
    InstructionHandle   ih  = start;

    for(int i=0; i < length; i++) {
      ihs[i] = ih;
      ih = ih.next;
    }

    return ihs;
  }

  /**
   * Get positions (offsets) of all instructions in the list. This relies on that
   * the list has been freshly created from an byte code array, or that setPositions()
   * has been called. Otherwise this may be inaccurate.
   *
   * @return array containing all instruction's offset in byte code
   */
  public int[] getInstructionPositions() { return byte_positions; }

  /**
   * @return complete, i.e., deep copy of this list
   */
  public InstructionList copy() {
    HashMap         map = new HashMap();
    InstructionList il  = new InstructionList();

    /* Pass 1: Make copies of all instructions, append them to the new list
     * and associate old instruction references with the new ones, i.e.,
     * a 1:1 mapping.
     */
    for(InstructionHandle ih=start; ih != null; ih = ih.next) {
      Instruction i = ih.instruction;
      Instruction c = i.copy(); // Use clone for shallow copy

      if(c instanceof BranchInstruction)
        map.put(ih, il.append((BranchInstruction)c));
      else
        map.put(ih, il.append(c));
    }

    /* Pass 2: Update branch targets.
     */
    InstructionHandle ih=start;
    InstructionHandle ch=il.start;

    while(ih != null) {
      Instruction i = ih.instruction;
      Instruction c = ch.instruction;

      if(i instanceof BranchInstruction) {
        BranchInstruction bi      = (BranchInstruction)i;
        BranchInstruction bc      = (BranchInstruction)c;
        InstructionHandle itarget = bi.getTarget(); // old target

        // New target is in hash map
        bc.setTarget((InstructionHandle)map.get(itarget));

        if(bi instanceof Select) { // Either LOOKUPSWITCH or TABLESWITCH
          InstructionHandle[] itargets = ((Select)bi).getTargets();
          InstructionHandle[] ctargets = ((Select)bc).getTargets();

          for(int j=0; j < itargets.length; j++) { // Update all targets
            ctargets[j] = (InstructionHandle)map.get(itargets[j]);
          }
        }
      }

      ih = ih.next;
      ch = ch.next;
    }

    return il;
  }

  /** Replace all references to the old constant pool with references to the new
   *  constant pool
   */
  public void replaceConstantPool(ConstantPoolGen old_cp, ConstantPoolGen new_cp) {
    for(InstructionHandle ih=start; ih != null; ih = ih.next) {
      Instruction i = ih.instruction;

      if(i instanceof CPInstruction) {
        CPInstruction ci = (CPInstruction)i;
        Constant      c  = old_cp.getConstant(ci.getIndex());
        ci.setIndex(new_cp.addConstant(c, old_cp));
      }
    }
  }

  private void clear() {
    start = end = null;
    length = 0;
  }

  /**
   * Delete contents of list. Provides besser memory utilization,
   * because the system then may reuse the instruction handles. This
   * method is typically called right after
   * <href="MethodGen.html#getMethod()">MethodGen.getMethod()</a>.
   */
  public void dispose() {
    // Traverse in reverse order, because ih.next is overwritten
    for(InstructionHandle ih=end; ih != null; ih = ih.prev)
      /* Causes BranchInstructions to release target and targeters, because it
       * calls dispose() on the contained instruction.
       */
      ih.dispose();

    clear();
  }

  /**
   * @return start of list
   */
  public InstructionHandle getStart() { return start; }

  /**
   * @return end of list
   */
  public InstructionHandle getEnd()   { return end; }

  /**
   * @return length of list (Number of instructions, not bytes)
   */
  public int getLength() { return length; }

  /**
   * @return length of list (Number of instructions, not bytes)
   */
  public int size() { return length; }

  /**
   * Redirect all references from old_target to new_target, i.e., update targets
   * of branch instructions.
   *
   * @param old_target the old target instruction handle
   * @param new_target the new target instruction handle
   */
  public void redirectBranches(InstructionHandle old_target,
                               InstructionHandle new_target) {
    for(InstructionHandle ih = start; ih != null; ih = ih.next) {
      Instruction i  = ih.getInstruction();

      if(i instanceof BranchInstruction) {
        BranchInstruction b      = (BranchInstruction)i;
        InstructionHandle target = b.getTarget();

        if(target == old_target)
          b.setTarget(new_target);

        if(b instanceof Select) { // Either LOOKUPSWITCH or TABLESWITCH
          InstructionHandle[] targets = ((Select)b).getTargets();

          for(int j=0; j < targets.length; j++) // Update targets
            if(targets[j] == old_target)
              ((Select)b).setTarget(j, new_target);
        }
      }
    }
  }

  /**
   * Redirect all references of local variables from old_target to new_target.
   *
   * @param lg array of local variables
   * @param old_target the old target instruction handle
   * @param new_target the new target instruction handle
   * @see MethodGen
   */
  public void redirectLocalVariables(LocalVariableGen[] lg,
                                     InstructionHandle old_target,
                                     InstructionHandle new_target) {
    for(int i=0; i < lg.length; i++) {
      InstructionHandle start = lg[i].getStart();
      InstructionHandle end   = lg[i].getEnd();

      if(start == old_target)
        lg[i].setStart(new_target);

      if(end == old_target)
        lg[i].setEnd(new_target);
    }
  }

  /**
   * Redirect all references of exception handlers from old_target to new_target.
   *
   * @param exceptions array of exception handlers
   * @param old_target the old target instruction handle
   * @param new_target the new target instruction handle
   * @see MethodGen
   */
  public void redirectExceptionHandlers(CodeExceptionGen[] exceptions,
                                        InstructionHandle old_target,
                                        InstructionHandle new_target) {
    for(int i=0; i < exceptions.length; i++) {
      if(exceptions[i].getStartPC() == old_target)
        exceptions[i].setStartPC(new_target);

      if(exceptions[i].getEndPC() == old_target)
        exceptions[i].setEndPC(new_target);

      if(exceptions[i].getHandlerPC() == old_target)
        exceptions[i].setHandlerPC(new_target);
    }
  }

  private ArrayList observers;

  /** Add observer for this object.
   */
  public void addObserver(InstructionListObserver o) {
    if(observers == null)
      observers = new ArrayList();

    observers.add(o);
  }

  /** Remove observer for this object.
   */
  public void removeObserver(InstructionListObserver o) {
    if(observers != null)
      observers.remove(o);
  }

  /** Call notify() method on all observers. This method is not called
   * automatically whenever the state has changed, but has to be
   * called by the user after he has finished editing the object.
   */
  public void update() {
    if(observers != null)
      for(Iterator e = observers.iterator(); e.hasNext(); )
        ((InstructionListObserver)e.next()).notify(this);
  }
}
