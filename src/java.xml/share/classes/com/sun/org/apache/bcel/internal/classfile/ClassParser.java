/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.sun.org.apache.bcel.internal.Const;

/**
 * Wrapper class that parses a given Java .class file. The method <a href ="#parse">parse</a> returns a
 * <a href ="JavaClass.html"> JavaClass</a> object on success. When an I/O error or an inconsistency occurs an
 * appropriate exception is propagated back to the caller.
 *
 * The structure and the names comply, except for a few conveniences, exactly with the
 * <a href="https://docs.oracle.com/javase/specs/"> JVM specification 1.0</a>. See this paper for further details about
 * the structure of a bytecode file.
 *
 * @LastModified: Nov 2026
 */
public final class ClassParser {

    private static final int BUFSIZE = 8192;

    static int[] readU2U2Table(final DataInput dataInput) throws IOException {
        final int count = dataInput.readUnsignedShort();
        final int[] table = new int[count];
        for (int i = 0; i < count; i++) {
            table[i] = dataInput.readUnsignedShort();
        }
        return table;
    }

    private DataInputStream dataInputStream;
    private final boolean fileOwned;
    private final String fileName;
    private String zipFile;
    private int classNameIndex;
    private int superclassNameIndex;
    private int major; // Compiler version
    private int minor; // Compiler version
    private int accessFlags; // Access rights of parsed class
    private int[] interfaces; // Names of implemented interfaces
    private ConstantPool constantPool; // collection of constants
    private Field[] fields; // class fields, that is, its variables
    private Method[] methods; // methods defined in the class
    private Attribute[] attributes; // attributes defined in the class

    private final boolean isZip; // Loaded from ZIP file

    /**
     * Parses class from the given stream.
     *
     * @param inputStream Input stream.
     * @param fileName File name.
     */
    public ClassParser(final InputStream inputStream, final String fileName) {
        this.fileName = fileName;
        this.fileOwned = false;
        final String clazz = inputStream.getClass().getName(); // Not a very clean solution ...
        this.isZip = clazz.startsWith("java.util.zip.") || clazz.startsWith("java.util.jar.");
        if (inputStream instanceof DataInputStream) {
            this.dataInputStream = (DataInputStream) inputStream;
        } else {
            this.dataInputStream = new DataInputStream(new BufferedInputStream(inputStream, BUFSIZE));
        }
    }

    /**
     * Parses class from given .class file.
     *
     * @param fileName file name.
     */
    public ClassParser(final String fileName) {
        this.isZip = false;
        this.fileName = fileName;
        this.fileOwned = true;
    }

    /**
     * Parses class from given .class file in a ZIP-archive
     *
     * @param zipFile ZIP file name.
     * @param fileName file name.
     */
    public ClassParser(final String zipFile, final String fileName) {
        this.isZip = true;
        this.fileOwned = true;
        this.zipFile = zipFile;
        this.fileName = fileName;
    }

    /**
     * Parses the given Java class file and return an object that represents the contained data, that is, constants, methods,
     * fields and commands. A <em>ClassFormatException</em> is raised, if the file is not a valid .class file. (This does
     * not include verification of the byte code as it is performed by the Java interpreter).
     *
     * @return Class object representing the parsed class file.
     * @throws IOException Thrown if an I/O error occurs.
     * @throws ClassFormatException Thrown if a class is malformed or cannot be interpreted as a class file.
     */
    public JavaClass parse() throws IOException, ClassFormatException {
        ZipFile zip = null;
        try {
            if (fileOwned) {
                if (isZip) {
                    zip = new ZipFile(zipFile);
                    final ZipEntry entry = zip.getEntry(fileName);

                    if (entry == null) {
                        throw new IOException("File " + fileName + " not found");
                    }

                    dataInputStream = new DataInputStream(new BufferedInputStream(zip.getInputStream(entry), BUFSIZE));
                } else {
                    dataInputStream = new DataInputStream(new BufferedInputStream(new FileInputStream(fileName), BUFSIZE));
                }
            }
            // -- Read headers --
            // Check magic tag of class file
            readID();
            // Get compiler version
            readVersion();
            // -- Read constant pool and related **************/
            // Read constant pool entries
            readConstantPool();
            // Get class information
            readClassInfo();
            // Get interface information, that is, implemented interfaces
            readInterfaces();
            // -- Read class fields and methods --
            // Read class fields, that is, the variables of the class
            readFields();
            // Read class methods, that is, the functions in the class
            readMethods();
            // Read class attributes
            readAttributes();
            // Check for unknown variables
            // Unknown[] u = Unknown.getUnknownAttributes();
            // for (int i=0; i < u.length; i++)
            // System.err.println("WARNING: " + u[i]);
            // Everything should have been read now
            // if (file.available() > 0) {
            // int bytes = file.available();
            // byte[] buf = new byte[bytes];
            // file.read(buf);
            // if (!(isZip && (buf.length == 1))) {
            // System.err.println("WARNING: Trailing garbage at end of " + fileName);
            // System.err.println(bytes + " extra bytes: " + Utility.toHexString(buf));
            // }
            // }
        } finally {
            // Read everything of interest, so close the file
            try {
                if (fileOwned && dataInputStream != null) {
                    dataInputStream.close();
                }
                if (zip != null) zip.close();
            } catch (IOException ignored) {
            }
        }
        // Return the information we have gathered in a new object
        return new JavaClass(classNameIndex, superclassNameIndex, fileName, major, minor, accessFlags, constantPool, interfaces, fields, methods, attributes,
            isZip ? JavaClass.ZIP : JavaClass.FILE);
    }

    /**
     * Reads information about the attributes of the class.
     *
     * @throws IOException Thrown if an I/O error occurs.
     * @throws ClassFormatException Thrown if a class is malformed or cannot be interpreted as a class file.
     */
    private void readAttributes() throws IOException, ClassFormatException {
        final int attributesCount = dataInputStream.readUnsignedShort();
        attributes = new Attribute[attributesCount];
        for (int i = 0; i < attributesCount; i++) {
            attributes[i] = Attribute.readAttribute(dataInputStream, constantPool);
        }
    }

    /**
     * Reads information about the class and its super class.
     *
     * @throws IOException Thrown if an I/O error occurs.
     * @throws ClassFormatException Thrown if a class is malformed or cannot be interpreted as a class file.
     */
    private void readClassInfo() throws IOException, ClassFormatException {
        accessFlags = dataInputStream.readUnsignedShort();
        /*
         * Interfaces are implicitly abstract, the flag should be set according to the JVM specification.
         */
        if ((accessFlags & Const.ACC_INTERFACE) != 0) {
            accessFlags |= Const.ACC_ABSTRACT;
        }
        if ((accessFlags & Const.ACC_ABSTRACT) != 0 && (accessFlags & Const.ACC_FINAL) != 0) {
            throw new ClassFormatException("Class " + fileName + " can't be both final and abstract");
        }
        classNameIndex = dataInputStream.readUnsignedShort();
        superclassNameIndex = dataInputStream.readUnsignedShort();
    }

    /**
     * Reads constant pool entries.
     *
     * @throws IOException Thrown if an I/O error occurs.
     * @throws ClassFormatException Thrown if a class is malformed or cannot be interpreted as a class file.
     */
    private void readConstantPool() throws IOException, ClassFormatException {
        constantPool = new ConstantPool(dataInputStream);
    }

    /**
     * Reads information about the fields of the class, that is, its variables.
     *
     * @throws IOException Thrown if an I/O error occurs.
     * @throws ClassFormatException Thrown if a class is malformed or cannot be interpreted as a class file.
     */
    private void readFields() throws IOException, ClassFormatException {
        final int fieldsCount = dataInputStream.readUnsignedShort();
        fields = new Field[fieldsCount];
        for (int i = 0; i < fieldsCount; i++) {
            fields[i] = new Field(dataInputStream, constantPool);
        }
    }

    /**
     * Checks whether the header of the file is ok. Of course, this has to be the first action on successive file reads.
     *
     * @throws IOException Thrown if an I/O error occurs.
     * @throws ClassFormatException Thrown if a class is malformed or cannot be interpreted as a class file.
     */
    private void readID() throws IOException, ClassFormatException {
        if (dataInputStream.readInt() != Const.JVM_CLASSFILE_MAGIC) {
            throw new ClassFormatException(fileName + " is not a Java .class file");
        }
    }

    /**
     * Reads information about the interfaces implemented by this class.
     *
     * @throws IOException Thrown if an I/O error occurs.
     * @throws ClassFormatException Thrown if a class is malformed or cannot be interpreted as a class file.
     */
    private void readInterfaces() throws IOException, ClassFormatException {
        interfaces = readU2U2Table(dataInputStream);
    }

    /**
     * Reads information about the methods of the class.
     *
     * @throws IOException Thrown if an I/O error occurs.
     * @throws ClassFormatException Thrown if a class is malformed or cannot be interpreted as a class file.
     */
    private void readMethods() throws IOException {
        final int methodsCount = dataInputStream.readUnsignedShort();
        methods = new Method[methodsCount];
        for (int i = 0; i < methodsCount; i++) {
            methods[i] = new Method(dataInputStream, constantPool);
        }
    }

    /**
     * Reads major and minor version of compiler which created the file.
     *
     * @throws IOException Thrown if an I/O error occurs.
     * @throws ClassFormatException Thrown if a class is malformed or cannot be interpreted as a class file.
     */
    private void readVersion() throws IOException, ClassFormatException {
        minor = dataInputStream.readUnsignedShort();
        major = dataInputStream.readUnsignedShort();
    }
}
