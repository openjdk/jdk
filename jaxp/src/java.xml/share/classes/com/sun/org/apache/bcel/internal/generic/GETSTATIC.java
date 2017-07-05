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
import com.sun.org.apache.bcel.internal.ExceptionConstants;

/**
 * GETSTATIC - Fetch static field from class
 * <PRE>Stack: ..., -&gt; ..., value</PRE>
 * OR
 * <PRE>Stack: ..., -&gt; ..., value.word1, value.word2</PRE>
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public class GETSTATIC extends FieldInstruction implements PushInstruction, ExceptionThrower {
  /**
   * Empty constructor needed for the Class.newInstance() statement in
   * Instruction.readInstruction(). Not to be used otherwise.
   */
  GETSTATIC() {}

  public GETSTATIC(int index) {
    super(Constants.GETSTATIC, index);
  }

  public int produceStack(ConstantPoolGen cpg) { return getFieldSize(cpg); }

  public Class[] getExceptions() {
    Class[] cs = new Class[1 + ExceptionConstants.EXCS_FIELD_AND_METHOD_RESOLUTION.length];

    System.arraycopy(ExceptionConstants.EXCS_FIELD_AND_METHOD_RESOLUTION, 0,
                     cs, 0, ExceptionConstants.EXCS_FIELD_AND_METHOD_RESOLUTION.length);
    cs[ExceptionConstants.EXCS_FIELD_AND_METHOD_RESOLUTION.length] =
      ExceptionConstants.INCOMPATIBLE_CLASS_CHANGE_ERROR;

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
    v.visitStackProducer(this);
    v.visitPushInstruction(this);
    v.visitExceptionThrower(this);
    v.visitTypedInstruction(this);
    v.visitLoadClass(this);
    v.visitCPInstruction(this);
    v.visitFieldOrMethod(this);
    v.visitFieldInstruction(this);
    v.visitGETSTATIC(this);
  }
}
