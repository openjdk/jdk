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

import com.sun.org.apache.bcel.internal.classfile.*;

/**
 * This class represents an exception handler, i.e., specifies the  region where
 * a handler is active and an instruction where the actual handling is done.
 * pool as parameters. Opposed to the JVM specification the end of the handled
 * region is set to be inclusive, i.e. all instructions between start and end
 * are protected including the start and end instructions (handles) themselves.
 * The end of the region is automatically mapped to be exclusive when calling
 * getCodeException(), i.e., there is no difference semantically.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @see     MethodGen
 * @see     CodeException
 * @see     InstructionHandle
 */
public final class CodeExceptionGen
  implements InstructionTargeter, Cloneable, java.io.Serializable {
  private InstructionHandle start_pc;
  private InstructionHandle end_pc;
  private InstructionHandle handler_pc;
  private ObjectType        catch_type;

  /**
   * Add an exception handler, i.e., specify region where a handler is active and an
   * instruction where the actual handling is done.
   *
   * @param start_pc Start of handled region (inclusive)
   * @param end_pc End of handled region (inclusive)
   * @param handler_pc Where handling is done
   * @param catch_type which exception is handled, null for ANY
   */
  public CodeExceptionGen(InstructionHandle start_pc, InstructionHandle end_pc,
                          InstructionHandle handler_pc, ObjectType catch_type) {
    setStartPC(start_pc);
    setEndPC(end_pc);
    setHandlerPC(handler_pc);
    this.catch_type = catch_type;
  }

  /**
   * Get CodeException object.<BR>
   *
   * This relies on that the instruction list has already been dumped
   * to byte code or or that the `setPositions' methods has been
   * called for the instruction list.
   *
   * @param cp constant pool
   */
  public CodeException getCodeException(ConstantPoolGen cp) {
    return new CodeException(start_pc.getPosition(),
                             end_pc.getPosition() + end_pc.getInstruction().getLength(),
                             handler_pc.getPosition(),
                             (catch_type == null)? 0 : cp.addClass(catch_type));
  }

  /* Set start of handler
   * @param start_pc Start of handled region (inclusive)
   */
  public final void setStartPC(InstructionHandle start_pc) {
    BranchInstruction.notifyTargetChanging(this.start_pc, this);
    this.start_pc = start_pc;
    BranchInstruction.notifyTargetChanged(this.start_pc, this);
  }

  /* Set end of handler
   * @param end_pc End of handled region (inclusive)
   */
  public final void setEndPC(InstructionHandle end_pc) {
    BranchInstruction.notifyTargetChanging(this.end_pc, this);
    this.end_pc = end_pc;
    BranchInstruction.notifyTargetChanged(this.end_pc, this);
  }

  /* Set handler code
   * @param handler_pc Start of handler
   */
  public final void setHandlerPC(InstructionHandle handler_pc) {
    BranchInstruction.notifyTargetChanging(this.handler_pc, this);
    this.handler_pc = handler_pc;
    BranchInstruction.notifyTargetChanged(this.handler_pc, this);
  }

  /**
   * @param old_ih old target, either start or end
   * @param new_ih new target
   */
  @Override
  public void updateTarget(InstructionHandle old_ih, InstructionHandle new_ih) {
    boolean targeted = false;

    if(start_pc == old_ih) {
      targeted = true;
      setStartPC(new_ih);
    }

    if(end_pc == old_ih) {
      targeted = true;
      setEndPC(new_ih);
    }

    if(handler_pc == old_ih) {
      targeted = true;
      setHandlerPC(new_ih);
    }

    if(!targeted)
      throw new ClassGenException("Not targeting " + old_ih + ", but {" + start_pc + ", " +
                                  end_pc + ", " + handler_pc + "}");
  }

  /**
   * @return true, if ih is target of this handler
   */
  @Override
  public boolean containsTarget(InstructionHandle ih) {
    return (start_pc == ih) || (end_pc == ih) || (handler_pc == ih);
  }

  /** Sets the type of the Exception to catch. Set 'null' for ANY. */
  public void              setCatchType(ObjectType catch_type)        { this.catch_type = catch_type; }
  /** Gets the type of the Exception to catch, 'null' for ANY. */
  public ObjectType        getCatchType()                             { return catch_type; }

  /** @return start of handled region (inclusive)
   */
  public InstructionHandle getStartPC()                               { return start_pc; }

  /** @return end of handled region (inclusive)
   */
  public InstructionHandle getEndPC()                                 { return end_pc; }

  /** @return start of handler
   */
  public InstructionHandle getHandlerPC()                             { return handler_pc; }

  @Override
  public String toString() {
    return "CodeExceptionGen(" + start_pc + ", " + end_pc + ", " + handler_pc + ")";
  }

  @Override
  public Object clone() {
    try {
      return super.clone();
    } catch(CloneNotSupportedException e) {
      System.err.println(e);
      return null;
    }
  }
}
