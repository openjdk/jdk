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

package com.sun.org.apache.bcel.internal.classfile;


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
