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


/**
 * ARRAYLENGTH -  Get length of array
 * <PRE>Stack: ..., arrayref -&gt; ..., length</PRE>
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public class ARRAYLENGTH extends Instruction
  implements ExceptionThrower, StackProducer {
  /** Get length of array
   */
  public ARRAYLENGTH() {
    super(com.sun.org.apache.bcel.internal.Constants.ARRAYLENGTH, (short)1);
  }

  /** @return exceptions this instruction may cause
   */
  public Class[] getExceptions() {
    return new Class[] { com.sun.org.apache.bcel.internal.ExceptionConstants.NULL_POINTER_EXCEPTION };
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
    v.visitStackProducer(this);
    v.visitARRAYLENGTH(this);
  }
}
