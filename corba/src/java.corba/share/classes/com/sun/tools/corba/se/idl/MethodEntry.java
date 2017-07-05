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
// - What does oneway mean?

import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * This is the symbol table entry for methods.
 **/
public class MethodEntry extends SymtabEntry
{
  protected MethodEntry ()
  {
    super ();
  } // ctor

  protected MethodEntry (MethodEntry that)
  {
    super (that);
    _exceptionNames = (Vector)that._exceptionNames.clone ();
    _exceptions     = (Vector)that._exceptions.clone ();
    _contexts       = (Vector)that._contexts.clone ();
    _parameters     = (Vector)that._parameters.clone ();
    _oneway         = that._oneway;
  } // ctor

  protected MethodEntry (InterfaceEntry that, IDLID clone)
  {
    super (that, clone);
    if (module ().equals (""))
      module (name ());
    else if (!name ().equals (""))
      module (module () + "/" + name ());
  } // ctor

  public Object clone ()
  {
    return new MethodEntry (this);
  } // clone

  /** Invoke the method generator.
      @param symbolTable the symbol table is a hash table whose key is
       a fully qualified type name and whose value is a SymtabEntry or
       a subclass of SymtabEntry.
      @param stream the stream to which the generator should sent its output.
      @see SymtabEntry */
  public void generate (Hashtable symbolTable, PrintWriter stream)
  {
    methodGen.generate (symbolTable, this, stream);
  } // generate

  /** Access the method generator.
      @return an object which implements the MethodGen interface.
      @see MethodGen */
  public Generator generator ()
  {
    return methodGen;
  } // generator

  public void type (SymtabEntry newType)
  {
    super.type (newType);
    if (newType == null)
      typeName ("void");
  } // type

  /** Add an exception to the exception list. */
  public void addException (ExceptionEntry exception)
  {
    _exceptions.addElement (exception);
  } // addException

  /** This a a vector of the exceptions which this method raises. */
  public Vector exceptions ()
  {
    return _exceptions;
  } // exceptions

  /** Add an exception name to the list of exception names. */
  public void addExceptionName (String name)
  {
    _exceptionNames.addElement (name);
  } // addExceptionName

  /** This is a vector of strings, each of which is the full name of an
      exception which this method throws.  This vector corresponds to the
      exceptions vector.  The first element of this vector is the name
      of the first element of the exceptions vector, etc. */
  public Vector exceptionNames ()
  {
    return _exceptionNames;
  } // exceptionNames

  /* Add a context to the context list. */
  public void addContext (String context)
  {
    _contexts.addElement (context);
  } // addContext

  /** This is a vector of strings, each of which is the name of a context. */
  public Vector contexts ()
  {
    return _contexts;
  } // contexts

  /** Add a parameter to the parameter list. */
  public void addParameter (ParameterEntry parameter)
  {
    _parameters.addElement (parameter);
  } // addParameter

  /** This is a vector of ParameterEntry's.  They are the parameters on
      this method and their order in the vector is the order they appear
      on the method. */
  public Vector parameters ()
  {
    return _parameters;
  } // parameters

  /** Is this a oneway method? */
  public void oneway (boolean yes)
  {
    _oneway = yes;
  } // oneway

  /** Is this a oneway method? */
  public boolean oneway ()
  {
    return _oneway;
  } // oneway

  /** Is this a value method? */
  public void valueMethod (boolean yes)
  {
    _valueMethod = yes;
  } // valueMethod

  /** Is this a value method? */
  public boolean valueMethod ()
  {
    return _valueMethod;
  } // valueMethod

  void exceptionsAddElement (ExceptionEntry e)
  {
    addException (e);
    addExceptionName (e.fullName ());
  } // exceptionsAddElement

  private Vector  _exceptionNames = new Vector ();
  private Vector  _exceptions     = new Vector ();
  private Vector  _contexts       = new Vector ();
  private Vector  _parameters     = new Vector ();
  private boolean _oneway         = false;
  private boolean _valueMethod    = false;

  static MethodGen methodGen;
} // class MethodEntry
