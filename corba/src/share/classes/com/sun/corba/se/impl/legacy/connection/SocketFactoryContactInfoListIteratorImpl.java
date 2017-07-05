/*
 * Copyright 2003-2004 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.corba.se.impl.legacy.connection;

import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.SystemException;

import com.sun.corba.se.pept.transport.ContactInfo;

import com.sun.corba.se.spi.legacy.connection.GetEndPointInfoAgainException;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.transport.CorbaContactInfo;
import com.sun.corba.se.spi.transport.CorbaContactInfoList;
import com.sun.corba.se.spi.transport.SocketInfo;

import com.sun.corba.se.impl.transport.CorbaContactInfoListIteratorImpl;
import com.sun.corba.se.impl.transport.SharedCDRContactInfoImpl;

public class SocketFactoryContactInfoListIteratorImpl
    extends CorbaContactInfoListIteratorImpl
{
    private SocketInfo socketInfoCookie;

    public SocketFactoryContactInfoListIteratorImpl(
        ORB orb,
        CorbaContactInfoList corbaContactInfoList)
    {
        super(orb, corbaContactInfoList, null, null);
    }

    ////////////////////////////////////////////////////
    //
    // java.util.Iterator
    //

    public boolean hasNext()
    {
        return true;
    }

    public Object next()
    {
        if (contactInfoList.getEffectiveTargetIOR().getProfile().isLocal()){
            return new SharedCDRContactInfoImpl(
                orb, contactInfoList,
                contactInfoList.getEffectiveTargetIOR(),
                orb.getORBData().getGIOPAddressDisposition());
        } else {
            // REVISIT:
            // on comm_failure maybe need to give IOR instead of located.
            return new SocketFactoryContactInfoImpl(
                orb, contactInfoList,
                contactInfoList.getEffectiveTargetIOR(),
                orb.getORBData().getGIOPAddressDisposition(),
                socketInfoCookie);
        }
    }

    ////////////////////////////////////////////////////
    //
    // pept.ContactInfoListIterator
    //

    public boolean reportException(ContactInfo contactInfo,
                                   RuntimeException ex)
    {
        this.failureContactInfo = (CorbaContactInfo)contactInfo;
        this.failureException = ex;
        if (ex instanceof org.omg.CORBA.COMM_FAILURE) {

            if (ex.getCause() instanceof GetEndPointInfoAgainException) {
                socketInfoCookie =
                    ((GetEndPointInfoAgainException) ex.getCause())
                    .getEndPointInfo();
                return true;
            }

            SystemException se = (SystemException) ex;
            if (se.completed == CompletionStatus.COMPLETED_NO) {
                if (contactInfoList.getEffectiveTargetIOR() !=
                    contactInfoList.getTargetIOR())
                {
                    // retry from root ior
                    contactInfoList.setEffectiveTargetIOR(
                        contactInfoList.getTargetIOR());
                    return true;
                }
            }
        }
        return false;
    }
}

// End of file.
