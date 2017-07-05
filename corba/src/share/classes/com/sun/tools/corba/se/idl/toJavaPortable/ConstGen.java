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

import java.io.File;
import java.io.PrintWriter;
import java.util.Hashtable;
import java.util.Vector;

import com.sun.tools.corba.se.idl.GenFileStream;
import com.sun.tools.corba.se.idl.ConstEntry;
import com.sun.tools.corba.se.idl.ModuleEntry;
import com.sun.tools.corba.se.idl.PrimitiveEntry;
import com.sun.tools.corba.se.idl.StringEntry;
import com.sun.tools.corba.se.idl.SymtabEntry;
import com.sun.tools.corba.se.idl.TypedefEntry;

/**
 *
 **/
public class ConstGen implements com.sun.tools.corba.se.idl.ConstGen
{
  /**
   * Public zero-argument constructor.
   **/
  public ConstGen ()
  {
  } // ctor

  /**
   * Generate Java code for an IDL constant.  A constant is written to
   * a new class only when it is not a member of an interface; otherwise
   * it written to the interface class in which it resides.
   **/
  public void generate (Hashtable symbolTable, ConstEntry c, PrintWriter s)
  {
    this.symbolTable = symbolTable;
    this.c           = c;
    this.stream      = s;
    init ();

    if (c.container () instanceof ModuleEntry)
      generateConst ();
    else if (stream != null)
      writeConstExpr ();
  } // generate

  /**
   * Initialize members unique to this generator.
   **/
  protected void init ()
  {
  } // init

  /**
   * Generate the class defining the constant.
   **/
  protected void generateConst ()
  {
    openStream ();
    if (stream == null)
      return;
    writeHeading ();
    writeBody ();
    writeClosing ();
    closeStream ();
  } // generateConst

  /**
   * Open a new print stream only if the constant is not a member
   * of an interface.
   **/
  protected void openStream ()
  {
    stream = Util.stream (c, ".java");
  } // openStream

  /**
   * Write the heading for the class defining the constant.
   **/
  protected void writeHeading ()
  {
    Util.writePackage (stream, c);
    Util.writeProlog (stream, ((GenFileStream)stream).name ());
    stream.println ("public interface " + c.name ());
        // should not be done according to the mapping
        // + " extends org.omg.CORBA.portable.IDLEntity");
    stream.println ("{");
  } // writeHeading

  /**
   * Write the constant expression and any comment, if present.
   **/
  protected void writeBody ()
  {
    writeConstExpr ();
  } // writeBody

  /**
   * Write the entire constant expression and any comment, if present.
   **/
  protected void writeConstExpr ()
  {
    if (c.comment () != null)
      c.comment ().generate ("  ", stream);
    if (c.container () instanceof ModuleEntry) {

      stream.print ("  public static final " + Util.javaName (c.type ()) + " value = ");
    } else {
      stream.print ("  public static final " + Util.javaName (c.type ()) + ' ' + c.name () + " = ");
    }
    writeConstValue (c.type ());
  } // writeConstExpr

  /**
   * Write the constant's value according to its type.
   **/
  private void writeConstValue (SymtabEntry type)
  {
    if (type instanceof PrimitiveEntry)
      stream.println ('(' + Util.javaName (type) + ")(" + Util.parseExpression (c.value ()) + ");");
    else if (type instanceof StringEntry)
      stream.println (Util.parseExpression (c.value ()) + ';');
    else if (type instanceof TypedefEntry)
    {
      while (type instanceof TypedefEntry)
        type = type.type ();
      writeConstValue (type);
    }
    else
      stream.println (Util.parseExpression (c.value ()) + ';');
  } // writeValue

  /**
   * Generate any last words and close the class.
   **/
  protected void writeClosing ()
  {
    stream.println ("}");
  } // writeClosing

  /**
   * Close the print stream, causing the file to be written.
   **/
  protected void closeStream ()
  {
    stream.close ();
  } // closeStream

  protected java.util.Hashtable  symbolTable = null;
  protected ConstEntry           c           = null;
  protected PrintWriter          stream      = null;
} // class ConstGen


/*=======================================================================================
  DATE-AUTHOR   ACTION
  ---------------------------------------------------------------------------------------
  11sep1997daz  Return when print stream is null and container is NOT a module. Fixes
                -keep option, which causes null print stream to be sent to ConstGen.
  31jul1997daz  Write source comment immediately preceding constant declaration.
  =======================================================================================*/
