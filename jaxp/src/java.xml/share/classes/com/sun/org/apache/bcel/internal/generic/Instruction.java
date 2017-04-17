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
import com.sun.org.apache.bcel.internal.classfile.Utility;
import com.sun.org.apache.bcel.internal.classfile.ConstantPool;
import java.io.*;
import com.sun.org.apache.bcel.internal.util.ByteSequence;

/**
 * Abstract super class for all Java byte codes.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public abstract class Instruction implements Cloneable, Serializable {
  protected short length = 1;  // Length of instruction in bytes
  protected short opcode = -1; // Opcode number

  private static InstructionComparator cmp = InstructionComparator.DEFAULT;

  /**
   * Empty constructor needed for the Class.newInstance() statement in
   * Instruction.readInstruction(). Not to be used otherwise.
   */
  Instruction() {}

  public Instruction(short opcode, short length) {
    this.length = length;
    this.opcode = opcode;
  }

  /**
   * Dump instruction as byte code to stream out.
   * @param out Output stream
   */
  public void dump(DataOutputStream out) throws IOException {
    out.writeByte(opcode); // Common for all instructions
  }

  /** @return name of instruction, i.e., opcode name
   */
  public String getName() {
    return Constants.OPCODE_NAMES[opcode];
  }

  /**
   * Long output format:
   *
   * &lt;name of opcode&gt; "["&lt;opcode number&gt;"]"
   * "("&lt;length of instruction&gt;")"
   *
   * @param verbose long/short format switch
   * @return mnemonic for instruction
   */
  public String toString(boolean verbose) {
    if(verbose)
      return getName() + "[" + opcode + "](" + length + ")";
    else
      return getName();
  }

  /**
   * @return mnemonic for instruction in verbose format
   */
  public String toString() {
    return toString(true);
  }

  /**
   * @return mnemonic for instruction with sumbolic references resolved
   */
  public String toString(ConstantPool cp) {
    return toString(false);
  }

  /**
   * Use with caution, since `BranchInstruction's have a `target' reference which
   * is not copied correctly (only basic types are). This also applies for
   * `Select' instructions with their multiple branch targets.
   *
   * @see BranchInstruction
   * @return (shallow) copy of an instruction
   */
  public Instruction copy() {
    Instruction i = null;

    // "Constant" instruction, no need to duplicate
    if(InstructionConstants.INSTRUCTIONS[this.getOpcode()] != null)
      i = this;
    else {
      try {
        i = (Instruction)clone();
      } catch(CloneNotSupportedException e) {
        System.err.println(e);
      }
    }

    return i;
  }

  /**
   * Read needed data (e.g. index) from file.
   *
   * @param bytes byte sequence to read from
   * @param wide "wide" instruction flag
   */
  protected void initFromFile(ByteSequence bytes, boolean wide)
    throws IOException
  {}

  /**
   * Read an instruction from (byte code) input stream and return the
   * appropiate object.
   *
   * @param file file to read from
   * @return instruction object being read
   */
  public static final Instruction readInstruction(ByteSequence bytes)
    throws IOException
  {
    boolean     wide   = false;
    short       opcode = (short)bytes.readUnsignedByte();
    Instruction obj    = null;

    if(opcode == Constants.WIDE) { // Read next opcode after wide byte
      wide = true;
      opcode  = (short)bytes.readUnsignedByte();
    }

    if(InstructionConstants.INSTRUCTIONS[opcode] != null)
      return InstructionConstants.INSTRUCTIONS[opcode]; // Used predefined immutable object, if available

    /* Find appropiate class, instantiate an (empty) instruction object
     * and initialize it by hand.
     */
    Class clazz;

    try {
      clazz = Class.forName(className(opcode));
    } catch (ClassNotFoundException cnfe){
      // If a class by that name does not exist, the opcode is illegal.
      // Note that IMPDEP1, IMPDEP2, BREAKPOINT are also illegal in a sense.
      throw new ClassGenException("Illegal opcode detected.");
    }

    try {
      obj = (Instruction)clazz.newInstance();

      if(wide && !((obj instanceof LocalVariableInstruction) ||
                   (obj instanceof IINC) ||
                   (obj instanceof RET)))
        throw new Exception("Illegal opcode after wide: " + opcode);

      obj.setOpcode(opcode);
      obj.initFromFile(bytes, wide); // Do further initializations, if any
      // Byte code offset set in InstructionList
    } catch(Exception e) { throw new ClassGenException(e.toString()); }

    return obj;
  }

  private static final String className(short opcode) {
    String name = Constants.OPCODE_NAMES[opcode].toUpperCase();

    /* ICONST_0, etc. will be shortened to ICONST, etc., since ICONST_0 and the like
     * are not implemented (directly).
     */
    try {
      int  len = name.length();
      char ch1 = name.charAt(len - 2), ch2 = name.charAt(len - 1);

      if((ch1 == '_') && (ch2 >= '0')  && (ch2 <= '5'))
        name = name.substring(0, len - 2);

      if(name.equals("ICONST_M1")) // Special case
        name = "ICONST";
    } catch(StringIndexOutOfBoundsException e) { System.err.println(e); }

    return "com.sun.org.apache.bcel.internal.generic." + name;
  }

  /**
   * This method also gives right results for instructions whose
   * effect on the stack depends on the constant pool entry they
   * reference.
   *  @return Number of words consumed from stack by this instruction,
   * or Constants.UNPREDICTABLE, if this can not be computed statically
   */
  public int consumeStack(ConstantPoolGen cpg) {
    return Constants.CONSUME_STACK[opcode];
  }

  /**
   * This method also gives right results for instructions whose
   * effect on the stack depends on the constant pool entry they
   * reference.
   * @return Number of words produced onto stack by this instruction,
   * or Constants.UNPREDICTABLE, if this can not be computed statically
   */
  public int produceStack(ConstantPoolGen cpg) {
    return Constants.PRODUCE_STACK[opcode];
  }

  /**
   * @return this instructions opcode
   */
  public short getOpcode()    { return opcode; }

  /**
   * @return length (in bytes) of instruction
   */
  public int getLength()   { return length; }

  /**
   * Needed in readInstruction.
   */
  private void setOpcode(short opcode) { this.opcode = opcode; }

  /** Some instructions may be reused, so don't do anything by default.
   */
  void dispose() {}

  /**
   * Call corresponding visitor method(s). The order is:
   * Call visitor methods of implemented interfaces first, then
   * call methods according to the class hierarchy in descending order,
   * i.e., the most specific visitXXX() call comes last.
   *
   * @param v Visitor object
   */
  public abstract void accept(Visitor v);

  /** Get Comparator object used in the equals() method to determine
   * equality of instructions.
   *
   * @return currently used comparator for equals()
   */
  public static InstructionComparator getComparator() { return cmp; }

  /** Set comparator to be used for equals().
   */
  public static void setComparator(InstructionComparator c) { cmp = c; }

  /** Check for equality, delegated to comparator
   * @return true if that is an Instruction and has the same opcode
   */
  public boolean equals(Object that) {
    return (that instanceof Instruction)?
      cmp.equals(this, (Instruction)that) : false;
  }
}
