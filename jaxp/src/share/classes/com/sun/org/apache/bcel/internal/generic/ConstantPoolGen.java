/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
package com.sun.org.apache.bcel.internal.generic;

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
import com.sun.org.apache.bcel.internal.classfile.*;
import java.util.HashMap;

/**
 * This class is used to build up a constant pool. The user adds
 * constants via `addXXX' methods, `addString', `addClass',
 * etc.. These methods return an index into the constant
 * pool. Finally, `getFinalConstantPool()' returns the constant pool
 * built up. Intermediate versions of the constant pool can be
 * obtained with `getConstantPool()'. A constant pool has capacity for
 * Constants.MAX_SHORT entries. Note that the first (0) is used by the
 * JVM and that Double and Long constants need two slots.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @see Constant
 */
public class ConstantPoolGen implements java.io.Serializable {
  protected int        size      = 1024; // Inital size, sufficient in most cases
  protected Constant[] constants = new Constant[size];
  protected int        index     = 1; // First entry (0) used by JVM

  private static final String METHODREF_DELIM  = ":";
  private static final String IMETHODREF_DELIM = "#";
  private static final String FIELDREF_DELIM   = "&";
  private static final String NAT_DELIM        = "%";

  private static class Index implements java.io.Serializable {
    int index;
    Index(int i) { index = i; }
  }

  /**
   * Initialize with given array of constants.
   *
   * @param c array of given constants, new ones will be appended
   */
  public ConstantPoolGen(Constant[] cs) {
    if(cs.length > size) {
      size      = cs.length;
      constants = new Constant[size];
    }

    System.arraycopy(cs, 0, constants, 0, cs.length);

    if(cs.length > 0)
      index = cs.length;

    for(int i=1; i < index; i++) {
      Constant c = constants[i];

      if(c instanceof ConstantString) {
        ConstantString s  = (ConstantString)c;
        ConstantUtf8   u8 = (ConstantUtf8)constants[s.getStringIndex()];

        string_table.put(u8.getBytes(), new Index(i));
      } else if(c instanceof ConstantClass) {
        ConstantClass s  = (ConstantClass)c;
        ConstantUtf8  u8 = (ConstantUtf8)constants[s.getNameIndex()];

        class_table.put(u8.getBytes(), new Index(i));
      } else if(c instanceof ConstantNameAndType) {
        ConstantNameAndType n    = (ConstantNameAndType)c;
        ConstantUtf8        u8   = (ConstantUtf8)constants[n.getNameIndex()];
        ConstantUtf8        u8_2 = (ConstantUtf8)constants[n.getSignatureIndex()];

        n_a_t_table.put(u8.getBytes() + NAT_DELIM + u8_2.getBytes(), new Index(i));
       } else if(c instanceof ConstantUtf8) {
         ConstantUtf8 u = (ConstantUtf8)c;

         utf8_table.put(u.getBytes(), new Index(i));
      } else if(c instanceof ConstantCP) {
        ConstantCP          m     = (ConstantCP)c;
        ConstantClass       clazz = (ConstantClass)constants[m.getClassIndex()];
        ConstantNameAndType n     = (ConstantNameAndType)constants[m.getNameAndTypeIndex()];

        ConstantUtf8 u8         = (ConstantUtf8)constants[clazz.getNameIndex()];
        String       class_name = u8.getBytes().replace('/', '.');

        u8 = (ConstantUtf8)constants[n.getNameIndex()];
        String method_name = u8.getBytes();

        u8 = (ConstantUtf8)constants[n.getSignatureIndex()];
        String signature = u8.getBytes();

        String delim = METHODREF_DELIM;

        if(c instanceof ConstantInterfaceMethodref)
          delim = IMETHODREF_DELIM;
        else if(c instanceof ConstantFieldref)
          delim = FIELDREF_DELIM;

        cp_table.put(class_name + delim + method_name + delim + signature, new Index(i));
      }
    }
  }

  /**
   * Initialize with given constant pool.
   */
  public ConstantPoolGen(ConstantPool cp) {
    this(cp.getConstantPool());
  }

  /**
   * Create empty constant pool.
   */
  public ConstantPoolGen() {}

  /** Resize internal array of constants.
   */
  protected void adjustSize() {
    if(index + 3 >= size) {
      Constant[] cs = constants;

      size      *= 2;
      constants  = new Constant[size];
      System.arraycopy(cs, 0, constants, 0, index);
    }
  }

  private HashMap string_table = new HashMap();

  /**
   * Look for ConstantString in ConstantPool containing String `str'.
   *
   * @param str String to search for
   * @return index on success, -1 otherwise
   */
  public int lookupString(String str) {
    Index index = (Index)string_table.get(str);
    return (index != null)? index.index : -1;
  }

  /**
   * Add a new String constant to the ConstantPool, if it is not already in there.
   *
   * @param str String to add
   * @return index of entry
   */
  public int addString(String str) {
    int ret;

    if((ret = lookupString(str)) != -1)
      return ret; // Already in CP

    int utf8 = addUtf8(str);

    adjustSize();

    ConstantString s  = new ConstantString(utf8);

    ret = index;
    constants[index++] = s;

    string_table.put(str, new Index(ret));

    return ret;
  }

  private HashMap class_table = new HashMap();

  /**
   * Look for ConstantClass in ConstantPool named `str'.
   *
   * @param str String to search for
   * @return index on success, -1 otherwise
   */
  public int lookupClass(String str) {
    Index index = (Index)class_table.get(str.replace('.', '/'));
    return (index != null)? index.index : -1;
  }

  private int addClass_(String clazz) {
    int    ret;

    if((ret = lookupClass(clazz)) != -1)
      return ret; // Already in CP

    adjustSize();

    ConstantClass c = new ConstantClass(addUtf8(clazz));

    ret = index;
    constants[index++] = c;

    class_table.put(clazz, new Index(ret));

    return ret;
  }

  /**
   * Add a new Class reference to the ConstantPool, if it is not already in there.
   *
   * @param str Class to add
   * @return index of entry
   */
  public int addClass(String str) {
    return addClass_(str.replace('.', '/'));
  }

  /**
   * Add a new Class reference to the ConstantPool for a given type.
   *
   * @param str Class to add
   * @return index of entry
   */
  public int addClass(ObjectType type) {
    return addClass(type.getClassName());
  }

  /**
   * Add a reference to an array class (e.g. String[][]) as needed by MULTIANEWARRAY
   * instruction, e.g. to the ConstantPool.
   *
   * @param type type of array class
   * @return index of entry
   */
  public int addArrayClass(ArrayType type) {
    return addClass_(type.getSignature());
  }

  /**
   * Look for ConstantInteger in ConstantPool.
   *
   * @param n integer number to look for
   * @return index on success, -1 otherwise
   */
  public int lookupInteger(int n) {
    for(int i=1; i < index; i++) {
      if(constants[i] instanceof ConstantInteger) {
        ConstantInteger c = (ConstantInteger)constants[i];

        if(c.getBytes() == n)
          return i;
      }
    }

    return -1;
  }

  /**
   * Add a new Integer constant to the ConstantPool, if it is not already in there.
   *
   * @param n integer number to add
   * @return index of entry
   */
  public int addInteger(int n) {
    int  ret;

    if((ret = lookupInteger(n)) != -1)
      return ret; // Already in CP

    adjustSize();

    ret = index;
    constants[index++] = new ConstantInteger(n);

    return ret;
  }

  /**
   * Look for ConstantFloat in ConstantPool.
   *
   * @param n Float number to look for
   * @return index on success, -1 otherwise
   */
  public int lookupFloat(float n) {
    int bits = Float.floatToIntBits(n);

    for(int i=1; i < index; i++) {
      if(constants[i] instanceof ConstantFloat) {
        ConstantFloat c = (ConstantFloat)constants[i];

        if(Float.floatToIntBits(c.getBytes()) == bits)
          return i;
      }
    }

    return -1;
  }

  /**
   * Add a new Float constant to the ConstantPool, if it is not already in there.
   *
   * @param n Float number to add
   * @return index of entry
   */
  public int addFloat(float n) {
    int  ret;

    if((ret = lookupFloat(n)) != -1)
      return ret; // Already in CP

    adjustSize();

    ret = index;
    constants[index++] = new ConstantFloat(n);

    return ret;
  }

  private HashMap utf8_table = new HashMap();

  /**
   * Look for ConstantUtf8 in ConstantPool.
   *
   * @param n Utf8 string to look for
   * @return index on success, -1 otherwise
   */
  public int lookupUtf8(String n) {
    Index index = (Index)utf8_table.get(n);

    return (index != null)? index.index : -1;
  }

  /**
   * Add a new Utf8 constant to the ConstantPool, if it is not already in there.
   *
   * @param n Utf8 string to add
   * @return index of entry
   */
  public int addUtf8(String n) {
    int  ret;

    if((ret = lookupUtf8(n)) != -1)
      return ret; // Already in CP

    adjustSize();

    ret = index;
    constants[index++] = new ConstantUtf8(n);

    utf8_table.put(n, new Index(ret));

    return ret;
  }

  /**
   * Look for ConstantLong in ConstantPool.
   *
   * @param n Long number to look for
   * @return index on success, -1 otherwise
   */
  public int lookupLong(long n) {
    for(int i=1; i < index; i++) {
      if(constants[i] instanceof ConstantLong) {
        ConstantLong c = (ConstantLong)constants[i];

        if(c.getBytes() == n)
          return i;
      }
    }

    return -1;
  }

  /**
   * Add a new long constant to the ConstantPool, if it is not already in there.
   *
   * @param n Long number to add
   * @return index of entry
   */
  public int addLong(long n) {
    int  ret;

    if((ret = lookupLong(n)) != -1)
      return ret; // Already in CP

    adjustSize();

    ret = index;
    constants[index] = new ConstantLong(n);
    index += 2;   // Wastes one entry according to spec

    return ret;
  }

  /**
   * Look for ConstantDouble in ConstantPool.
   *
   * @param n Double number to look for
   * @return index on success, -1 otherwise
   */
  public int lookupDouble(double n) {
    long bits = Double.doubleToLongBits(n);

    for(int i=1; i < index; i++) {
      if(constants[i] instanceof ConstantDouble) {
        ConstantDouble c = (ConstantDouble)constants[i];

        if(Double.doubleToLongBits(c.getBytes()) == bits)
          return i;
      }
    }

    return -1;
  }

  /**
   * Add a new double constant to the ConstantPool, if it is not already in there.
   *
   * @param n Double number to add
   * @return index of entry
   */
  public int addDouble(double n) {
    int  ret;

    if((ret = lookupDouble(n)) != -1)
      return ret; // Already in CP

    adjustSize();

    ret = index;
    constants[index] = new ConstantDouble(n);
    index += 2;   // Wastes one entry according to spec

    return ret;
  }

  private HashMap n_a_t_table = new HashMap();

  /**
   * Look for ConstantNameAndType in ConstantPool.
   *
   * @param name of variable/method
   * @param signature of variable/method
   * @return index on success, -1 otherwise
   */
  public int lookupNameAndType(String name, String signature) {
    Index index = (Index)n_a_t_table.get(name + NAT_DELIM + signature);
    return (index != null)? index.index : -1;
  }

  /**
   * Add a new NameAndType constant to the ConstantPool if it is not already
   * in there.
   *
   * @param n NameAndType string to add
   * @return index of entry
   */
  public int addNameAndType(String name, String signature) {
    int  ret;
    int  name_index, signature_index;

    if((ret = lookupNameAndType(name, signature)) != -1)
      return ret; // Already in CP

    adjustSize();

    name_index      = addUtf8(name);
    signature_index = addUtf8(signature);
    ret = index;
    constants[index++] = new ConstantNameAndType(name_index, signature_index);

    n_a_t_table.put(name + NAT_DELIM + signature, new Index(ret));
    return ret;
  }

  private HashMap cp_table = new HashMap();

  /**
   * Look for ConstantMethodref in ConstantPool.
   *
   * @param class_name Where to find method
   * @param method_name Guess what
   * @param signature return and argument types
   * @return index on success, -1 otherwise
   */
  public int lookupMethodref(String class_name, String method_name, String signature) {
    Index index = (Index)cp_table.get(class_name + METHODREF_DELIM + method_name +
                                      METHODREF_DELIM + signature);
    return (index != null)? index.index : -1;
  }

  public int lookupMethodref(MethodGen method) {
    return lookupMethodref(method.getClassName(), method.getName(),
                          method.getSignature());
  }

  /**
   * Add a new Methodref constant to the ConstantPool, if it is not already
   * in there.
   *
   * @param n Methodref string to add
   * @return index of entry
   */
  public int addMethodref(String class_name, String method_name, String signature) {
    int  ret, class_index, name_and_type_index;

    if((ret = lookupMethodref(class_name, method_name, signature)) != -1)
      return ret; // Already in CP

    adjustSize();

    name_and_type_index = addNameAndType(method_name, signature);
    class_index         = addClass(class_name);
    ret = index;
    constants[index++] = new ConstantMethodref(class_index, name_and_type_index);

    cp_table.put(class_name + METHODREF_DELIM + method_name +
                 METHODREF_DELIM + signature, new Index(ret));

    return ret;
  }

  public int addMethodref(MethodGen method) {
    return addMethodref(method.getClassName(), method.getName(),
                        method.getSignature());
  }

  /**
   * Look for ConstantInterfaceMethodref in ConstantPool.
   *
   * @param class_name Where to find method
   * @param method_name Guess what
   * @param signature return and argument types
   * @return index on success, -1 otherwise
   */
  public int lookupInterfaceMethodref(String class_name, String method_name, String signature) {
    Index index = (Index)cp_table.get(class_name + IMETHODREF_DELIM + method_name +
                                      IMETHODREF_DELIM + signature);
    return (index != null)? index.index : -1;
  }

  public int lookupInterfaceMethodref(MethodGen method) {
    return lookupInterfaceMethodref(method.getClassName(), method.getName(),
                                    method.getSignature());
  }

  /**
   * Add a new InterfaceMethodref constant to the ConstantPool, if it is not already
   * in there.
   *
   * @param n InterfaceMethodref string to add
   * @return index of entry
   */
  public int addInterfaceMethodref(String class_name, String method_name, String signature) {
    int ret, class_index, name_and_type_index;

    if((ret = lookupInterfaceMethodref(class_name, method_name, signature)) != -1)
      return ret; // Already in CP

    adjustSize();

    class_index         = addClass(class_name);
    name_and_type_index = addNameAndType(method_name, signature);
    ret = index;
    constants[index++] = new ConstantInterfaceMethodref(class_index, name_and_type_index);

    cp_table.put(class_name + IMETHODREF_DELIM + method_name +
                 IMETHODREF_DELIM + signature, new Index(ret));

    return ret;
  }

  public int addInterfaceMethodref(MethodGen method) {
    return addInterfaceMethodref(method.getClassName(), method.getName(),
                                 method.getSignature());
  }

  /**
   * Look for ConstantFieldref in ConstantPool.
   *
   * @param class_name Where to find method
   * @param field_name Guess what
   * @param signature return and argument types
   * @return index on success, -1 otherwise
   */
  public int lookupFieldref(String class_name, String field_name, String signature) {
    Index index = (Index)cp_table.get(class_name + FIELDREF_DELIM + field_name +
                                      FIELDREF_DELIM + signature);
    return (index != null)? index.index : -1;
  }

  /**
   * Add a new Fieldref constant to the ConstantPool, if it is not already
   * in there.
   *
   * @param n Fieldref string to add
   * @return index of entry
   */
  public int addFieldref(String class_name, String field_name, String signature) {
    int  ret;
    int  class_index, name_and_type_index;

    if((ret = lookupFieldref(class_name, field_name, signature)) != -1)
      return ret; // Already in CP

    adjustSize();

    class_index         = addClass(class_name);
    name_and_type_index = addNameAndType(field_name, signature);
    ret = index;
    constants[index++] = new ConstantFieldref(class_index, name_and_type_index);

    cp_table.put(class_name + FIELDREF_DELIM + field_name + FIELDREF_DELIM + signature, new Index(ret));

    return ret;
  }

  /**
   * @param i index in constant pool
   * @return constant pool entry at index i
   */
  public Constant getConstant(int i) { return constants[i]; }

  /**
   * Use with care!
   *
   * @param i index in constant pool
   * @param c new constant pool entry at index i
   */
  public void setConstant(int i, Constant c) { constants[i] = c; }

  /**
   * @return intermediate constant pool
   */
  public ConstantPool getConstantPool() {
    return new ConstantPool(constants);
  }

  /**
   * @return current size of constant pool
   */
  public int getSize() {
    return index;
  }

  /**
   * @return constant pool with proper length
   */
  public ConstantPool getFinalConstantPool() {
    Constant[] cs = new Constant[index];

    System.arraycopy(constants, 0, cs, 0, index);

    return new ConstantPool(cs);
  }

  /**
   * @return String representation.
   */
  public String toString() {
    StringBuffer buf = new StringBuffer();

    for(int i=1; i < index; i++)
      buf.append(i + ")" + constants[i] + "\n");

    return buf.toString();
  }

  /** Import constant from another ConstantPool and return new index.
   */
  public int addConstant(Constant c, ConstantPoolGen cp) {
    Constant[] constants = cp.getConstantPool().getConstantPool();

    switch(c.getTag()) {
    case Constants.CONSTANT_String: {
      ConstantString s  = (ConstantString)c;
      ConstantUtf8   u8 = (ConstantUtf8)constants[s.getStringIndex()];

      return addString(u8.getBytes());
    }

    case Constants.CONSTANT_Class: {
      ConstantClass s  = (ConstantClass)c;
      ConstantUtf8  u8 = (ConstantUtf8)constants[s.getNameIndex()];

      return addClass(u8.getBytes());
    }

    case Constants.CONSTANT_NameAndType: {
      ConstantNameAndType n    = (ConstantNameAndType)c;
      ConstantUtf8        u8   = (ConstantUtf8)constants[n.getNameIndex()];
      ConstantUtf8        u8_2 = (ConstantUtf8)constants[n.getSignatureIndex()];

      return addNameAndType(u8.getBytes(), u8_2.getBytes());
    }

    case Constants.CONSTANT_Utf8:
      return addUtf8(((ConstantUtf8)c).getBytes());

    case Constants.CONSTANT_Double:
      return addDouble(((ConstantDouble)c).getBytes());

    case Constants.CONSTANT_Float:
      return addFloat(((ConstantFloat)c).getBytes());

    case Constants.CONSTANT_Long:
      return addLong(((ConstantLong)c).getBytes());

    case Constants.CONSTANT_Integer:
      return addInteger(((ConstantInteger)c).getBytes());

    case Constants.CONSTANT_InterfaceMethodref: case Constants.CONSTANT_Methodref:
    case Constants.CONSTANT_Fieldref: {
      ConstantCP          m     = (ConstantCP)c;
      ConstantClass       clazz = (ConstantClass)constants[m.getClassIndex()];
      ConstantNameAndType n     = (ConstantNameAndType)constants[m.getNameAndTypeIndex()];
      ConstantUtf8 u8           = (ConstantUtf8)constants[clazz.getNameIndex()];
      String       class_name   = u8.getBytes().replace('/', '.');

      u8 = (ConstantUtf8)constants[n.getNameIndex()];
      String name = u8.getBytes();

      u8 = (ConstantUtf8)constants[n.getSignatureIndex()];
      String signature = u8.getBytes();

      switch(c.getTag()) {
      case Constants.CONSTANT_InterfaceMethodref:
        return addInterfaceMethodref(class_name, name, signature);

      case Constants.CONSTANT_Methodref:
        return addMethodref(class_name, name, signature);

      case Constants.CONSTANT_Fieldref:
        return addFieldref(class_name, name, signature);

      default: // Never reached
        throw new RuntimeException("Unknown constant type " + c);
      }
    }

    default: // Never reached
      throw new RuntimeException("Unknown constant type " + c);
    }
  }
}
