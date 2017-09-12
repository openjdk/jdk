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
// -F46082.51<daz> Remove -stateful feature; javaStatefulName() obsolete.
// -D61056   <klr> Use Util.helperName

import java.io.PrintWriter;
import java.util.Hashtable;

import com.sun.tools.corba.se.idl.InterfaceEntry;
import com.sun.tools.corba.se.idl.PrimitiveEntry;
import com.sun.tools.corba.se.idl.SequenceEntry;
import com.sun.tools.corba.se.idl.StringEntry;
import com.sun.tools.corba.se.idl.SymtabEntry;

import com.sun.tools.corba.se.idl.constExpr.Expression;

/**
 *
 **/
public class SequenceGen implements com.sun.tools.corba.se.idl.SequenceGen, JavaGenerator
{
  /**
   * Public zero-argument constructor.
   **/
  public SequenceGen ()
  {
  } // ctor

  /**
   *
   **/
  public void generate (Hashtable symbolTable, SequenceEntry s, PrintWriter stream)
  {
  } // generator

  ///////////////
  // From JavaGenerator

  public int helperType (int index, String indent, TCOffsets tcoffsets, String name, SymtabEntry entry, PrintWriter stream)
  {
    int offsetOfType = tcoffsets.offset (entry.type ().fullName ());
    if (offsetOfType >= 0)
    {
      // This code uses the deprecated create_recursive_sequence_tc()
      // It should be eliminated when the API is removed from the ORB class
      // Regardles, this code will not be emitted since updated emitters invoke
      // method type() below instead of helperType() when handling sequences

      // This is a recursive sequence
      tcoffsets.set (null);
      Expression maxSize = ((SequenceEntry)entry).maxSize ();
      if (maxSize == null)
        stream.println (indent + name + " = org.omg.CORBA.ORB.init ().create_recursive_sequence_tc (0, " + (offsetOfType - tcoffsets.currentOffset ()) + ");");
      else
        stream.println (indent + name + " = org.omg.CORBA.ORB.init ().create_recursive_sequence_tc (" + Util.parseExpression (maxSize) + ", " + (offsetOfType - tcoffsets.currentOffset ()) + ");");
      tcoffsets.bumpCurrentOffset (4); // add indirection field
    }
    else
    {
      // This is a normal sequence
      tcoffsets.set (entry);
      index = ((JavaGenerator)entry.type ().generator ()).helperType (index + 1, indent, tcoffsets, name, entry.type (), stream);
      Expression maxSize = ((SequenceEntry)entry).maxSize ();
      if (maxSize == null)
        stream.println (indent + name + " = org.omg.CORBA.ORB.init ().create_sequence_tc (0, " + name + ");");
      else
        stream.println (indent + name + " = org.omg.CORBA.ORB.init ().create_sequence_tc (" + Util.parseExpression (maxSize) + ", " + name + ");");
    }
    tcoffsets.bumpCurrentOffset (4); // add on the seq max size
    return index;
  } // helperType

  public int type (int index, String indent, TCOffsets tcoffsets, String name, SymtabEntry entry, PrintWriter stream) {
    int offsetOfType = tcoffsets.offset (entry.type ().fullName ());
    if (offsetOfType >= 0)
    {
      // This is a recursive sequence
      tcoffsets.set (null);

      // Need to fix later: how to get repositoryId of IDL type containing this sequence?
      // entry.repositoryID().ID() returns empty string and
      // Util.javaQualifiedName(entry) returns internal name which is not valid repId

      stream.println (indent + name + " = org.omg.CORBA.ORB.init ().create_recursive_tc (" + "\"\"" + ");");
      tcoffsets.bumpCurrentOffset (4); // add indirection field
    }
    else
    {
      // This is a normal sequence
      tcoffsets.set (entry);
      index = ((JavaGenerator)entry.type ().generator ()).type (index + 1, indent, tcoffsets, name, entry.type (), stream);
      Expression maxSize = ((SequenceEntry)entry).maxSize ();
      if (maxSize == null)
        stream.println (indent + name + " = org.omg.CORBA.ORB.init ().create_sequence_tc (0, " + name + ");");
      else
        stream.println (indent + name + " = org.omg.CORBA.ORB.init ().create_sequence_tc (" + Util.parseExpression (maxSize) + ", " + name + ");");
    }
    //stream.println (indent + name + " = " + Util.helperName (entry, true) + ".type ();"); // <d61056>
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
    SequenceEntry seq = (SequenceEntry)entry;
    String length = "_len" + index++;
    stream.println (indent + "int " + length + " = istream.read_long ();");
    if (seq.maxSize () != null)
    {
      stream.println (indent + "if (" + length + " > (" + Util.parseExpression (seq.maxSize ()) + "))");
      stream.println (indent + "  throw new org.omg.CORBA.MARSHAL (0, org.omg.CORBA.CompletionStatus.COMPLETED_MAYBE);");
    }
    String seqOfName;
    try
    {
      seqOfName = Util.sansArrayInfo ((String)seq.dynamicVariable (Compile.typedefInfo));
    }
    catch (NoSuchFieldException e)
    {
      seqOfName = seq.name ();
    }
    int startArray = seqOfName.indexOf ('[');
    String arrayDcl = seqOfName.substring (startArray);
    seqOfName = seqOfName.substring (0, startArray);

    // For interfaces having state, e.g., valuetypes.
    SymtabEntry seqOfEntry = (SymtabEntry)Util.symbolTable.get (seqOfName.replace ('.', '/'));
    if (seqOfEntry != null && seqOfEntry instanceof InterfaceEntry && ((InterfaceEntry)seqOfEntry).state () != null)
      // <f46082.51> Remove -stateful feature; javaStatefulName() obsolete.
      //seqOfName = Util.javaStatefulName ((InterfaceEntry)seqOfEntry);
      seqOfName = Util.javaName ((InterfaceEntry)seqOfEntry);

    arrayDcl = arrayDcl.substring (2);
    stream.println (indent + name + " = new " + seqOfName + '[' + length + ']' + arrayDcl + ';');
    if (seq.type () instanceof PrimitiveEntry)
      // <d61961> Check for CORBA::Principal, too
      //if (seq.type ().name ().equals ("any") || seq.type ().name ().equals ("TypeCode"))
      if (seq.type ().name ().equals ("any") ||
          seq.type ().name ().equals ("TypeCode") ||
          seq.type ().name ().equals ("Principal"))
      {
        String loopIndex = "_o" + index;
        stream.println (indent + "for (int " + loopIndex + " = 0;" + loopIndex + " < " + name + ".length; ++" + loopIndex + ')');
        stream.println (indent + "  " + name + '[' + loopIndex + "] = istream.read_" + seq.type ().name () + " ();");
      }
      else
      { // special case for ValueBox: if name is "xxx tmp", drop xxx
        String varName = name;
        int nameIndex = varName.indexOf (' ');
        if ( nameIndex != -1 )
          varName = varName.substring( nameIndex + 1 );
        stream.println (indent + "istream.read_" + Util.collapseName (entry.type ().name ()) + "_array (" + varName + ", 0, " + length + ");");
      }
    else if (entry.type () instanceof StringEntry)
    {
      String loopIndex = "_o" + index;
      stream.println (indent + "for (int " + loopIndex + " = 0;" + loopIndex + " < " + name + ".length; ++" + loopIndex + ')');
      stream.println (indent + "  " + name + '[' + loopIndex + "] = istream.read_" + seq.type ().name () + " ();");
    }
    else if (entry.type () instanceof SequenceEntry)
    {
      String loopIndex = "_o" + index;
      stream.println (indent + "for (int " + loopIndex + " = 0;" + loopIndex + " < " + name + ".length; ++" + loopIndex + ')');
      stream.println (indent + '{');
      index = ((JavaGenerator)seq.type ().generator ()).read (index, indent + "  ", name + '[' + loopIndex + ']', seq.type (), stream);
      stream.println (indent + '}');
    }
    else
    { // special case for ValueBox: if name is "xxx tmp", drop xxx
      String varName = name;
      int nameIndex = varName.indexOf (' ');
      if ( nameIndex != -1 )
        varName = varName.substring( nameIndex + 1 );
      String loopIndex = "_o" + index;
      stream.println (indent + "for (int " + loopIndex + " = 0;" + loopIndex + " < " + varName + ".length; ++" + loopIndex + ')');
      stream.println (indent + "  " + varName + '[' + loopIndex + "] = " + Util.helperName (seq.type (), true) + ".read (istream);"); // <d61056>
    }
    return index;
  } // read

  public int write (int index, String indent, String name, SymtabEntry entry, PrintWriter stream)
  {
    SequenceEntry seq = (SequenceEntry)entry;
    if (seq.maxSize () != null)
    {
      stream.println (indent + "if (" + name + ".length > (" + Util.parseExpression (seq.maxSize ()) + "))");
      stream.println (indent + "  throw new org.omg.CORBA.MARSHAL (0, org.omg.CORBA.CompletionStatus.COMPLETED_MAYBE);");
    }
    stream.println (indent + "ostream.write_long (" + name + ".length);");
    if (entry.type () instanceof PrimitiveEntry)
      // <d61961> Check for CORBA::Principal, too.
      //if (entry.type ().name ().equals ("any") || entry.type ().name ().equals ("TypeCode"))
      if (entry.type ().name ().equals ("any") ||
          entry.type ().name ().equals ("TypeCode") ||
          entry.type ().name ().equals ("Principal"))
      {
        String loopIndex = "_i" + index++;
        stream.println (indent + "for (int " + loopIndex + " = 0;" + loopIndex + " < " + name + ".length; ++" + loopIndex + ')');
        stream.println (indent + "  ostream.write_" + seq.type ().name () + " (" + name + '[' + loopIndex + "]);");
      }
      else
        stream.println (indent + "ostream.write_" + Util.collapseName (entry.type ().name ()) + "_array (" + name + ", 0, " + name + ".length);");
    else if (entry.type () instanceof StringEntry)
    {
      String loopIndex = "_i" + index++;
      stream.println (indent + "for (int " + loopIndex + " = 0;" + loopIndex + " < " + name + ".length; ++" + loopIndex + ')');
      stream.println (indent + "  ostream.write_" + seq.type ().name () + " (" + name + '[' + loopIndex + "]);");
    }
    else if (entry.type () instanceof SequenceEntry)
    {
      String loopIndex = "_i" + index++;
      stream.println (indent + "for (int " + loopIndex + " = 0;" + loopIndex + " < " + name + ".length; ++" + loopIndex + ')');
      stream.println (indent + '{');
      index = ((JavaGenerator)seq.type ().generator ()).write (index, indent + "  ", name + '[' + loopIndex + ']', seq.type (), stream);
      stream.println (indent + '}');
    }
    else
    {
      String loopIndex = "_i" + index++;
      stream.println (indent + "for (int " + loopIndex + " = 0;" + loopIndex + " < " + name + ".length; ++" + loopIndex + ')');
      stream.println (indent + "  " + Util.helperName (seq.type (), true) + ".write (ostream, " + name + '[' + loopIndex + "]);"); // <d61056>
    }
    return index;
  } // write

  // From JavaGenerator
  ///////////////
} // class SequenceGen
