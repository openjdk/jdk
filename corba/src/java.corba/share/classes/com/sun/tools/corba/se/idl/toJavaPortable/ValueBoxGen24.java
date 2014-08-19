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
// -D62023  <klr> Update for Java 2.4 RTF

import java.io.File;
import java.io.PrintWriter;
import java.util.Hashtable;
import java.util.Enumeration;
import java.util.Vector;

import com.sun.tools.corba.se.idl.GenFileStream;
import com.sun.tools.corba.se.idl.InterfaceEntry;
import com.sun.tools.corba.se.idl.SymtabEntry;
import com.sun.tools.corba.se.idl.TypedefEntry;
import com.sun.tools.corba.se.idl.ValueEntry;
import com.sun.tools.corba.se.idl.ValueBoxEntry;
import com.sun.tools.corba.se.idl.InterfaceState;
import com.sun.tools.corba.se.idl.MethodEntry;
import com.sun.tools.corba.se.idl.PrimitiveEntry;
import com.sun.tools.corba.se.idl.SequenceEntry;
import com.sun.tools.corba.se.idl.StringEntry;

/**
 *
 **/
public class ValueBoxGen24 extends ValueBoxGen
{
  /**
   * Public zero-argument constructor.
   **/
  public ValueBoxGen24 ()
  {
  } // ctor

  /**
   * <d62023> - Move from helper to mapped class
   **/
  protected void writeTruncatable () // <d60929>
  {
      stream.print   ("  private static String[] _truncatable_ids = {");
      stream.println (Util.helperName(v, true) + ".id ()};");
      stream.println ();
      stream.println ("  public String[] _truncatable_ids() {");
      stream.println ("    return _truncatable_ids;");
      stream.println ("  }");
      stream.println ();
  } // writeTruncatable


  /**
   * <d62023>
   **/
  public void helperRead (String entryName, SymtabEntry entry, PrintWriter stream)
  {
    stream.println ("    if (!(istream instanceof org.omg.CORBA_2_3.portable.InputStream)) {");
    stream.println ("      throw new org.omg.CORBA.BAD_PARAM(); }");
    stream.println ("    return (" + entryName +") ((org.omg.CORBA_2_3.portable.InputStream) istream).read_value (_instance);");
    stream.println ("  }");
    stream.println ();

    // done with "read", now do "read_value with real marshalling code.

    stream.println ("  public java.io.Serializable read_value (org.omg.CORBA.portable.InputStream istream)"); // <d60929>
    stream.println ("  {");

    String indent = "    ";
    Vector vMembers = ((ValueBoxEntry) entry).state ();
    TypedefEntry member = ((InterfaceState) vMembers.elementAt (0)).entry;
    SymtabEntry mType = member.type ();
    if (mType instanceof PrimitiveEntry ||
        mType instanceof SequenceEntry ||
        mType instanceof TypedefEntry ||
        mType instanceof StringEntry ||
        !member.arrayInfo ().isEmpty ()) {
      stream.println (indent + Util.javaName (mType) + " tmp;");
      ((JavaGenerator)member.generator ()).read (0, indent, "tmp", member, stream);
    }
    else
      stream.println (indent + Util.javaName (mType) + " tmp = " +
                      Util.helperName ( mType, true ) + ".read (istream);");
    if (mType instanceof PrimitiveEntry)
      stream.println (indent + "return new " + entryName + " (tmp);");
    else
      stream.println (indent + "return (java.io.Serializable) tmp;");
  } // helperRead

  /**
   * <d62023>
   **/
  public void helperWrite (SymtabEntry entry, PrintWriter stream)
  {
    stream.println ("    if (!(ostream instanceof org.omg.CORBA_2_3.portable.OutputStream)) {");
    stream.println ("      throw new org.omg.CORBA.BAD_PARAM(); }");
    stream.println ("    ((org.omg.CORBA_2_3.portable.OutputStream) ostream).write_value (value, _instance);");
    stream.println ("  }");
    stream.println ();

    // done with "write", now do "write_value with real marshalling code.

    stream.println ("  public void write_value (org.omg.CORBA.portable.OutputStream ostream, java.io.Serializable value)");
    stream.println ("  {");

    String entryName = Util.javaName(entry);
    stream.println ("    if (!(value instanceof " + entryName + ")) {");
    stream.println ("      throw new org.omg.CORBA.MARSHAL(); }");
    stream.println ("    " + entryName + " valueType = (" + entryName + ") value;");
    write (0, "    ", "valueType", entry, stream);
  } // helperWrite

  /**
   * <d62023>
   **/
  public int write (int index, String indent, String name, SymtabEntry entry, PrintWriter stream)
  {
    Vector vMembers = ( (ValueEntry) entry ).state ();
    TypedefEntry member = ((InterfaceState) vMembers.elementAt (0)).entry;
    SymtabEntry mType = member.type ();

    if (mType instanceof PrimitiveEntry || !member.arrayInfo ().isEmpty ())
      index = ((JavaGenerator)member.generator ()).write (index, indent, name + ".value", member, stream);
    else if (mType instanceof SequenceEntry || mType instanceof StringEntry || mType instanceof TypedefEntry || !member.arrayInfo ().isEmpty ())
      index = ((JavaGenerator)member.generator ()).write (index, indent, name, member, stream);
    else
      stream.println (indent + Util.helperName (mType, true) + ".write (ostream, " + name + ");"); // <d61056>
    return index;
  } // write
}
