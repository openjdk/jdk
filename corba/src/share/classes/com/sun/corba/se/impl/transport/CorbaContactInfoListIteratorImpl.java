/*
 * Copyright (c) 2002, 2004, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Iterator;
import java.util.List;

import org.omg.CORBA.COMM_FAILURE;
import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.INTERNAL;
import org.omg.CORBA.SystemException;

import com.sun.corba.se.pept.transport.ContactInfo ;
import com.sun.corba.se.pept.transport.ContactInfoList ;

import com.sun.corba.se.spi.ior.IOR ;
import com.sun.corba.se.spi.logging.CORBALogDomains;
import com.sun.corba.se.spi.orb.ORB ;
import com.sun.corba.se.spi.transport.CorbaContactInfo;
import com.sun.corba.se.spi.transport.CorbaContactInfoList;
import com.sun.corba.se.spi.transport.CorbaContactInfoListIterator;
import com.sun.corba.se.spi.transport.IIOPPrimaryToContactInfo;

import com.sun.corba.se.impl.logging.ORBUtilSystemException;
import com.sun.corba.se.impl.protocol.CorbaInvocationInfo;

// REVISIT: create a unit test for this class.

public class CorbaContactInfoListIteratorImpl
    implements
        CorbaContactInfoListIterator
{
    protected ORB orb;
    protected CorbaContactInfoList contactInfoList;
    protected CorbaContactInfo successContactInfo;
    protected CorbaContactInfo failureContactInfo;
    protected RuntimeException failureException;

    // ITERATOR state
    protected Iterator effectiveTargetIORIterator;
    protected CorbaContactInfo previousContactInfo;
    protected boolean isAddrDispositionRetry;
    protected IIOPPrimaryToContactInfo primaryToContactInfo;
    protected ContactInfo primaryContactInfo;
    protected List listOfContactInfos;
    // End ITERATOR state

    public CorbaContactInfoListIteratorImpl(
        ORB orb,
        CorbaContactInfoList corbaContactInfoList,
        ContactInfo primaryContactInfo,
        List listOfContactInfos)
    {
        this.orb = orb;
        this.contactInfoList = corbaContactInfoList;
        this.primaryContactInfo = primaryContactInfo;
        if (listOfContactInfos != null) {
            // listOfContactInfos is null when used by the legacy
            // socket factory.  In that case this iterator is NOT used.
            this.effectiveTargetIORIterator = listOfContactInfos.iterator();
        }
        // List is immutable so no need to synchronize access.
        this.listOfContactInfos = listOfContactInfos;

        this.previousContactInfo = null;
        this.isAddrDispositionRetry = false;

        this.successContactInfo = null;
        this.failureContactInfo = null;
        this.failureException = null;

        primaryToContactInfo = orb.getORBData().getIIOPPrimaryToContactInfo();
    }

    ////////////////////////////////////////////////////
    //
    // java.util.Iterator
    //

    public boolean hasNext()
    {
        // REVISIT: Implement as internal closure iterator which would
        // wraps sticky or default.  Then hasNext and next just call
        // the closure.

        if (isAddrDispositionRetry) {
            return true;
        }

        boolean result;

        if (primaryToContactInfo != null) {
            result = primaryToContactInfo.hasNext(primaryContactInfo,
                                                  previousContactInfo,
                                                  listOfContactInfos);
        } else {
            result = effectiveTargetIORIterator.hasNext();
        }

        return result;
    }

    public Object next()
    {
        if (isAddrDispositionRetry) {
            isAddrDispositionRetry = false;
            return previousContactInfo;
        }

        // We hold onto the last in case we get an addressing
        // disposition retry.  Then we use it again.

        // We also hold onto it for the sticky manager.

        if (primaryToContactInfo != null) {
            previousContactInfo = (CorbaContactInfo)
                primaryToContactInfo.next(primaryContactInfo,
                                          previousContactInfo,
                                          listOfContactInfos);
        } else {
            previousContactInfo = (CorbaContactInfo)
                effectiveTargetIORIterator.next();
        }

        return previousContactInfo;
    }

    public void remove()
    {
        throw new UnsupportedOperationException();
    }

    ////////////////////////////////////////////////////
    //
    // com.sun.corba.se.pept.transport.ContactInfoListIterator
    //

    public ContactInfoList getContactInfoList()
    {
        return contactInfoList;
    }

    public void reportSuccess(ContactInfo contactInfo)
    {
        this.successContactInfo = (CorbaContactInfo)contactInfo;
    }

    public boolean reportException(ContactInfo contactInfo,
                                   RuntimeException ex)
    {
        this.failureContactInfo = (CorbaContactInfo)contactInfo;
        this.failureException = ex;
        if (ex instanceof COMM_FAILURE) {
            SystemException se = (SystemException) ex;
            if (se.completed == CompletionStatus.COMPLETED_NO) {
                if (hasNext()) {
                    return true;
                }
                if (contactInfoList.getEffectiveTargetIOR() !=
                    contactInfoList.getTargetIOR())
                {
                    // retry from root ior
                    updateEffectiveTargetIOR(contactInfoList.getTargetIOR());
                    return true;
                }
            }
        }
        return false;
    }

    public RuntimeException getFailureException()
    {
        if (failureException == null) {
            return
                ORBUtilSystemException.get( orb,
                                            CORBALogDomains.RPC_TRANSPORT )
                    .invalidContactInfoListIteratorFailureException();
        } else {
            return failureException;
        }
    }

    ////////////////////////////////////////////////////
    //
    // spi.CorbaContactInfoListIterator
    //

    public void reportAddrDispositionRetry(CorbaContactInfo contactInfo,
                                           short disposition)
    {
        previousContactInfo.setAddressingDisposition(disposition);
        isAddrDispositionRetry = true;
    }

    public void reportRedirect(CorbaContactInfo contactInfo,
                               IOR forwardedIOR)
    {
        updateEffectiveTargetIOR(forwardedIOR);
    }

    ////////////////////////////////////////////////////
    //
    // Implementation.
    //

    //
    // REVISIT:
    //
    // The normal operation for a standard iterator is to throw
    // ConcurrentModificationException whenever the underlying collection
    // changes.  This is implemented by keeping a modification counter (the
    // timestamp may fail because the granularity is too coarse).
    // Essentially what you need to do is whenever the iterator fails this
    // way, go back to ContactInfoList and get a new iterator.
    //
    // Need to update CorbaClientRequestDispatchImpl to catch and use
    // that exception.
    //

    public void updateEffectiveTargetIOR(IOR newIOR)
    {
        contactInfoList.setEffectiveTargetIOR(newIOR);
        // If we report the exception in _request (i.e., beginRequest
        // we cannot throw RemarshalException to the stub because _request
        // does not declare that exception.
        // To keep the two-level dispatching (first level chooses ContactInfo,
        // second level is specific to that ContactInfo/EPT) we need to
        // ensure that the request dispatchers get their iterator from the
        // InvocationStack (i.e., ThreadLocal). That way if the list iterator
        // needs a complete update it happens right here.
        ((CorbaInvocationInfo)orb.getInvocationInfo())
            .setContactInfoListIterator(contactInfoList.iterator());
    }
}

// End of file.
