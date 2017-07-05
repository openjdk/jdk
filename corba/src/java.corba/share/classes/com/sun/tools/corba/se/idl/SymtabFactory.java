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

/**
 * Each entry must have three ways in which it can be instantiated:
 * <ul>
 * <li>with no parameters;
 * <li>cloned from a copy of itself;
 * <li>the normal-use instantiation (usually with 2 parameters:  the container and the id of the container).
 * </ul>
 **/
public interface SymtabFactory
{
  AttributeEntry attributeEntry ();
  AttributeEntry attributeEntry (InterfaceEntry container, IDLID id);

  ConstEntry constEntry ();
  ConstEntry constEntry (SymtabEntry container, IDLID id);

  NativeEntry nativeEntry ();
  NativeEntry nativeEntry (SymtabEntry container, IDLID id);

  EnumEntry enumEntry ();
  EnumEntry enumEntry (SymtabEntry container, IDLID id);

  ExceptionEntry exceptionEntry ();
  ExceptionEntry exceptionEntry (SymtabEntry container, IDLID id);

  ForwardEntry forwardEntry ();
  ForwardEntry forwardEntry (ModuleEntry container, IDLID id);

  ForwardValueEntry forwardValueEntry ();
  ForwardValueEntry forwardValueEntry (ModuleEntry container, IDLID id);

  IncludeEntry includeEntry ();
  IncludeEntry includeEntry (SymtabEntry container);

  InterfaceEntry interfaceEntry ();
  InterfaceEntry interfaceEntry (ModuleEntry container, IDLID id);

  ValueEntry valueEntry ();
  ValueEntry valueEntry (ModuleEntry container, IDLID id);

  ValueBoxEntry valueBoxEntry ();
  ValueBoxEntry valueBoxEntry (ModuleEntry container, IDLID id);

  MethodEntry methodEntry ();
  MethodEntry methodEntry (InterfaceEntry container, IDLID id);

  ModuleEntry moduleEntry ();
  ModuleEntry moduleEntry (ModuleEntry container, IDLID id);

  ParameterEntry parameterEntry ();
  ParameterEntry parameterEntry (MethodEntry container, IDLID id);

  PragmaEntry pragmaEntry ();
  PragmaEntry pragmaEntry (SymtabEntry container);

  PrimitiveEntry primitiveEntry ();
  /** name can be, but is not limited to, the primitive idl type names:
      char, octet, short, long, etc.  The reason it is not limited to
      these is that, as an extender, you may wish to override these names.
      For instance, when generating Java code, octet translates to byte,
      so there is an entry in Compile.overrideNames: {@code <"octet", "byte">}
      and a PrimitiveEntry in the symbol table for "byte". */
  PrimitiveEntry primitiveEntry (String name);

  SequenceEntry sequenceEntry ();
  SequenceEntry sequenceEntry (SymtabEntry container, IDLID id);

  StringEntry stringEntry ();

  StructEntry structEntry ();
  StructEntry structEntry (SymtabEntry container, IDLID id);

  TypedefEntry typedefEntry ();
  TypedefEntry typedefEntry (SymtabEntry container, IDLID id);

  UnionEntry unionEntry ();
  UnionEntry unionEntry (SymtabEntry container, IDLID id);
} // interface SymtabFactory
