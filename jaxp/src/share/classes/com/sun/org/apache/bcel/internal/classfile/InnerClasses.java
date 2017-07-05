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
