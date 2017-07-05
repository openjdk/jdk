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
 * This class represents the constant pool, i.e., a table of constants, of
 * a parsed classfile. It may contain null references, due to the JVM
 * specification that skips an entry after an 8-byte constant (double,
 * long) entry.  Those interested in generating constant pools
 * programatically should see <a href="../generic/ConstantPoolGen.html">
 * ConstantPoolGen</a>.

 * @see     Constant
 * @see     com.sun.org.apache.bcel.internal.generic.ConstantPoolGen
 * @author <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public class ConstantPool implements Cloneable, Node, Serializable {
  private int        constant_pool_count;
  private Constant[] constant_pool;

  /**
   * @param constant_pool Array of constants
   */
  public ConstantPool(Constant[] constant_pool)
  {
    setConstantPool(constant_pool);
  }

  /**
   * Read constants from given file stream.
   *
   * @param file Input stream
   * @throws IOException
   * @throws ClassFormatException
   */
  ConstantPool(DataInputStream file) throws IOException, ClassFormatException
  {
    byte tag;

    constant_pool_count = file.readUnsignedShort();
    constant_pool       = new Constant[constant_pool_count];

    /* constant_pool[0] is unused by the compiler and may be used freely
     * by the implementation.
     */
    for(int i=1; i < constant_pool_count; i++) {
      constant_pool[i] = Constant.readConstant(file);

      /* Quote from the JVM specification:
       * "All eight byte constants take up two spots in the constant pool.
       * If this is the n'th byte in the constant pool, then the next item
       * will be numbered n+2"
       *
       * Thus we have to increment the index counter.
       */
      tag = constant_pool[i].getTag();
      if((tag == Constants.CONSTANT_Double) || (tag == Constants.CONSTANT_Long))
        i++;
    }
  }

  /**
   * Called by objects that are traversing the nodes of the tree implicitely
   * defined by the contents of a Java class. I.e., the hierarchy of methods,
   * fields, attributes, etc. spawns a tree of objects.
   *
   * @param v Visitor object
   */
  public void accept(Visitor v) {
    v.visitConstantPool(this);
  }

  /**
   * Resolve constant to a string representation.
   *
   * @param  constant Constant to be printed
   * @return String representation
   */
  public String constantToString(Constant c)
       throws ClassFormatException
  {
    String   str;
    int      i;
    byte     tag = c.getTag();

    switch(tag) {
    case Constants.CONSTANT_Class:
      i   = ((ConstantClass)c).getNameIndex();
      c   = getConstant(i, Constants.CONSTANT_Utf8);
      str = Utility.compactClassName(((ConstantUtf8)c).getBytes(), false);
      break;

    case Constants.CONSTANT_String:
      i   = ((ConstantString)c).getStringIndex();
      c   = getConstant(i, Constants.CONSTANT_Utf8);
      str = "\"" + escape(((ConstantUtf8)c).getBytes()) + "\"";
      break;

    case Constants.CONSTANT_Utf8:    str = ((ConstantUtf8)c).getBytes();         break;
    case Constants.CONSTANT_Double:  str = "" + ((ConstantDouble)c).getBytes();  break;
    case Constants.CONSTANT_Float:   str = "" + ((ConstantFloat)c).getBytes();   break;
    case Constants.CONSTANT_Long:    str = "" + ((ConstantLong)c).getBytes();    break;
    case Constants.CONSTANT_Integer: str = "" + ((ConstantInteger)c).getBytes(); break;

    case Constants.CONSTANT_NameAndType:
      str = (constantToString(((ConstantNameAndType)c).getNameIndex(),
                              Constants.CONSTANT_Utf8) + " " +
             constantToString(((ConstantNameAndType)c).getSignatureIndex(),
                              Constants.CONSTANT_Utf8));
      break;

    case Constants.CONSTANT_InterfaceMethodref: case Constants.CONSTANT_Methodref:
    case Constants.CONSTANT_Fieldref:
      str = (constantToString(((ConstantCP)c).getClassIndex(),
                              Constants.CONSTANT_Class) + "." +
             constantToString(((ConstantCP)c).getNameAndTypeIndex(),
                              Constants.CONSTANT_NameAndType));
      break;

    default: // Never reached
      throw new RuntimeException("Unknown constant type " + tag);
    }

    return str;
  }

  private static final String escape(String str) {
    int          len = str.length();
    StringBuffer buf = new StringBuffer(len + 5);
    char[]       ch  = str.toCharArray();

    for(int i=0; i < len; i++) {
      switch(ch[i]) {
      case '\n' : buf.append("\\n"); break;
      case '\r' : buf.append("\\r"); break;
      case '\t' : buf.append("\\t"); break;
      case '\b' : buf.append("\\b"); break;
      case '"'  : buf.append("\\\""); break;
      default: buf.append(ch[i]);
      }
    }

    return buf.toString();
  }


  /**
   * Retrieve constant at `index' from constant pool and resolve it to
   * a string representation.
   *
   * @param  index of constant in constant pool
   * @param  tag expected type
   * @return String representation
   */
  public String constantToString(int index, byte tag)
       throws ClassFormatException
  {
    Constant c = getConstant(index, tag);
    return constantToString(c);
  }

  /**
   * Dump constant pool to file stream in binary format.
   *
   * @param file Output file stream
   * @throws IOException
   */
  public void dump(DataOutputStream file) throws IOException
  {
    file.writeShort(constant_pool_count);

    for(int i=1; i < constant_pool_count; i++)
      if(constant_pool[i] != null)
        constant_pool[i].dump(file);
  }

  /**
   * Get constant from constant pool.
   *
   * @param  index Index in constant pool
   * @return Constant value
   * @see    Constant
   */
  public Constant getConstant(int index) {
    if (index >= constant_pool.length || index < 0)
      throw new ClassFormatException("Invalid constant pool reference: " +
                                 index + ". Constant pool size is: " +
                                 constant_pool.length);
    return constant_pool[index];
  }

  /**
   * Get constant from constant pool and check whether it has the
   * expected type.
   *
   * @param  index Index in constant pool
   * @param  tag Tag of expected constant, i.e., its type
   * @return Constant value
   * @see    Constant
   * @throws  ClassFormatException
   */
  public Constant getConstant(int index, byte tag)
       throws ClassFormatException
  {
    Constant c;

    c = getConstant(index);

    if(c == null)
      throw new ClassFormatException("Constant pool at index " + index + " is null.");

    if(c.getTag() == tag)
      return c;
    else
      throw new ClassFormatException("Expected class `" + Constants.CONSTANT_NAMES[tag] +
                                 "' at index " + index + " and got " + c);
  }

  /**
   * @return Array of constants.
   * @see    Constant
   */
  public Constant[] getConstantPool() { return constant_pool;  }
  /**
   * Get string from constant pool and bypass the indirection of
   * `ConstantClass' and `ConstantString' objects. I.e. these classes have
   * an index field that points to another entry of the constant pool of
   * type `ConstantUtf8' which contains the real data.
   *
   * @param  index Index in constant pool
   * @param  tag Tag of expected constant, either ConstantClass or ConstantString
   * @return Contents of string reference
   * @see    ConstantClass
   * @see    ConstantString
   * @throws  ClassFormatException
   */
  public String getConstantString(int index, byte tag)
       throws ClassFormatException
  {
    Constant c;
    int    i;

    c = getConstant(index, tag);

    /* This switch() is not that elegant, since the two classes have the
     * same contents, they just differ in the name of the index
     * field variable.
     * But we want to stick to the JVM naming conventions closely though
     * we could have solved these more elegantly by using the same
     * variable name or by subclassing.
     */
    switch(tag) {
    case Constants.CONSTANT_Class:  i = ((ConstantClass)c).getNameIndex();    break;
    case Constants.CONSTANT_String: i = ((ConstantString)c).getStringIndex(); break;
    default:
      throw new RuntimeException("getConstantString called with illegal tag " + tag);
    }

    // Finally get the string from the constant pool
    c = getConstant(i, Constants.CONSTANT_Utf8);
    return ((ConstantUtf8)c).getBytes();
  }
  /**
   * @return Length of constant pool.
   */
  public int getLength()
  {
    return constant_pool_count;
  }

  /**
   * @param constant Constant to set
   */
  public void setConstant(int index, Constant constant) {
    constant_pool[index] = constant;
  }

  /**
   * @param constant_pool
   */
  public void setConstantPool(Constant[] constant_pool) {
    this.constant_pool = constant_pool;
    constant_pool_count = (constant_pool == null)? 0 : constant_pool.length;
  }
  /**
   * @return String representation.
   */
  public String toString() {
    StringBuffer buf = new StringBuffer();

    for(int i=1; i < constant_pool_count; i++)
      buf.append(i + ")" + constant_pool[i] + "\n");

    return buf.toString();
  }

  /**
   * @return deep copy of this constant pool
   */
  public ConstantPool copy() {
    ConstantPool c = null;

    try {
      c = (ConstantPool)clone();
    } catch(CloneNotSupportedException e) {}

    c.constant_pool = new Constant[constant_pool_count];

    for(int i=1; i < constant_pool_count; i++) {
      if(constant_pool[i] != null)
        c.constant_pool[i] = constant_pool[i].copy();
    }

    return c;
  }
}
