/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.org.apache.bcel.internal.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import com.sun.org.apache.bcel.internal.Const;
import com.sun.org.apache.bcel.internal.classfile.Attribute;
import com.sun.org.apache.bcel.internal.classfile.ClassParser;
import com.sun.org.apache.bcel.internal.classfile.ConstantPool;
import com.sun.org.apache.bcel.internal.classfile.JavaClass;
import com.sun.org.apache.bcel.internal.classfile.Method;
import com.sun.org.apache.bcel.internal.classfile.Utility;

/**
 * Read class file(s) and convert them into HTML files.
 *
 * Given a JavaClass object "class" that is in package "package" five files will be created in the specified directory.
 *
 * <OL>
 * <LI>"package"."class".html as the main file which defines the frames for the following subfiles.
 * <LI>"package"."class"_attributes.html contains all (known) attributes found in the file
 * <LI>"package"."class"_cp.html contains the constant pool
 * <LI>"package"."class"_code.html contains the byte code
 * <LI>"package"."class"_methods.html contains references to all methods and fields of the class
 * </OL>
 *
 * All subfiles reference each other appropriately, e.g. clicking on a method in the Method's frame will jump to the
 * appropriate method in the Code frame.
 *
 * @LastModified: Feb 2023
 */
public class Class2HTML {

    private static String classPackage; // name of package, unclean to make it static, but ...
    private static String className; // name of current class, dito
    private static ConstantPool constantPool;
    private static final Set<String> basicTypes = new HashSet<>();
    static {
        basicTypes.add("int");
        basicTypes.add("short");
        basicTypes.add("boolean");
        basicTypes.add("void");
        basicTypes.add("char");
        basicTypes.add("byte");
        basicTypes.add("long");
        basicTypes.add("double");
        basicTypes.add("float");
    }

    public static void _main(final String[] argv) throws IOException {
        final String[] fileName = new String[argv.length];
        int files = 0;
        ClassParser parser = null;
        JavaClass javaClass = null;
        String zipFile = null;
        final char sep = File.separatorChar;
        String dir = "." + sep; // Where to store HTML files
        /*
         * Parse command line arguments.
         */
        for (int i = 0; i < argv.length; i++) {
            if (argv[i].charAt(0) == '-') { // command line switch
                if (argv[i].equals("-d")) { // Specify target directory, default '.'
                    dir = argv[++i];
                    if (!dir.endsWith("" + sep)) {
                        dir = dir + sep;
                    }
                    final File store = new File(dir);
                    if (!store.isDirectory()) {
                        final boolean created = store.mkdirs(); // Create target directory if necessary
                        if (!created && !store.isDirectory()) {
                            System.out.println("Tried to create the directory " + dir + " but failed");
                        }
                    }
                } else if (argv[i].equals("-zip")) {
                    zipFile = argv[++i];
                } else {
                    System.out.println("Unknown option " + argv[i]);
                }
            } else {
                fileName[files++] = argv[i];
            }
        }
        if (files == 0) {
            System.err.println("Class2HTML: No input files specified.");
        } else { // Loop through files ...
            for (int i = 0; i < files; i++) {
                System.out.print("Processing " + fileName[i] + "...");
                if (zipFile == null) {
                    parser = new ClassParser(fileName[i]); // Create parser object from file
                } else {
                    parser = new ClassParser(zipFile, fileName[i]); // Create parser object from zip file
                }
                javaClass = parser.parse();
                new Class2HTML(javaClass, dir);
                System.out.println("Done.");
            }
        }
    }

    /**
     * Utility method that converts a class reference in the constant pool, i.e., an index to a string.
     */
    static String referenceClass(final int index) {
        String str = constantPool.getConstantString(index, Const.CONSTANT_Class);
        str = Utility.compactClassName(str);
        str = Utility.compactClassName(str, classPackage + ".", true);
        return "<A HREF=\"" + className + "_cp.html#cp" + index + "\" TARGET=ConstantPool>" + str + "</A>";
    }

    static String referenceType(final String type) {
        String shortType = Utility.compactClassName(type);
        shortType = Utility.compactClassName(shortType, classPackage + ".", true);
        final int index = type.indexOf('['); // Type is an array?
        String baseType = type;
        if (index > -1) {
            baseType = type.substring(0, index); // Tack of the '['
        }
        // test for basic type
        if (basicTypes.contains(baseType)) {
            return "<FONT COLOR=\"#00FF00\">" + type + "</FONT>";
        }
        return "<A HREF=\"" + baseType + ".html\" TARGET=_top>" + shortType + "</A>";
    }

    static String toHTML(final String str) {
        final StringBuilder buf = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char ch;
            switch (ch = str.charAt(i)) {
            case '<':
                buf.append("&lt;");
                break;
            case '>':
                buf.append("&gt;");
                break;
            case '\n':
                buf.append("\\n");
                break;
            case '\r':
                buf.append("\\r");
                break;
            default:
                buf.append(ch);
            }
        }
        return buf.toString();
    }

    private final JavaClass javaClass; // current class object

    private final String dir;

    /**
     * Write contents of the given JavaClass into HTML files.
     *
     * @param javaClass The class to write
     * @param dir The directory to put the files in
     * @throws IOException Thrown when an I/O exception of some sort has occurred.
     */
    public Class2HTML(final JavaClass javaClass, final String dir) throws IOException {
        this(javaClass, dir, StandardCharsets.UTF_8);
    }

    private Class2HTML(final JavaClass javaClass, final String dir, final Charset charset) throws IOException {
        final Method[] methods = javaClass.getMethods();
        this.javaClass = javaClass;
        this.dir = dir;
        className = javaClass.getClassName(); // Remember full name
        constantPool = javaClass.getConstantPool();
        // Get package name by tacking off everything after the last '.'
        final int index = className.lastIndexOf('.');
        if (index > -1) {
            classPackage = className.substring(0, index);
        } else {
            classPackage = ""; // default package
        }
        final ConstantHTML constantHtml = new ConstantHTML(dir, className, classPackage, methods, constantPool, charset);
        /*
         * Attributes can't be written in one step, so we just open a file which will be written consequently.
         */
        try (AttributeHTML attributeHtml = new AttributeHTML(dir, className, constantPool, constantHtml, charset)) {
            new MethodHTML(dir, className, methods, javaClass.getFields(), constantHtml, attributeHtml, charset);
            // Write main file (with frames, yuk)
            writeMainHTML(attributeHtml, charset);
            new CodeHTML(dir, className, methods, constantPool, constantHtml, charset);
        }
    }

    private void writeMainHTML(final AttributeHTML attributeHtml, final Charset charset) throws FileNotFoundException, UnsupportedEncodingException {
        try (PrintWriter file = new PrintWriter(dir + className + ".html", charset.name())) {
            file.println("<HTML>\n" + "<HEAD><TITLE>Documentation for " + className + "</TITLE>" + "</HEAD>\n" + "<FRAMESET BORDER=1 cols=\"30%,*\">\n"
                + "<FRAMESET BORDER=1 rows=\"80%,*\">\n" + "<FRAME NAME=\"ConstantPool\" SRC=\"" + className + "_cp.html" + "\"\n MARGINWIDTH=\"0\" "
                + "MARGINHEIGHT=\"0\" FRAMEBORDER=\"1\" SCROLLING=\"AUTO\">\n" + "<FRAME NAME=\"Attributes\" SRC=\"" + className + "_attributes.html"
                + "\"\n MARGINWIDTH=\"0\" " + "MARGINHEIGHT=\"0\" FRAMEBORDER=\"1\" SCROLLING=\"AUTO\">\n" + "</FRAMESET>\n"
                + "<FRAMESET BORDER=1 rows=\"80%,*\">\n" + "<FRAME NAME=\"Code\" SRC=\"" + className + "_code.html\"\n MARGINWIDTH=0 "
                + "MARGINHEIGHT=0 FRAMEBORDER=1 SCROLLING=\"AUTO\">\n" + "<FRAME NAME=\"Methods\" SRC=\"" + className + "_methods.html\"\n MARGINWIDTH=0 "
                + "MARGINHEIGHT=0 FRAMEBORDER=1 SCROLLING=\"AUTO\">\n" + "</FRAMESET></FRAMESET></HTML>");
        }
        final Attribute[] attributes = javaClass.getAttributes();
        for (int i = 0; i < attributes.length; i++) {
            attributeHtml.writeAttribute(attributes[i], "class" + i);
        }
    }
}
