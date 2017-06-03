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
 * and represents a reference to a Utf8 encoded string.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @see     Constant
 */
public final class ConstantUtf8 extends Constant {
  private String bytes;

  /**
   * Initialize from another object.
   */
  public ConstantUtf8(ConstantUtf8 c) {
    this(c.getBytes());
  }

  /**
   * Initialize instance from file data.
   *
   * @param file Input stream
   * @throws IOException
   */
  ConstantUtf8(DataInputStream file) throws IOException
  {
    super(Constants.CONSTANT_Utf8);

    bytes = file.readUTF();
  }

  /**
   * @param bytes Data
   */
  public ConstantUtf8(String bytes)
  {
    super(Constants.CONSTANT_Utf8);

    if(bytes == null)
      throw new IllegalArgumentException("bytes must not be null!");

    this.bytes  = bytes;
  }

  /**
   * Called by objects that are traversing the nodes of the tree implicitely
   * defined by the contents of a Java class. I.e., the hierarchy of methods,
   * fields, attributes, etc. spawns a tree of objects.
   *
   * @param v Visitor object
   */
  public void accept(Visitor v) {
    v.visitConstantUtf8(this);
  }

  /**
   * Dump String in Utf8 format to file stream.
   *
   * @param file Output file stream
   * @throws IOException
   */
  public final void dump(DataOutputStream file) throws IOException
  {
    file.writeByte(tag);
    file.writeUTF(bytes);
  }

  /**
   * @return Data converted to string.
   */
  public final String getBytes() { return bytes; }

  /**
   * @param bytes.
   */
  public final void setBytes(String bytes) {
    this.bytes = bytes;
  }

  /**
   * @return String representation
   */
  public final String toString()
  {
    return super.toString() + "(\"" + Utility.replace(bytes, "\n", "\\n") + "\")";
  }
}
