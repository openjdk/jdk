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
