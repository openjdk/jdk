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
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * This is the symbol table entry for structs.
 **/
public class StructEntry extends SymtabEntry
{
  protected StructEntry ()
  {
    super ();
  } // ctor

  protected StructEntry (StructEntry that)
  {
    super (that);
    if (!name ().equals (""))
    {
      module (module () + name ());
      name ("");
    }
    _members   = (Vector)that._members.clone ();
    _contained = (Vector)that._contained.clone ();
  } // ctor

  protected StructEntry (SymtabEntry that, IDLID clone)
  {
    super (that, clone);
    if (module ().equals (""))
      module (name ());
    else if (!name ().equals (""))
      module (module () + "/" + name ());
  } // ctor

  public Object clone ()
  {
    return new StructEntry (this);
  } // clone

  /** Invoke the struct generator.
      @param symbolTable the symbol table is a hash table whose key is
       a fully qualified type name and whose value is a SymtabEntry or
       a subclass of SymtabEntry.
      @param stream the stream to which the generator should sent its output.
      @see SymtabEntry */
  public void generate (Hashtable symbolTable, PrintWriter stream)
  {
    structGen.generate (symbolTable, this, stream);
  } // generate

  /** Access the struct generator.
      @returns an object which implements the StructGen interface.
      @see StructGen */
  public Generator generator ()
  {
    return structGen;
  } // generator

  /** Add a member to the member list. */
  public void addMember (TypedefEntry member)
  {
    _members.addElement (member);
  } // addMember

  /** This is a vector of TypedefEntry's.  In this context, only the name,
      type, and arrayInfo fields hold any meaning. */
  public Vector members ()
  {
    return _members;
  } // members

  public void addContained (SymtabEntry entry)
  {
    _contained.addElement (entry);
  } // addContained

  /** This is a vector of SymtabEntry's.  It itemizes any types which
      this struct contains.  It is different than the member list.
      For example:
      <pre>
      struct A
      {
        long x;
        Struct B
        {
          long a;
          long b;
        } y;
      }
      </pre>
      Struct B is contained within struct A.
      The members vector will contain entries for x and y. */
  public Vector contained ()
  {
    return _contained;
  } // contained

  private Vector _members   = new Vector ();
  private Vector _contained = new Vector ();

  static StructGen structGen;
} // class StructEntry
