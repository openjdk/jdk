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

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.sun.org.apache.bcel.internal.Const;

/**
 * Wrapper class that parses a given Java .class file. The method <A
 * href ="#parse">parse</A> returns a <A href ="JavaClass.html">
 * JavaClass</A> object on success. When an I/O error or an
 * inconsistency occurs an appropiate exception is propagated back to
 * the caller.
 *
 * The structure and the names comply, except for a few conveniences,
 * exactly with the <A href="http://docs.oracle.com/javase/specs/">
 * JVM specification 1.0</a>. See this paper for
 * further details about the structure of a bytecode file.
 *
 * @version $Id$
 */
public final class ClassParser {

    private DataInputStream dataInputStream;
    private final boolean fileOwned;
    private final String file_name;
    private String zip_file;
    private int class_name_index;
    private int superclass_name_index;
    private int major; // Compiler version
    private int minor; // Compiler version
    private int access_flags; // Access rights of parsed class
    private int[] interfaces; // Names of implemented interfaces
    private ConstantPool constant_pool; // collection of constants
    private Field[] fields; // class fields, i.e., its variables
    private Method[] methods; // methods defined in the class
    private Attribute[] attributes; // attributes defined in the class
    private final boolean is_zip; // Loaded from zip file
    private static final int BUFSIZE = 8192;


    /**
     * Parse class from the given stream.
     *
     * @param inputStream Input stream
     * @param file_name File name
     */
    public ClassParser(final InputStream inputStream, final String file_name) {
        this.file_name = file_name;
        fileOwned = false;
        final String clazz = inputStream.getClass().getName(); // Not a very clean solution ...
        is_zip = clazz.startsWith("java.util.zip.") || clazz.startsWith("java.util.jar.");
        if (inputStream instanceof DataInputStream) {
            this.dataInputStream = (DataInputStream) inputStream;
        } else {
            this.dataInputStream = new DataInputStream(new BufferedInputStream(inputStream, BUFSIZE));
        }
    }


    /** Parse class from given .class file.
     *
     * @param file_name file name
     */
    public ClassParser(final String file_name) {
        is_zip = false;
        this.file_name = file_name;
        fileOwned = true;
    }


    /** Parse class from given .class file in a ZIP-archive
     *
     * @param zip_file zip file name
     * @param file_name file name
     */
    public ClassParser(final String zip_file, final String file_name) {
        is_zip = true;
        fileOwned = true;
        this.zip_file = zip_file;
        this.file_name = file_name;
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
    public JavaClass parse() throws IOException, ClassFormatException {
        ZipFile zip = null;
        try {
            if (fileOwned) {
                if (is_zip) {
                    zip = new ZipFile(zip_file);
                    final ZipEntry entry = zip.getEntry(file_name);

                    if (entry == null) {
                        throw new IOException("File " + file_name + " not found");
                    }

                    dataInputStream = new DataInputStream(new BufferedInputStream(zip.getInputStream(entry),
                            BUFSIZE));
                } else {
                    dataInputStream = new DataInputStream(new BufferedInputStream(new FileInputStream(
                            file_name), BUFSIZE));
                }
            }
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
            //for (int i=0; i < u.length; i++)
            //  System.err.println("WARNING: " + u[i]);
            // Everything should have been read now
            //      if(file.available() > 0) {
            //        int bytes = file.available();
            //        byte[] buf = new byte[bytes];
            //        file.read(buf);
            //        if(!(is_zip && (buf.length == 1))) {
            //      System.err.println("WARNING: Trailing garbage at end of " + file_name);
            //      System.err.println(bytes + " extra bytes: " + Utility.toHexString(buf));
            //        }
            //      }
        } finally {
            // Read everything of interest, so close the file
            if (fileOwned) {
                try {
                    if (dataInputStream != null) {
                        dataInputStream.close();
                    }
                } catch (final IOException ioe) {
                    //ignore close exceptions
                }
            }
            try {
                if (zip != null) {
                    zip.close();
                }
            } catch (final IOException ioe) {
                //ignore close exceptions
            }
        }
        // Return the information we have gathered in a new object
        return new JavaClass(class_name_index, superclass_name_index, file_name, major, minor,
                access_flags, constant_pool, interfaces, fields, methods, attributes, is_zip
                        ? JavaClass.ZIP
                        : JavaClass.FILE);
    }


    /**
     * Read information about the attributes of the class.
     * @throws  IOException
     * @throws  ClassFormatException
     */
    private void readAttributes() throws IOException, ClassFormatException {
        final int attributes_count = dataInputStream.readUnsignedShort();
        attributes = new Attribute[attributes_count];
        for (int i = 0; i < attributes_count; i++) {
            attributes[i] = Attribute.readAttribute(dataInputStream, constant_pool);
        }
    }


    /**
     * Read information about the class and its super class.
     * @throws  IOException
     * @throws  ClassFormatException
     */
    private void readClassInfo() throws IOException, ClassFormatException {
        access_flags = dataInputStream.readUnsignedShort();
        /* Interfaces are implicitely abstract, the flag should be set
         * according to the JVM specification.
         */
        if ((access_flags & Const.ACC_INTERFACE) != 0) {
            access_flags |= Const.ACC_ABSTRACT;
        }
        if (((access_flags & Const.ACC_ABSTRACT) != 0)
                && ((access_flags & Const.ACC_FINAL) != 0)) {
            throw new ClassFormatException("Class " + file_name + " can't be both final and abstract");
        }
        class_name_index = dataInputStream.readUnsignedShort();
        superclass_name_index = dataInputStream.readUnsignedShort();
    }


    /**
     * Read constant pool entries.
     * @throws  IOException
     * @throws  ClassFormatException
     */
    private void readConstantPool() throws IOException, ClassFormatException {
        constant_pool = new ConstantPool(dataInputStream);
    }


    /**
     * Read information about the fields of the class, i.e., its variables.
     * @throws  IOException
     * @throws  ClassFormatException
     */
    private void readFields() throws IOException, ClassFormatException {
        final int fields_count = dataInputStream.readUnsignedShort();
        fields = new Field[fields_count];
        for (int i = 0; i < fields_count; i++) {
            fields[i] = new Field(dataInputStream, constant_pool);
        }
    }


    /******************** Private utility methods **********************/
    /**
     * Check whether the header of the file is ok.
     * Of course, this has to be the first action on successive file reads.
     * @throws  IOException
     * @throws  ClassFormatException
     */
    private void readID() throws IOException, ClassFormatException {
        if (dataInputStream.readInt() != Const.JVM_CLASSFILE_MAGIC) {
            throw new ClassFormatException(file_name + " is not a Java .class file");
        }
    }


    /**
     * Read information about the interfaces implemented by this class.
     * @throws  IOException
     * @throws  ClassFormatException
     */
    private void readInterfaces() throws IOException, ClassFormatException {
        final int interfaces_count = dataInputStream.readUnsignedShort();
        interfaces = new int[interfaces_count];
        for (int i = 0; i < interfaces_count; i++) {
            interfaces[i] = dataInputStream.readUnsignedShort();
        }
    }


    /**
     * Read information about the methods of the class.
     * @throws  IOException
     * @throws  ClassFormatException
     */
    private void readMethods() throws IOException, ClassFormatException {
        final int methods_count = dataInputStream.readUnsignedShort();
        methods = new Method[methods_count];
        for (int i = 0; i < methods_count; i++) {
            methods[i] = new Method(dataInputStream, constant_pool);
        }
    }


    /**
     * Read major and minor version of compiler which created the file.
     * @throws  IOException
     * @throws  ClassFormatException
     */
    private void readVersion() throws IOException, ClassFormatException {
        minor = dataInputStream.readUnsignedShort();
        major = dataInputStream.readUnsignedShort();
    }
}
