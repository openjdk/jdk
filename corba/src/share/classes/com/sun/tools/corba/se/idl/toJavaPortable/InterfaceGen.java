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
// -The ctor should really throw an exception, but then it must have a
//  throws clause. How much of a ripple effect is this?
// -F46082.51<daz> Remove -stateful feature.
// -D60929   <klr> Update for RTF2.4 changes
// -D61056   <klr> Use Util.helperName
// -D62014   <klr> Move const definitions from signature to operations interf.
// -D62310   <klr> Fix declaration of interfaces extending abstract intf.
// -D62023   <klr> Move const definitions back from operations to signature.

import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import com.sun.tools.corba.se.idl.GenFileStream;
import com.sun.tools.corba.se.idl.ConstEntry;
import com.sun.tools.corba.se.idl.InterfaceEntry;
import com.sun.tools.corba.se.idl.InterfaceState;
import com.sun.tools.corba.se.idl.MethodEntry;
import com.sun.tools.corba.se.idl.PrimitiveEntry;
import com.sun.tools.corba.se.idl.SequenceEntry;
import com.sun.tools.corba.se.idl.StringEntry;
import com.sun.tools.corba.se.idl.SymtabEntry;
import com.sun.tools.corba.se.idl.TypedefEntry;

/**
 *
 **/
public class InterfaceGen implements com.sun.tools.corba.se.idl.InterfaceGen, JavaGenerator
{
  /**
   * Public zero-argument constructor.
   **/
  public InterfaceGen ()
  {
    //emit = ((Arguments)Compile.compiler.arguments).emit;
    //factories = (Factories)Compile.compiler.factories ();
  } // ctor

  /**
   * Generate the interface and all the files associated with it.
   * Provides general algorithm for binding generation:
   * 1.) Initialize symbol table and symbol table entry members, common to all generators.
   * 2.) Generate the skeleton if required by calling generateSkeletn ()
   * 3.) Generate the holder by calling generateHolder ()
   * 4.) Generate the helper by calling generateHelper ()
   * 5.) Generate the stub if required by calling generateStub ()
   * 6.) Generate the interface by calling generateInterface ()
   **/
  public void generate (Hashtable symbolTable, InterfaceEntry i, PrintWriter stream)
  {
    if (!isPseudo(i))
    {
      this.symbolTable = symbolTable;
      this.i           = i;
      init ();

      // for sun_local pragma, just generate the signature and operations interfaces
      // for sun_localservant pragma, generate the Local Stubs, and Skel, should not
      // have _invoke defined.
      // for local (is_local()) case, generate only Helpers and Holder, where they
      // have been modified to throw appropriate exceptions for read and write, and
      // narrow is modified to not invoke _is_a

      if (! (i.isLocalSignature())) {
          // generate the stubs and skeletons for non-local interfaces
          if (! (i.isLocal())) {
              // for local servant case just generate the skeleton, but
              // for others generate the stubs also
              generateSkeleton ();

              // _REVISIT_, Whenever there is time restructure the code to
              // encapsulate stub and skeleton generation.

              // If the option is -fallTie then generate the Tie class first
              // and then generate the ImplBase class to make the generation
              // complete for the Hierarchy.
              Arguments theArguments = (Arguments)Compile.compiler.arguments;
              if( (theArguments.TIEServer == true )
                &&(theArguments.emit == theArguments.All ) )
              {
                  theArguments.TIEServer = false;
                  // Generate the ImplBase class
                  generateSkeleton ();
                  // Revert in case file contains multiple interfaces
                  theArguments.TIEServer = true;
              }
              generateStub ();
          }
          generateHolder ();
          generateHelper ();
      }
      intfType = SIGNATURE;
      generateInterface ();
      intfType = OPERATIONS;
      generateInterface ();
      intfType = 0;
    }
  } // generate

  /**
   * Initialize members unique to this generator.
   **/
  protected void init ()
  {
    emit = ((Arguments)Compile.compiler.arguments).emit;
    factories = (Factories)Compile.compiler.factories ();
  } // init

  /**
   * Generate a Skeleton when the user does not want just the client-side code.
   **/
  protected void generateSkeleton ()
  {
    // <f46082.51> Remove -stateful feature.
    // The Skeleton is generated only when the user doesn't want
    // JUST the client code OR when the interface is stateful
    //if (emit != Arguments.Client || i.state () != null)
    //  factories.skeleton ().generate (symbolTable, i);
    if (emit != Arguments.Client)
      factories.skeleton ().generate (symbolTable, i);
  } // generateSkeleton

  /**
   * Generate a Stub when the user does not want just the server-side code.
   **/
  protected void generateStub ()
  {
    // <klr> According to Simon on 10/28/98, we should generate stubs for
    // abstract interfaces too.
    if (emit != Arguments.Server /* && !i.isAbstract () */)
      factories.stub ().generate (symbolTable, i);
  } // generateStub

  /**
   * Generate a Helper when the user does not want just the server-side code.
   **/
  protected void generateHelper ()
  {
    if (emit != Arguments.Server)
      factories.helper ().generate (symbolTable, i);
  } // generateHelper

  /**
   * Generate a Holder when the user does not want just the server-side code.
   **/
  protected void generateHolder ()
  {
    if (emit != Arguments.Server)
      factories.holder ().generate (symbolTable, i);
  } // generateHolder

  /**
   * Generate the interface. Provides general algorithm for binding generation:
   * 1.) Initialize members unique to this generator. - init ()
   * 2.) Open print stream - openStream ()
   * 3.) Write class heading (package, prologue, class statement, open curly - writeHeading ()
   * 4.) Write class body (member data and methods) - write*Body ()
   * 5.) Write class closing (close curly) - writeClosing ()
   * 6.) Close the print stream - closeStream ()
   *
   * For CORBA 2.3, interfaces are mapped to Operations and Signature
   * interfaces. The Operations interface contains the method definitions.
   * The Signature interface extends the Operations interface and adds
   * CORBA::Object. <klr>
   **/
  protected void generateInterface ()
  {
    init ();
    openStream ();
    if (stream == null)
      return;
    writeHeading ();
    if (intfType == OPERATIONS)
      writeOperationsBody ();
    if (intfType == SIGNATURE)
      writeSignatureBody ();
    writeClosing ();
    closeStream ();
  } // generateInterface

  /**
   *
   **/
  protected void openStream ()
  {
    if (i.isAbstract () || intfType == SIGNATURE)
       stream = Util.stream (i, ".java");
    else if (intfType == OPERATIONS)
       stream = Util.stream (i, "Operations.java");
  } // openStream

  /**
   *
   **/
  protected void writeHeading ()
  {
    Util.writePackage (stream, i, Util.TypeFile);
    Util.writeProlog (stream, ((GenFileStream)stream).name ());

    // Transfer interface comment to target <31jul1997>.
    if (i.comment () != null)
      i.comment ().generate ("", stream);

    String className = i.name ();
//  if (((Arguments)Compile.compiler.arguments).TIEServer)
//  {
//    // For the delegate model, don't make interface a subclass of CORBA.Object
//    stream.print ("public interface " + className);
//    boolean firstTime = true;
//    for (int ii = 0; ii < i.derivedFrom ().size (); ++ii)
//    {
//      SymtabEntry parent = (SymtabEntry)i.derivedFrom ().elementAt (ii);
//      if (!parent.fullName ().equals ("org/omg/CORBA/Object"))
//      {
//        if (firstTime)
//        {
//          firstTime = false;
//          stream.print (" extends ");
//        }
//        else
//          stream.print (", ");
//        stream.print (Util.javaName (parent));
//      }
//    }
//    if (i.derivedFrom ().size () > 0)
//      stream.print (", ");
//    stream.print ("org.omg.CORBA.portable.IDLEntity ");
//  }
//
//  else
//  {
      if (intfType == SIGNATURE)
         writeSignatureHeading ();
      else if (intfType == OPERATIONS)
         writeOperationsHeading ();
//  }

    stream.println ();
    stream.println ('{');
  } // writeHeading

  /**
   *
   **/
  protected void writeSignatureHeading ()
  {
    String className = i.name ();
    stream.print ("public interface " + className + " extends " + className + "Operations, ");
    boolean firstTime = true;
    boolean hasNonAbstractParent = false; // <d62310-klr>
    for (int k = 0; k < i.derivedFrom ().size (); ++k)
    {
      if (firstTime)
        firstTime = false;
      else
        stream.print (", ");
      InterfaceEntry parent = (InterfaceEntry)i.derivedFrom ().elementAt (k);
      stream.print (Util.javaName (parent));
      if (! parent.isAbstract ()) // <d62310-klr>
        hasNonAbstractParent = true; // <d62310-klr>
    }
    // <d62310-klr> - begin
    // If this interface extends only abstract interfaces,
    // it should extend both org.omg.CORBA.Object and IDLEntity.
    if (!hasNonAbstractParent) {
      stream.print (", org.omg.CORBA.Object, org.omg.CORBA.portable.IDLEntity ");
    }
    else {
    // <d62310-klr> - end
        // extends IDLEntity if there's only one default parent - CORBA.Object
        if (i.derivedFrom ().size () == 1)
          stream.print (", org.omg.CORBA.portable.IDLEntity ");
    }
  } // writeSignatureHeading

  /**
   *
   **/
  protected void writeOperationsHeading ()
  {
    stream.print ("public interface " + i.name ());
    if ( !i.isAbstract ())
      stream.print ("Operations ");
    else {
        // <d60929> - base abstract interfaces extend AbstractBase
        // changed to IDLEntity by SCN per latest spec...
        if (i.derivedFrom ().size () == 0)
          stream.print (" extends org.omg.CORBA.portable.IDLEntity");
    }

    boolean firstTime = true;
    for (int k = 0; k < i.derivedFrom ().size (); ++k)
    {
      InterfaceEntry parent = (InterfaceEntry) i.derivedFrom ().elementAt (k);
      String parentName = Util.javaName (parent);

      // ignore the default parent - CORBA.Object
      if (parentName.equals ("org.omg.CORBA.Object"))
          continue;

      if (firstTime)
      {
        firstTime = false;
        stream.print (" extends ");
      }
      else
        stream.print (", ");

      // Don't append suffix Operations to the parents of abstract interface
      // or to the abstract parents of regular interface
      if (parent.isAbstract () || i.isAbstract ())
        stream.print (parentName);
      else
        stream.print (parentName + "Operations");
    }
  } // writeOperationsHeading


  /**
   *
   **/
  protected void writeOperationsBody ()
  {
    // Generate everything but constants
    Enumeration e = i.contained ().elements ();
    while (e.hasMoreElements ())
    {
      SymtabEntry contained = (SymtabEntry)e.nextElement ();
      if (contained instanceof MethodEntry)
      {
        MethodEntry element = (MethodEntry)contained;
        ((MethodGen)element.generator ()).interfaceMethod (symbolTable, element, stream);
      }
      else
        if ( !(contained instanceof ConstEntry))
          contained.generate (symbolTable, stream);
    }
  } // writeOperationsBody

  /**
   *
   **/
  protected void writeSignatureBody ()
  {
    // Generate only constants
    Enumeration e = i.contained ().elements ();
    while (e.hasMoreElements ())
    {
      SymtabEntry contained = (SymtabEntry)e.nextElement ();
      if (contained instanceof ConstEntry)
        contained.generate (symbolTable, stream);
    }
  } // writeSignatureBody

  /**
   *
   **/
  protected void writeClosing ()
  {
    String intfName = i.name ();
    if ( !i.isAbstract () && intfType == OPERATIONS)
      intfName = intfName + "Operations";
    stream.println ("} // interface " + intfName);
  } // writeClosing

  /**
   *
   **/
  protected void closeStream ()
  {
    stream.close ();
  } // closeStream

  ///////////////
  // From JavaGenerator

  // <f46082.51> Remove -stateful feature.
  /*
  public int helperType (int index, String indent, TCOffsets tcoffsets, String name, SymtabEntry entry, PrintWriter stream)
  {
    InterfaceEntry i = (InterfaceEntry)entry;
    if (i.state () != null && i.state ().size () > 0)
      index = structHelperType (index, indent, tcoffsets, name, entry, stream);
    else
    {
      tcoffsets.set (entry);
      if (entry.fullName ().equals ("org/omg/CORBA/Object"))
        stream.println (indent + name
            + " = org.omg.CORBA.ORB.init ().get_primitive_tc (org.omg.CORBA.TCKind.tk_objref);");
      else
        stream.println (indent + name
            // <54697>
            //+ " = org.omg.CORBA.ORB.init ().create_interface_tc (_id, "
            + " = org.omg.CORBA.ORB.init ().create_interface_tc (" + Util.helperName (i, true) + ".id (), " // <d61056>
            + '\"' + Util.stripLeadingUnderscores (entry.name ()) + "\");");
    }
    return index;
  } // helperType
  */
  public int helperType (int index, String indent, TCOffsets tcoffsets, String name, SymtabEntry entry, PrintWriter stream)
  {
    InterfaceEntry i = (InterfaceEntry)entry;
    tcoffsets.set (entry);
    if (entry.fullName ().equals ("org/omg/CORBA/Object"))
      stream.println (indent + name
          + " = org.omg.CORBA.ORB.init ().get_primitive_tc (org.omg.CORBA.TCKind.tk_objref);");
    else
      stream.println (indent + name
          // <54697>
          //+ " = org.omg.CORBA.ORB.init ().create_interface_tc (_id, "
          + " = org.omg.CORBA.ORB.init ().create_interface_tc (" + Util.helperName (i, true) + ".id (), " // <d61056>
          + '\"' + Util.stripLeadingUnderscores (entry.name ()) + "\");");
    return index;
  } // helperType

  public int type (int index, String indent, TCOffsets tcoffsets, String name, SymtabEntry entry, PrintWriter stream) {
    stream.println (indent + name + " = " + Util.helperName (entry, true) + ".type ();"); // <d61056>
    return index;
  } // type

  // <f46082.51> Remove -stateful feature.
  /*
  public void helperRead (String entryName, SymtabEntry entry, PrintWriter stream)
  {
    InterfaceEntry i = (InterfaceEntry)entry;
    if (i.state () != null)
      structHelperRead (entryName, i, stream);
    else
    {
      if (i.isAbstract ())
         stream.println ("    return narrow (((org.omg.CORBA_2_3.portable.InputStream)istream).read_abstract_interface (_" + i.name () + "Stub.class));"); // <d60929>
      else
         stream.println ("    return narrow (istream.read_Object (_" + i.name () + "Stub.class));");
    }
  } // helperRead

  */
  public void helperRead (String entryName, SymtabEntry entry, PrintWriter stream)
  {
    InterfaceEntry i = (InterfaceEntry)entry;
    if (i.isAbstract ())
      stream.println ("    return narrow (((org.omg.CORBA_2_3.portable.InputStream)istream).read_abstract_interface (_" + i.name () + "Stub.class));"); // <d60929>
    else
      stream.println ("    return narrow (istream.read_Object (_" + i.name () + "Stub.class));");
  } // helperRead

  // <f46082.51> Remove -stateful feature.
  /*
  public void helperWrite (SymtabEntry entry, PrintWriter stream)
  {
    InterfaceEntry i = (InterfaceEntry)entry;
    if (i.state () != null)
      structHelperWrite (entry, stream);
    else
      write (0, "    ", "value", entry, stream);
  } // helperWrite
  */
  public void helperWrite (SymtabEntry entry, PrintWriter stream)
  {
    write (0, "    ", "value", entry, stream);
  } // helperWrite

  // <f46082.51> Remove -stateful feature.
  /*
  public int read (int index, String indent, String name, SymtabEntry entry, PrintWriter stream)
  {
    InterfaceEntry i = (InterfaceEntry)entry;
    if (i.state () != null)
      index = structRead (index, indent, name, i, stream);
    else
    {
      if (entry.fullName ().equals ("org/omg/CORBA/Object"))
        stream.println (indent + name + " = istream.read_Object (_" + i.name () + "Stub.class);");
      else
        stream.println (indent + name + " = " + Util.helperName (entry, false) + ".narrow (istream.read_Object (_" + i.name () + "Stub.class));"); // <d61056>
    }
    return index;
  } // read
  */
  public int read (int index, String indent, String name, SymtabEntry entry, PrintWriter stream)
  {
    InterfaceEntry i = (InterfaceEntry)entry;
    if (entry.fullName ().equals ("org/omg/CORBA/Object"))
      stream.println (indent + name + " = istream.read_Object (_" + i.name () + "Stub.class);");
    else
      stream.println (indent + name + " = " + Util.helperName (entry, false) + ".narrow (istream.read_Object (_" + i.name () + "Stub.class));"); // <d61056>
    return index;
  } // read

  // <f46082.51> Remove -stateful feature.
  /*
  public int write (int index, String indent, String name, SymtabEntry entry, PrintWriter stream)
  {
    InterfaceEntry i = (InterfaceEntry)entry;
    if (i.state () != null)
      index = structWrite (index, indent, name, entry, stream);
    else
    {
      if (i.isAbstract ())
         stream.println (indent + "((org.omg.CORBA_2_3.portable.OutputStream)ostream).write_abstract_interface ((java.lang.Object) " + name + ");"); // <d60929>
      else
         stream.println (indent + "ostream.write_Object ((org.omg.CORBA.Object) " + name + ");");
    }
    return index;
  } // write
  */
  public int write (int index, String indent, String name, SymtabEntry entry, PrintWriter stream)
  {
    InterfaceEntry i = (InterfaceEntry)entry;
    if (i.isAbstract ())
      stream.println (indent + "((org.omg.CORBA_2_3.portable.OutputStream)ostream).write_abstract_interface ((java.lang.Object) " + name + ");"); // <d60929>
    else
      stream.println (indent + "ostream.write_Object ((org.omg.CORBA.Object) " + name + ");");
    return index;
  } // write

  // <f46082.51> Remove -stateful feature.
  /*
  // These methods are cobbled from StructGen.  Stateful interfaces
  // are sent across the wire as if they were structs, with the first
  // element being a string - the Java name of the class.

  public int structHelperType (int index, String indent, TCOffsets tcoffsets, String name, SymtabEntry entry, PrintWriter stream)
  {
    TCOffsets innerOffsets = new TCOffsets ();
    innerOffsets.set (entry);
    int offsetForStruct = innerOffsets.currentOffset ();
    InterfaceEntry i = (InterfaceEntry)entry;
    String membersName = "_members" + index++;
    Vector state = i.state ();
    stream.println (indent + "org.omg.CORBA.StructMember[] " + membersName + " = new org.omg.CORBA.StructMember [" + (state.size () + 1) + "];");
    String tcOfMembers = "_tcOf" + membersName;
    stream.println (indent + "org.omg.CORBA.TypeCode " + tcOfMembers + ';');

    // The name string is the first element of the struct
    String memberName = "_name";
    StringEntry stringEntry = Compile.compiler.factory.stringEntry ();
    index = ((JavaGenerator)stringEntry.generator ()).helperType (index, indent, innerOffsets, tcOfMembers, stringEntry, stream);
    stream.println (indent + membersName + "[0] = new org.omg.CORBA.StructMember (");
    stream.println (indent + "  \"" + memberName + "\",");
    stream.println (indent + "  " + tcOfMembers + ',');
    stream.println (indent + "  null);");
    int offsetSoFar = innerOffsets.currentOffset ();
    innerOffsets = new TCOffsets ();
    innerOffsets.set (entry);
    innerOffsets.bumpCurrentOffset (offsetSoFar - offsetForStruct);

    for (int idx = 0; idx < state.size (); ++idx)
    {
      TypedefEntry member = ((InterfaceState)state.elementAt (idx)).entry;
      memberName = member.name ();
      index = ((JavaGenerator)member.generator ()).helperType (index, indent, innerOffsets, tcOfMembers, member, stream);
      stream.println (indent + membersName + '[' + (idx + 1) + "] = new org.omg.CORBA.StructMember (");
      stream.println (indent + "  \"" + memberName + "\",");
      stream.println (indent + "  " + tcOfMembers + ',');
      stream.println (indent + "  null);");
      offsetSoFar = innerOffsets.currentOffset ();
      innerOffsets = new TCOffsets ();
      innerOffsets.set (entry);
      innerOffsets.bumpCurrentOffset (offsetSoFar - offsetForStruct);
    }
    tcoffsets.bumpCurrentOffset (innerOffsets.currentOffset ());
    stream.println (indent + name + " = org.omg.CORBA.ORB.init ().create_struct_tc (id (), \"" + entry.name () + "\", " + membersName + ");");
    return index;
  } // structHelperType

  public void structHelperRead (String entryName, InterfaceEntry entry, PrintWriter stream)
  {
    String impl = implName ((InterfaceEntry)entry);
    stream.println ("      " + Util.javaStatefulName (entry) + " value = null;");
    structRead (0, "      ", "value", entry, stream);
    stream.println ("      return value;");
  } // structHelperRead

  private String implName (InterfaceEntry entry)
  {
    String name;
    if (entry.container ().name ().equals (""))
      name =  '_' + entry.name () + "Impl";
    else
      name = Util.containerFullName (entry.container ()) + "._" + entry.name () + "Impl";
    return name.replace ('/', '.');
  } // implName

  public int structRead (int index, String indent, String name, InterfaceEntry entry, PrintWriter stream)
  {
    // The first element will be the name of the Java implementation class.
    String stringName = "_name" + index++;
    stream.println (indent + "String " + stringName + " = istream.read_string ();");
    stream.println (indent + "try");
    stream.println (indent + "{");
    stream.println (indent + "  " + name + " = (" + Util.javaStatefulName (entry) + ")com.sun.CORBA.iiop.ORB.getImpl (" + stringName + ".replace ('/', '.'));");
    stream.println (indent + "}");
    stream.println (indent + "catch (Exception e)");
    stream.println (indent + "{");
    stream.println (indent + "  " + name + " = null;");
    stream.println (indent + "}");
    stream.println (indent + "if (" + name + " == null)");
    stream.println (indent + "  throw new org.omg.CORBA.NO_IMPLEMENT (0, org.omg.CORBA.CompletionStatus.COMPLETED_NO);");
    stream.println ();

    stream.println (indent + "if (!" + stringName + ".equals (\"" + entry.fullName () + "\"))");
    stream.println (indent + '{');
    stream.println (indent + "  Class _cls = " + name + ".getClass ();");
    stream.println (indent + "  boolean _found = false;");
    stream.println (indent + "  while (!_found && _cls != null)");
    stream.println (indent + "  {");
    stream.println (indent + "    Class[] interfaces = _cls.getInterfaces ();");
    stream.println (indent + "    for (int i = 0; i < interfaces.length; ++i)");
    stream.println (indent + "      if (interfaces[i].getName ().indexOf (\"State\") > 0)");
    stream.println (indent + "      {");
    stream.println (indent + "        _cls = interfaces[i];");
    stream.println (indent + "        _found = true;");
    stream.println (indent + "        break;");
    stream.println (indent + "      }");
    stream.println (indent + "    if (!_found)");
    stream.println (indent + "      _cls = _cls.getSuperclass ();");
    stream.println (indent + "  }");
    stream.println (indent + "  if (_cls == null)");
    stream.println (indent + "    throw new org.omg.CORBA.NO_IMPLEMENT (0, org.omg.CORBA.CompletionStatus.COMPLETED_NO);");
    stream.println ();
    stream.println (indent + "  String _className = _cls.getName ();");
    stream.println (indent + "  int _index = _className.lastIndexOf ('.');");
    stream.println (indent + "  String _helperName = _className.substring (0, _index + 1) + _className.substring (_index + 2, _className.length () - 5) + \"Helper\"; // 5 == \"State\".length");
    stream.println (indent + "  try");
    stream.println (indent + "  {");
    stream.println (indent + "    Class _helperClass = Class.forName (_helperName);");
    stream.println (indent + "    Class[] _formalParms = new Class [1];");
    stream.println (indent + "    _formalParms[0] = Class.forName (\"org.omg.CORBA.portable.InputStream\");");
    stream.println (indent + "    java.lang.reflect.Method _read = _helperClass.getMethod (\"read\", _formalParms);");
    stream.println (indent + "    Object[] _actualParms = new Object [1];");
    stream.println (indent + "    _actualParms[0] = istream;");
    stream.println (indent + "      " + name + " = (" + Util.javaStatefulName (entry) + ")_read.invoke (null, _actualParms);");
    stream.println (indent + "  }");
    stream.println (indent + "  catch (Exception e)");
    stream.println (indent + "  {");
    stream.println (indent + "    throw new org.omg.CORBA.NO_IMPLEMENT (0, org.omg.CORBA.CompletionStatus.COMPLETED_NO);");
    stream.println (indent + "  }");
    stream.println (indent + '}');

    // instantiate an implementation
    stream.println (indent + "else");
    stream.println (indent + '{');

    // Load the state
    readState (index, indent, name, (InterfaceEntry)entry, stream);

    stream.println (indent + '}');
    return index;
  } // structRead

  private void readState (int index, String indent, String name, InterfaceEntry entry, PrintWriter stream)
  {
    // First write the state from all parents
    Enumeration e = entry.derivedFrom ().elements ();
    while (e.hasMoreElements ())
    {
      InterfaceEntry parent = (InterfaceEntry)e.nextElement ();
      if (parent.state () != null)
      {
        if (parent.state ().size () > 0)
          readState (index, indent, name, parent, stream);
        break;
      }
    }

    // Now write the state for the local entry
    e = entry.state ().elements ();
    while (e.hasMoreElements ())
    {
      TypedefEntry member = ((InterfaceState)e.nextElement ()).entry;
      String tmpName = '_' + member.name () + "Tmp";
      Util.writeInitializer (indent + "  ", tmpName, "", member, stream);
      if (!member.arrayInfo ().isEmpty () || member.type () instanceof SequenceEntry || member.type () instanceof PrimitiveEntry || member.type () instanceof StringEntry)
        index = ((JavaGenerator)member.generator ()).read (index, indent + "  ", tmpName, member, stream);
      else
        stream.println (indent + "  " + tmpName + " = " + Util.helperName (member.type (), true) + ".read (istream);"); // <d61056>
      stream.println (indent + "  " + name + '.' + member.name () + " (" + tmpName + ");");
    }
  } // readState

  public void structHelperWrite (SymtabEntry entry, PrintWriter stream)
  {
    structWrite (0, "      ", "value", entry, stream);
  } // structHelperWrite

  public int structWrite (int index, String indent, String name, SymtabEntry entry, PrintWriter stream)
  {
    // The first element of the struct must be the name of the real interface.
    stream.println (indent + "Class _cls = " + name + ".getClass ();");
    stream.println (indent + "boolean _found = false;");
    stream.println (indent + "while (!_found && _cls != null)");
    stream.println (indent + "{");
    stream.println (indent + "  Class[] interfaces = _cls.getInterfaces ();");
    stream.println (indent + "  for (int i = 0; i < interfaces.length; ++i)");
    stream.println (indent + "    if (interfaces[i].getName ().indexOf (\"State\") > 0)");
    stream.println (indent + "    {");
    stream.println (indent + "      _cls = interfaces[i];");
    stream.println (indent + "      _found = true;");
    stream.println (indent + "      break;");
    stream.println (indent + "    }");
    stream.println (indent + "  if (!_found)");
    stream.println (indent + "    _cls = _cls.getSuperclass ();");
    stream.println (indent + '}');
    stream.println ();
    stream.println (indent + "if (_cls == null)");
    stream.println (indent + "  throw new org.omg.CORBA.MARSHAL (0, org.omg.CORBA.CompletionStatus.COMPLETED_NO);");
    stream.println ();
    stream.println (indent + "String _className = _cls.getName ();");
    stream.println (indent + "int _index = _className.lastIndexOf ('.');");
    stream.println (indent + "String _interfaceName = _className.substring (0, _index + 1) + _className.substring (_index + 2, _className.length () - 5); // 5 == \"State\".length");
    stream.println (indent + "ostream.write_string (_interfaceName.replace ('.', '/'));");

    // If _className != Util.javaName (entry), then call that class's helper class.
    stream.println ();
    stream.println (indent + "if (!_interfaceName.equals (\"" + Util.javaName (entry) + "\"))");
    stream.println (indent + '{');
    stream.println (indent + "  try");
    stream.println (indent + "  {");
    stream.println (indent + "    Class _helperClass = Class.forName (_interfaceName + \"Helper\");");
    stream.println (indent + "    Class[] _formalParms = new Class [2];");
    stream.println (indent + "    _formalParms[0] = Class.forName (\"org.omg.CORBA.portable.OutputStream\");");
    stream.println (indent + "    _formalParms[1] = _cls;");
    stream.println (indent + "    java.lang.reflect.Method _write = _helperClass.getMethod (\"write\", _formalParms);");
    stream.println (indent + "    Object[] _actualParms = new Object [2];");
    stream.println (indent + "    _actualParms[0] = ostream;");
    stream.println (indent + "    _actualParms[1] = " + name + ';');
    stream.println (indent + "    _write.invoke (null, _actualParms);");
    stream.println (indent + "  }");
    stream.println (indent + "  catch (Exception e)");
    stream.println (indent + "  {");
    stream.println (indent + "    throw new org.omg.CORBA.MARSHAL (0, org.omg.CORBA.CompletionStatus.COMPLETED_NO);");
    stream.println (indent + "  }");
    stream.println (indent + '}');

    stream.println (indent + "else");
    stream.println (indent + '{');

    writeState (index, indent, name, (InterfaceEntry)entry, stream);

    stream.println (indent + '}');
    return index;
  } // structWrite

  private void writeState (int index, String indent, String name, InterfaceEntry entry, PrintWriter stream)
  {
    // First write the state from all parents
    Enumeration e = entry.derivedFrom ().elements ();
    while (e.hasMoreElements ())
    {
      InterfaceEntry parent = (InterfaceEntry)e.nextElement ();
      if (parent.state () != null)
      {
        if (parent.state ().size () > 0)
          writeState (index, indent, name, parent, stream);
        break;
      }
    }

    // Now write the state for the local entry
    Vector members = entry.state ();
    for (int i = 0; i < members.size (); ++i)
    {
      TypedefEntry member = ((InterfaceState)members.elementAt (i)).entry;
      if (!member.arrayInfo ().isEmpty () || member.type () instanceof SequenceEntry || member.type () instanceof PrimitiveEntry || member.type () instanceof StringEntry)
        index = ((JavaGenerator)member.generator ()).write (index, indent + "  ", name + '.' + member.name () + "()", member, stream);
      else
        stream.println (indent + "  " + Util.helperName (member.type (), true) + ".write (ostream, " + name + '.' + member.name () + " ());"); // <d61056>
    }
  } // writeState
  */

  /**
   * @return true if the entry is for a CORBA pseudo-object.
   **/
  private boolean isPseudo(InterfaceEntry i) {
    java.lang.String fullname = i.fullName();
    if (fullname.equalsIgnoreCase("CORBA/TypeCode"))
        return true;
    if (fullname.equalsIgnoreCase("CORBA/Principal"))
        return true;
    if (fullname.equalsIgnoreCase("CORBA/ORB"))
        return true;
    if (fullname.equalsIgnoreCase("CORBA/Any"))
        return true;
    if (fullname.equalsIgnoreCase("CORBA/Context"))
        return true;
    if (fullname.equalsIgnoreCase("CORBA/ContextList"))
        return true;
    if (fullname.equalsIgnoreCase("CORBA/DynamicImplementation"))
        return true;
    if (fullname.equalsIgnoreCase("CORBA/Environment"))
        return true;
    if (fullname.equalsIgnoreCase("CORBA/ExceptionList"))
        return true;
    if (fullname.equalsIgnoreCase("CORBA/NVList"))
        return true;
    if (fullname.equalsIgnoreCase("CORBA/NamedValue"))
        return true;
    if (fullname.equalsIgnoreCase("CORBA/Request"))
        return true;
    if (fullname.equalsIgnoreCase("CORBA/ServerRequest"))
        return true;
    if (fullname.equalsIgnoreCase("CORBA/UserException"))
        return true;
    return false;
  }

  // From JavaGenerator
  ///////////////

  protected int            emit        = 0;
  protected Factories      factories   = null;

  protected Hashtable      symbolTable = null;
  protected InterfaceEntry i           = null;
  protected PrintWriter    stream      = null;

  // <f46082.03, f46838.1/.2/.3> Modify access to protected.
  protected static final   int SIGNATURE  = 1;
  protected static final   int OPERATIONS = 2;
  protected                int intfType   = 0;
} // class InterfaceGen
