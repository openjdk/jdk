/*
 * Copyright (c) 1999, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
/*
 * COMPONENT_NAME: idl.toJava
 *
 * ORIGINS: 27
 *
 * Licensed Materials - Property of IBM
 * 5639-D57 (C) COPYRIGHT International Business Machines Corp. 1997, 1999
 * RMI-IIOP v1.0
 *
 */

package com.sun.tools.corba.se.idl.toJavaPortable;

// NOTES:
// -D61056   <klr> Use Util.helperName

import java.io.File;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import com.sun.tools.corba.se.idl.GenFileStream;
import com.sun.tools.corba.se.idl.EnumEntry;
import com.sun.tools.corba.se.idl.SymtabEntry;

/**
 *
 **/
public class EnumGen implements com.sun.tools.corba.se.idl.EnumGen, JavaGenerator
{
  /**
   * Public zero-argument constructor.
   **/
  public EnumGen ()
  {
  } // ctor

  /**
   * Generate the Java code for an IDL enumeration.
   **/
  public void generate (Hashtable symbolTable, EnumEntry e, PrintWriter s)
  {
    this.symbolTable = symbolTable;
    this.e           = e;
    init ();

    openStream ();
    if (stream == null) return;
    generateHolder ();
    generateHelper ();
    writeHeading ();
    writeBody ();
    writeClosing ();
    closeStream ();
  } // generate

  /**
   * Initialize members unique to this generator.
   **/
  protected void init ()
  {
    className = e.name ();
    fullClassName = Util.javaName (e);
  }

  /**
   * Open the print stream to which to write the enumeration class.
   **/
  protected void openStream ()
  {
    stream = Util.stream (e, ".java");
  }

  /**
   * Generate the holder class for this enumeration.
   **/
  protected void generateHolder ()
  {
    ((Factories)Compile.compiler.factories ()).holder ().generate (symbolTable, e);
  }

  /**
   * Generate the helper class for this enumeration.
   **/
  protected void generateHelper ()
  {
    ((Factories)Compile.compiler.factories ()).helper ().generate (symbolTable, e);
  }

  /**
   * Write the heading of the enumeration class, including the package,
   * imports, class statement, and open curly.
   **/
  protected void writeHeading ()
  {
    Util.writePackage (stream, e);
    Util.writeProlog (stream, ((GenFileStream)stream).name ());
    if (e.comment () != null)
      e.comment ().generate ("", stream);
    stream.println ("public class " + className + " implements org.omg.CORBA.portable.IDLEntity");
    stream.println ("{");
  }

  /**
   * Write the members of enumeration class.
   **/
  protected void writeBody ()
  {
    stream.println ("  private        int __value;");
    stream.println ("  private static int __size = " + (e.elements ().size ()) + ';');
    stream.println ("  private static " + fullClassName + "[] __array = new " + fullClassName + " [__size];");
    stream.println ();
    for (int i = 0; i < e.elements ().size (); ++i)
    {
      String label = (String)e.elements ().elementAt (i);
      stream.println ("  public static final int _" + label + " = " + i + ';');
      stream.println ("  public static final " + fullClassName + ' ' + label + " = new " + fullClassName + "(_" + label + ");");
    }
    stream.println ();
    writeValue ();
    writeFromInt ();
    writeCtors ();
  }

  /**
   * Write the value method for the enumeration class.
   **/
  protected void writeValue ()
  {
    stream.println ("  public int value ()");
    stream.println ("  {");
    stream.println ("    return __value;");
    stream.println ("  }");
    stream.println ();
  } // writeValue

  /**
   * Write the from_int method for the enumeration class.
   **/
  protected void writeFromInt ()
  {
    stream.println ("  public static " + fullClassName + " from_int (int value)");
    stream.println ("  {");
    stream.println ("    if (value >= 0 && value < __size)");
    stream.println ("      return __array[value];");
    stream.println ("    else");
    stream.println ("      throw new org.omg.CORBA.BAD_PARAM ();");
    stream.println ("  }");
    stream.println ();
  }

  /**
   * Write the protected constructor for the enumeration class.
   **/
  protected void writeCtors ()
  {
    stream.println ("  protected " + className + " (int value)");
    stream.println ("  {");
    stream.println ("    __value = value;");
    stream.println ("    __array[__value] = this;");
    stream.println ("  }");
  }

  /**
   * Close the enumeration class.
   **/
  protected void writeClosing ()
  {
    stream.println ("} // class " + className);
  }

  /**
   * Close the print stream, which writes the stream to file.
   **/
  protected void closeStream ()
  {
    stream.close ();
  }

  ///////////////
  // From JavaGenerator

  public int helperType (int index, String indent, TCOffsets tcoffsets, String name, SymtabEntry entry, PrintWriter stream)
  {
    tcoffsets.set (entry);
    EnumEntry enumEntry = (EnumEntry)entry;
    StringBuffer emit = new StringBuffer ("new String[] { ");
    Enumeration e = enumEntry.elements ().elements ();
    boolean firstTime = true;
    while (e.hasMoreElements ())
    {
      if (firstTime)
        firstTime = false;
      else
        emit.append (", ");
      emit.append ('"' + Util.stripLeadingUnderscores ((String)e.nextElement ()) + '"');
    }
    emit.append ("} ");
    stream.println (indent + name + " = org.omg.CORBA.ORB.init ().create_enum_tc ("
      + Util.helperName (enumEntry, true) + ".id (), \"" // <54697> // <d61056>
//      + "_id, \"" <54697>
      + Util.stripLeadingUnderscores (entry.name ()) + "\", "
      + new String (emit) + ");");
    return index + 1;

  } // helperType

  public int type (int index, String indent, TCOffsets tcoffsets, String name, SymtabEntry entry, PrintWriter stream) {
    stream.println (indent + name + " = " + Util.helperName (entry, true) + ".type ();"); // <d61056>
    return index;
  } // type

  public void helperRead (String entryName, SymtabEntry entry, PrintWriter stream)
  {
    stream.println ("    return " + Util.javaQualifiedName (entry) + ".from_int (istream.read_long ());");
  } // helperRead

  public void helperWrite (SymtabEntry entry, PrintWriter stream)
  {
    stream.println ("    ostream.write_long (value.value ());");
  } // helperWrite

  public int read (int index, String indent, String name, SymtabEntry entry, PrintWriter stream)
  {
    stream.println (indent + name + " = " + Util.javaQualifiedName (entry) + ".from_int (istream.read_long ());");
    return index;
  } // read

  public int write (int index, String indent, String name, SymtabEntry entry, PrintWriter stream)
  {
    stream.println (indent + "ostream.write_long (" + name + ".value ());");
    return index;
  } // write

  // From JavaGenerator
  ///////////////

  protected Hashtable    symbolTable = null;
  protected EnumEntry    e           = null;
  protected PrintWriter  stream      = null;

  // Member data unique to this generator
  String className     = null;
  String fullClassName = null;
} // class EnumGen


/*============================================================================
  DATE<AUTHOR>   ACTION
  ----------------------------------------------------------------------------
  31jul1997<daz> Modified to write comment immediately preceding class defining
                 enumeration declaration.
  12dec1998<klr> D55971 - omg 98-11-03 Java 2.4 RTF  - make subclassable
  ===========================================================================*/
