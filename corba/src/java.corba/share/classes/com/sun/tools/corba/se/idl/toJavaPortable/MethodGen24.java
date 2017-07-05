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
// -D62023   <klr> New file to implement CORBA 2.4 RTF
// -D62794   <klr> Fix problem with no-arg create functions

import java.io.PrintWriter;

import java.util.Enumeration;
import java.util.Vector;
import java.util.Hashtable;

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
public class MethodGen24 extends MethodGen
{
  /**
   * Public zero-argument constructor.
   **/
  public MethodGen24 ()
  {
  } // ctor

  /**
   * Print the parameter list for the factory method.
   * @param m The method to list parameters for
   * @param listTypes If try, declare the parms, otherwise just list them
   * @param stream The PrintWriter to print on
   */
  protected void writeParmList (MethodEntry m, boolean listTypes, PrintWriter stream) {
    boolean firstTime = true;
    Enumeration e = m.parameters ().elements ();
    while (e.hasMoreElements ())
    {
      if (firstTime)
        firstTime = false;
      else
        stream.print (", ");
      ParameterEntry parm = (ParameterEntry)e.nextElement ();
      if (listTypes) {
        writeParmType (parm.type (), parm.passType ());
        stream.print (' ');
      }
      // Print parm name
      stream.print (parm.name ());
      // end of parameter list
    }
  }

  /**
   * d62023 - write the methodEntry for a valuetype factory method into
   *          the Value Helper class. Contents from email from Simon,
   *          4/25/99.
   **/
  protected void helperFactoryMethod (Hashtable symbolTable, MethodEntry m, SymtabEntry t, PrintWriter stream)
  {
    this.symbolTable = symbolTable;
    this.m = m;
    this.stream = stream;
    String initializerName = m.name ();
    String typeName = Util.javaName (t);
    String factoryName = typeName + "ValueFactory";

    // Step 1. Print factory method decl up to parms.
    stream.print  ("  public static " + typeName + " " + initializerName +
            " (org.omg.CORBA.ORB $orb");
    if (!m.parameters ().isEmpty ())
      stream.print (", "); // <d62794>

    // Step 2. Print the declaration parameter list.
    writeParmList (m, true, stream);

    // Step 3. Print the body of the factory method
    stream.println (")");
    stream.println ("  {");
    stream.println ("    try {");
    stream.println ("      " + factoryName + " $factory = (" + factoryName + ")");
    stream.println ("          ((org.omg.CORBA_2_3.ORB) $orb).lookup_value_factory(id());");
    stream.print   ("      return $factory." + initializerName + " (");
    writeParmList (m, false, stream);
    stream.println (");");
    stream.println ("    } catch (ClassCastException $ex) {");
    stream.println ("      throw new org.omg.CORBA.BAD_PARAM ();");
    stream.println ("    }");
    stream.println ("  }");
    stream.println ();
  } // helperFactoryMethod

  /**
   * d62023 - write an abstract method definition
   **/
  protected void abstractMethod (Hashtable symbolTable, MethodEntry m, PrintWriter stream)
  {
    this.symbolTable = symbolTable;
    this.m           = m;
    this.stream      = stream;
    if (m.comment () != null)
      m.comment ().generate ("  ", stream);
    stream.print ("  ");
    stream.print ("public abstract ");
    writeMethodSignature ();
    stream.println (";");
    stream.println ();
  } // abstractMethod

  /**
   * d62023   - write a default factory method implementation for the
   *            {@code <value>DefaultFactory}. m is a methodEntry for a factory
   *            method contained in a non-abstract ValueEntry.
   **/
  protected void defaultFactoryMethod (Hashtable symbolTable, MethodEntry m, PrintWriter stream)
  {
    this.symbolTable = symbolTable;
    this.m           = m;
    this.stream      = stream;
    String typeName = m.container (). name ();
    stream.println ();
    if (m.comment () != null)
      m.comment ().generate ("  ", stream);
    stream.print   ("  public " + typeName + " " + m.name () + " (");
    writeParmList  (m, true, stream);
    stream.println (")");
    stream.println ("  {");
    stream.print   ("    return new " + typeName + "Impl (");
    writeParmList (m, false, stream);
    stream.println (");");
    stream.println ("  }");
  } // defaultFactoryMethod

  /**
   * d62023 - remove all valueInitializer junk
   **/
  protected void writeMethodSignature ()
  {
    // Step 0.  Print the return type and name.
    // A return type of null indicates the "void" return type. If m is a
    // Valuetype factory method, it has a null return type,
    if (m.type () == null)
    {
        // if factory method, result type is container
        if (isValueInitializer ())
            stream.print (m.container ().name ());
        else
            stream.print ("void");
    }
    else
    {
      stream.print (Util.javaName (m.type ()));
    }
    stream.print (' ' + m.name () + " (");

    // Step 1.  Print the parameter list.
    boolean firstTime = true;
    Enumeration e = m.parameters ().elements ();
    while (e.hasMoreElements ())
    {
      if (firstTime)
        firstTime = false;
      else
        stream.print (", ");
      ParameterEntry parm = (ParameterEntry)e.nextElement ();

      writeParmType (parm.type (), parm.passType ());

      // Print parm name
      stream.print (' ' + parm.name ());
    }

    // Step 2.  Add the context parameter if necessary.
    if (m.contexts ().size () > 0)
    {
      if (!firstTime)
        stream.print (", ");
      stream.print ("org.omg.CORBA.Context $context");
    }

    // Step 3.  Print the throws clause (if necessary).
    if (m.exceptions ().size () > 0)
    {
      stream.print (") throws ");
      e = m.exceptions ().elements ();
      firstTime = true;
      while (e.hasMoreElements ())
      {
        if (firstTime)
          firstTime = false;
        else
          stream.print (", ");
        stream.print (Util.javaName ((SymtabEntry)e.nextElement ()));
      }
    }
    else
      stream.print (')');
  } // writeMethodSignature

  /**
   * d62023 - delete method templates for valuetypes
   **/
  protected void interfaceMethod (Hashtable symbolTable, MethodEntry m, PrintWriter stream)
  {
    this.symbolTable = symbolTable;
    this.m           = m;
    this.stream      = stream;
    if (m.comment () != null)
      m.comment ().generate ("  ", stream);
    stream.print ("  ");
    writeMethodSignature ();
    stream.println (";");
  } // interfaceMethod
}
