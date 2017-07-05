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
import com.sun.org.apache.bcel.internal.generic.Type;
import java.io.*;

/**
 * This class represents the field info structure, i.e., the representation
 * for a variable in the class. See JVM specification for details.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public final class Field extends FieldOrMethod {
  /**
   * Initialize from another object. Note that both objects use the same
   * references (shallow copy). Use clone() for a physical copy.
   */
  public Field(Field c) {
    super(c);
  }

  /**
   * Construct object from file stream.
   * @param file Input stream
   */
  Field(DataInputStream file, ConstantPool constant_pool)
       throws IOException, ClassFormatException
  {
    super(file, constant_pool);
  }

  /**
   * @param access_flags Access rights of field
   * @param name_index Points to field name in constant pool
   * @param signature_index Points to encoded signature
   * @param attributes Collection of attributes
   * @param constant_pool Array of constants
   */
  public Field(int access_flags, int name_index, int signature_index,
               Attribute[] attributes, ConstantPool constant_pool)
  {
    super(access_flags, name_index, signature_index, attributes, constant_pool);
  }

  /**
   * Called by objects that are traversing the nodes of the tree implicitely
   * defined by the contents of a Java class. I.e., the hierarchy of methods,
   * fields, attributes, etc. spawns a tree of objects.
   *
   * @param v Visitor object
   */
  public void accept(Visitor v) {
    v.visitField(this);
  }

  /**
   * @return constant value associated with this field (may be null)
   */
  public final ConstantValue getConstantValue() {
    for(int i=0; i < attributes_count; i++)
      if(attributes[i].getTag() == Constants.ATTR_CONSTANT_VALUE)
        return (ConstantValue)attributes[i];

    return null;
  }

  /**
   * Return string representation close to declaration format,
   * `public static final short MAX = 100', e.g..
   *
   * @return String representation of field, including the signature.
   */
  public final String toString() {
    String name, signature, access; // Short cuts to constant pool

    // Get names from constant pool
    access    = Utility.accessToString(access_flags);
    access    = access.equals("")? "" : (access + " ");
    signature = Utility.signatureToString(getSignature());
    name      = getName();

    StringBuffer  buf = new StringBuffer(access + signature + " " + name);
    ConstantValue cv  = getConstantValue();

    if(cv != null)
      buf.append(" = " + cv);

    for(int i=0; i < attributes_count; i++) {
      Attribute a = attributes[i];

      if(!(a instanceof ConstantValue))
        buf.append(" [" + a.toString() + "]");
    }

    return buf.toString();
  }

  /**
   * @return deep copy of this field
   */
  public final Field copy(ConstantPool constant_pool) {
    return (Field)copy_(constant_pool);
  }

  /**
   * @return type of field
   */
  public Type getType() {
    return Type.getReturnType(getSignature());
  }
}
