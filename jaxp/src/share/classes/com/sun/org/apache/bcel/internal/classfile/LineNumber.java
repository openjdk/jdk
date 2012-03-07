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
 * This class represents a (PC offset, line number) pair, i.e., a line number in
 * the source that corresponds to a relative address in the byte code. This
 * is used for debugging purposes.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @see     LineNumberTable
 */
public final class LineNumber implements Cloneable, Node, Serializable {
  private int start_pc;    // Program Counter (PC) corresponds to line
  private int line_number; // number in source file

  /**
   * Initialize from another object.
   */
  public LineNumber(LineNumber c) {
    this(c.getStartPC(), c.getLineNumber());
  }

  /**
   * Construct object from file stream.
   * @param file Input stream
   * @throws IOException
   */
  LineNumber(DataInputStream file) throws IOException
  {
    this(file.readUnsignedShort(), file.readUnsignedShort());
  }

  /**
   * @param start_pc Program Counter (PC) corresponds to
   * @param line_number line number in source file
   */
  public LineNumber(int start_pc, int line_number)
  {
    this.start_pc    = start_pc;
    this.line_number = line_number;
  }

  /**
   * Called by objects that are traversing the nodes of the tree implicitely
   * defined by the contents of a Java class. I.e., the hierarchy of methods,
   * fields, attributes, etc. spawns a tree of objects.
   *
   * @param v Visitor object
   */
  public void accept(Visitor v) {
    v.visitLineNumber(this);
  }

  /**
   * Dump line number/pc pair to file stream in binary format.
   *
   * @param file Output file stream
   * @throws IOException
   */
  public final void dump(DataOutputStream file) throws IOException
  {
    file.writeShort(start_pc);
    file.writeShort(line_number);

  }
  /**
   * @return Corresponding source line
   */
  public final int getLineNumber() { return line_number; }

  /**
   * @return PC in code
   */
  public final int getStartPC() { return start_pc; }

  /**
   * @param line_number.
   */
  public final void setLineNumber(int line_number) {
    this.line_number = line_number;
  }

  /**
   * @param start_pc.
   */
  public final void setStartPC(int start_pc) {
    this.start_pc = start_pc;
  }

  /**
   * @return String representation
   */
  public final String toString() {
    return "LineNumber(" + start_pc + ", " + line_number + ")";
  }

  /**
   * @return deep copy of this object
   */
  public LineNumber copy() {
    try {
      return (LineNumber)clone();
    } catch(CloneNotSupportedException e) {}

    return null;
  }
}
