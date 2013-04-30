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

import java.util.ListIterator ;
import java.util.Iterator ;
import java.util.Map ;
import java.util.HashMap ;

import java.io.StringWriter;
import java.io.IOException;

import javax.rmi.CORBA.Util;

import org.omg.CORBA_2_3.portable.InputStream ;
import org.omg.CORBA_2_3.portable.OutputStream ;

import org.omg.IOP.TAG_INTERNET_IOP ;

import com.sun.corba.se.spi.ior.ObjectId ;
import com.sun.corba.se.spi.ior.TaggedProfileTemplate ;
import com.sun.corba.se.spi.ior.TaggedProfile ;
import com.sun.corba.se.spi.ior.IOR ;
import com.sun.corba.se.spi.ior.IORTemplate ;
import com.sun.corba.se.spi.ior.IORTemplateList ;
import com.sun.corba.se.spi.ior.IdentifiableFactoryFinder ;
import com.sun.corba.se.spi.ior.IdentifiableContainerBase ;
import com.sun.corba.se.spi.ior.ObjectKeyTemplate ;
import com.sun.corba.se.spi.ior.IORFactories ;

import com.sun.corba.se.spi.ior.iiop.GIOPVersion;

import com.sun.corba.se.spi.protocol.RequestDispatcherRegistry;

import com.sun.corba.se.spi.orb.ORB;

import com.sun.corba.se.spi.logging.CORBALogDomains;

import com.sun.corba.se.impl.encoding.MarshalOutputStream;

import com.sun.corba.se.impl.encoding.EncapsOutputStream;

import com.sun.corba.se.impl.orbutil.HexOutputStream;
import com.sun.corba.se.impl.orbutil.ORBConstants;

import com.sun.corba.se.impl.logging.IORSystemException ;

// XXX remove this once getProfile is gone
import com.sun.corba.se.spi.ior.iiop.IIOPProfile ;

/** An IOR is represented as a list of profiles.
* Only objects that extend TaggedProfile should be added to an IOR.
* However, enforcing this restriction requires overriding all
* of the addXXX methods inherited from List, so no check
* is included here.
* @author Ken Cavanaugh
*/
public class IORImpl extends IdentifiableContainerBase implements IOR
{
    private String typeId;
    private ORB factory = null ;
    private boolean isCachedHashValue = false;
    private int cachedHashValue;
    IORSystemException wrapper ;

    public ORB getORB()
    {
        return factory ;
    }

    /* This variable is set directly from the constructors that take
     * an IORTemplate or IORTemplateList as arguments; otherwise it
     * is derived from the list of TaggedProfile instances on the first
     * call to getIORTemplates.  Note that we assume that an IOR with
     * mutiple TaggedProfile instances has the same ObjectId in each
     * TaggedProfile, as otherwise the IOR could never be created through
     * an ObjectReferenceFactory.
     */
    private IORTemplateList iortemps = null ;

    public boolean equals( Object obj )
    {
        if (obj == null)
            return false ;

        if (!(obj instanceof IOR))
            return false ;

        IOR other = (IOR)obj ;

        return super.equals( obj ) && typeId.equals( other.getTypeId() ) ;
    }

    public synchronized int hashCode()
    {
        if (! isCachedHashValue) {
              cachedHashValue =  (super.hashCode() ^ typeId.hashCode());
              isCachedHashValue = true;
        }
        return cachedHashValue;
    }

    /** Construct an empty IOR.  This is needed for null object references.
    */
    public IORImpl( ORB orb )
    {
        this( orb, "" ) ;
    }

    public IORImpl( ORB orb, String typeid )
    {
        factory = orb ;
        wrapper = IORSystemException.get( orb,
            CORBALogDomains.OA_IOR ) ;
        this.typeId = typeid ;
    }

    /** Construct an IOR from an IORTemplate by applying the same
    * object id to each TaggedProfileTemplate in the IORTemplate.
    */
    public IORImpl( ORB orb, String typeId, IORTemplate iortemp, ObjectId id)
    {
        this( orb, typeId ) ;

        this.iortemps = IORFactories.makeIORTemplateList() ;
        this.iortemps.add( iortemp ) ;

        addTaggedProfiles( iortemp, id ) ;

        makeImmutable() ;
    }

    private void addTaggedProfiles( IORTemplate iortemp, ObjectId id )
    {
        ObjectKeyTemplate oktemp = iortemp.getObjectKeyTemplate() ;
        Iterator templateIterator = iortemp.iterator() ;

        while (templateIterator.hasNext()) {
            TaggedProfileTemplate ptemp =
                (TaggedProfileTemplate)(templateIterator.next()) ;

            TaggedProfile profile = ptemp.create( oktemp, id ) ;

            add( profile ) ;
        }
    }

    /** Construct an IOR from an IORTemplate by applying the same
    * object id to each TaggedProfileTemplate in the IORTemplate.
    */
    public IORImpl( ORB orb, String typeId, IORTemplateList iortemps, ObjectId id)
    {
        this( orb, typeId ) ;

        this.iortemps = iortemps ;

        Iterator iter = iortemps.iterator() ;
        while (iter.hasNext()) {
            IORTemplate iortemp = (IORTemplate)(iter.next()) ;
            addTaggedProfiles( iortemp, id ) ;
        }

        makeImmutable() ;
    }

    public IORImpl(InputStream is)
    {
        this( (ORB)(is.orb()), is.read_string() ) ;

        IdentifiableFactoryFinder finder =
            factory.getTaggedProfileFactoryFinder() ;

        EncapsulationUtility.readIdentifiableSequence( this, finder, is ) ;

        makeImmutable() ;
    }

    public String getTypeId()
    {
        return typeId ;
    }

    public void write(OutputStream os)
    {
        os.write_string( typeId ) ;
        EncapsulationUtility.writeIdentifiableSequence( this, os ) ;
    }

    public String stringify()
    {
        StringWriter bs;

        MarshalOutputStream s =
            sun.corba.OutputStreamFactory.newEncapsOutputStream(factory);
        s.putEndian();
        write( (OutputStream)s );
        bs = new StringWriter();
        try {
            s.writeTo(new HexOutputStream(bs));
        } catch (IOException ex) {
            throw wrapper.stringifyWriteError( ex ) ;
        }

        return ORBConstants.STRINGIFY_PREFIX + bs;
    }

    public synchronized void makeImmutable()
    {
        makeElementsImmutable() ;

        if (iortemps != null)
            iortemps.makeImmutable() ;

        super.makeImmutable() ;
    }

    public org.omg.IOP.IOR getIOPIOR() {
        EncapsOutputStream os =
            sun.corba.OutputStreamFactory.newEncapsOutputStream(factory);
        write(os);
        InputStream is = (InputStream) (os.create_input_stream());
        return org.omg.IOP.IORHelper.read(is);
    }

    public boolean isNil()
    {
        //
        // The check for typeId length of 0 below is commented out
        // as a workaround for a bug in ORBs which send a
        // null objref with a non-empty typeId string.
        //
        return ((size() == 0) /* && (typeId.length() == 0) */);
    }

    public boolean isEquivalent(IOR ior)
    {
        Iterator myIterator = iterator() ;
        Iterator otherIterator = ior.iterator() ;
        while (myIterator.hasNext() && otherIterator.hasNext()) {
            TaggedProfile myProfile = (TaggedProfile)(myIterator.next()) ;
            TaggedProfile otherProfile = (TaggedProfile)(otherIterator.next()) ;
            if (!myProfile.isEquivalent( otherProfile ))
                return false ;
        }

        return myIterator.hasNext() == otherIterator.hasNext() ;
    }

    private void initializeIORTemplateList()
    {
        // Maps ObjectKeyTemplate to IORTemplate
        Map oktempToIORTemplate = new HashMap() ;

        iortemps = IORFactories.makeIORTemplateList() ;
        Iterator iter = iterator() ;
        ObjectId oid = null ; // used to check that all profiles have the same oid.
        while (iter.hasNext()) {
            TaggedProfile prof = (TaggedProfile)(iter.next()) ;
            TaggedProfileTemplate ptemp = prof.getTaggedProfileTemplate() ;
            ObjectKeyTemplate oktemp = prof.getObjectKeyTemplate() ;

            // Check that all oids for all profiles are the same: if they are not,
            // throw exception.
            if (oid == null)
                oid = prof.getObjectId() ;
            else if (!oid.equals( prof.getObjectId() ))
                throw wrapper.badOidInIorTemplateList() ;

            // Find or create the IORTemplate for oktemp.
            IORTemplate iortemp = (IORTemplate)(oktempToIORTemplate.get( oktemp )) ;
            if (iortemp == null) {
                iortemp = IORFactories.makeIORTemplate( oktemp ) ;
                oktempToIORTemplate.put( oktemp, iortemp ) ;
                iortemps.add( iortemp ) ;
            }

            iortemp.add( ptemp ) ;
        }

        iortemps.makeImmutable() ;
    }

    /** Return the IORTemplateList for this IOR.  Will throw
     * exception if it is not possible to generate an IOR
     * from the IORTemplateList that is equal to this IOR,
     * which can only happen if not every TaggedProfile in the
     * IOR has the same ObjectId.
     */
    public synchronized IORTemplateList getIORTemplates()
    {
        if (iortemps == null)
            initializeIORTemplateList() ;

        return iortemps ;
    }

    /** Return the first IIOPProfile in this IOR.
     * XXX THIS IS TEMPORARY FOR BACKWARDS COMPATIBILITY AND WILL BE REMOVED
     * SOON!
     */
    public IIOPProfile getProfile()
    {
        IIOPProfile iop = null ;
        Iterator iter = iteratorById( TAG_INTERNET_IOP.value ) ;
        if (iter.hasNext())
            iop = (IIOPProfile)(iter.next()) ;

        if (iop != null)
            return iop ;

        // if we come to this point then no IIOP Profile
        // is present.  Therefore, throw an exception.
        throw wrapper.iorMustHaveIiopProfile() ;
    }
}
