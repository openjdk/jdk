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

import com.sun.tools.corba.se.idl.constExpr.ExprFactory;

public class Noop implements
   AttributeGen,    ConstGen,   EnumGen,      ExceptionGen, ForwardGen,
   ForwardValueGen, IncludeGen, InterfaceGen, ValueGen,     ValueBoxGen,
   MethodGen,       ModuleGen,  NativeGen, ParameterGen, PragmaGen,
   PrimitiveGen, SequenceGen,     StringGen,  StructGen,    TypedefGen,
   UnionGen, GenFactory
{
  public void generate (Hashtable symbolTable, AttributeEntry entry, PrintWriter stream)
  {
  } // generate

  public void generate (Hashtable symbolTable, ConstEntry entry, PrintWriter stream)
  {
  } // generate

  public void generate (Hashtable symbolTable, EnumEntry entry, PrintWriter stream)
  {
  } // generate

  public void generate (Hashtable symbolTable, ExceptionEntry entry, PrintWriter stream)
  {
  } // generate

  public void generate (Hashtable symbolTable, ForwardEntry entry, PrintWriter stream)
  {
  } // generate

  public void generate (Hashtable symbolTable, ForwardValueEntry entry, PrintWriter stream)
  {
  } // generate

  public void generate (Hashtable symbolTable, IncludeEntry entry, PrintWriter stream)
  {
  } // generate

  public void generate (Hashtable symbolTable, InterfaceEntry entry, PrintWriter stream)
  {
  } // generate

  public void generate (Hashtable symbolTable, ValueEntry entry, PrintWriter stream)
  {
  } // generate

  public void generate (Hashtable symbolTable, ValueBoxEntry entry, PrintWriter stream)
  {
  } // generate

  public void generate (Hashtable symbolTable, MethodEntry entry, PrintWriter stream)
  {
  } // generate

  public void generate (Hashtable symbolTable, ModuleEntry entry, PrintWriter stream)
  {
  } // generate

  public void generate (Hashtable symbolTable, ParameterEntry entry, PrintWriter stream)
  {
  } // generate

  public void generate (Hashtable symbolTable, PragmaEntry entry, PrintWriter stream)
  {
  } // generate

  public void generate (Hashtable symbolTable, PrimitiveEntry entry, PrintWriter stream)
  {
  } // generate

  public void generate (Hashtable symbolTable, SequenceEntry entry, PrintWriter stream)
  {
  } // generate

  public void generate (Hashtable symbolTable, StringEntry entry, PrintWriter stream)
  {
  } // generate

  public void generate (Hashtable symbolTable, StructEntry entry, PrintWriter stream)
  {
  } // generate

  public void generate (Hashtable symbolTable, TypedefEntry entry, PrintWriter stream)
  {
  } // generate

  public void generate (Hashtable symbolTable, UnionEntry entry, PrintWriter stream)
  {
  } // generate

  public void generate (Hashtable symbolTable, NativeEntry entry, PrintWriter stream)
  {
  } // generate

  // For GenFactory
  public AttributeGen createAttributeGen ()
  {
    return null;
  } // createAttributeGen

  public ConstGen createConstGen ()
  {
    return null;
  } // createConstGen

  public EnumGen createEnumGen ()
  {
    return null;
  } // createEnumGen

  public ExceptionGen createExceptionGen ()
  {
    return null;
  } // createExceptionGen

  public ForwardGen createForwardGen ()
  {
    return null;
  } // createForwardGen

  public ForwardValueGen createForwardValueGen ()
  {
    return null;
  } // createForwardValueGen

  public IncludeGen createIncludeGen ()
  {
    return null;
  } // createIncludeGen

  public InterfaceGen createInterfaceGen ()
  {
    return null;
  } // createInterfaceGen

  public ValueGen createValueGen ()
  {
    return null;
  } // createValueGen

  public ValueBoxGen createValueBoxGen ()
  {
    return null;
  } // createValueBoxGen

  public MethodGen createMethodGen ()
  {
    return null;
  } // createMethodGen

  public ModuleGen createModuleGen ()
  {
    return null;
  } // createModuleGen

  public NativeGen createNativeGen ()
  {
    return null;
  } // createNativeGen

  public ParameterGen createParameterGen ()
  {
    return null;
  } // createParameterGen

  public PragmaGen createPragmaGen ()
  {
    return null;
  } // createPragmaGen

  public PrimitiveGen createPrimitiveGen ()
  {
    return null;
  } // createPrimitiveGen

  public SequenceGen createSequenceGen ()
  {
    return null;
  } // createSequenceGen

  public StringGen createStringGen ()
  {
    return null;
  } // createStringGen

  public StructGen createStructGen ()
  {
    return null;
  } // createStructGen

  public TypedefGen createTypedefGen ()
  {
    return null;
  } // createTypedefGen

  public UnionGen createUnionGen ()
  {
    return null;
  } // createUnionGen
} // class Noop
