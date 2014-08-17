/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.protocol;

import java.io.IOException;
import java.util.Iterator;
import java.rmi.RemoteException;

import javax.rmi.CORBA.Util;
import javax.rmi.CORBA.Tie;

import org.omg.CORBA.COMM_FAILURE;
import org.omg.CORBA.INTERNAL;
import org.omg.CORBA.SystemException;
import org.omg.CORBA.Request;
import org.omg.CORBA.NamedValue;
import org.omg.CORBA.NVList;
import org.omg.CORBA.Context;
import org.omg.CORBA.ContextList;
import org.omg.CORBA.ExceptionList;
import org.omg.CORBA.TypeCode;
import org.omg.CORBA.portable.RemarshalException;
import org.omg.CORBA_2_3.portable.InputStream;
import org.omg.CORBA_2_3.portable.OutputStream;
import org.omg.CORBA.portable.Delegate;
import org.omg.CORBA.portable.ServantObject;
import org.omg.CORBA.portable.ApplicationException;
import org.omg.CORBA.portable.UnknownException;
import org.omg.IOP.ExceptionDetailMessage;
import org.omg.IOP.TAG_CODE_SETS;

import com.sun.org.omg.SendingContext.CodeBase;

import com.sun.corba.se.pept.broker.Broker;
import com.sun.corba.se.pept.encoding.InputObject;
import com.sun.corba.se.pept.encoding.OutputObject;
import com.sun.corba.se.pept.protocol.ClientRequestDispatcher;
import com.sun.corba.se.pept.protocol.MessageMediator;
import com.sun.corba.se.pept.transport.Connection;
import com.sun.corba.se.pept.transport.OutboundConnectionCache;
import com.sun.corba.se.pept.transport.ContactInfo;

import com.sun.corba.se.spi.ior.IOR;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion;
import com.sun.corba.se.spi.ior.iiop.IIOPProfileTemplate;
import com.sun.corba.se.spi.ior.iiop.CodeSetsComponent;
import com.sun.corba.se.spi.oa.OAInvocationInfo;
import com.sun.corba.se.spi.oa.ObjectAdapterFactory;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.orb.ORBVersion;
import com.sun.corba.se.spi.orb.ORBVersionFactory;
import com.sun.corba.se.spi.protocol.CorbaMessageMediator;
import com.sun.corba.se.spi.protocol.RequestDispatcherRegistry;
import com.sun.corba.se.spi.transport.CorbaContactInfo ;
import com.sun.corba.se.spi.transport.CorbaContactInfoList ;
import com.sun.corba.se.spi.transport.CorbaContactInfoListIterator ;
import com.sun.corba.se.spi.transport.CorbaConnection;
import com.sun.corba.se.spi.logging.CORBALogDomains;

import com.sun.corba.se.spi.servicecontext.MaxStreamFormatVersionServiceContext;
import com.sun.corba.se.spi.servicecontext.ServiceContext;
import com.sun.corba.se.spi.servicecontext.ServiceContexts;
import com.sun.corba.se.spi.servicecontext.UEInfoServiceContext;
import com.sun.corba.se.spi.servicecontext.CodeSetServiceContext;
import com.sun.corba.se.spi.servicecontext.SendingContextServiceContext;
import com.sun.corba.se.spi.servicecontext.ORBVersionServiceContext;
import com.sun.corba.se.spi.servicecontext.MaxStreamFormatVersionServiceContext;
import com.sun.corba.se.spi.servicecontext.UnknownServiceContext;

import com.sun.corba.se.impl.encoding.CDRInputObject;
import com.sun.corba.se.impl.encoding.CodeSetComponentInfo;
import com.sun.corba.se.impl.encoding.CodeSetConversion;
import com.sun.corba.se.impl.encoding.EncapsInputStream;
import com.sun.corba.se.impl.encoding.MarshalOutputStream;
import com.sun.corba.se.impl.encoding.MarshalInputStream;
import com.sun.corba.se.impl.logging.ORBUtilSystemException;
import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.impl.protocol.giopmsgheaders.ReplyMessage;
import com.sun.corba.se.impl.protocol.giopmsgheaders.KeyAddr;
import com.sun.corba.se.impl.protocol.giopmsgheaders.ProfileAddr;
import com.sun.corba.se.impl.protocol.giopmsgheaders.ReferenceAddr;
import com.sun.corba.se.impl.transport.CorbaContactInfoListIteratorImpl;
import com.sun.corba.se.impl.util.JDKBridge;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import sun.corba.EncapsInputStreamFactory;

/**
 * ClientDelegate is the RMI client-side subcontract or representation
 * It implements RMI delegate as well as our internal ClientRequestDispatcher
 * interface.
 */
public class CorbaClientRequestDispatcherImpl
    implements
        ClientRequestDispatcher
{
    private ConcurrentMap<ContactInfo, Object> locks =
            new ConcurrentHashMap<ContactInfo, Object>();

    public OutputObject beginRequest(Object self, String opName,
                                     boolean isOneWay, ContactInfo contactInfo)
    {
      ORB orb = null;
      try {
        CorbaContactInfo corbaContactInfo = (CorbaContactInfo) contactInfo;
        orb =  (ORB)contactInfo.getBroker();

        if (orb.subcontractDebugFlag) {
            dprint(".beginRequest->: op/" + opName);
        }

        //
        // Portable Interceptor initialization.
        //

        orb.getPIHandler().initiateClientPIRequest( false );

        //
        // Connection.
        //

        CorbaConnection connection = null;

        // This locking is done so that multiple connections are not created
        // for the same endpoint
        // 7046238 - Synchronization on a single monitor for contactInfo parameters
        // with identical hashCode(), so we lock on same monitor for equal parameters
        // (which can refer to equal (in terms of equals()) but not the same objects)

        Object lock = locks.get(contactInfo);

        if (lock == null) {
            Object newLock = new Object();
            lock = locks.putIfAbsent(contactInfo, newLock);
            if (lock == null) {
                lock = newLock;
            }
        }

        synchronized (lock) {
            if (contactInfo.isConnectionBased()) {
                if (contactInfo.shouldCacheConnection()) {
                    connection = (CorbaConnection)
                        orb.getTransportManager()
                        .getOutboundConnectionCache(contactInfo).get(contactInfo);
                }
                if (connection != null) {
                    if (orb.subcontractDebugFlag) {
                        dprint(".beginRequest: op/" + opName
                               + ": Using cached connection: " + connection);
                    }
                } else {
                    try {
                        connection = (CorbaConnection)
                            contactInfo.createConnection();
                        if (orb.subcontractDebugFlag) {
                            dprint(".beginRequest: op/" + opName
                                   + ": Using created connection: " + connection);
                        }
                    } catch (RuntimeException e) {
                        if (orb.subcontractDebugFlag) {
                            dprint(".beginRequest: op/" + opName
                                   + ": failed to create connection: " + e);
                        }
                        // REVISIT: this part similar to marshalingComplete below.
                        boolean retry = getContactInfoListIterator(orb)
                                           .reportException(contactInfo, e);
                        // REVISIT:
                        // this part similar to Remarshal in this method below
                        if (retry) {
                            if(getContactInfoListIterator(orb).hasNext()) {
                                contactInfo = (ContactInfo)
                                   getContactInfoListIterator(orb).next();
                                unregisterWaiter(orb);
                                return beginRequest(self, opName,
                                                    isOneWay, contactInfo);
                            } else {
                                throw e;
                            }
                        } else {
                            throw e;
                        }
                    }
                    if (connection.shouldRegisterReadEvent()) {
                        // REVISIT: cast
                        orb.getTransportManager().getSelector(0)
                            .registerForEvent(connection.getEventHandler());
                        connection.setState("ESTABLISHED");
                    }
                    // Do not do connection reclaim here since the connections
                    // are marked in use by registerWaiter() call and since this
                    // call happens later do it after that.
                    if (contactInfo.shouldCacheConnection()) {
                        OutboundConnectionCache connectionCache =
                         orb.getTransportManager()
                            .getOutboundConnectionCache(contactInfo);
                        connectionCache.stampTime(connection);
                        connectionCache.put(contactInfo, connection);
    //              connectionCache.reclaim();
                    }
                }
            }
        }

        CorbaMessageMediator messageMediator = (CorbaMessageMediator)
            contactInfo.createMessageMediator(
                orb, contactInfo, connection, opName, isOneWay);
        if (orb.subcontractDebugFlag) {
            dprint(".beginRequest: " + opAndId(messageMediator)
                   + ": created message mediator: " +  messageMediator);
        }

        // NOTE: Thread data so we can get the mediator in release reply
        // in order to remove the waiter in CorbaConnection.
        // We cannot depend on obtaining information in releaseReply
        // via its InputStream argument since, on certain errors
        // (e.g., client marshaling errors), the stream may be null.
        // Likewise for releaseReply "self".
        // NOTE: This must be done before initializing the message since
        // that may start sending fragments which may end up in "early"
        // replies or client marshaling exceptions.

        orb.getInvocationInfo().setMessageMediator(messageMediator);

        if (connection != null && connection.getCodeSetContext() == null) {
            performCodeSetNegotiation(messageMediator);
        }

        addServiceContexts(messageMediator);

        OutputObject outputObject =
            contactInfo.createOutputObject(messageMediator);
        if (orb.subcontractDebugFlag) {
            dprint(".beginRequest: " + opAndId(messageMediator)
                   + ": created output object: " + outputObject);
        }


        // NOTE: Not necessary for oneways, but useful for debugging.
        // This must be done BEFORE message initialization since fragments
        // may be sent at that time.
        registerWaiter(messageMediator);

        // Do connection reclaim now
        synchronized (lock) {
            if (contactInfo.isConnectionBased()) {
                if (contactInfo.shouldCacheConnection()) {
                    OutboundConnectionCache connectionCache =
                             orb.getTransportManager()
                                .getOutboundConnectionCache(contactInfo);
                    connectionCache.reclaim();
                }
            }
        }

        orb.getPIHandler().setClientPIInfo(messageMediator);
        try {
            // This MUST come before message is initialized so
            // service contexts may be added by PI because
            // initial fragments may be sent during message initialization.
            orb.getPIHandler().invokeClientPIStartingPoint();
        } catch( RemarshalException e ) {
            if (orb.subcontractDebugFlag) {
                dprint(".beginRequest: " + opAndId(messageMediator)
                       + ": Remarshal");
            }

            // NOTE: We get here because an interceptor raised ForwardRequest
            // and updated the IOR/Iterator.  Since we have a fresh iterator
            // hasNext should succeed.

            // REVISIT: We should feed ALL interceptor exceptions to
            // iterator.reportException so it can determine if it wants
            // to retry.  Right now, SystemExceptions will flow to the
            // client code.

            // REVISIT:
            // This assumes that interceptors update
            // ContactInfoList outside of subcontract.
            // Want to move that update to here.
            if (getContactInfoListIterator(orb).hasNext()) {
                contactInfo = (ContactInfo)getContactInfoListIterator(orb).next();
                if (orb.subcontractDebugFlag) {
                    dprint( "RemarshalException: hasNext true\ncontact info " + contactInfo );
                }

                // Fix for 6763340: Complete the first attempt before starting another.
                orb.getPIHandler().makeCompletedClientRequest(
                    ReplyMessage.LOCATION_FORWARD, null ) ;
                unregisterWaiter(orb);
                orb.getPIHandler().cleanupClientPIRequest() ;

                return beginRequest(self, opName, isOneWay, contactInfo);
            } else {
                if (orb.subcontractDebugFlag) {
                    dprint( "RemarshalException: hasNext false" );
                }
                ORBUtilSystemException wrapper =
                    ORBUtilSystemException.get(orb,
                                               CORBALogDomains.RPC_PROTOCOL);
                throw wrapper.remarshalWithNowhereToGo();
            }
        }

        messageMediator.initializeMessage();
        if (orb.subcontractDebugFlag) {
            dprint(".beginRequest: " + opAndId(messageMediator)
                   + ": initialized message");
        }

        return outputObject;

      } finally {
        if (orb.subcontractDebugFlag) {
            dprint(".beginRequest<-: op/" + opName);
        }
      }
    }

    public InputObject marshalingComplete(java.lang.Object self,
                                          OutputObject outputObject)
        throws
            ApplicationException,
            org.omg.CORBA.portable.RemarshalException
    {
        ORB orb = null;
        CorbaMessageMediator messageMediator = null;
        try {
            messageMediator = (CorbaMessageMediator)
                outputObject.getMessageMediator();

            orb = (ORB) messageMediator.getBroker();

            if (orb.subcontractDebugFlag) {
                dprint(".marshalingComplete->: " + opAndId(messageMediator));
            }

            InputObject inputObject =
                marshalingComplete1(orb, messageMediator);

            return processResponse(orb, messageMediator, inputObject);

        } finally {
            if (orb.subcontractDebugFlag) {
                dprint(".marshalingComplete<-: " + opAndId(messageMediator));
            }
        }
    }

    public InputObject marshalingComplete1(
            ORB orb, CorbaMessageMediator messageMediator)
        throws
            ApplicationException,
            org.omg.CORBA.portable.RemarshalException
    {
        try {
            messageMediator.finishSendingRequest();

            if (orb.subcontractDebugFlag) {
                dprint(".marshalingComplete: " + opAndId(messageMediator)
                       + ": finished sending request");
            }

            return messageMediator.waitForResponse();

        } catch (RuntimeException e) {

            if (orb.subcontractDebugFlag) {
                dprint(".marshalingComplete: " + opAndId(messageMediator)
                       + ": exception: " + e.toString());
            }

            boolean retry  =
                getContactInfoListIterator(orb)
                    .reportException(messageMediator.getContactInfo(), e);

            //Bug 6382377: must not lose exception in PI

            // Must run interceptor end point before retrying.
            Exception newException =
                    orb.getPIHandler().invokeClientPIEndingPoint(
                    ReplyMessage.SYSTEM_EXCEPTION, e);

            if (retry) {
                if (newException == e) {
                    continueOrThrowSystemOrRemarshal(messageMediator,
                                                     new RemarshalException());
                } else {
                    continueOrThrowSystemOrRemarshal(messageMediator,
                                                     newException);
                }
            } else {
                if (newException instanceof RuntimeException){
                    throw (RuntimeException)newException;
                }
                else if (newException instanceof RemarshalException)
                {
                    throw (RemarshalException)newException;
                }

                // NOTE: Interceptor ending point will run in releaseReply.
                throw e;
            }
            return null; // for compiler
        }
    }

    protected InputObject processResponse(ORB orb,
                                          CorbaMessageMediator messageMediator,
                                          InputObject inputObject)
        throws
            ApplicationException,
            org.omg.CORBA.portable.RemarshalException
    {
        ORBUtilSystemException wrapper =
            ORBUtilSystemException.get( orb,
                CORBALogDomains.RPC_PROTOCOL ) ;

        if (orb.subcontractDebugFlag) {
            dprint(".processResponse: " + opAndId(messageMediator)
                   + ": response received");
        }

        // We know for sure now that we've sent a message.
        // So OK to not send initial again.
        if (messageMediator.getConnection() != null) {
            ((CorbaConnection)messageMediator.getConnection())
                .setPostInitialContexts();
        }

        // NOTE: not necessary to set MessageMediator for PI.
        // It already has it.

        // Process the response.

        Exception exception = null;

        if (messageMediator.isOneWay()) {
            getContactInfoListIterator(orb)
                .reportSuccess(messageMediator.getContactInfo());
            // Invoke Portable Interceptors with receive_other
            exception = orb.getPIHandler().invokeClientPIEndingPoint(
                ReplyMessage.NO_EXCEPTION, exception );
            continueOrThrowSystemOrRemarshal(messageMediator, exception);
            return null;
        }

        consumeServiceContexts(orb, messageMediator);

        // Now that we have the service contexts processed and the
        // correct ORBVersion set, we must finish initializing the stream.
        // REVISIT - need interface for this operation.
        ((CDRInputObject)inputObject).performORBVersionSpecificInit();

        if (messageMediator.isSystemExceptionReply()) {

            SystemException se = messageMediator.getSystemExceptionReply();

            if (orb.subcontractDebugFlag) {
                dprint(".processResponse: " + opAndId(messageMediator)
                       + ": received system exception: " + se);
            }

            boolean doRemarshal =
                getContactInfoListIterator(orb)
                    .reportException(messageMediator.getContactInfo(), se);

            if (doRemarshal) {

                // Invoke Portable Interceptors with receive_exception:
                exception = orb.getPIHandler().invokeClientPIEndingPoint(
                    ReplyMessage.SYSTEM_EXCEPTION, se );

                // If PI did not change the exception, throw a
                // Remarshal.
                if( se == exception ) {
                    // exception = null is to maintain symmetry with
                    // GenericPOAClientSC.
                    exception = null;
                    continueOrThrowSystemOrRemarshal(messageMediator,
                                                     new RemarshalException());
                    throw wrapper.statementNotReachable1() ;
                } else {
                    //  Otherwise, throw the exception PI wants thrown.
                    continueOrThrowSystemOrRemarshal(messageMediator,
                                                     exception);
                    throw wrapper.statementNotReachable2() ;
                }
            }

            // No retry, so see if was unknown.

            ServiceContexts contexts =
                messageMediator.getReplyServiceContexts();
            if (contexts != null) {
                UEInfoServiceContext usc =
                    (UEInfoServiceContext)
                    contexts.get(UEInfoServiceContext.SERVICE_CONTEXT_ID);

                if (usc != null) {
                    Throwable unknown = usc.getUE() ;
                    UnknownException ue = new UnknownException(unknown);

                    // Invoke Portable Interceptors with receive_exception:
                    exception = orb.getPIHandler().invokeClientPIEndingPoint(
                        ReplyMessage.SYSTEM_EXCEPTION, ue );

                    continueOrThrowSystemOrRemarshal(messageMediator, exception);
                    throw wrapper.statementNotReachable3() ;
                }
            }

            // It was not a comm failure nor unknown.
            // This is the general case.

            // Invoke Portable Interceptors with receive_exception:
            exception = orb.getPIHandler().invokeClientPIEndingPoint(
                ReplyMessage.SYSTEM_EXCEPTION, se );

            continueOrThrowSystemOrRemarshal(messageMediator, exception);

            // Note: We should never need to execute this line, but
            // we should assert in case exception is null somehow.
            throw wrapper.statementNotReachable4() ;
        } else if (messageMediator.isUserExceptionReply()) {

            if (orb.subcontractDebugFlag) {
                dprint(".processResponse: " + opAndId(messageMediator)
                       + ": received user exception");
            }

            getContactInfoListIterator(orb)
                .reportSuccess(messageMediator.getContactInfo());

            String exceptionRepoId = peekUserExceptionId(inputObject);
            Exception newException = null;

            if (messageMediator.isDIIRequest()) {
                exception = messageMediator.unmarshalDIIUserException(
                                exceptionRepoId, (InputStream)inputObject);
                newException = orb.getPIHandler().invokeClientPIEndingPoint(
                                   ReplyMessage.USER_EXCEPTION, exception );
                messageMediator.setDIIException(newException);

            } else {
                ApplicationException appException =
                    new ApplicationException(
                        exceptionRepoId,
                        (org.omg.CORBA.portable.InputStream)inputObject);
                exception = appException;
                newException = orb.getPIHandler().invokeClientPIEndingPoint(
                                   ReplyMessage.USER_EXCEPTION, appException );
            }

            if (newException != exception) {
                continueOrThrowSystemOrRemarshal(messageMediator,newException);
            }

            if (newException instanceof ApplicationException) {
                throw (ApplicationException)newException;
            }
            // For DII:
            // This return will be ignored - already unmarshaled above.
            return inputObject;

        } else if (messageMediator.isLocationForwardReply()) {

            if (orb.subcontractDebugFlag) {
                dprint(".processResponse: " + opAndId(messageMediator)
                       + ": received location forward");
            }

            // NOTE: Expects iterator to update target IOR
            getContactInfoListIterator(orb).reportRedirect(
                (CorbaContactInfo)messageMediator.getContactInfo(),
                messageMediator.getForwardedIOR());

            // Invoke Portable Interceptors with receive_other:
            Exception newException = orb.getPIHandler().invokeClientPIEndingPoint(
                ReplyMessage.LOCATION_FORWARD, null );

            if( !(newException instanceof RemarshalException) ) {
                exception = newException;
            }

            // If PI did not change exception, throw Remarshal, else
            // throw the exception PI wants thrown.
            // KMC: GenericPOAClientSC did not check exception != null
            if( exception != null ) {
                continueOrThrowSystemOrRemarshal(messageMediator, exception);
            }
            continueOrThrowSystemOrRemarshal(messageMediator,
                                             new RemarshalException());
            throw wrapper.statementNotReachable5() ;

        } else if (messageMediator.isDifferentAddrDispositionRequestedReply()){

            if (orb.subcontractDebugFlag) {
                dprint(".processResponse: " + opAndId(messageMediator)
                       + ": received different addressing dispostion request");
            }

            // Set the desired target addressing disposition.
            getContactInfoListIterator(orb).reportAddrDispositionRetry(
                (CorbaContactInfo)messageMediator.getContactInfo(),
                messageMediator.getAddrDispositionReply());

            // Invoke Portable Interceptors with receive_other:
            Exception newException = orb.getPIHandler().invokeClientPIEndingPoint(
                ReplyMessage.NEEDS_ADDRESSING_MODE, null);

            // For consistency with corresponding code in GenericPOAClientSC:
            if( !(newException instanceof RemarshalException) ) {
                exception = newException;
            }

            // If PI did not change exception, throw Remarshal, else
            // throw the exception PI wants thrown.
            // KMC: GenericPOAClientSC did not include exception != null check
            if( exception != null ) {
                continueOrThrowSystemOrRemarshal(messageMediator, exception);
            }
            continueOrThrowSystemOrRemarshal(messageMediator,
                                             new RemarshalException());
            throw wrapper.statementNotReachable6() ;
        } else /* normal response */ {

            if (orb.subcontractDebugFlag) {
                dprint(".processResponse: " + opAndId(messageMediator)
                       + ": received normal response");
            }

            getContactInfoListIterator(orb)
                .reportSuccess(messageMediator.getContactInfo());

            messageMediator.handleDIIReply((InputStream)inputObject);

            // Invoke Portable Interceptors with receive_reply:
            exception = orb.getPIHandler().invokeClientPIEndingPoint(
                ReplyMessage.NO_EXCEPTION, null );

            // Remember: not thrown if exception is null.
            continueOrThrowSystemOrRemarshal(messageMediator, exception);

            return inputObject;
        }
    }

    // Filters the given exception into a SystemException or a
    // RemarshalException and throws it.  Assumes the given exception is
    // of one of these two types.  This is a utility method for
    // the above invoke code which must do this numerous times.
    // If the exception is null, no exception is thrown.
    //
    // Note that this code is duplicated in GenericPOAClientSC.java
    protected void continueOrThrowSystemOrRemarshal(
        CorbaMessageMediator messageMediator, Exception exception)
        throws
            SystemException, RemarshalException
    {

        ORB orb = (ORB) messageMediator.getBroker();

        if( exception == null ) {

            // do nothing.

        } else if( exception instanceof RemarshalException ) {

            // REVISIT - unify with PI handling
            orb.getInvocationInfo().setIsRetryInvocation(true);

            // NOTE - We must unregister the waiter NOW for this request
            // since the retry will result in a new request id.  Therefore
            // the old request id would be lost and we would have a memory
            // leak in the responseWaitingRoom.
            unregisterWaiter(orb);

            if (orb.subcontractDebugFlag) {
                dprint(".continueOrThrowSystemOrRemarshal: "
                       + opAndId(messageMediator)
                       + ": throwing Remarshal");
            }

            throw (RemarshalException)exception;

        } else {

            if (orb.subcontractDebugFlag) {
                dprint(".continueOrThrowSystemOrRemarshal: "
                       + opAndId(messageMediator)
                       + ": throwing sex:"
                       + exception);
            }

            throw (SystemException)exception;
        }
    }

    protected CorbaContactInfoListIterator  getContactInfoListIterator(ORB orb)
    {
        return (CorbaContactInfoListIterator)
            ((CorbaInvocationInfo)orb.getInvocationInfo())
                .getContactInfoListIterator();
    }

    protected void registerWaiter(CorbaMessageMediator messageMediator)
    {
        if (messageMediator.getConnection() != null) {
            messageMediator.getConnection().registerWaiter(messageMediator);
        }
    }

    protected void unregisterWaiter(ORB orb)
    {
        MessageMediator messageMediator =
            orb.getInvocationInfo().getMessageMediator();
        if (messageMediator!=null && messageMediator.getConnection() != null) {
            // REVISIT:
            // The messageMediator may be null if COMM_FAILURE before
            // it is created.
            messageMediator.getConnection().unregisterWaiter(messageMediator);
        }
    }

    protected void addServiceContexts(CorbaMessageMediator messageMediator)
    {
        ORB orb = (ORB)messageMediator.getBroker();
        CorbaConnection c = (CorbaConnection) messageMediator.getConnection();
        GIOPVersion giopVersion = messageMediator.getGIOPVersion();

        ServiceContexts contexts = messageMediator.getRequestServiceContexts();

        addCodeSetServiceContext(c, contexts, giopVersion);

        // Add the RMI-IIOP max stream format version
        // service context to every request.  Once we have GIOP 1.3,
        // we could skip it since we now support version 2, but
        // probably safer to always send it.
        contexts.put(MaxStreamFormatVersionServiceContext.singleton);

        // ORBVersion servicecontext needs to be sent
        ORBVersionServiceContext ovsc = new ORBVersionServiceContext(
                        ORBVersionFactory.getORBVersion() ) ;
        contexts.put( ovsc ) ;

        // NOTE : We only want to send the runtime context the first time
        if ((c != null) && !c.isPostInitialContexts()) {
            // Do not do c.setPostInitialContexts() here.
            // If a client interceptor send_request does a ForwardRequest
            // which ends up using the same connection then the service
            // context would not be sent.
            SendingContextServiceContext scsc =
                new SendingContextServiceContext( orb.getFVDCodeBaseIOR() ) ; //d11638
            contexts.put( scsc ) ;
        }
    }

    protected void consumeServiceContexts(ORB orb,
                                        CorbaMessageMediator messageMediator)
    {
        ServiceContexts ctxts = messageMediator.getReplyServiceContexts();
        ServiceContext sc ;
        ORBUtilSystemException wrapper = ORBUtilSystemException.get( orb,
                CORBALogDomains.RPC_PROTOCOL ) ;

        if (ctxts == null) {
            return; // no service context available, return gracefully.
        }

        sc = ctxts.get( SendingContextServiceContext.SERVICE_CONTEXT_ID ) ;

        if (sc != null) {
            SendingContextServiceContext scsc =
                (SendingContextServiceContext)sc ;
            IOR ior = scsc.getIOR() ;

            try {
                // set the codebase returned by the server
                if (messageMediator.getConnection() != null) {
                    ((CorbaConnection)messageMediator.getConnection()).setCodeBaseIOR(ior);
                }
            } catch (ThreadDeath td) {
                throw td ;
            } catch (Throwable t) {
                throw wrapper.badStringifiedIor( t ) ;
            }
        }

        // see if the version subcontract is present, if yes, then set
        // the ORBversion
        sc = ctxts.get( ORBVersionServiceContext.SERVICE_CONTEXT_ID ) ;

        if (sc != null) {
            ORBVersionServiceContext ovsc =
               (ORBVersionServiceContext) sc;

            ORBVersion version = ovsc.getVersion();
            orb.setORBVersion( version ) ;
        }

        getExceptionDetailMessage(messageMediator, wrapper);
    }

    protected void getExceptionDetailMessage(
        CorbaMessageMediator  messageMediator,
        ORBUtilSystemException wrapper)
    {
        ServiceContext sc = messageMediator.getReplyServiceContexts()
            .get(ExceptionDetailMessage.value);
        if (sc == null)
            return ;

        if (! (sc instanceof UnknownServiceContext)) {
            throw wrapper.badExceptionDetailMessageServiceContextType();
        }
        byte[] data = ((UnknownServiceContext)sc).getData();
        EncapsInputStream in =
                EncapsInputStreamFactory.newEncapsInputStream((ORB)messageMediator.getBroker(),
                                      data, data.length);
        in.consumeEndian();

        String msg =
              "----------BEGIN server-side stack trace----------\n"
            + in.read_wstring() + "\n"
            + "----------END server-side stack trace----------";

        messageMediator.setReplyExceptionDetailMessage(msg);
    }

    public void endRequest(Broker broker, Object self, InputObject inputObject)
    {
        ORB orb = (ORB)broker ;

        try {
            if (orb.subcontractDebugFlag) {
                dprint(".endRequest->");
            }

            // Note: the inputObject may be null if an error occurs
            //       in request or before _invoke returns.
            // Note: self may be null also (e.g., compiler generates null in stub).

            MessageMediator messageMediator =
                orb.getInvocationInfo().getMessageMediator();
            if (messageMediator != null)
            {
                if (messageMediator.getConnection() != null)
                {
                    ((CorbaMessageMediator)messageMediator)
                              .sendCancelRequestIfFinalFragmentNotSent();
                }

                // Release any outstanding NIO ByteBuffers to the ByteBufferPool

                InputObject inputObj = messageMediator.getInputObject();
                if (inputObj != null) {
                    inputObj.close();
                }

                OutputObject outputObj = messageMediator.getOutputObject();
                if (outputObj != null) {
                    outputObj.close();
                }

            }

            // XREVISIT NOTE - Assumes unregistering the waiter for
            // location forwards has already happened somewhere else.
            // The code below is only going to unregister the final successful
            // request.

            // NOTE: In the case of a recursive stack of endRequests in a
            // finally block (because of Remarshal) only the first call to
            // unregisterWaiter will remove the waiter.  The rest will be
            // noops.
            unregisterWaiter(orb);

            // Invoke Portable Interceptors cleanup.  This is done to handle
            // exceptions during stream marshaling.  More generally, exceptions
            // that occur in the ORB after send_request (which includes
            // after returning from _request) before _invoke:
            orb.getPIHandler().cleanupClientPIRequest();

            // REVISIT: Early replies?
        } catch (IOException ex) {
            // See CDRInput/OutputObject.close() for more info.
            // This won't result in a Corba error if an IOException happens.
            if (orb.subcontractDebugFlag)
            {
                dprint(".endRequest: ignoring IOException - " + ex.toString());
            }
        } finally {
            if (orb.subcontractDebugFlag) {
                dprint(".endRequest<-");
            }
        }
    }


    protected void performCodeSetNegotiation(CorbaMessageMediator messageMediator)
    {
        CorbaConnection conn =
            (CorbaConnection) messageMediator.getConnection();
        IOR ior =
            ((CorbaContactInfo)messageMediator.getContactInfo())
            .getEffectiveTargetIOR();
        GIOPVersion giopVersion = messageMediator.getGIOPVersion();

        // XXX This seems to be a broken double checked locking idiom: FIX IT!

        // conn.getCodeSetContext() is null when no other requests have
        // been made on this connection to trigger code set negotation.
        if (conn != null &&
            conn.getCodeSetContext() == null &&
            !giopVersion.equals(GIOPVersion.V1_0)) {

            synchronized(conn) {
                // Double checking.  Don't let any other
                // threads use this connection until the
                // code sets are straight.
                if (conn.getCodeSetContext() != null)
                    return;

                // This only looks at the first code set component.  If
                // there can be multiple locations with multiple code sets,
                // this requires more work.
                IIOPProfileTemplate temp =
                    (IIOPProfileTemplate)ior.getProfile().
                    getTaggedProfileTemplate();
                Iterator iter = temp.iteratorById(TAG_CODE_SETS.value);
                if (!iter.hasNext()) {
                    // Didn't have a code set component.  The default will
                    // be to use ISO8859-1 for char data and throw an
                    // exception if wchar data is used.
                    return;
                }

                // Get the native and conversion code sets the
                // server specified in its IOR
                CodeSetComponentInfo serverCodeSets
                    = ((CodeSetsComponent)iter.next()).getCodeSetComponentInfo();

                // Perform the negotiation between this ORB's code sets and
                // the ones from the IOR
                CodeSetComponentInfo.CodeSetContext result
                    = CodeSetConversion.impl().negotiate(
                          conn.getBroker().getORBData().getCodeSetComponentInfo(),
                          serverCodeSets);

                conn.setCodeSetContext(result);
            }
        }
    }

    protected void addCodeSetServiceContext(CorbaConnection conn,
                                          ServiceContexts ctxs,
                                          GIOPVersion giopVersion) {

        // REVISIT.  OMG issue 3318 concerning sending the code set
        // service context more than once was deemed too much for the
        // RTF.  Here's our strategy for the moment:
        //
        // Send it on every request (necessary in cases of fragmentation
        // with multithreaded clients or when the first thing on a
        // connection is a LocateRequest).  Provide an ORB property
        // to disable multiple sends.
        //
        // Note that the connection is null in the local case and no
        // service context is included.  We use the ORB provided
        // encapsulation streams.
        //
        // Also, there will be no negotiation or service context
        // in GIOP 1.0.  ISO8859-1 is used for char/string, and
        // wchar/wstring are illegal.
        //
        if (giopVersion.equals(GIOPVersion.V1_0) || conn == null)
            return;

        CodeSetComponentInfo.CodeSetContext codeSetCtx = null;

        if (conn.getBroker().getORBData().alwaysSendCodeSetServiceContext() ||
            !conn.isPostInitialContexts()) {

            // Get the negotiated code sets (if any) out of the connection
            codeSetCtx = conn.getCodeSetContext();
        }

        // Either we shouldn't send the code set service context, or
        // for some reason, the connection doesn't have its code sets.
        // Perhaps the server didn't include them in the IOR.  Uses
        // ISO8859-1 for char and makes wchar/wstring illegal.
        if (codeSetCtx == null)
            return;

        CodeSetServiceContext cssc = new CodeSetServiceContext(codeSetCtx);
        ctxs.put(cssc);
    }

    protected String peekUserExceptionId(InputObject inputObject)
    {
        CDRInputObject cdrInputObject = (CDRInputObject) inputObject;
        // REVISIT - need interface for mark/reset
        cdrInputObject.mark(Integer.MAX_VALUE);
        String result = cdrInputObject.read_string();
        cdrInputObject.reset();
        return result;
    }

    protected void dprint(String msg)
    {
        ORBUtility.dprint("CorbaClientRequestDispatcherImpl", msg);
    }

    protected String opAndId(CorbaMessageMediator mediator)
    {
        return ORBUtility.operationNameAndRequestId(mediator);
    }
}

// End of file.
