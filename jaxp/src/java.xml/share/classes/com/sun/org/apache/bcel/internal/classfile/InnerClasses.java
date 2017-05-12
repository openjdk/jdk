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
 * This class is derived from <em>Attribute</em> and denotes that this class
 * is an Inner class of another.
 * to the source file of this class.
 * It is instantiated from the <em>Attribute.readAttribute()</em> method.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @see     Attribute
 */
public final class InnerClasses extends Attribute {
  private InnerClass[] inner_classes;
  private int          number_of_classes;

  /**
   * Initialize from another object. Note that both objects use the same
   * references (shallow copy). Use clone() for a physical copy.
   */
  public InnerClasses(InnerClasses c) {
    this(c.getNameIndex(), c.getLength(), c.getInnerClasses(),
         c.getConstantPool());
  }

  /**
   * @param name_index Index in constant pool to CONSTANT_Utf8
   * @param length Content length in bytes
   * @param inner_classes array of inner classes attributes
   * @param constant_pool Array of constants
   * @param sourcefile_index Index in constant pool to CONSTANT_Utf8
   */
  public InnerClasses(int name_index, int length,
                      InnerClass[] inner_classes,
                      ConstantPool constant_pool)
  {
    super(Constants.ATTR_INNER_CLASSES, name_index, length, constant_pool);
    setInnerClasses(inner_classes);
  }

  /**
   * Construct object from file stream.
   *
   * @param name_index Index in constant pool to CONSTANT_Utf8
   * @param length Content length in bytes
   * @param file Input stream
   * @param constant_pool Array of constants
   * @throws IOException
   */
  InnerClasses(int name_index, int length, DataInputStream file,
               ConstantPool constant_pool) throws IOException
  {
    this(name_index, length, (InnerClass[])null, constant_pool);

    number_of_classes = file.readUnsignedShort();
    inner_classes = new InnerClass[number_of_classes];

    for(int i=0; i < number_of_classes; i++)
      inner_classes[i] = new InnerClass(file);
  }
  /**
   * Called by objects that are traversing the nodes of the tree implicitely
   * defined by the contents of a Java class. I.e., the hierarchy of methods,
   * fields, attributes, etc. spawns a tree of objects.
   *
   * @param v Visitor object
   */
  public void accept(Visitor v) {
    v.visitInnerClasses(this);
  }
  /**
   * Dump source file attribute to file stream in binary format.
   *
   * @param file Output file stream
   * @throws IOException
   */
  public final void dump(DataOutputStream file) throws IOException
  {
    super.dump(file);
    file.writeShort(number_of_classes);

    for(int i=0; i < number_of_classes; i++)
      inner_classes[i].dump(file);
  }

  /**
   * @return array of inner class "records"
   */
  public final InnerClass[] getInnerClasses() { return inner_classes; }

  /**
   * @param inner_classes.
   */
  public final void setInnerClasses(InnerClass[] inner_classes) {
    this.inner_classes = inner_classes;
    number_of_classes = (inner_classes == null)? 0 : inner_classes.length;
  }

  /**
   * @return String representation.
   */
  public final String toString() {
    StringBuffer buf = new StringBuffer();

    for(int i=0; i < number_of_classes; i++)
      buf.append(inner_classes[i].toString(constant_pool) + "\n");

    return buf.toString();
  }

  /**
   * @return deep copy of this attribute
   */
  public Attribute copy(ConstantPool constant_pool) {
    InnerClasses c = (InnerClasses)clone();

    c.inner_classes = new InnerClass[number_of_classes];
    for(int i=0; i < number_of_classes; i++)
      c.inner_classes[i] = inner_classes[i].copy();

    c.constant_pool = constant_pool;
    return c;
  }
}
