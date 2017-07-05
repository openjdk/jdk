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
import com.sun.org.apache.bcel.internal.classfile.Utility;
import com.sun.org.apache.bcel.internal.Constants;

/**
 * Abstract super class for instructions dealing with local variables.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public abstract class LocalVariableInstruction extends Instruction
  implements TypedInstruction, IndexedInstruction {
  protected int     n         = -1; // index of referenced variable
  private short     c_tag     = -1; // compact version, such as ILOAD_0
  private short     canon_tag = -1; // canonical tag such as ILOAD

  private final boolean wide() { return n > Constants.MAX_BYTE; }

  /**
   * Empty constructor needed for the Class.newInstance() statement in
   * Instruction.readInstruction(). Not to be used otherwise.
   * tag and length are defined in readInstruction and initFromFile, respectively.
   */
  LocalVariableInstruction(short canon_tag, short c_tag) {
    super();
    this.canon_tag = canon_tag;
    this.c_tag     = c_tag;
  }

  /**
   * Empty constructor needed for the Class.newInstance() statement in
   * Instruction.readInstruction(). Also used by IINC()!
   */
  LocalVariableInstruction() {
  }

  /**
   * @param opcode Instruction opcode
   * @param c_tag Instruction number for compact version, ALOAD_0, e.g.
   * @param n local variable index (unsigned short)
   */
  protected LocalVariableInstruction(short opcode, short c_tag, int n) {
    super(opcode, (short)2);

    this.c_tag = c_tag;
    canon_tag  = opcode;

    setIndex(n);
  }

  /**
   * Dump instruction as byte code to stream out.
   * @param out Output stream
   */
  public void dump(DataOutputStream out) throws IOException {
    if(wide()) // Need WIDE prefix ?
      out.writeByte(Constants.WIDE);

    out.writeByte(opcode);

    if(length > 1) { // Otherwise ILOAD_n, instruction, e.g.
      if(wide())
        out.writeShort(n);
      else
        out.writeByte(n);
    }
  }

  /**
   * Long output format:
   *
   * &lt;name of opcode&gt; "["&lt;opcode number&gt;"]"
   * "("&lt;length of instruction&gt;")" "&lt;"&lt; local variable index&gt;"&gt;"
   *
   * @param verbose long/short format switch
   * @return mnemonic for instruction
   */
  public String toString(boolean verbose) {
    if(((opcode >= Constants.ILOAD_0) &&
        (opcode <= Constants.ALOAD_3)) ||
       ((opcode >= Constants.ISTORE_0) &&
        (opcode <= Constants.ASTORE_3)))
      return super.toString(verbose);
    else
      return super.toString(verbose) + " " + n;
  }

  /**
   * Read needed data (e.g. index) from file.
   * PRE: (ILOAD <= tag <= ALOAD_3) || (ISTORE <= tag <= ASTORE_3)
   */
  protected void initFromFile(ByteSequence bytes, boolean wide)
    throws IOException
  {
    if(wide) {
      n         = bytes.readUnsignedShort();
      length    = 4;
    } else if(((opcode >= Constants.ILOAD) &&
               (opcode <= Constants.ALOAD)) ||
              ((opcode >= Constants.ISTORE) &&
               (opcode <= Constants.ASTORE))) {
      n      = bytes.readUnsignedByte();
      length = 2;
    } else if(opcode <= Constants.ALOAD_3) { // compact load instruction such as ILOAD_2
      n      = (opcode - Constants.ILOAD_0) % 4;
      length = 1;
    } else { // Assert ISTORE_0 <= tag <= ASTORE_3
      n      = (opcode - Constants.ISTORE_0) % 4;
      length = 1;
    }
 }

  /**
   * @return local variable index  referred by this instruction.
   */
  public final int getIndex() { return n; }

  /**
   * Set the local variable index
   */
  public void setIndex(int n) {
    if((n < 0) || (n > Constants.MAX_SHORT))
      throw new ClassGenException("Illegal value: " + n);

    this.n = n;

    if(n >= 0 && n <= 3) { // Use more compact instruction xLOAD_n
      opcode = (short)(c_tag + n);
      length = 1;
    } else {
      opcode = canon_tag;

      if(wide()) // Need WIDE prefix ?
        length = 4;
      else
        length = 2;
    }
  }

  /** @return canonical tag for instruction, e.g., ALOAD for ALOAD_0
   */
  public short getCanonicalTag() {
    return canon_tag;
  }

  /**
   * Returns the type associated with the instruction -
   * in case of ALOAD or ASTORE Type.OBJECT is returned.
   * This is just a bit incorrect, because ALOAD and ASTORE
   * may work on every ReferenceType (including Type.NULL) and
   * ASTORE may even work on a ReturnaddressType .
   * @return type associated with the instruction
   */
  public Type getType(ConstantPoolGen cp) {
    switch(canon_tag) {
    case Constants.ILOAD: case Constants.ISTORE:
      return Type.INT;
    case Constants.LLOAD: case Constants.LSTORE:
      return Type.LONG;
    case Constants.DLOAD: case Constants.DSTORE:
      return Type.DOUBLE;
    case Constants.FLOAD: case Constants.FSTORE:
      return Type.FLOAT;
    case Constants.ALOAD: case Constants.ASTORE:
      return Type.OBJECT;

    default: throw new ClassGenException("Oops: unknown case in switch" + canon_tag);
    }
  }
}
