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
import com.sun.org.apache.bcel.internal.Constants;
import com.sun.org.apache.bcel.internal.classfile.*;

/**
 * Abstract super class for instructions that use an index into the
 * constant pool such as LDC, INVOKEVIRTUAL, etc.
 *
 * @see ConstantPoolGen
 * @see LDC
 * @see INVOKEVIRTUAL
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public abstract class CPInstruction extends Instruction
  implements TypedInstruction, IndexedInstruction
{
  protected int index; // index to constant pool

  /**
   * Empty constructor needed for the Class.newInstance() statement in
   * Instruction.readInstruction(). Not to be used otherwise.
   */
  CPInstruction() {}

  /**
   * @param index to constant pool
   */
  protected CPInstruction(short opcode, int index) {
    super(opcode, (short)3);
    setIndex(index);
  }

  /**
   * Dump instruction as byte code to stream out.
   * @param out Output stream
   */
  public void dump(DataOutputStream out) throws IOException {
    out.writeByte(opcode);
    out.writeShort(index);
  }

  /**
   * Long output format:
   *
   * &lt;name of opcode&gt; "["&lt;opcode number&gt;"]"
   * "("&lt;length of instruction&gt;")" "&lt;"&lt; constant pool index&gt;"&gt;"
   *
   * @param verbose long/short format switch
   * @return mnemonic for instruction
   */
  public String toString(boolean verbose) {
    return super.toString(verbose) + " " + index;
  }

  /**
   * @return mnemonic for instruction with symbolic references resolved
   */
  public String toString(ConstantPool cp) {
    Constant c   = cp.getConstant(index);
    String   str = cp.constantToString(c);

    if(c instanceof ConstantClass)
      str = str.replace('.', '/');

    return com.sun.org.apache.bcel.internal.Constants.OPCODE_NAMES[opcode] + " " + str;
  }

  /**
   * Read needed data (i.e., index) from file.
   * @param bytes input stream
   * @param wide wide prefix?
   */
  protected void initFromFile(ByteSequence bytes, boolean wide)
       throws IOException
  {
    setIndex(bytes.readUnsignedShort());
    length = 3;
  }

  /**
   * @return index in constant pool referred by this instruction.
   */
  public final int getIndex() { return index; }

  /**
   * Set the index to constant pool.
   * @param index in  constant pool.
   */
  public void setIndex(int index) {
    if(index < 0)
      throw new ClassGenException("Negative index value: " + index);

    this.index = index;
  }

  /** @return type related with this instruction.
   */
  public Type getType(ConstantPoolGen cpg) {
    ConstantPool cp   = cpg.getConstantPool();
    String       name = cp.getConstantString(index, com.sun.org.apache.bcel.internal.Constants.CONSTANT_Class);

    if(!name.startsWith("["))
      name = "L" + name + ";";

    return Type.getType(name);
  }
}
