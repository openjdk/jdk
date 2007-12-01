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
 * This class represents a stack map entry recording the types of
 * local variables and the the of stack items at a given byte code offset.
 * See CLDC specification 5.3.1.2
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @see     StackMap
 * @see     StackMapType
 */
public final class StackMapEntry implements Cloneable {
  private int            byte_code_offset;
  private int            number_of_locals;
  private StackMapType[] types_of_locals;
  private int            number_of_stack_items;
  private StackMapType[] types_of_stack_items;
  private ConstantPool   constant_pool;

  /**
   * Construct object from file stream.
   * @param file Input stream
   * @throws IOException
   */
  StackMapEntry(DataInputStream file, ConstantPool constant_pool) throws IOException
  {
    this(file.readShort(), file.readShort(), null, -1, null, constant_pool);

    types_of_locals = new StackMapType[number_of_locals];
    for(int i=0; i < number_of_locals; i++)
      types_of_locals[i] = new StackMapType(file, constant_pool);

    number_of_stack_items = file.readShort();
    types_of_stack_items = new StackMapType[number_of_stack_items];
    for(int i=0; i < number_of_stack_items; i++)
      types_of_stack_items[i] = new StackMapType(file, constant_pool);
  }

  public StackMapEntry(int byte_code_offset, int number_of_locals,
                       StackMapType[] types_of_locals,
                       int number_of_stack_items,
                       StackMapType[] types_of_stack_items,
                       ConstantPool constant_pool) {
    this.byte_code_offset = byte_code_offset;
    this.number_of_locals = number_of_locals;
    this.types_of_locals = types_of_locals;
    this.number_of_stack_items = number_of_stack_items;
    this.types_of_stack_items = types_of_stack_items;
    this.constant_pool = constant_pool;
  }

  /**
   * Dump stack map entry
   *
   * @param file Output file stream
   * @throws IOException
   */
  public final void dump(DataOutputStream file) throws IOException
  {
    file.writeShort(byte_code_offset);

    file.writeShort(number_of_locals);
    for(int i=0; i < number_of_locals; i++)
      types_of_locals[i].dump(file);

    file.writeShort(number_of_stack_items);
    for(int i=0; i < number_of_stack_items; i++)
      types_of_stack_items[i].dump(file);
  }

  /**
   * @return String representation.
   */
  public final String toString() {
    StringBuffer buf = new StringBuffer("(offset=" + byte_code_offset);

    if(number_of_locals > 0) {
      buf.append(", locals={");

      for(int i=0; i < number_of_locals; i++) {
        buf.append(types_of_locals[i]);
        if(i < number_of_locals - 1)
          buf.append(", ");
      }

      buf.append("}");
    }

    if(number_of_stack_items > 0) {
      buf.append(", stack items={");

      for(int i=0; i < number_of_stack_items; i++) {
        buf.append(types_of_stack_items[i]);
        if(i < number_of_stack_items - 1)
          buf.append(", ");
      }

      buf.append("}");
    }

    buf.append(")");

    return buf.toString();
  }


  public void           setByteCodeOffset(int b)               { byte_code_offset = b; }
  public int            getByteCodeOffset()                    { return byte_code_offset; }
  public void           setNumberOfLocals(int n)               { number_of_locals = n; }
  public int            getNumberOfLocals()                    { return number_of_locals; }
  public void           setTypesOfLocals(StackMapType[] t)     { types_of_locals = t; }
  public StackMapType[] getTypesOfLocals()                     { return types_of_locals; }
  public void           setNumberOfStackItems(int n)           { number_of_stack_items = n; }
  public int            getNumberOfStackItems()                { return number_of_stack_items; }
  public void           setTypesOfStackItems(StackMapType[] t) { types_of_stack_items = t; }
  public StackMapType[] getTypesOfStackItems()                 { return types_of_stack_items; }

  /**
   * @return deep copy of this object
   */
  public StackMapEntry copy() {
    try {
      return (StackMapEntry)clone();
    } catch(CloneNotSupportedException e) {}

    return null;
  }

  /**
   * Called by objects that are traversing the nodes of the tree implicitely
   * defined by the contents of a Java class. I.e., the hierarchy of methods,
   * fields, attributes, etc. spawns a tree of objects.
   *
   * @param v Visitor object
   */
  public void accept(Visitor v) {
    v.visitStackMapEntry(this);
  }

  /**
   * @return Constant pool used by this object.
   */
  public final ConstantPool getConstantPool() { return constant_pool; }

  /**
   * @param constant_pool Constant pool to be used for this object.
   */
  public final void setConstantPool(ConstantPool constant_pool) {
    this.constant_pool = constant_pool;
  }
}
