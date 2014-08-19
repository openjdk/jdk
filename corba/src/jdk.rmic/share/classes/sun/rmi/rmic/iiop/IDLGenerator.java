/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
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
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 */

package sun.rmi.rmic.iiop;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.Vector;
import sun.tools.java.Identifier;
import sun.tools.java.ClassDefinition;
import sun.tools.java.CompilerError;
import sun.rmi.rmic.IndentingWriter;
import java.util.HashSet;
import sun.rmi.rmic.Main;

/**
 * An IDL generator for rmic.
 *
 * @author  Steve Newberry, Bryan Atsatt
 */
public class IDLGenerator extends sun.rmi.rmic.iiop.Generator {

    private boolean valueMethods = true;
    private boolean factory = true;                              //init==!factory
    private Hashtable ifHash = new Hashtable();              //IDL file Hashtable
    private Hashtable imHash = new Hashtable();            //IDL module Hashtable

    private boolean isThrown = true;                      //flag for writeInclude
    private boolean isException = true;       //flag for writeBanner, writeIfndef
    private boolean isForward = true;                      //flag for writeIfndef
    private boolean forValuetype = true;                 //flag for writeInherits

    /**
     * Default constructor for Main to use.
     */
    public IDLGenerator() {
    }


    /**
     * Return true if a new instance should be created for each
     * class on the command line. Subclasses which return true
     * should override newInstance() to return an appropriately
     * constructed instance.
     */
    protected boolean requireNewInstance() {
        return false;
    }

    /**
     * Return true if non-conforming types should be parsed.
     * @param stack The context stack.
     */
    protected boolean parseNonConforming(ContextStack stack) {
        return valueMethods;
    }

    /**
     * Create and return a top-level type.
     * @param env The compiler environment.
     * @param cdef The top-level class definition.
     * @return An RemoteType or null if is non-conforming.
     */
    protected sun.rmi.rmic.iiop.CompoundType getTopType(ClassDefinition cdef,
                                                        ContextStack stack) {
        return CompoundType.forCompound(cdef,stack);
    }


    /**
     * Return an Identifier which contains the fully-qualified IDL filename
     * for the given OutputType.
     * The OutputType contains a filename string (not directory) and Type.
     * @param ot the OutputType for which directory nesting is to be defined.
     * @return the new identifier.
     */
    protected Identifier getOutputId (
                                      OutputType ot ) {
        Identifier id = super.getOutputId( ot );

        Type t = ot.getType();
        String fName = ot.getName();

        if ( id == idJavaLangClass )                 //java.lang.Class and array of
            if ( t.isArray() )
                return Identifier.lookup(
                                         "org.omg.boxedRMI.javax.rmi.CORBA." + fName  );
            else return idClassDesc;

        if ( id == idJavaLangString &&                  //array of java.lang.String
             t.isArray() )
            return Identifier.lookup( "org.omg.boxedRMI.CORBA." + fName );

        if ( "org.omg.CORBA.Object".equals( t.getQualifiedName() ) &&
             t.isArray() )                          //array of org.omg.CORBA.Object
            return Identifier.lookup( "org.omg.boxedRMI." + fName );

        if ( t.isArray()) {                                                 //array
            ArrayType at = (ArrayType)t;
            Type et = at.getElementType();
            if ( et.isCompound() ) {
                CompoundType ct = (CompoundType)et;
                String qName = ct.getQualifiedName();
                if ( ct.isIDLEntity() )
                    return Identifier.lookup( getQualifiedName( at ) );
            }
            return Identifier.lookup( idBoxedRMI,id );
        }

        if ( t.isCompound() ) {                                   //boxed IDLEntity
            CompoundType ct = (CompoundType)t;
            String qName = ct.getQualifiedName();
            if ( ct.isBoxed() )
                return Identifier.lookup( getQualifiedName( ct ) );
        }

        return id;
    }



    /**
     * Return the file name extension for the given file name (e.g. ".java").
     * All files generated with the ".java" extension will be compiled. To
     * change this behavior for ".java" files, override the compileJavaSourceFile
     * method to return false.
     * @param outputType One of the items returned by getOutputTypesFor(...)
     */
    protected String getFileNameExtensionFor(OutputType outputType) {
        return IDL_FILE_EXTENSION;
    }


    /**
     * Examine and consume command line arguments.
     * @param argv The command line arguments. Ignore null
     * and unknown arguments. Set each consumed argument to null.
     * @param error Report any errors using the main.error() methods.
     * @return true if no errors, false otherwise.
     */
    public boolean parseArgs(String argv[], Main main) {
        boolean result = super.parseArgs(argv,main);
        String idlFrom;
        String idlTo;
        if (result) {
        nextArg:
            for (int i = 0; i < argv.length; i++) {
                if (argv[i] != null) {
                    if (argv[i].equalsIgnoreCase("-idl")) {
                        idl = true;
                        argv[i] = null;
                    }
                    else if ( argv[i].equalsIgnoreCase( "-valueMethods" ) ) {
                        valueMethods = true;
                        argv[i] = null;
                    }
                    else if ( argv[i].equalsIgnoreCase( "-noValueMethods" ) ) {
                        valueMethods = false;
                        argv[i] = null;
                    }
                    else if ( argv[i].equalsIgnoreCase( "-init" ) ) {
                        factory = false;
                        argv[i] = null;
                }
                    else if ( argv[i].equalsIgnoreCase( "-factory" ) ) {
                        factory = true;
                        argv[i] = null;
            }
                    else if ( argv[i].equalsIgnoreCase( "-idlfile" ) ) {
                        argv[i] = null;
                        if ( ++i < argv.length && argv[i] != null && !argv[i].startsWith("-") ) {
                            idlFrom = argv[i];
                            argv[i] = null;
                            if ( ++i < argv.length && argv[i] != null && !argv[i].startsWith("-") ) {
                                idlTo = argv[i];
                                argv[i] = null;
                                ifHash.put( idlFrom,idlTo );
                                continue nextArg;
        }
                        }
                        main.error("rmic.option.requires.argument", "-idlfile");
                        result = false;
                    }
                    else if ( argv[i].equalsIgnoreCase( "-idlmodule" ) ) {
                        argv[i] = null;
                        if ( ++i < argv.length && argv[i] != null && !argv[i].startsWith("-") ) {
                            idlFrom = argv[i];
                            argv[i] = null;
                            if ( ++i < argv.length && argv[i] != null && !argv[i].startsWith("-") ) {
                                idlTo = argv[i];
                                argv[i] = null;
                                imHash.put( idlFrom,idlTo );
                                continue nextArg;
                            }
                        }
                        main.error("rmic.option.requires.argument", "-idlmodule");
                        result = false;
                    }


                }
            }
        }
        return result;
    }



    /**
     * Return an array of OutputTypes for the IDL files that need to be
     * generated for the given top-level type.
     * OutputTypes contain filename string (not directory) and Type.
     * @param topType The type returned by getTopType().
     * @param alreadyChecked A set of Types which have already been checked.
     * @return Array of OutputTypes to generate
     */
    protected OutputType[] getOutputTypesFor(
                                             CompoundType topType,
                                             HashSet alreadyChecked ) {
        Vector refVec = getAllReferencesFor( topType );
        Vector outVec = new Vector();
        for ( int i1 = 0; i1 < refVec.size(); i1++ ) {          //forall references
            Type t = (Type)refVec.elementAt( i1 );
            if ( t.isArray() ) {
                ArrayType at = (ArrayType)t;
                int dim = at.getArrayDimension();
                Type et = at.getElementType();
                String fName = unEsc( et.getIDLName() ).replace( ' ','_' );
                for ( int i2 = 0; i2 < dim; i2++ ) {                //foreach dimension
                    String fileName = "seq" + ( i2 + 1 ) + "_" + fName;
                    outVec.addElement( new OutputType( fileName,at ) );
                }
            }
            else if ( t.isCompound() ) {
                String fName = unEsc( t.getIDLName() );
                outVec.addElement( new OutputType( fName.replace( ' ','_' ),t ) );
            if ( t.isClass() ) {
                ClassType ct = (ClassType)t;
                    if ( ct.isException() ) {                            //exception file
                        fName = unEsc( ct.getIDLExceptionName() );
                        outVec.addElement( new OutputType( fName.replace( ' ','_' ),t ) );
            }
        }
    }
    }
        OutputType[] outArr = new OutputType[outVec.size()];
        outVec.copyInto( outArr );
        return outArr;
    }

    /**
     * Get all referenced types of a given tyoe for which an IDL file needs
     * to be generated.
     * @param ct The given type.
     * @return Vector of Types for which IDL must be generated.
     */
    protected Vector getAllReferencesFor(
                                         CompoundType ct ) {
        Hashtable refHash = new Hashtable();
        Hashtable spcHash = new Hashtable();
        Hashtable arrHash = new Hashtable();
        int refSize;
        refHash.put( ct.getQualifiedName(),ct );               //put the given type
        accumulateReferences( refHash,spcHash,arrHash );
        do {
            refSize = refHash.size();
            accumulateReferences( refHash,spcHash,arrHash );
        }
        while ( refSize < refHash.size() );        //till hashtable stays same size

        Vector outVec = new Vector();
        Enumeration e = refHash.elements();                   //ordinary references
        while ( e.hasMoreElements() ) {
            CompoundType t = (CompoundType)e.nextElement();
            outVec.addElement( t );
        }
        e = spcHash.elements();                                //special references
        while ( e.hasMoreElements() ) {
            CompoundType t = (CompoundType)e.nextElement();
            outVec.addElement( t );
    }
        e = arrHash.elements();                                  //array references
                                         nextSequence:
        while ( e.hasMoreElements() ) {
            ArrayType at = (ArrayType)e.nextElement();
            int dim = at.getArrayDimension();
            Type et = at.getElementType();
            Enumeration e2 = arrHash.elements();
            while ( e2.hasMoreElements() ) {                   //eliminate duplicates
                ArrayType at2 = (ArrayType)e2.nextElement();
                if ( et == at2.getElementType() &&                //same element type &
                     dim < at2.getArrayDimension() )               //smaller dimension?
                    continue nextSequence;                              //ignore this one
            }
            outVec.addElement( at );
        }
        return outVec;
    }


    /**
     * Accumulate and filter all those types that are referenced by the given
     * referenced types.
     * Keep only those for which IDL is to be generated.
     * @param refHash Hashtable containing the given types
     * @param spcHash Hashtable containing referenced specials (IDL typedefs)
     * @param arrHash Hashtable containing referenced arrays (dimensioned)
     */
    protected void accumulateReferences(
                                        Hashtable refHash,
                                        Hashtable spcHash,
                                        Hashtable arrHash ) {
        Enumeration e = refHash.elements();
        while ( e.hasMoreElements() ) {
            CompoundType t = (CompoundType)e.nextElement();
            Vector datVec = getData( t );                     //collect and sort data
            Vector mthVec = getMethods( t );             //collect and filter methods
            getInterfaces( t,refHash );                          //collect interfaces
            getInheritance( t,refHash );                            //add inheritance
            getMethodReferences( mthVec,refHash,spcHash,arrHash,refHash );
            getMemberReferences( datVec,refHash,spcHash,arrHash );
        }
        e = arrHash.elements();                      //add array element references
        while ( e.hasMoreElements() ) {
            ArrayType at = (ArrayType)e.nextElement();
            Type et = at.getElementType();
            addReference( et,refHash,spcHash,arrHash );
        }
        e = refHash.elements();
        while ( e.hasMoreElements() ) {
            CompoundType t = (CompoundType)e.nextElement();
            if ( !isIDLGeneratedFor( t ) )              //remove if no IDL generation
                refHash.remove( t.getQualifiedName() );
    }
    }



    /**
     * Determine if IDL should be generated for a referenced type.
     * Do not generate IDL for a CORBA Object reference. It gets mapped
     * to the original IDL or to Object (if exactly org.omg.CORBA.Object)
     * Generate (boxed) IDL for an IDL Entity unless it is an IDL user
     * exception, a ValueBase, an AbstractBase (or a CORBA Object).
     * Do not generate IDL for Implementation classes..unless they inherit
     * from multiple distinct remote interfaces
     * @param t The type to check.
     * @return true or false
     */
    protected boolean isIDLGeneratedFor(
                                 CompoundType t ) {
        if ( t.isCORBAObject() ) return false;
        if ( t.isIDLEntity() )
            if ( t.isBoxed() ) return true;
            else if ( "org.omg.CORBA.portable.IDLEntity"
                      .equals( t.getQualifiedName() ) ) return true;
            else if ( t.isCORBAUserException() ) return true;
            else return false;
        Hashtable inhHash = new Hashtable();
        getInterfaces( t,inhHash );
        if ( t.getTypeCode() == TYPE_IMPLEMENTATION )
            if ( inhHash.size() < 2 ) return false;         //no multiple inheritance
            else return true;
        return true;                                   //generate IDL for this type
    }


    /**
     * Write the output for the given OutputFileName into the output stream.
     * (The IDL mapping for java.lang.Class is generated from
     * javax.rmi.CORBA.ClassDesc in the tools workspace)
     * @param OutputType ot One of the items returned by getOutputTypesFor(...)
     * @param alreadyChecked A set of Types which have already been checked.
     *  Intended to be passed to Type.collectMatching(filter,alreadyChecked).
     * @param p The output stream.
     */
    protected void writeOutputFor(
                                  OutputType ot,
                                  HashSet alreadyChecked,
                                  IndentingWriter p )
        throws IOException {
        Type t = ot.getType();
        if ( t.isArray() ) {                                //specialcase: sequence
            writeSequence( ot,p );
            return;
        }
        if ( isSpecialReference( t ) ) {                //specialcase: IDL typecode
            writeSpecial( t,p );
            return;
        }
        if ( t.isCompound() ) {                            //specialcase: boxed IDL
            CompoundType ct = (CompoundType)t;
            if ( ct.isIDLEntity() && ct.isBoxed() ) {
                writeBoxedIDL( ct,p );
                return;
            }
        }
        if ( t.isClass() ) {                               //specialcase: exception
            ClassType ct = (ClassType)t;
            if ( ct.isException() ) {
                String eName = unEsc( ct.getIDLExceptionName() );
                String fName = ot.getName();
                if ( fName.equals( eName.replace( ' ','_' ) ) ) {
                    writeException( ct,p );
                    return;
                }
            }
        }
        switch ( t.getTypeCode() ) {                                 //general case
        case TYPE_IMPLEMENTATION:
            writeImplementation( (ImplementationType)t,p );
            break;
        case TYPE_NC_CLASS:
        case TYPE_NC_INTERFACE:
            writeNCType( (CompoundType)t,p );
            break;
        case TYPE_ABSTRACT:                        //AbstractType is a RemoteType
        case TYPE_REMOTE:
            writeRemote( (RemoteType)t,p );
            break;
        case TYPE_VALUE:
            writeValue( (ValueType)t,p );
            break;
        default:
            throw new CompilerError(
                                    "IDLGenerator got unexpected type code: "
                                    + t.getTypeCode());
        }
    }


    /**
     * Write an IDL interface definition for a Java implementation class
     * @param t The current ImplementationType
     * @param p The output stream.
     */
    protected void writeImplementation(
                                       ImplementationType t,
                                       IndentingWriter p )
        throws IOException {
        Hashtable inhHash = new Hashtable();
        Hashtable refHash = new Hashtable();
        getInterfaces( t,inhHash );                            //collect interfaces

        writeBanner( t,0,!isException,p );
        writeInheritedIncludes( inhHash,p );
        writeIfndef( t,0,!isException,!isForward,p );
        writeIncOrb( p );
        writeModule1( t,p );
        p.pln();p.pI();
        p.p( "interface " + t.getIDLName() );
        writeInherits( inhHash,!forValuetype,p );

        p.pln( " {" );
        p.pln( "};" );

        p.pO();p.pln();
        writeModule2( t,p );
        writeEpilog( t,refHash,p );
    }


    /**
     * Write an IDL valuetype definition for
     * 1) a nonconforming Java class
     * 2) a nonconforming Java interface (that is not an AbstractType)
     * @param t The current NC Type (NCClassType or NCInterfaceType)
     * @param p The output stream.
     */
    protected void writeNCType(
                               CompoundType t,
                               IndentingWriter p )
        throws IOException {
        Vector conVec = getConstants( t );                      //collect constants
        Vector mthVec = getMethods( t );                          //collect methods
        Hashtable inhHash = new Hashtable();
        Hashtable refHash = new Hashtable();
        Hashtable spcHash = new Hashtable();
        Hashtable arrHash = new Hashtable();
        Hashtable excHash = new Hashtable();
        getInterfaces( t,inhHash );                            //collect interfaces
        getInheritance( t,inhHash );                              //add inheritance
        getMethodReferences( mthVec,refHash,spcHash,arrHash,excHash );

        writeProlog( t,refHash,spcHash,arrHash,excHash,inhHash,p );
        writeModule1( t,p );
        p.pln();p.pI();
        p.p( "abstract valuetype " + t.getIDLName() );
        writeInherits( inhHash,!forValuetype,p );

        p.pln( " {" );
        if ( conVec.size() + mthVec.size() > 0 ) {                   //any content?
            p.pln();p.pI();
            for ( int i1 = 0; i1 < conVec.size(); i1++ )            //write constants
                writeConstant( (CompoundType.Member)conVec.elementAt( i1 ),p );
            for ( int i1 = 0; i1 < mthVec.size(); i1++ )              //write methods
                writeMethod( (CompoundType.Method)mthVec.elementAt( i1 ),p );
            p.pO();p.pln();
        }
        p.pln( "};" );

                p.pO();p.pln();
        writeModule2( t,p );
        writeEpilog( t,refHash,p );
    }


    /**
     * Write an IDL interface definition for either:
     * 1) a conforming Java remote interface (RemoteType)..or
     * 2) a non-conforming Java interface whose methods all throw
     *     java.rmi.RemoteException (AbstractType)
     * @param t The current RemoteType
     * @param p The output stream.
     */
    protected void writeRemote(
                               RemoteType t,
                               IndentingWriter p )
        throws IOException {
        Vector conVec = getConstants( t );                      //collect constants
        Vector mthVec = getMethods( t );                          //collect methods
        Hashtable inhHash = new Hashtable();
        Hashtable refHash = new Hashtable();
        Hashtable spcHash = new Hashtable();
        Hashtable arrHash = new Hashtable();
        Hashtable excHash = new Hashtable();
        getInterfaces( t,inhHash );                            //collect interfaces
        getMethodReferences( mthVec,refHash,spcHash,arrHash,excHash );

        writeProlog( t,refHash,spcHash,arrHash,excHash,inhHash,p );
        writeModule1( t,p );
        p.pln();p.pI();
        if ( t.getTypeCode() == TYPE_ABSTRACT ) p.p( "abstract " );
        p.p( "interface " + t.getIDLName() );
        writeInherits( inhHash,!forValuetype,p );

        p.pln( " {" );
        if ( conVec.size() + mthVec.size() > 0 ) {      //any constants or methods?
            p.pln();p.pI();
            for ( int i1 = 0; i1 < conVec.size(); i1++ )                  //constants
                writeConstant( (CompoundType.Member)conVec.elementAt( i1 ),p );
            for ( int i1 = 0; i1 < mthVec.size(); i1++ )        //methods, attributes
                writeMethod( (CompoundType.Method)mthVec.elementAt( i1 ),p );
            p.pO();p.pln();
        }
        p.pln( "};" );

        p.pO();p.pln();
        writeRepositoryID ( t,p );
        p.pln();
        writeModule2( t,p );
        writeEpilog( t,refHash,p );
    }


    /**
     * Write an IDL valuetype definition for a conforming Java class.
     * Methods and constructors are optional..controlled by -valueMethods flag
     * @param t The current ValueType
     * @param p The output stream.
     */
    protected void writeValue(
                              ValueType t,
                              IndentingWriter p )
        throws IOException {
        Vector datVec = getData( t );                       //collect and sort data
        Vector conVec = getConstants( t );                      //collect constants
        Vector mthVec = getMethods( t );               //collect and filter methods
        Hashtable inhHash = new Hashtable();
        Hashtable refHash = new Hashtable();
        Hashtable spcHash = new Hashtable();
        Hashtable arrHash = new Hashtable();
        Hashtable excHash = new Hashtable();
        getInterfaces( t,inhHash );                            //collect interfaces
        getInheritance( t,inhHash );                              //add inheritance
        getMethodReferences( mthVec,refHash,spcHash,arrHash,excHash );
        getMemberReferences( datVec,refHash,spcHash,arrHash );

        writeProlog( t,refHash,spcHash,arrHash,excHash,inhHash,p );
        writeModule1( t,p );
        p.pln();p.pI();
        if ( t.isCustom() ) p.p( "custom " );
        p.p( "valuetype " + t.getIDLName() );
        writeInherits( inhHash,forValuetype,p );

        p.pln( " {" );
        if ( conVec.size() + datVec.size() + mthVec.size() > 0 ) {   //any content?
            p.pln();p.pI();
            for ( int i1 = 0; i1 < conVec.size(); i1++ )            //write constants
                writeConstant( (CompoundType.Member)conVec.elementAt( i1 ),p );
            for ( int i1 = 0; i1 < datVec.size(); i1++ ) {
                CompoundType.Member mem = (CompoundType.Member)datVec.elementAt( i1 );
                if ( mem.getType().isPrimitive() )
                    writeData( mem,p );                            //write primitive data
            }
            for ( int i1 = 0; i1 < datVec.size(); i1++ ) {
                CompoundType.Member mem = (CompoundType.Member)datVec.elementAt( i1 );
                if ( !mem.getType().isPrimitive() )
                    writeData( mem,p );                        //write non-primitive data
            }
            for ( int i1 = 0; i1 < mthVec.size(); i1++ )              //write methods
                writeMethod( (CompoundType.Method)mthVec.elementAt( i1 ),p );
            p.pO();p.pln();
        }
        p.pln( "};" );

        p.pO();p.pln();
        writeRepositoryID ( t,p );
            p.pln();
        writeModule2( t,p );
        writeEpilog( t,refHash,p );
        }


    /**
     * Write IDL prolog for a CompoundType.
     * @param t The CompoundType.
     * @param refHash Hashtable loaded with type references.
     * @param spcHash Hashtable loaded with special type references.
     * @param arrHash Hashtable loaded with array references.
     * @param excHash Hashtable loaded with exceptions thrown.
     * @param inhHash Hashtable loaded with inherited types.
     * @param p The output stream.
     */
    protected void writeProlog(
                               CompoundType t,
                               Hashtable refHash,
                               Hashtable spcHash,
                               Hashtable arrHash,
                               Hashtable excHash,
                               Hashtable inhHash,
                               IndentingWriter p )
        throws IOException {
        writeBanner( t,0,!isException,p );
        writeForwardReferences( refHash,p );
        writeIncludes( excHash,isThrown,p );      //#includes for exceptions thrown
        writeInheritedIncludes( inhHash,p );
        writeIncludes( spcHash,!isThrown,p );         //#includes for special types
        writeBoxedRMIIncludes( arrHash,p );
        writeIDLEntityIncludes( refHash,p );
        writeIncOrb( p );
        writeIfndef( t,0,!isException,!isForward,p );
    }


    /**
     * Write IDL epilog for a CompoundType.
     * @param t The CompoundType.
     * @param refHash Hashtable loaded with type references.
     * @param p The output stream.
     */
    protected void writeEpilog(
                               CompoundType t,
                               Hashtable refHash,
                               IndentingWriter p )
        throws IOException {
        writeIncludes( refHash,!isThrown,p );     //#includes for forward dcl types
        writeEndif( p );
    }



    /**
     * Write special typedef
     * @param t A special Type.
     * @param p The output stream.
     */
    protected void writeSpecial(
                                Type t,
                                IndentingWriter p )
        throws IOException {
        String spcName = t.getQualifiedName();
        if ( "java.io.Serializable".equals( spcName ) )
            writeJavaIoSerializable( t,p );
        else if ( "java.io.Externalizable".equals( spcName ) )
            writeJavaIoExternalizable( t,p );
        else if ( "java.lang.Object".equals( spcName) )
            writeJavaLangObject( t,p );
        else if ( "java.rmi.Remote".equals( spcName) )
            writeJavaRmiRemote( t,p );
        else if ( "org.omg.CORBA.portable.IDLEntity".equals( spcName) )
            writeIDLEntity( t,p );
    }



    /**
     * Write a hard-coded IDL typedef definition for the special case
     * java.io.Serializable.
     * @param t The current Type
     * @param p The output stream.
     */
    protected void writeJavaIoSerializable(
                                           Type t,
                                           IndentingWriter p )
        throws IOException {
        writeBanner( t,0,!isException,p );
        writeIfndef( t,0,!isException,!isForward,p );
        writeModule1( t,p );
        p.pln();p.pI();
        p.pln( "typedef any Serializable;" );
        p.pO();p.pln();
        writeModule2( t,p );
        writeEndif( p );
    }


    /**
     * Write a hard-coded IDL typedef definition for the special case
     * java.io.Externalizable.
     * @param t The current Type
     * @param p The output stream.
     */
    protected void writeJavaIoExternalizable(
                                             Type t,
                                             IndentingWriter p )
        throws IOException {
        writeBanner( t,0,!isException,p );
        writeIfndef( t,0,!isException,!isForward,p );
        writeModule1( t,p );
        p.pln();p.pI();
        p.pln( "typedef any Externalizable;" );
        p.pO();p.pln();
        writeModule2( t,p );
        writeEndif( p );
    }


    /**
     * Write a hard-coded IDL typedef definition for the special case
     * java.lang.Object.
     * @param t The current Type
     * @param p The output stream.
     */
    protected void writeJavaLangObject(
                                       Type t,
                                       IndentingWriter p )
        throws IOException {
        writeBanner( t,0,!isException,p );
        writeIfndef( t,0,!isException,!isForward,p );
        writeModule1( t,p );
        p.pln();p.pI();
        p.pln( "typedef any _Object;" );
        p.pO();p.pln();
        writeModule2( t,p );
        writeEndif( p );
    }


    /**
     * Write a hard-coded IDL typedef definition for the special case
     * java.rmi.Remote.
     * @param t The current Type
     * @param p The output stream.
     */
    protected void writeJavaRmiRemote(
                                      Type t,
                                      IndentingWriter p )
        throws IOException {
        writeBanner( t,0,!isException,p );
        writeIfndef( t,0,!isException,!isForward,p );
        writeModule1( t,p );
        p.pln();p.pI();
        p.pln( "typedef Object Remote;" );
        p.pO();p.pln();
        writeModule2( t,p );
        writeEndif( p );
    }



    /**
     * Write a hard-coded IDL typedef definition for the special case
     * org.omg.CORBA.portable.IDLEntity
     * @param t The current Type
     * @param p The output stream.
     */
    protected void writeIDLEntity(
                                  Type t,
                                  IndentingWriter p )
        throws IOException {
        writeBanner( t,0,!isException,p );
        writeIfndef( t,0,!isException,!isForward,p );
        writeModule1( t,p );
        p.pln();p.pI();
        p.pln( "typedef any IDLEntity;" );
        p.pO();p.pln();
        writeModule2( t,p );
        writeEndif( p );
    }


    /**
     * Filter and collect non-duplicate inherited interfaces for a type
     * @param ct The current CompoundType
     * @param inhHash Hashtable containing the inherited interfaces
     */
    protected void getInterfaces(
                                 CompoundType ct,
                                 Hashtable inhHash ) {
        InterfaceType[] infs = ct.getInterfaces();
                                 nextInterface:
        for ( int i1 = 0; i1 < infs.length; i1++ ) {  //forall inherited interfaces
            String inhName = infs[i1].getQualifiedName();
            switch ( ct.getTypeCode() ) {
            case TYPE_NC_CLASS:
            case TYPE_VALUE:                                   //filter for classes
                if ( "java.io.Externalizable".equals( inhName ) ||
                     "java.io.Serializable".equals( inhName ) ||
                     "org.omg.CORBA.portable.IDLEntity".equals( inhName ) )
                    continue nextInterface;
                break;
            default:                                        //filter for all others
                if ( "java.rmi.Remote".equals( inhName ) )
                    continue nextInterface;
                break;
            }
            inhHash.put( inhName,infs[i1] );                           //add this one
        }
    }


    /**
     * Filter and add base class inheritance for a class type
     * @param ct The current CompoundType
     * @param inhHash Hashtable containing inherited types
     */
    protected void getInheritance(
                                  CompoundType ct,
                                  Hashtable inhHash ) {
        ClassType par = ct.getSuperclass();                            //get parent
        if ( par == null ) return;
        String parName = par.getQualifiedName();
        switch ( ct.getTypeCode() ) {
        case TYPE_NC_CLASS:
        case TYPE_VALUE:
            if ( "java.lang.Object".equals( parName ) )          //this is implicit
                return;
            break;
        default: return;                                     //ignore other types
        }
        inhHash.put( parName,par );                          //add valid base class
    }


    /**
     * Collect and filter type and array references from methods
     * @param mthVec Given Vector of methods
     * @param refHash Hashtable for type references
     * @param spcHash Hashtable for special type references
     * @param arrHash Hashtable for array references
     * @param excHash Hashtable for exceptions thrown
     */
    protected void getMethodReferences(
                                       Vector mthVec,
                                       Hashtable refHash,
                                       Hashtable spcHash,
                                       Hashtable arrHash,
                                       Hashtable excHash ) {
        for ( int i1 = 0; i1 < mthVec.size(); i1++ ) {             //forall methods
            CompoundType.Method mth = (CompoundType.Method)mthVec.elementAt( i1 );
            Type[] args = mth.getArguments();
            Type ret = mth.getReturnType();
            getExceptions( mth,excHash );                 //collect exceptions thrown
            for ( int i2 = 0; i2 < args.length; i2++ )             //forall arguments
                addReference( args[i2],refHash,spcHash,arrHash );
            addReference( ret,refHash,spcHash,arrHash );
        }
    }


    /**
     * Collect and filter type and array references from data members
     * @param datVec Given Vector of data members
     * @param refHash Hashtable for type references
     * @param spcHash Hashtable for special type references
     * @param arrHash Hashtable for array references
     */
    protected void getMemberReferences(
                                       Vector datVec,
                                       Hashtable refHash,
                                       Hashtable spcHash,
                                       Hashtable arrHash ) {
        for ( int i1 = 0; i1 < datVec.size(); i1++ ) {         //forall datamembers
            CompoundType.Member mem = (CompoundType.Member)datVec.elementAt( i1 );
            Type dat = mem.getType();
            addReference( dat,refHash,spcHash,arrHash );
        }
    }


    /**
     * Add reference for given type avoiding duplication.
     * Sort into specials, arrays and regular references.
     * Filter out types which are not required.
     * @param t Given Type
     * @param refHash Hashtable for type references
     * @param spcHash Hashtable for special type references
     * @param arrHash Hashtable for array references
     */
    protected void addReference(
                                Type ref,
                                Hashtable refHash,
                                Hashtable spcHash,
                                Hashtable arrHash ) {
        String rName = ref.getQualifiedName();
        switch ( ref.getTypeCode() ) {
        case TYPE_ABSTRACT:
        case TYPE_REMOTE:
        case TYPE_NC_CLASS:
        case TYPE_NC_INTERFACE:
        case TYPE_VALUE:
            refHash.put( rName,ref );
            return;
        case TYPE_CORBA_OBJECT:
            if ( "org.omg.CORBA.Object".equals( rName ) ) return;      //don't want
            refHash.put( rName,ref );
            return;
        case TYPE_ARRAY:                                                 //array?
            arrHash.put( rName + ref.getArrayDimension(),ref );
            return;
        default:
            if ( isSpecialReference( ref ) )                 //special IDL typedef?
                spcHash.put( rName,ref );
        }
    }



    /**
     * Determine whether given Type is a special reference.
     * Special cases are: java.io.Serializable, java.io.Externalizable,
     * java.lang.Object, java.rmi.Remote and org.omg.CORBA.portable.IDLEntity
     * They are special because they have a hard-coded typedef defined in the
     * spec.
     * @param ref A referenced Type
     * @return boolean indicating whether it's a special reference
     */
    protected boolean isSpecialReference(
                                         Type ref ) {
        String rName = ref.getQualifiedName();
        if ( "java.io.Serializable".equals( rName ) ) return true;
        if ( "java.io.Externalizable".equals( rName ) ) return true;
        if ( "java.lang.Object".equals( rName) ) return true;
        if ( "java.rmi.Remote".equals( rName) ) return true;
        if ( "org.omg.CORBA.portable.IDLEntity".equals( rName) ) return true;
        return false;
    }


    /**
     * Collect and filter thrown exceptions for a given pre-filtered method.
     * Keep only 'checked' exception classes minus java.rmi.RemoteException
     * and its subclasses
     * @param method The current method
     * @param excHash Hashtable containing non-duplicate thrown exceptions
     */
    protected void getExceptions(
                                      CompoundType.Method mth,
                                      Hashtable excHash ) {
        ClassType[] excs = mth.getExceptions();
        for ( int i1 = 0; i1 < excs.length; i1++ ) {            //forall exceptions
            ClassType exc = excs[i1];
            if ( exc.isCheckedException() &&
                 !exc.isRemoteExceptionOrSubclass() ) {
                excHash.put( exc.getQualifiedName(),exc );
        }
    }
    }


    /**
     * Collect and filter methods for a type.
     * Remove any private or inherited methods.
     * @param ct The current CompoundType
     * @return Vector containing the methods
     */
    protected Vector getMethods(
                                CompoundType ct ) {
        Vector vec = new Vector();
        int ctType = ct.getTypeCode();
        switch ( ctType ) {
        case TYPE_ABSTRACT:
        case TYPE_REMOTE:       break;
        case TYPE_NC_CLASS:
        case TYPE_NC_INTERFACE:
        case TYPE_VALUE:        if ( valueMethods ) break;
        default: return vec;
        }
        Identifier ctId = ct.getIdentifier();
        CompoundType.Method[] mths = ct.getMethods();
                                nextMethod:
        for ( int i1 = 0; i1 < mths.length; i1++ ) {               //forall methods
            if ( mths[i1].isPrivate() ||                            //private method?
                 mths[i1].isInherited() )                         //inherited method?
                continue nextMethod;                                   //yes..ignore it
            if ( ctType == TYPE_VALUE ) {
                String mthName = mths[i1].getName();
                if ( "readObject"  .equals( mthName ) ||
                     "writeObject" .equals( mthName ) ||
                     "readExternal".equals( mthName ) ||
                     "writeExternal".equals( mthName ) )
                    continue nextMethod;                                //ignore this one
            }
            if ( ( ctType == TYPE_NC_CLASS ||
                   ctType == TYPE_NC_INTERFACE ) &&
                 mths[i1].isConstructor() )   //init not valid for abstract valuetype
                continue nextMethod;                                  //ignore this one
            vec.addElement( mths[i1] );                                //add this one
        }
        return vec;
    }


    /**
     * Collect constants for a type.
     * A valid constant is a "public final static" field with a compile-time
     * constant value for a primitive type or String
     * @param ct The current CompoundType
     * @return Vector containing the constants
     */
    protected Vector getConstants(
                                  CompoundType ct ) {
        Vector vec = new Vector();
        CompoundType.Member[] mems = ct.getMembers();
        for ( int i1 = 0; i1 < mems.length; i1++ ) {               //forall members
            Type   memType  = mems[i1].getType();
            String memValue = mems[i1].getValue();
            if ( mems[i1].isPublic() &&
                 mems[i1].isFinal()  &&
                 mems[i1].isStatic() &&
                 ( memType.isPrimitive() || "String".equals( memType.getName() ) ) &&
                 memValue != null )
                vec.addElement( mems[i1] );                              //add this one
        }
        return vec;
    }


    /**
     * Collect and sort data fields for a ValueType.
     * Sort in Java (not IDL) Unicode name string lexicographic increasing
     * order.
     * Non-static, non-transient fields are mapped.
     * If the type is a custom valuetype, only public fields are mapped.
     * @param ct The current CompoundType
     * @return Vector containing the data fields
     */
    protected Vector getData(
                             CompoundType t ) {
        Vector vec = new Vector();
        if ( t.getTypeCode() != TYPE_VALUE ) return vec;
        ValueType vt = (ValueType)t;
        CompoundType.Member[] mems = vt.getMembers();
        boolean notCust = !vt.isCustom();
        for ( int i1 = 0; i1 < mems.length; i1++ ) {               //forall members
            if ( !mems[i1].isStatic()    &&
                 !mems[i1].isTransient() &&
                 (  mems[i1].isPublic() || notCust ) ) {
                int i2;
                String memName = mems[i1].getName();
                for ( i2 = 0; i2 < vec.size(); i2++ ) {      //insert in java lex order
                    CompoundType.Member aMem = (CompoundType.Member)vec.elementAt( i2 );
                    if ( memName.compareTo( aMem.getName() ) < 0 ) break;
                }
                vec.insertElementAt( mems[i1],i2 );                   //insert this one
            }
        }
        return vec;
    }


    /**
     * Write forward references for referenced interfaces and valuetypes
     * ...but not if the reference is to a boxed IDLEntity,
     * @param refHash Hashtable loaded with referenced types
     * @param p The output stream.
     */
    protected void writeForwardReferences(
                                          Hashtable refHash,
                                          IndentingWriter p )
        throws IOException {
        Enumeration refEnum = refHash.elements();
        nextReference:
        while ( refEnum.hasMoreElements() ) {
            Type t = (Type)refEnum.nextElement();
            if ( t.isCompound() ) {
                CompoundType ct = (CompoundType)t;
                if ( ct.isIDLEntity() )
                    continue nextReference;                  //ignore IDLEntity reference
            }
            writeForwardReference( t,p );
        }
    }


    /**
     * Write forward reference for given type
     * @param t Given type
     * @param p The output stream.
     */
    protected void writeForwardReference(
                                         Type t,
                                         IndentingWriter p )
        throws IOException {
        String qName = t.getQualifiedName();
        if ( "java.lang.String".equals( qName ) ) ;
        else if ( "org.omg.CORBA.Object".equals( qName ) ) return ;    //no fwd dcl

        writeIfndef( t,0,!isException,isForward,p );
            writeModule1( t,p );
            p.pln();p.pI();
            switch ( t.getTypeCode() ) {
        case TYPE_NC_CLASS:
            case TYPE_NC_INTERFACE: p.p( "abstract valuetype " ); break;
            case TYPE_ABSTRACT:     p.p( "abstract interface " ); break;
            case TYPE_VALUE:        p.p( "valuetype " ); break;
        case TYPE_REMOTE:
        case TYPE_CORBA_OBJECT: p.p( "interface " ); break;
            default: ;                              //all other types were filtered
            }
            p.pln( t.getIDLName() + ";" );
            p.pO();p.pln();
            writeModule2( t,p );
        writeEndif( p );
        }


    /**
     * Write forward reference for boxed valuetype for single dimension of IDL
     * sequence.
     * If the dimension is <1 and the element is a CompoundType, write a
     * forward declare for the element
     * @param at ArrayType for forward declare
     * @param dim The dimension to write
     * @param p The output stream.
     */
    protected void writeForwardReference(
                                         ArrayType at,
                                         int dim,
                                         IndentingWriter p)
        throws IOException {
        Type et = at.getElementType();
        if ( dim < 1 ) {
            if ( et.isCompound() ) {
                CompoundType ct = (CompoundType)et;
                writeForwardReference( et,p);
    }
            return;
        }
        String fName = unEsc( et.getIDLName() ).replace( ' ','_' );

        writeIfndef( at,dim,!isException,isForward,p );
        writeModule1( at,p );
        p.pln();p.pI();
        switch ( et.getTypeCode() ) {
        case TYPE_NC_CLASS:
        case TYPE_NC_INTERFACE: p.p( "abstract valuetype " ); break;
        case TYPE_ABSTRACT:     p.p( "abstract interface " ); break;
        case TYPE_VALUE:        p.p( "valuetype " ); break;
        case TYPE_REMOTE:
        case TYPE_CORBA_OBJECT: p.p( "interface " ); break;
        default: ;                              //all other types were filtered
        }
        p.pln( "seq" + dim + "_" + fName + ";" );
        p.pO();p.pln();
        writeModule2( at,p );
        writeEndif( p );
    }


    /**
     * Write #includes for boxed IDLEntity references.
     * @param refHash Hashtable loaded with referenced types
     * @param p The output stream.
     */
    protected void writeIDLEntityIncludes(
                                          Hashtable refHash,
                                          IndentingWriter p )
        throws IOException {
        Enumeration refEnum = refHash.elements();
        while ( refEnum.hasMoreElements() ) {
            Type t = (Type)refEnum.nextElement();
            if ( t.isCompound() ) {
                CompoundType ct = (CompoundType)t;
                if ( ct.isIDLEntity() ) {                          //select IDLEntities
                    writeInclude( ct,0,!isThrown,p );
                    refHash.remove( ct.getQualifiedName() );     //avoid another #include
                }
            }
        }
    }


    /**
     * Write #includes
     * @param incHash Hashtable loaded with Types to include
     * @param isThrown true if Types are thrown exceptions
     * @param p The output stream.
     */
    protected void writeIncludes(
                                 Hashtable incHash,
                                 boolean isThrown,
                                 IndentingWriter p )
        throws IOException {
        Enumeration incEnum = incHash.elements();
        while ( incEnum.hasMoreElements() ) {
            CompoundType t = (CompoundType)incEnum.nextElement();
            writeInclude( t,0,isThrown,p );
            }
    }


    /**
     * Write includes for boxedRMI valuetypes for IDL sequences.
     * Write only the maximum dimension found for an ArrayType.
     * @param arrHash Hashtable loaded with array types
     * @param p The output stream.
     */
    protected void writeBoxedRMIIncludes(
                                         Hashtable arrHash,
                                         IndentingWriter p)
        throws IOException {
        Enumeration e1 = arrHash.elements();
        nextSequence:
        while ( e1.hasMoreElements() ) {
            ArrayType at = (ArrayType)e1.nextElement();
            int dim = at.getArrayDimension();
            Type et = at.getElementType();

            Enumeration e2 = arrHash.elements();
            while ( e2.hasMoreElements() ) {                   //eliminate duplicates
                ArrayType at2 = (ArrayType)e2.nextElement();
                if ( et == at2.getElementType() &&                //same element type &
                     dim < at2.getArrayDimension() )               //smaller dimension?
                    continue nextSequence;                              //ignore this one
        }
            writeInclude( at,dim,!isThrown,p );
    }
    }


    /**
     * Write #includes
     * @param incHash Hashtable loaded with Types to include
     * @param p The output stream.
     */
    protected void writeInheritedIncludes(
                                          Hashtable inhHash,
                                 IndentingWriter p )
        throws IOException {
        Enumeration inhEnum = inhHash.elements();
        while ( inhEnum.hasMoreElements() ) {
            CompoundType t = (CompoundType)inhEnum.nextElement();
            writeInclude( t,0,!isThrown,p );
        }
    }


    /**
     * Write a #include.
     * @param t Type to include
     * @param dim The dimension to write if t is an array.
     * @param isThrown boolean indicating if include is for thrown exception.
     * @param p The output stream.
     */
    protected void writeInclude(
                                Type t,
                                int dim,
                                boolean isThrown,
                                  IndentingWriter p)
        throws IOException {
        CompoundType ct;
        String tName;
        String[] modNames;
        if ( t.isCompound() ) {
            ct = (CompoundType)t;
            String qName = ct.getQualifiedName();
            if ( "java.lang.String".equals( qName ) ) {
                writeIncOrb( p );                         //#include orb.idl for String
                return;
            }
            if ( "org.omg.CORBA.Object".equals( qName ) )
                return;                                 //Object treated like primitive
            modNames = getIDLModuleNames( ct );                   //module name array
            tName = unEsc( ct.getIDLName() );                     //file name default

            if ( ct.isException() )
                if ( ct.isIDLEntityException() )
                    if ( ct.isCORBAUserException() )
                        if ( isThrown ) tName = unEsc( ct.getIDLExceptionName() );
                        else ;
                    else tName = ct.getName();                    //use original IDL name
                else if ( isThrown )
                    tName = unEsc( ct.getIDLExceptionName() );
            }
        else if ( t.isArray() ) {
            Type et = t.getElementType();                    //file name for sequence
            if ( dim > 0 ) {
                modNames = getIDLModuleNames( t );                  //module name array
                tName = "seq" + dim + "_" + unEsc( et.getIDLName().replace( ' ','_' ) );
            }
            else{                                                  //#include element
                if ( !et.isCompound() ) return;       //no include needed for primitive
                ct = (CompoundType) et;
                modNames = getIDLModuleNames( ct );           //no boxedRMI for element
                tName = unEsc( ct.getIDLName() );
                writeInclude( ct,modNames,tName,p );
                return;
            }
        }
        else return;                              //no include needed for primitive
        writeInclude( t,modNames,tName,p );
    }


    /**
     * Write a #include doing user specified -idlFile translation (if any) for
     * IDLEntities.
     * @param t Type to include.
     * @param modNames Preprocessed module names (default).
     * @param tName Preprocessed Type name (default).
     * @param p The output stream.
     */
    protected void writeInclude(
                                Type t,
                                String[] modNames,
                                String tName,
                                IndentingWriter p)
        throws IOException {
        if ( t.isCompound() ) {
            CompoundType it = (CompoundType)t;

            if ( ifHash.size() > 0 &&             //any -idlFile translation to apply
                 it.isIDLEntity() ) {                         //..for this IDLEntity?
                String qName = t.getQualifiedName();   //fully qualified orig Java name

                Enumeration k = ifHash.keys();
                while ( k.hasMoreElements() ) {      //loop thro user-defined -idlFiles
                    String from = (String)k.nextElement();
                    if ( qName.startsWith( from ) ) {                    //found a match?
                        String to = (String)ifHash.get( from );
                        p.pln( "#include \"" + to + "\"" );   //user-specified idl filename
                        return;                                   //don't look for any more
                    }
                }
            }
        }
        else if ( t.isArray() ) ;        //no -idlFile translation needed for array
        else return;                             //no #include needed for primitive

        p.p( "#include \"" );                    //no -idlFile translation required
        for ( int i1 = 0; i1 < modNames.length; i1++ ) p.p( modNames[i1] + "/" );
        p.p( tName + ".idl\"" );
        p.pln();
    }


    /**
     * Return the fully qualified Java Name for a Type.
     * IDLEntity preprocessing done by getIDLModuleNames(t)
     * @param t Given Type
     * @return Array containing the original module nesting.
     */
    protected String getQualifiedName(
                                      Type t ) {
        String[] modNames = getIDLModuleNames( t );
        int len = modNames.length;
        StringBuffer buf = new StringBuffer();
        for ( int i1 = 0; i1 < len; i1++ )
            buf.append( modNames[i1] + "." );
        buf.append( t.getIDLName() );
        return buf.toString();
    }


    /**
     * Return the global fully qualified IDL Name for a Type.
     * IDLEntity preprocessing done by getIDLModuleNames(t)
     * @param t Given Type
     * @return Array containing the original module nesting.
     */
    protected String getQualifiedIDLName(Type t) {
        if ( t.isPrimitive() )
            return t.getIDLName();
        if ( !t.isArray() &&
             "org.omg.CORBA.Object".equals( t.getQualifiedName() ) )
            return t.getIDLName();

        String[] modNames = getIDLModuleNames( t );
        int len = modNames.length;
        if (len > 0) {
            StringBuffer buf = new StringBuffer();
            for ( int i1 = 0; i1 < len; i1++ )
                buf.append( IDL_NAME_SEPARATOR + modNames[i1] );
            buf.append( IDL_NAME_SEPARATOR + t.getIDLName() );
            return buf.toString();
        } else {
            return t.getIDLName();
        }
    }


    /**
     * Return the IDL module nesting of the given Type.
     * For IDLEntity CompoundTypes (or their arrays) apply any user specified
     * -idlModule translation or, if none applicable, strip any package
     * prefix.
     * Add boxedIDL or boxedRMI modules if required.
     * @param t Given Type
     * @return Array containing the original module nesting.
     */
    protected String[] getIDLModuleNames(Type t) {
        String[] modNames = t.getIDLModuleNames();      //default module name array
        CompoundType ct;
        if ( t.isCompound() ) {
            ct = (CompoundType)t;
            if ( !ct.isIDLEntity ) return modNames;     //normal (non-IDLEntity) case
            if ( "org.omg.CORBA.portable.IDLEntity"
                 .equals( t.getQualifiedName() ) )
                return modNames;
        }
        else if ( t.isArray() ) {
            Type et = t.getElementType();
            if ( et.isCompound() ) {
                ct = (CompoundType)et;
                if ( !ct.isIDLEntity ) return modNames;   //normal (non-IDLEntity) case
                if ( "org.omg.CORBA.portable.IDLEntity"
                     .equals( t.getQualifiedName() ) )
                    return modNames;
            }
            else return modNames;
        }
        else return modNames;              //no preprocessing needed for primitives

        //it's an IDLEntity or an array of...
        Vector mVec = new Vector();
        if ( !translateJavaPackage( ct,mVec ) )      //apply -idlModule translation
            stripJavaPackage( ct,mVec );             //..or strip prefixes (not both)

        if ( ct.isBoxed() ) {                            //add boxedIDL if required
            mVec.insertElementAt( "org",0 );
            mVec.insertElementAt( "omg",1 );
            mVec.insertElementAt( "boxedIDL",2 );
        }
        if ( t.isArray() ) {                             //add boxedRMI if required
            mVec.insertElementAt( "org",0 );
            mVec.insertElementAt( "omg",1 );
            mVec.insertElementAt( "boxedRMI",2 );
        }
        String[] outArr = new String[mVec.size()];
        mVec.copyInto( outArr );
        return outArr;
    }


    /**
     * Apply user specified -idlModule translation to package names of given
     * IDLEntity ct. Example:
     *   -idlModule foo.bar real::mod::nesting
     * @param ct CompoundType containing given IDLEntity.
     * @param vec Returned Vector of translated IDL module names.
     * @return boolean true if any translation was done.
     */
    protected boolean translateJavaPackage(
                                           CompoundType ct,
                                           Vector vec ) {
        vec.removeAllElements();
        boolean ret = false;
        String fc = null;
        if ( ! ct.isIDLEntity() ) return ret;

        String pName = ct.getPackageName();         //start from Java package names
        if ( pName == null ) return ret;
        StringTokenizer pt = new StringTokenizer( pName,"." );
        while ( pt.hasMoreTokens() ) vec.addElement( pt.nextToken() );

        if ( imHash.size() > 0 ) {           //any -idlModule translation to apply?
            Enumeration k = imHash.keys();

        nextModule:
            while ( k.hasMoreElements() ) {      //loop thro user-defined -idlModules
                String from = (String)k.nextElement();                  //from String..
                StringTokenizer ft = new StringTokenizer( from,"." );
                int vecLen = vec.size();
                int ifr;
                for ( ifr = 0; ifr < vecLen && ft.hasMoreTokens(); ifr++ )
                    if ( ! vec.elementAt(ifr).equals( ft.nextToken() ) )
                        continue nextModule;                                  //..no match

                if ( ft.hasMoreTokens() ) {                          //matched so far..
                    fc = ft.nextToken();                         //a 'from' token remains
                    if ( ! ct.getName().equals( fc ) ||             //matches class name?
                         ft.hasMoreTokens() )
                        continue nextModule;                                   //..no match
                }

                ret = true;                                             //found a match
                for ( int i4 = 0; i4 < ifr; i4++ )
                    vec.removeElementAt( 0 );                     //remove 'from' package

                String to = (String)imHash.get( from );                   //..to String
                StringTokenizer tt = new StringTokenizer( to,IDL_NAME_SEPARATOR );

                int itoco = tt.countTokens();
                int ito = 0;
                if ( fc != null ) itoco--;               //user may have given IDL type
                for ( ito = 0; ito < itoco; ito++ )
                    vec.insertElementAt( tt.nextToken(),ito );      //insert 'to' modules
                if ( fc != null ) {
                    String tc = tt.nextToken();
                    if ( ! ct.getName().equals( tc ) )           //not the IDL type, so..
                        vec.insertElementAt( tc,ito );           //insert final 'to' module
                }
            }
        }
        return ret;
    }


    /**
     * Strip Java #pragma prefix and/or -pkgPrefix prefix package names from
     * given IDLEntity ct.
     * Strip any package prefix which may have been added by comparing with
     * repository id. For example in Java package fake.omega:
     *   repid = IDL:phoney.pfix/omega/Juliet:1.0 gives { "omega" }
     * @param ct CompoundType containing given IDLEntity.
     * @param vec Returned Vector of stripped IDL module names.
     */
    protected void stripJavaPackage(
                                    CompoundType ct,
                                    Vector vec ) {
        vec.removeAllElements();
        if ( ! ct.isIDLEntity() ) return;

        String repID = ct.getRepositoryID().substring( 4 );
        StringTokenizer rept = new StringTokenizer( repID,"/" );
        if ( rept.countTokens() < 2 ) return;

        while ( rept.hasMoreTokens() )
            vec.addElement( rept.nextToken() );
        vec.removeElementAt( vec.size() - 1 );

        String pName = ct.getPackageName();         //start from Java package names
        if ( pName == null ) return;
        Vector pVec = new Vector();
        StringTokenizer pt = new StringTokenizer( pName,"." );
        while ( pt.hasMoreTokens() ) pVec.addElement( pt.nextToken() );

        int i1 = vec.size() - 1;
        int i2 = pVec.size() - 1;
        while ( i1 >= 0 && i2 >= 0 ) {                      //go R->L till mismatch
            String rep = (String)( vec.elementAt( i1 ) );
            String pkg = (String)( pVec.elementAt( i2 ) );
            if ( ! pkg.equals( rep ) ) break;
            i1--; i2--;
        }
        for ( int i3 = 0; i3 <= i1; i3++ )
            vec.removeElementAt( 0 );                                  //strip prefix
    }



    /**
     * Write boxedRMI valuetype for a single dimension of an IDL sequence
     * indicated by the given OutputType.
     * The filename for the OutputType is of the form "seqn_elemName" where n
     * is the dimension required.
     * @param ot Given OutputType.
     * @param p The output stream.
     */
    protected void writeSequence(
                                 OutputType ot,
                                 IndentingWriter p)
        throws IOException {
        ArrayType at = (ArrayType)ot.getType();
        Type et = at.getElementType();
        String fName = ot.getName();
        int dim = Integer.parseInt( fName.substring( 3,fName.indexOf( "_" ) ) );
        String idlName = unEsc( et.getIDLName() ).replace( ' ','_' );
        String qIdlName = getQualifiedIDLName( et );
        String qName = et.getQualifiedName();

        String repID = at.getRepositoryID();
        int rix1 = repID.indexOf( '[' );                       //edit repository id
        int rix2 = repID.lastIndexOf( '[' ) + 1;
            StringBuffer rid = new StringBuffer(
                                            repID.substring( 0,rix1 ) +
                                            repID.substring( rix2 ) );
        for ( int i1 = 0; i1 < dim; i1++ ) rid.insert( rix1,'[' );

        String vtName = "seq" + dim + "_" + idlName;
        boolean isFromIDL = false;
        if ( et.isCompound() ) {
            CompoundType ct = (CompoundType)et;
            isFromIDL = ct.isIDLEntity() || ct.isCORBAObject();
        }
        boolean isForwardInclude =
            et.isCompound() &&
            !isSpecialReference( et ) &&
            dim == 1 &&
            !isFromIDL &&
            !"org.omg.CORBA.Object".equals(qName) &&
            !"java.lang.String".equals(qName);

        writeBanner( at,dim,!isException,p );
        if ( dim == 1 && "java.lang.String".equals(qName) )          //special case
            writeIncOrb( p );
        if ( dim == 1 && "org.omg.CORBA.Object".equals(qName) ) ;
        else if ( isSpecialReference( et ) || dim > 1 || isFromIDL )
            writeInclude( at,dim-1,!isThrown,p );               //"trivial" include
        writeIfndef( at,dim,!isException,!isForward,p );
        if ( isForwardInclude )
            writeForwardReference( at,dim-1,p );                    //forward declare
        writeModule1( at,p );
                p.pln();p.pI();
                p.p( "valuetype " + vtName );
                p.p( " sequence<" );
        if ( dim == 1 ) p.p( qIdlName );
                else {
            p.p( "seq" + ( dim - 1 ) + "_"  );
                    p.p( idlName );
                }
                p.pln( ">;" );
                p.pO();p.pln();
                p.pln( "#pragma ID " + vtName + " \"" + rid + "\"" );
                p.pln();
        writeModule2( at,p );
        if ( isForwardInclude )
            writeInclude( at,dim-1,!isThrown,p );      //#include for forward declare
                writeEndif( p );
            }


    /**
     * Write valuetype for a boxed IDLEntity.
     * @param t Given CompoundType representing the IDLEntity.
     * @param p The output stream.
     */
    protected void writeBoxedIDL(
                                 CompoundType t,
                                 IndentingWriter p)
        throws IOException {
        String[] boxNames = getIDLModuleNames( t );
        int len = boxNames.length;
        String[] modNames = new String[len - 3];               //remove box modules
        for ( int i1 = 0; i1 < len - 3; i1++ ) modNames[i1] = boxNames[i1 + 3];
        String tName = unEsc( t.getIDLName() );

        writeBanner( t,0,!isException,p );
        writeInclude( t,modNames,tName,p );
        writeIfndef( t,0,!isException,!isForward,p );
        writeModule1( t,p );
        p.pln();p.pI();

        p.p( "valuetype " + tName + " " );
        for ( int i1 = 0; i1 < modNames.length; i1++ )
            p.p( IDL_NAME_SEPARATOR + modNames[i1] );
        p.pln( IDL_NAME_SEPARATOR + tName + ";" );

        p.pO();p.pln();
        writeRepositoryID( t,p );
        p.pln();
        writeModule2( t,p );
        writeEndif( p );
        }


    /**
     * Write an exception.
     * @param t Given ClassType representing the exception.
     * @param p The output stream.
     */
    protected void writeException(
                                  ClassType t,
                                  IndentingWriter p)
        throws IOException {
        writeBanner( t,0,isException,p );
        writeIfndef( t,0,isException,!isForward,p );
        writeForwardReference( t,p );
        writeModule1( t,p );
        p.pln();p.pI();

        p.pln( "exception " + t.getIDLExceptionName() + " {" );
        p.pln();p.pI();
        p.pln( t.getIDLName() + " value;" );
        p.pO();p.pln();
        p.pln( "};" );

        p.pO();p.pln();
        writeModule2( t,p );
        writeInclude( t,0,!isThrown,p );               //include valuetype idl file
        writeEndif( p );
    }


    /**
     * Write #pragma to identify the repository ID of the given type
     * @param t The given Type.
     * @param p The output stream.
     */
    protected void writeRepositoryID(
                                     Type t,
                                     IndentingWriter p )
        throws IOException {
        String repid = t.getRepositoryID();
        if ( t.isCompound() ) {
            CompoundType ct = (CompoundType)t;
            if ( ct.isBoxed() )
                repid = ct.getBoxedRepositoryID();
        }

        p.pln( "#pragma ID " + t.getIDLName() + " \"" +
               repid + "\"" );
    }

    /**
     * Write inheritance for an IDL interface or valuetype. Any class
     * inheritance precedes any interface inheritance.
     * For a valutype any inheritance from abstract interfaces then
     * follows the "supports" keyword.
     * @param inhHash Hashtable loaded with inherited Types
     * @param forValuetype true if writing inheritance for a valuetype
     * @param p The output stream.
     */
    protected void writeInherits(
                                 Hashtable inhHash,
                                 boolean forValuetype,
                                 IndentingWriter p )
        throws IOException {
        int itot = inhHash.size();
        int iinh = 0;
        int isup = 0;
        if ( itot < 1 ) return;                         //any inheritance to write?
        Enumeration inhEnum = inhHash.elements();
        CompoundType ct;
        if ( forValuetype )
            while ( inhEnum.hasMoreElements() ) {
                ct = (CompoundType)inhEnum.nextElement();
                if ( ct.getTypeCode() == TYPE_ABSTRACT ) isup++;
            }
        iinh = itot - isup;

        if ( iinh > 0 ) {
        p.p( ": " );
            inhEnum = inhHash.elements();
        while ( inhEnum.hasMoreElements() ) {         //write any class inheritance
                ct = (CompoundType)inhEnum.nextElement();
                if ( ct.isClass() ) {
                    p.p( getQualifiedIDLName( ct ) );
                    if ( iinh > 1 ) p.p( ", " );               //delimit them with commas
                    else if ( itot > 1 ) p.p( " " );
                break;                                                //only one parent
            }
        }
            int i = 0;
        inhEnum = inhHash.elements();
        while ( inhEnum.hasMoreElements() ) {     //write any interface inheritance
                ct = (CompoundType)inhEnum.nextElement();
                if ( !ct.isClass() &&
                     !( ct.getTypeCode() == TYPE_ABSTRACT ) ) {
                    if ( i++ > 0 ) p.p( ", " );                     //delimit with commas
                    p.p( getQualifiedIDLName( ct ) );
            }
        }
    }
        if ( isup > 0 ) {                    //write abstract interface inheritance
            p.p( " supports " );
            int i = 0;
            inhEnum = inhHash.elements();
            while ( inhEnum.hasMoreElements() ) {
                ct = (CompoundType)inhEnum.nextElement();
                if ( ct.getTypeCode() == TYPE_ABSTRACT ) {
                    if ( i++ > 0 ) p.p( ", " );                     //delimit with commas
                    p.p( getQualifiedIDLName( ct ) );
                }
            }
        }
    }


    /**
     * Write an IDL constant
     * @param constant The current CompoundType.Member constant
     * @param p The output stream.
     */
    protected void writeConstant(
                                 CompoundType.Member constant,
                                 IndentingWriter p )
        throws IOException {
        Type t = constant.getType();
        p.p( "const " );
        p.p( getQualifiedIDLName( t ) );
        p.p( " " + constant.getIDLName() + " = " + constant.getValue() );
        p.pln( ";" );
    }



    /**
     * Write an IDL data member
     * @param data The current CompoundType.Member data member
     * @param p The output stream.
     */
    protected void writeData(
                             CompoundType.Member data,
                             IndentingWriter p )
        throws IOException {
        if ( data.isInnerClassDeclaration() ) return;                      //ignore
        Type t = data.getType();
        if ( data.isPublic() )
            p.p( "public " );
        else p.p( "private " );
        p.pln( getQualifiedIDLName( t ) +  " " +
               data.getIDLName() + ";" );
    }



    /**
     * Write an IDL Attribute
     * @param attr The current CompoundType.Method attribute
     * @param p The output stream.
     */
    protected void writeAttribute(
                                  CompoundType.Method attr,
                                  IndentingWriter p )
        throws IOException {
        if ( attr.getAttributeKind() == ATTRIBUTE_SET ) return;  //use getters only
        Type t = attr.getReturnType();
        if ( !attr.isReadWriteAttribute() ) p.p( "readonly " );
        p.p( "attribute " + getQualifiedIDLName( t ) + " " );
        p.pln( attr.getAttributeName() + ";" );
    }



    /**
     * Write an IDL method
     * @param method The current CompoundType.Method
     * @param p The output stream.
     */
    protected void writeMethod(
                               CompoundType.Method method,
                               IndentingWriter p )
        throws IOException {
        if ( method.isAttribute() ) {
            writeAttribute( method,p );
            return;
        }
        Type[]    pts = method.getArguments();
        String[]  paramNames = method.getArgumentNames();
        Type      rt = method.getReturnType();
        Hashtable excHash = new Hashtable();
        getExceptions( method,excHash );

        if ( method.isConstructor() )
            if ( factory ) p.p( "factory " + method.getIDLName() + "(" );
            else p.p( "init(" );                                    //IDL initializer
        else {
            p.p( getQualifiedIDLName( rt ) );
            p.p( " " + method.getIDLName() + "(" );
        }
        p.pI();

        for ( int i=0; i < pts.length; i++ ) {
            if ( i > 0 ) p.pln( "," );               //delimit with comma and newline
            else p.pln();
            p.p( "in " );
            p.p( getQualifiedIDLName( pts[i] ) );
            p.p( " " + paramNames[i] );
        }
        p.pO();
        p.p( " )" );

        if ( excHash.size() > 0 ) {                      //any exceptions to write?
            p.pln( " raises (" );
            p.pI();
            int i = 0;
            Enumeration excEnum = excHash.elements();
            while ( excEnum.hasMoreElements() ) {
                ValueType exc = (ValueType)excEnum.nextElement();
                if ( i > 0 ) p.pln( "," );                   //delimit them with commas
                if ( exc.isIDLEntityException() )
                    if ( exc.isCORBAUserException() )
                        p.p( "::org::omg::CORBA::UserEx" );
                    else {
                        String[] modNames = getIDLModuleNames( exc );
                        for ( int i2 = 0; i2 < modNames.length; i2++ )
                            p.p( IDL_NAME_SEPARATOR + modNames[i2] );
                        p.p( IDL_NAME_SEPARATOR + exc.getName() );
                    }
                else p.p( exc.getQualifiedIDLExceptionName( true ) );
                i++;
            }
            p.pO();
            p.p( " )" );
        }

        p.pln( ";" );
    }


    /**
     * Remove escape character ("_"), if any, from given String
     * @param name Given String
     * @return String with any escape character removed
     */
    protected String unEsc(
                           String name ) {
        if ( name.startsWith( "_" ) ) return name.substring( 1 );
        else return name;
    }


    /**
     * Write IDL banner into the output stream for a given Type
     * @param t The given Type.
     * @param dim The dimension required if t is an ArrayType.
     * @param isException true if writing an exception.
     * @param p The output stream.
     */
    protected void writeBanner(
                               Type t,
                               int dim,
                               boolean isException,
                               IndentingWriter p )
        throws IOException {
        String[] modNames = getIDLModuleNames( t );             //module name array
        String fName = unEsc( t.getIDLName() );                 //file name default
        if ( isException && t.isClass() ) {
            ClassType ct = (ClassType)t;                    //file name for Exception
            fName = unEsc( ct.getIDLExceptionName() );
        }
        if ( dim > 0 && t.isArray() ) {
            Type et = t.getElementType();                    //file name for sequence
            fName = "seq" + dim + "_" + unEsc( et.getIDLName().replace( ' ','_' ) );
        }

        p.pln( "/**" );
        p.p( " * " );
        for ( int i1 = 0; i1 < modNames.length; i1++ )
            p.p( modNames[i1] + "/" );
        p.pln( fName + ".idl" );
        p.pln( " * Generated by rmic -idl. Do not edit" );
        String d = DateFormat.getDateTimeInstance(
                                                  DateFormat.FULL,DateFormat.FULL,Locale.getDefault() )
            .format( new Date() );
        String ocStr = "o'clock";
        int ocx = d.indexOf( ocStr );             //remove unwanted o'clock, if any
        p.p ( " * " );
        if ( ocx > -1 )
            p.pln( d.substring( 0,ocx ) + d.substring( ocx + ocStr.length() ) );
        else p.pln( d );
        p.pln( " */" );
        p.pln();
    }


    /**
     * Write #include for orb.idl
     * @param p The output stream.
     */
    protected void writeIncOrb(
                               IndentingWriter p )
        throws IOException {
        p.pln( "#include \"orb.idl\"" );
    }


    /**
     * Write #ifndef guard into the output stream for a given Type
     * @param t The given Type.
     * @param dim The dimension required if t is an ArrayType.
     * @param isException true if writing an exception.
     * @param isForward. No #define needed if it's a forward declare
     * @param p The output stream.
     */
    protected void writeIfndef(
                               Type t,
                               int dim,
                               boolean isException,
                               boolean isForward,
                               IndentingWriter p )
        throws IOException {
        String[] modNames = getIDLModuleNames( t );             //module name array
        String fName = unEsc( t.getIDLName() );                 //file name default
        if ( isException && t.isClass() ) {
            ClassType ct = (ClassType)t;                    //file name for Exception
            fName = unEsc( ct.getIDLExceptionName() );
        }
        if ( dim > 0 && t.isArray() ) {
            Type et = t.getElementType();                    //file name for sequence
            fName = "seq" + dim + "_" + unEsc( et.getIDLName().replace( ' ','_' ) );
        }
        p.pln();
        p.p( "#ifndef __" );
        for ( int i = 0; i < modNames.length; i++ ) p.p( modNames[i] + "_" );
        p.pln( fName + "__" );
        if ( !isForward ) {
        p.p( "#define __" );
        for ( int i = 0; i < modNames.length; i++ ) p.p( modNames[i] + "_" );
            p.pln( fName + "__" );
            p.pln();
    }
    }


    /**
     * Write #endif bracket into the output stream
     * @param p The output stream.
     */
    protected void writeEndif(
                              IndentingWriter p )
        throws IOException {
        p.pln("#endif");
        p.pln();
    }

    /**
     * Write Module start bracketing for the given type into the output stream
     * @param t The given Type
     * @param p The output stream.
     */
    protected void writeModule1(
                                Type t,
                                IndentingWriter p )
        throws IOException {

        String[] modNames = getIDLModuleNames( t );
        p.pln();
        for ( int i = 0; i < modNames.length; i++ )
            p.pln( "module " + modNames[i] + " {" );
    }

    /**
     * Write Module end bracketing for the given type into the output stream
     * @param t The given Type
     * @param p The output stream.
     */
    protected void writeModule2(
                                Type t,
                                IndentingWriter p )
        throws IOException {
        String[] modNames = getIDLModuleNames( t );
        for ( int i=0; i < modNames.length; i++ ) p.pln( "};" );
        p.pln();
    }

}
