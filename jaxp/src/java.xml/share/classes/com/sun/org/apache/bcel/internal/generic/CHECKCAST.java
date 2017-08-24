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

import com.sun.org.apache.bcel.internal.ExceptionConstants;
/**
 * CHECKCAST - Check whether object is of given type
 * <PRE>Stack: ..., objectref -&gt; ..., objectref</PRE>
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public class CHECKCAST extends CPInstruction
  implements LoadClass, ExceptionThrower, StackProducer, StackConsumer {
  /**
   * Empty constructor needed for the Class.newInstance() statement in
   * Instruction.readInstruction(). Not to be used otherwise.
   */
  CHECKCAST() {}

  /** Check whether object is of given type
   * @param n index to class in constant pool
   */
  public CHECKCAST(int index) {
    super(com.sun.org.apache.bcel.internal.Constants.CHECKCAST, index);
  }

  /** @return exceptions this instruction may cause
   */
  public Class[] getExceptions() {
    Class[] cs = new Class[1 + ExceptionConstants.EXCS_CLASS_AND_INTERFACE_RESOLUTION.length];

    System.arraycopy(ExceptionConstants.EXCS_CLASS_AND_INTERFACE_RESOLUTION, 0,
                     cs, 0, ExceptionConstants.EXCS_CLASS_AND_INTERFACE_RESOLUTION.length);
    cs[ExceptionConstants.EXCS_CLASS_AND_INTERFACE_RESOLUTION.length] =
      ExceptionConstants.CLASS_CAST_EXCEPTION;
    return cs;
  }

  public ObjectType getLoadClassType(ConstantPoolGen cpg) {
    Type t = getType(cpg);

    if(t instanceof ArrayType)
      t = ((ArrayType) t).getBasicType();

    return (t instanceof ObjectType)? (ObjectType) t : null;
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
    v.visitLoadClass(this);
    v.visitExceptionThrower(this);
    v.visitStackProducer(this);
    v.visitStackConsumer(this);
    v.visitTypedInstruction(this);
    v.visitCPInstruction(this);
    v.visitCHECKCAST(this);
  }
}
