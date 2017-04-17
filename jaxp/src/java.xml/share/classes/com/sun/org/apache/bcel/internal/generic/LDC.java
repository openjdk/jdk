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
 * LDC - Push item from constant pool.
 *
 * <PRE>Stack: ... -&gt; ..., item</PRE>
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public class LDC extends CPInstruction
  implements PushInstruction, ExceptionThrower, TypedInstruction {
  /**
   * Empty constructor needed for the Class.newInstance() statement in
   * Instruction.readInstruction(). Not to be used otherwise.
   */
  LDC() {}

  public LDC(int index) {
    super(com.sun.org.apache.bcel.internal.Constants.LDC_W, index);
    setSize();
  }

  // Adjust to proper size
  protected final void setSize() {
    if(index <= com.sun.org.apache.bcel.internal.Constants.MAX_BYTE) { // Fits in one byte?
      opcode = com.sun.org.apache.bcel.internal.Constants.LDC;
      length = 2;
    } else {
      opcode = com.sun.org.apache.bcel.internal.Constants.LDC_W;
      length = 3;
    }
  }

  /**
   * Dump instruction as byte code to stream out.
   * @param out Output stream
   */
  public void dump(DataOutputStream out) throws IOException {
    out.writeByte(opcode);

    if(length == 2)
      out.writeByte(index);
    else // Applies for LDC_W
      out.writeShort(index);
  }

  /**
   * Set the index to constant pool and adjust size.
   */
  public final void setIndex(int index) {
    super.setIndex(index);
    setSize();
  }

  /**
   * Read needed data (e.g. index) from file.
   */
  protected void initFromFile(ByteSequence bytes, boolean wide)
       throws IOException
  {
    length = 2;
    index  = bytes.readUnsignedByte();
  }

  public Object getValue(ConstantPoolGen cpg) {
    com.sun.org.apache.bcel.internal.classfile.Constant c = cpg.getConstantPool().getConstant(index);

    switch(c.getTag()) {
      case com.sun.org.apache.bcel.internal.Constants.CONSTANT_String:
        int i = ((com.sun.org.apache.bcel.internal.classfile.ConstantString)c).getStringIndex();
        c = cpg.getConstantPool().getConstant(i);
        return ((com.sun.org.apache.bcel.internal.classfile.ConstantUtf8)c).getBytes();

    case com.sun.org.apache.bcel.internal.Constants.CONSTANT_Float:
        return new Float(((com.sun.org.apache.bcel.internal.classfile.ConstantFloat)c).getBytes());

    case com.sun.org.apache.bcel.internal.Constants.CONSTANT_Integer:
        return new Integer(((com.sun.org.apache.bcel.internal.classfile.ConstantInteger)c).getBytes());

    default: // Never reached
      throw new RuntimeException("Unknown or invalid constant type at " + index);
      }
  }

  public Type getType(ConstantPoolGen cpg) {
    switch(cpg.getConstantPool().getConstant(index).getTag()) {
    case com.sun.org.apache.bcel.internal.Constants.CONSTANT_String:  return Type.STRING;
    case com.sun.org.apache.bcel.internal.Constants.CONSTANT_Float:   return Type.FLOAT;
    case com.sun.org.apache.bcel.internal.Constants.CONSTANT_Integer: return Type.INT;
    default: // Never reached
      throw new RuntimeException("Unknown or invalid constant type at " + index);
    }
  }

  public Class[] getExceptions() {
    return com.sun.org.apache.bcel.internal.ExceptionConstants.EXCS_STRING_RESOLUTION;
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
    v.visitStackProducer(this);
    v.visitPushInstruction(this);
    v.visitExceptionThrower(this);
    v.visitTypedInstruction(this);
    v.visitCPInstruction(this);
    v.visitLDC(this);
  }
}
