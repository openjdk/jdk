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
/**
 * Super class for the x2y family of instructions.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public abstract class ConversionInstruction extends Instruction
  implements TypedInstruction, StackProducer, StackConsumer {
  /**
   * Empty constructor needed for the Class.newInstance() statement in
   * Instruction.readInstruction(). Not to be used otherwise.
   */
  ConversionInstruction() {}

  /**
   * @param opcode opcode of instruction
   */
  protected ConversionInstruction(short opcode) {
    super(opcode, (short)1);
  }

  /** @return type associated with the instruction
   */
  public Type getType(ConstantPoolGen cp) {
    switch(opcode) {
    case Constants.D2I: case Constants.F2I: case Constants.L2I:
      return Type.INT;
    case Constants.D2F: case Constants.I2F: case Constants.L2F:
      return Type.FLOAT;
    case Constants.D2L: case Constants.F2L: case Constants.I2L:
      return Type.LONG;
    case Constants.F2D:  case Constants.I2D: case Constants.L2D:
        return Type.DOUBLE;
    case Constants.I2B:
      return Type.BYTE;
    case Constants.I2C:
      return Type.CHAR;
    case Constants.I2S:
      return Type.SHORT;

    default: // Never reached
      throw new ClassGenException("Unknown type " + opcode);
    }
  }
}
