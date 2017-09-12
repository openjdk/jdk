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

import java.io.PrintWriter;
import java.util.Hashtable;

import com.sun.tools.corba.se.idl.PrimitiveEntry;
import com.sun.tools.corba.se.idl.SymtabEntry;

/**
 *
 **/
public class PrimitiveGen implements com.sun.tools.corba.se.idl.PrimitiveGen, JavaGenerator
{
  /**
   * Public zero-argument constructor.
   **/
  public PrimitiveGen ()
  {
  } // ctor

  /**
   * This method should never be called; this class exists for
   * the JavaGenerator interface.
   **/
  public void generate (Hashtable symbolTable, PrimitiveEntry e, PrintWriter stream)
  {
  } // generate

  ///////////////
  // From JavaGenerator

  public int helperType (int index, String indent, TCOffsets tcoffsets, String name, SymtabEntry entry, PrintWriter stream)
  {
    return type (index, indent, tcoffsets, name, entry, stream);
  } // helperType

  public int type (int index, String indent, TCOffsets tcoffsets, String name, SymtabEntry entry, PrintWriter stream) {
    tcoffsets.set (entry);
    String emit = "tk_null";
    if (entry.name ().equals ("null"))
      emit = "tk_null";
    else if (entry.name ().equals ("void"))
      emit = "tk_void";
    else if (entry.name ().equals ("short"))
      emit = "tk_short";
    else if (entry.name ().equals ("long"))
      emit = "tk_long";
    else if (entry.name ().equals ("long long"))
      emit = "tk_longlong";
    else if (entry.name ().equals ("unsigned short"))
      emit = "tk_ushort";
    else if (entry.name ().equals ("unsigned long"))
      emit = "tk_ulong";
    else if (entry.name ().equals ("unsigned long long"))
      emit = "tk_ulonglong";
    else if (entry.name ().equals ("float"))
      emit = "tk_float";
    else if (entry.name ().equals ("double"))
      emit = "tk_double";
    else if (entry.name ().equals ("boolean"))
      emit = "tk_boolean";
    else if (entry.name ().equals ("char"))
      emit = "tk_char";
    else if (entry.name ().equals ("octet"))
      emit = "tk_octet";
    else if (entry.name ().equals ("any"))
      emit = "tk_any";
    else if (entry.name ().equals ("TypeCode"))
      emit = "tk_TypeCode";
    else if (entry.name ().equals ("wchar"))
      emit = "tk_wchar";
    else if (entry.name ().equals ("Principal")) // <d61961>
      emit = "tk_Principal";
    else if (entry.name ().equals ("wchar"))
      emit = "tk_wchar";
    stream.println (indent + name + " = org.omg.CORBA.ORB.init ().get_primitive_tc (org.omg.CORBA.TCKind." + emit + ");");
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
    stream.println (indent + name + " = " + "istream.read_" + Util.collapseName (entry.name ()) + " ();");
    return index;
  } // read

  public int write (int index, String indent, String name, SymtabEntry entry, PrintWriter stream)
  {
    stream.println (indent + "ostream.write_" + Util.collapseName (entry.name ()) + " (" + name + ");");
    return index;
  } // write

  // From JavaGenerator
  ///////////////
} // class PrimitiveGen
