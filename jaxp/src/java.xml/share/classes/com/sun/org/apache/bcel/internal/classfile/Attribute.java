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

import com.sun.org.apache.bcel.internal.Constants;
import java.io.*;
import java.util.HashMap;

/**
 * Abstract super class for <em>Attribute</em> objects. Currently the
 * <em>ConstantValue</em>, <em>SourceFile</em>, <em>Code</em>,
 * <em>Exceptiontable</em>, <em>LineNumberTable</em>,
 * <em>LocalVariableTable</em>, <em>InnerClasses</em> and
 * <em>Synthetic</em> attributes are supported. The
 * <em>Unknown</em> attribute stands for non-standard-attributes.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @see     ConstantValue
 * @see     SourceFile
 * @see     Code
 * @see     Unknown
 * @see     ExceptionTable
 * @see     LineNumberTable
 * @see     LocalVariableTable
 * @see     InnerClasses
 * @see     Synthetic
 * @see     Deprecated
 * @see     Signature
*/
public abstract class Attribute implements Cloneable, Node, Serializable {
  protected int          name_index; // Points to attribute name in constant pool
  protected int          length;     // Content length of attribute field
  protected byte         tag;        // Tag to distiguish subclasses
  protected ConstantPool constant_pool;

  protected Attribute(byte tag, int name_index, int length,
                      ConstantPool constant_pool) {
    this.tag           = tag;
    this.name_index    = name_index;
    this.length        = length;
    this.constant_pool = constant_pool;
  }

  /**
   * Called by objects that are traversing the nodes of the tree implicitely
   * defined by the contents of a Java class. I.e., the hierarchy of methods,
   * fields, attributes, etc. spawns a tree of objects.
   *
   * @param v Visitor object
   */
  public abstract void accept(Visitor v);

  /**
   * Dump attribute to file stream in binary format.
   *
   * @param file Output file stream
   * @throws IOException
   */
  public void dump(DataOutputStream file) throws IOException
  {
    file.writeShort(name_index);
    file.writeInt(length);
  }

  private static HashMap readers = new HashMap();

  /** Add an Attribute reader capable of parsing (user-defined) attributes
   * named "name". You should not add readers for the standard attributes
   * such as "LineNumberTable", because those are handled internally.
   *
   * @param name the name of the attribute as stored in the class file
   * @param r the reader object
   */
  public static void addAttributeReader(String name, AttributeReader r) {
    readers.put(name, r);
  }

  /** Remove attribute reader
   *
   * @param name the name of the attribute as stored in the class file
   */
  public static void removeAttributeReader(String name) {
    readers.remove(name);
  }

  /* Class method reads one attribute from the input data stream.
   * This method must not be accessible from the outside.  It is
   * called by the Field and Method constructor methods.
   *
   * @see    Field
   * @see    Method
   * @param  file Input stream
   * @param  constant_pool Array of constants
   * @return Attribute
   * @throws  IOException
   * @throws  ClassFormatException
   */
  public static final Attribute readAttribute(DataInputStream file,
                                              ConstantPool constant_pool)
    throws IOException, ClassFormatException
  {
    ConstantUtf8 c;
    String       name;
    int          name_index;
    int          length;
    byte         tag = Constants.ATTR_UNKNOWN; // Unknown attribute

    // Get class name from constant pool via `name_index' indirection
    name_index = (int)file.readUnsignedShort();
    c          = (ConstantUtf8)constant_pool.getConstant(name_index,
                                                         Constants.CONSTANT_Utf8);
    name       = c.getBytes();

    // Length of data in bytes
    length = file.readInt();

    // Compare strings to find known attribute
    for(byte i=0; i < Constants.KNOWN_ATTRIBUTES; i++) {
      if(name.equals(Constants.ATTRIBUTE_NAMES[i])) {
        tag = i; // found!
        break;
      }
    }

    // Call proper constructor, depending on `tag'
    switch(tag) {
    case Constants.ATTR_UNKNOWN:
      AttributeReader r = (AttributeReader)readers.get(name);

      if(r != null)
        return r.createAttribute(name_index, length, file, constant_pool);
      else
        return new Unknown(name_index, length, file, constant_pool);

    case Constants.ATTR_CONSTANT_VALUE:
      return new ConstantValue(name_index, length, file, constant_pool);

    case Constants.ATTR_SOURCE_FILE:
      return new SourceFile(name_index, length, file, constant_pool);

    case Constants.ATTR_CODE:
      return new Code(name_index, length, file, constant_pool);

    case Constants.ATTR_EXCEPTIONS:
      return new ExceptionTable(name_index, length, file, constant_pool);

    case Constants.ATTR_LINE_NUMBER_TABLE:
      return new LineNumberTable(name_index, length, file, constant_pool);

    case Constants.ATTR_LOCAL_VARIABLE_TABLE:
      return new LocalVariableTable(name_index, length, file, constant_pool);

    case Constants.ATTR_LOCAL_VARIABLE_TYPE_TABLE:
      return new LocalVariableTypeTable(name_index, length, file, constant_pool);

    case Constants.ATTR_INNER_CLASSES:
      return new InnerClasses(name_index, length, file, constant_pool);

    case Constants.ATTR_SYNTHETIC:
      return new Synthetic(name_index, length, file, constant_pool);

    case Constants.ATTR_DEPRECATED:
      return new Deprecated(name_index, length, file, constant_pool);

    case Constants.ATTR_PMG:
      return new PMGClass(name_index, length, file, constant_pool);

    case Constants.ATTR_SIGNATURE:
      return new Signature(name_index, length, file, constant_pool);

    case Constants.ATTR_STACK_MAP:
      return new StackMap(name_index, length, file, constant_pool);

    default: // Never reached
      throw new IllegalStateException("Ooops! default case reached.");
    }
  }

  /**
   * @return Length of attribute field in bytes.
   */
  public final int   getLength()    { return length; }

  /**
   * @param Attribute length in bytes.
   */
  public final void setLength(int length) {
    this.length = length;
  }

  /**
   * @param name_index of attribute.
   */
  public final void setNameIndex(int name_index) {
    this.name_index = name_index;
  }

  /**
   * @return Name index in constant pool of attribute name.
   */
  public final int getNameIndex() { return name_index; }

  /**
   * @return Tag of attribute, i.e., its type. Value may not be altered, thus
   * there is no setTag() method.
   */
  public final byte  getTag()       { return tag; }

  /**
   * @return Constant pool used by this object.
   * @see ConstantPool
   */
  public final ConstantPool getConstantPool() { return constant_pool; }

  /**
   * @param constant_pool Constant pool to be used for this object.
   * @see ConstantPool
   */
  public final void setConstantPool(ConstantPool constant_pool) {
    this.constant_pool = constant_pool;
  }

  /**
   * Use copy() if you want to have a deep copy(), i.e., with all references
   * copied correctly.
   *
   * @return shallow copy of this attribute
   */
  public Object clone() {
    Object o = null;

    try {
      o = super.clone();
    } catch(CloneNotSupportedException e) {
      e.printStackTrace(); // Never occurs
    }

    return o;
  }

  /**
   * @return deep copy of this attribute
   */
  public abstract Attribute copy(ConstantPool constant_pool);

  /**
   * @return attribute name.
   */
  public String toString() {
    return Constants.ATTRIBUTE_NAMES[tag];
  }
}
