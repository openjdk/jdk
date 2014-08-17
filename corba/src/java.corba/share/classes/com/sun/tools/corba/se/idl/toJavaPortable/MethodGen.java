/*
 * Copyright (c) 1999, 2002, Oracle and/or its affiliates. All rights reserved.
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
// -After demarshalling an IOR, think about how to deal with the exceptions.
// -catching Exception throws a string which should be in a properties file.
// -30jul1997<daz> Modified to write comment immediately preceding method signature.
// -07May1998<ktp> Modified to support RMI Portable Stub
// -26Aug1998<klr> Modified to pass helper instance to read_Value.
// -F46082.51<daz> Remove -stateful feature; javaStatefulName() obsolete.
// -D56554   <klr> Port bounded string checks from toJava to toJavaPortable
// -D58549   <klr> bounded string checks on in/inout parms throw BAD_PARAM
// -D57112<daz> Valuetype initializers map to ctor, regardless of name, and
//  "void _init(...)" methods now mapped correctly.
// -D59297   <klr> pass context parm when Remarshalling
// -D59560   <klr> call read/write_Context
// -D60929   <klr> Update for RTF2.4 changes
// -D61056   <klr> Use Util.helperName
// -D61650<daz> Remove '\n' from generated strings; use println()'s.

import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Hashtable;

import com.sun.tools.corba.se.idl.EnumEntry;
import com.sun.tools.corba.se.idl.ExceptionEntry;
import com.sun.tools.corba.se.idl.InterfaceEntry;
import com.sun.tools.corba.se.idl.MethodEntry;
import com.sun.tools.corba.se.idl.ParameterEntry;
import com.sun.tools.corba.se.idl.PrimitiveEntry;
import com.sun.tools.corba.se.idl.StringEntry;
import com.sun.tools.corba.se.idl.SymtabEntry;
import com.sun.tools.corba.se.idl.SequenceEntry;
import com.sun.tools.corba.se.idl.ValueEntry;
import com.sun.tools.corba.se.idl.ValueBoxEntry;
import com.sun.tools.corba.se.idl.InterfaceState;
import com.sun.tools.corba.se.idl.TypedefEntry;
import com.sun.tools.corba.se.idl.AttributeEntry;

import com.sun.tools.corba.se.idl.constExpr.Expression;

/**
 *
 **/
public class MethodGen implements com.sun.tools.corba.se.idl.MethodGen
{
  private static final String ONE_INDENT   = "    ";
  private static final String TWO_INDENT   = "        ";
  private static final String THREE_INDENT = "            ";
  private static final String FOUR_INDENT  = "                ";
  private static final String FIVE_INDENT  = "                    ";
  // This is the length of _get_ and _set_
  private static final int ATTRIBUTE_METHOD_PREFIX_LENGTH  = 5;
  /**
   * Public zero-argument constructor.
   **/
  public MethodGen ()
  {
  } // ctor

  /**
   * Method generate() is not used in MethodGen.  They are replaced by the
   * more granular interfaceMethod, stub, skeleton, dispatchSkeleton.
   **/
  public void generate (Hashtable symbolTable, MethodEntry m, PrintWriter stream)
  {
  } // generate

  /**
   *
   **/
  protected void interfaceMethod (Hashtable symbolTable, MethodEntry m, PrintWriter stream)
  {
    this.symbolTable = symbolTable;
    this.m           = m;
    this.stream      = stream;
    if (m.comment () != null)
      m.comment ().generate ("", stream);
    stream.print ("  ");
    SymtabEntry container = (SymtabEntry)m.container ();
    boolean isAbstract = false;
    boolean valueContainer = false;
    if (container instanceof ValueEntry)
    {
      isAbstract = ((ValueEntry)container).isAbstract ();
      valueContainer = true;
    }
    if (valueContainer && !isAbstract)
      stream.print ("public ");
    writeMethodSignature ();
    if (valueContainer && !isAbstract)
    {
      stream.println ();
      stream.println ("  {");
      stream.println ("  }");
      stream.println ();
    }
    else
      stream.println (";");
  } // interfaceMethod

  /**
   *
   **/
  protected void stub (String className, boolean isAbstract,
      Hashtable symbolTable, MethodEntry m, PrintWriter stream, int index)
  {
    localOptimization =
        ((Arguments)Compile.compiler.arguments).LocalOptimization;
    this.isAbstract  = isAbstract;
    this.symbolTable = symbolTable;
    this.m           = m;
    this.stream      = stream;
    this.methodIndex = index;
    if (m.comment () != null)
      m.comment ().generate ("  ", stream);
    stream.print ("  public ");
    writeMethodSignature ();
    stream.println ();
    stream.println ("  {");
    writeStubBody ( className );
    stream.println ("  } // " + m.name ());
    stream.println ();
  } // stub

  /**
   *
   **/
  protected void localstub (Hashtable symbolTable, MethodEntry m, PrintWriter stream, int index, InterfaceEntry i)
  {
    this.symbolTable = symbolTable;
    this.m           = m;
    this.stream      = stream;
    this.methodIndex = index;
    if (m.comment () != null)
      m.comment ().generate ("  ", stream);
    stream.print ("  public ");
    writeMethodSignature ();
    stream.println ();
    stream.println ("  {");
    writeLocalStubBody (i);
    stream.println ("  } // " + m.name ());
    stream.println ();
  } // stub
  /**
   *
   **/
  protected void skeleton (Hashtable symbolTable, MethodEntry m, PrintWriter stream, int index)
  {
    this.symbolTable = symbolTable;
    this.m           = m;
    this.stream      = stream;
    this.methodIndex = index;
    if (m.comment () != null)
      m.comment ().generate ("  ", stream);
    stream.print ("  public ");
    writeMethodSignature ();
    stream.println ();
    stream.println ("  {");
    writeSkeletonBody ();
    stream.println ("  } // " + m.name ());
  } // skeleton

  /**
   *
   **/
  protected void dispatchSkeleton (Hashtable symbolTable, MethodEntry m, PrintWriter stream, int index)
  {
    this.symbolTable = symbolTable;
    this.m           = m;
    this.stream      = stream;
    this.methodIndex = index;
    if (m.comment () != null)
      m.comment ().generate ("  ", stream);
    writeDispatchCall ();
  } // dispatchSkeleton

  // <d57112>
  /**
   * Determine whether method entry m is a valuetype initializer.
   * @return true if is m is valuetype initializer, false otherwise.
   **/
  protected boolean isValueInitializer ()
  {
    MethodEntry currentInit = null;
    if ((m.container () instanceof ValueEntry))
    {
      Enumeration e = ((ValueEntry)m.container ()).initializers ().elements ();
      while (currentInit != m && e.hasMoreElements ())
        currentInit = (MethodEntry)e.nextElement ();
    }
    return (currentInit == m) && (null != m);  // True ==> yes, false ==> no.
  } // isValueInitializer

  /**
   *
   **/
  protected void writeMethodSignature ()
  {
    boolean isValueInitializer = isValueInitializer ();  // <d57112>

    // Step 0.  Print the return type and name.
    // A return type of null indicates the "void" return type. If m is a
    // Valuetype intitializer, it has name "init" and a null return type,
    // but it maps to a ctor.
    // <d57112>
    //if (m.type () == null)
    //{
    //  if (m.name ().compareTo ("init") != 0)
    //    stream.print ("void");
    //}
    if (m.type () == null)
    {
      if (!isValueInitializer)
        stream.print ("void");
    }
    else
    {
      // <f46082.51> Remove -stateful feature; javaStatefulName() obsolete.
      //stream.print (Util.javaStatefulName (m.type ()));
      stream.print (Util.javaName (m.type ()));
    }
    // <d57112> Value initializers map to constructors.
    // If the value has an 'init' method with a return type, handle
    // the method like other regular methods
    //if (m.valueMethod () && m.name ().compareTo ("init") == 0 &&
    //    m.type () == null)
    if (isValueInitializer)
      stream.print (' ' + m.container ().name () + " (");
    else
      stream.print (' ' + m.name () + " (");

    // Step 1.  Print the parameter list.
    boolean firstTime = true;
    Enumeration e = m.parameters ().elements ();
    while (e.hasMoreElements ())
    {
      if (firstTime)
        firstTime = false;
      else
        stream.print (", ");
      ParameterEntry parm = (ParameterEntry)e.nextElement ();

      writeParmType (parm.type (), parm.passType ());

      // Print parm name
      stream.print (' ' + parm.name ());
    }

    // Step 2.  Add the context parameter if necessary.
    if (m.contexts ().size () > 0)
    {
      if (!firstTime)
        stream.print (", ");
      stream.print ("org.omg.CORBA.Context $context");
    }

    // Step 3.  Print the throws clause (if necessary).
    if (m.exceptions ().size () > 0)
    {
      stream.print (") throws ");
      e = m.exceptions ().elements ();
      firstTime = true;
      while (e.hasMoreElements ())
      {
        if (firstTime)
          firstTime = false;
        else
          stream.print (", ");
        stream.print (Util.javaName ((SymtabEntry)e.nextElement ()));
      }
    }
    else
      stream.print (')');
  } // writeMethodSignature

  /**
   *
   **/
  protected void writeParmType (SymtabEntry parm, int passType)
  {
    if (passType != ParameterEntry.In)
    {
      parm = Util.typeOf (parm);
      stream.print (Util.holderName (parm));
    }
    else // passType is `in'
      // <f46082.51> Remove -stateful feature; javaStatefulName() obsolete.
      //stream.print (Util.javaStatefulName (parm));
      stream.print (Util.javaName (parm));
  } // writeParmType

  /**
   *
   **/
  protected void writeDispatchCall ()
  {
    String indent = "       ";
    String fullMethodName = m.fullName ();
    if (m instanceof AttributeEntry)
    {
      // determine the index at which the attribute name starts in the full name
      int index = fullMethodName.lastIndexOf ('/') + 1;
      if (m.type () == null)          // if it's a modifier
        fullMethodName = fullMethodName.substring (0, index) + "_set_" + m.name ();
      else
        fullMethodName = fullMethodName.substring (0, index) + "_get_" + m.name ();
    }
    stream.println (indent + "case " + methodIndex + ":  // " + fullMethodName);
    stream.println (indent + "{");
    indent = indent + "  ";
    if (m.exceptions ().size () > 0)
    {
      stream.println (indent + "try {");
      indent = indent + "  ";
    }

    // Step 1 Read arguments from the input stream
    SymtabEntry mtype = Util.typeOf (m.type ());
    Enumeration parms = m.parameters ().elements ();
    parms = m.parameters ().elements ();
    while (parms.hasMoreElements ())
    {
      ParameterEntry parm     = (ParameterEntry) parms.nextElement ();
      String         name     = parm.name ();
      String         anyName  = '_' + name;
      SymtabEntry    type     = parm.type ();
      int            passType = parm.passType ();

      if (passType == ParameterEntry.In)
        Util.writeInitializer (indent, name, "", type, writeInputStreamRead ("in", type), stream);

      else // the parm is a holder
      {
        String holderName = Util.holderName (type);
        stream.println (indent + holderName + ' ' + name + " = new " + holderName + " ();");
        if (passType == ParameterEntry.Inout)
        {
          if (type instanceof ValueBoxEntry)
          {
            ValueBoxEntry v = (ValueBoxEntry) type;
            TypedefEntry member = ((InterfaceState) v.state ().elementAt (0)).entry;
            SymtabEntry mType = member.type ();
            if (mType instanceof PrimitiveEntry)
              stream.println (indent + name + ".value = (" + writeInputStreamRead ("in", parm.type ()) + ").value;");
            else
              stream.println (indent + name + ".value = " + writeInputStreamRead ("in", parm.type ()) + ";");
          }
          else
            stream.println (indent +  name  + ".value = " + writeInputStreamRead ("in", parm.type ()) + ";");
        }
      }
    }

    // Step 1a.  Read the context parameter if necessary. <d59560>
    if (m.contexts ().size () > 0)
    {
      stream.println (indent + "org.omg.CORBA.Context $context = in.read_Context ();");
    }

    // Step 2 Load return if necessary
    if (mtype != null)
      Util.writeInitializer (indent, "$result", "", mtype, stream);

    // Step 3 Call the method with the list of parameters
    writeMethodCall (indent);

    parms = m.parameters ().elements ();
    boolean firstTime = true;
    while (parms.hasMoreElements ())
    {
      ParameterEntry parm = (ParameterEntry)parms.nextElement ();
      if (firstTime)
        firstTime = false;
      else
        stream.print (", ");
      stream.print (parm.name ());
    }

    // Step 3a.  Add the context parameter if necessary. <d59560>
    if (m.contexts ().size () > 0)
    {
      if (!firstTime)
        stream.print (", ");
      stream.print ("$context");
    }

    stream.println (");");

    //Step 3b. Create reply;
    writeCreateReply (indent);

    // Step 4 Write method's result to the output stream
    if (mtype != null)
    {
      writeOutputStreamWrite (indent, "out", "$result", mtype, stream);
    }

    // Step 5 Write inout/out value to the output stream
    parms = m.parameters ().elements ();
    while (parms.hasMoreElements ())
    {
      ParameterEntry parm = (ParameterEntry)parms.nextElement ();
      int passType = parm.passType ();
      if (passType != ParameterEntry.In)
      {
        writeOutputStreamWrite (indent, "out", parm.name () + ".value", parm.type (), stream);
      }
    }

    // Step 6 Handle exception
    if (m.exceptions ().size () > 0)
    {
      Enumeration exceptions = m.exceptions ().elements ();
      while (exceptions.hasMoreElements ())
      {
        indent = "         ";
        ExceptionEntry exc = (ExceptionEntry) exceptions.nextElement ();
        String fullName = Util.javaQualifiedName (exc);
        stream.println (indent + "} catch (" +  fullName + " $ex) {");
        indent = indent + "  ";
        stream.println (indent + "out = $rh.createExceptionReply ();");
        stream.println (indent + Util.helperName (exc, true) + ".write (out, $ex);"); // <d61056>
      }

      indent = "         ";
      stream.println (indent + "}");
    }

    stream.println ("         break;");
    stream.println ("       }");
    stream.println ();
  } // writeDispatchCall

  /**
   *
   **/
  protected void writeStubBody ( String className )
  {
    // Step 1  Create a request
    String methodName = Util.stripLeadingUnderscores (m.name ());
    if (m instanceof AttributeEntry)
    {
      if (m.type () == null)          // if it's a modifier
        methodName = "_set_" + methodName;
      else
        methodName = "_get_" + methodName;
    }
    if( localOptimization && !isAbstract ) {
        stream.println (ONE_INDENT + "while(true) {" );
        stream.println(TWO_INDENT + "if(!this._is_local()) {" );
    }
    stream.println(THREE_INDENT +
        "org.omg.CORBA.portable.InputStream $in = null;");
    stream.println(THREE_INDENT + "try {");
    stream.println(FOUR_INDENT + "org.omg.CORBA.portable.OutputStream $out =" +
        " _request (\"" +  methodName + "\", " + !m.oneway() + ");");

    // Step 1.b.  Check string bounds <d56554 - klr>
    // begin <d56554> in/inout string bounds check
    Enumeration parms = m.parameters ().elements ();
    while (parms.hasMoreElements ())
    {
      ParameterEntry parm = (ParameterEntry)parms.nextElement ();
      SymtabEntry parmType = Util.typeOf (parm.type ());
      if (parmType instanceof StringEntry)
        if ((parm.passType () == ParameterEntry.In) ||
            (parm.passType () == ParameterEntry.Inout))
        {
          StringEntry string = (StringEntry)parmType;
          if (string.maxSize () != null)
          {
            stream.print (THREE_INDENT + "if (" + parm.name ());
            if (parm.passType () == ParameterEntry.Inout)
              stream.print (".value"); // get from holder
            stream.print (" == null || " + parm.name ());
            if (parm.passType () == ParameterEntry.Inout)
              stream.print (".value"); // get from holder
            stream.println (".length () > (" +
                Util.parseExpression (string.maxSize ()) + "))");
            stream.println (THREE_INDENT +
                "throw new org.omg.CORBA.BAD_PARAM (0," +
                " org.omg.CORBA.CompletionStatus.COMPLETED_NO);");
          }
        }
    }
    // end <d56554> in/inout string bounds check

    // Step 2  Load the parameters into the outputStream
    parms = m.parameters ().elements ();
    while (parms.hasMoreElements ())
    {
      ParameterEntry parm = (ParameterEntry)parms.nextElement ();
      if (parm.passType () == ParameterEntry.In)
        writeOutputStreamWrite(FOUR_INDENT, "$out", parm.name (), parm.type (),
            stream);
      else if (parm.passType () == ParameterEntry.Inout)
        writeOutputStreamWrite(FOUR_INDENT, "$out", parm.name () + ".value",
            parm.type (), stream);
    }

    // Step 2a.  Write the context parameter if necessary. <d59560>
    if (m.contexts ().size () > 0)
    {
      stream.println(FOUR_INDENT + "org.omg.CORBA.ContextList $contextList =" +
         "_orb ().create_context_list ();");

      for (int cnt = 0; cnt < m.contexts ().size (); cnt++)
      {
          stream.println(FOUR_INDENT +
             "$contextList.add (\"" + m.contexts (). elementAt (cnt) + "\");");
      }
      stream.println(FOUR_INDENT +
          "$out.write_Context ($context, $contextList);");
    }

    // Step 3 Invoke the method with the output stream
    stream.println (FOUR_INDENT + "$in = _invoke ($out);");

    SymtabEntry mtype = m.type ();
    if (mtype != null)
      Util.writeInitializer (FOUR_INDENT, "$result", "", mtype,
          writeInputStreamRead ("$in", mtype), stream);

    // Step 4  Read the inout/out values
    parms = m.parameters ().elements ();
    while (parms.hasMoreElements ())
    {
      ParameterEntry parm = (ParameterEntry)parms.nextElement ();
      if (parm.passType () != ParameterEntry.In)
      {
        if (parm.type () instanceof ValueBoxEntry)
        {
          ValueBoxEntry v = (ValueBoxEntry) parm.type ();
          TypedefEntry member =
              ((InterfaceState) v.state ().elementAt (0)).entry;
          SymtabEntry mType = member.type ();
          if (mType instanceof PrimitiveEntry)
            stream.println(FOUR_INDENT +  parm.name () +
                ".value = (" + writeInputStreamRead ("$in", parm.type ()) +
                ").value;");
          else
            stream.println(FOUR_INDENT +  parm.name () +
                ".value = " + writeInputStreamRead ("$in", parm.type ()) +";");
        }
        else
          stream.println (FOUR_INDENT +  parm.name () + ".value = " +
              writeInputStreamRead ("$in", parm.type ()) + ";");
      }
    }
    // Step 4.b.  Check string bounds <d56554 - klr>
    // begin <d56554> out/inout/return string bounds check
    parms = m.parameters ().elements ();
    while (parms.hasMoreElements ())
    {
      ParameterEntry parm = (ParameterEntry)parms.nextElement ();
      SymtabEntry parmType = Util.typeOf (parm.type ());
      if (parmType instanceof StringEntry)
        if ((parm.passType () == ParameterEntry.Out) ||
            (parm.passType () == ParameterEntry.Inout))
        {
          StringEntry string = (StringEntry)parmType;
          if (string.maxSize () != null)
          {
            stream.print (FOUR_INDENT + "if (" + parm.name () +
                ".value.length ()");
            stream.println ("         > (" +
                Util.parseExpression (string.maxSize ()) + "))");
            stream.println (FIVE_INDENT + "throw new org.omg.CORBA.MARSHAL(0,"+
                "org.omg.CORBA.CompletionStatus.COMPLETED_NO);");
          }
        }
    }
    if (mtype instanceof StringEntry)
    {
      StringEntry string = (StringEntry)mtype;
      if (string.maxSize () != null)
      {
        stream.println(FOUR_INDENT + "if ($result.length () > (" +
            Util.parseExpression (string.maxSize ()) + "))");
        stream.println (FIVE_INDENT + "throw new org.omg.CORBA.MARSHAL (0," +
            " org.omg.CORBA.CompletionStatus.COMPLETED_NO);");
      }
    }
    // end <d56554> out/inout/return string bounds check

    // Step 5  Handle return if necessary
    if (mtype != null) {
      stream.println(FOUR_INDENT + "return $result;");
    } else {
      stream.println(FOUR_INDENT + "return;");
    }

    // Step 6  Handle exceptions
    stream.println(THREE_INDENT +
        "} catch (org.omg.CORBA.portable.ApplicationException " + "$ex) {");
    stream.println(FOUR_INDENT + "$in = $ex.getInputStream ();");
    stream.println(FOUR_INDENT + "String _id = $ex.getId ();");

    if (m.exceptions ().size () > 0)
    {
      Enumeration exceptions = m.exceptions ().elements ();
      boolean firstExc = true;
      while (exceptions.hasMoreElements ())
      {
        ExceptionEntry exc = (ExceptionEntry)exceptions.nextElement ();
        if (firstExc)
        {
          stream.print(FOUR_INDENT + "if ");
          firstExc = false;
        }
        else
          stream.print(FOUR_INDENT + "else if ");

        stream.println( "(_id.equals (\"" + exc.repositoryID ().ID () + "\"))");
        stream.println (FIVE_INDENT + "throw " +
            Util.helperName ((SymtabEntry)exc, false) + ".read ($in);");
      }
      stream.println(FOUR_INDENT + "else");
      stream.println(FIVE_INDENT + "throw new org.omg.CORBA.MARSHAL (_id);");
    }
    else
      stream.println(FOUR_INDENT + "throw new org.omg.CORBA.MARSHAL (_id);");

    stream.println(THREE_INDENT +
        "} catch (org.omg.CORBA.portable.RemarshalException $rm) {");
    stream.print( FOUR_INDENT );
    if (m.type () != null) // not a void method
      stream.print ("return ");
    stream.print (m.name () + " (");
    {
      // write parm names
      boolean firstTime = true;
      Enumeration e = m.parameters ().elements ();
      while (e.hasMoreElements ())
      {
        if (firstTime)
          firstTime = false;
        else
          stream.print (", ");
        ParameterEntry parm = (ParameterEntry)e.nextElement ();
        stream.print (parm.name ());
      }
      // Step 2.  Add the context parameter if necessary. <d59297>
      if (m.contexts ().size () > 0)
      {
        if (!firstTime)
          stream.print (", ");
        stream.print ("$context");
      }
    }
    stream.println (TWO_INDENT + ");");
    stream.println (THREE_INDENT + "} finally {");
    stream.println (FOUR_INDENT + "_releaseReply ($in);");
    stream.println (THREE_INDENT + "}");
    if( localOptimization && !isAbstract ) {
        stream.println (TWO_INDENT + "}");
        writeStubBodyForLocalInvocation( className, methodName );
    }

  } // writeStubBody


  /**
   * This method writes the else part of the stub method invocation to
   * enable local invocation in case of collocation.
   * NOTE: This will only be invoked from writeStubBody.
   */
  private void writeStubBodyForLocalInvocation( String className,
      String methodName )
  {
    stream.println (TWO_INDENT + "else {" );
    stream.println (THREE_INDENT +
        "org.omg.CORBA.portable.ServantObject _so =");
    stream.println (FOUR_INDENT + "_servant_preinvoke(\"" + methodName +
                    "\", _opsClass);" );
    stream.println(THREE_INDENT + "if (_so == null ) {");
    stream.println(FOUR_INDENT + "continue;" );
    stream.println(THREE_INDENT + "}");
    stream.println(THREE_INDENT + className + "Operations _self =" );
    stream.println(FOUR_INDENT + "(" + className + "Operations) _so.servant;");
    stream.println(THREE_INDENT + "try {" );
    Enumeration parms = m.parameters ().elements ();
    if (m instanceof AttributeEntry)
    {
        // Local Method Name should drop _get_ or _set_ prefix for attribute
        // entry
        methodName = methodName.substring( ATTRIBUTE_METHOD_PREFIX_LENGTH );
    }
    boolean voidReturnType = (this.m.type() == null);
    if ( !voidReturnType ) {
        stream.println (FOUR_INDENT + Util.javaName (this.m.type ()) +
            " $result;");
    }
    if( !isValueInitializer() ) {
        if ( voidReturnType ) {
            stream.print(FOUR_INDENT + "_self." + methodName + "( " );
        } else {
            stream.print(FOUR_INDENT + "$result = _self." +
                     methodName + "( " );
        }
        while (parms.hasMoreElements ()) {
            ParameterEntry param = (ParameterEntry)parms.nextElement ();
            if( parms.hasMoreElements( ) ) {
                stream.print( " " + param.name() +  "," );
            } else  {
                stream.print( " " + param.name() );
            }
        }
        stream.print( ");" );
        stream.println( " " );
        if( voidReturnType ) {
            stream.println(FOUR_INDENT + "return;" );
        } else {
            stream.println(FOUR_INDENT + "return $result;" );
        }
    }
    stream.println(" ");
    stream.println (THREE_INDENT + "}" );
    stream.println (THREE_INDENT + "finally {" );
    stream.println (FOUR_INDENT + "_servant_postinvoke(_so);" );
    stream.println (THREE_INDENT + "}" );
    stream.println (TWO_INDENT + "}" );
    stream.println (ONE_INDENT + "}" );
  }


  protected void writeLocalStubBody (InterfaceEntry i)
  {
    // Step 1  Create a request
    String methodName = Util.stripLeadingUnderscores (m.name ());
    if (m instanceof AttributeEntry)
    {
      if (m.type () == null)          // if it's a modifier
        methodName = "_set_" + methodName;
      else
        methodName = "_get_" + methodName;
    }
    //stream.println ("    while(true) {");
    stream.println ("      org.omg.CORBA.portable.ServantObject $so = " +
                    "_servant_preinvoke (\"" + methodName + "\", " + "_opsClass);");
    //stream.println ("      if ($so == null) {");
    //stream.println ("          continue;");
    //stream.println ("      }");
    String opsName = i.name() + "Operations";
    stream.println ("      " + opsName + "  $self = " + "(" + opsName + ") " + "$so.servant;");
    stream.println ();
    stream.println ("      try {");
    stream.print ("         ");
    if (m.type () != null) // not a void method
        stream.print ("return ");
    stream.print ("$self." + m.name () + " (");
    {
        // write parm names
        boolean firstTime = true;
        Enumeration e = m.parameters ().elements ();
        while (e.hasMoreElements ())
        {
          if (firstTime)
            firstTime = false;
          else
            stream.print (", ");
          ParameterEntry parm = (ParameterEntry)e.nextElement ();
          stream.print (parm.name ());
        }
        // Step 2.  Add the context parameter if necessary. <d59297>
        if (m.contexts ().size () > 0)
        {
          if (!firstTime)
            stream.print (", ");
          stream.print ("$context");
        }
    }
    stream.println (");");
    //stream.println ("      } catch (org.omg.CORBA.portable.RemarshalException $rm) {");
    //stream.println ("         continue; ");
    stream.println ("      } finally {");
    stream.println ("          _servant_postinvoke ($so);");
    stream.println ("      }");
    //stream.println ("    }");

  } // writeLocalStubBody



  /**
   *
   **/
  private void writeInsert (String indent, String target, String source, SymtabEntry type, PrintWriter stream)
  {
    String typeName = type.name ();
    if (type instanceof PrimitiveEntry)
    {
      // RJB does something have to be done with TC offsets?
      if (typeName.equals ("long long"))
        stream.println (indent + source + ".insert_longlong (" + target + ");");
      else if (typeName.equals ("unsigned short"))
        stream.println (indent + source + ".insert_ushort (" + target + ");");
      else if (typeName.equals ("unsigned long"))
        stream.println (indent + source + ".insert_ulong (" + target + ");");
      else if (typeName.equals ("unsigned long long"))
        stream.println (indent + source + ".insert_ulonglong (" + target + ");");
      else
        stream.println (indent + source + ".insert_" + typeName + " (" + target + ");");
    }
    else if (type instanceof StringEntry)
      stream.println (indent + source + ".insert_" + typeName + " (" + target + ");");
    else
      stream.println (indent + Util.helperName (type, true) + ".insert (" + source + ", " + target + ");"); // <d61056>
  } // writeInsert

  /**
   *
   **/
  private void writeType (String indent, String name, SymtabEntry type, PrintWriter stream)
  {
    if (type instanceof PrimitiveEntry)
    {
      // RJB does something have to be done with TC offsets?
      if (type.name ().equals ("long long"))
        stream.println (indent + name + " (org.omg.CORBA.ORB.init ().get_primitive_tc (org.omg.CORBA.TCKind.tk_longlong));");
      else if (type.name ().equals ("unsigned short"))
        stream.println (indent + name + " (org.omg.CORBA.ORB.init ().get_primitive_tc (org.omg.CORBA.TCKind.tk_ushort));");
      else if (type.name ().equals ("unsigned long"))
        stream.println (indent + name + " (org.omg.CORBA.ORB.init ().get_primitive_tc (org.omg.CORBA.TCKind.tk_ulong));");
      else if (type.name ().equals ("unsigned long long"))
        stream.println (indent + name + " (org.omg.CORBA.ORB.init ().get_primitive_tc (org.omg.CORBA.TCKind.tk_ulonglong));");
      else
        stream.println (indent + name + " (org.omg.CORBA.ORB.init ().get_primitive_tc (org.omg.CORBA.TCKind.tk_" + type.name () + "));");
    }
    else if (type instanceof StringEntry)
    {
      StringEntry s = (StringEntry)type;
      Expression e  = s.maxSize ();
      if (e == null)
        stream.println (indent + name + " (org.omg.CORBA.ORB.init ().create_" + type.name () + "_tc (" + Util.parseExpression (e) + "));");
     else
        stream.println (indent + name + " (org.omg.CORBA.ORB.init ().create_" + type.name () + "_tc (0));");
    }
    else
      stream.println (indent + name + '(' + Util.helperName (type, true) + ".type ());"); // <d61056>
  } // writeType

  /**
   *
   **/
  private void writeExtract (String indent, String target, String source, SymtabEntry type, PrintWriter stream)
  {
    if (type instanceof PrimitiveEntry)
    {
      if (type.name ().equals ("long long"))
        stream.println (indent + target + " = " + source + ".extract_longlong ();");
      else if (type.name ().equals ("unsigned short"))
        stream.println (indent + target + " = " + source + ".extract_ushort ();");
      else if (type.name ().equals ("unsigned long"))
        stream.println (indent + target + " = " + source + ".extract_ulong ();");
      else if (type.name ().equals ("unsigned long long"))
        stream.println (indent + target + " = " + source + ".extract_ulonglong ();");
      else
        stream.println (indent + target + " = " + source + ".extract_" + type.name () + " ();");
    }
    else if (type instanceof StringEntry)
      stream.println (indent + target + " = " + source + ".extract_" + type.name () + " ();");
    else
      stream.println (indent + target + " = " + Util.helperName (type, true) + ".extract (" + source + ");"); // <d61056>
  } // writeExtract

  /**
   *
   **/
  private String writeExtract (String source, SymtabEntry type)
  {
    String extract;
    if (type instanceof PrimitiveEntry)
    {
      if (type.name ().equals ("long long"))
        extract = source + ".extract_longlong ()";
      else if (type.name ().equals ("unsigned short"))
        extract = source + ".extract_ushort ()";
      else if (type.name ().equals ("unsigned long"))
        extract = source + ".extract_ulong ()";
      else if (type.name ().equals ("unsigned long long"))
        extract = source + ".extract_ulonglong ()";
      else
        extract = source + ".extract_" + type.name () + " ()";
    }
    else if (type instanceof StringEntry)
      extract = source + ".extract_" + type.name () + " ()";
    else
      extract = Util.helperName (type, true) + ".extract (" + source + ')'; // <d61056>
    return extract;
  } // writeExtract

  /**
   *
   **/
  private void writeSkeletonBody ()
  {
    SymtabEntry mtype = Util.typeOf (m.type ());

    // If there is a return value, increment the appropriate counter
    stream.print ("    ");
    if (mtype != null)
      stream.print ("return ");
    stream.print ("_impl." + m.name () + '(');

    // Load the parameters
    Enumeration parms = m.parameters ().elements ();
    boolean first = true;
    while (parms.hasMoreElements ())
    {
      ParameterEntry parm = (ParameterEntry)parms.nextElement ();
      if (first)
        first = false;
      else
        stream.print (", ");
      stream.print (parm.name ());
    }
    if (m.contexts ().size () != 0)
    {
      if (!first)
        stream.print (", ");
      stream.print ("$context");
    }

    stream.println (");");
  } // writeSkeletonBody

  /**
   *
   **/
  protected String passType (int passType)
  {
    String type;
    switch (passType)
    {
      case ParameterEntry.Inout:
        type = "org.omg.CORBA.ARG_INOUT.value";
        break;
      case ParameterEntry.Out:
        type = "org.omg.CORBA.ARG_OUT.value";
        break;
      case ParameterEntry.In:
      default:
        type = "org.omg.CORBA.ARG_IN.value";
        break;
    }
    return type;
  } // passType

  /**
   * This is only used by AttributeGen.  The java mapping says
   * the names should be getXXX and setXXX, but CORBA says they
   * should be _get_XXX and _set_XXX.  this.name () will be
   * getXXX.  realName is set by AttributeGen to _get_XXX.
   **/
  protected void serverMethodName (String name)
  {
    realName = (name == null) ? "" : name;
  } // serverMethodName

  /**
   *
   **/
  private void writeOutputStreamWrite (String indent, String oStream, String name, SymtabEntry type, PrintWriter stream)
  {
    String typeName = type.name ();
    stream.print (indent);
    if (type instanceof PrimitiveEntry)
    {
      if (typeName.equals ("long long"))
        stream.println (oStream + ".write_longlong (" + name +");");
      else if (typeName.equals ("unsigned short"))
        stream.println (oStream + ".write_ushort (" + name + ");");
      else if (typeName.equals ("unsigned long"))
        stream.println (oStream + ".write_ulong (" + name + ");");
      else if (typeName.equals ("unsigned long long"))
        stream.println (oStream + ".write_ulonglong (" + name + ");");
      else
        stream.println (oStream + ".write_" + typeName + " (" + name + ");");
    }
    else if (type instanceof StringEntry)
      stream.println (oStream + ".write_" + typeName + " (" + name + ");");
    else if (type instanceof SequenceEntry)
      stream.println (oStream + ".write_" + type.type().name() + " (" + name + ");");
    else if (type instanceof ValueBoxEntry)
    {
      ValueBoxEntry v = (ValueBoxEntry) type;
      TypedefEntry member = ((InterfaceState) v.state ().elementAt (0)).entry;
      SymtabEntry mType = member.type ();

      // if write value to the boxed holder indicated by the name ending with ".value"
      if (mType instanceof PrimitiveEntry && name.endsWith (".value"))
        stream.println (Util.helperName (type, true) + ".write (" + oStream + ", "  // <d61056>
        + " new " + Util.javaQualifiedName (type) + " (" + name + "));"); //<d60929>
      else
        stream.println (Util.helperName (type, true) + ".write (" + oStream + ", " + name + ");"); //<d60929> // <d61056>
    }
    else if (type instanceof ValueEntry)
        stream.println (Util.helperName (type, true) + ".write (" + oStream + ", " + name + ");"); //<d60929> // <d61056>
    else
      stream.println (Util.helperName (type, true) + ".write (" + oStream + ", " + name + ");"); // <d61056>
  } // writeOutputStreamWrite

  /**
   *
   **/
  private String writeInputStreamRead (String source, SymtabEntry type)
  {
    String read = "";
    if (type instanceof PrimitiveEntry)
    {
      if (type.name ().equals ("long long"))
        read = source + ".read_longlong ()";
      else if (type.name ().equals ("unsigned short"))
        read = source + ".read_ushort ()";
      else if (type.name ().equals ("unsigned long"))
        read = source + ".read_ulong ()";
      else if (type.name ().equals ("unsigned long long"))
        read = source + ".read_ulonglong ()";
      else
        read = source + ".read_" + type.name () + " ()";
    }
    else if (type instanceof StringEntry)
      read = source + ".read_" + type.name () + " ()";
    else
      read = Util.helperName (type, true) + ".read (" + source + ')'; // <d61056>
    return read;
  } // writeInputStreamRead

  /**
   *
   **/
  protected void writeMethodCall (String indent)
  {
    SymtabEntry mtype = Util.typeOf (m.type ());
    if (mtype == null)
      stream.print (indent + "this." + m.name () + " (");
    else
      stream.print (indent + "$result = this." + m.name () + " (");
  } // writeMethodCall

  /**
   *
   **/
  protected void writeCreateReply(String indent){
    stream.println(indent + "out = $rh.createReply();");
  }

  protected int           methodIndex = 0;
  protected String        realName    = "";
  protected Hashtable     symbolTable = null;
  protected MethodEntry   m           = null;
  protected PrintWriter   stream      = null;
  protected boolean localOptimization = false;
  protected boolean isAbstract        = false;
} // class MethodGen
