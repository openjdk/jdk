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
 * This class represents a chunk of Java byte code contained in a
 * method. It is instantiated by the
 * <em>Attribute.readAttribute()</em> method. A <em>Code</em>
 * attribute contains informations about operand stack, local
 * variables, byte code and the exceptions handled within this
 * method.
 *
 * This attribute has attributes itself, namely <em>LineNumberTable</em> which
 * is used for debugging purposes and <em>LocalVariableTable</em> which
 * contains information about the local variables.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @see     Attribute
 * @see     CodeException
 * @see     LineNumberTable
 * @see LocalVariableTable
 */
public final class Code extends Attribute {
  private int             max_stack;   // Maximum size of stack used by this method
  private int             max_locals;  // Number of local variables
  private int             code_length; // Length of code in bytes
  private byte[]          code;        // Actual byte code

  private int             exception_table_length;
  private CodeException[] exception_table;  // Table of handled exceptions
  private int             attributes_count; // Attributes of code: LineNumber
  private Attribute[]     attributes;       // or LocalVariable

  /**
   * Initialize from another object. Note that both objects use the same
   * references (shallow copy). Use copy() for a physical copy.
   */
  public Code(Code c) {
    this(c.getNameIndex(), c.getLength(), c.getMaxStack(), c.getMaxLocals(),
         c.getCode(), c.getExceptionTable(), c.getAttributes(),
         c.getConstantPool());
  }

  /**
   * @param name_index Index pointing to the name <em>Code</em>
   * @param length Content length in bytes
   * @param file Input stream
   * @param constant_pool Array of constants
   */
  Code(int name_index, int length, DataInputStream file,
       ConstantPool constant_pool) throws IOException
  {
    // Initialize with some default values which will be overwritten later
    this(name_index, length,
         file.readUnsignedShort(), file.readUnsignedShort(),
         (byte[])null, (CodeException[])null, (Attribute[])null,
         constant_pool);

    code_length = file.readInt();
    code = new byte[code_length]; // Read byte code
    file.readFully(code);

    /* Read exception table that contains all regions where an exception
     * handler is active, i.e., a try { ... } catch() block.
     */
    exception_table_length = file.readUnsignedShort();
    exception_table        = new CodeException[exception_table_length];

    for(int i=0; i < exception_table_length; i++)
      exception_table[i] = new CodeException(file);

    /* Read all attributes, currently `LineNumberTable' and
     * `LocalVariableTable'
     */
    attributes_count = file.readUnsignedShort();
    attributes = new Attribute[attributes_count];
    for(int i=0; i < attributes_count; i++)
      attributes[i] = Attribute.readAttribute(file, constant_pool);

    /* Adjust length, because of setAttributes in this(), s.b.  length
     * is incorrect, because it didn't take the internal attributes
     * into account yet! Very subtle bug, fixed in 3.1.1.
     */
    this.length = length;
  }

  /**
   * @param name_index Index pointing to the name <em>Code</em>
   * @param length Content length in bytes
   * @param max_stack Maximum size of stack
   * @param max_locals Number of local variables
   * @param code Actual byte code
   * @param exception_table Table of handled exceptions
   * @param attributes Attributes of code: LineNumber or LocalVariable
   * @param constant_pool Array of constants
   */
  public Code(int name_index, int length,
              int max_stack,  int max_locals,
              byte[]          code,
              CodeException[] exception_table,
              Attribute[]     attributes,
              ConstantPool    constant_pool)
  {
    super(Constants.ATTR_CODE, name_index, length, constant_pool);

    this.max_stack         = max_stack;
    this.max_locals        = max_locals;

    setCode(code);
    setExceptionTable(exception_table);
    setAttributes(attributes); // Overwrites length!
  }

  /**
   * Called by objects that are traversing the nodes of the tree implicitely
   * defined by the contents of a Java class. I.e., the hierarchy of methods,
   * fields, attributes, etc. spawns a tree of objects.
   *
   * @param v Visitor object
   */
  public void accept(Visitor v) {
    v.visitCode(this);
  }

  /**
   * Dump code attribute to file stream in binary format.
   *
   * @param file Output file stream
   * @throws IOException
   */
  public final void dump(DataOutputStream file) throws IOException
  {
    super.dump(file);

    file.writeShort(max_stack);
    file.writeShort(max_locals);
    file.writeInt(code_length);
    file.write(code, 0, code_length);

    file.writeShort(exception_table_length);
    for(int i=0; i < exception_table_length; i++)
      exception_table[i].dump(file);

    file.writeShort(attributes_count);
    for(int i=0; i < attributes_count; i++)
      attributes[i].dump(file);
  }

  /**
   * @return Collection of code attributes.
   * @see Attribute
   */
  public final Attribute[] getAttributes()         { return attributes; }

  /**
   * @return LineNumberTable of Code, if it has one
   */
  public LineNumberTable getLineNumberTable() {
    for(int i=0; i < attributes_count; i++)
      if(attributes[i] instanceof LineNumberTable)
        return (LineNumberTable)attributes[i];

    return null;
  }

  /**
   * @return LocalVariableTable of Code, if it has one
   */
  public LocalVariableTable getLocalVariableTable() {
    for(int i=0; i < attributes_count; i++)
      if(attributes[i] instanceof LocalVariableTable)
        return (LocalVariableTable)attributes[i];

    return null;
  }

  /**
   * @return Actual byte code of the method.
   */
  public final byte[] getCode()      { return code; }

  /**
   * @return Table of handled exceptions.
   * @see CodeException
   */
  public final CodeException[] getExceptionTable() { return exception_table; }

  /**
   * @return Number of local variables.
   */
  public final int  getMaxLocals() { return max_locals; }

  /**
   * @return Maximum size of stack used by this method.
   */

  public final int  getMaxStack()  { return max_stack; }

  /**
   * @return the internal length of this code attribute (minus the first 6 bytes)
   * and excluding all its attributes
   */
  private final int getInternalLength() {
    return 2 /*max_stack*/ + 2 /*max_locals*/ + 4 /*code length*/
      + code_length /*byte-code*/
      + 2 /*exception-table length*/
      + 8 * exception_table_length /* exception table */
      + 2 /* attributes count */;
  }

  /**
   * @return the full size of this code attribute, minus its first 6 bytes,
   * including the size of all its contained attributes
   */
  private final int calculateLength() {
    int len = 0;

    for(int i=0; i < attributes_count; i++)
      len += attributes[i].length + 6 /*attribute header size*/;

    return len + getInternalLength();
  }

  /**
   * @param attributes.
   */
  public final void setAttributes(Attribute[] attributes) {
    this.attributes  = attributes;
    attributes_count = (attributes == null)? 0 : attributes.length;
    length = calculateLength(); // Adjust length
  }

  /**
   * @param code byte code
   */
  public final void setCode(byte[] code) {
    this.code   = code;
    code_length = (code == null)? 0 : code.length;
  }

  /**
   * @param exception_table exception table
   */
  public final void setExceptionTable(CodeException[] exception_table) {
    this.exception_table   = exception_table;
    exception_table_length = (exception_table == null)? 0 :
      exception_table.length;
  }

  /**
   * @param max_locals maximum number of local variables
   */
  public final void setMaxLocals(int max_locals) {
    this.max_locals = max_locals;
  }

  /**
   * @param max_stack maximum stack size
   */
  public final void setMaxStack(int max_stack) {
    this.max_stack = max_stack;
  }

  /**
   * @return String representation of code chunk.
   */
  public final String toString(boolean verbose) {
    StringBuffer buf;

    buf = new StringBuffer("Code(max_stack = " + max_stack +
                           ", max_locals = " + max_locals +
                           ", code_length = " + code_length + ")\n" +
                           Utility.codeToString(code, constant_pool, 0, -1, verbose));

    if(exception_table_length > 0) {
      buf.append("\nException handler(s) = \n" + "From\tTo\tHandler\tType\n");

      for(int i=0; i < exception_table_length; i++)
        buf.append(exception_table[i].toString(constant_pool, verbose) + "\n");
    }

    if(attributes_count > 0) {
      buf.append("\nAttribute(s) = \n");

      for(int i=0; i < attributes_count; i++)
        buf.append(attributes[i].toString() + "\n");
    }

    return buf.toString();
  }

  /**
   * @return String representation of code chunk.
   */
  public final String toString() {
    return toString(true);
  }

  /**
   * @return deep copy of this attribute
   */
  public Attribute copy(ConstantPool constant_pool) {
    Code c = (Code)clone();
    c.code          = (byte[])code.clone();
    c.constant_pool = constant_pool;

    c.exception_table = new CodeException[exception_table_length];
    for(int i=0; i < exception_table_length; i++)
      c.exception_table[i] = exception_table[i].copy();

    c.attributes = new Attribute[attributes_count];
    for(int i=0; i < attributes_count; i++)
      c.attributes[i] = attributes[i].copy(constant_pool);

    return c;
  }
}
