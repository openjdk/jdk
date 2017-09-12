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
 * This is the symbol table entry for forward declarations of interfaces.
 **/
public class ForwardEntry extends SymtabEntry implements InterfaceType
{
  protected ForwardEntry ()
  {
    super ();
  } // ctor

  protected ForwardEntry (ForwardEntry that)
  {
    super (that);
  } // ctor

  protected ForwardEntry (SymtabEntry that, IDLID clone)
  {
    super (that, clone);
    if (module ().equals (""))
      module (name ());
    else if (!name ().equals (""))
      module (module () + "/" + name ());
  } // ctor

  public Object clone ()
  {
    return new ForwardEntry (this);
  } // clone

  /** Invoke the forward declaration generator.
      @param symbolTable the symbol table is a hash table whose key is
       a fully qualified type name and whose value is a SymtabEntry or
       a subclass of SymtabEntry.
      @param stream the stream to which the generator should sent its output.
      @see SymtabEntry */
  public void generate (Hashtable symbolTable, PrintWriter stream)
  {
    forwardGen.generate (symbolTable, this, stream);
  } // generate

  /** Access the interface generator.
      @return an object which implements the InterfaceGen interface.
      @see InterfaceGen */
  public Generator generator ()
  {
    return forwardGen;
  } // generator

  static boolean replaceForwardDecl (InterfaceEntry interfaceEntry)
  {
    boolean result = true;
    try
    {
      ForwardEntry forwardEntry =
          (ForwardEntry)Parser.symbolTable.get (interfaceEntry.fullName ());
      if ( forwardEntry != null )
      {
        result = (interfaceEntry.getInterfaceType () ==
            forwardEntry.getInterfaceType ());
        forwardEntry.type (interfaceEntry);

        // If this interface has been forward declared, there are probably
        // other interfaces which derive from a ForwardEntry.  Replace
        // those ForwardEntry's with this InterfaceEntry:
        interfaceEntry.forwardedDerivers = forwardEntry.derivers;
        for ( Enumeration derivers = forwardEntry.derivers.elements();
              derivers.hasMoreElements(); )
          ((InterfaceEntry)derivers.nextElement ()).replaceForwardDecl (forwardEntry, interfaceEntry);

        // Replace the entry's whose types are forward declarations:
        for ( Enumeration types = forwardEntry.types.elements ();
              types.hasMoreElements (); )
          ((SymtabEntry)types.nextElement ()).type (interfaceEntry);
      }
    }
    catch (Exception exception)
    {}
    return result;
  } // replaceForwardDecl

  ///////////////
  // Implement interface InterfaceType

  public int getInterfaceType ()
  {
    return _type;
  }

  public void setInterfaceType (int type)
  {
    _type = type;
  }

  static ForwardGen forwardGen;
  Vector            derivers   = new Vector (); // Vector of InterfaceEntry's.
  Vector            types      = new Vector (); // Vector of the entry's whose type is a forward declaration.
  private int   _type  = InterfaceType.NORMAL; // interface type
} // class ForwardEntry
