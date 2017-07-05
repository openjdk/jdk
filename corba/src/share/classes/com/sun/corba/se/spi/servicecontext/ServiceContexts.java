/*
 * Copyright (c) 1999, 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.spi.servicecontext;

import java.lang.reflect.InvocationTargetException ;
import java.lang.reflect.Modifier ;
import java.lang.reflect.Field ;
import java.lang.reflect.Constructor ;
import java.util.*;

import org.omg.CORBA.OctetSeqHelper;
import org.omg.CORBA.SystemException;
import org.omg.CORBA.INTERNAL;
import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA_2_3.portable.OutputStream ;
import org.omg.CORBA_2_3.portable.InputStream ;

import com.sun.org.omg.SendingContext.CodeBase;

import com.sun.corba.se.spi.ior.iiop.GIOPVersion;

import com.sun.corba.se.spi.orb.ORB ;

import com.sun.corba.se.spi.logging.CORBALogDomains;


import com.sun.corba.se.spi.servicecontext.ServiceContext ;
import com.sun.corba.se.spi.servicecontext.ServiceContextRegistry ;
import com.sun.corba.se.spi.servicecontext.ServiceContextData ;
import com.sun.corba.se.spi.servicecontext.UnknownServiceContext ;

import com.sun.corba.se.impl.encoding.CDRInputStream;
import com.sun.corba.se.impl.encoding.EncapsInputStream ;
import com.sun.corba.se.impl.orbutil.ORBUtility ;
import com.sun.corba.se.impl.util.Utility ;
import com.sun.corba.se.impl.logging.ORBUtilSystemException ;

public class ServiceContexts {
    private static boolean isDebugging( OutputStream os )
    {
        ORB orb = (ORB)(os.orb()) ;
        if (orb==null)
            return false ;
        return orb.serviceContextDebugFlag ;
    }

    private static boolean isDebugging( InputStream is )
    {
        ORB orb = (ORB)(is.orb()) ;
        if (orb==null)
            return false ;
        return orb.serviceContextDebugFlag ;
    }

    private void dprint( String msg )
    {
        ORBUtility.dprint( this, msg ) ;
    }

    public static void writeNullServiceContext( OutputStream os )
    {
        if (isDebugging(os))
            ORBUtility.dprint( "ServiceContexts", "Writing null service context" ) ;
        os.write_long( 0 ) ;
    }

    /**
     * Given the input stream, this fills our service
     * context map.  See the definition of scMap for
     * details.  Creates a HashMap.
     *
     * Note that we don't actually unmarshal the
     * bytes of the service contexts here.  That is
     * done when they are actually requested via
     * get(int).
     */
    private void createMapFromInputStream(InputStream is)
    {
        orb = (ORB)(is.orb()) ;
        if (orb.serviceContextDebugFlag)
            dprint( "Constructing ServiceContexts from input stream" ) ;

        int numValid = is.read_long() ;

        if (orb.serviceContextDebugFlag)
            dprint("Number of service contexts = " + numValid);

        for (int ctr = 0; ctr < numValid; ctr++) {
            int scId = is.read_long();

            if (orb.serviceContextDebugFlag)
                dprint("Reading service context id " + scId);

            byte[] data = OctetSeqHelper.read(is);

            if (orb.serviceContextDebugFlag)
                dprint("Service context" + scId + " length: " + data.length);

            scMap.put(new Integer(scId), data);
        }
    }

    public ServiceContexts( ORB orb )
    {
        this.orb = orb ;
        wrapper = ORBUtilSystemException.get( orb,
            CORBALogDomains.RPC_PROTOCOL ) ;

        addAlignmentOnWrite = false ;

        scMap = new HashMap();

        // Use the GIOP version of the ORB.  Should
        // be specified in ServiceContext.
        // See REVISIT below concerning giopVersion.
        giopVersion = orb.getORBData().getGIOPVersion();
        codeBase = null ;
    }

    /**
     * Read the Service contexts from the input stream.
     */
    public ServiceContexts(InputStream s)
    {
        this( (ORB)(s.orb()) ) ;

        // We need to store this so that we can have access
        // to the CodeBase for unmarshaling possible
        // RMI-IIOP valuetype data within an encapsulation.
        // (Known case: UnknownExceptionInfo)
        codeBase = ((CDRInputStream)s).getCodeBase();

        createMapFromInputStream(s);

        // Fix for bug 4904723
        giopVersion = ((CDRInputStream)s).getGIOPVersion();
    }

    /**
     * Find the ServiceContextData for a given scId and unmarshal
     * the bytes.
     */
    private ServiceContext unmarshal(Integer scId, byte[] data) {

        ServiceContextRegistry scr = orb.getServiceContextRegistry();

        ServiceContextData scd = scr.findServiceContextData(scId.intValue());
        ServiceContext sc = null;

        if (scd == null) {
            if (orb.serviceContextDebugFlag) {
                dprint("Could not find ServiceContextData for "
                       + scId
                       + " using UnknownServiceContext");
            }

            sc = new UnknownServiceContext(scId.intValue(), data);

        } else {

            if (orb.serviceContextDebugFlag) {
                dprint("Found " + scd);
            }

            // REVISIT.  GIOP version should be specified as
            // part of a service context's definition, so should
            // be accessible from ServiceContextData via
            // its ServiceContext implementation class.
            //
            // Since we don't have that, yet, I'm using the GIOP
            // version of the input stream, presuming that someone
            // can't send a service context of a later GIOP
            // version than its stream version.
            //
            // Note:  As of Jan 2001, no standard OMG or Sun service contexts
            // ship wchar data or are defined as using anything but GIOP 1.0 CDR.
            EncapsInputStream eis
                = new EncapsInputStream(orb,
                                        data,
                                        data.length,
                                        giopVersion,
                                        codeBase);
            eis.consumeEndian();

            // Now the input stream passed to a ServiceContext
            // constructor is already the encapsulation input
            // stream with the endianness read off, so the
            // service context should just unmarshal its own
            // data.
            sc = scd.makeServiceContext(eis, giopVersion);
            if (sc == null)
                throw wrapper.svcctxUnmarshalError(
                    CompletionStatus.COMPLETED_MAYBE);
        }

        return sc;
    }

    public void addAlignmentPadding()
    {
        // Make service context 12 bytes longer by adding
        // JAVAIDL_ALIGN_SERVICE_ID service context at end.
        // The exact length
        // must be >8 (minimum service context size) and
        // =4 mod 8, so 12 is the minimum.
        addAlignmentOnWrite = true ;
    }

    /**
     * Hopefully unused scid:  This should be changed to a proper
     * VMCID aligned value.  REVISIT!
     */
    private static final int JAVAIDL_ALIGN_SERVICE_ID = 0xbe1345cd ;

    /**
     * Write the service contexts to the output stream.
     *
     * If they haven't been unmarshaled, we don't have to
     * unmarshal them.
     */
    public void write(OutputStream os, GIOPVersion gv)
    {
        if (isDebugging(os)) {
            dprint( "Writing service contexts to output stream" ) ;
            Utility.printStackTrace() ;
        }

        int numsc = scMap.size();

        if (addAlignmentOnWrite) {
            if (isDebugging(os))
                dprint( "Adding alignment padding" ) ;

            numsc++ ;
        }

        if (isDebugging(os))
            dprint( "Service context has " + numsc + " components"  ) ;

        os.write_long( numsc ) ;

        writeServiceContextsInOrder(os, gv);

        if (addAlignmentOnWrite) {
            if (isDebugging(os))
                dprint( "Writing alignment padding" ) ;

            os.write_long( JAVAIDL_ALIGN_SERVICE_ID ) ;
            os.write_long( 4 ) ;
            os.write_octet( (byte)0 ) ;
            os.write_octet( (byte)0 ) ;
            os.write_octet( (byte)0 ) ;
            os.write_octet( (byte)0 ) ;
        }

        if (isDebugging(os))
            dprint( "Service context writing complete" ) ;
    }

    /**
     * Write the service contexts in scMap in a desired order.
     * Right now, the only special case we have is UnknownExceptionInfo,
     * so I'm merely writing it last if present.
     */
    private void writeServiceContextsInOrder(OutputStream os, GIOPVersion gv) {

        // Temporarily remove this rather than check it per iteration
        Integer ueInfoId
            = new Integer(UEInfoServiceContext.SERVICE_CONTEXT_ID);

        Object unknownExceptionInfo = scMap.remove(ueInfoId);

        Iterator iter = scMap.keySet().iterator();

        while (iter.hasNext()) {
            Integer id = (Integer)iter.next();

            writeMapEntry(os, id, scMap.get(id), gv);
        }

        // Write the UnknownExceptionInfo service context last
        // (so it will be after the CodeBase) and restore it in
        // the map.
        if (unknownExceptionInfo != null) {
            writeMapEntry(os, ueInfoId, unknownExceptionInfo, gv);

            scMap.put(ueInfoId, unknownExceptionInfo);
        }
    }

    /**
     * Write the given entry from the scMap to the OutputStream.
     * See note on giopVersion.  The service context should
     * know the GIOP version it is meant for.
     */
    private void writeMapEntry(OutputStream os, Integer id, Object scObj, GIOPVersion gv) {

        // If it's still in byte[] form, we don't need to
        // unmarshal it here, just copy the bytes into
        // the new stream.

        if (scObj instanceof byte[]) {
            if (isDebugging(os))
                dprint( "Writing service context bytes for id " + id);

            OctetSeqHelper.write(os, (byte[])scObj);

        } else {

            // We actually unmarshaled it into a ServiceContext
            // at some point.
            ServiceContext sc = (ServiceContext)scObj;

            if (isDebugging(os))
                dprint( "Writing service context " + sc ) ;

            sc.write(os, gv);
        }
    }

    /** Add a service context to the stream, if there is not already
     * a service context in this object with the same id as sc.
     */
    public void put( ServiceContext sc )
    {
        Integer id = new Integer(sc.getId());
        scMap.put(id, sc);
    }

    public void delete( int scId ) {
        this.delete(new Integer(scId));
    }

    public void delete(Integer id)
    {
        scMap.remove(id)  ;
    }

    public ServiceContext get(int scId) {
        return this.get(new Integer(scId));
    }

    public ServiceContext get(Integer id)
    {
        Object result = scMap.get(id);
        if (result == null)
            return null ;

        // Lazy unmarshaling on first use.
        if (result instanceof byte[]) {

            ServiceContext sc = unmarshal(id, (byte[])result);

            scMap.put(id, sc);

            return sc;
        } else {
            return (ServiceContext)result;
        }
    }

    private ORB orb ;

    /**
     * Map of all ServiceContext objects in this container.
     *
     * Keys are java.lang.Integers for service context IDs.
     * Values are either instances of ServiceContext or the
     * unmarshaled byte arrays (unmarshaled on first use).
     *
     * This provides a mild optimization if we don't happen to
     * use a given service context, but it's main advantage is
     * that it allows us to change the order in which we
     * unmarshal them.  We need to do the UnknownExceptionInfo service
     * context after the SendingContextRunTime service context so that we can
     * get the CodeBase if necessary.
     */
    private Map scMap;

    /**
     * If true, write out a special alignment service context to force the
     * correct alignment on re-marshalling.
     */
    private boolean addAlignmentOnWrite ;

    private CodeBase codeBase;
    private GIOPVersion giopVersion;
    private ORBUtilSystemException wrapper ;
}
