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

/**
 * Super class for JSR - Jump to subroutine
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public abstract class JsrInstruction extends BranchInstruction
  implements UnconditionalBranch, TypedInstruction, StackProducer
{
  JsrInstruction(short opcode, InstructionHandle target) {
    super(opcode, target);
  }

  /**
   * Empty constructor needed for the Class.newInstance() statement in
   * Instruction.readInstruction(). Not to be used otherwise.
   */
  JsrInstruction(){}

  /** @return return address type
   */
  public Type getType(ConstantPoolGen cp) {
    return new ReturnaddressType(physicalSuccessor());
  }

  /**
   * Returns an InstructionHandle to the physical successor
   * of this JsrInstruction. <B>For this method to work,
   * this JsrInstruction object must not be shared between
   * multiple InstructionHandle objects!</B>
   * Formally, there must not be InstructionHandle objects
   * i, j where i != j and i.getInstruction() == this ==
   * j.getInstruction().
   * @return an InstructionHandle to the "next" instruction that
   * will be executed when RETurned from a subroutine.
   */
  public InstructionHandle physicalSuccessor(){
    InstructionHandle ih = this.target;

    // Rewind!
    while(ih.getPrev() != null)
      ih = ih.getPrev();

    // Find the handle for "this" JsrInstruction object.
    while(ih.getInstruction() != this)
      ih = ih.getNext();

    InstructionHandle toThis = ih;

    while(ih != null){
        ih = ih.getNext();
        if ((ih != null) && (ih.getInstruction() == this))
        throw new RuntimeException("physicalSuccessor() called on a shared JsrInstruction.");
    }

    // Return the physical successor
    return toThis.getNext();
  }
}
