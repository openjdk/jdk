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
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Template class for building up a java class. May be initialized with an
 * existing java class (file).
 *
 * @see JavaClass
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public class ClassGen extends AccessFlags implements Cloneable {
  /* Corresponds to the fields found in a JavaClass object.
   */
  private String   class_name, super_class_name, file_name;
  private int      class_name_index = -1, superclass_name_index = -1;
  private int      major = Constants.MAJOR_1_1, minor = Constants.MINOR_1_1;

  private ConstantPoolGen cp; // Template for building up constant pool

  // ArrayLists instead of arrays to gather fields, methods, etc.
  private ArrayList   field_vec     = new ArrayList();
  private ArrayList   method_vec    = new ArrayList();
  private ArrayList   attribute_vec = new ArrayList();
  private ArrayList   interface_vec = new ArrayList();

  /** Convenience constructor to set up some important values initially.
   *
   * @param class_name fully qualified class name
   * @param super_class_name fully qualified superclass name
   * @param file_name source file name
   * @param access_flags access qualifiers
   * @param interfaces implemented interfaces
   * @param cp constant pool to use
   */
  public ClassGen(String class_name, String super_class_name, String file_name,
                  int access_flags, String[] interfaces, ConstantPoolGen cp) {
    this.class_name       = class_name;
    this.super_class_name = super_class_name;
    this.file_name        = file_name;
    this.access_flags     = access_flags;
    this.cp               = cp;

    // Put everything needed by default into the constant pool and the vectors
    if(file_name != null)
      addAttribute(new SourceFile(cp.addUtf8("SourceFile"), 2,
                                  cp.addUtf8(file_name), cp.getConstantPool()));

    class_name_index      = cp.addClass(class_name);
    superclass_name_index = cp.addClass(super_class_name);

    if(interfaces != null)
      for(int i=0; i < interfaces.length; i++)
        addInterface(interfaces[i]);
  }

  /** Convenience constructor to set up some important values initially.
   *
   * @param class_name fully qualified class name
   * @param super_class_name fully qualified superclass name
   * @param file_name source file name
   * @param access_flags access qualifiers
   * @param interfaces implemented interfaces
   */
  public ClassGen(String class_name, String super_class_name, String file_name,
                  int access_flags, String[] interfaces) {
    this(class_name, super_class_name, file_name, access_flags, interfaces,
         new ConstantPoolGen());
  }

  /**
   * Initialize with existing class.
   * @param clazz JavaClass object (e.g. read from file)
   */
  public ClassGen(JavaClass clazz) {
    class_name_index      = clazz.getClassNameIndex();
    superclass_name_index = clazz.getSuperclassNameIndex();
    class_name            = clazz.getClassName();
    super_class_name      = clazz.getSuperclassName();
    file_name             = clazz.getSourceFileName();
    access_flags          = clazz.getAccessFlags();
    cp                    = new ConstantPoolGen(clazz.getConstantPool());
    major                 = clazz.getMajor();
    minor                 = clazz.getMinor();

    Attribute[] attributes = clazz.getAttributes();
    Method[]    methods    = clazz.getMethods();
    Field[]     fields     = clazz.getFields();
    String[]    interfaces = clazz.getInterfaceNames();

    for(int i=0; i < interfaces.length; i++)
      addInterface(interfaces[i]);

    for(int i=0; i < attributes.length; i++)
      addAttribute(attributes[i]);

    for(int i=0; i < methods.length; i++)
      addMethod(methods[i]);

    for(int i=0; i < fields.length; i++)
      addField(fields[i]);
  }

  /**
   * @return the (finally) built up Java class object.
   */
  public JavaClass getJavaClass() {
    int[]        interfaces = getInterfaces();
    Field[]      fields     = getFields();
    Method[]     methods    = getMethods();
    Attribute[]  attributes = getAttributes();

    // Must be last since the above calls may still add something to it
    ConstantPool cp         = this.cp.getFinalConstantPool();

    return new JavaClass(class_name_index, superclass_name_index,
                         file_name, major, minor, access_flags,
                         cp, interfaces, fields, methods, attributes);
  }

  /**
   * Add an interface to this class, i.e., this class has to implement it.
   * @param name interface to implement (fully qualified class name)
   */
  public void addInterface(String name) {
    interface_vec.add(name);
  }

  /**
   * Remove an interface from this class.
   * @param name interface to remove (fully qualified name)
   */
  public void removeInterface(String name) {
    interface_vec.remove(name);
  }

  /**
   * @return major version number of class file
   */
  public int  getMajor()      { return major; }

  /** Set major version number of class file, default value is 45 (JDK 1.1)
   * @param major major version number
   */
  public void setMajor(int major) {
    this.major = major;
  }

  /** Set minor version number of class file, default value is 3 (JDK 1.1)
   * @param minor minor version number
   */
  public void setMinor(int minor) {
    this.minor = minor;
  }

  /**
   * @return minor version number of class file
   */
  public int  getMinor()      { return minor; }

  /**
   * Add an attribute to this class.
   * @param a attribute to add
   */
  public void addAttribute(Attribute a)    { attribute_vec.add(a); }

  /**
   * Add a method to this class.
   * @param m method to add
   */
  public void addMethod(Method m)          { method_vec.add(m); }

  /**
   * Convenience method.
   *
   * Add an empty constructor to this class that does nothing but calling super().
   * @param access rights for constructor
   */
  public void addEmptyConstructor(int access_flags) {
    InstructionList il = new InstructionList();
    il.append(InstructionConstants.THIS); // Push `this'
    il.append(new INVOKESPECIAL(cp.addMethodref(super_class_name,
                                                "<init>", "()V")));
    il.append(InstructionConstants.RETURN);

    MethodGen mg = new MethodGen(access_flags, Type.VOID, Type.NO_ARGS, null,
                       "<init>", class_name, il, cp);
    mg.setMaxStack(1);
    addMethod(mg.getMethod());
  }

  /**
   * Add a field to this class.
   * @param f field to add
   */
  public void addField(Field f)            { field_vec.add(f); }

  public boolean containsField(Field f)    { return field_vec.contains(f); }

  /** @return field object with given name, or null
   */
  public Field containsField(String name) {
    for(Iterator e=field_vec.iterator(); e.hasNext(); ) {
      Field f = (Field)e.next();
      if(f.getName().equals(name))
        return f;
    }

    return null;
  }

  /** @return method object with given name and signature, or null
   */
  public Method containsMethod(String name, String signature) {
    for(Iterator e=method_vec.iterator(); e.hasNext();) {
      Method m = (Method)e.next();
      if(m.getName().equals(name) && m.getSignature().equals(signature))
        return m;
    }

    return null;
  }

  /**
   * Remove an attribute from this class.
   * @param a attribute to remove
   */
  public void removeAttribute(Attribute a) { attribute_vec.remove(a); }

  /**
   * Remove a method from this class.
   * @param m method to remove
   */
  public void removeMethod(Method m)       { method_vec.remove(m); }

  /** Replace given method with new one. If the old one does not exist
   * add the new_ method to the class anyway.
   */
  public void replaceMethod(Method old, Method new_) {
    if(new_ == null)
      throw new ClassGenException("Replacement method must not be null");

    int i = method_vec.indexOf(old);

    if(i < 0)
      method_vec.add(new_);
    else
      method_vec.set(i, new_);
  }

  /** Replace given field with new one. If the old one does not exist
   * add the new_ field to the class anyway.
   */
  public void replaceField(Field old, Field new_) {
    if(new_ == null)
      throw new ClassGenException("Replacement method must not be null");

    int i = field_vec.indexOf(old);

    if(i < 0)
      field_vec.add(new_);
    else
      field_vec.set(i, new_);
  }

  /**
   * Remove a field to this class.
   * @param f field to remove
   */
  public void removeField(Field f)         { field_vec.remove(f); }

  public String getClassName()      { return class_name; }
  public String getSuperclassName() { return super_class_name; }
  public String getFileName()       { return file_name; }

  public void setClassName(String name) {
    class_name = name.replace('/', '.');
    class_name_index = cp.addClass(name);
  }

  public void setSuperclassName(String name) {
    super_class_name = name.replace('/', '.');
    superclass_name_index = cp.addClass(name);
  }

  public Method[] getMethods() {
    Method[] methods = new Method[method_vec.size()];
    method_vec.toArray(methods);
    return methods;
  }

  public void setMethods(Method[] methods) {
    method_vec.clear();
    for(int m=0; m<methods.length; m++)
      addMethod(methods[m]);
  }

  public void setMethodAt(Method method, int pos) {
    method_vec.set(pos, method);
  }

  public Method getMethodAt(int pos) {
    return (Method)method_vec.get(pos);
  }

  public String[] getInterfaceNames() {
    int      size = interface_vec.size();
    String[] interfaces = new String[size];

    interface_vec.toArray(interfaces);
    return interfaces;
  }

  public int[] getInterfaces() {
    int   size = interface_vec.size();
    int[] interfaces = new int[size];

    for(int i=0; i < size; i++)
      interfaces[i] = cp.addClass((String)interface_vec.get(i));

    return interfaces;
  }

  public Field[] getFields() {
    Field[] fields = new Field[field_vec.size()];
    field_vec.toArray(fields);
    return fields;
  }

  public Attribute[] getAttributes() {
    Attribute[] attributes = new Attribute[attribute_vec.size()];
    attribute_vec.toArray(attributes);
    return attributes;
  }

  public ConstantPoolGen getConstantPool() { return cp; }
  public void setConstantPool(ConstantPoolGen constant_pool) {
    cp = constant_pool;
  }

  public void setClassNameIndex(int class_name_index) {
    this.class_name_index = class_name_index;
    class_name = cp.getConstantPool().
      getConstantString(class_name_index, Constants.CONSTANT_Class).replace('/', '.');
  }

  public void setSuperclassNameIndex(int superclass_name_index) {
    this.superclass_name_index = superclass_name_index;
    super_class_name = cp.getConstantPool().
      getConstantString(superclass_name_index, Constants.CONSTANT_Class).replace('/', '.');
  }

  public int getSuperclassNameIndex() { return superclass_name_index; }

  public int getClassNameIndex()   { return class_name_index; }

  private ArrayList observers;

  /** Add observer for this object.
   */
  public void addObserver(ClassObserver o) {
    if(observers == null)
      observers = new ArrayList();

    observers.add(o);
  }

  /** Remove observer for this object.
   */
  public void removeObserver(ClassObserver o) {
    if(observers != null)
      observers.remove(o);
  }

  /** Call notify() method on all observers. This method is not called
   * automatically whenever the state has changed, but has to be
   * called by the user after he has finished editing the object.
   */
  public void update() {
    if(observers != null)
      for(Iterator e = observers.iterator(); e.hasNext(); )
        ((ClassObserver)e.next()).notify(this);
  }

  public Object clone() {
    try {
      return super.clone();
    } catch(CloneNotSupportedException e) {
      System.err.println(e);
      return null;
    }
  }
}
