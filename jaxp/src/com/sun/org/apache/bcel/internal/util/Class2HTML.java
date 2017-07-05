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

import java.io.*;
import java.util.BitSet;
import com.sun.org.apache.bcel.internal.classfile.*;
import com.sun.org.apache.bcel.internal.Constants;

/**
 * Read class file(s) and convert them into HTML files.
 *
 * Given a JavaClass object "class" that is in package "package" five files
 * will be created in the specified directory.
 *
 * <OL>
 * <LI> "package"."class".html as the main file which defines the frames for
 * the following subfiles.
 * <LI>  "package"."class"_attributes.html contains all (known) attributes found in the file
 * <LI>  "package"."class"_cp.html contains the constant pool
 * <LI>  "package"."class"_code.html contains the byte code
 * <LI>  "package"."class"_methods.html contains references to all methods and fields of the class
 * </OL>
 *
 * All subfiles reference each other appropiately, e.g. clicking on a
 * method in the Method's frame will jump to the appropiate method in
 * the Code frame.
 *
 * @version $Id: Class2HTML.java,v 1.3 2007-07-19 04:34:52 ofung Exp $
 * @author <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
*/
public class Class2HTML implements Constants
{
  private JavaClass java_class;     // current class object
  private String    dir;

  private static String       class_package;  // name of package, unclean to make it static, but ...
  private static String       class_name;     // name of current class, dito
  private static ConstantPool constant_pool;

  /**
   * Write contents of the given JavaClass into HTML files.
   *
   * @param java_class The class to write
   * @param dir The directory to put the files in
   */
  public Class2HTML(JavaClass java_class, String dir) throws IOException {
    Method[]     methods       = java_class.getMethods();

    this.java_class = java_class;
    this.dir        = dir;
    class_name      = java_class.getClassName();     // Remember full name
    constant_pool   = java_class.getConstantPool();

    // Get package name by tacking off everything after the last `.'
    int index = class_name.lastIndexOf('.');
    if(index > -1)
      class_package = class_name.substring(0, index);
    else
      class_package = ""; // default package

    ConstantHTML constant_html = new ConstantHTML(dir, class_name, class_package, methods,
                                                  constant_pool);

    /* Attributes can't be written in one step, so we just open a file
     * which will be written consequently.
     */
    AttributeHTML attribute_html = new AttributeHTML(dir, class_name, constant_pool, constant_html);

    MethodHTML method_html = new MethodHTML(dir, class_name, methods, java_class.getFields(),
                                            constant_html, attribute_html);
    // Write main file (with frames, yuk)
    writeMainHTML(attribute_html);
    new CodeHTML(dir, class_name, methods, constant_pool, constant_html);
    attribute_html.close();
  }

  public static void _main(String argv[])
  {
    String[]    file_name = new String[argv.length];
    int         files=0;
    ClassParser parser=null;
    JavaClass   java_class=null;
    String      zip_file = null;
    char        sep = SecuritySupport.getSystemProperty("file.separator").toCharArray()[0];
    String      dir = "." + sep; // Where to store HTML files

    try {
      /* Parse command line arguments.
       */
      for(int i=0; i < argv.length; i++) {
        if(argv[i].charAt(0) == '-') {  // command line switch
          if(argv[i].equals("-d")) {   // Specify target directory, default '.'
            dir = argv[++i];

            if(!dir.endsWith("" + sep))
              dir = dir + sep;

            new File(dir).mkdirs(); // Create target directory if necessary
          }
          else if(argv[i].equals("-zip"))
            zip_file = argv[++i];
          else
            System.out.println("Unknown option " + argv[i]);
        }
        else // add file name to list */
          file_name[files++] = argv[i];
      }

      if(files == 0)
        System.err.println("Class2HTML: No input files specified.");
      else { // Loop through files ...
        for(int i=0; i < files; i++) {
          System.out.print("Processing " + file_name[i] + "...");
          if(zip_file == null)
            parser = new ClassParser(file_name[i]); // Create parser object from file
          else
            parser = new ClassParser(zip_file, file_name[i]); // Create parser object from zip file

          java_class = parser.parse();
          new Class2HTML(java_class, dir);
          System.out.println("Done.");
        }
      }
    } catch(Exception e) {
      System.out.println(e);
      e.printStackTrace(System.out);
    }
  }

  /**
   * Utility method that converts a class reference in the constant pool,
   * i.e., an index to a string.
   */
  static String referenceClass(int index) {
    String str = constant_pool.getConstantString(index, CONSTANT_Class);
    str = Utility.compactClassName(str);
    str = Utility.compactClassName(str, class_package + ".", true);

    return "<A HREF=\"" + class_name + "_cp.html#cp" + index +
      "\" TARGET=ConstantPool>" + str + "</A>";
  }

  static final String referenceType(String type) {
    String short_type = Utility.compactClassName(type);
    short_type = Utility.compactClassName(short_type, class_package + ".", true);

    int index = type.indexOf('['); // Type is an array?
    if(index > -1)
      type = type.substring(0, index); // Tack of the `['

    // test for basic type
    if(type.equals("int")  || type.equals("short") || type.equals("boolean") || type.equals("void")   ||
       type.equals("char") || type.equals("byte")  || type.equals("long")    || type.equals("double") ||
       type.equals("float"))
      return "<FONT COLOR=\"#00FF00\">" + type + "</FONT>";
    else
      return "<A HREF=\"" + type + ".html\" TARGET=_top>" + short_type + "</A>";
  }

  static String toHTML(String str) {
    StringBuffer buf = new StringBuffer();

    try { // Filter any characters HTML doesn't like such as < and > in particular
      for(int i=0; i < str.length(); i++) {
        char ch;

        switch(ch=str.charAt(i)) {
        case '<': buf.append("&lt;"); break;
        case '>': buf.append("&gt;"); break;
        case '\n': buf.append("\\n"); break;
        case '\r': buf.append("\\r"); break;
        default:  buf.append(ch);
        }
      }
    } catch(StringIndexOutOfBoundsException e) {} // Never occurs

    return buf.toString();
  }

  private void writeMainHTML(AttributeHTML attribute_html) throws IOException {
    PrintWriter file       = new PrintWriter(new FileOutputStream(dir + class_name + ".html"));
    Attribute[] attributes = java_class.getAttributes();

    file.println("<HTML>\n" + "<HEAD><TITLE>Documentation for " + class_name + "</TITLE>" +
                 "</HEAD>\n" +
                 "<FRAMESET BORDER=1 cols=\"30%,*\">\n" +
                 "<FRAMESET BORDER=1 rows=\"80%,*\">\n" +

                 "<FRAME NAME=\"ConstantPool\" SRC=\"" + class_name + "_cp.html" + "\"\n MARGINWIDTH=\"0\" " +
                 "MARGINHEIGHT=\"0\" FRAMEBORDER=\"1\" SCROLLING=\"AUTO\">\n" +
                 "<FRAME NAME=\"Attributes\" SRC=\"" + class_name + "_attributes.html" +
                 "\"\n MARGINWIDTH=\"0\" " +
                 "MARGINHEIGHT=\"0\" FRAMEBORDER=\"1\" SCROLLING=\"AUTO\">\n" +
                 "</FRAMESET>\n" +

                 "<FRAMESET BORDER=1 rows=\"80%,*\">\n" +
                 "<FRAME NAME=\"Code\" SRC=\"" + class_name + "_code.html\"\n MARGINWIDTH=0 " +
                 "MARGINHEIGHT=0 FRAMEBORDER=1 SCROLLING=\"AUTO\">\n" +
                 "<FRAME NAME=\"Methods\" SRC=\"" + class_name + "_methods.html\"\n MARGINWIDTH=0 " +
                 "MARGINHEIGHT=0 FRAMEBORDER=1 SCROLLING=\"AUTO\">\n" +
                 "</FRAMESET></FRAMESET></HTML>"
                 );

    file.close();

    for(int i=0; i < attributes.length; i++)
      attribute_html.writeAttribute(attributes[i], "class" + i);
  }
}
