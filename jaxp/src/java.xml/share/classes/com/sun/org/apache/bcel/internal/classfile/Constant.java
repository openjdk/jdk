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
 * Abstract superclass for classes to represent the different constant types
 * in the constant pool of a class file. The classes keep closely to
 * the JVM specification.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public abstract class Constant implements Cloneable, Node, Serializable {
  /* In fact this tag is redundant since we can distinguish different
   * `Constant' objects by their type, i.e., via `instanceof'. In some
   * places we will use the tag for switch()es anyway.
   *
   * First, we want match the specification as closely as possible. Second we
   * need the tag as an index to select the corresponding class name from the
   * `CONSTANT_NAMES' array.
   */
  protected byte tag;

  Constant(byte tag) { this.tag = tag; }

  /**
   * Called by objects that are traversing the nodes of the tree implicitely
   * defined by the contents of a Java class. I.e., the hierarchy of methods,
   * fields, attributes, etc. spawns a tree of objects.
   *
   * @param v Visitor object
   */
  public abstract void accept(Visitor v);

  public abstract void dump(DataOutputStream file) throws IOException;

  /**
   * @return Tag of constant, i.e., its type. No setTag() method to avoid
   * confusion.
   */
  public final byte getTag() { return tag; }

  /**
   * @return String representation.
   */
  public String toString() {
    return Constants.CONSTANT_NAMES[tag] + "[" + tag + "]";
  }

  /**
   * @return deep copy of this constant
   */
  public Constant copy() {
    try {
      return (Constant)super.clone();
    } catch(CloneNotSupportedException e) {}

    return null;
  }

  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  /**
   * Read one constant from the given file, the type depends on a tag byte.
   *
   * @param file Input stream
   * @return Constant object
   */
  static final Constant readConstant(DataInputStream file)
    throws IOException, ClassFormatException
  {
    byte b = file.readByte(); // Read tag byte

    switch(b) {
    case Constants.CONSTANT_Class:              return new ConstantClass(file);
    case Constants.CONSTANT_Fieldref:           return new ConstantFieldref(file);
    case Constants.CONSTANT_Methodref:          return new ConstantMethodref(file);
    case Constants.CONSTANT_InterfaceMethodref: return new
                                        ConstantInterfaceMethodref(file);
    case Constants.CONSTANT_String:             return new ConstantString(file);
    case Constants.CONSTANT_Integer:            return new ConstantInteger(file);
    case Constants.CONSTANT_Float:              return new ConstantFloat(file);
    case Constants.CONSTANT_Long:               return new ConstantLong(file);
    case Constants.CONSTANT_Double:             return new ConstantDouble(file);
    case Constants.CONSTANT_NameAndType:        return new ConstantNameAndType(file);
    case Constants.CONSTANT_Utf8:               return new ConstantUtf8(file);
    default:
      throw new ClassFormatException("Invalid byte tag in constant pool: " + b);
    }
  }
}
