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
 * This class is derived from the abstract
 * <A HREF="com.sun.org.apache.bcel.internal.classfile.Constant.html">Constant</A> class
 * and represents a reference to a Double object.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @see     Constant
 */
public final class ConstantDouble extends Constant implements ConstantObject {
  private double bytes;

  /**
   * @param bytes Data
   */
  public ConstantDouble(double bytes) {
    super(Constants.CONSTANT_Double);
    this.bytes = bytes;
  }

  /**
   * Initialize from another object.
   */
  public ConstantDouble(ConstantDouble c) {
    this(c.getBytes());
  }

  /**
   * Initialize instance from file data.
   *
   * @param file Input stream
   * @throws IOException
   */
  ConstantDouble(DataInputStream file) throws IOException
  {
    this(file.readDouble());
  }

  /**
   * Called by objects that are traversing the nodes of the tree implicitely
   * defined by the contents of a Java class. I.e., the hierarchy of methods,
   * fields, attributes, etc. spawns a tree of objects.
   *
   * @param v Visitor object
   */
  public void accept(Visitor v) {
    v.visitConstantDouble(this);
  }
  /**
   * Dump constant double to file stream in binary format.
   *
   * @param file Output file stream
   * @throws IOException
   */
  public final void dump(DataOutputStream file) throws IOException
  {
    file.writeByte(tag);
    file.writeDouble(bytes);
  }
  /**
   * @return data, i.e., 8 bytes.
   */
  public final double getBytes() { return bytes; }
  /**
   * @param bytes.
   */
  public final void setBytes(double bytes) {
    this.bytes = bytes;
  }
  /**
   * @return String representation.
   */
  public final String toString()
  {
    return super.toString() + "(bytes = " + bytes + ")";
  }

  /** @return Double object
   */
  public Object getConstantValue(ConstantPool cp) {
    return new Double(bytes);
  }
}
