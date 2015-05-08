/*
 * Copyright (c) 1999, 2001, Oracle and/or its affiliates. All rights reserved.
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
// -09/23/98 KLR Ported -m updates (F46838.1-3)
// -f46082.51<daz> Transferred makefile list generation (for ODE delta-builds,
//  see f46838) to toJava; cleaned-out dead code.
// -D58319<daz> Display version info. for -version option.
// -D58951<daz> Modify to allow QuickTest to build.
// -D49526<daz> Remove "TypeCode" symbol from preParse().
// -D58591<daz> Publicise _factories and compile for QuickTest.  Need to revert
//  t0 private and add accessor methods.
// -D59437<daz> Fill typename information for value boxes.

import java.io.File;
import java.io.IOException;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import com.sun.tools.corba.se.idl.GenFileStream;
import com.sun.tools.corba.se.idl.SymtabFactory;
import com.sun.tools.corba.se.idl.IncludeEntry;
import com.sun.tools.corba.se.idl.InterfaceEntry;
import com.sun.tools.corba.se.idl.InterfaceState;
import com.sun.tools.corba.se.idl.ModuleEntry;
import com.sun.tools.corba.se.idl.PrimitiveEntry;
import com.sun.tools.corba.se.idl.SequenceEntry;
import com.sun.tools.corba.se.idl.StructEntry;
import com.sun.tools.corba.se.idl.SymtabEntry;
import com.sun.tools.corba.se.idl.TypedefEntry;
import com.sun.tools.corba.se.idl.UnionBranch;
import com.sun.tools.corba.se.idl.UnionEntry;
import com.sun.tools.corba.se.idl.ValueEntry;
import com.sun.tools.corba.se.idl.ValueBoxEntry;
import com.sun.tools.corba.se.idl.InvalidArgument;

/**
 * Compiler usage:
 * <br><br>
 *
 * java com.sun.tools.corba.se.idl.toJavaPortable.Compile [options] &lt;idl file&gt;
 * <br><br>
 *
 * where &lt;idl file&gt; is the name of a file containing IDL definitions,
 * and [options] is any combination of the options listed below.  The options
 * may appear in any order.
 * <br><br>
 *
 * Options:
 * <dl>
 *   <dt>{@code -i <include path>}
 *   <dd>By default, the current directory is scanned for included files.
 *   This option adds another directory.  See also the note below.
 *
 *   <dt>{@code -d <symbol>}
 *   <dd>This is equivalent to the following line in an IDL file:
 *   {@code #define <symbol>}
 *
 *   <dt>{@code -f <side>}
 *   <dd>Defines what bindings to emit. {@code <side>} is one of client, server, all,
 *   serverTIE, allTIE.  serverTIE and allTIE cause delegate model skeletons
 *   to be emitted. If this flag is not used, -fclient is assumed.
 *   allPOA has the same effect as all, except for generation POA type skeletons.
 *
 *   <dt>{@code -keep}
 *   <dd>If a file to be generated already exists, do not overwrite it. By
 *   default it is overwritten.
 *
 *   <dt>{@code -sep <string>}
 *   <dd>Only valid with -m.  Replace the file separator character with
 *     {@code <string>} in the file names listed in the .u file.
 *
 *   <dt>{@code -emitAll}
 *   <dd>Emit all types, including those found in #included files.
 *
 *   <dt>{@code -v}
 *   <dd>Verbose mode.
 *
 *   <dt>{@code -pkgPrefix <type> <package>}
 *   <dd>Whereever {@code <type>} is encountered, make sure it resides within
 *   {@code <package>} in all generated files.  {@code <type>} is a fully
 *   qualified, java-style name.
 * </dl>
 *
 * <B>Note:</B> If you have an include path or paths that you will always
 * be using, it can get tedious putting these on the command with the -i
 * option all the time.  Instead, these can be placed into a config file
 * called idl.config.  This file must be in the CLASSPATH.  The format of
 * the includes line is:
 *
 * <pre>{@code
 * includes=<path1>;<path2>;...;<pathN>
 * }</pre>
 *
 * Note that the path separator character, here shown as a semicolon,
 * is machine dependent.  For instance, on Windows 95 this character
 * is a semicolon, on UNIX it is a colon.
 **/
public class Compile extends com.sun.tools.corba.se.idl.Compile
{
 /**
  *
  **/
  public static void main (String[] args)
  {
    compiler = new Compile ();
    compiler.start (args);
  } // main

 /**
  *
  **/
  public void start (String[] args)
  {
    try
    {
      // <f46082.51> Use generator-specific messages file.
      //Util.registerMessageFile ("com/sun/corba/se/idl/toJavaPortable/toJava.prp");
      Util.registerMessageFile ("com/sun/tools/corba/se/idl/toJavaPortable/toJavaPortable.prp");
      init (args);
      if (arguments.versionRequest)
        displayVersion ();
      else
      {
        preParse ();
        Enumeration e = parse ();
        if (e != null)
        {
          preEmit (e);
          generate ();
          // <f46082.03> Move ODE delta-build support to toJava
          //if (((Arguments)arguments).genMakefileLists)
          //  generateMakefileLists ();
        }
      }
    }
    catch (InvalidArgument e)
    {
      System.err.println (e);
    }
    catch (IOException e)
    {
      System.err.println (e);
    }
  } // start

  /**
   *
   **/
  protected Compile ()
  {
    factory = factories ().symtabFactory ();
  } // ctor

  // <d58591> _factories was made public for QuickTest to operate correctly,
  // but the code needs to be changed to this:
  //private Factories _factories = null;
  //protected com.sun.tools.corba.se.idl.Factories factories ()
  //{
  //  if (_factories == null)
  //    _factories = new Factories ();
  //  return _factories;
  //} // factories

  public Factories _factories = new Factories ();  // 58974 - changed for quicktest
  protected com.sun.tools.corba.se.idl.Factories factories ()
  {
    return _factories;
  } // factories


  ModuleEntry org;
  ModuleEntry omg;
  ModuleEntry corba;
  InterfaceEntry object;

  /**
   *
   **/
  protected void preParse ()
  {
    Util.setSymbolTable (symbolTable);
    Util.setPackageTranslation( ((Arguments)arguments).packageTranslation ) ;

    // Need modules for the predefined objects
    org = factory.moduleEntry ();
    // <d61919> Suppress generation of this module.  If the parser reopens it
    // while parsing the main IDL source, any definitions appearing in the module
    // -- and not appearing in a global-scope include file -- will be added to
    // the emit list with emit=true for eventual generation.
    org.emit (false);
    org.name ("org");
    org.container (null);
    omg = factory.moduleEntry ();
    omg.emit (false); // <d61919>
    omg.name ("omg");
    omg.module ("org");
    omg.container (org);
    org.addContained (omg);
    corba = factory.moduleEntry ();
    corba.emit (false); // <d61919>
    corba.name ("CORBA");
    corba.module ("org/omg");
    corba.container (omg);
    omg.addContained (corba);
    symbolTable.put ("org", org);
    symbolTable.put ("org/omg", omg);
    symbolTable.put ("org/omg/CORBA", corba);

    // Add CORBA::Object to symbol table.
    object = (InterfaceEntry)symbolTable.get ("Object");
    object.module ("org/omg/CORBA");
    object.container (corba);
    symbolTable.put ("org/omg/CORBA/Object", object);

    // <d61961> Add PIDL type (primitive) CORBA::TypeCode to symbol table.
    PrimitiveEntry pEntry = factory.primitiveEntry ();
    pEntry.name ("TypeCode");
    pEntry.module ("org/omg/CORBA");
    pEntry.container (corba);
    symbolTable.put ("org/omg/CORBA/TypeCode", pEntry);
    symbolTable.put ("CORBA/TypeCode", pEntry);                      // <d55699>
    overrideNames.put ("CORBA/TypeCode", "org/omg/CORBA/TypeCode");  // <d55699>
    overrideNames.put ("org/omg/CORBA/TypeCode", "CORBA/TypeCode");  // <d55699>
    // <d49526> Allow user to specify types named "TypeCode"
    //symbolTable.put ("TypeCode", pEntry);
    //overrideNames.put ("TypeCode", "org/omg/CORBA/TypeCode");

    // CORBA::Principal is deprecated!
    // <d61961> Add PIDL type (primitive) CORBA::Principal to symbol table.
    pEntry = factory.primitiveEntry ();
    pEntry.name ("Principal");
    pEntry.module ("org/omg/CORBA");
    pEntry.container (corba);
    symbolTable.put ("org/omg/CORBA/Principle", pEntry);
    symbolTable.put ("CORBA/Principal", pEntry);
    overrideNames.put ("CORBA/Principal", "org/omg/CORBA/Principal");
    overrideNames.put ("org/omg/CORBA/Principal", "CORBA/Principal");

    // <d61961> Add PIDL type (interface) CORBA::Current to symbol table.
    //InterfaceEntry iEntry = factory.interfaceEntry ();
    //iEntry.name ("Current");
    //iEntry.module ("org/omg/CORBA");
    //iEntry.container (corba);
    //symbolTable.put ("org/omg/CORBA/Current", iEntry);
    //symbolTable.put ("CORBA/Current", iEntry);
    //overrideNames.put ("CORBA/Current", "org/omg/CORBA/Current");
    //overrideNames.put ("org/omg/CORBA/Current", "CORBA/Current");

    overrideNames.put ("TRUE", "true");
    overrideNames.put ("FALSE", "false");
    //overrideNames.put ("any", "org/omg/CORBA/Any");

    // Add CORBA module to symbol table
    symbolTable.put ("CORBA", corba);  // 55699
    overrideNames.put ("CORBA", "org/omg/CORBA");  // <d55699>
    overrideNames.put ("org/omg/CORBA", "CORBA");  // <d55699>
  } // preParse


  protected void preEmit (Enumeration emitList)
  {
    typedefInfo = SymtabEntry.getVariableKey ();
    Hashtable tempST = (Hashtable)symbolTable.clone ();

    for (Enumeration e = tempST.elements (); e.hasMoreElements ();)
    {
      SymtabEntry element = (SymtabEntry)e.nextElement ();

      // Any other symbolTable processing?
      preEmitSTElement (element);
    }

    // Do this processing AFTER any other processing to get the
    // correct names.
    Enumeration elements = symbolTable.elements ();
    while (elements.hasMoreElements ())
    {
      // Find all TypedefEntry's and fill in the SymtabEntry.info
      // field with it's real type , including [][]... with const
      // exprs.
      SymtabEntry element = (SymtabEntry)elements.nextElement ();
      if (element instanceof TypedefEntry || element instanceof SequenceEntry)
        Util.fillInfo (element);

      // <d59437> Members of constructed types may now be value boxes, and value
      // boxes may contain types that are directly defined rather than typedef-ed
      // (e.g., "valuetype vb sequence <long, 5>;").  If member resolves to a value
      // box, then check and fillInfo() for value box and its content type BEFORE
      // doing fillInfo() on member; otherwise, could get an exception.  There's
      // code in fillInfo() that performs this check, so it does not appear here.

      else if (element instanceof StructEntry)
      {
        Enumeration members = ((StructEntry)element).members ().elements ();
        while (members.hasMoreElements ())
          Util.fillInfo ((SymtabEntry)members.nextElement ());
      }
      else if (element instanceof InterfaceEntry && ((InterfaceEntry)element).state () != null)
      {
        Enumeration members = ((InterfaceEntry)element).state ().elements ();
        while (members.hasMoreElements ())
          Util.fillInfo (((InterfaceState)members.nextElement ()).entry);
      }
      else if (element instanceof UnionEntry)
      {
        Enumeration branches = ((UnionEntry)element).branches ().elements ();
        while (branches.hasMoreElements ())
          Util.fillInfo (((UnionBranch)branches.nextElement ()).typedef);
      }

      // For each type that is at the top level that is NOT a module
      // or IncludeEntry, add it to the imports list.  If there are
      // types within modules which refer to these, their types must
      // be explicitly stated in an import statement.
      if (element.module ().equals ("") && !(element instanceof ModuleEntry || element instanceof IncludeEntry || element instanceof PrimitiveEntry))
        importTypes.addElement (element);
    }

    while (emitList.hasMoreElements ())
    {
      SymtabEntry entry = (SymtabEntry)emitList.nextElement ();

      // Any other emitList processing:
      preEmitELElement (entry);
    }
  } // preEmit

  /**
   * This method is called by preEmit once for each symbol table entry.
   * It can be called by extenders.
   **/
  protected void preEmitSTElement (SymtabEntry entry)
  {
    // If the -package argument was used, search the packages list
    // for the given type name and prepend the package to it.
    Hashtable packages = ((Arguments)arguments).packages;
    if (packages.size () > 0)
    {
      String substr = (String)packages.get (entry.fullName ());
      if (substr != null)
      {
        String pkg = null;
        ModuleEntry mod = null;
        ModuleEntry prev = null;
        while (substr != null)
        {
          int dot = substr.indexOf ('.');
          if (dot < 0)
          {
            pkg = substr;
            substr = null;
          }
          else
          {
            pkg = substr.substring (0, dot);
            substr = substr.substring (dot + 1);
          }

          String fullName = prev == null ? pkg : prev.fullName () + '/' + pkg;
          mod = (ModuleEntry)symbolTable.get (fullName);
          if (mod == null)
          {
            mod = factory.moduleEntry ();
            mod.name (pkg);
            mod.container (prev);
            if (prev != null) mod.module (prev.fullName ());
            symbolTable.put (pkg, mod);
          }
          prev = mod;
        }
        entry.module (mod.fullName ());
        entry.container (mod);
      }
    }
  } // preEmitSTElement

  /**
   * This method is called by preEmit once for each emitList entry.
   * It can be called by extenders.
   **/
  protected void preEmitELElement (SymtabEntry entry)
  {
  } // preEmitELElement

  public        Vector        importTypes  = new Vector ();
  public        SymtabFactory factory;
  public static int           typedefInfo;
  public        Hashtable     list         = new Hashtable ();
  public static Compile       compiler     = null;  // <d58591>
} // class Compile
