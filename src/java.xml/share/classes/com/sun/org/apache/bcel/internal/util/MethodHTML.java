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

package com.sun.org.apache.bcel.internal.util;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import com.sun.org.apache.bcel.internal.Const;
import com.sun.org.apache.bcel.internal.classfile.Attribute;
import com.sun.org.apache.bcel.internal.classfile.Code;
import com.sun.org.apache.bcel.internal.classfile.ExceptionTable;
import com.sun.org.apache.bcel.internal.classfile.Field;
import com.sun.org.apache.bcel.internal.classfile.Method;
import com.sun.org.apache.bcel.internal.classfile.Utility;

/**
 * Convert methods and fields into HTML file.
 */
final class MethodHTML {

    private final String className; // name of current class
    private final PrintWriter printWriter; // file to write to
    private final ConstantHTML constantHtml;
    private final AttributeHTML attributeHtml;

    MethodHTML(final String dir, final String className, final Method[] methods, final Field[] fields, final ConstantHTML constantHtml,
        final AttributeHTML attributeHtml, final Charset charset) throws FileNotFoundException, UnsupportedEncodingException {
        this.className = className;
        this.attributeHtml = attributeHtml;
        this.constantHtml = constantHtml;
        try (PrintWriter newPrintWriter = new PrintWriter(dir + className + "_methods.html", charset.name())) {
            printWriter = newPrintWriter;
            printWriter.print("<HTML><head><meta charset=\"");
            printWriter.print(charset.name());
            printWriter.println("\"></head>");
            printWriter.println("<BODY BGCOLOR=\"#C0C0C0\"><TABLE BORDER=0>");
            printWriter.println("<TR><TH ALIGN=LEFT>Access&nbsp;flags</TH><TH ALIGN=LEFT>Type</TH>" + "<TH ALIGN=LEFT>Field&nbsp;name</TH></TR>");
            for (final Field field : fields) {
                writeField(field);
            }
            printWriter.println("</TABLE>");
            printWriter.println("<TABLE BORDER=0><TR><TH ALIGN=LEFT>Access&nbsp;flags</TH>"
                + "<TH ALIGN=LEFT>Return&nbsp;type</TH><TH ALIGN=LEFT>Method&nbsp;name</TH>" + "<TH ALIGN=LEFT>Arguments</TH></TR>");
            for (int i = 0; i < methods.length; i++) {
                writeMethod(methods[i], i);
            }
            printWriter.println("</TABLE></BODY></HTML>");
        }
    }

    /**
     * Print field of class.
     *
     * @param field field to print
     */
    private void writeField(final Field field) {
        final String type = Utility.signatureToString(field.getSignature());
        final String name = field.getName();
        String access = Utility.accessToString(field.getAccessFlags());
        Attribute[] attributes;
        access = Utility.replace(access, " ", "&nbsp;");
        printWriter.print("<TR><TD><FONT COLOR=\"#FF0000\">" + access + "</FONT></TD>\n<TD>" + Class2HTML.referenceType(type) + "</TD><TD><A NAME=\"field"
            + name + "\">" + name + "</A></TD>");
        attributes = field.getAttributes();
        // Write them to the Attributes.html file with anchor "<name>[<i>]"
        for (int i = 0; i < attributes.length; i++) {
            attributeHtml.writeAttribute(attributes[i], name + "@" + i);
        }
        for (int i = 0; i < attributes.length; i++) {
            if (attributes[i].getTag() == Const.ATTR_CONSTANT_VALUE) { // Default value
                final String str = attributes[i].toString();
                // Reference attribute in _attributes.html
                printWriter.print("<TD>= <A HREF=\"" + className + "_attributes.html#" + name + "@" + i + "\" TARGET=\"Attributes\">" + str + "</TD>\n");
                break;
            }
        }
        printWriter.println("</TR>");
    }

    private void writeMethod(final Method method, final int methodNumber) {
        // Get raw signature
        final String signature = method.getSignature();
        // Get array of strings containing the argument types
        final String[] args = Utility.methodSignatureArgumentTypes(signature, false);
        // Get return type string
        final String type = Utility.methodSignatureReturnType(signature, false);
        // Get method name
        final String name = method.getName();
        String htmlName;
        // Get method's access flags
        String access = Utility.accessToString(method.getAccessFlags());
        // Get the method's attributes, the Code Attribute in particular
        final Attribute[] attributes = method.getAttributes();
        /*
         * HTML doesn't like names like <clinit> and spaces are places to break lines. Both we don't want...
         */
        access = Utility.replace(access, " ", "&nbsp;");
        htmlName = Class2HTML.toHTML(name);
        printWriter.print("<TR VALIGN=TOP><TD><FONT COLOR=\"#FF0000\"><A NAME=method" + methodNumber + ">" + access + "</A></FONT></TD>");
        printWriter.print("<TD>" + Class2HTML.referenceType(type) + "</TD><TD>" + "<A HREF=" + className + "_code.html#method" + methodNumber + " TARGET=Code>"
            + htmlName + "</A></TD>\n<TD>(");
        for (int i = 0; i < args.length; i++) {
            printWriter.print(Class2HTML.referenceType(args[i]));
            if (i < args.length - 1) {
                printWriter.print(", ");
            }
        }
        printWriter.print(")</TD></TR>");
        // Check for thrown exceptions
        for (int i = 0; i < attributes.length; i++) {
            attributeHtml.writeAttribute(attributes[i], "method" + methodNumber + "@" + i, methodNumber);
            final byte tag = attributes[i].getTag();
            if (tag == Const.ATTR_EXCEPTIONS) {
                printWriter.print("<TR VALIGN=TOP><TD COLSPAN=2></TD><TH ALIGN=LEFT>throws</TH><TD>");
                final int[] exceptions = ((ExceptionTable) attributes[i]).getExceptionIndexTable();
                for (int j = 0; j < exceptions.length; j++) {
                    printWriter.print(constantHtml.referenceConstant(exceptions[j]));
                    if (j < exceptions.length - 1) {
                        printWriter.print(", ");
                    }
                }
                printWriter.println("</TD></TR>");
            } else if (tag == Const.ATTR_CODE) {
                final Attribute[] attributeArray = ((Code) attributes[i]).getAttributes();
                for (int j = 0; j < attributeArray.length; j++) {
                    attributeHtml.writeAttribute(attributeArray[j], "method" + methodNumber + "@" + i + "@" + j, methodNumber);
                }
            }
        }
    }
}
