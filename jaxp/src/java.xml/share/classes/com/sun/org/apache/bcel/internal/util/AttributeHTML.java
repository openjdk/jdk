/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
package com.sun.org.apache.bcel.internal.util;

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

import com.sun.org.apache.bcel.internal.classfile.*;
import java.io.*;

/**
 * Convert found attributes into HTML file.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 *
 */
final class AttributeHTML implements com.sun.org.apache.bcel.internal.Constants {
  private String       class_name;     // name of current class
  private PrintWriter  file;                                    // file to write to
  private int          attr_count = 0;
  private ConstantHTML constant_html;
  private ConstantPool constant_pool;

  AttributeHTML(String dir, String class_name, ConstantPool constant_pool,
                ConstantHTML constant_html) throws IOException
  {
    this.class_name    = class_name;
    this.constant_pool = constant_pool;
    this.constant_html = constant_html;

    file = new PrintWriter(new FileOutputStream(dir + class_name + "_attributes.html"));
    file.println("<HTML><BODY BGCOLOR=\"#C0C0C0\"><TABLE BORDER=0>");
  }

  private final String codeLink(int link, int method_number) {
    return "<A HREF=\"" + class_name + "_code.html#code" +
      method_number + "@" + link + "\" TARGET=Code>" +
      link + "</A>";
  }

  final void close() {
    file.println("</TABLE></BODY></HTML>");
    file.close();
  }

  final void writeAttribute(Attribute attribute, String anchor) throws IOException {
    writeAttribute(attribute, anchor, 0);
  }

  final void writeAttribute(Attribute attribute, String anchor, int method_number) throws IOException {
    byte         tag = attribute.getTag();
    int        index;

    if(tag == ATTR_UNKNOWN) // Don't know what to do about this one
      return;

    attr_count++; // Increment number of attributes found so far

    if(attr_count % 2 == 0)
      file.print("<TR BGCOLOR=\"#C0C0C0\"><TD>");
    else
      file.print("<TR BGCOLOR=\"#A0A0A0\"><TD>");

    file.println("<H4><A NAME=\"" + anchor + "\">" + attr_count + " " + ATTRIBUTE_NAMES[tag] + "</A></H4>");

    /* Handle different attributes
     */
    switch(tag) {
    case ATTR_CODE:
      Code        c          = (Code)attribute;

      // Some directly printable values
      file.print("<UL><LI>Maximum stack size = " + c.getMaxStack() +
                 "</LI>\n<LI>Number of local variables = " +
                 c.getMaxLocals() + "</LI>\n<LI><A HREF=\"" + class_name +
                 "_code.html#method" + method_number + "\" TARGET=Code>Byte code</A></LI></UL>\n");

      // Get handled exceptions and list them
      CodeException[] ce  = c.getExceptionTable();
      int             len = ce.length;

      if(len > 0) {
        file.print("<P><B>Exceptions handled</B><UL>");

        for(int i=0; i < len; i++) {
          int catch_type = ce[i].getCatchType(); // Index in constant pool

          file.print("<LI>");

          if(catch_type != 0)
            file.print(constant_html.referenceConstant(catch_type)); // Create Link to _cp.html
          else
            file.print("Any Exception");

          file.print("<BR>(Ranging from lines " + codeLink(ce[i].getStartPC(), method_number) +
                     " to " + codeLink(ce[i].getEndPC(), method_number) + ", handled at line " +
                     codeLink(ce[i].getHandlerPC(), method_number) + ")</LI>");
        }
        file.print("</UL>");
      }
      break;

    case ATTR_CONSTANT_VALUE:
      index = ((ConstantValue)attribute).getConstantValueIndex();

      // Reference _cp.html
      file.print("<UL><LI><A HREF=\"" + class_name + "_cp.html#cp" + index +
                 "\" TARGET=\"ConstantPool\">Constant value index(" + index +")</A></UL>\n");
      break;

    case ATTR_SOURCE_FILE:
      index = ((SourceFile)attribute).getSourceFileIndex();

      // Reference _cp.html
      file.print("<UL><LI><A HREF=\"" + class_name + "_cp.html#cp" + index +
                 "\" TARGET=\"ConstantPool\">Source file index(" + index +")</A></UL>\n");
      break;

    case ATTR_EXCEPTIONS:
      // List thrown exceptions
      int[] indices = ((ExceptionTable)attribute).getExceptionIndexTable();

      file.print("<UL>");

      for(int i=0; i < indices.length; i++)
        file.print("<LI><A HREF=\"" + class_name + "_cp.html#cp" + indices[i] +
                   "\" TARGET=\"ConstantPool\">Exception class index(" + indices[i] + ")</A>\n");

      file.print("</UL>\n");
      break;

    case ATTR_LINE_NUMBER_TABLE:
      LineNumber[] line_numbers =((LineNumberTable)attribute).getLineNumberTable();

      // List line number pairs
      file.print("<P>");

      for(int i=0; i < line_numbers.length; i++) {
        file.print("(" + line_numbers[i].getStartPC() + ",&nbsp;" + line_numbers[i].getLineNumber() + ")");

        if(i < line_numbers.length - 1)
          file.print(", "); // breakable
      }
      break;

    case ATTR_LOCAL_VARIABLE_TABLE:
      LocalVariable[] vars = ((LocalVariableTable)attribute).getLocalVariableTable();

      // List name, range and type
      file.print("<UL>");

      for(int i=0; i < vars.length; i++) {
        index = vars[i].getSignatureIndex();
        String signature = ((ConstantUtf8)constant_pool.getConstant(index, CONSTANT_Utf8)).getBytes();
        signature = Utility.signatureToString(signature, false);
        int  start = vars[i].getStartPC();
        int  end   = (start + vars[i].getLength());

        file.println("<LI>" + Class2HTML.referenceType(signature) +
                     "&nbsp;<B>" + vars[i].getName() + "</B> in slot %" + vars[i].getIndex() +
                     "<BR>Valid from lines " +
                     "<A HREF=\"" + class_name + "_code.html#code" + method_number + "@" + start + "\" TARGET=Code>" +
                     start + "</A> to " +
                     "<A HREF=\"" + class_name + "_code.html#code" + method_number + "@" + end + "\" TARGET=Code>" +
                     end + "</A></LI>");
      }
      file.print("</UL>\n");

      break;

    case ATTR_INNER_CLASSES:
      InnerClass[] classes = ((InnerClasses)attribute).getInnerClasses();

      // List inner classes
      file.print("<UL>");

      for(int i=0; i < classes.length; i++) {
        String name, access;

        index = classes[i].getInnerNameIndex();
        if(index > 0)
          name =((ConstantUtf8)constant_pool.getConstant(index, CONSTANT_Utf8)).getBytes();
        else
          name = "&lt;anonymous&gt;";

        access = Utility.accessToString(classes[i].getInnerAccessFlags());

        file.print("<LI><FONT COLOR=\"#FF0000\">" + access + "</FONT> "+
                   constant_html.referenceConstant(classes[i].getInnerClassIndex()) +
                   " in&nbsp;class " +
                   constant_html.referenceConstant(classes[i].getOuterClassIndex()) +
                   " named " + name + "</LI>\n");
      }

      file.print("</UL>\n");
      break;

    default: // Such as Unknown attribute or Deprecated
      file.print("<P>" + attribute.toString());
    }

    file.println("</TD></TR>");
    file.flush();
  }
}
