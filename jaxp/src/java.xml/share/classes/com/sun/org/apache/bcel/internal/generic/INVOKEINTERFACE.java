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

import com.sun.org.apache.bcel.internal.classfile.ConstantPool;
import com.sun.org.apache.bcel.internal.Constants;
import com.sun.org.apache.bcel.internal.ExceptionConstants;

import java.io.*;
import com.sun.org.apache.bcel.internal.util.ByteSequence;

/**
 * INVOKEINTERFACE - Invoke interface method
 * <PRE>Stack: ..., objectref, [arg1, [arg2 ...]] -&gt; ...</PRE>
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public final class INVOKEINTERFACE extends InvokeInstruction {
  private int nargs; // Number of arguments on stack (number of stack slots), called "count" in vmspec2

  /**
   * Empty constructor needed for the Class.newInstance() statement in
   * Instruction.readInstruction(). Not to be used otherwise.
   */
  INVOKEINTERFACE() {}

  public INVOKEINTERFACE(int index, int nargs) {
    super(Constants.INVOKEINTERFACE, index);
    length = 5;

    if(nargs < 1)
      throw new ClassGenException("Number of arguments must be > 0 " + nargs);

    this.nargs = nargs;
  }

  /**
   * Dump instruction as byte code to stream out.
   * @param out Output stream
   */
  public void dump(DataOutputStream out) throws IOException {
    out.writeByte(opcode);
    out.writeShort(index);
    out.writeByte(nargs);
    out.writeByte(0);
  }

  /**
   * The <B>count</B> argument according to the Java Language Specification,
   * Second Edition.
   */
  public int getCount() { return nargs; }

  /**
   * Read needed data (i.e., index) from file.
   */
  protected void initFromFile(ByteSequence bytes, boolean wide)
       throws IOException
  {
    super.initFromFile(bytes, wide);

    length = 5;
    nargs = bytes.readUnsignedByte();
    bytes.readByte(); // Skip 0 byte
  }

  /**
   * @return mnemonic for instruction with symbolic references resolved
   */
  public String toString(ConstantPool cp) {
    return super.toString(cp) + " " + nargs;
  }

  public int consumeStack(ConstantPoolGen cpg) { // nargs is given in byte-code
    return nargs;  // nargs includes this reference
  }

  public Class[] getExceptions() {
    Class[] cs = new Class[4 + ExceptionConstants.EXCS_INTERFACE_METHOD_RESOLUTION.length];

    System.arraycopy(ExceptionConstants.EXCS_INTERFACE_METHOD_RESOLUTION, 0,
                     cs, 0, ExceptionConstants.EXCS_INTERFACE_METHOD_RESOLUTION.length);

    cs[ExceptionConstants.EXCS_INTERFACE_METHOD_RESOLUTION.length+3] = ExceptionConstants.INCOMPATIBLE_CLASS_CHANGE_ERROR;
    cs[ExceptionConstants.EXCS_INTERFACE_METHOD_RESOLUTION.length+2] = ExceptionConstants.ILLEGAL_ACCESS_ERROR;
    cs[ExceptionConstants.EXCS_INTERFACE_METHOD_RESOLUTION.length+1] = ExceptionConstants.ABSTRACT_METHOD_ERROR;
    cs[ExceptionConstants.EXCS_INTERFACE_METHOD_RESOLUTION.length]   = ExceptionConstants.UNSATISFIED_LINK_ERROR;

    return cs;
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
    v.visitExceptionThrower(this);
    v.visitTypedInstruction(this);
    v.visitStackConsumer(this);
    v.visitStackProducer(this);
    v.visitLoadClass(this);
    v.visitCPInstruction(this);
    v.visitFieldOrMethod(this);
    v.visitInvokeInstruction(this);
    v.visitINVOKEINTERFACE(this);
  }
}
