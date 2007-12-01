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
import com.sun.org.apache.bcel.internal.Constants;
import java.io.*;

/**
 * Wrapper class for push operations, which are implemented either as BIPUSH,
 * LDC or xCONST_n instructions.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public final class PUSH
  implements CompoundInstruction, VariableLengthInstruction, InstructionConstants
{
  private Instruction instruction;

  /**
   * This constructor also applies for values of type short, char, byte
   *
   * @param cp Constant pool
   * @param value to be pushed
   */
  public PUSH(ConstantPoolGen cp, int value) {
    if((value >= -1) && (value <= 5)) // Use ICONST_n
      instruction = INSTRUCTIONS[Constants.ICONST_0 + value];
    else if((value >= -128) && (value <= 127)) // Use BIPUSH
      instruction = new BIPUSH((byte)value);
    else if((value >= -32768) && (value <= 32767)) // Use SIPUSH
      instruction = new SIPUSH((short)value);
    else // If everything fails create a Constant pool entry
      instruction = new LDC(cp.addInteger(value));
  }

  /**
   * @param cp Constant pool
   * @param value to be pushed
   */
  public PUSH(ConstantPoolGen cp, boolean value) {
    instruction = INSTRUCTIONS[Constants.ICONST_0 + (value? 1 : 0)];
  }

  /**
   * @param cp Constant pool
   * @param value to be pushed
   */
  public PUSH(ConstantPoolGen cp, float value) {
    if(value == 0.0)
      instruction = FCONST_0;
    else if(value == 1.0)
      instruction = FCONST_1;
    else if(value == 2.0)
      instruction = FCONST_2;
    else // Create a Constant pool entry
      instruction = new LDC(cp.addFloat(value));
  }

  /**
   * @param cp Constant pool
   * @param value to be pushed
   */
  public PUSH(ConstantPoolGen cp, long value) {
    if(value == 0)
      instruction = LCONST_0;
    else if(value == 1)
      instruction = LCONST_1;
    else // Create a Constant pool entry
      instruction = new LDC2_W(cp.addLong(value));
  }

  /**
   * @param cp Constant pool
   * @param value to be pushed
   */
  public PUSH(ConstantPoolGen cp, double value) {
    if(value == 0.0)
      instruction = DCONST_0;
    else if(value == 1.0)
      instruction = DCONST_1;
    else // Create a Constant pool entry
      instruction = new LDC2_W(cp.addDouble(value));
  }

  /**
   * @param cp Constant pool
   * @param value to be pushed
   */
  public PUSH(ConstantPoolGen cp, String value) {
    if(value == null)
      instruction = ACONST_NULL;
    else // Create a Constant pool entry
      instruction = new LDC(cp.addString(value));
  }

  /**
   * @param cp Constant pool
   * @param value to be pushed
   */
  public PUSH(ConstantPoolGen cp, Number value) {
    if((value instanceof Integer) || (value instanceof Short) || (value instanceof Byte))
      instruction = new PUSH(cp, value.intValue()).instruction;
    else if(value instanceof Double)
      instruction = new PUSH(cp, value.doubleValue()).instruction;
    else if(value instanceof Float)
      instruction = new PUSH(cp, value.floatValue()).instruction;
    else if(value instanceof Long)
      instruction = new PUSH(cp, value.longValue()).instruction;
    else
      throw new ClassGenException("What's this: " + value);
  }

  /**
   * @param cp Constant pool
   * @param value to be pushed
   */
  public PUSH(ConstantPoolGen cp, Character value) {
    this(cp, (int)value.charValue());
  }

  /**
   * @param cp Constant pool
   * @param value to be pushed
   */
  public PUSH(ConstantPoolGen cp, Boolean value) {
    this(cp, value.booleanValue());
  }

  public final InstructionList getInstructionList() {
    return new InstructionList(instruction);
  }

  public final Instruction getInstruction() {
    return instruction;
  }

  /**
   * @return mnemonic for instruction
   */
  public String toString() {
    return instruction.toString() + " (PUSH)";
  }
}
