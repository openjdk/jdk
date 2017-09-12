/*
 * Copyright (c) 2002, 2006, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.transport;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.sun.corba.se.pept.transport.ContactInfo;

import com.sun.corba.se.spi.ior.IOR ;
import com.sun.corba.se.spi.ior.iiop.IIOPProfile ;
import com.sun.corba.se.spi.ior.iiop.IIOPProfileTemplate ;
import com.sun.corba.se.spi.oa.ObjectAdapterFactory;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.protocol.LocalClientRequestDispatcher;
import com.sun.corba.se.spi.protocol.LocalClientRequestDispatcherFactory;
import com.sun.corba.se.spi.transport.CorbaContactInfoList ;
import com.sun.corba.se.spi.transport.SocketInfo;

import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.impl.protocol.NotLocalLocalCRDImpl;

/**
 * @author Harold Carr
 */
public class CorbaContactInfoListImpl
    implements
        CorbaContactInfoList
{
    protected ORB orb;
    protected LocalClientRequestDispatcher LocalClientRequestDispatcher;
    protected IOR targetIOR;
    protected IOR effectiveTargetIOR;
    protected List effectiveTargetIORContactInfoList;
    protected ContactInfo primaryContactInfo;

    // XREVISIT - is this used?
    public CorbaContactInfoListImpl(ORB orb)
    {
        this.orb = orb;
    }

    public CorbaContactInfoListImpl(ORB orb, IOR targetIOR)
    {
        this(orb);
        setTargetIOR(targetIOR);
    }

    ////////////////////////////////////////////////////
    //
    // pept.transport.ContactInfoList
    //

    public synchronized Iterator iterator()
    {
        createContactInfoList();
        return new CorbaContactInfoListIteratorImpl(
            orb, this, primaryContactInfo,
            effectiveTargetIORContactInfoList);
    }

    ////////////////////////////////////////////////////
    //
    // spi.transport.CorbaContactInfoList
    //

    public synchronized void setTargetIOR(IOR targetIOR)
    {
        this.targetIOR = targetIOR;
        setEffectiveTargetIOR(targetIOR);
    }

    public synchronized IOR getTargetIOR()
    {
        return targetIOR;
    }

    public synchronized void setEffectiveTargetIOR(IOR effectiveTargetIOR)
    {
        this.effectiveTargetIOR = effectiveTargetIOR;
        effectiveTargetIORContactInfoList = null;
        if (primaryContactInfo != null &&
            orb.getORBData().getIIOPPrimaryToContactInfo() != null)
        {
            orb.getORBData().getIIOPPrimaryToContactInfo()
                .reset(primaryContactInfo);
        }
        primaryContactInfo = null;
        setLocalSubcontract();
    }

    public synchronized IOR getEffectiveTargetIOR()
    {
        return effectiveTargetIOR;
    }

    public synchronized LocalClientRequestDispatcher getLocalClientRequestDispatcher()
    {
        return LocalClientRequestDispatcher;
    }

    ////////////////////////////////////////////////////
    //
    // org.omg.CORBA.portable.Delegate
    //

    // REVISIT - hashCode(org.omg.CORBA.Object self)

    ////////////////////////////////////////////////////
    //
    // java.lang.Object
    //

    public synchronized int hashCode()
    {
        return targetIOR.hashCode();
    }

    ////////////////////////////////////////////////////
    //
    // Implementation
    //

    protected void createContactInfoList()
    {
        if (effectiveTargetIORContactInfoList != null) {
            return;
        }

        effectiveTargetIORContactInfoList = new ArrayList();

        IIOPProfile iiopProfile = effectiveTargetIOR.getProfile();
        String hostname =
            ((IIOPProfileTemplate)iiopProfile.getTaggedProfileTemplate())
                .getPrimaryAddress().getHost().toLowerCase();
        int    port     =
            ((IIOPProfileTemplate)iiopProfile.getTaggedProfileTemplate())
                .getPrimaryAddress().getPort();
        // For use by "sticky manager" if one is registered.
        primaryContactInfo =
            createContactInfo(SocketInfo.IIOP_CLEAR_TEXT, hostname, port);

        if (iiopProfile.isLocal()) {
            // NOTE: IMPORTANT:
            // Only do local.  The APP Server interceptors check
            // effectiveTarget.isLocal - which is determined via
            // the IOR - so if we added other addresses then
            // transactions and interceptors would not execute.
            ContactInfo contactInfo = new SharedCDRContactInfoImpl(
                orb, this, effectiveTargetIOR,
                orb.getORBData().getGIOPAddressDisposition());
            effectiveTargetIORContactInfoList.add(contactInfo);
        } else {
            addRemoteContactInfos(effectiveTargetIOR,
                                  effectiveTargetIORContactInfoList);
        }
    }

    protected void addRemoteContactInfos(
        IOR  effectiveTargetIOR,
        List effectiveTargetIORContactInfoList)
    {
        ContactInfo contactInfo;
        List socketInfos = orb.getORBData()
            .getIORToSocketInfo().getSocketInfo(effectiveTargetIOR);
        Iterator iterator = socketInfos.iterator();
        while (iterator.hasNext()) {
            SocketInfo socketInfo = (SocketInfo) iterator.next();
            String type = socketInfo.getType();
            String host = socketInfo.getHost().toLowerCase();
            int    port = socketInfo.getPort();
            contactInfo = createContactInfo(type, host, port);
            effectiveTargetIORContactInfoList.add(contactInfo);
        }
    }

    protected ContactInfo createContactInfo(String type,
                                            String hostname, int port)
    {
        return new SocketOrChannelContactInfoImpl(
            orb, this,
            // XREVISIT - See Base Line 62
            effectiveTargetIOR,
            orb.getORBData().getGIOPAddressDisposition(),
            type, hostname, port);
    }

    /**
     * setLocalSubcontract sets cached information that is set whenever
     * the effectiveTargetIOR changes.
     *
     * Note: this must be maintained accurately whether or not the ORB
     * allows local optimization, because ServantManagers in the POA
     * ALWAYS use local optimization ONLY (they do not have a remote case).
     */
    protected void setLocalSubcontract()
    {
        if (!effectiveTargetIOR.getProfile().isLocal()) {
            LocalClientRequestDispatcher = new NotLocalLocalCRDImpl();
            return;
        }

        // XXX Note that this always uses the first IIOP profile to get the
        // scid.  What about multi-profile IORs?  This should perhaps be
        // tied to the current ContactInfo in some way, together with an
        // implementation of ClientDelegate that generally prefers co-located
        // ContactInfo.  This may in fact mean that we should do this at
        // the ContactInfo level, rather than the IOR/profile level.
        int scid = effectiveTargetIOR.getProfile().getObjectKeyTemplate().
            getSubcontractId() ;
        LocalClientRequestDispatcherFactory lcsf = orb.getRequestDispatcherRegistry().getLocalClientRequestDispatcherFactory( scid ) ;
        LocalClientRequestDispatcher = lcsf.create( scid, effectiveTargetIOR ) ;
    }
}

// End of file.
