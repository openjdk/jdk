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
 * This class is derived from <em>Attribute</em> and represents a reference
 * to a <a href="http://www.inf.fu-berlin.de/~bokowski/pmgjava/index.html">PMG</a>
 * attribute.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @see     Attribute
 */
public final class PMGClass extends Attribute {
  private int pmg_class_index, pmg_index;

  /**
   * Initialize from another object. Note that both objects use the same
   * references (shallow copy). Use clone() for a physical copy.
   */
  public PMGClass(PMGClass c) {
    this(c.getNameIndex(), c.getLength(), c.getPMGIndex(), c.getPMGClassIndex(),
         c.getConstantPool());
  }

  /**
   * Construct object from file stream.
   * @param name_index Index in constant pool to CONSTANT_Utf8
   * @param length Content length in bytes
   * @param file Input stream
   * @param constant_pool Array of constants
   * @throws IOException
   */
  PMGClass(int name_index, int length, DataInputStream file,
           ConstantPool constant_pool) throws IOException
  {
    this(name_index, length, file.readUnsignedShort(), file.readUnsignedShort(),
         constant_pool);
  }

  /**
   * @param name_index Index in constant pool to CONSTANT_Utf8
   * @param length Content length in bytes
   * @param constant_pool Array of constants
   * @param PMGClass_index Index in constant pool to CONSTANT_Utf8
   */
  public PMGClass(int name_index, int length, int pmg_index, int pmg_class_index,
                  ConstantPool constant_pool)
  {
    super(Constants.ATTR_PMG, name_index, length, constant_pool);
    this.pmg_index       = pmg_index;
    this.pmg_class_index = pmg_class_index;
  }

  /**
   * Called by objects that are traversing the nodes of the tree implicitely
   * defined by the contents of a Java class. I.e., the hierarchy of methods,
   * fields, attributes, etc. spawns a tree of objects.
   *
   * @param v Visitor object
   */
   public void accept(Visitor v) {
     System.err.println("Visiting non-standard PMGClass object");
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
    file.writeShort(pmg_index);
    file.writeShort(pmg_class_index);
  }

  /**
   * @return Index in constant pool of source file name.
   */
  public final int getPMGClassIndex() { return pmg_class_index; }

  /**
   * @param PMGClass_index.
   */
  public final void setPMGClassIndex(int pmg_class_index) {
    this.pmg_class_index = pmg_class_index;
  }

  /**
   * @return Index in constant pool of source file name.
   */
  public final int getPMGIndex() { return pmg_index; }

  /**
   * @param PMGClass_index.
   */
  public final void setPMGIndex(int pmg_index) {
    this.pmg_index = pmg_index;
  }

  /**
   * @return PMG name.
   */
  public final String getPMGName() {
    ConstantUtf8 c = (ConstantUtf8)constant_pool.getConstant(pmg_index,
                                                             Constants.CONSTANT_Utf8);
    return c.getBytes();
  }

  /**
   * @return PMG class name.
   */
  public final String getPMGClassName() {
    ConstantUtf8 c = (ConstantUtf8)constant_pool.getConstant(pmg_class_index,
                                                             Constants.CONSTANT_Utf8);
    return c.getBytes();
  }

  /**
   * @return String representation
   */
  public final String toString() {
    return "PMGClass(" + getPMGName() + ", " + getPMGClassName() + ")";
  }

  /**
   * @return deep copy of this attribute
   */
  public Attribute copy(ConstantPool constant_pool) {
    return (PMGClass)clone();
  }
}
