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
// -F46082.51<daz> Remove -stateful feature; javaStatefulName() obsolete.
// -D57147   <klr> Make _Tie implement org.omg.CORBA.portable.InvokeHandler
// -D58037   <klr> Make _Tie delegate to Operations interface
// -D62739   <klr> no TIE for values that support abstract interfaces, etc.

import java.io.File;
import java.io.PrintWriter;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import com.sun.tools.corba.se.idl.AttributeEntry;
import com.sun.tools.corba.se.idl.GenFileStream;

import com.sun.tools.corba.se.idl.InterfaceEntry;
import com.sun.tools.corba.se.idl.InterfaceState;
import com.sun.tools.corba.se.idl.MethodEntry;
import com.sun.tools.corba.se.idl.SymtabEntry;
import com.sun.tools.corba.se.idl.TypedefEntry;
import com.sun.tools.corba.se.idl.ValueEntry;

/**
 *
 **/
public class Skeleton implements AuxGen
{
  private NameModifier skeletonNameModifier ;
  private NameModifier tieNameModifier ;

  public Skeleton ()
  {
  }

  public void generate (Hashtable symbolTable, SymtabEntry entry)
  {
    // <d62739-begin>
    // Per Simon, 5-12-99, don't generate TIE or Skeleton for
    //
    // 1) valuetypes supporting abstract interfaces
    // 2) valuetypes with no supports.
    // 3) abstract interfaces
    //
    if (entry instanceof ValueEntry)
    {
      ValueEntry v = (ValueEntry) entry;
      if ((v.supports ().size () == 0) ||
          ((InterfaceEntry) v.supports ().elementAt (0)).isAbstract ()) {
        return;
        }
    }
    if (((InterfaceEntry) entry).isAbstract ()) {
        return;
    }
    // <d62739-end>

    this.symbolTable = symbolTable;

    this.i           = (InterfaceEntry)entry;
    init ();
    openStream ();
    if (stream == null)
      return;
    writeHeading ();
    writeBody ();
    writeClosing ();
    closeStream ();
  } // generate

  /**
   * Initialize members unique to this generator.
   **/
  protected void init ()
  {
    tie = ((Arguments)Compile.compiler.arguments).TIEServer ;
    poa = ((Arguments)Compile.compiler.arguments).POAServer ;

    skeletonNameModifier =
        ((Arguments)Compile.compiler.arguments).skeletonNameModifier ;
    tieNameModifier =
        ((Arguments)Compile.compiler.arguments).tieNameModifier ;

    tieClassName = tieNameModifier.makeName( i.name() ) ;
    skeletonClassName = skeletonNameModifier.makeName( i.name() ) ;

    intfName = Util.javaName (i);
    // for valuetype, get the name of the interface the valuetype supports
    if (i instanceof ValueEntry)
    {
      ValueEntry v = (ValueEntry) i;
      InterfaceEntry intf = (InterfaceEntry) v.supports ().elementAt (0);
      intfName = Util.javaName (intf);
    }
  } // init

  protected void openStream ()
  {
    if (tie)
        stream = Util.stream( i, tieNameModifier, ".java" ) ;
    else
        stream = Util.stream( i, skeletonNameModifier, ".java" ) ;
  } // openStream

  protected void writeHeading ()
  {
    Util.writePackage (stream, i, Util.StubFile);
    Util.writeProlog (stream, ((GenFileStream)stream).name ());
    if (i.comment () != null)
      i.comment ().generate ("", stream);
    writeClassDeclaration ();
    stream.println ('{');
    stream.println ();
  } // writeHeading

  protected void writeClassDeclaration ()
  {
    if (tie){
        stream.println ("public class " + tieClassName +
            " extends " + skeletonClassName ) ;
    } else {
        if (poa) {
            stream.println ("public abstract class " + skeletonClassName +
                            " extends org.omg.PortableServer.Servant");
            stream.print   (" implements " + intfName + "Operations, ");
            stream.println ("org.omg.CORBA.portable.InvokeHandler");
        } else {
            stream.println ("public abstract class " + skeletonClassName +
                            " extends org.omg.CORBA.portable.ObjectImpl");
            stream.print   ("                implements " + intfName + ", ");
            stream.println ("org.omg.CORBA.portable.InvokeHandler");
        }
    }
  } // writeClassDeclaration

  /**
   *
   **/
  protected void writeBody ()
  {
    // <f46082.51> Remove -stateful feature.  ?????
    //if (i.state () != null)
    //  writeState ();
    writeCtors ();
    if (i instanceof ValueEntry)
    {
      // use the interface the valuetype supports to generate the
      // tie class instead of using the valuetype itself
      ValueEntry v = (ValueEntry) i;
      this.i = (InterfaceEntry) v.supports ().elementAt (0);
    }
    buildMethodList ();
    //DispatchMethod and MethodTable
    if (tie){ //Concrete class implementing the remote interface
        //The logic is here for future use
        if (poa) {
            writeMethods ();
            stream.println ("  private " + intfName + "Operations _impl;");
            stream.println ("  private org.omg.PortableServer.POA _poa;");
        } else {
            writeMethods ();
            stream.println ("  private " + intfName + "Operations _impl;");
        }
    } else { //Both POA and ImplBase are abstract InvokeHandler
        //The logic is here for future use
        if (poa) {
            writeMethodTable ();
            writeDispatchMethod ();
            writeCORBAOperations ();
        } else {
            writeMethodTable ();
            writeDispatchMethod ();
            writeCORBAOperations ();
        }
    }
    //legacy !!
    writeOperations ();
  } // writeBody

  /**
   * Close the skeleton class. The singleton ORB member is
   * necessary only for portable skeletons.
   **/
  protected void writeClosing ()
  {
    stream.println ();
    if (tie){
        stream.println ("} // class " + tieClassName);
    } else {
        stream.println ("} // class " + skeletonClassName);
    }
  } // writeClosing

  /**
   * Close the print stream, which flushes the stream to file.
   **/
  protected void closeStream ()
  {
    stream.close ();
  } // closeStream

  protected void writeCtors ()
  {
    stream.println ("  // Constructors");
    // Empty argument constructors
    if (!poa) {
        if (tie){
            stream.println ("  public " + tieClassName + " ()");
            stream.println ("  {");
            stream.println ("  }");
        } else {
            stream.println ("  public " + skeletonClassName + " ()");
            stream.println ("  {");
            stream.println ("  }");
        }
    }
    stream.println ();
    // Argumented constructors
    if (tie){
        if (poa) {
            //Write constructors
            writePOATieCtors();
            //Write state setters and getters
            writePOATieFieldAccessMethods();
        } else {
            stream.println ("  public " + tieClassName +
                            " (" + intfName + "Operations impl)");
            stream.println ("  {");
            // Does it derive from a interface having state, e.g., valuetype?
            if (((InterfaceEntry)i.derivedFrom ().firstElement ()).state () != null)
                stream.println ("    super (impl);");
            else
                stream.println ("    super ();");
            stream.println ("    _impl = impl;");
            stream.println ("  }");
            stream.println ();
        }
    } else { //Skeleton is not Tie so it has no constructors.
        if (poa) {
        } else {
        }
    }

  } // writeCtors


  private void writePOATieCtors(){
    //First constructor
    stream.println ("  public " + tieClassName + " ( " + intfName + "Operations delegate ) {");
    stream.println ("      this._impl = delegate;");
    stream.println ("  }");
    //Second constructor specifying default poa.
    stream.println ("  public " + tieClassName + " ( " + intfName +
                    "Operations delegate , org.omg.PortableServer.POA poa ) {");
    stream.println ("      this._impl = delegate;");
    stream.println ("      this._poa      = poa;");
    stream.println ("  }");
  }

  private void writePOATieFieldAccessMethods(){
    //Getting delegate
    stream.println ("  public " + intfName+ "Operations _delegate() {");
    stream.println ("      return this._impl;");
    stream.println ("  }");
    //Setting delegate
    stream.println ("  public void _delegate (" + intfName + "Operations delegate ) {");
    stream.println ("      this._impl = delegate;");
    stream.println ("  }");
    //Overriding default poa
    stream.println ("  public org.omg.PortableServer.POA _default_POA() {");
    stream.println ("      if(_poa != null) {");
    stream.println ("          return _poa;");
    stream.println ("      }");
    stream.println ("      else {");
    stream.println ("          return super._default_POA();");
    stream.println ("      }");
    stream.println ("  }");
  }

  /**
   * Build a list of all of the methods, keeping out duplicates.
   **/
  protected void buildMethodList ()
  {
    // Start from scratch
    methodList = new Vector ();

    buildMethodList (i);
  } // buildMethodList

  /**
   *
   **/
  private void buildMethodList (InterfaceEntry entry)
  {
    // Add the local methods
    Enumeration locals = entry.methods ().elements ();
    while (locals.hasMoreElements ())
      addMethod ((MethodEntry)locals.nextElement ());

    // Add the inherited methods
    Enumeration parents = entry.derivedFrom ().elements ();
    while (parents.hasMoreElements ())
    {
      InterfaceEntry parent = (InterfaceEntry)parents.nextElement ();
      if (!parent.name ().equals ("Object"))
        buildMethodList (parent);
    }
  } // buildMethodList

  /**
   *
   **/
  private void addMethod (MethodEntry method)
  {
    if (!methodList.contains (method))
      methodList.addElement (method);
  } // addMethod

  /**
   *
   **/
  protected void writeDispatchMethod ()
  {
    String indent = "                                ";
    stream.println ("  public org.omg.CORBA.portable.OutputStream _invoke (String $method,");
    stream.println (indent + "org.omg.CORBA.portable.InputStream in,");
    stream.println (indent + "org.omg.CORBA.portable.ResponseHandler $rh)");
    stream.println ("  {");

    // this is a special case code generation for cases servantLocator and
    // servantActivator, where OMG is taking too long to define them
    // as local objects

    boolean isLocalInterface = false;
    if (i instanceof InterfaceEntry) {
        isLocalInterface = i.isLocalServant();
    }

    if (!isLocalInterface) {
        // Per Simon 8/26/98, create and return reply stream for all methods - KLR
        stream.println ("    org.omg.CORBA.portable.OutputStream out = null;");
        stream.println ("    java.lang.Integer __method = (java.lang.Integer)_methods.get ($method);");
        stream.println ("    if (__method == null)");
        stream.println ("      throw new org.omg.CORBA.BAD_OPERATION (0, org.omg.CORBA.CompletionStatus.COMPLETED_MAYBE);");
        stream.println ();
        if (methodList.size () > 0)
        {
          stream.println ("    switch (__method.intValue ())");
          stream.println ("    {");

          // Write the method case statements
          int realI = 0;
          for (int i = 0; i < methodList.size (); ++i)
          {
            MethodEntry method = (MethodEntry)methodList.elementAt (i);
            ((MethodGen)method.generator ()).dispatchSkeleton (symbolTable, method, stream, realI);
            if (method instanceof AttributeEntry && !((AttributeEntry)method).readOnly ())
              realI += 2;
            else
              ++realI;
          }

          indent = "       ";
          stream.println (indent + "default:");
          stream.println (indent + "  throw new org.omg.CORBA.BAD_OPERATION (0, org.omg.CORBA.CompletionStatus.COMPLETED_MAYBE);");
          stream.println ("    }");
          stream.println ();
        }
        stream.println ("    return out;");
    } else {
        stream.println("    throw new org.omg.CORBA.BAD_OPERATION();");
    }
    stream.println ("  } // _invoke");
    stream.println ();
  } // writeDispatchMethod

  /**
   *
   **/
  protected void writeMethodTable ()
  {
    // Write the methods hashtable
    stream.println ("  private static java.util.Hashtable _methods = new java.util.Hashtable ();");
    stream.println ("  static");
    stream.println ("  {");

    int count = -1;
    Enumeration e = methodList.elements ();
    while (e.hasMoreElements ())
    {
      MethodEntry method = (MethodEntry)e.nextElement ();
      if (method instanceof AttributeEntry)
      {
        stream.println ("    _methods.put (\"_get_" + Util.stripLeadingUnderscores (method.name ()) + "\", new java.lang.Integer (" + (++count) + "));");
        if (!((AttributeEntry)method).readOnly ())
          stream.println ("    _methods.put (\"_set_" + Util.stripLeadingUnderscores (method.name ()) + "\", new java.lang.Integer (" + (++count) + "));");
      }
      else
        stream.println ("    _methods.put (\"" + Util.stripLeadingUnderscores (method.name ()) + "\", new java.lang.Integer (" + (++count) + "));");
    }
    stream.println ("  }");
    stream.println ();
  } // writeMethodTable

  /**
   *
   **/
  protected void writeMethods ()
  {
      int realI = 0;
      for (int i = 0; i < methodList.size (); ++i)
          {
              MethodEntry method = (MethodEntry)methodList.elementAt (i);
              ((MethodGen)method.generator ()).skeleton
                  (symbolTable, method, stream, realI);
              if (method instanceof AttributeEntry &&
                  !((AttributeEntry)method).readOnly ())
                  realI += 2;
              else
                  ++realI;
              stream.println ();
          }
  } // writeMethods

  /**
   *
   **/
  private void writeIDs ()
  {
    Vector list = new Vector ();
    buildIDList (i, list);
    Enumeration e = list.elements ();
    boolean first = true;
    while (e.hasMoreElements ())
    {
      if (first)
        first = false;
      else
        stream.println (", ");
      stream.print ("    \"" + (String)e.nextElement () + '"');
    }
  } // writeIDs

  /**
   *
   **/
  private void buildIDList (InterfaceEntry entry, Vector list)
  {
    if (!entry.fullName ().equals ("org/omg/CORBA/Object"))
    {
      String id = Util.stripLeadingUnderscoresFromID (entry.repositoryID ().ID ());
      if (!list.contains (id))
        list.addElement (id);
      Enumeration e = entry.derivedFrom ().elements ();
      while (e.hasMoreElements ())
        buildIDList ((InterfaceEntry)e.nextElement (), list);
    }
  } // buildIDList

  /**
   *
   **/
  protected void writeCORBAOperations ()
  {
    stream.println ("  // Type-specific CORBA::Object operations");

    stream.println ("  private static String[] __ids = {");
    writeIDs ();
    stream.println ("};");
    stream.println ();
    if (poa)
        writePOACORBAOperations();
    else
        writeNonPOACORBAOperations();

  } // writeCORBAOperations

  protected void writePOACORBAOperations(){
      stream.println ("  public String[] _all_interfaces (org.omg.PortableServer.POA poa, byte[] objectId)");
      //Right now, with our POA implementation, the same
      //implementation of _ids() type methods seem to work for both non-POA
      //as well as POA servers. We need to REVISIT since the equivalent
      //POA interface, i.e. _all_interfaces, has parameters which are not being
      //used in the _ids() implementation.
      stream.println ("  {");
      stream.println ("    return (String[])__ids.clone ();");
      stream.println ("  }");
      stream.println ();
      //_this()
      stream.println ("  public "+ i.name() +" _this() ");
      stream.println ("  {");
      stream.println ("    return "+ i.name() +"Helper.narrow(" );
      stream.println ("    super._this_object());");
      stream.println ("  }");
      stream.println ();
      //_this(org.omg.CORBA.ORB orb)
      stream.println ("  public "+ i.name() +" _this(org.omg.CORBA.ORB orb) ");
      stream.println ("  {");
      stream.println ("    return "+ i.name() +"Helper.narrow(" );
      stream.println ("    super._this_object(orb));");
      stream.println ("  }");
      stream.println ();
  }
  protected void writeNonPOACORBAOperations(){
      stream.println ("  public String[] _ids ()");
      stream.println ("  {");
      stream.println ("    return (String[])__ids.clone ();");
      stream.println ("  }");
      stream.println ();
  }
  /**
   *
   **/
  protected void writeOperations ()
  {
    // _get_ids removed at Simon's request 8/26/98 - KLR
  } // writeOperations

  protected Hashtable      symbolTable = null;
  protected InterfaceEntry i           = null;
  protected PrintWriter    stream      = null;

  // Unique to this generator
  protected String         tieClassName   = null;
  protected String         skeletonClassName   = null;
  protected boolean        tie         = false;
  protected boolean        poa         = false;
  protected Vector         methodList  = null;
  protected String         intfName    = "";
} // class Skeleton
