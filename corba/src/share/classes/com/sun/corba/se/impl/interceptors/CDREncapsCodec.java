/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.interceptors;

import org.omg.CORBA.Any;
import org.omg.CORBA.ORB;
import org.omg.CORBA.TypeCode;
import org.omg.CORBA.LocalObject;

import com.sun.corba.se.spi.ior.iiop.GIOPVersion;
import com.sun.corba.se.spi.logging.CORBALogDomains;

import com.sun.corba.se.impl.corba.AnyImpl;
import com.sun.corba.se.impl.encoding.EncapsInputStream;
import com.sun.corba.se.impl.encoding.EncapsOutputStream;
import com.sun.corba.se.impl.logging.ORBUtilSystemException;

import org.omg.IOP.Codec;
import org.omg.IOP.CodecPackage.FormatMismatch;
import org.omg.IOP.CodecPackage.InvalidTypeForEncoding;
import org.omg.IOP.CodecPackage.TypeMismatch;

/**
 * CDREncapsCodec is an implementation of Codec, as described
 * in orbos/99-12-02, that supports CDR encapsulation version 1.0, 1.1, and
 * 1.2.
 */
public final class CDREncapsCodec
    extends org.omg.CORBA.LocalObject
    implements Codec
{
    // The ORB that created the factory this codec was created from
    private ORB orb;
    ORBUtilSystemException wrapper;

    // The GIOP version we are encoding for
    private GIOPVersion giopVersion;

    /*
     *******************************************************************
     * NOTE: CDREncapsCodec must remain immutable!  This is so that we
     * can pre-create CDREncapsCodecs for each version of GIOP in
     * CodecFactoryImpl.
     *******************************************************************/

    /**
     * Creates a new codec implementation.  Uses the given ORB to create
     * CDRInputStreams when necessary.
     *
     * @param orb The ORB to use to create a CDRInputStream or CDROutputStream
     * @param major The major version of GIOP we are encoding for
     * @param minor The minor version of GIOP we are encoding for
     */
    public CDREncapsCodec( ORB orb, int major, int minor ) {
        this.orb = orb;
        wrapper = ORBUtilSystemException.get(
            (com.sun.corba.se.spi.orb.ORB)orb, CORBALogDomains.RPC_PROTOCOL ) ;

        giopVersion = GIOPVersion.getInstance( (byte)major, (byte)minor );
    }

    /**
     * Convert the given any into a CDR encapsulated octet sequence
     */
    public byte[] encode( Any data )
        throws InvalidTypeForEncoding
    {
        if ( data == null )
            throw wrapper.nullParam() ;
        return encodeImpl( data, true );
    }

    /**
     * Decode the given octet sequence into an any based on a CDR
     * encapsulated octet sequence.
     */
    public Any decode ( byte[] data )
        throws FormatMismatch
    {
        if( data == null )
            throw wrapper.nullParam() ;
        return decodeImpl( data, null );
    }

    /**
     * Convert the given any into a CDR encapsulated octet sequence.  Only
     * the data is stored.  The type code is not.
     */
    public byte[] encode_value( Any data )
        throws InvalidTypeForEncoding
    {
        if( data == null )
            throw wrapper.nullParam() ;
        return encodeImpl( data, false );
    }

    /**
     * Decode the given octet sequence into an any based on a CDR
     * encapsulated octet sequence.  The type code is expected not to appear
     * in the octet sequence, and the given type code is used instead.
     */
    public Any decode_value( byte[] data, TypeCode tc )
        throws FormatMismatch, TypeMismatch
    {
        if( data == null )
            throw wrapper.nullParam() ;
        if( tc == null )
            throw  wrapper.nullParam() ;
        return decodeImpl( data, tc );
    }

    /**
     * Convert the given any into a CDR encapsulated octet sequence.
     * If sendTypeCode is true, the type code is sent with the message, as in
     * a standard encapsulation.  If it is false, only the data is sent.
     * Either way, the endian type is sent as the first part of the message.
     */
    private byte[] encodeImpl( Any data, boolean sendTypeCode )
        throws InvalidTypeForEncoding
    {
        if( data == null )
            throw wrapper.nullParam() ;

        // _REVISIT_ Note that InvalidTypeForEncoding is never thrown in
        // the body of this method.  This is due to the fact that CDR*Stream
        // will never throw an exception if the encoding is invalid.  To
        // fix this, the CDROutputStream must know the version of GIOP it
        // is encoding for and it must check to ensure that, for example,
        // wstring cannot be encoded in GIOP 1.0.
        //
        // As part of the GIOP 1.2 work, the CDRInput and OutputStream will
        // be versioned.  This can be handled once this work is complete.

        // Create output stream with default endianness.
        EncapsOutputStream cdrOut =
            sun.corba.OutputStreamFactory.newEncapsOutputStream(
            (com.sun.corba.se.spi.orb.ORB)orb, giopVersion );

        // This is an encapsulation, so put out the endian:
        cdrOut.putEndian();

        // Sometimes encode type code:
        if( sendTypeCode ) {
            cdrOut.write_TypeCode( data.type() );
        }

        // Encode value and return.
        data.write_value( cdrOut );

        return cdrOut.toByteArray();
    }

    /**
     * Decode the given octet sequence into an any based on a CDR
     * encapsulated octet sequence.  If the type code is null, it is
     * expected to appear in the octet sequence.  Otherwise, the given
     * type code is used.
     */
    private Any decodeImpl( byte[] data, TypeCode tc )
        throws FormatMismatch
    {
        if( data == null )
            throw wrapper.nullParam() ;

        AnyImpl any = null;  // return value

        // _REVISIT_ Currently there is no way for us to distinguish between
        // a FormatMismatch and a TypeMismatch because we cannot get this
        // information from the CDRInputStream.  If a RuntimeException occurs,
        // it is turned into a FormatMismatch exception.

        try {
            EncapsInputStream cdrIn = new EncapsInputStream( orb, data,
                data.length, giopVersion );

            cdrIn.consumeEndian();

            // If type code not specified, read it from octet stream:
            if( tc == null ) {
                tc = cdrIn.read_TypeCode();
            }

            // Create a new Any object:
            any = new AnyImpl( (com.sun.corba.se.spi.orb.ORB)orb );
            any.read_value( cdrIn, tc );
        }
        catch( RuntimeException e ) {
            // See above note.
            throw new FormatMismatch();
        }

        return any;
    }
}
