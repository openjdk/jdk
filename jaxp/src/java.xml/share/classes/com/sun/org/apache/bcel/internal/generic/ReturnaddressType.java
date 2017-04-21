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
import java.util.Objects;

/**
 * Returnaddress, the type JSR or JSR_W instructions push upon the stack.
 *
 * see vmspec2 3.3.3
 * @author  <A HREF="http://www.inf.fu-berlin.de/~ehaase">Enver Haase</A>
 */
public class ReturnaddressType extends Type {

  public static final ReturnaddressType NO_TARGET = new ReturnaddressType();
  private InstructionHandle returnTarget;

  /**
   * A Returnaddress [that doesn't know where to return to].
   */
  private ReturnaddressType(){
    super(Constants.T_ADDRESS, "<return address>");
  }

  /**
   * Creates a ReturnaddressType object with a target.
   */
  public ReturnaddressType(InstructionHandle returnTarget) {
    super(Constants.T_ADDRESS, "<return address targeting "+returnTarget+">");
        this.returnTarget = returnTarget;
  }

  @Override
  public int hashCode() {
      return Objects.hashCode(this.returnTarget);
  }

  /**
   * Returns if the two Returnaddresses refer to the same target.
   */
  @Override
  public boolean equals(Object rat){
    if(!(rat instanceof ReturnaddressType))
      return false;

    return ((ReturnaddressType)rat).returnTarget.equals(this.returnTarget);
  }

  /**
   * @return the target of this ReturnaddressType
   */
  public InstructionHandle getTarget(){
    return returnTarget;
  }
}
