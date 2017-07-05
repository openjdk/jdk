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
import  com.sun.org.apache.bcel.internal.util.SyntheticRepository;
import  com.sun.org.apache.bcel.internal.util.ClassVector;
import  com.sun.org.apache.bcel.internal.util.ClassQueue;
import  com.sun.org.apache.bcel.internal.generic.Type;
import com.sun.org.apache.xalan.internal.utils.SecuritySupport;

import  java.io.*;
import  java.util.StringTokenizer;

/**
 * Represents a Java class, i.e., the data structures, constant pool,
 * fields, methods and commands contained in a Java .class file.
 * See <a href="ftp://java.sun.com/docs/specs/">JVM
 * specification</a> for details.

 * The intent of this class is to represent a parsed or otherwise existing
 * class file.  Those interested in programatically generating classes
 * should see the <a href="../generic/ClassGen.html">ClassGen</a> class.

 * @version $Id: JavaClass.java,v 1.4 2007-07-19 04:34:42 ofung Exp $
 * @see com.sun.org.apache.bcel.internal.generic.ClassGen
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public class JavaClass extends AccessFlags implements Cloneable, Node {
  private String       file_name;
  private String       package_name;
  private String       source_file_name = "<Unknown>";
  private int          class_name_index;
  private int          superclass_name_index;
  private String       class_name;
  private String       superclass_name;
  private int          major, minor;  // Compiler version
  private ConstantPool constant_pool; // Constant pool
  private int[]        interfaces;    // implemented interfaces
  private String[]     interface_names;
  private Field[]      fields;        // Fields, i.e., variables of class
  private Method[]     methods;       // methods defined in the class
  private Attribute[]  attributes;    // attributes defined in the class
  private byte         source = HEAP; // Generated in memory

  public static final byte HEAP = 1;
  public static final byte FILE = 2;
  public static final byte ZIP  = 3;

  static boolean debug = false; // Debugging on/off
  static char    sep   = '/';   // directory separator

  /**
   * In cases where we go ahead and create something,
   * use the default SyntheticRepository, because we
   * don't know any better.
   */
  private transient com.sun.org.apache.bcel.internal.util.Repository repository =
    SyntheticRepository.getInstance();

  /**
   * Constructor gets all contents as arguments.
   *
   * @param class_name_index Index into constant pool referencing a
   * ConstantClass that represents this class.
   * @param superclass_name_index Index into constant pool referencing a
   * ConstantClass that represents this class's superclass.
   * @param file_name File name
   * @param major Major compiler version
   * @param minor Minor compiler version
   * @param access_flags Access rights defined by bit flags
   * @param constant_pool Array of constants
   * @param interfaces Implemented interfaces
   * @param fields Class fields
   * @param methods Class methods
   * @param attributes Class attributes
   * @param source Read from file or generated in memory?
   */
  public JavaClass(int        class_name_index,
                   int        superclass_name_index,
                   String     file_name,
                   int        major,
                   int        minor,
                   int        access_flags,
                   ConstantPool constant_pool,
                   int[]      interfaces,
                   Field[]      fields,
                   Method[]     methods,
                   Attribute[]  attributes,
                   byte          source)
  {
    if(interfaces == null) // Allowed for backward compatibility
      interfaces = new int[0];
    if(attributes == null)
      this.attributes = new Attribute[0];
    if(fields == null)
      fields = new Field[0];
    if(methods == null)
      methods = new Method[0];

    this.class_name_index      = class_name_index;
    this.superclass_name_index = superclass_name_index;
    this.file_name             = file_name;
    this.major                 = major;
    this.minor                 = minor;
    this.access_flags          = access_flags;
    this.constant_pool         = constant_pool;
    this.interfaces            = interfaces;
    this.fields                = fields;
    this.methods               = methods;
    this.attributes            = attributes;
    this.source                = source;

    // Get source file name if available
    for(int i=0; i < attributes.length; i++) {
      if(attributes[i] instanceof SourceFile) {
        source_file_name = ((SourceFile)attributes[i]).getSourceFileName();
        break;
      }
    }

    /* According to the specification the following entries must be of type
     * `ConstantClass' but we check that anyway via the
     * `ConstPool.getConstant' method.
     */
    class_name = constant_pool.getConstantString(class_name_index,
                                                 Constants.CONSTANT_Class);
    class_name = Utility.compactClassName(class_name, false);

    int index = class_name.lastIndexOf('.');
    if(index < 0)
      package_name = "";
    else
      package_name = class_name.substring(0, index);

    if(superclass_name_index > 0) { // May be zero -> class is java.lang.Object
      superclass_name = constant_pool.getConstantString(superclass_name_index,
                                                        Constants.CONSTANT_Class);
      superclass_name = Utility.compactClassName(superclass_name, false);
    }
    else
      superclass_name = "java.lang.Object";

    interface_names = new String[interfaces.length];
    for(int i=0; i < interfaces.length; i++) {
      String str = constant_pool.getConstantString(interfaces[i], Constants.CONSTANT_Class);
      interface_names[i] = Utility.compactClassName(str, false);
    }
  }

  /**
   * Constructor gets all contents as arguments.
   *
   * @param class_name_index Class name
   * @param superclass_name_index Superclass name
   * @param file_name File name
   * @param major Major compiler version
   * @param minor Minor compiler version
   * @param access_flags Access rights defined by bit flags
   * @param constant_pool Array of constants
   * @param interfaces Implemented interfaces
   * @param fields Class fields
   * @param methods Class methods
   * @param attributes Class attributes
   */
  public JavaClass(int        class_name_index,
                   int        superclass_name_index,
                   String     file_name,
                   int        major,
                   int        minor,
                   int        access_flags,
                   ConstantPool constant_pool,
                   int[]      interfaces,
                   Field[]      fields,
                   Method[]     methods,
                   Attribute[]  attributes) {
    this(class_name_index, superclass_name_index, file_name, major, minor, access_flags,
         constant_pool, interfaces, fields, methods, attributes, HEAP);
  }


  /**
   * Called by objects that are traversing the nodes of the tree implicitely
   * defined by the contents of a Java class. I.e., the hierarchy of methods,
   * fields, attributes, etc. spawns a tree of objects.
   *
   * @param v Visitor object
   */
  public void accept(Visitor v) {
    v.visitJavaClass(this);
  }

  /* Print debug information depending on `JavaClass.debug'
   */
  static final void Debug(String str) {
    if(debug)
      System.out.println(str);
  }

  /**
   * Dump class to a file.
   *
   * @param file Output file
   * @throws IOException
   */
  public void dump(File file) throws IOException
  {
    String parent = file.getParent();

    if(parent != null) {
      File dir = new File(parent);

      if(dir != null)
        dir.mkdirs();
    }

    dump(new DataOutputStream(new FileOutputStream(file)));
  }

  /**
   * Dump class to a file named file_name.
   *
   * @param file_name Output file name
   * @exception IOException
   */
  public void dump(String file_name) throws IOException
  {
    dump(new File(file_name));
  }

  /**
   * @return class in binary format
   */
  public byte[] getBytes() {
    ByteArrayOutputStream s  = new ByteArrayOutputStream();
    DataOutputStream      ds = new DataOutputStream(s);

    try {
      dump(ds);
    } catch(IOException e) {
      e.printStackTrace();
    } finally {
      try { ds.close(); } catch(IOException e2) { e2.printStackTrace(); }
    }

    return s.toByteArray();
  }

  /**
   * Dump Java class to output stream in binary format.
   *
   * @param file Output stream
   * @exception IOException
   */
  public void dump(OutputStream file) throws IOException {
    dump(new DataOutputStream(file));
  }

  /**
   * Dump Java class to output stream in binary format.
   *
   * @param file Output stream
   * @exception IOException
   */
  public void dump(DataOutputStream file) throws IOException
  {
    file.writeInt(0xcafebabe);
    file.writeShort(minor);
    file.writeShort(major);

    constant_pool.dump(file);

    file.writeShort(access_flags);
    file.writeShort(class_name_index);
    file.writeShort(superclass_name_index);

    file.writeShort(interfaces.length);
    for(int i=0; i < interfaces.length; i++)
      file.writeShort(interfaces[i]);

    file.writeShort(fields.length);
    for(int i=0; i < fields.length; i++)
      fields[i].dump(file);

    file.writeShort(methods.length);
    for(int i=0; i < methods.length; i++)
      methods[i].dump(file);

    if(attributes != null) {
      file.writeShort(attributes.length);
      for(int i=0; i < attributes.length; i++)
        attributes[i].dump(file);
    }
    else
      file.writeShort(0);

    file.close();
  }

  /**
   * @return Attributes of the class.
   */
  public Attribute[] getAttributes() { return attributes; }

  /**
   * @return Class name.
   */
  public String getClassName()       { return class_name; }

  /**
   * @return Package name.
   */
  public String getPackageName()       { return package_name; }

  /**
   * @return Class name index.
   */
  public int getClassNameIndex()   { return class_name_index; }

  /**
   * @return Constant pool.
   */
  public ConstantPool getConstantPool() { return constant_pool; }

  /**
   * @return Fields, i.e., variables of the class. Like the JVM spec
   * mandates for the classfile format, these fields are those specific to
   * this class, and not those of the superclass or superinterfaces.
   */
  public Field[] getFields()         { return fields; }

  /**
   * @return File name of class, aka SourceFile attribute value
   */
  public String getFileName()        { return file_name; }

  /**
   * @return Names of implemented interfaces.
   */
  public String[] getInterfaceNames()  { return interface_names; }

  /**
   * @return Indices in constant pool of implemented interfaces.
   */
  public int[] getInterfaceIndices()     { return interfaces; }

  /**
   * @return Major number of class file version.
   */
  public int  getMajor()           { return major; }

  /**
   * @return Methods of the class.
   */
  public Method[] getMethods()       { return methods; }

  /**
   * @return A com.sun.org.apache.bcel.internal.classfile.Method corresponding to
   * java.lang.reflect.Method if any
   */
  public Method getMethod(java.lang.reflect.Method m) {
    for(int i = 0; i < methods.length; i++) {
      Method method = methods[i];

      if(m.getName().equals(method.getName()) &&
         (m.getModifiers() == method.getModifiers()) &&
         Type.getSignature(m).equals(method.getSignature())) {
        return method;
      }
    }

    return null;
  }

  /**
   * @return Minor number of class file version.
   */
  public int  getMinor()           { return minor; }

  /**
   * @return sbsolute path to file where this class was read from
   */
  public String getSourceFileName()  { return source_file_name; }

  /**
   * @return Superclass name.
   */
  public String getSuperclassName()  { return superclass_name; }

  /**
   * @return Class name index.
   */
  public int getSuperclassNameIndex() { return superclass_name_index; }

  static {
    // Debugging ... on/off
    String debug = null, sep = null;

    try {
      debug = SecuritySupport.getSystemProperty("JavaClass.debug");
      // Get path separator either / or \ usually
      sep = SecuritySupport.getSystemProperty("file.separator");
    }
    catch (SecurityException e) {
        // falls through
    }

    if(debug != null)
      JavaClass.debug = new Boolean(debug).booleanValue();

    if(sep != null)
      try {
        JavaClass.sep = sep.charAt(0);
      } catch(StringIndexOutOfBoundsException e) {} // Never reached
  }

  /**
   * @param attributes .
   */
  public void setAttributes(Attribute[] attributes) {
    this.attributes = attributes;
  }

  /**
   * @param class_name .
   */
  public void setClassName(String class_name) {
    this.class_name = class_name;
  }

  /**
   * @param class_name_index .
   */
  public void setClassNameIndex(int class_name_index) {
    this.class_name_index = class_name_index;
  }

  /**
   * @param constant_pool .
   */
  public void setConstantPool(ConstantPool constant_pool) {
    this.constant_pool = constant_pool;
  }

  /**
   * @param fields .
   */
  public void setFields(Field[] fields) {
    this.fields = fields;
  }

  /**
   * Set File name of class, aka SourceFile attribute value
   */
  public void setFileName(String file_name) {
    this.file_name = file_name;
  }

  /**
   * @param interface_names .
   */
  public void setInterfaceNames(String[] interface_names) {
    this.interface_names = interface_names;
  }

  /**
   * @param interfaces .
   */
  public void setInterfaces(int[] interfaces) {
    this.interfaces = interfaces;
  }

  /**
   * @param major .
   */
  public void setMajor(int major) {
    this.major = major;
  }

  /**
   * @param methods .
   */
  public void setMethods(Method[] methods) {
    this.methods = methods;
  }

  /**
   * @param minor .
   */
  public void setMinor(int minor) {
    this.minor = minor;
  }

  /**
   * Set absolute path to file this class was read from.
   */
  public void setSourceFileName(String source_file_name) {
    this.source_file_name = source_file_name;
  }

  /**
   * @param superclass_name .
   */
  public void setSuperclassName(String superclass_name) {
    this.superclass_name = superclass_name;
  }

  /**
   * @param superclass_name_index .
   */
  public void setSuperclassNameIndex(int superclass_name_index) {
    this.superclass_name_index = superclass_name_index;
  }

  /**
   * @return String representing class contents.
   */
  public String toString() {
    String access = Utility.accessToString(access_flags, true);
    access = access.equals("")? "" : (access + " ");

    StringBuffer buf = new StringBuffer(access +
                                        Utility.classOrInterface(access_flags) +
                                        " " +
                                        class_name + " extends " +
                                        Utility.compactClassName(superclass_name,
                                                                 false) + '\n');
    int size = interfaces.length;

    if(size > 0) {
      buf.append("implements\t\t");

      for(int i=0; i < size; i++) {
        buf.append(interface_names[i]);
        if(i < size - 1)
          buf.append(", ");
      }

      buf.append('\n');
    }

    buf.append("filename\t\t" + file_name + '\n');
    buf.append("compiled from\t\t" + source_file_name + '\n');
    buf.append("compiler version\t" + major + "." + minor + '\n');
    buf.append("access flags\t\t" + access_flags + '\n');
    buf.append("constant pool\t\t" + constant_pool.getLength() + " entries\n");
    buf.append("ACC_SUPER flag\t\t" + isSuper() + "\n");

    if(attributes.length > 0) {
      buf.append("\nAttribute(s):\n");
      for(int i=0; i < attributes.length; i++)
        buf.append(indent(attributes[i]));
    }

    if(fields.length > 0) {
      buf.append("\n" + fields.length + " fields:\n");
      for(int i=0; i < fields.length; i++)
        buf.append("\t" + fields[i] + '\n');
    }

    if(methods.length > 0) {
      buf.append("\n" + methods.length + " methods:\n");
      for(int i=0; i < methods.length; i++)
        buf.append("\t" + methods[i] + '\n');
    }

    return buf.toString();
  }

  private static final String indent(Object obj) {
    StringTokenizer tok = new StringTokenizer(obj.toString(), "\n");
    StringBuffer buf = new StringBuffer();

    while(tok.hasMoreTokens())
      buf.append("\t" + tok.nextToken() + "\n");

    return buf.toString();
  }

  /**
   * @return deep copy of this class
   */
  public JavaClass copy() {
    JavaClass c = null;

    try {
      c = (JavaClass)clone();
    } catch(CloneNotSupportedException e) {}

    c.constant_pool   = constant_pool.copy();
    c.interfaces      = (int[])interfaces.clone();
    c.interface_names = (String[])interface_names.clone();

    c.fields = new Field[fields.length];
    for(int i=0; i < fields.length; i++)
      c.fields[i] = fields[i].copy(c.constant_pool);

    c.methods = new Method[methods.length];
    for(int i=0; i < methods.length; i++)
      c.methods[i] = methods[i].copy(c.constant_pool);

    c.attributes = new Attribute[attributes.length];
    for(int i=0; i < attributes.length; i++)
      c.attributes[i] = attributes[i].copy(c.constant_pool);

    return c;
  }

  public final boolean isSuper() {
    return (access_flags & Constants.ACC_SUPER) != 0;
  }

  public final boolean isClass() {
    return (access_flags & Constants.ACC_INTERFACE) == 0;
  }

  /** @return returns either HEAP (generated), FILE, or ZIP
   */
  public final byte getSource() {
    return source;
  }

  /********************* New repository functionality *********************/

  /**
   * Gets the ClassRepository which holds its definition. By default
   * this is the same as SyntheticRepository.getInstance();
   */
  public com.sun.org.apache.bcel.internal.util.Repository getRepository() {
    return repository;
  }

  /**
   * Sets the ClassRepository which loaded the JavaClass.
   * Should be called immediately after parsing is done.
   */
  public void setRepository(com.sun.org.apache.bcel.internal.util.Repository repository) {
    this.repository = repository;
  }

  /** Equivalent to runtime "instanceof" operator.
   *
   * @return true if this JavaClass is derived from teh super class
   */
  public final boolean instanceOf(JavaClass super_class) {
    if(this.equals(super_class))
      return true;

    JavaClass[] super_classes = getSuperClasses();

    for(int i=0; i < super_classes.length; i++) {
      if(super_classes[i].equals(super_class)) {
        return true;
      }
    }

    if(super_class.isInterface()) {
      return implementationOf(super_class);
    }

    return false;
  }

  /**
   * @return true, if clazz is an implementation of interface inter
   */
  public boolean implementationOf(JavaClass inter) {
    if(!inter.isInterface()) {
      throw new IllegalArgumentException(inter.getClassName() + " is no interface");
    }

    if(this.equals(inter)) {
      return true;
    }

    JavaClass[] super_interfaces = getAllInterfaces();

    for(int i=0; i < super_interfaces.length; i++) {
      if(super_interfaces[i].equals(inter)) {
        return true;
      }
    }

    return false;
  }

  /**
   * @return the superclass for this JavaClass object, or null if this
   * is java.lang.Object
   */
  public JavaClass getSuperClass() {
    if("java.lang.Object".equals(getClassName())) {
      return null;
    }

    try {
      return repository.loadClass(getSuperclassName());
    } catch(ClassNotFoundException e) {
      System.err.println(e);
      return null;
    }
  }

  /**
   * @return list of super classes of this class in ascending order, i.e.,
   * java.lang.Object is always the last element
   */
  public JavaClass[] getSuperClasses() {
    JavaClass   clazz = this;
    ClassVector vec   = new ClassVector();

    for(clazz = clazz.getSuperClass(); clazz != null;
        clazz = clazz.getSuperClass())
    {
      vec.addElement(clazz);
    }

    return vec.toArray();
  }

  /**
   * Get interfaces directly implemented by this JavaClass.
   */
  public JavaClass[] getInterfaces() {
    String[]    interfaces = getInterfaceNames();
    JavaClass[] classes    = new JavaClass[interfaces.length];

    try {
      for(int i = 0; i < interfaces.length; i++) {
        classes[i] = repository.loadClass(interfaces[i]);
      }
    } catch(ClassNotFoundException e) {
      System.err.println(e);
      return null;
    }

    return classes;
  }

  /**
   * Get all interfaces implemented by this JavaClass (transitively).
   */
  public JavaClass[] getAllInterfaces() {
    ClassQueue  queue = new ClassQueue();
    ClassVector vec   = new ClassVector();

    queue.enqueue(this);

    while(!queue.empty()) {
      JavaClass clazz = queue.dequeue();

      JavaClass   souper     = clazz.getSuperClass();
      JavaClass[] interfaces = clazz.getInterfaces();

      if(clazz.isInterface()) {
        vec.addElement(clazz);
      } else {
        if(souper != null) {
          queue.enqueue(souper);
        }
      }

      for(int i = 0; i < interfaces.length; i++) {
        queue.enqueue(interfaces[i]);
      }
    }

    return vec.toArray();
  }
}
