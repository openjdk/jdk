/*
 * Copyright (c) 1999, 2001, Oracle and/or its affiliates. All rights reserved.
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
// - Think about arrays (and sequences?) as members
//   - A sequence must be converted to an array, but a memory of the
//     max size must be retained.
// - After demarshalling an IOR, think about how to deal with the exceptions.
// - The demarshall method should be throwing a ClientException,
//   but should it, really?
// -D60929   <klr> Update for RTF2.4 changes
// -D61056   <klr> Use Util.helperName
// -D62023   <klr> Use corbaLevel in read/write generation
// -D59437   <daz> Modify read() to enit qualified name of value box helper.

import java.io.File;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import com.sun.tools.corba.se.idl.GenFileStream;
import com.sun.tools.corba.se.idl.EnumEntry;
import com.sun.tools.corba.se.idl.InterfaceEntry;
import com.sun.tools.corba.se.idl.PrimitiveEntry;
import com.sun.tools.corba.se.idl.SequenceEntry;
import com.sun.tools.corba.se.idl.StringEntry;
import com.sun.tools.corba.se.idl.StructEntry;
import com.sun.tools.corba.se.idl.SymtabEntry;
import com.sun.tools.corba.se.idl.TypedefEntry;
import com.sun.tools.corba.se.idl.ValueEntry;
import com.sun.tools.corba.se.idl.ValueBoxEntry;
import com.sun.tools.corba.se.idl.InterfaceState;

/**
 *
 **/
public class StructGen implements com.sun.tools.corba.se.idl.StructGen, JavaGenerator
{
  /**
   * Public zero-argument constructor.
   **/
  public StructGen ()
  {
  } // ctor

  /**
   * Constructor for ExceptionGen.
   **/
  protected StructGen (boolean exception)
  {
    thisIsReallyAnException = exception;
  } // ctor

  /**
   *
   **/
  public void generate (Hashtable symbolTable, StructEntry s, PrintWriter str)
  {
    this.symbolTable = symbolTable;
    this.s           = s;
    //init ();

    openStream ();
    if (stream == null)
      return;
    generateHelper ();
    generateHolder ();
    writeHeading ();
    writeBody ();
    writeClosing ();
    closeStream ();
    generateContainedTypes ();
  } // generate

  /**
   * Initialize members unique to this generator.
   **/
  protected void init ()
  {
  } // init

  /**
   *
   **/
  protected void openStream ()
  {
    stream = Util.stream (s, ".java");
  } // openStream

  /**
   *
   **/
  protected void generateHelper ()
  {
    ((Factories)Compile.compiler.factories ()).helper ().generate (symbolTable, s);
  } // generateHelper

  /**
   *
   **/
  protected void generateHolder ()
  {
    ((Factories)Compile.compiler.factories ()).holder ().generate (symbolTable, s);
  } // generateHolder

  /**
   *
   **/
  protected void writeHeading ()
  {
    Util.writePackage (stream, s);
    Util.writeProlog (stream, ((GenFileStream)stream).name ());

    if (s.comment () != null)
      s.comment ().generate ("", stream);

    stream.print ("public final class " + s.name ());
    if (thisIsReallyAnException)
      stream.print (" extends org.omg.CORBA.UserException");
    else
      stream.print(" implements org.omg.CORBA.portable.IDLEntity");
    stream.println ();
    stream.println ("{");
  } // writeHeading

  /**
   *
   **/
  protected void writeBody ()
  {
    writeMembers ();
    writeCtors ();
  } // writeBody

  /**
   *
   **/
  protected void writeClosing ()
  {
   stream.println ("} // class " + s.name ());
  } // writeClosing

  /**
   *
   **/
  protected void closeStream ()
  {
    stream.close ();
  } // closeStream

  /**
   *
   **/
  protected void generateContainedTypes ()
  {
    // Generate all of the contained types
    Enumeration e = s.contained ().elements ();
    while (e.hasMoreElements ())
    {
      SymtabEntry entry = (SymtabEntry)e.nextElement ();

      // Don't generate contained entries if they are sequences.
      // Sequences are unnamed and since they translate to arrays,
      // no classes are generated for them, not even holders in this
      // case since they cannot be accessed outside of this struct.

      if (!(entry instanceof SequenceEntry))
        entry.generate (symbolTable, stream);
    }
  } // generateContainedTypes

  /**
   *
   **/
  protected void writeMembers ()
  {
    // Write members and populate quality arrays
    int size = s.members ().size ();
    memberIsPrimitive = new boolean [size];
    memberIsInterface = new boolean [size];
    memberIsTypedef   = new boolean [size];
    for (int i = 0; i < s.members ().size (); ++i)
    {
      SymtabEntry member = (SymtabEntry)s.members ().elementAt (i);
      memberIsPrimitive[i] = member.type () instanceof PrimitiveEntry;
      memberIsInterface[i] = member.type () instanceof InterfaceEntry;
      memberIsTypedef[i]   = member.type () instanceof TypedefEntry;
      Util.fillInfo (member);
      // Transfer member comment to target <31jul1997>.
      if (member.comment () != null)
         member.comment ().generate ("  ", stream);
      Util.writeInitializer ("  public ", member.name (), "", member, stream);
    }
  } // writeMembers

  /**
   *
   **/
  protected void writeCtors ()
  {
    // Write default ctor
    stream.println ();
    stream.println ("  public " + s.name () + " ()");
    stream.println ("  {");
    // fixed mapping for exceptions
    if (thisIsReallyAnException)
        stream.println ("    super(" + s.name() + "Helper.id());");
    stream.println ("  } // ctor");
    writeInitializationCtor(true);
    if (thisIsReallyAnException) {
        // for exception according to mapping we should always
        // have a full constructor
        writeInitializationCtor(false);
    }
  }

  private void writeInitializationCtor(boolean init)
  {
    // Write initialization ctor
    if (!init || (s.members ().size () > 0))
    {
      stream.println ();
      stream.print ("  public " + s.name () + " (");
      boolean firstTime = true;
      if (!init) {
        stream.print ("String $reason");
        firstTime = false;
      }

      for (int i = 0; i < s.members ().size (); ++i)
      {
        SymtabEntry member = (SymtabEntry)s.members ().elementAt (i);
        if (firstTime)
          firstTime = false;
        else
          stream.print (", ");
        stream.print (Util.javaName (member) + " _" + member.name ());
      }
      stream.println (")");
      stream.println ("  {");
      // fixed mapping for exceptions
      if (thisIsReallyAnException) {
          if (init)
              stream.println ("    super(" + s.name() + "Helper.id());");
          else
              stream.println ("    super(" + s.name() + "Helper.id() + \"  \" + $reason);");
      }
      for (int i = 0; i < s.members ().size (); ++i)
      {
        SymtabEntry member = (SymtabEntry)s.members ().elementAt (i);
        stream.println ("    " + member.name () + " = _" + member.name () + ";");
      }
      stream.println ("  } // ctor");
    }
    stream.println ();
  } // writeInitializationCtor

  ///////////////
  // From JavaGenerator

  public int helperType (int index, String indent, TCOffsets tcoffsets, String name, SymtabEntry entry, PrintWriter stream)
  {
    TCOffsets innerOffsets = new TCOffsets ();
    innerOffsets.set (entry);
    int offsetForStruct = innerOffsets.currentOffset ();
    StructEntry s = (StructEntry)entry;
    String membersName = "_members" + index++;
    stream.println (indent + "org.omg.CORBA.StructMember[] " + membersName + " = new org.omg.CORBA.StructMember [" + s.members ().size () + "];");
    String tcOfMembers = "_tcOf" + membersName;
    stream.println (indent + "org.omg.CORBA.TypeCode " + tcOfMembers + " = null;");
    for (int i = 0; i < s.members ().size (); ++i)
    {
      TypedefEntry member = (TypedefEntry)s.members ().elementAt (i);
      String memberName = member.name ();
      // Generate and assign member TypeCode to tcofMembers
      index = ((JavaGenerator)member.generator ()).type (index, indent, innerOffsets, tcOfMembers, member, stream);
      stream.println (indent + membersName + '[' + i + "] = new org.omg.CORBA.StructMember (");
      stream.println (indent + "  \"" + Util.stripLeadingUnderscores (memberName) + "\",");
      stream.println (indent + "  " + tcOfMembers + ',');
      stream.println (indent + "  null);");
      int offsetSoFar = innerOffsets.currentOffset ();
      innerOffsets = new TCOffsets ();
      innerOffsets.set (entry);
      innerOffsets.bumpCurrentOffset (offsetSoFar - offsetForStruct);

    }
    tcoffsets.bumpCurrentOffset (innerOffsets.currentOffset ());
    // <54697>
    //stream.println (indent + name + " = org.omg.CORBA.ORB.init ().create_struct_tc (id (), \"" + Util.stripLeadingUnderscores (entry.name ()) + "\", " + membersName + ");");
    stream.println (indent + name + " = org.omg.CORBA.ORB.init ().create_" + (thisIsReallyAnException ? "exception" : "struct") + "_tc (" + Util.helperName (s, true) + ".id (), \"" + Util.stripLeadingUnderscores (entry.name ()) + "\", " + membersName + ");"); // <d61056>
    return index;
  } // helperType

  public int type (int index, String indent, TCOffsets tcoffsets, String name, SymtabEntry entry, PrintWriter stream) {
    stream.println (indent + name + " = " + Util.helperName (entry, true) + ".type ();"); // <d61056>
    return index;
  } // type

  public void helperRead (String entryName, SymtabEntry entry, PrintWriter stream)
  {
    stream.println ("    " + entryName + " value = new " + entryName + " ();");
    read (0, "    ", "value", entry, stream);
    stream.println ("    return value;");
  } // helperRead

  public int read (int index, String indent, String name, SymtabEntry entry, PrintWriter stream)
  {
    if (thisIsReallyAnException)
    {
      stream.println (indent + "// read and discard the repository ID");
      stream.println (indent + "istream.read_string ();");
    }

    Enumeration e = ((StructEntry)entry).members ().elements ();
    while (e.hasMoreElements ())
    {
      TypedefEntry member = (TypedefEntry)e.nextElement ();
      SymtabEntry  mType = member.type ();

      if (!member.arrayInfo ().isEmpty () || mType instanceof SequenceEntry ||
          mType instanceof PrimitiveEntry || mType instanceof StringEntry ||
          mType instanceof TypedefEntry)
        index = ((JavaGenerator)member.generator ()).read (index, indent, name + '.' + member.name (), member, stream);
      else if (mType instanceof ValueBoxEntry)
      {
        // call read_value instead of Helper.read for the value
        Vector st = ((ValueBoxEntry) mType).state ();
        TypedefEntry vbMember = ((InterfaceState) st.elementAt (0)).entry;
        SymtabEntry vbType = vbMember.type ();

        String jName = null;
        String jHelper = null;

        if (vbType instanceof SequenceEntry || vbType instanceof StringEntry ||
            !vbMember.arrayInfo ().isEmpty ())
        {
          jName = Util.javaName (vbType);      // name of mapped Java type
          // <d59437> REVISIT.  Typename info. now correct for value boxes, so
          // these two cases may be obsolete.  See UnionGen.read().
          //jHelper = Util.helperName (vbType, false);  // <d61056>
          jHelper = Util.helperName (mType, true);
              }
        else
        {
          jName = Util.javaName (mType);       // name of mapped Java class
          // <d59437>
          //jHelper = Util.helperName (mType, false);  // <d61056>
          jHelper = Util.helperName (mType, true);
        }
        // <d62023> Call xHelper.read() for valueboxes for RTF2.4
        if (Util.corbaLevel (2.4f, 99.0f))
          stream.println (indent + name + '.' + member.name () + " = (" + jName + ") " + jHelper + ".read (istream);");
        else
          stream.println (indent + name + '.' + member.name () + " = (" + jName + ") ((org.omg.CORBA_2_3.portable.InputStream)istream).read_value (" + jHelper + ".get_instance ());"); // <d60929> <d61056>
      }
      // <d62023-klr> for corbaLevel 2.4 and up, use Helper.read like
      // everything else
      else if ((mType instanceof ValueEntry) &&
          !Util.corbaLevel (2.4f, 99.0f)) // <d62023>
      {
        // call read_value instead of Helper.read for the value
        stream.println (indent + name + '.' + member.name () + " = (" + Util.javaName (mType) + ") ((org.omg.CORBA_2_3.portable.InputStream)istream).read_value (" + Util.helperName (mType, false) + ".get_instance ());"); // <d60929> // <d61056>
      }
      else
        stream.println (indent + name + '.' + member.name () + " = " + Util.helperName (member.type (), true) + ".read (istream);"); // <d61056>
    }
    return index;
  } // read

  public void helperWrite (SymtabEntry entry, PrintWriter stream)
  {
    write (0, "    ", "value", entry, stream);
  } // helperWrite

  public int write (int index, String indent, String name, SymtabEntry entry, PrintWriter stream)
  {
    if (thisIsReallyAnException)
    {
      stream.println (indent + "// write the repository ID");
      stream.println (indent + "ostream.write_string (id ());");
    }

    Vector members = ((StructEntry)entry).members ();
    for (int i = 0; i < members.size (); ++i)
    {
      TypedefEntry member = (TypedefEntry)members.elementAt (i);
      SymtabEntry  mType = member.type ();

      if (!member.arrayInfo ().isEmpty () || mType instanceof SequenceEntry ||
           mType instanceof TypedefEntry || mType instanceof PrimitiveEntry ||
           mType instanceof StringEntry)
        index = ((JavaGenerator)member.generator ()).write (index, "    ", name + '.' + member.name (), member, stream);

      // <d62023-klr> for corbaLevel 2.4 and up, use Helper.write like
      //                everything else
      else if ((mType instanceof ValueEntry || mType instanceof ValueBoxEntry)
                && !Util.corbaLevel (2.4f, 99.0f)) { // <d62023>
        stream.println (indent + "((org.omg.CORBA_2_3.portable.OutputStream)ostream).write_value ((java.io.Serializable) " // <d60929>
                        + name + '.' + member.name () + ", "
                        + Util.helperName (member.type (), true) // <d61056>
                        + ".get_instance ());"); // <d61056>
      }
      else
        stream.println (indent + Util.helperName (member.type (), true) + ".write (ostream, " + name + '.' + member.name () + ");"); // <d61056>
    }
    return index;
  } // write

  // From JavaGenerator
  ///////////////

  protected Hashtable   symbolTable = null;
  protected StructEntry s           = null;
  protected PrintWriter stream      = null;

  protected boolean     thisIsReallyAnException = false;
  private   boolean[]   memberIsPrimitive;
  private   boolean[]   memberIsInterface;
  private   boolean[]   memberIsTypedef;
} // class StructGen
