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
// -D62023<klr> Add corbaLevel=2.4

/**
 *
 **/
public class Factories extends com.sun.tools.corba.se.idl.Factories
{
  public com.sun.tools.corba.se.idl.GenFactory genFactory ()
  {
    return new GenFactory ();
  } // genFactory

  public com.sun.tools.corba.se.idl.Arguments arguments ()
  {
    return new Arguments ();
  } // arguments

  public String[] languageKeywords ()
  {
  // These are Java keywords that are not also IDL keywords.
    return keywords;
  } // languageKeywords

  static String[] keywords =
    {"abstract",   "break",     "byte",
     "catch",      "class",     "continue",
     "do",         "else",      "extends",
     "false",      "final",     "finally",
     "for",        "goto",      "if",
     "implements", "import",    "instanceof",
     "int",        "interface", "native",
     "new",        "null",      "operator",
     "outer",      "package",   "private",
     "protected",  "public",    "return",
     "static",     "super",     "synchronized",
     "this",       "throw",     "throws",
     "transient",  "true",      "try",
     "volatile",   "while",
// Special reserved suffixes:
     "+Helper",    "+Holder",   "+Package",
// These following are not strictly keywords.  They
// are methods on java.lang.Object and, as such, must
// not have conflicts with methods defined on IDL
// interfaces.  Treat them the same as keywords.
     "clone",      "equals",       "finalize",
     "getClass",   "hashCode",     "notify",
     "notifyAll",  "toString",     "wait"};

  ///////////////
  // toJava-specific factory methods

  private Helper _helper = null;        // <62023>
  public Helper helper ()
  {
    if (_helper == null)
      if (Util.corbaLevel (2.4f, 99.0f)) // <d60023>
         _helper = new Helper24 ();     // <d60023>
      else
         _helper = new Helper ();
    return _helper;
  } // helper

  private ValueFactory _valueFactory = null;        // <62023>
  public ValueFactory valueFactory ()
  {
    if (_valueFactory == null)
      if (Util.corbaLevel (2.4f, 99.0f)) // <d60023>
         _valueFactory = new ValueFactory ();     // <d60023>
      // else return null since shouldn't be used
    return _valueFactory;
  } // valueFactory

  private DefaultFactory _defaultFactory = null;        // <62023>
  public DefaultFactory defaultFactory ()
  {
    if (_defaultFactory == null)
      if (Util.corbaLevel (2.4f, 99.0f)) // <d60023>
         _defaultFactory = new DefaultFactory ();     // <d60023>
      // else return null since shouldn't be used
    return _defaultFactory;
  } // defaultFactory

  private Holder _holder = new Holder ();
  public Holder holder ()
  {
    return _holder;
  } // holder

  private Skeleton _skeleton = new Skeleton ();
  public Skeleton skeleton ()
  {
    return _skeleton;
  } // skeleton

  private Stub _stub = new Stub ();
  public Stub stub ()
  {
    return _stub;
  } // stub

  // toJava-specific factory methods
  ///////////////
} // class Factories
