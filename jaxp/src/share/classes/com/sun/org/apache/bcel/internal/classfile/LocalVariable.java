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
 * This class represents a local variable within a method. It contains its
 * scope, name, signature and index on the method's frame.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @see     LocalVariableTable
 */
public final class LocalVariable
  implements Constants, Cloneable, Node, Serializable
{
  private int start_pc;        // Range in which the variable is valid
  private int length;
  private int name_index;      // Index in constant pool of variable name
  private int signature_index; // Index of variable signature
  private int index;            /* Variable is `index'th local variable on
                                * this method's frame.
                                */

  private ConstantPool constant_pool;

  /**
   * Initialize from another object. Note that both objects use the same
   * references (shallow copy). Use copy() for a physical copy.
   */
  public LocalVariable(LocalVariable c) {
    this(c.getStartPC(), c.getLength(), c.getNameIndex(),
         c.getSignatureIndex(), c.getIndex(), c.getConstantPool());
  }

  /**
   * Construct object from file stream.
   * @param file Input stream
   * @throws IOException
   */
  LocalVariable(DataInputStream file, ConstantPool constant_pool)
       throws IOException
  {
    this(file.readUnsignedShort(), file.readUnsignedShort(),
         file.readUnsignedShort(), file.readUnsignedShort(),
         file.readUnsignedShort(), constant_pool);
  }

  /**
   * @param start_pc Range in which the variable
   * @param length ... is valid
   * @param name_index Index in constant pool of variable name
   * @param signature_index Index of variable's signature
   * @param index Variable is `index'th local variable on the method's frame
   * @param constant_pool Array of constants
   */
  public LocalVariable(int start_pc, int length, int name_index,
                       int signature_index, int index,
                       ConstantPool constant_pool)
  {
    this.start_pc        = start_pc;
    this.length          = length;
    this.name_index      = name_index;
    this.signature_index = signature_index;
    this.index           = index;
    this.constant_pool   = constant_pool;
  }

  /**
   * Called by objects that are traversing the nodes of the tree implicitely
   * defined by the contents of a Java class. I.e., the hierarchy of methods,
   * fields, attributes, etc. spawns a tree of objects.
   *
   * @param v Visitor object
   */
  public void accept(Visitor v) {
    v.visitLocalVariable(this);
  }

  /**
   * Dump local variable to file stream in binary format.
   *
   * @param file Output file stream
   * @throws IOException
   */
  public final void dump(DataOutputStream file) throws IOException
  {
    file.writeShort(start_pc);
    file.writeShort(length);
    file.writeShort(name_index);
    file.writeShort(signature_index);
    file.writeShort(index);
  }

  /**
   * @return Constant pool used by this object.
   */
  public final ConstantPool getConstantPool() { return constant_pool; }

  /**
   * @return Variable is valid within getStartPC() .. getStartPC()+getLength()
   */
  public final int getLength()         { return length; }

  /**
   * @return Variable name.
   */
  public final String getName() {
    ConstantUtf8  c;

    c = (ConstantUtf8)constant_pool.getConstant(name_index, CONSTANT_Utf8);
    return c.getBytes();
  }

  /**
   * @return Index in constant pool of variable name.
   */
  public final int getNameIndex()      { return name_index; }

  /**
   * @return Signature.
   */
  public final String getSignature() {
    ConstantUtf8  c;
    c = (ConstantUtf8)constant_pool.getConstant(signature_index,
                                                CONSTANT_Utf8);
    return c.getBytes();
  }

  /**
   * @return Index in constant pool of variable signature.
   */
  public final int getSignatureIndex() { return signature_index; }

  /**
   * @return index of register where variable is stored
   */
  public final int getIndex()           { return index; }

  /**
   * @return Start of range where he variable is valid
   */
  public final int getStartPC()        { return start_pc; }

  /**
   * @param constant_pool Constant pool to be used for this object.
   */
  public final void setConstantPool(ConstantPool constant_pool) {
    this.constant_pool = constant_pool;
  }

  /**
   * @param length.
   */
  public final void setLength(int length) {
    this.length = length;
  }

  /**
   * @param name_index.
   */
  public final void setNameIndex(int name_index) {
    this.name_index = name_index;
  }

  /**
   * @param signature_index.
   */
  public final void setSignatureIndex(int signature_index) {
    this.signature_index = signature_index;
  }

  /**
   * @param index.
   */
  public final void setIndex(int index) { this.index = index; }

  /**
   * @param start_pc Specify range where the local variable is valid.
   */
  public final void setStartPC(int start_pc) {
    this.start_pc = start_pc;
  }

  /**
   * @return string representation.
   */
  public final String toString() {
    String name = getName(), signature = Utility.signatureToString(getSignature());

    return "LocalVariable(start_pc = " + start_pc + ", length = " + length +
      ", index = " + index + ":" + signature + " " + name + ")";
  }

  /**
   * @return deep copy of this object
   */
  public LocalVariable copy() {
    try {
      return (LocalVariable)clone();
    } catch(CloneNotSupportedException e) {}

    return null;
  }
}
