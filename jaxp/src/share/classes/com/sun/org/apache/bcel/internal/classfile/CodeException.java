/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
package com.sun.org.apache.bcel.internal.classfile;

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

import  com.sun.org.apache.bcel.internal.Constants;
import  java.io.*;

/**
 * This class represents an entry in the exception table of the <em>Code</em>
 * attribute and is used only there. It contains a range in which a
 * particular exception handler is active.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @see     Code
 */
public final class CodeException
  implements Cloneable, Constants, Node, Serializable
{
  private int start_pc;   // Range in the code the exception handler is
  private int end_pc;     // active. start_pc is inclusive, end_pc exclusive
  private int handler_pc; /* Starting address of exception handler, i.e.,
                           * an offset from start of code.
                           */
  private int catch_type; /* If this is zero the handler catches any
                           * exception, otherwise it points to the
                           * exception class which is to be caught.
                           */
  /**
   * Initialize from another object.
   */
  public CodeException(CodeException c) {
    this(c.getStartPC(), c.getEndPC(), c.getHandlerPC(), c.getCatchType());
  }

  /**
   * Construct object from file stream.
   * @param file Input stream
   * @throws IOException
   */
  CodeException(DataInputStream file) throws IOException
  {
    this(file.readUnsignedShort(), file.readUnsignedShort(),
         file.readUnsignedShort(), file.readUnsignedShort());
  }

  /**
   * @param start_pc Range in the code the exception handler is active,
   * start_pc is inclusive while
   * @param end_pc is exclusive
   * @param handler_pc Starting address of exception handler, i.e.,
   * an offset from start of code.
   * @param catch_type If zero the handler catches any
   * exception, otherwise it points to the exception class which is
   * to be caught.
   */
  public CodeException(int start_pc, int end_pc, int handler_pc,
                       int catch_type)
  {
    this.start_pc   = start_pc;
    this.end_pc     = end_pc;
    this.handler_pc = handler_pc;
    this.catch_type = catch_type;
  }

  /**
   * Called by objects that are traversing the nodes of the tree implicitely
   * defined by the contents of a Java class. I.e., the hierarchy of methods,
   * fields, attributes, etc. spawns a tree of objects.
   *
   * @param v Visitor object
   */
  public void accept(Visitor v) {
    v.visitCodeException(this);
  }
  /**
   * Dump code exception to file stream in binary format.
   *
   * @param file Output file stream
   * @throws IOException
   */
  public final void dump(DataOutputStream file) throws IOException
  {
    file.writeShort(start_pc);
    file.writeShort(end_pc);
    file.writeShort(handler_pc);
    file.writeShort(catch_type);
  }

  /**
   * @return 0, if the handler catches any exception, otherwise it points to
   * the exception class which is to be caught.
   */
  public final int getCatchType() { return catch_type; }

  /**
   * @return Exclusive end index of the region where the handler is active.
   */
  public final int getEndPC() { return end_pc; }

  /**
   * @return Starting address of exception handler, relative to the code.
   */
  public final int getHandlerPC() { return handler_pc; }

  /**
   * @return Inclusive start index of the region where the handler is active.
   */
  public final int getStartPC() { return start_pc; }

  /**
   * @param catch_type.
   */
  public final void setCatchType(int catch_type) {
    this.catch_type = catch_type;
  }

  /**
   * @param end_pc end of handled block
   */
  public final void setEndPC(int end_pc) {
    this.end_pc = end_pc;
  }

  /**
   * @param handler_pc where the actual code is
   */
  public final void setHandlerPC(int handler_pc) {
    this.handler_pc = handler_pc;
  }

  /**
   * @param start_pc start of handled block
   */
  public final void setStartPC(int start_pc) {
    this.start_pc = start_pc;
  }

  /**
   * @return String representation.
   */
  public final String toString() {
    return "CodeException(start_pc = " + start_pc +
      ", end_pc = " + end_pc +
      ", handler_pc = " + handler_pc + ", catch_type = " + catch_type + ")";
  }

  /**
   * @return String representation.
   */
  public final String toString(ConstantPool cp, boolean verbose) {
    String str;

    if(catch_type == 0)
      str = "<Any exception>(0)";
    else
      str = Utility.compactClassName(cp.getConstantString(catch_type, CONSTANT_Class), false) +
        (verbose? "(" + catch_type + ")" : "");

    return start_pc + "\t" + end_pc + "\t" + handler_pc + "\t" + str;
  }

  public final String toString(ConstantPool cp) {
    return toString(cp, true);
  }

  /**
   * @return deep copy of this object
   */
  public CodeException copy() {
    try {
      return (CodeException)clone();
    } catch(CloneNotSupportedException e) {}

    return null;
  }
}
