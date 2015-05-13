/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.corba;

import java.io.Serializable;
import java.math.BigDecimal;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.List ;
import java.util.ArrayList ;

import org.omg.CORBA.Principal ;
import org.omg.CORBA.TypeCode ;
import org.omg.CORBA.Any ;
import org.omg.CORBA.CompletionStatus ;
import org.omg.CORBA.TCKind ;

import org.omg.CORBA.portable.Streamable;
import org.omg.CORBA.portable.InputStream;
import org.omg.CORBA.portable.OutputStream;
import org.omg.CORBA.TypeCodePackage.BadKind;
import org.omg.CORBA.TypeCodePackage.Bounds;

import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.orb.ORBVersionFactory;
import com.sun.corba.se.spi.logging.CORBALogDomains;
import com.sun.corba.se.spi.presentation.rmi.StubAdapter;

import com.sun.corba.se.impl.encoding.CDRInputStream;
import com.sun.corba.se.impl.encoding.EncapsInputStream;
import com.sun.corba.se.impl.encoding.EncapsOutputStream;
import com.sun.corba.se.impl.io.ValueUtility;
import com.sun.corba.se.impl.orbutil.RepositoryIdFactory;
import com.sun.corba.se.impl.orbutil.RepositoryIdStrings;
import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.impl.logging.ORBUtilSystemException;

// subclasses must provide a matching helper class
public class AnyImpl extends Any
{
    private static final class AnyInputStream extends EncapsInputStream
    {
        public AnyInputStream(EncapsInputStream theStream )
        {
            super( theStream );
        }
    }

    private static final class AnyOutputStream extends EncapsOutputStream
    {
        public AnyOutputStream(ORB orb)
        {
            super((ORB)orb);
        }

        public org.omg.CORBA.portable.InputStream create_input_stream() {
            final org.omg.CORBA.portable.InputStream is = super
                    .create_input_stream();
            AnyInputStream aIS = AccessController
                    .doPrivileged(new PrivilegedAction<AnyInputStream>() {
                        @Override
                        public AnyInputStream run() {
                            return new AnyInputStream(
                                    (com.sun.corba.se.impl.encoding.EncapsInputStream) is);
                        }
                    });
            return aIS;
        }
    }

    //
    // Always valid.
    //
    private TypeCodeImpl typeCode;
    protected ORB orb;
    private ORBUtilSystemException wrapper ;

    //
    // Validity depends upon typecode. The 'value' and 'object' instance
    // members are used to hold immutable types as defined by the
    // isStreamed[] table below. Otherwise, 'stream' is non-null and
    // holds the value in CDR marshaled format. As an optimization, the
    // stream type is an Any extension of CDR stream that is used to
    // detect an optimization in read_value().
    //
    private CDRInputStream stream;
    private long value;
    private java.lang.Object object;

    // Setting the typecode via the type() accessor wipes out the value.
    // An attempt to extract before the value is set will result
    // in a BAD_OPERATION exception being raised.
    private boolean isInitialized = false;

    private static final int DEFAULT_BUFFER_SIZE = 32;

    /*
     * This boolean array tells us if a given typecode must be
     * streamed. Objects that are immutable don't have to be streamed.
     */
    static boolean isStreamed[] = {
        false,  // null
        false,  // void
        false,  // short
        false,  // long
        false,  // ushort
        false,  // ulong
        false,  // float
        false,  // double
        false,  // boolean
        false,  // char
        false,  // octet
        false,  // any
        false,  // TypeCode
        true,   // Principal
        false,  // objref
        true,   // struct
        true,   // union
        false,  // enum
        false,  // string
        true,   // sequence
        true,   // array
        true,   // alias
        true,   // except
        false,  // longlong
        false,  // ulonglong
        false,  // longdouble
        false,  // wchar
        false,  // wstring
        false,  // fixed
        false,  // value
        false,  // value_box (used to be true)
        false,  // native
        false   // abstract interface
    };

    static AnyImpl convertToNative(ORB orb, Any any) {
        if (any instanceof AnyImpl) {
            return (AnyImpl)any;
        } else {
            AnyImpl anyImpl = new AnyImpl(orb, any);
            anyImpl.typeCode = TypeCodeImpl.convertToNative(orb, anyImpl.typeCode);
            return anyImpl;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Constructors

    /**
     * A constructor that sets the Any to contain a null. It also marks
     * the value as being invalid so that extractions throw an exception
     * until an insertion has been performed.
     */
    public AnyImpl(ORB orb)
    {
        this.orb = orb;
        wrapper = ORBUtilSystemException.get( (com.sun.corba.se.spi.orb.ORB)orb,
            CORBALogDomains.RPC_PRESENTATION ) ;

        typeCode = orb.get_primitive_tc(TCKind._tk_null);
        stream = null;
        object = null;
        value = 0;
        // null is a valid value
        isInitialized = true;
    }

    //
    // Create a new AnyImpl which is a copy of obj.
    //
    public AnyImpl(ORB orb, Any obj) {
        this(orb);

        if ((obj instanceof AnyImpl)) {
            AnyImpl objImpl = (AnyImpl)obj;
            typeCode = objImpl.typeCode;
            value = objImpl.value;
            object = objImpl.object;
            isInitialized = objImpl.isInitialized;

            if (objImpl.stream != null)
                stream = objImpl.stream.dup();

        } else {
            read_value(obj.create_input_stream(), obj.type());
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // basic accessors

    /**
     * returns the type of the element contained in the Any.
     *
     * @return          the TypeCode for the element in the Any
     */
    public TypeCode type() {
        return typeCode;
    }

    private TypeCode realType() {
        return realType(typeCode);
    }

    private TypeCode realType(TypeCode aType) {
        TypeCode realType = aType;
        try {
            // Note: Indirect types are handled in kind() method
            while (realType.kind().value() == TCKind._tk_alias) {
                realType = realType.content_type();
            }
        } catch (BadKind bad) { // impossible
            throw wrapper.badkindCannotOccur( bad ) ;
        }
        return realType;
    }

    /**
     * sets the type of the element to be contained in the Any.
     *
     * @param tc                the TypeCode for the element in the Any
     */
    public void type(TypeCode tc)
    {
        //debug.log ("type2");
        // set the typecode
        typeCode = TypeCodeImpl.convertToNative(orb, tc);

        stream = null;
        value = 0;
        object = null;
        // null is the only legal value this Any can have after resetting the type code
        isInitialized = (tc.kind().value() == TCKind._tk_null);
    }

    /**
     * checks for equality between Anys.
     *
     * @param otherAny  the Any to be compared with.
     * @return          true if the Anys are equal, false otherwise.
     */
    public boolean equal(Any otherAny)
    {
        //debug.log ("equal");

        if (otherAny == this)
            return true;

        // first check for typecode equality.
        // note that this will take aliases into account
        if (!typeCode.equal(otherAny.type()))
            return false;

        // Resolve aliases here
        TypeCode realType = realType();

        // _REVISIT_ Possible optimization for the case where
        // otherAny is a AnyImpl and the endianesses match.
        // Need implementation of CDRInputStream.equals()
        // For now we disable this to encourage testing the generic,
        // unoptimized code below.
        // Unfortunately this generic code needs to copy the whole stream
        // at least once.
        //    if (AnyImpl.isStreamed[realType.kind().value()]) {
        //        if (otherAny instanceof AnyImpl) {
        //            return ((AnyImpl)otherAny).stream.equals(stream);
        //        }
        //    }
        switch (realType.kind().value()) {
            // handle primitive types
            case TCKind._tk_null:
            case TCKind._tk_void:
                return true;
            case TCKind._tk_short:
                return (extract_short() == otherAny.extract_short());
            case TCKind._tk_long:
                return (extract_long() == otherAny.extract_long());
            case TCKind._tk_ushort:
                return (extract_ushort() == otherAny.extract_ushort());
            case TCKind._tk_ulong:
                return (extract_ulong() == otherAny.extract_ulong());
            case TCKind._tk_float:
                return (extract_float() == otherAny.extract_float());
            case TCKind._tk_double:
                return (extract_double() == otherAny.extract_double());
            case TCKind._tk_boolean:
                return (extract_boolean() == otherAny.extract_boolean());
            case TCKind._tk_char:
                return (extract_char() == otherAny.extract_char());
            case TCKind._tk_wchar:
                return (extract_wchar() == otherAny.extract_wchar());
            case TCKind._tk_octet:
                return (extract_octet() == otherAny.extract_octet());
            case TCKind._tk_any:
                return extract_any().equal(otherAny.extract_any());
            case TCKind._tk_TypeCode:
                return extract_TypeCode().equal(otherAny.extract_TypeCode());
            case TCKind._tk_string:
                return extract_string().equals(otherAny.extract_string());
            case TCKind._tk_wstring:
                return (extract_wstring().equals(otherAny.extract_wstring()));
            case TCKind._tk_longlong:
                return (extract_longlong() == otherAny.extract_longlong());
            case TCKind._tk_ulonglong:
                return (extract_ulonglong() == otherAny.extract_ulonglong());

            case TCKind._tk_objref:
                return (extract_Object().equals(otherAny.extract_Object()));
            case TCKind._tk_Principal:
                return (extract_Principal().equals(otherAny.extract_Principal()));

            case TCKind._tk_enum:
                return (extract_long() == otherAny.extract_long());
            case TCKind._tk_fixed:
                return (extract_fixed().compareTo(otherAny.extract_fixed()) == 0);
            case TCKind._tk_except:
            case TCKind._tk_struct:
            case TCKind._tk_union:
            case TCKind._tk_sequence:
            case TCKind._tk_array:
                InputStream copyOfMyStream = this.create_input_stream();
                InputStream copyOfOtherStream = otherAny.create_input_stream();
                return equalMember(realType, copyOfMyStream, copyOfOtherStream);

            // Too complicated to handle value types the way we handle
            // other complex types above. Don't try to decompose it here
            // for faster comparison, just use Object.equals().
            case TCKind._tk_value:
            case TCKind._tk_value_box:
                return extract_Value().equals(otherAny.extract_Value());

            case TCKind._tk_alias:
                throw wrapper.errorResolvingAlias() ;

            case TCKind._tk_longdouble:
                // Unspecified for Java
                throw wrapper.tkLongDoubleNotSupported() ;

            default:
                throw wrapper.typecodeNotSupported() ;
        }
    }

    // Needed for equal() in order to achieve linear performance for complex types.
    // Uses up (recursively) copies of the InputStream in both Anys that got created in equal().
    private boolean equalMember(TypeCode memberType, InputStream myStream, InputStream otherStream) {
        // Resolve aliases here
        TypeCode realType = realType(memberType);

        try {
            switch (realType.kind().value()) {
                // handle primitive types
                case TCKind._tk_null:
                case TCKind._tk_void:
                    return true;
                case TCKind._tk_short:
                    return (myStream.read_short() == otherStream.read_short());
                case TCKind._tk_long:
                    return (myStream.read_long() == otherStream.read_long());
                case TCKind._tk_ushort:
                    return (myStream.read_ushort() == otherStream.read_ushort());
                case TCKind._tk_ulong:
                    return (myStream.read_ulong() == otherStream.read_ulong());
                case TCKind._tk_float:
                    return (myStream.read_float() == otherStream.read_float());
                case TCKind._tk_double:
                    return (myStream.read_double() == otherStream.read_double());
                case TCKind._tk_boolean:
                    return (myStream.read_boolean() == otherStream.read_boolean());
                case TCKind._tk_char:
                    return (myStream.read_char() == otherStream.read_char());
                case TCKind._tk_wchar:
                    return (myStream.read_wchar() == otherStream.read_wchar());
                case TCKind._tk_octet:
                    return (myStream.read_octet() == otherStream.read_octet());
                case TCKind._tk_any:
                    return myStream.read_any().equal(otherStream.read_any());
                case TCKind._tk_TypeCode:
                    return myStream.read_TypeCode().equal(otherStream.read_TypeCode());
                case TCKind._tk_string:
                    return myStream.read_string().equals(otherStream.read_string());
                case TCKind._tk_wstring:
                    return (myStream.read_wstring().equals(otherStream.read_wstring()));
                case TCKind._tk_longlong:
                    return (myStream.read_longlong() == otherStream.read_longlong());
                case TCKind._tk_ulonglong:
                    return (myStream.read_ulonglong() == otherStream.read_ulonglong());

                case TCKind._tk_objref:
                    return (myStream.read_Object().equals(otherStream.read_Object()));
                case TCKind._tk_Principal:
                    return (myStream.read_Principal().equals(otherStream.read_Principal()));

                case TCKind._tk_enum:
                    return (myStream.read_long() == otherStream.read_long());
                case TCKind._tk_fixed:
                    return (myStream.read_fixed().compareTo(otherStream.read_fixed()) == 0);
                case TCKind._tk_except:
                case TCKind._tk_struct: {
                    int length = realType.member_count();
                    for (int i=0; i<length; i++) {
                        if ( ! equalMember(realType.member_type(i), myStream, otherStream)) {
                            return false;
                        }
                    }
                    return true;
                }
                case TCKind._tk_union: {
                    Any myDiscriminator = orb.create_any();
                    Any otherDiscriminator = orb.create_any();
                    myDiscriminator.read_value(myStream, realType.discriminator_type());
                    otherDiscriminator.read_value(otherStream, realType.discriminator_type());

                    if ( ! myDiscriminator.equal(otherDiscriminator)) {
                        return false;
                    }
                    TypeCodeImpl realTypeCodeImpl = TypeCodeImpl.convertToNative(orb, realType);
                    int memberIndex = realTypeCodeImpl.currentUnionMemberIndex(myDiscriminator);
                    if (memberIndex == -1)
                        throw wrapper.unionDiscriminatorError() ;

                    if ( ! equalMember(realType.member_type(memberIndex), myStream, otherStream)) {
                        return false;
                    }
                    return true;
                }
                case TCKind._tk_sequence: {
                    int length = myStream.read_long();
                    otherStream.read_long(); // just so that the two stream are in sync
                    for (int i=0; i<length; i++) {
                        if ( ! equalMember(realType.content_type(), myStream, otherStream)) {
                            return false;
                        }
                    }
                    return true;
                }
                case TCKind._tk_array: {
                    int length = realType.member_count();
                    for (int i=0; i<length; i++) {
                        if ( ! equalMember(realType.content_type(), myStream, otherStream)) {
                            return false;
                        }
                    }
                    return true;
                }

                // Too complicated to handle value types the way we handle
                // other complex types above. Don't try to decompose it here
                // for faster comparison, just use Object.equals().
                case TCKind._tk_value:
                case TCKind._tk_value_box:
                    org.omg.CORBA_2_3.portable.InputStream mine =
                        (org.omg.CORBA_2_3.portable.InputStream)myStream;
                    org.omg.CORBA_2_3.portable.InputStream other =
                        (org.omg.CORBA_2_3.portable.InputStream)otherStream;
                    return mine.read_value().equals(other.read_value());

                case TCKind._tk_alias:
                    // error resolving alias above
                    throw wrapper.errorResolvingAlias() ;

                case TCKind._tk_longdouble:
                    throw wrapper.tkLongDoubleNotSupported() ;

                default:
                    throw wrapper.typecodeNotSupported() ;
            }
        } catch (BadKind badKind) { // impossible
            throw wrapper.badkindCannotOccur() ;
        } catch (Bounds bounds) { // impossible
            throw wrapper.boundsCannotOccur() ;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // accessors for marshaling/unmarshaling

    /**
     * returns an output stream that an Any value can be marshaled into.
     *
     * @return          the OutputStream to marshal value of Any into
     */
    public org.omg.CORBA.portable.OutputStream create_output_stream()
    {
        //debug.log ("create_output_stream");
        final ORB finalorb = this.orb;
        return AccessController.doPrivileged(new PrivilegedAction<AnyOutputStream>() {
            @Override
            public AnyOutputStream run() {
                return new AnyOutputStream(finalorb);
            }
        });
    }

    /**
     * returns an input stream that an Any value can be marshaled out of.
     *
     * @return          the InputStream to marshal value of Any out of.
     */
    public org.omg.CORBA.portable.InputStream create_input_stream()
    {
        //
        // We create a new InputStream so that multiple threads can call here
        // and read the streams in parallel without thread safety problems.
        //
        //debug.log ("create_input_stream");
        if (AnyImpl.isStreamed[realType().kind().value()]) {
            return stream.dup();
        } else {
            OutputStream os = (OutputStream)orb.create_output_stream();
            TCUtility.marshalIn(os, realType(), value, object);

            return os.create_input_stream();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // marshaling/unmarshaling routines

    //
    // If the InputStream is a CDRInputStream then we can copy the bytes
    // since it is in our format and does not have alignment issues.
    //
    public void read_value(org.omg.CORBA.portable.InputStream in, TypeCode tc)
    {
        //debug.log ("read_value");
        //
        // Assume that someone isn't going to think they can keep reading
        // from this stream after calling us. That would be likely for
        // an IIOPInputStream but if it is an AnyInputStream then they
        // presumably obtained it via our create_output_stream() so they could
        // write the contents of an IDL data type to it and then call
        // create_input_stream() for us to read it. This is how Helper classes
        // typically implement the insert() method.
        // We should probably document this behavior in the 1.1 revision
        // task force.
        //

        typeCode = TypeCodeImpl.convertToNative(orb, tc);
        int kind = realType().kind().value();
        if (kind >= isStreamed.length) {
            throw wrapper.invalidIsstreamedTckind( CompletionStatus.COMPLETED_MAYBE,
                new Integer(kind)) ;
        }

        if (AnyImpl.isStreamed[kind]) {
            if ( in instanceof AnyInputStream ) {
                // could only have been created here
                stream = (CDRInputStream)in;
            } else {
                org.omg.CORBA_2_3.portable.OutputStream out =
                    (org.omg.CORBA_2_3.portable.OutputStream)orb.create_output_stream();
                typeCode.copy((org.omg.CORBA_2_3.portable.InputStream)in, out);
                stream = (CDRInputStream)out.create_input_stream();
            }
        } else {
            java.lang.Object[] objholder = new java.lang.Object[1];
            objholder[0] = object;
            long[] longholder = new long[1];
            TCUtility.unmarshalIn(in, realType(), longholder, objholder);
            value = longholder[0];
            object = objholder[0];
            stream = null;
        }
        isInitialized = true;
    }


    //
    // We could optimize this by noticing whether the target stream
    // has ever had anything marshaled on it that required an
    // alignment of greater than 4 (was write_double() ever called on it).
    // If not, then we can just do a byte array copy without having to
    // drive the remarshaling through typecode interpretation.
    //
    public void write_value(OutputStream out)
    {
        //debug.log ("write_value");
        if (AnyImpl.isStreamed[realType().kind().value()]) {
            typeCode.copy(stream.dup(), out);
        } else {
            // _REVISIT_ check isInitialized whether all we write is TypeCode!
            TCUtility.marshalIn(out, realType(), value, object);
        }
    }

    /**
     * takes a streamable and inserts its reference into the any
     *
     * @param s         the streamable to insert
     */
    public void insert_Streamable(Streamable s)
    {
        //debug.log ("insert_Streamable");
        typeCode = TypeCodeImpl.convertToNative(orb, s._type());
        object = s;
        isInitialized = true;
    }

    public Streamable extract_Streamable()
    {
        //debug.log( "extract_Streamable" ) ;
        return (Streamable)object;
    }

    ///////////////////////////////////////////////////////////////////////////
    // insertion/extraction/replacement for all basic types

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public void insert_short(short s)
    {
        //debug.log ("insert_short");
        typeCode = orb.get_primitive_tc(TCKind._tk_short);
        value = s;
        isInitialized = true;
    }

    private String getTCKindName( int tc )
    {
        if ((tc >= 0) && (tc < TypeCodeImpl.kindNames.length))
            return TypeCodeImpl.kindNames[tc] ;
        else
            return "UNKNOWN(" + tc + ")" ;
    }

    private void checkExtractBadOperation( int expected )
    {
        if (!isInitialized)
            throw wrapper.extractNotInitialized() ;

        int tc = realType().kind().value() ;
        if (tc != expected) {
            String tcName = getTCKindName( tc ) ;
            String expectedName = getTCKindName( expected ) ;
            throw wrapper.extractWrongType( expectedName, tcName ) ;
        }
    }

    private void checkExtractBadOperationList( int[] expected )
    {
        if (!isInitialized)
            throw wrapper.extractNotInitialized() ;

        int tc = realType().kind().value() ;
        for (int ctr=0; ctr<expected.length; ctr++)
            if (tc == expected[ctr])
                return ;

        List list = new ArrayList() ;
        for (int ctr=0; ctr<expected.length; ctr++)
            list.add( getTCKindName( expected[ctr] ) ) ;

        String tcName = getTCKindName( tc ) ;
        throw wrapper.extractWrongTypeList( list, tcName ) ;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public short extract_short()
    {
        //debug.log ("extract_short");
        checkExtractBadOperation( TCKind._tk_short ) ;
        return (short)value;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public void insert_long(int l)
    {
        //debug.log ("insert_long");
        // A long value is applicable to enums as well, so don't erase the enum type code
        // in case it was initialized that way before.
        int kind = realType().kind().value();
        if (kind != TCKind._tk_long && kind != TCKind._tk_enum) {
            typeCode = orb.get_primitive_tc(TCKind._tk_long);
        }
        value = l;
        isInitialized = true;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public int extract_long()
    {
        //debug.log ("extract_long");
        checkExtractBadOperationList( new int[] { TCKind._tk_long, TCKind._tk_enum } ) ;
        return (int)value;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public void insert_ushort(short s)
    {
        //debug.log ("insert_ushort");
        typeCode = orb.get_primitive_tc(TCKind._tk_ushort);
        value = s;
        isInitialized = true;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public short extract_ushort()
    {
        //debug.log ("extract_ushort");
        checkExtractBadOperation( TCKind._tk_ushort ) ;
        return (short)value;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public void insert_ulong(int l)
    {
        //debug.log ("insert_ulong");
        typeCode = orb.get_primitive_tc(TCKind._tk_ulong);
        value = l;
        isInitialized = true;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public int extract_ulong()
    {
        //debug.log ("extract_ulong");
        checkExtractBadOperation( TCKind._tk_ulong ) ;
        return (int)value;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public void insert_float(float f)
    {
        //debug.log ("insert_float");
        typeCode = orb.get_primitive_tc(TCKind._tk_float);
        value = Float.floatToIntBits(f);
        isInitialized = true;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public float extract_float()
    {
        //debug.log ("extract_float");
        checkExtractBadOperation( TCKind._tk_float ) ;
        return Float.intBitsToFloat((int)value);
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public void insert_double(double d)
    {
        //debug.log ("insert_double");
        typeCode = orb.get_primitive_tc(TCKind._tk_double);
        value = Double.doubleToLongBits(d);
        isInitialized = true;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public double extract_double()
    {
        //debug.log ("extract_double");
        checkExtractBadOperation( TCKind._tk_double ) ;
        return Double.longBitsToDouble(value);
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public void insert_longlong(long l)
    {
        //debug.log ("insert_longlong");
        typeCode = orb.get_primitive_tc(TCKind._tk_longlong);
        value = l;
        isInitialized = true;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public long extract_longlong()
    {
        //debug.log ("extract_longlong");
        checkExtractBadOperation( TCKind._tk_longlong ) ;
        return value;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public void insert_ulonglong(long l)
    {
        //debug.log ("insert_ulonglong");
        typeCode = orb.get_primitive_tc(TCKind._tk_ulonglong);
        value = l;
        isInitialized = true;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public long extract_ulonglong()
    {
        //debug.log ("extract_ulonglong");
        checkExtractBadOperation( TCKind._tk_ulonglong ) ;
        return value;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public void insert_boolean(boolean b)
    {
        //debug.log ("insert_boolean");
        typeCode = orb.get_primitive_tc(TCKind._tk_boolean);
        value = (b)? 1:0;
        isInitialized = true;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public boolean extract_boolean()
    {
        //debug.log ("extract_boolean");
        checkExtractBadOperation( TCKind._tk_boolean ) ;
        return (value == 0)? false: true;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public void insert_char(char c)
    {
        //debug.log ("insert_char");
        typeCode = orb.get_primitive_tc(TCKind._tk_char);
        value = c;
        isInitialized = true;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public char extract_char()
    {
        //debug.log ("extract_char");
        checkExtractBadOperation( TCKind._tk_char ) ;
        return (char)value;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public void insert_wchar(char c)
    {
        //debug.log ("insert_wchar");
        typeCode = orb.get_primitive_tc(TCKind._tk_wchar);
        value = c;
        isInitialized = true;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public char extract_wchar()
    {
        //debug.log ("extract_wchar");
        checkExtractBadOperation( TCKind._tk_wchar ) ;
        return (char)value;
    }


    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public void insert_octet(byte b)
    {
        //debug.log ("insert_octet");
        typeCode = orb.get_primitive_tc(TCKind._tk_octet);
        value = b;
        isInitialized = true;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public byte extract_octet()
    {
        //debug.log ("extract_octet");
        checkExtractBadOperation( TCKind._tk_octet ) ;
        return (byte)value;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public void insert_string(String s)
    {
        //debug.log ("insert_string");
        // Make sure type code information for bounded strings is not erased
        if (typeCode.kind() == TCKind.tk_string) {
            int length = 0;
            try {
                length = typeCode.length();
            } catch (BadKind bad) {
                throw wrapper.badkindCannotOccur() ;
            }

            // Check if bounded strings length is not exceeded
            if (length != 0 && s != null && s.length() > length) {
                throw wrapper.badStringBounds( new Integer(s.length()),
                    new Integer(length) ) ;
            }
        } else {
            typeCode = orb.get_primitive_tc(TCKind._tk_string);
        }
        object = s;
        isInitialized = true;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public String extract_string()
    {
        //debug.log ("extract_string");
        checkExtractBadOperation( TCKind._tk_string ) ;
        return (String)object;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public void insert_wstring(String s)
    {
        //debug.log ("insert_wstring");
        // Make sure type code information for bounded strings is not erased
        if (typeCode.kind() == TCKind.tk_wstring) {
            int length = 0;
            try {
                length = typeCode.length();
            } catch (BadKind bad) {
                throw wrapper.badkindCannotOccur() ;
            }

            // Check if bounded strings length is not exceeded
            if (length != 0 && s != null && s.length() > length) {
                throw wrapper.badStringBounds( new Integer(s.length()),
                    new Integer(length) ) ;
            }
        } else {
            typeCode = orb.get_primitive_tc(TCKind._tk_wstring);
        }
        object = s;
        isInitialized = true;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public String extract_wstring()
    {
        //debug.log ("extract_wstring");
        checkExtractBadOperation( TCKind._tk_wstring ) ;
        return (String)object;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public void insert_any(Any a)
    {
        //debug.log ("insert_any");
        typeCode = orb.get_primitive_tc(TCKind._tk_any);
        object = a;
        stream = null;
        isInitialized = true;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public Any extract_any()
    {
        //debug.log ("extract_any");
        checkExtractBadOperation( TCKind._tk_any ) ;
        return (Any)object;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public void insert_Object(org.omg.CORBA.Object o)
    {
        //debug.log ("insert_Object");
        if ( o == null ) {
            typeCode = orb.get_primitive_tc(TCKind._tk_objref);
        } else {
            if (StubAdapter.isStub(o)) {
                String[] ids = StubAdapter.getTypeIds( o ) ;
                typeCode = new TypeCodeImpl(orb, TCKind._tk_objref, ids[0], "");
            } else {
                throw wrapper.badInsertobjParam(
                    CompletionStatus.COMPLETED_MAYBE, o.getClass().getName() ) ;
            }
        }

        object = o;
        isInitialized = true;
    }

    /**
     * A variant of the insertion operation that takes a typecode
     * argument as well.
     */
    public void insert_Object(org.omg.CORBA.Object o, TypeCode tc)
    {
        //debug.log ("insert_Object2");
        try {
            if ( tc.id().equals("IDL:omg.org/CORBA/Object:1.0") || o._is_a(tc.id()) )
                {
                    typeCode = TypeCodeImpl.convertToNative(orb, tc);
                    object = o;
                }
            else {
                throw wrapper.insertObjectIncompatible() ;
            }
        } catch ( Exception ex ) {
            throw wrapper.insertObjectFailed(ex) ;
        }
        isInitialized = true;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public org.omg.CORBA.Object extract_Object()
    {
        //debug.log ("extract_Object");
        if (!isInitialized)
            throw wrapper.extractNotInitialized() ;

        // Check if the object contained here is of the type in typeCode
        org.omg.CORBA.Object obj = null;
        try {
            obj = (org.omg.CORBA.Object) object;
            if (typeCode.id().equals("IDL:omg.org/CORBA/Object:1.0") || obj._is_a(typeCode.id())) {
                return obj;
            } else {
                throw wrapper.extractObjectIncompatible() ;
            }
        } catch ( Exception ex ) {
            throw wrapper.extractObjectFailed(ex);
        }
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public void insert_TypeCode(TypeCode tc)
    {
        //debug.log ("insert_TypeCode");
        typeCode = orb.get_primitive_tc(TCKind._tk_TypeCode);
        object = tc;
        isInitialized = true;
    }

    /**
     * See the description of the <a href="#anyOps">general Any operations.</a>
     */
    public TypeCode extract_TypeCode()
    {
        //debug.log ("extract_TypeCode");
        checkExtractBadOperation( TCKind._tk_TypeCode ) ;
        return (TypeCode)object;
    }

    /**
     * @deprecated
     */
    @Deprecated
    public void insert_Principal(Principal p)
    {
        typeCode = orb.get_primitive_tc(TCKind._tk_Principal);
        object = p;
        isInitialized = true;
    }

    /**
     * @deprecated
     */
    @Deprecated
    public Principal extract_Principal()
    {
        checkExtractBadOperation( TCKind._tk_Principal ) ;
        return (Principal)object;
    }

    /**
     * Note that the Serializable really should be an IDLEntity of
     * some kind.  It shouldn't just be an RMI-IIOP type.  Currently,
     * we accept and will produce RMI repIds with the latest
     * calculations if given a non-IDLEntity Serializable.
     */
    public Serializable extract_Value()
    {
        //debug.log ("extract_Value");
        checkExtractBadOperationList( new int[] { TCKind._tk_value,
            TCKind._tk_value_box, TCKind._tk_abstract_interface } ) ;
        return (Serializable)object;
    }

    public void insert_Value(Serializable v)
    {
        //debug.log ("insert_Value");
        object = v;

        TypeCode tc;

        if ( v == null ) {
            tc = orb.get_primitive_tc (TCKind.tk_value);
        } else {
            // See note in getPrimitiveTypeCodeForClass.  We
            // have to use the latest type code fixes in this
            // case since there is no way to know what ORB will
            // actually send this Any.  In RMI-IIOP, when using
            // Util.writeAny, we can do the versioning correctly,
            // and use the insert_Value(Serializable, TypeCode)
            // method.
            //
            // The ORB singleton uses the latest version.
            tc = createTypeCodeForClass (v.getClass(), (ORB)ORB.init());
        }

        typeCode = TypeCodeImpl.convertToNative(orb, tc);
        isInitialized = true;
    }

    public void insert_Value(Serializable v, org.omg.CORBA.TypeCode t)
    {
        //debug.log ("insert_Value2");
        object = v;
        typeCode = TypeCodeImpl.convertToNative(orb, t);
        isInitialized = true;
    }

    public void insert_fixed(java.math.BigDecimal value) {
        typeCode = TypeCodeImpl.convertToNative(orb,
            orb.create_fixed_tc(TypeCodeImpl.digits(value), TypeCodeImpl.scale(value)));
        object = value;
        isInitialized = true;
    }

    public void insert_fixed(java.math.BigDecimal value, org.omg.CORBA.TypeCode type)
    {
        try {
            if (TypeCodeImpl.digits(value) > type.fixed_digits() ||
                TypeCodeImpl.scale(value) > type.fixed_scale())
            {
                throw wrapper.fixedNotMatch() ;
            }
        } catch (org.omg.CORBA.TypeCodePackage.BadKind bk) {
            // type isn't even of kind fixed
            throw wrapper.fixedBadTypecode( bk ) ;
        }
        typeCode = TypeCodeImpl.convertToNative(orb, type);
        object = value;
        isInitialized = true;
    }

    public java.math.BigDecimal extract_fixed() {
        checkExtractBadOperation( TCKind._tk_fixed ) ;
        return (BigDecimal)object;
    }

    /**
     * Utility method for insert_Value and Util.writeAny.
     *
     * The ORB passed in should have the desired ORBVersion.  It
     * is used to generate the type codes.
     */
    public TypeCode createTypeCodeForClass (java.lang.Class c, ORB tcORB)
    {
        // Look in the cache first
        TypeCodeImpl classTC = tcORB.getTypeCodeForClass(c);
        if (classTC != null)
            return classTC;

        // All cases need to be able to create repository IDs.
        //
        // See bug 4391648 for more info about the tcORB in this
        // case.
        RepositoryIdStrings repStrs
            = RepositoryIdFactory.getRepIdStringsFactory();


        // Assertion: c instanceof Serializable?

        if ( c.isArray() ) {
            // Arrays - may recurse for multi-dimensional arrays
            Class componentClass = c.getComponentType();
            TypeCode embeddedType;
            if ( componentClass.isPrimitive() ) {
                embeddedType = getPrimitiveTypeCodeForClass(componentClass,
                                                            tcORB);
            } else {
                embeddedType = createTypeCodeForClass (componentClass,
                                                       tcORB);
            }
            TypeCode t = tcORB.create_sequence_tc (0, embeddedType);

            String id = repStrs.createForJavaType(c);

            return tcORB.create_value_box_tc (id, "Sequence", t);
        } else if ( c == java.lang.String.class ) {
            // Strings
            TypeCode t = tcORB.create_string_tc (0);

            String id = repStrs.createForJavaType(c);

            return tcORB.create_value_box_tc (id, "StringValue", t);
        }

        // Anything else
        // We know that this is a TypeCodeImpl since it is our ORB
        classTC = (TypeCodeImpl)ValueUtility.createTypeCodeForClass(
            tcORB, c, ORBUtility.createValueHandler());
        // Intruct classTC to store its buffer
        classTC.setCaching(true);
        // Update the cache
        tcORB.setTypeCodeForClass(c, classTC);
        return classTC;
    }

    /**
     * It looks like this was copied from io.ValueUtility at some
     * point.
     *
     * It's used by createTypeCodeForClass.  The tcORB passed in
     * should have the desired ORB version, and is used to
     * create the type codes.
     */
    private TypeCode getPrimitiveTypeCodeForClass (Class c, ORB tcORB)
    {
        //debug.log ("getPrimitiveTypeCodeForClass");

        if (c == Integer.TYPE) {
            return tcORB.get_primitive_tc (TCKind.tk_long);
        } else if (c == Byte.TYPE) {
            return tcORB.get_primitive_tc (TCKind.tk_octet);
        } else if (c == Long.TYPE) {
            return tcORB.get_primitive_tc (TCKind.tk_longlong);
        } else if (c == Float.TYPE) {
            return tcORB.get_primitive_tc (TCKind.tk_float);
        } else if (c == Double.TYPE) {
            return tcORB.get_primitive_tc (TCKind.tk_double);
        } else if (c == Short.TYPE) {
            return tcORB.get_primitive_tc (TCKind.tk_short);
        } else if (c == Character.TYPE) {
            // For Merlin or later JDKs, or for foreign ORBs,
            // we correctly say that a Java char maps to a
            // CORBA wchar.  For backwards compatibility
            // with our older ORBs, we say it maps to a
            // CORBA char.  This is only used in RMI-IIOP
            // in our javax.rmi.CORBA.Util delegate's
            // writeAny method.  In Java IDL, there's no way
            // to know the ORB version that the Any will be
            // sent out with -- it could be different than
            // the one used to create the Any -- so we use the
            // most recent version (see insert_Value).
            if (ORBVersionFactory.getFOREIGN().compareTo(tcORB.getORBVersion()) == 0 ||
                ORBVersionFactory.getNEWER().compareTo(tcORB.getORBVersion()) <= 0)
                return tcORB.get_primitive_tc(TCKind.tk_wchar);
            else
                return tcORB.get_primitive_tc(TCKind.tk_char);
        } else if (c == Boolean.TYPE) {
            return tcORB.get_primitive_tc (TCKind.tk_boolean);
        } else {
            // _REVISIT_ Not sure if this is right.
            return tcORB.get_primitive_tc (TCKind.tk_any);
        }
    }

    // Extracts a member value according to the given TypeCode from the given complex Any
    // (at the Anys current internal stream position, consuming the anys stream on the way)
    // and returns it wrapped into a new Any
    public Any extractAny(TypeCode memberType, ORB orb) {
        Any returnValue = orb.create_any();
        OutputStream out = returnValue.create_output_stream();
        TypeCodeImpl.convertToNative(orb, memberType).copy((InputStream)stream, out);
        returnValue.read_value(out.create_input_stream(), memberType);
        return returnValue;
    }

    // This method could very well be moved into TypeCodeImpl or a common utility class,
    // but is has to be in this package.
    static public Any extractAnyFromStream(TypeCode memberType, InputStream input, ORB orb) {
        Any returnValue = orb.create_any();
        OutputStream out = returnValue.create_output_stream();
        TypeCodeImpl.convertToNative(orb, memberType).copy(input, out);
        returnValue.read_value(out.create_input_stream(), memberType);
        return returnValue;
    }

    // There is no other way for DynAnys to find out whether the Any is initialized.
    public boolean isInitialized() {
        return isInitialized;
    }
}
