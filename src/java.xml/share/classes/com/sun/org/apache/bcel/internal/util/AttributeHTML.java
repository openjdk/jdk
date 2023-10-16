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

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import com.sun.org.apache.bcel.internal.Const;
import com.sun.org.apache.bcel.internal.classfile.Attribute;
import com.sun.org.apache.bcel.internal.classfile.Code;
import com.sun.org.apache.bcel.internal.classfile.CodeException;
import com.sun.org.apache.bcel.internal.classfile.ConstantPool;
import com.sun.org.apache.bcel.internal.classfile.ConstantValue;
import com.sun.org.apache.bcel.internal.classfile.ExceptionTable;
import com.sun.org.apache.bcel.internal.classfile.InnerClass;
import com.sun.org.apache.bcel.internal.classfile.InnerClasses;
import com.sun.org.apache.bcel.internal.classfile.LineNumber;
import com.sun.org.apache.bcel.internal.classfile.LineNumberTable;
import com.sun.org.apache.bcel.internal.classfile.LocalVariableTable;
import com.sun.org.apache.bcel.internal.classfile.SourceFile;
import com.sun.org.apache.bcel.internal.classfile.Utility;

/**
 * Convert found attributes into HTML file.
 */
final class AttributeHTML implements Closeable {

    private final String className; // name of current class
    private final PrintWriter printWriter; // file to write to
    private int attrCount;
    private final ConstantHTML constantHtml;
    private final ConstantPool constantPool;

    AttributeHTML(final String dir, final String className, final ConstantPool constantPool, final ConstantHTML constantHtml, final Charset charset)
        throws FileNotFoundException, UnsupportedEncodingException {
        this.className = className;
        this.constantPool = constantPool;
        this.constantHtml = constantHtml;
        printWriter = new PrintWriter(dir + className + "_attributes.html", charset.name());
        printWriter.print("<HTML><head><meta charset=\"");
        printWriter.print(charset.name());
        printWriter.println("\"></head>");
        printWriter.println("<BODY BGCOLOR=\"#C0C0C0\"><TABLE BORDER=0>");
    }

    @Override
    public void close() {
        printWriter.println("</TABLE></BODY></HTML>");
        printWriter.close();
    }

    private String codeLink(final int link, final int methodNumber) {
        return "<A HREF=\"" + className + "_code.html#code" + methodNumber + "@" + link + "\" TARGET=Code>" + link + "</A>";
    }

    void writeAttribute(final Attribute attribute, final String anchor) {
        writeAttribute(attribute, anchor, 0);
    }

    void writeAttribute(final Attribute attribute, final String anchor, final int methodNumber) {
        final byte tag = attribute.getTag();
        int index;
        if (tag == Const.ATTR_UNKNOWN) {
            return;
        }
        attrCount++; // Increment number of attributes found so far
        if (attrCount % 2 == 0) {
            printWriter.print("<TR BGCOLOR=\"#C0C0C0\"><TD>");
        } else {
            printWriter.print("<TR BGCOLOR=\"#A0A0A0\"><TD>");
        }
        printWriter.println("<H4><A NAME=\"" + anchor + "\">" + attrCount + " " + Const.getAttributeName(tag) + "</A></H4>");
        /*
         * Handle different attributes
         */
        switch (tag) {
        case Const.ATTR_CODE:
            final Code c = (Code) attribute;
            // Some directly printable values
            printWriter.print("<UL><LI>Maximum stack size = " + c.getMaxStack() + "</LI>\n<LI>Number of local variables = " + c.getMaxLocals()
                + "</LI>\n<LI><A HREF=\"" + className + "_code.html#method" + methodNumber + "\" TARGET=Code>Byte code</A></LI></UL>\n");
            // Get handled exceptions and list them
            final CodeException[] ce = c.getExceptionTable();
            final int len = ce.length;
            if (len > 0) {
                printWriter.print("<P><B>Exceptions handled</B><UL>");
                for (final CodeException cex : ce) {
                    final int catchType = cex.getCatchType(); // Index in constant pool
                    printWriter.print("<LI>");
                    if (catchType != 0) {
                        printWriter.print(constantHtml.referenceConstant(catchType)); // Create Link to _cp.html
                    } else {
                        printWriter.print("Any Exception");
                    }
                    printWriter.print("<BR>(Ranging from lines " + codeLink(cex.getStartPC(), methodNumber) + " to " + codeLink(cex.getEndPC(), methodNumber)
                        + ", handled at line " + codeLink(cex.getHandlerPC(), methodNumber) + ")</LI>");
                }
                printWriter.print("</UL>");
            }
            break;
        case Const.ATTR_CONSTANT_VALUE:
            index = ((ConstantValue) attribute).getConstantValueIndex();
            // Reference _cp.html
            printWriter
                .print("<UL><LI><A HREF=\"" + className + "_cp.html#cp" + index + "\" TARGET=\"ConstantPool\">Constant value index(" + index + ")</A></UL>\n");
            break;
        case Const.ATTR_SOURCE_FILE:
            index = ((SourceFile) attribute).getSourceFileIndex();
            // Reference _cp.html
            printWriter
                .print("<UL><LI><A HREF=\"" + className + "_cp.html#cp" + index + "\" TARGET=\"ConstantPool\">Source file index(" + index + ")</A></UL>\n");
            break;
        case Const.ATTR_EXCEPTIONS:
            // List thrown exceptions
            final int[] indices = ((ExceptionTable) attribute).getExceptionIndexTable();
            printWriter.print("<UL>");
            for (final int indice : indices) {
                printWriter
                    .print("<LI><A HREF=\"" + className + "_cp.html#cp" + indice + "\" TARGET=\"ConstantPool\">Exception class index(" + indice + ")</A>\n");
            }
            printWriter.print("</UL>\n");
            break;
        case Const.ATTR_LINE_NUMBER_TABLE:
            final LineNumber[] lineNumbers = ((LineNumberTable) attribute).getLineNumberTable();
            // List line number pairs
            printWriter.print("<P>");
            for (int i = 0; i < lineNumbers.length; i++) {
                printWriter.print("(" + lineNumbers[i].getStartPC() + ",&nbsp;" + lineNumbers[i].getLineNumber() + ")");
                if (i < lineNumbers.length - 1) {
                    printWriter.print(", "); // breakable
                }
            }
            break;
        case Const.ATTR_LOCAL_VARIABLE_TABLE:
            // List name, range and type
            printWriter.print("<UL>");
            ((LocalVariableTable) attribute).forEach(var -> {
                final int sigIdx = var.getSignatureIndex();
                String signature = constantPool.getConstantUtf8(sigIdx).getBytes();
                signature = Utility.signatureToString(signature, false);
                final int start = var.getStartPC();
                final int end = start + var.getLength();
                printWriter.println("<LI>" + Class2HTML.referenceType(signature) + "&nbsp;<B>" + var.getName() + "</B> in slot %" + var.getIndex()
                    + "<BR>Valid from lines " + "<A HREF=\"" + className + "_code.html#code" + methodNumber + "@" + start + "\" TARGET=Code>" + start
                    + "</A> to " + "<A HREF=\"" + className + "_code.html#code" + methodNumber + "@" + end + "\" TARGET=Code>" + end + "</A></LI>");
            });
            printWriter.print("</UL>\n");
            break;
        case Const.ATTR_INNER_CLASSES:
            // List inner classes
            printWriter.print("<UL>");
            for (final InnerClass clazz : ((InnerClasses) attribute).getInnerClasses()) {
                final String name;
                final String access;
                index = clazz.getInnerNameIndex();
                if (index > 0) {
                    name = constantPool.getConstantUtf8(index).getBytes();
                } else {
                    name = "&lt;anonymous&gt;";
                }
                access = Utility.accessToString(clazz.getInnerAccessFlags());
                printWriter.print("<LI><FONT COLOR=\"#FF0000\">" + access + "</FONT> " + constantHtml.referenceConstant(clazz.getInnerClassIndex())
                    + " in&nbsp;class " + constantHtml.referenceConstant(clazz.getOuterClassIndex()) + " named " + name + "</LI>\n");
            }
            printWriter.print("</UL>\n");
            break;
        default: // Such as Unknown attribute or Deprecated
            printWriter.print("<P>" + attribute);
        }
        printWriter.println("</TD></TR>");
        printWriter.flush();
    }
}
