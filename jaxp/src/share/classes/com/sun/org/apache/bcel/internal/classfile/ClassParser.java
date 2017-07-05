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
import  java.util.zip.*;

/**
 * Wrapper class that parses a given Java .class file. The method <A
 * href ="#parse">parse</A> returns a <A href ="JavaClass.html">
 * JavaClass</A> object on success. When an I/O error or an
 * inconsistency occurs an appropiate exception is propagated back to
 * the caller.
 *
 * The structure and the names comply, except for a few conveniences,
 * exactly with the <A href="ftp://java.sun.com/docs/specs/vmspec.ps">
 * JVM specification 1.0</a>. See this paper for
 * further details about the structure of a bytecode file.
 *
 * @author <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 */
public final class ClassParser {
  private DataInputStream file;
  private ZipFile         zip;
  private String          file_name;
  private int             class_name_index, superclass_name_index;
  private int             major, minor; // Compiler version
  private int             access_flags; // Access rights of parsed class
  private int[]           interfaces; // Names of implemented interfaces
  private ConstantPool    constant_pool; // collection of constants
  private Field[]         fields; // class fields, i.e., its variables
  private Method[]        methods; // methods defined in the class
  private Attribute[]     attributes; // attributes defined in the class
  private boolean         is_zip; // Loaded from zip file

  private static final int BUFSIZE = 8192;

  /**
   * Parse class from the given stream.
   *
   * @param file Input stream
   * @param file_name File name
   */
  public ClassParser(InputStream file, String file_name) {
    this.file_name = file_name;

    String clazz = file.getClass().getName(); // Not a very clean solution ...
    is_zip = clazz.startsWith("java.util.zip.") || clazz.startsWith("java.util.jar.");

    if(file instanceof DataInputStream) // Is already a data stream
      this.file = (DataInputStream)file;
    else
      this.file = new DataInputStream(new BufferedInputStream(file, BUFSIZE));
  }

  /** Parse class from given .class file.
   *
   * @param file_name file name
   * @throws IOException
   */
  public ClassParser(String file_name) throws IOException
  {
    is_zip = false;
    this.file_name = file_name;
    file = new DataInputStream(new BufferedInputStream
                               (new FileInputStream(file_name), BUFSIZE));
  }

  /** Parse class from given .class file in a ZIP-archive
   *
   * @param file_name file name
   * @throws IOException
   */
  public ClassParser(String zip_file, String file_name) throws IOException
  {
    is_zip = true;
    zip = new ZipFile(zip_file);
    ZipEntry entry = zip.getEntry(file_name);

    this.file_name = file_name;

    file = new DataInputStream(new BufferedInputStream(zip.getInputStream(entry),
                                                       BUFSIZE));
  }

  /**
   * Parse the given Java class file and return an object that represents
   * the contained data, i.e., constants, methods, fields and commands.
   * A <em>ClassFormatException</em> is raised, if the file is not a valid
   * .class file. (This does not include verification of the byte code as it
   * is performed by the java interpreter).
   *
   * @return Class object representing the parsed class file
   * @throws  IOException
   * @throws  ClassFormatException
   */
  public JavaClass parse() throws IOException, ClassFormatException
  {
    /****************** Read headers ********************************/
    // Check magic tag of class file
    readID();

    // Get compiler version
    readVersion();

    /****************** Read constant pool and related **************/
    // Read constant pool entries
    readConstantPool();

    // Get class information
    readClassInfo();

    // Get interface information, i.e., implemented interfaces
    readInterfaces();

    /****************** Read class fields and methods ***************/
    // Read class fields, i.e., the variables of the class
    readFields();

    // Read class methods, i.e., the functions in the class
    readMethods();

    // Read class attributes
    readAttributes();

    // Check for unknown variables
    //Unknown[] u = Unknown.getUnknownAttributes();
    //for(int i=0; i < u.length; i++)
    //  System.err.println("WARNING: " + u[i]);

    // Everything should have been read now
    //      if(file.available() > 0) {
    //        int bytes = file.available();
    //        byte[] buf = new byte[bytes];
    //        file.read(buf);

    //        if(!(is_zip && (buf.length == 1))) {
    //          System.err.println("WARNING: Trailing garbage at end of " + file_name);
    //          System.err.println(bytes + " extra bytes: " + Utility.toHexString(buf));
    //        }
    //      }

    // Read everything of interest, so close the file
    file.close();
    if(zip != null)
      zip.close();

    // Return the information we have gathered in a new object
    return new JavaClass(class_name_index, superclass_name_index,
                         file_name, major, minor, access_flags,
                         constant_pool, interfaces, fields,
                         methods, attributes, is_zip? JavaClass.ZIP : JavaClass.FILE);
  }

  /**
   * Read information about the attributes of the class.
   * @throws  IOException
   * @throws  ClassFormatException
   */
  private final void readAttributes() throws IOException, ClassFormatException
  {
    int attributes_count;

    attributes_count = file.readUnsignedShort();
    attributes       = new Attribute[attributes_count];

    for(int i=0; i < attributes_count; i++)
      attributes[i] = Attribute.readAttribute(file, constant_pool);
  }

  /**
   * Read information about the class and its super class.
   * @throws  IOException
   * @throws  ClassFormatException
   */
  private final void readClassInfo() throws IOException, ClassFormatException
  {
    access_flags = file.readUnsignedShort();

    /* Interfaces are implicitely abstract, the flag should be set
     * according to the JVM specification.
     */
    if((access_flags & Constants.ACC_INTERFACE) != 0)
      access_flags |= Constants.ACC_ABSTRACT;

    if(((access_flags & Constants.ACC_ABSTRACT) != 0) &&
       ((access_flags & Constants.ACC_FINAL)    != 0 ))
      throw new ClassFormatException("Class can't be both final and abstract");

    class_name_index      = file.readUnsignedShort();
    superclass_name_index = file.readUnsignedShort();
  }
  /**
   * Read constant pool entries.
   * @throws  IOException
   * @throws  ClassFormatException
   */
  private final void readConstantPool() throws IOException, ClassFormatException
  {
    constant_pool = new ConstantPool(file);
  }

  /**
   * Read information about the fields of the class, i.e., its variables.
   * @throws  IOException
   * @throws  ClassFormatException
   */
  private final void readFields() throws IOException, ClassFormatException
  {
    int fields_count;

    fields_count = file.readUnsignedShort();
    fields       = new Field[fields_count];

    for(int i=0; i < fields_count; i++)
      fields[i] = new Field(file, constant_pool);
  }

  /******************** Private utility methods **********************/

  /**
   * Check whether the header of the file is ok.
   * Of course, this has to be the first action on successive file reads.
   * @throws  IOException
   * @throws  ClassFormatException
   */
  private final void readID() throws IOException, ClassFormatException
  {
    int magic = 0xCAFEBABE;

    if(file.readInt() != magic)
      throw new ClassFormatException(file_name + " is not a Java .class file");
  }
  /**
   * Read information about the interfaces implemented by this class.
   * @throws  IOException
   * @throws  ClassFormatException
   */
  private final void readInterfaces() throws IOException, ClassFormatException
  {
    int interfaces_count;

    interfaces_count = file.readUnsignedShort();
    interfaces       = new int[interfaces_count];

    for(int i=0; i < interfaces_count; i++)
      interfaces[i] = file.readUnsignedShort();
  }
  /**
   * Read information about the methods of the class.
   * @throws  IOException
   * @throws  ClassFormatException
   */
  private final void readMethods() throws IOException, ClassFormatException
  {
    int methods_count;

    methods_count = file.readUnsignedShort();
    methods       = new Method[methods_count];

    for(int i=0; i < methods_count; i++)
      methods[i] = new Method(file, constant_pool);
  }
  /**
   * Read major and minor version of compiler which created the file.
   * @throws  IOException
   * @throws  ClassFormatException
   */
  private final void readVersion() throws IOException, ClassFormatException
  {
    minor = file.readUnsignedShort();
    major = file.readUnsignedShort();
  }
}
