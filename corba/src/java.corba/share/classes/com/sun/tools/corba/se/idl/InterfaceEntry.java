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
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * This is the symbol table entry for interfaces.
 **/
public class InterfaceEntry extends SymtabEntry implements InterfaceType
{

  protected InterfaceEntry ()
  {
    super ();
  } // ctor

  protected InterfaceEntry (InterfaceEntry that)
  {
    super (that);
    _derivedFromNames = (Vector)that._derivedFromNames.clone ();
    _derivedFrom      = (Vector)that._derivedFrom.clone ();
    _methods          = (Vector)that._methods.clone ();
    _allMethods       = (Vector)that._allMethods.clone ();
    forwardedDerivers = (Vector)that.forwardedDerivers.clone ();
    _contained        = (Vector)that._contained.clone ();
    _interfaceType    = that._interfaceType;
  } // ctor

  protected InterfaceEntry (SymtabEntry that, IDLID clone)
  {
    super (that, clone);
    if (module ().equals (""))
      module (name ());
    else if (!name ().equals (""))
      module (module () + "/" + name ());
  } // ctor

  public boolean isAbstract()
  {
      return _interfaceType == ABSTRACT ;
  }

  public boolean isLocal()
  {
      return _interfaceType == LOCAL ;
  }

  public boolean isLocalServant()
  {
      return _interfaceType == LOCALSERVANT ;
  }

  public boolean isLocalSignature()
  {
      return _interfaceType == LOCAL_SIGNATURE_ONLY ;
  }

  public Object clone ()
  {
    return new InterfaceEntry (this);
  } // clone

  /** Invoke the interface generator.
      @param symbolTable the symbol table is a hash table whose key is
       a fully qualified type name and whose value is a SymtabEntry or
       a subclass of SymtabEntry.
      @param stream the stream to which the generator should sent its output.
      @see SymtabEntry */
  public void generate (Hashtable symbolTable, PrintWriter stream)
  {
    interfaceGen.generate (symbolTable, this, stream);
  } // generate

  /** Access the interface generator.
      @returns an object which implements the InterfaceGen interface.
      @see InterfaceGen */
  public Generator generator ()
  {
    return interfaceGen;
  } // generator

  /** Add an InterfaceEntry to the list of interfaces which this interface
      is derivedFrom.  During parsing, the parameter to this method COULD
      be a ForwardEntry, but when parsing is complete, calling derivedFrom
      will return a vector which only contains InterfaceEntry's. */
  public void addDerivedFrom (SymtabEntry derivedFrom)
  {
    _derivedFrom.addElement (derivedFrom);
  } // addDerivedFrom

  /** This method returns a vector of InterfaceEntry's. */
  public Vector derivedFrom ()
  {
    return _derivedFrom;
  } // derivedFrom

  /** Add to the list of derivedFrom names. */
  public void addDerivedFromName (String name)
  {
    _derivedFromNames.addElement (name);
  } // addDerivedFromName

  /** This method returns a vector of Strings, each of which is a fully
      qualified name of an interface. This vector corresponds to the
      derivedFrom vector.  The first element of this vector is the name
      of the first element of the derivedFrom vector, etc. */
  public Vector derivedFromNames ()
  {
    return _derivedFromNames;
  } // derivedFromNames

  /** Add a method/attribute to the list of methods. */
  public void addMethod (MethodEntry method)
  {
    _methods.addElement (method);
  } // addMethod

  /** This is a vector of MethodEntry's.  These are the methods and
      attributes contained within this Interface. */
  public Vector methods ()
  {
    return _methods;
  } // methods

  /** Add a symbol table entry to this interface's contained vector. */
  public void addContained (SymtabEntry entry)
  {
    _contained.addElement (entry);
  } // addContained

  /** This is a vector of SymtabEntry's.  Valid entries in this vector are:
      AttributeEntry, ConstEntry, EnumEntry, ExceptionEntry, MethodEntry,
      StructEntry, NativeEntry, TypedefEntry, UnionEntry.
      Note that the methods vector is a subset of this vector. */
  public Vector contained ()
  {
    return _contained;
  } // contained

  void methodsAddElement (MethodEntry method, Scanner scanner)
  {
    if (verifyMethod (method, scanner, false))
    {
      addMethod (method);
      _allMethods.addElement (method);

      // Add this method to the 'allMethods' list of any interfaces
      // which may have inherited this one when it was a forward
      // reference.
      addToForwardedAllMethods (method, scanner);
    }
  } // methodsAddElement

  void addToForwardedAllMethods (MethodEntry method, Scanner scanner)
  {
    Enumeration e = forwardedDerivers.elements ();
    while (e.hasMoreElements ())
    {
      InterfaceEntry derived = (InterfaceEntry)e.nextElement ();
      if (derived.verifyMethod (method, scanner, true))
        derived._allMethods.addElement (method);
    }
  } // addToForwardedAllMethods

  // Make sure a method by this name doesn't exist in this class or
  // in this class's parents
  private boolean verifyMethod (MethodEntry method, Scanner scanner, boolean clash)
  {
    boolean unique = true;
    String  lcName = method.name ().toLowerCase ();
    Enumeration e  = _allMethods.elements ();
    while (e.hasMoreElements ())
    {
      MethodEntry emethod = (MethodEntry)e.nextElement ();

      // Make sure the method doesn't exist either in its
      // original name or in all lower case.  In IDL, identifiers
      // which differ only in case are collisions.
      String lceName = emethod.name ().toLowerCase ();
      if (method != emethod && lcName.equals (lceName))
      {
        if (clash)
          ParseException.methodClash (scanner, fullName (), method.name ());
        else
          ParseException.alreadyDeclared (scanner, method.name ());
        unique = false;
        break;
      }
    }
    return unique;
  } // verifyMethod

  void derivedFromAddElement (SymtabEntry e, Scanner scanner)
  {
    addDerivedFrom (e);
    addDerivedFromName (e.fullName ());
    addParentType( e, scanner );
  } // derivedFromAddElement

  void addParentType (SymtabEntry e, Scanner scanner)
  {
    if (e instanceof ForwardEntry)
      addToDerivers ((ForwardEntry)e);
    else
    { // e instanceof InterfaceEntry
      InterfaceEntry derivedFrom = (InterfaceEntry)e;

      // Compare all of the parent's methods to the methods on this
      // interface, looking for name clashes:
      for ( Enumeration enumeration = derivedFrom._allMethods.elements ();
            enumeration.hasMoreElements (); )
      {
        MethodEntry method = (MethodEntry)enumeration.nextElement ();
        if ( verifyMethod (method, scanner, true))
          _allMethods.addElement (method);

        // Add this method to the 'allMethods' list of any interfaces
        // which may have inherited this one when it was a forward
        // reference:
        addToForwardedAllMethods (method, scanner);
      }

      // If any of the parent's parents are forward entries, make
      // sure this interface gets added to their derivers list so
      // that when the forward entry is defined, the 'allMethods'
      // list of this interface can be updated.
      lookForForwardEntrys (scanner, derivedFrom);
    }
  }  // addParentType

  private void lookForForwardEntrys (Scanner scanner, InterfaceEntry entry)
  {
    Enumeration parents = entry.derivedFrom ().elements ();
    while (parents.hasMoreElements ())
    {
      SymtabEntry parent = (SymtabEntry)parents.nextElement ();
      if (parent instanceof ForwardEntry)
        addToDerivers ((ForwardEntry)parent);
      else if (parent == entry)
        ParseException.selfInherit (scanner, entry.fullName ());
      else // it must be an InterfaceEntry
        lookForForwardEntrys (scanner, (InterfaceEntry)parent);
    }
  } // lookForForwardEntrys

  public boolean replaceForwardDecl (ForwardEntry oldEntry, InterfaceEntry newEntry)
  {
    int index = _derivedFrom.indexOf( oldEntry );
    if ( index >= 0 )
      _derivedFrom.setElementAt( newEntry, index );
    return (index >= 0);
  } // replaceForwardDecl

  private void addToDerivers (ForwardEntry forward)
  {
    // Add this interface to the derivers list on the forward entry
    // so that when the forward entry is defined, the 'allMethods'
    // list of this interface can be updated.
    forward.derivers.addElement (this);
    Enumeration e = forwardedDerivers.elements ();
    while (e.hasMoreElements ())
      forward.derivers.addElement ((InterfaceEntry)e.nextElement ());
  } // addToDerivers

  /** This method returns a vector of the elements in the state block.
      If it is null, this is not a stateful interface.  If it is non-null,
      but of zero length, then it is still stateful; it has no state
      entries itself, but it has an ancestor which does. */
  public Vector state ()
  {
    return _state;
  } // state

  public void initState ()
  {
    _state = new Vector ();
  } // initState

  public void addStateElement (InterfaceState state, Scanner scanner)
  {
    if (_state == null)
      _state = new Vector ();
    String name = state.entry.name ();
    for (Enumeration e = _state.elements (); e.hasMoreElements ();)
      if (name.equals (((InterfaceState) e.nextElement ()).entry.name ()))
        ParseException.duplicateState (scanner, name);
    _state.addElement (state);
  } // state

  public int getInterfaceType ()
  {
    return _interfaceType;
  }

  public void setInterfaceType (int type)
  {
    _interfaceType = type;
  }

  /** Get the allMethods vector. */
  public Vector allMethods ()
  {
    return _allMethods;
  }

  private Vector  _derivedFromNames = new Vector();
  private Vector  _derivedFrom      = new Vector();
  private Vector  _methods          = new Vector();
          Vector  _allMethods       = new Vector();
          Vector  forwardedDerivers = new Vector();
  private Vector  _contained        = new Vector();
  private Vector  _state            = null;
  private int _interfaceType         = NORMAL;

  static  InterfaceGen interfaceGen;
} // class InterfaceEntry
