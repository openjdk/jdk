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
// -D62023   <klr> New file to implement CORBA 2.4 RTF

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
public class Helper24 extends Helper
{
  /**
   * Public zero-argument constructor.
   **/
  public Helper24 ()
  {
  } // ctor

  /**
   * Generate the heading, including package, imports, class statements,
   * and open curly.
   * <d62023> - don't implement ValueHelper, make non-boxed helpers abstract
   **/
  protected void writeHeading ()
  {
    Util.writePackage (stream, entry, Util.HelperFile);
    Util.writeProlog (stream, stream.name ());

    // Transfer comment to target <30jul1997daz>.
    if (entry.comment () != null)
      entry.comment ().generate ("", stream);

    if (entry instanceof ValueBoxEntry) {
        stream.print   ("public final class " + helperClass);
        stream.println (" implements org.omg.CORBA.portable.BoxedValueHelper");
    }
    else
        stream.println ("abstract public class " + helperClass);
    stream.println ('{');
  }

  /**
   * Generate the instance variables.
   * <d62023> - no helper instance except for boxed valuetypes.
   *          - move truncatable_ids to mapped class.
   **/
  protected void writeInstVars ()
  {
    stream.println ("  private static String  _id = \"" + Util.stripLeadingUnderscoresFromID (entry.repositoryID ().ID ()) + "\";");
    if (entry instanceof ValueEntry)
    {
      stream.println ();
      if (entry instanceof ValueBoxEntry) {
          stream.println ("  private static " + helperClass + " _instance = new " + helperClass + " ();");
          stream.println ();
      }
    }
    stream.println ();
  } // writeInstVars

  /**
   * <d62023> generate members of BoxedValueHelper interface if boxed
   *
   * <d62023> Hook in here to write factory methods for non-boxed ValueTypes
   *          into Helper.
   **/
  protected void writeValueHelperInterface ()
  {
    if (entry instanceof ValueBoxEntry) {
        writeGetID ();
    } else if (entry instanceof ValueEntry) {
        writeHelperFactories ();
    }
  } // writeValueHelperInterface

  /**
   *
   **/
  protected void writeHelperFactories ()
  {
    Vector init = ((ValueEntry)entry).initializers ();
    if (init != null)
    {
      stream.println ();
      for (int i = 0; i < init.size (); i++)
      {
        MethodEntry element = (MethodEntry) init.elementAt (i);
        element.valueMethod (true); //tag value method if not tagged previously
        ((MethodGen24) element.generator ()). helperFactoryMethod (symbolTable, element, entry, stream);
      }
    }
  } // writeHelperFactories

  /**
   * <d62023> Generate constructors only for boxed valuetype helpers
   *            All other helpers are abstract.
   **/
  protected void writeCtors ()
  {
    if (entry instanceof ValueBoxEntry) {
        stream.println ("  public " + helperClass + "()");
        stream.println ("  {");
        stream.println ("  }");
        stream.println ();
    }
  } // writeCtors
}
