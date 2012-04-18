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
 * This class is derived from <em>Attribute</em> and represents a constant
 * value, i.e., a default value for initializing a class field.
 * This class is instantiated by the <em>Attribute.readAttribute()</em> method.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @see     Attribute
 */
public final class ConstantValue extends Attribute {
  private int constantvalue_index;

  /**
   * Initialize from another object. Note that both objects use the same
   * references (shallow copy). Use clone() for a physical copy.
   */
  public ConstantValue(ConstantValue c) {
    this(c.getNameIndex(), c.getLength(), c.getConstantValueIndex(),
         c.getConstantPool());
  }

  /**
   * Construct object from file stream.
   * @param name_index Name index in constant pool
   * @param length Content length in bytes
   * @param file Input stream
   * @param constant_pool Array of constants
   * @throw IOException
   */
  ConstantValue(int name_index, int length, DataInputStream file,
                ConstantPool constant_pool) throws IOException
  {
    this(name_index, length, (int)file.readUnsignedShort(), constant_pool);
  }

  /**
   * @param name_index Name index in constant pool
   * @param length Content length in bytes
   * @param constantvalue_index Index in constant pool
   * @param constant_pool Array of constants
   */
  public ConstantValue(int name_index, int length,
                       int constantvalue_index,
                       ConstantPool constant_pool)
  {
    super(Constants.ATTR_CONSTANT_VALUE, name_index, length, constant_pool);
    this.constantvalue_index = constantvalue_index;
  }

  /**
   * Called by objects that are traversing the nodes of the tree implicitely
   * defined by the contents of a Java class. I.e., the hierarchy of methods,
   * fields, attributes, etc. spawns a tree of objects.
   *
   * @param v Visitor object
   */
  public void accept(Visitor v) {
    v.visitConstantValue(this);
  }
  /**
   * Dump constant value attribute to file stream on binary format.
   *
   * @param file Output file stream
   * @throws IOException
   */
  public final void dump(DataOutputStream file) throws IOException
  {
    super.dump(file);
    file.writeShort(constantvalue_index);
  }
  /**
   * @return Index in constant pool of constant value.
   */
  public final int getConstantValueIndex() { return constantvalue_index; }

  /**
   * @param constantvalue_index.
   */
  public final void setConstantValueIndex(int constantvalue_index) {
    this.constantvalue_index = constantvalue_index;
  }

  /**
   * @return String representation of constant value.
   */
  public final String toString() {
    Constant c = constant_pool.getConstant(constantvalue_index);

    String   buf;
    int    i;

    // Print constant to string depending on its type
    switch(c.getTag()) {
    case Constants.CONSTANT_Long:    buf = "" + ((ConstantLong)c).getBytes();    break;
    case Constants.CONSTANT_Float:   buf = "" + ((ConstantFloat)c).getBytes();   break;
    case Constants.CONSTANT_Double:  buf = "" + ((ConstantDouble)c).getBytes();  break;
    case Constants.CONSTANT_Integer: buf = "" + ((ConstantInteger)c).getBytes(); break;
    case Constants.CONSTANT_String:
      i   = ((ConstantString)c).getStringIndex();
      c   = constant_pool.getConstant(i, Constants.CONSTANT_Utf8);
      buf = "\"" + Utility.convertString(((ConstantUtf8)c).getBytes()) + "\"";
      break;

    default:
      throw new IllegalStateException("Type of ConstValue invalid: " + c);
    }

    return buf;
  }

  /**
   * @return deep copy of this attribute
   */
  public Attribute copy(ConstantPool constant_pool) {
    ConstantValue c = (ConstantValue)clone();
    c.constant_pool = constant_pool;
    return c;
  }
}
