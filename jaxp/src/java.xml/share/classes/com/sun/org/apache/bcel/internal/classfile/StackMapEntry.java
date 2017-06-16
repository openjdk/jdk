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
