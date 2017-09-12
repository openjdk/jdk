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
// -11aug1997<daz> No modification: comments for type_defs will appear in
//  helper, holder classes as a result of modifications to routines
//  makeHelper(), makeHolder() in class com.sun.tools.corba.se.idl.toJava.Util.
// -F46082.51<daz> Remove -stateful feature; javaStatefulName() obsolete.
// -D61056   <klr> Use Util.helperName

import java.io.PrintWriter;

import java.util.Enumeration;
import java.util.Hashtable;

import com.sun.tools.corba.se.idl.InterfaceEntry;
import com.sun.tools.corba.se.idl.InterfaceState;
import com.sun.tools.corba.se.idl.PrimitiveEntry;
import com.sun.tools.corba.se.idl.SequenceEntry;
import com.sun.tools.corba.se.idl.StringEntry;
import com.sun.tools.corba.se.idl.StructEntry;
import com.sun.tools.corba.se.idl.SymtabEntry;
import com.sun.tools.corba.se.idl.TypedefEntry;
import com.sun.tools.corba.se.idl.UnionEntry;

import com.sun.tools.corba.se.idl.constExpr.Expression;

// Notes:

/**
 *
 **/
public class TypedefGen implements com.sun.tools.corba.se.idl.TypedefGen, JavaGenerator
{
  /**
   * Public zero-argument constructor.
   **/
  public TypedefGen ()
  {
  } // ctor

  /**
   *
   **/
  public void generate (Hashtable symbolTable, TypedefEntry t, PrintWriter stream)
  {
    this.symbolTable = symbolTable;
    this.t           = t;

    if (t.arrayInfo ().size () > 0 || t.type () instanceof SequenceEntry)
      generateHolder ();
    generateHelper ();
  } // generator

  /**
   *
   **/
  protected void generateHolder ()
  {
    ((Factories)Compile.compiler.factories ()).holder ().generate (symbolTable, t);
  }

  /**
   *
   **/
  protected void generateHelper ()
  {
    ((Factories)Compile.compiler.factories ()).helper ().generate (symbolTable, t);
  }

  ///////////////
  // From JavaGenerator

  private boolean inStruct (TypedefEntry entry)
  {
    boolean inStruct = false;
    if (entry.container () instanceof StructEntry || entry.container () instanceof UnionEntry)
      inStruct = true;
    else if (entry.container () instanceof InterfaceEntry)
    {
      InterfaceEntry i = (InterfaceEntry)entry.container ();
      if (i.state () != null)
      {
        Enumeration e = i.state ().elements ();
        while (e.hasMoreElements ())
          if (((InterfaceState)e.nextElement ()).entry == entry)
          {
            inStruct = true;
            break;
          }
      }
    }
    return inStruct;
  } // inStruct

  public int helperType (int index, String indent, TCOffsets tcoffsets, String name, SymtabEntry entry, PrintWriter stream)
  {
    TypedefEntry td = (TypedefEntry)entry;
    boolean inStruct = inStruct (td);
    if (inStruct)
      tcoffsets.setMember (entry);
    else
      tcoffsets.set (entry);

    // Print the base types typecode
    index = ((JavaGenerator)td.type ().generator ()).type (index, indent, tcoffsets, name, td.type (), stream);

    if (inStruct && td.arrayInfo ().size () != 0)
      tcoffsets.bumpCurrentOffset (4); // for array length field

    // Print the array typecodes (if there are any)
    int dimensions = td.arrayInfo ().size ();
    for (int i = 0; i < dimensions; ++i)
    {
      String size = Util.parseExpression ((Expression)td.arrayInfo ().elementAt (i));
      stream.println (indent + name + " = org.omg.CORBA.ORB.init ().create_array_tc (" + size + ", " + name + " );");
    }

    // If this typedef describes a struct/union member, don't put it
    // in an alias typedef; otherwise that's where it belongs.
    if (!inStruct)
      // <54697>
      //stream.println (indent + name + " = org.omg.CORBA.ORB.init ().create_alias_tc (id (), \"" + Util.stripLeadingUnderscores (td.name ()) + "\", " + name + ");");
      stream.println (indent + name + " = org.omg.CORBA.ORB.init ().create_alias_tc (" + Util.helperName (td, true) + ".id (), \"" + Util.stripLeadingUnderscores (td.name ()) + "\", " + name + ");"); // <d61056>

    return index;
  } // helperType

  public int type (int index, String indent, TCOffsets tcoffsets, String name, SymtabEntry entry, PrintWriter stream)
  {
    // The type() method is invoked from other emitters instead of when an IDL
    // typedef statement is being processed.  Code generated is identical minus the
    // generation of a create_alias_tc() which is required for IDL typedef's but not
    // needed when typedef is being processed as a member of struct/union/valuetype.

    return helperType( index, indent, tcoffsets, name, entry, stream);
  } // type

  public void helperRead (String entryName, SymtabEntry entry, PrintWriter stream)
  {
    Util.writeInitializer ("    ", "value", "", entry, stream);
    read (0, "    ", "value", entry, stream);
    stream.println ("    return value;");
  } // helperRead

  public void helperWrite (SymtabEntry entry, PrintWriter stream)
  {
    write (0, "    ", "value", entry, stream);
  } // helperWrite

  public int read (int index, String indent, String name, SymtabEntry entry, PrintWriter stream)
  {
    TypedefEntry td = (TypedefEntry)entry;
    String modifier = Util.arrayInfo (td.arrayInfo ());
    if (!modifier.equals (""))
    {
      // arrayInfo is a vector of Expressions which indicate the
      // number of array dimensions for this typedef.  But what if
      // this is a typedef of a sequence?
      // The `new' statement being generated must know the full
      // number of brackets.  That can be found in td.info.
      // For instance:
      // typedef sequence<short> A[10][10];
      // void proc (out A a);
      // typeModifier = "[10][10]"
      // td.info    = "short[][][]";
      // The first new statement generated is:
      // a.value = new short[10][][];
      // Note that the 3 sets of brackets come from td.info, not
      // arrayInfo;
      // The second new statement generated is:
      // a.value[_i1] = new short[10][];
      // ------------     ---- ------
      //    \           \    \
      //    name      baseName   arrayDcl
      int closingBrackets = 0;
      String loopIndex = "";
      String baseName;
      try
      {
        baseName = (String)td.dynamicVariable (Compile.typedefInfo);
      }
      catch (NoSuchFieldException e)
      {
        baseName = td.name ();
      }
      int startArray = baseName.indexOf ('[');
      String arrayDcl = Util.sansArrayInfo (baseName.substring (startArray)) + "[]"; // Add an extra set because the first gets stripped off in the loop.
      baseName = baseName.substring (0, startArray);

      // For interfaces having state, e.g., valuetypes.
      SymtabEntry baseEntry = (SymtabEntry)Util.symbolTable.get (baseName.replace ('.', '/'));
      if (baseEntry instanceof InterfaceEntry && ((InterfaceEntry)baseEntry).state () != null)
        // <f46082.51> Remove -stateful feature; javaStatefulName() obsolete.
        //baseName = Util.javaStatefulName ((InterfaceEntry)baseEntry);
        baseName = Util.javaName ((InterfaceEntry)baseEntry);

      int end1stArray;
      while (!modifier.equals (""))
      {
        int rbracket = modifier.indexOf (']');
        String size = modifier.substring (1, rbracket);
        end1stArray = arrayDcl.indexOf (']');
        arrayDcl = '[' + size + arrayDcl.substring (end1stArray + 2);
        stream.println (indent + name + " = new " + baseName + arrayDcl + ';');
        loopIndex = "_o" + index++;
        stream.println (indent + "for (int " + loopIndex + " = 0;" + loopIndex + " < (" + size + "); ++" + loopIndex + ')');
        stream.println (indent + '{');
        ++closingBrackets;
        modifier = modifier.substring (rbracket + 1);
        indent = indent + "  ";
        name = name + '[' + loopIndex + ']';
      }
      end1stArray = arrayDcl.indexOf (']');
      if (td.type () instanceof SequenceEntry || td.type () instanceof PrimitiveEntry || td.type () instanceof StringEntry)
        index = ((JavaGenerator)td.type ().generator ()).read (index, indent, name, td.type (), stream);
      else if (td.type () instanceof InterfaceEntry && td.type ().fullName ().equals ("org/omg/CORBA/Object"))
        stream.println (indent + name + " = istream.read_Object ();");
      else
        stream.println (indent + name + " = " + Util.helperName (td.type (), true) + ".read (istream);"); // <d61056>
      for (int i = 0; i < closingBrackets; ++i)
      {
        indent = indent.substring (2);
        stream.println (indent + '}');
      }
    }
    else
    {
      SymtabEntry tdtype = Util.typeOf (td.type ());
      if (tdtype instanceof SequenceEntry || tdtype instanceof PrimitiveEntry || tdtype instanceof StringEntry)
        index = ((JavaGenerator)tdtype.generator ()).read (index, indent, name, tdtype, stream);
      else if (tdtype instanceof InterfaceEntry && tdtype.fullName ().equals ("org/omg/CORBA/Object"))
        stream.println (indent + name + " = istream.read_Object ();");
      else
        stream.println (indent + name + " = " + Util.helperName (tdtype, true) + ".read (istream);"); // <d61056>
    }
    return index;
  } // read

  public int write (int index, String indent, String name, SymtabEntry entry, PrintWriter stream)
  {
    TypedefEntry td = (TypedefEntry)entry;
    String modifier = Util.arrayInfo (td.arrayInfo ());
    if (!modifier.equals (""))
    {
      int closingBrackets = 0;
      String loopIndex = "";
      while (!modifier.equals (""))
      {
        int rbracket = modifier.indexOf (']');
        String size = modifier.substring (1, rbracket);
        stream.println (indent + "if (" + name + ".length != (" + size + "))");
        stream.println (indent + "  throw new org.omg.CORBA.MARSHAL (0, org.omg.CORBA.CompletionStatus.COMPLETED_MAYBE);");
        loopIndex = "_i" + index++;
        stream.println (indent + "for (int " + loopIndex + " = 0;" + loopIndex + " < (" + size + "); ++" + loopIndex + ')');
        stream.println (indent + '{');
        ++closingBrackets;
        modifier = modifier.substring (rbracket + 1);
        indent = indent + "  ";
        name = name + '[' + loopIndex + ']';
      }
      if (td.type () instanceof SequenceEntry || td.type () instanceof PrimitiveEntry || td.type () instanceof StringEntry)
        index = ((JavaGenerator)td.type ().generator ()).write (index, indent, name, td.type (), stream);
      else if (td.type () instanceof InterfaceEntry && td.type ().fullName ().equals ("org/omg/CORBA/Object"))
        stream.println (indent + "ostream.write_Object (" + name + ");");
      else
        stream.println (indent + Util.helperName (td.type (), true) + ".write (ostream, " + name + ");"); // <d61056>
      for (int i = 0; i < closingBrackets; ++i)
      {
        indent = indent.substring (2);
        stream.println (indent + '}');
      }
    }
    else
    {
      SymtabEntry tdtype = Util.typeOf (td.type ());
      if (tdtype instanceof SequenceEntry || tdtype instanceof PrimitiveEntry || tdtype instanceof StringEntry)
        index = ((JavaGenerator)tdtype.generator ()).write (index, indent, name, tdtype, stream);
      else if (tdtype instanceof InterfaceEntry && tdtype.fullName ().equals ("org/omg/CORBA/Object"))
        stream.println (indent + "ostream.write_Object (" + name + ");");
      else
        stream.println (indent + Util.helperName (tdtype, true) + ".write (ostream, " + name + ");"); // <d61056>
    }
    return index;
  } // write

  // From JavaGenerator
  ////////////////

  protected Hashtable     symbolTable = null;
  protected TypedefEntry  t           = null;
} // class TypedefGen
