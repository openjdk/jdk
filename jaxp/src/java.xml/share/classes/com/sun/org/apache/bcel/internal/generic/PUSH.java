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
