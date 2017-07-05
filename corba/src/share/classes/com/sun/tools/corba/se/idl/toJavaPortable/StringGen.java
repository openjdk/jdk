/*
 * Copyright (c) 1999, 2000, Oracle and/or its affiliates. All rights reserved.
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

import java.io.PrintWriter;
import java.util.Hashtable;

import com.sun.tools.corba.se.idl.StringEntry;
import com.sun.tools.corba.se.idl.SymtabEntry;

/**
 * Handles generation of CORBA strings as well as wstrings.  Be careful
 * not to forget the wstrings.
 **/
public class StringGen implements com.sun.tools.corba.se.idl.StringGen, JavaGenerator
{
  /**
   * Public zero-argument constructor.
   **/
  public StringGen ()
  {
  } // ctor

  /**
   * This should never be called.  This class exists for the
   * JavaGenerator interface.
   **/
  public void generate (Hashtable symbolTable, StringEntry e, PrintWriter stream)
  {
  } // generate

  ///////////////
  // From JavaGenerator

  public int helperType (int index, String indent, TCOffsets tcoffsets, String name, SymtabEntry entry, PrintWriter stream)
  {
    return type(index, indent, tcoffsets, name, entry, stream);
  } // helperType

  public int type (int index, String indent, TCOffsets tcoffsets, String name, SymtabEntry entry, PrintWriter stream) {
    tcoffsets.set (entry);
    StringEntry stringEntry = (StringEntry)entry;
    String bound;
    if (stringEntry.maxSize () == null)
      bound = "0";
    else
      bound = Util.parseExpression (stringEntry.maxSize ());

    // entry.name() is necessary to determine whether it is a
    // string or wstring

    stream.println (indent
                    + name
                    + " = org.omg.CORBA.ORB.init ().create_"
                    + entry.name()
                    + "_tc ("
                    + bound + ");");
    return index;
  } // type

  public void helperRead (String entryName, SymtabEntry entry, PrintWriter stream)
  {
  } // helperRead

  public void helperWrite (SymtabEntry entry, PrintWriter stream)
  {
  } // helperWrite

  public int read (int index, String indent, String name, SymtabEntry entry, PrintWriter stream)
  {
    StringEntry string = (StringEntry)entry;
    String entryName = entry.name ();
    if (entryName.equals ("string"))
      stream.println (indent + name + " = istream.read_string ();");
    else if (entryName.equals ("wstring"))
      stream.println (indent + name + " = istream.read_wstring ();");
    if (string.maxSize () != null)
    {
      stream.println (indent + "if (" + name + ".length () > (" + Util.parseExpression (string.maxSize ()) + "))");
      stream.println (indent + "  throw new org.omg.CORBA.MARSHAL (0, org.omg.CORBA.CompletionStatus.COMPLETED_MAYBE);");
    }
    return index;
  } // read

  public int write (int index, String indent, String name, SymtabEntry entry, PrintWriter stream)
  {
    StringEntry string = (StringEntry)entry;
    if (string.maxSize () != null)
    {
      stream.print (indent + "if (" + name + ".length () > (" + Util.parseExpression (string.maxSize ()) + "))");
      stream.println (indent + "  throw new org.omg.CORBA.MARSHAL (0, org.omg.CORBA.CompletionStatus.COMPLETED_MAYBE);");
    }
    String entryName = entry.name ();
    if (entryName.equals ("string"))
      stream.println (indent + "ostream.write_string (" + name + ");");
    else if (entryName.equals ("wstring"))
      stream.println (indent + "ostream.write_wstring (" + name + ");");
    return index;
  } // write

  // From JavaGenerator
  ///////////////
} // class StringGen
