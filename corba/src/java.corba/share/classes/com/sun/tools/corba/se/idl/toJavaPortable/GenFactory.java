/*
 * Copyright (c) 1999, 2000, Oracle and/or its affiliates. All rights reserved.
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

/**
 *
 **/
public class GenFactory implements com.sun.tools.corba.se.idl.GenFactory
{

  public com.sun.tools.corba.se.idl.AttributeGen createAttributeGen ()
  {
    if (Util.corbaLevel (2.4f, 99.0f)) // <d60023>
      return new AttributeGen24 ();
    else
      return new AttributeGen ();
  } // createAttributeGen

  public com.sun.tools.corba.se.idl.ConstGen createConstGen ()
  {
    return new ConstGen ();
  } // createConstGen

  public com.sun.tools.corba.se.idl.NativeGen createNativeGen ()
  {
    return new NativeGen ();
  } // createNativeGen

  public com.sun.tools.corba.se.idl.EnumGen createEnumGen ()
  {
    return new EnumGen ();
  } // createEnumGen

  public com.sun.tools.corba.se.idl.ExceptionGen createExceptionGen ()
  {
    return new ExceptionGen ();
  } // createExceptionGen

  public com.sun.tools.corba.se.idl.ForwardGen createForwardGen ()
  {
    return null;
  } // createForwardGen

  public com.sun.tools.corba.se.idl.ForwardValueGen createForwardValueGen ()
  {
    return null;
  } // createForwardValueGen

  public com.sun.tools.corba.se.idl.IncludeGen createIncludeGen ()
  {
    return null;
  } // createIncludeGen

  public com.sun.tools.corba.se.idl.InterfaceGen createInterfaceGen ()
  {
    return new InterfaceGen ();
  } // createInterfaceGen

  public com.sun.tools.corba.se.idl.ValueGen createValueGen ()
  {
    if (Util.corbaLevel (2.4f, 99.0f)) // <d60023>
      return new ValueGen24 ();
    else
      return new ValueGen ();
  } // createValueGen

  public com.sun.tools.corba.se.idl.ValueBoxGen createValueBoxGen ()
  {
    if (Util.corbaLevel (2.4f, 99.0f)) // <d60023>
      return new ValueBoxGen24 ();
    else
      return new ValueBoxGen ();
  } // createValueBoxGen

  public com.sun.tools.corba.se.idl.MethodGen createMethodGen ()
  {
    if (Util.corbaLevel (2.4f, 99.0f)) // <d60023>
      return new MethodGen24 ();
    else
      return new MethodGen ();
  } // createMethodGen

  public com.sun.tools.corba.se.idl.ModuleGen createModuleGen ()
  {
    return new ModuleGen ();
  } // createModuleGen

  public com.sun.tools.corba.se.idl.ParameterGen createParameterGen ()
  {
    return null;
  } // createParameterGen

  public com.sun.tools.corba.se.idl.PragmaGen createPragmaGen ()
  {
    return null;
  } // createPragmaGen

  public com.sun.tools.corba.se.idl.PrimitiveGen createPrimitiveGen ()
  {
    return new PrimitiveGen ();
  } // createPrimitiveGen

  public com.sun.tools.corba.se.idl.SequenceGen createSequenceGen ()
  {
    return new SequenceGen ();
  } // createSequenceGen

  public com.sun.tools.corba.se.idl.StringGen createStringGen ()
  {
    return new StringGen ();
  } // createSequenceGen

  public com.sun.tools.corba.se.idl.StructGen createStructGen ()
  {
    return new StructGen ();
  } // createStructGen

  public com.sun.tools.corba.se.idl.TypedefGen createTypedefGen ()
  {
    return new TypedefGen ();
  } // createTypedefGen

  public com.sun.tools.corba.se.idl.UnionGen createUnionGen ()
  {
    return new UnionGen ();
  } // createUnionGen
} // class GenFactory
