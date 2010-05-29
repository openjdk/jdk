/*
 * Copyright (c) 1998, 2004, Oracle and/or its affiliates. All rights reserved.
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

import org.omg.PortableServer.Servant ;

import org.omg.CORBA.SystemException;
import org.omg.CORBA.INTERNAL;
import org.omg.CORBA.UNKNOWN;
import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.Any;

import org.omg.CORBA.portable.InvokeHandler;
import org.omg.CORBA.portable.InputStream;
import org.omg.CORBA.portable.OutputStream;
import org.omg.CORBA.portable.UnknownException;
import org.omg.CORBA.portable.ResponseHandler;

import com.sun.org.omg.SendingContext.CodeBase;

import com.sun.corba.se.pept.encoding.OutputObject;
import com.sun.corba.se.pept.protocol.MessageMediator;

import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.orb.ORBVersion;
import com.sun.corba.se.spi.orb.ORBVersionFactory;
import com.sun.corba.se.spi.ior.IOR ;
import com.sun.corba.se.spi.ior.ObjectKey;
import com.sun.corba.se.spi.ior.ObjectKeyTemplate;
import com.sun.corba.se.spi.ior.ObjectAdapterId;
import com.sun.corba.se.spi.oa.ObjectAdapterFactory;
import com.sun.corba.se.spi.oa.ObjectAdapter;
import com.sun.corba.se.spi.oa.OAInvocationInfo;
import com.sun.corba.se.spi.oa.OADestroyed;
import com.sun.corba.se.spi.oa.NullServant;
import com.sun.corba.se.spi.protocol.CorbaMessageMediator;
import com.sun.corba.se.spi.protocol.CorbaServerRequestDispatcher;
import com.sun.corba.se.spi.protocol.ForwardException ;
import com.sun.corba.se.spi.protocol.RequestDispatcherRegistry;
import com.sun.corba.se.spi.transport.CorbaConnection;
import com.sun.corba.se.spi.logging.CORBALogDomains;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion;

import com.sun.corba.se.impl.protocol.SpecialMethod ;
import com.sun.corba.se.spi.servicecontext.ServiceContext;
import com.sun.corba.se.spi.servicecontext.ServiceContexts;
import com.sun.corba.se.spi.servicecontext.UEInfoServiceContext;
import com.sun.corba.se.spi.servicecontext.CodeSetServiceContext;
import com.sun.corba.se.spi.servicecontext.SendingContextServiceContext;
import com.sun.corba.se.spi.servicecontext.ORBVersionServiceContext;

import com.sun.corba.se.impl.corba.ServerRequestImpl ;
import com.sun.corba.se.impl.encoding.MarshalInputStream;
import com.sun.corba.se.impl.encoding.MarshalOutputStream;
import com.sun.corba.se.impl.encoding.CodeSetComponentInfo;
import com.sun.corba.se.impl.encoding.OSFCodeSetRegistry;
import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.impl.protocol.RequestCanceledException;
import com.sun.corba.se.impl.logging.ORBUtilSystemException;
import com.sun.corba.se.impl.logging.POASystemException;

public class CorbaServerRequestDispatcherImpl
    implements CorbaServerRequestDispatcher
{
    protected ORB orb; // my ORB instance
    private ORBUtilSystemException wrapper ;
    private POASystemException poaWrapper ;

    // Added from last version because it broke the build - RTW
    // XXX remove me and rebuild: probably no longer needed
    // public static final int UNKNOWN_EXCEPTION_INFO_ID = 9;

    public CorbaServerRequestDispatcherImpl(ORB orb)
    {
        this.orb = orb;
        wrapper = ORBUtilSystemException.get( orb,
            CORBALogDomains.RPC_PROTOCOL ) ;
        poaWrapper = POASystemException.get( orb,
            CORBALogDomains.RPC_PROTOCOL ) ;
    }

    /** XXX/REVISIT:
     * We do not want to look for a servant in the POA/ServantManager case,
     * but we could in most other cases.  The OA could have a method that
     * returns true if the servant MAY exist, and false only if the servant
     * definitely DOES NOT exist.
     *
     * XXX/REVISIT:
     * We may wish to indicate OBJECT_HERE by some mechanism other than
     * returning a null result.
     *
     * Called from ORB.locate when a LocateRequest arrives.
     * Result is not always absolutely correct: may indicate OBJECT_HERE
     * for non-existent objects, which is resolved on invocation.  This
     * "bug" is unavoidable, since in general the object may be destroyed
     * between a locate and a request.  Note that this only checks that
     * the appropriate ObjectAdapter is available, not that the servant
     * actually exists.
     * Need to signal one of OBJECT_HERE, OBJECT_FORWARD, OBJECT_NOT_EXIST.
     * @return Result is null if object is (possibly) implemented here, otherwise
     * an IOR indicating objref to forward the request to.
     * @exception OBJECT_NOT_EXIST is thrown if we know the object does not
     * exist here, and we are not forwarding.
     */
    public IOR locate(ObjectKey okey)
    {
        try {
            if (orb.subcontractDebugFlag)
                dprint(".locate->");

            ObjectKeyTemplate oktemp = okey.getTemplate() ;

            try {
                checkServerId(okey);
            } catch (ForwardException fex) {
                return fex.getIOR() ;
            }

            // Called only for its side-effect of throwing appropriate exceptions
            findObjectAdapter(oktemp);

            return null ;
        } finally {
            if (orb.subcontractDebugFlag)
                dprint(".locate<-");
        }
    }

    public void dispatch(MessageMediator messageMediator)
    {
        CorbaMessageMediator request = (CorbaMessageMediator) messageMediator;
        try {
            if (orb.subcontractDebugFlag) {
                dprint(".dispatch->: " + opAndId(request));
            }

            // to set the codebase information, if any transmitted; and also
            // appropriate ORB Version.
            consumeServiceContexts(request);

            // Now that we have the service contexts processed and the
            // correct ORBVersion set, we must finish initializing the
            // stream.
            ((MarshalInputStream)request.getInputObject())
                .performORBVersionSpecificInit();

            ObjectKey okey = request.getObjectKey();

            // Check that this server is the right server
            try {
                checkServerId(okey);
            } catch (ForwardException fex) {
                if (orb.subcontractDebugFlag) {
                    dprint(".dispatch: " + opAndId(request)
                           + ": bad server id");
                }

                request.getProtocolHandler()
                    .createLocationForward(request, fex.getIOR(), null);
                return;
            }

            String operation = request.getOperationName();
            ObjectAdapter objectAdapter = null ;

            try {
                byte[] objectId = okey.getId().getId() ;
                ObjectKeyTemplate oktemp = okey.getTemplate() ;
                objectAdapter = findObjectAdapter(oktemp);

                java.lang.Object servant = getServantWithPI(request, objectAdapter,
                    objectId, oktemp, operation);

                dispatchToServant(servant, request, objectId, objectAdapter);
            } catch (ForwardException ex) {
                if (orb.subcontractDebugFlag) {
                    dprint(".dispatch: " + opAndId(request)
                           + ": ForwardException caught");
                }

                // Thrown by Portable Interceptors from InterceptorInvoker,
                // through Response constructor.
                request.getProtocolHandler()
                    .createLocationForward(request, ex.getIOR(), null);
            } catch (OADestroyed ex) {
                if (orb.subcontractDebugFlag) {
                    dprint(".dispatch: " + opAndId(request)
                           + ": OADestroyed exception caught");
                }

                // DO NOT CALL THIS HERE:
                // releaseServant(objectAdapter);
                // The problem is that OADestroyed is only thrown by oa.enter, in
                // which case oa.exit should NOT be called, and neither should
                // the invocationInfo stack be popped.

                // Destroyed POAs can be recreated by normal adapter activation.
                // So just restart the dispatch.
                dispatch(request);
            } catch (RequestCanceledException ex) {
                if (orb.subcontractDebugFlag) {
                    dprint(".dispatch: " + opAndId(request)
                           + ": RequestCanceledException caught");
                }

                // IDLJ generated non-tie based skeletons do not catch the
                // RequestCanceledException. Rethrow the exception, which will
                // cause the worker thread to unwind the dispatch and wait for
                // other requests.
                throw ex;
            } catch (UnknownException ex) {
                if (orb.subcontractDebugFlag) {
                    dprint(".dispatch: " + opAndId(request)
                           + ": UnknownException caught " + ex);
                }

                // RMIC generated tie skeletons convert all Throwable exception
                // types (including RequestCanceledException, ThreadDeath)
                // thrown during reading fragments into UnknownException.
                // If RequestCanceledException was indeed raised,
                // then rethrow it, which will eventually cause the worker
                // thread to unstack the dispatch and wait for other requests.
                if (ex.originalEx instanceof RequestCanceledException) {
                    throw (RequestCanceledException) ex.originalEx;
                }

                ServiceContexts contexts = new ServiceContexts(orb);
                UEInfoServiceContext usc = new UEInfoServiceContext(
                    ex.originalEx);

                contexts.put( usc ) ;

                SystemException sysex = wrapper.unknownExceptionInDispatch(
                        CompletionStatus.COMPLETED_MAYBE, ex ) ;
                request.getProtocolHandler()
                    .createSystemExceptionResponse(request, sysex,
                        contexts);
            } catch (Throwable ex) {
                if (orb.subcontractDebugFlag) {
                    dprint(".dispatch: " + opAndId(request)
                           + ": other exception " + ex);
                }
                request.getProtocolHandler()
                    .handleThrowableDuringServerDispatch(
                        request, ex, CompletionStatus.COMPLETED_MAYBE);
            }
            return;
        } finally {
            if (orb.subcontractDebugFlag) {
                dprint(".dispatch<-: " + opAndId(request));
            }
        }
    }

    private void releaseServant(ObjectAdapter objectAdapter)
    {
        try {
            if (orb.subcontractDebugFlag) {
                dprint(".releaseServant->");
            }

            if (objectAdapter == null) {
                if (orb.subcontractDebugFlag) {
                    dprint(".releaseServant: null object adapter");
                }
                return ;
            }

            try {
                objectAdapter.returnServant();
            } finally {
                objectAdapter.exit();
                orb.popInvocationInfo() ;
            }
        } finally {
            if (orb.subcontractDebugFlag) {
                dprint(".releaseServant<-");
            }
        }
    }

    // Note that objectAdapter.enter() must be called before getServant.
    private java.lang.Object getServant(ObjectAdapter objectAdapter, byte[] objectId,
        String operation)
        throws OADestroyed
    {
        try {
            if (orb.subcontractDebugFlag) {
                dprint(".getServant->");
            }

            OAInvocationInfo info = objectAdapter.makeInvocationInfo(objectId);
            info.setOperation(operation);
            orb.pushInvocationInfo(info);
            objectAdapter.getInvocationServant(info);
            return info.getServantContainer() ;
        } finally {
            if (orb.subcontractDebugFlag) {
                dprint(".getServant<-");
            }
        }
    }

    protected java.lang.Object getServantWithPI(CorbaMessageMediator request,
                                                 ObjectAdapter objectAdapter,
        byte[] objectId, ObjectKeyTemplate oktemp, String operation)
        throws OADestroyed
    {
        try {
            if (orb.subcontractDebugFlag) {
                dprint(".getServantWithPI->");
            }

            // Prepare Portable Interceptors for a new server request
            // and invoke receive_request_service_contexts.  The starting
            // point may throw a SystemException or ForwardException.
            orb.getPIHandler().initializeServerPIInfo(request, objectAdapter,
                objectId, oktemp);
            orb.getPIHandler().invokeServerPIStartingPoint();

            objectAdapter.enter() ;

            // This must be set just after the enter so that exceptions thrown by
            // enter do not cause
            // the exception reply to pop the thread stack and do an extra oa.exit.
            if (request != null)
                request.setExecuteReturnServantInResponseConstructor(true);

            java.lang.Object servant = getServant(objectAdapter, objectId,
                operation);

            // Note: we do not know the MDI on a null servant.
            // We only end up in that situation if _non_existent called,
            // so that the following handleNullServant call does not throw an
            // exception.
            String mdi = "unknown" ;

            if (servant instanceof NullServant)
                handleNullServant(operation, (NullServant)servant);
            else
                mdi = objectAdapter.getInterfaces(servant, objectId)[0] ;

            orb.getPIHandler().setServerPIInfo(servant, mdi);

            if (((servant != null) &&
                !(servant instanceof org.omg.CORBA.DynamicImplementation) &&
                !(servant instanceof org.omg.PortableServer.DynamicImplementation)) ||
                (SpecialMethod.getSpecialMethod(operation) != null)) {
                orb.getPIHandler().invokeServerPIIntermediatePoint();
            }

            return servant ;
        } finally {
            if (orb.subcontractDebugFlag) {
                dprint(".getServantWithPI<-");
            }
        }
    }

    protected void checkServerId(ObjectKey okey)
    {
        try {
            if (orb.subcontractDebugFlag) {
                dprint(".checkServerId->");
            }

            ObjectKeyTemplate oktemp = okey.getTemplate() ;
            int sId = oktemp.getServerId() ;
            int scid = oktemp.getSubcontractId() ;

            if (!orb.isLocalServerId(scid, sId)) {
                if (orb.subcontractDebugFlag) {
                    dprint(".checkServerId: bad server id");
                }

                orb.handleBadServerId(okey);
            }
        } finally {
            if (orb.subcontractDebugFlag) {
                dprint(".checkServerId<-");
            }
        }
    }

    private ObjectAdapter findObjectAdapter(ObjectKeyTemplate oktemp)
    {
        try {
            if (orb.subcontractDebugFlag) {
                dprint(".findObjectAdapter->");
            }

            RequestDispatcherRegistry scr = orb.getRequestDispatcherRegistry() ;
            int scid = oktemp.getSubcontractId() ;
            ObjectAdapterFactory oaf = scr.getObjectAdapterFactory(scid);
            if (oaf == null) {
                if (orb.subcontractDebugFlag) {
                    dprint(".findObjectAdapter: failed to find ObjectAdapterFactory");
                }

                throw wrapper.noObjectAdapterFactory() ;
            }

            ObjectAdapterId oaid = oktemp.getObjectAdapterId() ;
            ObjectAdapter oa = oaf.find(oaid);

            if (oa == null) {
                if (orb.subcontractDebugFlag) {
                    dprint(".findObjectAdapter: failed to find ObjectAdaptor");
                }

                throw wrapper.badAdapterId() ;
            }

            return oa ;
        } finally {
            if (orb.subcontractDebugFlag) {
                dprint(".findObjectAdapter<-");
            }
        }
    }

    /** Always throws OBJECT_NOT_EXIST if operation is not a special method.
    * If operation is _non_existent or _not_existent, this will just
    * return without performing any action, so that _non_existent can return
    * false.  Always throws OBJECT_NOT_EXIST for any other special method.
    * Update for issue 4385.
    */
    protected void handleNullServant(String operation, NullServant nserv )
    {
        try {
            if (orb.subcontractDebugFlag) {
                dprint(".handleNullServant->: " + operation);
            }

            SpecialMethod specialMethod =
                SpecialMethod.getSpecialMethod(operation);

            if ((specialMethod == null) ||
                !specialMethod.isNonExistentMethod()) {
                if (orb.subcontractDebugFlag) {
                    dprint(".handleNullServant: " + operation
                           + ": throwing OBJECT_NOT_EXIST");
                }

                throw nserv.getException() ;
            }
        } finally {
            if (orb.subcontractDebugFlag) {
                dprint(".handleNullServant<-: " + operation);
            }
        }
    }

    protected void consumeServiceContexts(CorbaMessageMediator request)
    {
        try {
            if (orb.subcontractDebugFlag) {
                dprint(".consumeServiceContexts->: "
                       + opAndId(request));
            }

            ServiceContexts ctxts = request.getRequestServiceContexts();
            ServiceContext sc ;

            GIOPVersion giopVersion = request.getGIOPVersion();

            // we cannot depend on this since for our local case, we do not send
            // in this service context.  Can we rely on just the CodeSetServiceContext?
            // boolean rtSC = false; // Runtime ServiceContext

            boolean hasCodeSetContext = processCodeSetContext(request, ctxts);

            if (orb.subcontractDebugFlag) {
                dprint(".consumeServiceContexts: " + opAndId(request)
                       + ": GIOP version: " + giopVersion);
                dprint(".consumeServiceContexts: " + opAndId(request)
                       + ": as code set context? " + hasCodeSetContext);
            }

            sc = ctxts.get(
                SendingContextServiceContext.SERVICE_CONTEXT_ID ) ;

            if (sc != null) {
                SendingContextServiceContext scsc =
                    (SendingContextServiceContext)sc ;
                IOR ior = scsc.getIOR() ;

                try {
                    ((CorbaConnection)request.getConnection())
                        .setCodeBaseIOR(ior);
                } catch (ThreadDeath td) {
                    throw td ;
                } catch (Throwable t) {
                    throw wrapper.badStringifiedIor( t ) ;
                }
            }

            // the RTSC is sent only once during session establishment.  We
            // need to find out if the CodeBaseRef is already set.  If yes,
            // then also the rtSC flag needs to be set to true
            // this is not possible for the LocalCase since there is no
            // IIOPConnection for the LocalCase

            // used for a case where we have JDK 1.3 supporting 1.0 protocol,
            // but sending 2 service contexts, that is not normal as per
            // GIOP rules, based on above information, we figure out that we
            // are talking to the legacy ORB and set the ORB Version Accordingly.

            // this special case tell us that it is legacy SUN orb
            // and not a foreign one
            // rtSC is not available for localcase due to which this generic
            // path would fail if relying on rtSC
            //if (giopVersion.equals(GIOPVersion.V1_0) && hasCodeSetContext && rtSC)
            boolean isForeignORB = false;

            if (giopVersion.equals(GIOPVersion.V1_0) && hasCodeSetContext) {
                if (orb.subcontractDebugFlag) {
                    dprint(".consumeServiceCOntexts: " + opAndId(request)
                           + ": Determined to be an old Sun ORB");
                }

                orb.setORBVersion(ORBVersionFactory.getOLD()) ;
                // System.out.println("setting legacy ORB version");
            } else {
                // If it didn't include our ORB version service context (below),
                // then it must be a foreign ORB.
                isForeignORB = true;
            }

            // try to get the ORBVersion sent as part of the ServiceContext
            // if any
            sc = ctxts.get( ORBVersionServiceContext.SERVICE_CONTEXT_ID ) ;
            if (sc != null) {
                ORBVersionServiceContext ovsc =
                   (ORBVersionServiceContext) sc;

                ORBVersion version = ovsc.getVersion();
                orb.setORBVersion(version);

                isForeignORB = false;
            }

            if (isForeignORB) {
                if (orb.subcontractDebugFlag) {
                    dprint(".consumeServiceContexts: " + opAndId(request)
                           + ": Determined to be a foreign ORB");
                }

                orb.setORBVersion(ORBVersionFactory.getFOREIGN());
            }
        } finally {
            if (orb.subcontractDebugFlag) {
                dprint(".consumeServiceContexts<-: " + opAndId(request));
            }
        }
    }

    protected CorbaMessageMediator dispatchToServant(
        java.lang.Object servant,
        CorbaMessageMediator req,
        byte[] objectId, ObjectAdapter objectAdapter)
    {
        try {
            if (orb.subcontractDebugFlag) {
                dprint(".dispatchToServant->: " + opAndId(req));
            }

            CorbaMessageMediator response = null ;

            String operation = req.getOperationName() ;

            SpecialMethod method = SpecialMethod.getSpecialMethod(operation) ;
            if (method != null) {
                if (orb.subcontractDebugFlag) {
                    dprint(".dispatchToServant: " + opAndId(req)
                           + ": Handling special method");
                }

                response = method.invoke(servant, req, objectId, objectAdapter);
                return response ;
            }

            // Invoke on the servant using the portable DSI skeleton
            if (servant instanceof org.omg.CORBA.DynamicImplementation) {
                if (orb.subcontractDebugFlag) {
                    dprint(".dispatchToServant: " + opAndId(req)
                           + ": Handling old style DSI type servant");
                }

                org.omg.CORBA.DynamicImplementation dynimpl =
                    (org.omg.CORBA.DynamicImplementation)servant;
                ServerRequestImpl sreq = new ServerRequestImpl(req, orb);

                // Note: When/if dynimpl.invoke calls arguments() or
                // set_exception() then intermediate points are run.
                dynimpl.invoke(sreq);

                response = handleDynamicResult(sreq, req);
            } else if (servant instanceof org.omg.PortableServer.DynamicImplementation) {
                if (orb.subcontractDebugFlag) {
                    dprint(".dispatchToServant: " + opAndId(req)
                           + ": Handling POA DSI type servant");
                }

                org.omg.PortableServer.DynamicImplementation dynimpl =
                    (org.omg.PortableServer.DynamicImplementation)servant;
                ServerRequestImpl sreq = new ServerRequestImpl(req, orb);

                // Note: When/if dynimpl.invoke calls arguments() or
                // set_exception() then intermediate points are run.
                dynimpl.invoke(sreq);

                response = handleDynamicResult(sreq, req);
            } else {
                if (orb.subcontractDebugFlag) {
                    dprint(".dispatchToServant: " + opAndId(req)
                           + ": Handling invoke handler type servant");
                }

                InvokeHandler invhandle = (InvokeHandler)servant ;

                OutputStream stream =
                    (OutputStream)invhandle._invoke(
                      operation,
                      (org.omg.CORBA.portable.InputStream)req.getInputObject(),
                      req);
                response = (CorbaMessageMediator)
                    ((OutputObject)stream).getMessageMediator();
            }

            return response ;
        } finally {
            if (orb.subcontractDebugFlag) {
                dprint(".dispatchToServant<-: " + opAndId(req));
            }
        }
    }

    protected CorbaMessageMediator handleDynamicResult(
        ServerRequestImpl sreq,
        CorbaMessageMediator req)
    {
        try {
            if (orb.subcontractDebugFlag) {
                dprint(".handleDynamicResult->: " + opAndId(req));
            }

            CorbaMessageMediator response = null ;

            // Check if ServerRequestImpl.result() has been called
            Any excany = sreq.checkResultCalled();

            if (excany == null) { // normal return
                if (orb.subcontractDebugFlag) {
                    dprint(".handleDynamicResult: " + opAndId(req)
                           + ": handling normal result");
                }

                // Marshal out/inout/return parameters into the ReplyMessage
                response = sendingReply(req);
                OutputStream os = (OutputStream) response.getOutputObject();
                sreq.marshalReplyParams(os);
            }  else {
                if (orb.subcontractDebugFlag) {
                    dprint(".handleDynamicResult: " + opAndId(req)
                           + ": handling error");
                }

                response = sendingReply(req, excany);
            }

            return response ;
        } finally {
            if (orb.subcontractDebugFlag) {
                dprint(".handleDynamicResult<-: " + opAndId(req));
            }
        }
    }

    protected CorbaMessageMediator sendingReply(CorbaMessageMediator req)
    {
        try {
            if (orb.subcontractDebugFlag) {
                dprint(".sendingReply->: " + opAndId(req));
            }

            ServiceContexts scs = new ServiceContexts(orb);
            return req.getProtocolHandler().createResponse(req, scs);
        } finally {
            if (orb.subcontractDebugFlag) {
                dprint(".sendingReply<-: " + opAndId(req));
            }
        }
    }

    /** Must always be called, just after the servant's method returns.
     *  Creates the ReplyMessage header and puts in the transaction context
     *  if necessary.
     */
    protected CorbaMessageMediator sendingReply(CorbaMessageMediator req, Any excany)
    {
        try {
            if (orb.subcontractDebugFlag) {
                dprint(".sendingReply/Any->: " + opAndId(req));
            }

            ServiceContexts scs = new ServiceContexts(orb);

            // Check if the servant set a SystemException or
            // UserException
            CorbaMessageMediator resp;
            String repId=null;
            try {
                repId = excany.type().id();
            } catch (org.omg.CORBA.TypeCodePackage.BadKind e) {
                throw wrapper.problemWithExceptionTypecode( e ) ;
            }

            if (ORBUtility.isSystemException(repId)) {
                if (orb.subcontractDebugFlag) {
                    dprint(".sendingReply/Any: " + opAndId(req)
                           + ": handling system exception");
                }

                // Get the exception object from the Any
                InputStream in = excany.create_input_stream();
                SystemException ex = ORBUtility.readSystemException(in);
                // Marshal the exception back
                resp = req.getProtocolHandler()
                    .createSystemExceptionResponse(req, ex, scs);
            } else {
                if (orb.subcontractDebugFlag) {
                    dprint(".sendingReply/Any: " + opAndId(req)
                           + ": handling user exception");
                }

                resp = req.getProtocolHandler()
                    .createUserExceptionResponse(req, scs);
                OutputStream os = (OutputStream)resp.getOutputObject();
                excany.write_value(os);
            }

            return resp;
        } finally {
            if (orb.subcontractDebugFlag) {
                dprint(".sendingReply/Any<-: " + opAndId(req));
            }
        }
    }

    /**
     * Handles setting the connection's code sets if required.
     * Returns true if the CodeSetContext was in the request, false
     * otherwise.
     */
    protected boolean processCodeSetContext(
        CorbaMessageMediator request, ServiceContexts contexts)
    {
        try {
            if (orb.subcontractDebugFlag) {
                dprint(".processCodeSetContext->: " + opAndId(request));
            }

            ServiceContext sc = contexts.get(
                CodeSetServiceContext.SERVICE_CONTEXT_ID);
            if (sc != null) {
                // Somehow a code set service context showed up in the local case.
                if (request.getConnection() == null) {
                    return true;
                }

                // If it's GIOP 1.0, it shouldn't have this context at all.  Our legacy
                // ORBs sent it and we need to know if it's here to make ORB versioning
                // decisions, but we don't use the contents.
                if (request.getGIOPVersion().equals(GIOPVersion.V1_0)) {
                    return true;
                }

                CodeSetServiceContext cssc = (CodeSetServiceContext)sc ;
                CodeSetComponentInfo.CodeSetContext csctx = cssc.getCodeSetContext();

                // Note on threading:
                //
                // getCodeSetContext and setCodeSetContext are synchronized
                // on the Connection.  At worst, this will result in
                // multiple threads entering this block and calling
                // setCodeSetContext but not actually changing the
                // values on the Connection.
                //
                // Alternative would be to lock the connection for the
                // whole block, but it's fine either way.

                // The connection's codeSetContext is null until we've received a
                // request with a code set context with the negotiated code sets.
                if (((CorbaConnection)request.getConnection())
                    .getCodeSetContext() == null)
                {

                    // Use these code sets on this connection
                    if (orb.subcontractDebugFlag) {
                        dprint(".processCodeSetContext: " + opAndId(request)
                               + ": Setting code sets to: " + csctx);
                    }

                    ((CorbaConnection)request.getConnection())
                        .setCodeSetContext(csctx);

                    // We had to read the method name using ISO 8859-1
                    // (which is the default in the CDRInputStream for
                    // char data), but now we may have a new char
                    // code set.  If it isn't ISO8859-1, we must tell
                    // the CDR stream to null any converter references
                    // it has created so that it will reacquire
                    // the code sets again using the new info.
                    //
                    // This should probably compare with the stream's
                    // char code set rather than assuming it's ISO8859-1.
                    // (However, the operation name is almost certainly
                    // ISO8859-1 or ASCII.)
                    if (csctx.getCharCodeSet() !=
                        OSFCodeSetRegistry.ISO_8859_1.getNumber()) {
                        ((MarshalInputStream)request.getInputObject())
                            .resetCodeSetConverters();
                    }
                }
            }

            // If no code set information is ever sent from the client,
            // the server will use ISO8859-1 for char and throw an
            // exception for any wchar transmissions.
            //
            // In the local case, we use ORB provided streams for
            // marshaling and unmarshaling.  Currently, they use
            // ISO8859-1 for char/string and UTF16 for wchar/wstring.
            return sc != null ;
        } finally {
            if (orb.subcontractDebugFlag) {
                dprint(".processCodeSetContext<-: " + opAndId(request));
            }
        }
    }

    protected void dprint(String msg)
    {
        ORBUtility.dprint("CorbaServerRequestDispatcherImpl", msg);
    }

    protected String opAndId(CorbaMessageMediator mediator)
    {
        return ORBUtility.operationNameAndRequestId(mediator);
    }
}

// End of file.
