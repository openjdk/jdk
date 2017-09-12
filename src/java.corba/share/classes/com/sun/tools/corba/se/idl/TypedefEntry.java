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
 * COMPONENT_NAME: idl.parser
 *
 * ORIGINS: 27
 *
 * Licensed Materials - Property of IBM
 * 5639-D57 (C) COPYRIGHT International Business Machines Corp. 1997, 1999
 * RMI-IIOP v1.0
 *
 */

package com.sun.tools.corba.se.idl;

// NOTES:

import java.io.PrintWriter;
import java.util.Hashtable;
import java.util.Vector;

import com.sun.tools.corba.se.idl.constExpr.Expression;

/**
 * This is the symbol table entry for typedefs.
 **/
public class TypedefEntry extends SymtabEntry
{
  protected TypedefEntry ()
  {
    super ();
  } // ctor

  protected TypedefEntry (TypedefEntry that)
  {
    super (that);
    _arrayInfo = (Vector)that._arrayInfo.clone ();
  } // ctor

  protected TypedefEntry (SymtabEntry that, IDLID clone)
  {
    super (that, clone);
    if (module ().equals (""))
      module (name ());
    else if (!name ().equals (""))
      module (module () + "/" + name ());
  } // ctor

  /** This method returns a vector of Expressions, each expression
      represents a dimension in an array.  A zero-length vector indicates
      no array information.*/
  public Vector arrayInfo ()
  {
    return _arrayInfo;
  } // arrayInfo

  public void addArrayInfo (Expression e)
  {
    _arrayInfo.addElement (e);
  } // addArrayInfo

  public Object clone ()
  {
    return new TypedefEntry (this);
  } // clone

  /** Invoke the typedef generator.
      @param symbolTable the symbol table is a hash table whose key is
       a fully qualified type name and whose value is a SymtabEntry or
       a subclass of SymtabEntry.
      @param stream the stream to which the generator should sent its output.
      @see SymtabEntry */
  public void generate (Hashtable symbolTable, PrintWriter stream)
  {
    typedefGen.generate (symbolTable, this, stream);
  } // generate

  public boolean isReferencable()
  {
    // A typedef is referencable if its component
    // type is.
    return type().isReferencable() ;
  }

  public void isReferencable( boolean value )
  {
    // NO-OP: this cannot be set for a typedef.
  }

  /** Access the typedef generator.
      @return an object which implements the TypedefGen interface.
      @see TypedefGen */
  public Generator generator ()
  {
    return typedefGen;
  } // generator

  private Vector _arrayInfo = new Vector ();

  static  TypedefGen typedefGen;
} // class TypedefEntry
