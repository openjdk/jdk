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
