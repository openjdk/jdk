/*
 * Copyright (c) 1999, 2004, Oracle and/or its affiliates. All rights reserved.
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
// -F46082.51<daz> Remove -stateful feature.
// -D57118   <klr> Fix "narrow" in helper for abstract interface
// -D58889   <klr> re-Fix "narrow" in helper for abstract interface
// -D59383   <klr> 'get_class' in value helper returns value class, not helper.
// -D59413   <klr> Remove Helper interface references for non-value types.
// -D59435   <klr> Remove read_Object, write_Object completely.
// -D59418   <klr> Move read_Value, write_Value to generator's helperRead.

import java.io.PrintWriter;

import java.util.Enumeration;
import java.util.Vector;

import com.sun.tools.corba.se.idl.GenFileStream;
import com.sun.tools.corba.se.idl.InterfaceEntry;
import com.sun.tools.corba.se.idl.MethodEntry;
import com.sun.tools.corba.se.idl.ParameterEntry;
import com.sun.tools.corba.se.idl.SymtabEntry;
import com.sun.tools.corba.se.idl.ValueEntry;
import com.sun.tools.corba.se.idl.ValueBoxEntry;
import com.sun.tools.corba.se.idl.TypedefEntry;
import com.sun.tools.corba.se.idl.InterfaceState;
import com.sun.tools.corba.se.idl.PrimitiveEntry;
import com.sun.tools.corba.se.idl.StructEntry;

/**
 *
 **/
public class Helper implements AuxGen
{
  /**
   * Public zero-argument constructor.
   **/
  public Helper ()
  {
  } // ctor

  /**
   * Generate the helper class.  Provides general algorithm
   * for auxiliary binding generation:
   *
   * 1.) Initialize symbol table and symbol table entry members,
   *     common to all generators.
   * 2.) Initialize members unique to this generator.
   * 3.) Open print stream
   * 4.) Write class heading: package, prologue, class statement, open curly
   * 5.) Write class body: member data and methods
   * 6.) Write class closing: close curly
   * 7.) Close the print stream
   **/
  public void generate (java.util.Hashtable symbolTable, com.sun.tools.corba.se.idl.SymtabEntry entry)
  {
    this.symbolTable = symbolTable;
    this.entry       = entry;
    init ();

    openStream ();
    if (stream == null)
      return;
    writeHeading ();
    writeBody ();
    writeClosing ();
    closeStream ();
  } // generate

  /**
   * Initialize variables unique to this generator.
   **/
  protected void init ()
  {
    helperClass = entry.name () + "Helper";
    if (entry instanceof ValueBoxEntry)
    {
      ValueBoxEntry v = (ValueBoxEntry) entry;
      TypedefEntry member = ((InterfaceState) v.state ().elementAt (0)).entry;
      SymtabEntry mType =  member.type ();

      if (mType instanceof PrimitiveEntry)
        helperType = Util.javaName (entry);
      else
        helperType = Util.javaName (mType);
    }
    else
      helperType = Util.javaName (entry);
  } // init

  /**
   * Open the print stream for subsequent output.
   **/
  protected void openStream ()
  {
    stream = Util.stream (entry, "Helper.java");
  } // openStream

  /**
   * Generate the heading, including package, imports, class statements,
   * and open curly.
   **/
  protected void writeHeading ()
  {
    Util.writePackage (stream, entry, Util.HelperFile);
    Util.writeProlog (stream, stream.name ());

    // Transfer comment to target <30jul1997daz>.
    if (entry.comment () != null)
      entry.comment ().generate ("", stream);

    stream.print ("public final class " + helperClass);
    if (entry instanceof ValueEntry)
      stream.println (" implements org.omg.CORBA.portable.ValueHelper");
    else
      stream.println ();
    stream.println ('{');
  }

  /**
   * Generate members of this class.
   **/
  protected void writeBody ()
  {
    writeInstVars ();
    writeCtors ();
    writeInsert ();
    writeExtract ();
    writeType ();
    writeID ();
    writeRead ();
    writeWrite ();
    if (entry instanceof InterfaceEntry && !(entry instanceof ValueEntry)) {
      writeNarrow ();
      writeUncheckedNarrow ();
    }
    writeHelperInterface ();
    if (entry instanceof ValueEntry)
      writeValueHelperInterface ();
  } // writeBody

  /**
   * Generate members of the Helper interface.
   **/
  protected void writeHelperInterface ()
  {
  } // writeHelperInterface

  /**
   * Generate members of the ValueHelper interface.
   **/
  protected void writeValueHelperInterface ()
  {
    writeGetID ();       // moved for <d59413>
    writeGetType ();     // moved for <d59413>
    writeGetInstance (); // not in ValueHelper interface
    writeGetClass ();
    writeGetSafeBaseIds ();
  } // writeHelperInterface

  /**
   * Generate the closing statements.
   **/
  protected void writeClosing ()
  {
    stream.println ('}');
  }

  /**
   * Write the stream to file by closing the print stream.
   **/
  protected void closeStream ()
  {
    stream.close ();
  }

  /**
   * Generate the instance variables.
   **/
  protected void writeInstVars ()
  {
    stream.println ("  private static String  _id = \"" + Util.stripLeadingUnderscoresFromID (entry.repositoryID ().ID ()) + "\";");
    if (entry instanceof ValueEntry)
    {
      stream.println ();
      stream.println ("  private static " + helperClass + " helper = new " + helperClass + " ();");
      stream.println ();
      stream.println ("  private static String[] _truncatable_ids = {");
      stream.print   ("    _id");

      // Any safe ValueEntry must have a concete value parent.
      // The topmost parent cannot be safe since it doesn't have
      // a concrete parent.
      ValueEntry child = (ValueEntry) entry;
      while (child.isSafe ())
      {
        stream.println(",");
        ValueEntry parent = (ValueEntry)child.derivedFrom ().elementAt (0);
        stream.print("    \"" + Util.stripLeadingUnderscoresFromID (parent.repositoryID ().ID ()) + "\"");
        child = parent;
      }
      stream.println("   };");
    }
    stream.println ();
  } // writeInstVars

  /**
   * Generate the constructors.
   **/
  protected void writeCtors ()
  {
    stream.println ("  public " + helperClass + "()");
    stream.println ("  {");
    stream.println ("  }");
    stream.println ();
  } // writeCtors

  /**
   * Generate the insert method.
   **/
  protected void writeInsert ()
  {
    stream.println ("  public static void insert (org.omg.CORBA.Any a, " + helperType + " that)");
    stream.println ("  {");
    stream.println ("    org.omg.CORBA.portable.OutputStream out = a.create_output_stream ();");
    stream.println ("    a.type (type ());");
    stream.println ("    write (out, that);");
    stream.println ("    a.read_value (out.create_input_stream (), type ());");
    stream.println ("  }");
    stream.println ();
  } // writeInsert

  /**
   * Generate the extract method.
   **/
  protected void writeExtract ()
  {
    stream.println ("  public static " + helperType + " extract (org.omg.CORBA.Any a)");
    stream.println ("  {");
    stream.println ("    return read (a.create_input_stream ());");
    stream.println ("  }");
    stream.println ();
  } // writeExtract

  /**
   * Generate the typecode variable and type method.
   **/
  protected void writeType ()
  {
    boolean canRecurse = entry instanceof ValueEntry
        || entry instanceof ValueBoxEntry
        || entry instanceof StructEntry;
    stream.println ("  private static org.omg.CORBA.TypeCode __typeCode = null;");
    if (canRecurse)
      stream.println ("  private static boolean __active = false;");
    stream.println ("  synchronized public static org.omg.CORBA.TypeCode type ()");
    stream.println ("  {");
    stream.println ("    if (__typeCode == null)");
    stream.println ("    {");
    if (canRecurse) {
    stream.println ("      synchronized (org.omg.CORBA.TypeCode.class)");
    stream.println ("      {");
    stream.println ("        if (__typeCode == null)");
    stream.println ("        {");
    stream.println ("          if (__active)");
    stream.println ("          {");
    stream.println ("            return org.omg.CORBA.ORB.init().create_recursive_tc ( _id );");
    stream.println ("          }");
    stream.println ("          __active = true;");
    ((JavaGenerator)entry.generator ()).helperType (0, "          ", new TCOffsets (), "__typeCode", entry, stream);
    }
    else
    ((JavaGenerator)entry.generator ()).helperType (0, "      ", new TCOffsets (), "__typeCode", entry, stream);

    // Generate body of type() method

    if (canRecurse) {
    stream.println ("          __active = false;");
    stream.println ("        }");
    stream.println ("      }");
    }
    stream.println ("    }");
    stream.println ("    return __typeCode;");
    stream.println ("  }");
    stream.println ();
  } // writeType

  /**
   * Generate the ID method.
   **/
  protected void writeID ()
  {
    stream.println ("  public static String id ()");
    stream.println ("  {");
    stream.println ("    return _id;");
    stream.println ("  }");
    stream.println ();
  } // writeID

  /**
   * Generate the read method.
   **/
  protected void writeRead ()
  {

    boolean isLocalInterface = false;

    if (entry instanceof InterfaceEntry) {
        InterfaceEntry ie = (InterfaceEntry) entry;

        // for #pragma sun_local or sun_localservant, or actual local
        // local interface, set the flag by checking on both
        isLocalInterface = ie.isLocal() | ie.isLocalServant();
    }

    stream.println ("  public static " + helperType + " read (org.omg.CORBA.portable.InputStream istream)");
    stream.println ("  {");
    if ( !isLocalInterface ) { // nonLocal Interface and other types
      ((JavaGenerator)entry.generator ()).helperRead (helperType, entry, stream);
    } else { //Local interface should throw exception
      stream.println ("      throw new org.omg.CORBA.MARSHAL ();");
    }
    stream.println ("  }");
    stream.println ();
  } // writeRead

  /**
   * Generate the write method.
   **/
  protected void writeWrite ()
  {

    boolean isLocalInterface = false;

    if (entry instanceof InterfaceEntry) {
        InterfaceEntry ie = (InterfaceEntry) entry;

        // for #pragma sun_local or sun_localservant, or actual local
        // local interface, set the flag by checking on both
        isLocalInterface = ie.isLocal() | ie.isLocalServant();
    }

    stream.println ("  public static void write (org.omg.CORBA.portable.OutputStream ostream, " + helperType + " value)");
    stream.println ("  {");
    if ( !isLocalInterface ) { // nonLocal Interface and other types
      ((JavaGenerator)entry.generator ()).helperWrite (entry, stream);
    } else { //Local interface should throw exception
      stream.println ("      throw new org.omg.CORBA.MARSHAL ();");
    }
    stream.println ("  }");
    stream.println ();
  } // writeWrite


  /**
   * Generate the narrow method.
   **/
  protected void writeNarrow ()
  {
    writeRemoteNarrow ();
    stream.println ();
  }

  /**
   * Write the narrow() method for a remotable object.
   **/
  protected void writeRemoteNarrow ()
  {
    InterfaceEntry ie = (InterfaceEntry) entry;

    // narrow for LocalObject interface
    if (ie.isLocal ()) {
        writeRemoteNarrowForLocal (false);
        return;
    }

    // narrow for Abstract interface
    if (ie.isAbstract ()) {
        writeRemoteNarrowForAbstract (false);
        return;
    } else {
        // Determine if the non-abstract interface has any abstract parents
        for (int i = 0; i < ie.derivedFrom ().size (); i++) {
            SymtabEntry parent = (SymtabEntry) ie.derivedFrom ().elementAt (i);
            if (((InterfaceEntry) parent).isAbstract ()) {
                writeRemoteNarrowForAbstract (true);
                break;
            }
        }
    }

    stream.println ("  public static " + helperType + " narrow (org.omg.CORBA.Object obj)");
    stream.println ("  {");
    stream.println ("    if (obj == null)");
    stream.println ("      return null;");
    stream.println ("    else if (obj instanceof " + helperType + ')');
    stream.println ("      return (" + helperType + ")obj;");
    stream.println ("    else if (!obj._is_a (id ()))");
    stream.println ("      throw new org.omg.CORBA.BAD_PARAM ();");
    stream.println ("    else");
    stream.println ("    {");
    stream.println ("      org.omg.CORBA.portable.Delegate delegate = ((org.omg.CORBA.portable.ObjectImpl)obj)._get_delegate ();");
    String stubNameofEntry = stubName ((InterfaceEntry)entry);
    stream.println ("      " + stubNameofEntry + " stub = new " + stubNameofEntry + " ();");
    stream.println ("      stub._set_delegate(delegate);");
    stream.println ("      return stub;");
    stream.println ("    }");
    stream.println ("  }");
  } // writeRemoteNarrow

  /**
   * Write the narrow() method for local interface.
   **/
  private void writeRemoteNarrowForLocal (boolean hasAbstractParent)
  {
    stream.println ("  public static " + helperType + " narrow (org.omg.CORBA.Object obj)");
    stream.println ("  {");
    stream.println ("    if (obj == null)");
    stream.println ("      return null;");
    stream.println ("    else if (obj instanceof " + helperType + ')');
    stream.println ("      return (" + helperType + ")obj;");
    stream.println ("    else");
    stream.println ("      throw new org.omg.CORBA.BAD_PARAM ();");
    stream.println ("  }");
  } // writeRemoteNarrowForLocal

  /**
   * Write the narrow() method for abstract interface.
   **/
  private void writeRemoteNarrowForAbstract (boolean hasAbstractParent)
  {
    stream.print ("  public static " + helperType + " narrow (java.lang.Object obj)");
    stream.println ("  {");
    stream.println ("    if (obj == null)");
    stream.println ("      return null;");
    if (hasAbstractParent)
    {
      stream.println ("    else if (obj instanceof org.omg.CORBA.Object)");
      stream.println ("      return narrow ((org.omg.CORBA.Object) obj);");
    }
    else
    {
      stream.println ("    else if (obj instanceof " + helperType + ')');
      stream.println ("      return (" + helperType + ")obj;");
    }

    // If hasAbstractParent is false, then THIS entry must be abstract.
    // This method is also called in case THIS entry is not abstract, but
    // there is an abstract parent.  If this entry is not abstract,
    // it can never narrow to a CORBA object reference.
    if (!hasAbstractParent) { // <d58889>
      String stubNameofEntry = stubName ((InterfaceEntry)entry);

      stream.println ("    else if ((obj instanceof org.omg.CORBA.portable.ObjectImpl) &&");
      stream.println ("             (((org.omg.CORBA.Object)obj)._is_a (id ()))) {");
      stream.println ("      org.omg.CORBA.portable.ObjectImpl impl = (org.omg.CORBA.portable.ObjectImpl)obj ;" ) ;
      stream.println ("      org.omg.CORBA.portable.Delegate delegate = impl._get_delegate() ;" ) ;
      stream.println ("      " + stubNameofEntry + " stub = new " + stubNameofEntry + " ();");
      stream.println ("      stub._set_delegate(delegate);");
      stream.println ("      return stub;" ) ;
      stream.println ("    }" ) ;
    };
    // end <d57118 - check for remotable - klr>

    stream.println ("    throw new org.omg.CORBA.BAD_PARAM ();");
    stream.println ("  }");
    stream.println ();
  } // writeRemoteNarrowForAbstract


  /**
   * Generate the unchecked narrow method.
   **/
  protected void writeUncheckedNarrow ()
  {
    writeUncheckedRemoteNarrow ();
    stream.println ();
  }

  /**
   * Write the unchecked narrow() method for a remotable object.
   **/
  protected void writeUncheckedRemoteNarrow ()
  {
    InterfaceEntry ie = (InterfaceEntry) entry;

    // unchecked narrow for LocalObject interface
    if (ie.isLocal ()) {
        writeRemoteUncheckedNarrowForLocal (false);
        return;
    }

    // unchecked narrow for Abstract interface
    if (ie.isAbstract ()) {
        writeRemoteUncheckedNarrowForAbstract (false);
        return;
    } else {
        // Determine if the non-abstract interface has any abstract parents
        for (int i = 0; i < ie.derivedFrom ().size (); i++) {
            SymtabEntry parent = (SymtabEntry) ie.derivedFrom ().elementAt (i);
            if (((InterfaceEntry) parent).isAbstract ()) {
                writeRemoteUncheckedNarrowForAbstract (true);
                break;
            }
        }
    }

    stream.println ("  public static " + helperType + " unchecked_narrow (org.omg.CORBA.Object obj)");
    stream.println ("  {");
    stream.println ("    if (obj == null)");
    stream.println ("      return null;");
    stream.println ("    else if (obj instanceof " + helperType + ')');
    stream.println ("      return (" + helperType + ")obj;");
    stream.println ("    else");
    stream.println ("    {");
    stream.println ("      org.omg.CORBA.portable.Delegate delegate = ((org.omg.CORBA.portable.ObjectImpl)obj)._get_delegate ();");
    String stubNameofEntry = stubName ((InterfaceEntry)entry);
    stream.println ("      " + stubNameofEntry + " stub = new " + stubNameofEntry + " ();");
    stream.println ("      stub._set_delegate(delegate);");
    stream.println ("      return stub;");
    stream.println ("    }");
    stream.println ("  }");
  } // writeUncheckedRemoteNarrow

  /**
   * Write the unchecked narrow() method for local interface.
   **/
  private void writeRemoteUncheckedNarrowForLocal (boolean hasAbstractParent)
  {
    stream.println ("  public static " + helperType + " unchecked_narrow (org.omg.CORBA.Object obj)");
    stream.println ("  {");
    stream.println ("    if (obj == null)");
    stream.println ("      return null;");
    stream.println ("    else if (obj instanceof " + helperType + ')');
    stream.println ("      return (" + helperType + ")obj;");
    stream.println ("    else");
    stream.println ("      throw new org.omg.CORBA.BAD_PARAM ();");
    stream.println ("  }");
  } // writeRemoteUncheckedNarrowForLocal

  /**
   * Write the unchecked narrow() method for abstract interface.
   **/
  private void writeRemoteUncheckedNarrowForAbstract (boolean hasAbstractParent)
  {
    stream.print ("  public static " + helperType + " unchecked_narrow (java.lang.Object obj)");
    stream.println ("  {");
    stream.println ("    if (obj == null)");
    stream.println ("      return null;");
    if (hasAbstractParent)
    {
      stream.println ("    else if (obj instanceof org.omg.CORBA.Object)");
      stream.println ("      return unchecked_narrow ((org.omg.CORBA.Object) obj);");
    }
    else
    {
      stream.println ("    else if (obj instanceof " + helperType + ')');
      stream.println ("      return (" + helperType + ")obj;");
    }

    if (!hasAbstractParent) {
      String stubNameofEntry = stubName ((InterfaceEntry)entry);

      stream.println ("    else if (obj instanceof org.omg.CORBA.portable.ObjectImpl) {");
      stream.println ("      org.omg.CORBA.portable.ObjectImpl impl = (org.omg.CORBA.portable.ObjectImpl)obj ;" ) ;
      stream.println ("      org.omg.CORBA.portable.Delegate delegate = impl._get_delegate() ;" ) ;
      stream.println ("      " + stubNameofEntry + " stub = new " + stubNameofEntry + " ();");
      stream.println ("      stub._set_delegate(delegate);");
      stream.println ("      return stub;" ) ;
      stream.println ("    }" ) ;
    };

    stream.println ("    throw new org.omg.CORBA.BAD_PARAM ();");
    stream.println ("  }");
    stream.println ();
  } // writeRemoteUncheckedNarrowForAbstract


  /**
   * Generate the GetID method.
   **/
  protected void writeGetID ()
  {
    if ( !Util.IDLEntity (entry))
      return;
    stream.println ("  public String get_id ()");
    stream.println ("  {");
    stream.println ("    return _id;");
    stream.println ("  }");
    stream.println ();
  } // writeGetID

  /**
   * Generate the GetType method.
   **/
  protected void writeGetType ()
  {
    if ( !Util.IDLEntity (entry))
      return;
    stream.println ("  public org.omg.CORBA.TypeCode get_type ()");
    stream.println ("  {");
    stream.println ("    return type ();");
    stream.println ("  }");
    stream.println ();
  } // writeGetID

  /**
   * Generate the get_class method.
   **/
  protected void writeGetClass ()
  {
    stream.println ("  public Class get_class ()");
    stream.println ("  {");
    stream.println ("    return " + helperType + ".class;"); //<d59383>
    stream.println ("  }");
    stream.println ();
  } // writeGetClass

  /**
   * Generate the get_instance method.
   **/
  protected void writeGetInstance ()
  {
    stream.println ("  public static org.omg.CORBA.portable.ValueHelper get_instance ()");
    stream.println ("  {");
    stream.println ("    return helper;");
    stream.println ("  }");
    stream.println ();
  } // writeGetInstance

  /**
   * Generate the GetSafeBaseIds method.
   **/
  protected void writeGetSafeBaseIds ()
  {
    stream.println ("  public String[] get_truncatable_base_ids ()");
    stream.println ("  {");
    stream.println ("    return _truncatable_ids;");
    stream.println ("  }");
    stream.println ();
  } // writeGetSafeBaseIds

  /**
   * Return the stub name for the interface entry.
   **/
  protected String stubName (InterfaceEntry entry)
  {
    String name;
    if (entry.container ().name ().equals (""))
      name =  '_' + entry.name () + "Stub";
    else
    {
      name = Util.containerFullName (entry.container ()) + "._" + entry.name () + "Stub";
    }
    return name.replace ('/', '.');
  } // stubName

  protected java.util.Hashtable     symbolTable;
  protected com.sun.tools.corba.se.idl.SymtabEntry entry;
  protected GenFileStream           stream;

  // Unique to this generator
  protected String helperClass;
  protected String helperType;
} // class Helper
