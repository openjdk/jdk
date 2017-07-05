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
