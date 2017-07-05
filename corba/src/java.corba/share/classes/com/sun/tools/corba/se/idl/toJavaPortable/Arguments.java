/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
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
// -09/23/98 <klr> Ported -td option to change output directory
// -09/23/98 <klr> Ported -m option to generate make dependencies
// -F46082.51<daz> Transferred -m, -mmin, mall, -mdepend options to com.sun.tools.corba.se.idl.toJava
// since these are IBM-specific (see f46838); cleaned-out dead code.
// -D57482   <klr> Added method setDefaultEmitter so could be overridden.
// -F60858.1<daz> Set corba level to 2.3.

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Vector;
import java.io.File;

import com.sun.tools.corba.se.idl.InvalidArgument;

/**
 *
 **/
public class Arguments extends com.sun.tools.corba.se.idl.Arguments
{
  /**
   * Public, zero-argument constructor.
   **/
  public Arguments ()
  {
    super ();
    corbaLevel = 2.4f;
  } // ctor

  /**
   *
   **/
  protected void parseOtherArgs (String[] args,
    Properties properties) throws InvalidArgument
  {
    String skeletonPattern = null ;
    String tiePattern = null ;

    // Get package prefixes from user's properties file.
    packages.put ("CORBA", "org.omg"); // klr - always needed
    packageFromProps (properties);

    // Now get package prefixes from command line (along with other args).
    // This order has the effect of making command line packages
    // supercede any idl.config file packages.
    try
    {
      Vector unknownArgs = new Vector ();

      // Process command line parameters
      for (int i = 0; i < args.length; ++i)
      {
        String lcArg = args[i].toLowerCase ();

        if (lcArg.charAt (0) != '-' && lcArg.charAt (0) != '/')
          throw new InvalidArgument (args[i]);
        if (lcArg.charAt (0) == '-' ) {
            lcArg = lcArg.substring (1);
        }

        // Proxy options; default is -fclient.
        if (lcArg.startsWith ("f"))
        {
          // If the command line had '-f client', make it '-fclient'
          if (lcArg.equals ("f"))
            lcArg = 'f' + args[++i].toLowerCase ();

          // Determine whether to emit bindings for client, server or both; and
          // whether to emit delegate-style (TIE) rather than derived-style
          // skeletons, which are the default.

          if (lcArg.equals ("fclient"))
          {
            emit = ((emit == Server || emit == All) ? All : Client);
          }
          else if (lcArg.equals ("fserver"))
          {
            emit = ((emit == Client || emit == All) ? All : Server);
            TIEServer = false;
          }
          else if (lcArg.equals ("fall"))
          {
            emit = All;
            TIEServer = false;
            //Should be removed and incorporated in the clause below
            //            POAServer = true;
          }
          else if (lcArg.equals ("fservertie"))
          {
            emit = ((emit == Client || emit == All) ? All : Server);
            TIEServer = true;
          }
          else if (lcArg.equals ("falltie"))
          {
            emit = All;
            TIEServer = true;
          }
          else
            i = collectUnknownArg (args, i, unknownArgs);
        }
        else if (lcArg.equals ("pkgtranslate"))
        {
          if (i + 2 >= args.length)
            throw new InvalidArgument( args[i] ) ;

          String orig = args[++i] ;
          String trans = args[++i] ;
          checkPackageNameValid( orig ) ;
          checkPackageNameValid( trans ) ;
          if (orig.equals( "org" ) || orig.startsWith( "org.omg" ))
              throw new InvalidArgument( args[i] ) ;
          orig = orig.replace( '.', '/' ) ;
          trans = trans.replace( '.', '/' ) ;
          packageTranslation.put( orig, trans ) ;
        }
        // Package prefix
        else if (lcArg.equals ("pkgprefix"))
        {
          if (i + 2 >= args.length)
            throw new InvalidArgument (args[i]);

          String type = args[++i];
          String pkg = args[++i];
          checkPackageNameValid( type ) ;
          checkPackageNameValid( pkg ) ;
          packages.put (type, pkg);
        }
        // Target directory
        else if (lcArg.equals ("td"))  // <f46838.4>
        {
          if (i + 1 >= args.length)
            throw new InvalidArgument (args[i]);
          String trgtDir = args[++i];
          if (trgtDir.charAt (0) == '-')
            throw new InvalidArgument (args[i - 1]);
          else
          {
            targetDir = trgtDir.replace ('/', File.separatorChar);
            if (targetDir.charAt (targetDir.length () - 1) != File.separatorChar)
              targetDir = targetDir + File.separatorChar;
          }
        }
        // Separator
        else if (lcArg.equals ("sep"))
        {
          if (i + 1 >= args.length)
            throw new InvalidArgument (args[i]);
          separator = args[++i];
        }
        // POA flag ?
        else if (lcArg.equals ("oldimplbase")){
            POAServer = false;
        }
        else if (lcArg.equals("skeletonname")){
          if (i + 1 >= args.length)
            throw new InvalidArgument (args[i]);
          skeletonPattern = args[++i];
        }
        else if (lcArg.equals("tiename")){
          if (i + 1 >= args.length)
            throw new InvalidArgument (args[i]);
          tiePattern = args[++i];
        }
        else if (lcArg.equals("localoptimization")) {
            LocalOptimization = true;
        }
        else i = collectUnknownArg (args, i, unknownArgs);
      }

      // Encountered unknown arguments?
      if (unknownArgs.size () > 0)
      {
        String [] otherArgs = new String [unknownArgs.size ()];
        unknownArgs.copyInto (otherArgs);
        // Throws InvalidArgument by default
        super.parseOtherArgs (otherArgs, properties);
      }

      setDefaultEmitter(); // d57482 <klr>
      setNameModifiers( skeletonPattern, tiePattern ) ;
    }
    catch (ArrayIndexOutOfBoundsException e)
    {
      // If there is any array indexing problem, it is probably
      // because the qualifier on the last argument is missing.
      // Report that this last argument is invalid.
      throw new InvalidArgument (args[args.length - 1]);
    }
  } // parseOtherArgs

  /**
   *
   **/
  protected int collectUnknownArg (String[] args, int i, Vector unknownArgs)
  {
    unknownArgs.addElement (args [i]);
    ++i;
    while (i < args.length && args[i].charAt (0) != '-' && args[i].charAt (0) != '/')
      unknownArgs.addElement (args[i++]);
    return --i;
  } // collectUnknownArg

  /**
   *
   **/
  // XXX Either generalize this facility or remove it completely.
  protected void packageFromProps (Properties props) throws InvalidArgument
  {
    Enumeration propsEnum = props.propertyNames ();
    while (propsEnum.hasMoreElements ())
    {
      String prop = (String)propsEnum.nextElement ();
      if (prop.startsWith ("PkgPrefix."))
      {
        String type = prop.substring (10);
        String pkg = props.getProperty (prop);
        checkPackageNameValid( pkg ) ;
        checkPackageNameValid( type ) ;
        packages.put (type, pkg);
      }
    }
  } // packageFromProps

  /**
   * d57482 (klr) method added so default emitter check could be overriden.
   **/
  protected void setDefaultEmitter () {
      // If the flag -fclient was not found, assume it.
      if (emit == None) emit = Client;
  }

  protected void setNameModifiers( String skeletonPattern,
    String tiePattern ) {
    if (emit>Client) {
        String tp ;
        String sp ;

        if (skeletonPattern != null)
            sp = skeletonPattern ;
        else if (POAServer)
            sp = "%POA" ;
        else
            sp = "_%ImplBase" ;

        if (tiePattern != null)
            tp = tiePattern ;
        else if (POAServer)
            tp = "%POATie" ;
        else
            tp = "%_Tie" ;

        skeletonNameModifier = new NameModifierImpl( sp ) ;
        tieNameModifier = new NameModifierImpl( tp ) ;
    }
  }

  /**
   *
   **/
  private void checkPackageNameValid (String name) throws InvalidArgument
  {
    if (name.charAt (0) == '.')
      throw new InvalidArgument (name);
    for (int i = 0; i < name.length ();++i)
      if (name.charAt (i) == '.')
      {
        if (i == name.length () - 1 || !Character.isJavaIdentifierStart (name.charAt (++i)))
          throw new InvalidArgument (name);
      }
      else if (!Character.isJavaIdentifierPart (name.charAt (i)))
        throw new InvalidArgument (name);
  } // validatePackageName

  // <46082.03><46838> Modified access restrictions from protected to public.

  // This is a hash table whose keys are top-level typenames and
  // whose values are the package prefixes to those types.
  // For instance, <"CORBA", "org.omg"> is a possible entry.
  public Hashtable packages         = new Hashtable ();

  public    String separator        = null;

  public static final int
    None   = 0,
    Client = 1,
    Server = 2,
    All    = 3;
  public int       emit              = None;
  public boolean   TIEServer         = false;
  public boolean   POAServer         = true;
  // By default we do not generate Locally Optimized stub because of an
  // unresolved PI problem. We will generate only if -localOptimization flag
  // is passed
  public boolean   LocalOptimization = false;
  public NameModifier skeletonNameModifier   = null ;
  public NameModifier tieNameModifier   = null ;

  // Key is original package name; value is translated package name.
  // Note that this translation happens AFTER prefixes are added in the
  // packages table.
  public Hashtable packageTranslation = new Hashtable() ;

  public String    targetDir        = "";     // <f46838.4>
} // class Arguments
