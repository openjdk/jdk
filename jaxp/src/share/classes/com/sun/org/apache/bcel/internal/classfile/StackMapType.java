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
 * This class represents the type of a local variable or item on stack
 * used in the StackMap entries.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @see     StackMapEntry
 * @see     StackMap
 * @see     Constants
 */
public final class StackMapType implements Cloneable {
  private byte         type;
  private int          index = -1; // Index to CONSTANT_Class or offset
  private ConstantPool constant_pool;

  /**
   * Construct object from file stream.
   * @param file Input stream
   * @throws IOException
   */
  StackMapType(DataInputStream file, ConstantPool constant_pool) throws IOException
  {
    this(file.readByte(), -1, constant_pool);

    if(hasIndex())
      setIndex(file.readShort());

    setConstantPool(constant_pool);
  }

  /**
   * @param type type tag as defined in the Constants interface
   * @param index index to constant pool, or byte code offset
   */
  public StackMapType(byte type, int index, ConstantPool constant_pool) {
    setType(type);
    setIndex(index);
    setConstantPool(constant_pool);
  }

  public void setType(byte t) {
    if((t < Constants.ITEM_Bogus) || (t > Constants.ITEM_NewObject))
      throw new RuntimeException("Illegal type for StackMapType: " + t);
    type = t;
  }

  public byte getType()       { return type; }
  public void setIndex(int t) { index = t; }

  /** @return index to constant pool if type == ITEM_Object, or offset
   * in byte code, if type == ITEM_NewObject, and -1 otherwise
   */
  public int  getIndex()      { return index; }

  /**
   * Dump type entries to file.
   *
   * @param file Output file stream
   * @throws IOException
   */
  public final void dump(DataOutputStream file) throws IOException
  {
    file.writeByte(type);
    if(hasIndex())
      file.writeShort(getIndex());
  }

  /** @return true, if type is either ITEM_Object or ITEM_NewObject
   */
  public final boolean hasIndex() {
    return ((type == Constants.ITEM_Object) ||
            (type == Constants.ITEM_NewObject));
  }

  private String printIndex() {
    if(type == Constants.ITEM_Object)
      return ", class=" + constant_pool.constantToString(index, Constants.CONSTANT_Class);
    else if(type == Constants.ITEM_NewObject)
      return ", offset=" + index;
    else
      return "";
  }

  /**
   * @return String representation
   */
  public final String toString() {
    return "(type=" + Constants.ITEM_NAMES[type] + printIndex() + ")";
  }

  /**
   * @return deep copy of this object
   */
  public StackMapType copy() {
    try {
      return (StackMapType)clone();
    } catch(CloneNotSupportedException e) {}

    return null;
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
