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

package com.sun.corba.se.impl.ior.iiop;

import java.util.List ;
import java.util.Iterator ;

import org.omg.CORBA.SystemException ;

import org.omg.CORBA_2_3.portable.OutputStream ;
import org.omg.CORBA_2_3.portable.InputStream ;

import org.omg.IOP.TAG_ALTERNATE_IIOP_ADDRESS ;
import org.omg.IOP.TAG_INTERNET_IOP;
import org.omg.IOP.TAG_JAVA_CODEBASE;

import com.sun.corba.se.spi.protocol.RequestDispatcherRegistry ;

import com.sun.corba.se.spi.oa.ObjectAdapter ;
import com.sun.corba.se.spi.oa.ObjectAdapterFactory ;

import com.sun.corba.se.spi.ior.ObjectId ;
import com.sun.corba.se.spi.ior.ObjectAdapterId ;
import com.sun.corba.se.spi.ior.TaggedProfile ;
import com.sun.corba.se.spi.ior.TaggedProfileTemplate ;
import com.sun.corba.se.spi.ior.ObjectKey ;
import com.sun.corba.se.spi.ior.ObjectKeyTemplate ;
import com.sun.corba.se.spi.ior.TaggedComponent ;
import com.sun.corba.se.spi.ior.IdentifiableBase ;
import com.sun.corba.se.spi.ior.IORFactories ;
import com.sun.corba.se.spi.ior.ObjectKeyFactory ;

import com.sun.corba.se.spi.ior.iiop.IIOPAddress ;
import com.sun.corba.se.spi.ior.iiop.IIOPProfile ;
import com.sun.corba.se.spi.ior.iiop.IIOPProfileTemplate ;
import com.sun.corba.se.spi.ior.iiop.IIOPFactories ;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion ;
import com.sun.corba.se.spi.ior.iiop.JavaCodebaseComponent ;

import com.sun.corba.se.spi.orb.ORB ;
import com.sun.corba.se.spi.orb.ORBVersion ;

import com.sun.corba.se.spi.logging.CORBALogDomains ;

import com.sun.corba.se.impl.ior.EncapsulationUtility ;

import com.sun.corba.se.impl.encoding.EncapsInputStream ;
import com.sun.corba.se.impl.encoding.EncapsOutputStream ;

import sun.corba.EncapsInputStreamFactory;

import com.sun.corba.se.impl.util.JDKBridge;

import com.sun.corba.se.impl.logging.IORSystemException;

/**
 * @author
 */
public class IIOPProfileImpl extends IdentifiableBase implements IIOPProfile
{
    private ORB orb ;
    private IORSystemException wrapper ;
    private ObjectId oid;
    private IIOPProfileTemplate proftemp;
    private ObjectKeyTemplate oktemp ;

    // Cached lookups
    protected String codebase = null ;
    protected boolean cachedCodebase = false;

    private boolean checkedIsLocal = false ;
    private boolean cachedIsLocal = false ;

    // initialize-on-demand holder
    private static class LocalCodeBaseSingletonHolder {
        public static JavaCodebaseComponent comp ;

        static {
            String localCodebase = JDKBridge.getLocalCodebase() ;
            if (localCodebase == null)
                comp = null ;
            else
                comp = IIOPFactories.makeJavaCodebaseComponent(
                    localCodebase ) ;
        }
    }

    private GIOPVersion giopVersion = null;

    public boolean equals( Object obj )
    {
        if (!(obj instanceof IIOPProfileImpl))
            return false ;

        IIOPProfileImpl other = (IIOPProfileImpl)obj ;

        return oid.equals( other.oid ) && proftemp.equals( other.proftemp ) &&
            oktemp.equals( other.oktemp ) ;
    }

    public int hashCode()
    {
        return oid.hashCode() ^ proftemp.hashCode() ^ oktemp.hashCode() ;
    }

    public ObjectId getObjectId()
    {
        return oid ;
    }

    public TaggedProfileTemplate getTaggedProfileTemplate()
    {
        return proftemp ;
    }

    public ObjectKeyTemplate getObjectKeyTemplate()
    {
        return oktemp ;
    }

    private IIOPProfileImpl( ORB orb )
    {
        this.orb = orb ;
        wrapper = IORSystemException.get( orb,
            CORBALogDomains.OA_IOR ) ;
    }

    public IIOPProfileImpl( ORB orb, ObjectKeyTemplate oktemp, ObjectId oid,
        IIOPProfileTemplate proftemp )
    {
        this( orb ) ;
        this.oktemp = oktemp ;
        this.oid = oid ;
        this.proftemp = proftemp ;
    }

    public IIOPProfileImpl( InputStream is )
    {
        this( (ORB)(is.orb()) ) ;
        init( is ) ;
    }

    public IIOPProfileImpl( ORB orb, org.omg.IOP.TaggedProfile profile)
    {
        this( orb ) ;

        if (profile == null || profile.tag != TAG_INTERNET_IOP.value ||
            profile.profile_data == null) {
            throw wrapper.invalidTaggedProfile() ;
        }

        EncapsInputStream istr = EncapsInputStreamFactory.newEncapsInputStream((ORB)orb, profile.profile_data,
                profile.profile_data.length);
        istr.consumeEndian();
        init( istr ) ;
    }

    private void init( InputStream istr )
    {
        // First, read all of the IIOP IOR data
        GIOPVersion version = new GIOPVersion() ;
        version.read( istr ) ;
        IIOPAddress primary = new IIOPAddressImpl( istr ) ;
        byte[] key = EncapsulationUtility.readOctets( istr ) ;

        ObjectKey okey = orb.getObjectKeyFactory().create( key ) ;
        oktemp = okey.getTemplate() ;
        oid = okey.getId() ;

        proftemp = IIOPFactories.makeIIOPProfileTemplate( orb,
            version, primary ) ;

        // Handle any tagged components (if applicable)
        if (version.getMinor() > 0)
            EncapsulationUtility.readIdentifiableSequence( proftemp,
                orb.getTaggedComponentFactoryFinder(), istr ) ;

        // If there is no codebase in this IOR and there IS a
        // java.rmi.server.codebase property set, we need to
        // update the IOR with the local codebase.  Note that
        // there is only one instance of the local codebase, but it
        // can be safely shared in multiple IORs since it is immutable.
        if (uncachedGetCodeBase() == null) {
            JavaCodebaseComponent jcc = LocalCodeBaseSingletonHolder.comp ;

            if (jcc != null) {
                if (version.getMinor() > 0)
                    proftemp.add( jcc ) ;

                codebase = jcc.getURLs() ;
            }

            // Whether codebase is null or not, we have it,
            // and so getCodebase ned never call uncachedGetCodebase.
            cachedCodebase = true;
        }
    }

    public void writeContents(OutputStream os)
    {
        proftemp.write( oktemp, oid, os ) ;
    }

    public int getId()
    {
        return proftemp.getId() ;
    }

    public boolean isEquivalent( TaggedProfile prof )
    {
        if (!(prof instanceof IIOPProfile))
            return false ;

        IIOPProfile other = (IIOPProfile)prof ;

        return oid.equals( other.getObjectId() ) &&
               proftemp.isEquivalent( other.getTaggedProfileTemplate() ) &&
               oktemp.equals( other.getObjectKeyTemplate() ) ;
    }

    public ObjectKey getObjectKey()
    {
        ObjectKey result = IORFactories.makeObjectKey( oktemp, oid ) ;
        return result ;
    }

    public org.omg.IOP.TaggedProfile getIOPProfile()
    {
        EncapsOutputStream os =
            sun.corba.OutputStreamFactory.newEncapsOutputStream(orb);
        os.write_long( getId() ) ;
        write( os ) ;
        InputStream is = (InputStream)(os.create_input_stream()) ;
        return org.omg.IOP.TaggedProfileHelper.read( is ) ;
    }

    private String uncachedGetCodeBase() {
        Iterator iter = proftemp.iteratorById( TAG_JAVA_CODEBASE.value ) ;

        if (iter.hasNext()) {
            JavaCodebaseComponent jcbc = (JavaCodebaseComponent)(iter.next()) ;
            return jcbc.getURLs() ;
        }

        return null ;
    }

    public synchronized String getCodebase() {
        if (!cachedCodebase) {
            cachedCodebase = true ;
            codebase = uncachedGetCodeBase() ;
        }

        return codebase ;
    }

    /**
     * @return the ORBVersion associated with the object key in the IOR.
     */
    public ORBVersion getORBVersion() {
        return oktemp.getORBVersion();
    }

    public synchronized boolean isLocal()
    {
        if (!checkedIsLocal) {
            checkedIsLocal = true ;
            String host = proftemp.getPrimaryAddress().getHost() ;

            cachedIsLocal = orb.isLocalHost(host) &&
                orb.isLocalServerId(oktemp.getSubcontractId(),
                                           oktemp.getServerId()) &&
                orb.getLegacyServerSocketManager()
                    .legacyIsLocalServerPort(
                        proftemp.getPrimaryAddress().getPort());
        }

        return cachedIsLocal ;
    }

    /** Return the servant for this IOR, if it is local AND if the OA that
     * implements this objref supports direct access to servants outside of an
     * invocation.
     * XXX revisit: do we want this at all?  If we do, it might move to the
     * ObjectKeyTemplate instead.
     */
    public java.lang.Object getServant()
    {
        if (!isLocal())
            return null ;

        RequestDispatcherRegistry scr = orb.getRequestDispatcherRegistry() ;
        ObjectAdapterFactory oaf = scr.getObjectAdapterFactory(
            oktemp.getSubcontractId() ) ;

        ObjectAdapterId oaid = oktemp.getObjectAdapterId() ;
        ObjectAdapter oa = null ;

        try {
            oa = oaf.find( oaid ) ;
        } catch (SystemException exc) {
            // Could not find the OA, so just return null.
            // This usually happens when POAs are being deleted,
            // and the POA always return null for getLocalServant anyway.
            wrapper.getLocalServantFailure( exc, oaid.toString() ) ;
            return null ;
        }

        byte[] boid = oid.getId() ;
        java.lang.Object servant = oa.getLocalServant( boid ) ;
        return servant ;
    }

    /**
     * Return GIOPVersion for this IOR.
     * Requests created against this IOR will be of the
     * return Version.
     */
    public synchronized GIOPVersion getGIOPVersion()
    {
        return proftemp.getGIOPVersion() ;
    }

    public void makeImmutable()
    {
        proftemp.makeImmutable() ;
    }
}
