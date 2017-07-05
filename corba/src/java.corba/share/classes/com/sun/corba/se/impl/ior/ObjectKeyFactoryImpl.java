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

package com.sun.corba.se.impl.ior;

import java.io.IOException ;

import org.omg.CORBA.MARSHAL ;
import org.omg.CORBA.OctetSeqHolder ;
import org.omg.CORBA_2_3.portable.InputStream ;

import com.sun.corba.se.spi.ior.ObjectId ;
import com.sun.corba.se.spi.ior.ObjectKey ;
import com.sun.corba.se.spi.ior.ObjectKeyFactory ;
import com.sun.corba.se.spi.ior.ObjectKeyTemplate ;

import com.sun.corba.se.spi.orb.ORB ;
import com.sun.corba.se.spi.logging.CORBALogDomains ;

import com.sun.corba.se.impl.orbutil.ORBConstants ;

import com.sun.corba.se.impl.ior.JIDLObjectKeyTemplate ;
import com.sun.corba.se.impl.ior.POAObjectKeyTemplate ;
import com.sun.corba.se.impl.ior.WireObjectKeyTemplate ;
import com.sun.corba.se.impl.ior.ObjectIdImpl ;
import com.sun.corba.se.impl.ior.ObjectKeyImpl ;
import com.sun.corba.se.impl.logging.IORSystemException ;

import com.sun.corba.se.impl.encoding.EncapsInputStream ;
import sun.corba.EncapsInputStreamFactory;


/** Based on the magic and scid, return the appropriate
* ObjectKeyTemplate.  Expects to be called with a valid
* magic.  If scid is not valid, null should be returned.
*/
interface Handler {
    ObjectKeyTemplate handle( int magic, int scid,
        InputStream is, OctetSeqHolder osh ) ;
}

/** Singleton used to manufacture ObjectKey and ObjectKeyTemplate
 * instances.
 * @author Ken Cavanaugh
 */
public class ObjectKeyFactoryImpl implements ObjectKeyFactory
{
    public static final int MAGIC_BASE                  = 0xAFABCAFE ;

    // Magic used in our object keys for JDK 1.2, 1.3, RMI-IIOP OP,
    // J2EE 1.0-1.2.1.
    public static final int JAVAMAGIC_OLD               = MAGIC_BASE ;

    // Magic used only in JDK 1.3.1.  No format changes in object keys.
    public static final int JAVAMAGIC_NEW               = MAGIC_BASE + 1 ;

    // New magic used in our object keys for JDK 1.4, J2EE 1.3 and later.
    // Format changes: all object keys have version string; POA key format
    // is changed.
    public static final int JAVAMAGIC_NEWER             = MAGIC_BASE + 2 ;

    public static final int MAX_MAGIC                   = JAVAMAGIC_NEWER ;

    // Beginning in JDK 1.3.1_01, we introduced changes which required
    // the ability to distinguish between JDK 1.3.1 FCS and the patch
    // versions.  See OldJIDLObjectKeyTemplate.
    public static final byte JDK1_3_1_01_PATCH_LEVEL = 1;

    private final ORB orb ;
    private IORSystemException wrapper ;

    public ObjectKeyFactoryImpl( ORB orb )
    {
        this.orb = orb ;
        wrapper = IORSystemException.get( orb,
            CORBALogDomains.OA_IOR ) ;
    }

    // XXX The handlers still need to be made pluggable.
    //
    // I think this can be done as follows:
    // 1. Move the Handler interface into the SPI as ObjectKeyHandler.
    // 2. Add two methods to ObjectAdapterFactory:
    //      ObjectKeyHandler getHandlerForObjectKey( ) ;
    //      ObjectKeyHandler getHandlerForObjectKeyTemplate( ) ;
    // 3. Move the implementation of the fullKey handler and the
    //    oktempOnly handler into TOAFactory and POAFactory.
    // 4. Move the ObjectKey impl classes into the impl/oa packages.
    // 5. Create an internal interface
    //      interface HandlerFinder {
    //          ObjectKeyHandler get( int scid ) ;
    //      }
    //    and modify create(InputStream,Handler,OctetSeqHolder)
    //    to take a HandlerFinder instead of a Handler.
    // 6. Modify create( byte[] ) and createTemplate( InputStream )
    //    to create an instance of HandlerFinder: something like:
    //      new HandlerFinder() {
    //          ObjectKeyHandler get( int scid )
    //          {
    //              return orb.getRequestDispatcherRegistry().
    //                  getObjectAdapterFactory( scid ).getHandlerForObjectKey() ;
    //          }
    //      and similarly for getHandlerForObjectKeyTemplate.

    /** This handler reads the full object key, both the oktemp
    * and the ID.
    */
    private Handler fullKey = new Handler() {
        public ObjectKeyTemplate handle( int magic, int scid,
            InputStream is, OctetSeqHolder osh ) {
                ObjectKeyTemplate oktemp = null ;

                if ((scid >= ORBConstants.FIRST_POA_SCID) &&
                    (scid <= ORBConstants.MAX_POA_SCID)) {
                    if (magic >= JAVAMAGIC_NEWER)
                        oktemp = new POAObjectKeyTemplate( orb, magic, scid, is, osh ) ;
                    else
                        oktemp = new OldPOAObjectKeyTemplate( orb, magic, scid, is, osh ) ;
                } else if ((scid >= 0) && (scid < ORBConstants.FIRST_POA_SCID)) {
                    if (magic >= JAVAMAGIC_NEWER)
                        oktemp = new JIDLObjectKeyTemplate( orb, magic, scid, is, osh ) ;
                    else
                        oktemp = new OldJIDLObjectKeyTemplate( orb, magic, scid, is, osh );
                }

                return oktemp ;
            }
        } ;

    /** This handler reads only the oktemp.
    */
    private Handler oktempOnly = new Handler() {
        public ObjectKeyTemplate handle( int magic, int scid,
            InputStream is, OctetSeqHolder osh ) {
                ObjectKeyTemplate oktemp = null ;

                if ((scid >= ORBConstants.FIRST_POA_SCID) &&
                    (scid <= ORBConstants.MAX_POA_SCID)) {
                    if (magic >= JAVAMAGIC_NEWER)
                        oktemp = new POAObjectKeyTemplate( orb, magic, scid, is ) ;
                    else
                        oktemp = new OldPOAObjectKeyTemplate( orb, magic, scid, is ) ;
                } else if ((scid >= 0) && (scid < ORBConstants.FIRST_POA_SCID)) {
                    if (magic >= JAVAMAGIC_NEWER)
                        oktemp = new JIDLObjectKeyTemplate( orb, magic, scid, is ) ;
                    else
                        oktemp = new OldJIDLObjectKeyTemplate( orb, magic, scid, is ) ;
                }

                return oktemp ;
            }
        } ;

    /** Returns true iff magic is in the range of valid magic numbers
    * for our ORB.
    */
    private boolean validMagic( int magic )
    {
        return (magic >= MAGIC_BASE) && (magic <= MAX_MAGIC) ;
    }

    /** Creates an ObjectKeyTemplate from the InputStream.  Most of the
    * decoding is done inside the handler.
    */
    private ObjectKeyTemplate create( InputStream is, Handler handler,
        OctetSeqHolder osh )
    {
        ObjectKeyTemplate oktemp = null ;

        try {
            is.mark(0) ;
            int magic = is.read_long() ;

            if (validMagic( magic )) {
                int scid = is.read_long() ;
                oktemp = handler.handle( magic, scid, is, osh ) ;
            }
        } catch (MARSHAL mexc) {
            // XXX log this error
            // ignore this: error handled below because oktemp == null
        }

        if (oktemp == null)
            // If we did not successfully construct a oktemp, reset the
            // stream so that WireObjectKeyTemplate can correctly construct the
            // object key.
            try {
                is.reset() ;
            } catch (IOException exc) {
                // XXX log this error
                // ignore this
            }

        return oktemp ;
    }

    public ObjectKey create( byte[] key )
    {
        OctetSeqHolder osh = new OctetSeqHolder() ;
        EncapsInputStream is = EncapsInputStreamFactory.newEncapsInputStream( orb, key, key.length );

        ObjectKeyTemplate oktemp = create( is, fullKey, osh ) ;
        if (oktemp == null)
            oktemp = new WireObjectKeyTemplate( is, osh ) ;

        ObjectId oid = new ObjectIdImpl( osh.value ) ;
        return new ObjectKeyImpl( oktemp, oid ) ;
    }

    public ObjectKeyTemplate createTemplate( InputStream is )
    {
        ObjectKeyTemplate oktemp = create( is, oktempOnly, null ) ;
        if (oktemp == null)
            oktemp = new WireObjectKeyTemplate( orb ) ;

        return oktemp ;
    }
}
