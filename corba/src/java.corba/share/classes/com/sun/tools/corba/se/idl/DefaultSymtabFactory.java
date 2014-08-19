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
 * This factory constructs the default symbol table entries, namely,
 * those declared within the package com.sun.tools.corba.se.idl.
 **/
public class DefaultSymtabFactory implements SymtabFactory
{
  public AttributeEntry attributeEntry ()
  {
    return new AttributeEntry ();
  } // attributeEntry

  public AttributeEntry attributeEntry (InterfaceEntry container, IDLID id)
  {
    return new AttributeEntry (container, id);
  } // attributeEntry

  public ConstEntry constEntry ()
  {
    return new ConstEntry ();
  } // constEntry

  public ConstEntry constEntry (SymtabEntry container, IDLID id)
  {
    return new ConstEntry (container, id);
  } // constEntry

  public NativeEntry nativeEntry ()
  {
    return new NativeEntry ();
  } // interfaceEntry

  public NativeEntry nativeEntry (SymtabEntry container, IDLID id)
  {
    return new NativeEntry (container, id);
  } // interfaceEntry

  public EnumEntry enumEntry ()
  {
    return new EnumEntry ();
  } // enumEntry

  public EnumEntry enumEntry (SymtabEntry container, IDLID id)
  {
    return new EnumEntry (container, id);
  } // enumEntry

  public ExceptionEntry exceptionEntry ()
  {
    return new ExceptionEntry ();
  } // exceptionEntry

  public ExceptionEntry exceptionEntry (SymtabEntry container, IDLID id)
  {
    return new ExceptionEntry (container, id);
  } // exceptionEntry

  public ForwardEntry forwardEntry ()
  {
    return new ForwardEntry ();
  } // forwardEntry

  public ForwardEntry forwardEntry (ModuleEntry container, IDLID id)
  {
    return new ForwardEntry (container, id);
  } // forwardEntry

  public ForwardValueEntry forwardValueEntry ()
  {
    return new ForwardValueEntry ();
  } // forwardValueEntry

  public ForwardValueEntry forwardValueEntry (ModuleEntry container, IDLID id)
  {
    return new ForwardValueEntry (container, id);
  } // forwardValueEntry

  public IncludeEntry includeEntry ()
  {
    return new IncludeEntry ();
  } // includeEntry

  public IncludeEntry includeEntry (SymtabEntry container)
  {
    return new IncludeEntry (container);
  } // includeEntry

  public InterfaceEntry interfaceEntry ()
  {
    return new InterfaceEntry ();
  } // interfaceEntry

  public InterfaceEntry interfaceEntry (ModuleEntry container, IDLID id)
  {
    return new InterfaceEntry (container, id);
  } // interfaceEntry

  public ValueEntry valueEntry ()
  {
    return new ValueEntry ();
  } // valueEntry

  public ValueEntry valueEntry (ModuleEntry container, IDLID id)
  {
    return new ValueEntry (container, id);
  } // valueEntry

  public ValueBoxEntry valueBoxEntry ()
  {
    return new ValueBoxEntry ();
  } // valueBoxEntry

  public ValueBoxEntry valueBoxEntry (ModuleEntry container, IDLID id)
  {
    return new ValueBoxEntry (container, id);
  } // valueBoxEntry

  public MethodEntry methodEntry ()
  {
    return new MethodEntry ();
  } // methodEntry

  public MethodEntry methodEntry (InterfaceEntry container, IDLID id)
  {
    return new MethodEntry (container, id);
  } // methodEntry

  public ModuleEntry moduleEntry ()
  {
    return new ModuleEntry ();
  } // moduleEntry

  public ModuleEntry moduleEntry (ModuleEntry container, IDLID id)
  {
    return new ModuleEntry (container, id);
  } // moduleEntry

  public ParameterEntry parameterEntry ()
  {
    return new ParameterEntry ();
  } // parameterEntry

  public ParameterEntry parameterEntry (MethodEntry container, IDLID id)
  {
    return new ParameterEntry (container, id);
  } // parameterEntry

  public PragmaEntry pragmaEntry ()
  {
    return new PragmaEntry ();
  } // pragmaEntry

  public PragmaEntry pragmaEntry (SymtabEntry container)
  {
    return new PragmaEntry (container);
  } // pragmaEntry

  public PrimitiveEntry primitiveEntry ()
  {
    return new PrimitiveEntry ();
  } // primitiveEntry

  /** "name" can be, but is not limited to, the primitive idl type names:
      'char', 'octet', 'short', 'long', etc.  The reason it is not limited
      to these is that, as an extender, you may wish to override these names.
      For instance, when generating Java code, octet translates to byte, so
      there is an entry in Compile.overrideNames:  <"octet", "byte"> and a
      PrimitiveEntry in the symbol table for "byte". */
  public PrimitiveEntry primitiveEntry (String name)
  {
    return new PrimitiveEntry (name);
  } // primitiveEntry

  public SequenceEntry sequenceEntry ()
  {
    return new SequenceEntry ();
  } // sequenceEntry

  public SequenceEntry sequenceEntry (SymtabEntry container, IDLID id)
  {
    return new SequenceEntry (container, id);
  } // sequenceEntry

  public StringEntry stringEntry ()
  {
    return new StringEntry ();
  } // stringEntry

  public StructEntry structEntry ()
  {
    return new StructEntry ();
  } // structEntry

  public StructEntry structEntry (SymtabEntry container, IDLID id)
  {
    return new StructEntry (container, id);
  } // structEntry

  public TypedefEntry typedefEntry ()
  {
    return new TypedefEntry ();
  } // typedefEntry

  public TypedefEntry typedefEntry (SymtabEntry container, IDLID id)
  {
    return new TypedefEntry (container, id);
  } // typedefEntry

  public UnionEntry unionEntry ()
  {
    return new UnionEntry ();
  } // unionEntry

  public UnionEntry unionEntry (SymtabEntry container, IDLID id)
  {
    return new UnionEntry (container, id);
  } // unionEntry

} // interface DefaultSymtabFactory
