/*
 * Copyright (c) 2001, 2004, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.INTERNAL;

import com.sun.corba.se.pept.broker.Broker;
import com.sun.corba.se.pept.encoding.InputObject;
import com.sun.corba.se.pept.encoding.OutputObject;
import com.sun.corba.se.pept.protocol.MessageMediator;
import com.sun.corba.se.pept.transport.Acceptor;
import com.sun.corba.se.pept.transport.Connection;
import com.sun.corba.se.pept.transport.ContactInfo;
import com.sun.corba.se.pept.transport.EventHandler;
import com.sun.corba.se.pept.transport.InboundConnectionCache;
import com.sun.corba.se.pept.transport.Selector;

import com.sun.corba.se.spi.extension.RequestPartitioningPolicy;
import com.sun.corba.se.spi.ior.IORTemplate;
import com.sun.corba.se.spi.ior.TaggedProfileTemplate;
import com.sun.corba.se.spi.ior.iiop.IIOPAddress ;
import com.sun.corba.se.spi.ior.iiop.IIOPFactories;
import com.sun.corba.se.spi.ior.iiop.IIOPProfileTemplate ;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion ;
import com.sun.corba.se.spi.ior.iiop.AlternateIIOPAddressComponent;
import com.sun.corba.se.spi.legacy.connection.LegacyServerSocketEndPointInfo;
import com.sun.corba.se.spi.logging.CORBALogDomains;
import com.sun.corba.se.spi.monitoring.LongMonitoredAttributeBase;
import com.sun.corba.se.spi.monitoring.MonitoringConstants;
import com.sun.corba.se.spi.monitoring.MonitoringFactories;
import com.sun.corba.se.spi.monitoring.MonitoredObject;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.orbutil.threadpool.Work;
import com.sun.corba.se.spi.protocol.CorbaMessageMediator;
import com.sun.corba.se.spi.transport.CorbaAcceptor;
import com.sun.corba.se.spi.transport.CorbaConnection;
import com.sun.corba.se.spi.transport.CorbaContactInfo;
import com.sun.corba.se.spi.transport.SocketInfo;
import com.sun.corba.se.spi.transport.SocketOrChannelAcceptor;

import com.sun.corba.se.impl.encoding.CDRInputObject;
import com.sun.corba.se.impl.encoding.CDROutputObject;
import com.sun.corba.se.impl.logging.ORBUtilSystemException;
import com.sun.corba.se.impl.oa.poa.Policies; // REVISIT impl/poa specific
import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.impl.orbutil.ORBUtility;
import com.sun.corba.se.impl.ior.iiop.JavaSerializationComponent;

// BEGIN Legacy support.
import com.sun.corba.se.spi.legacy.connection.LegacyServerSocketEndPointInfo;
// END Legacy support.

/**
 * @author Harold Carr
 */
public class SocketOrChannelAcceptorImpl
    extends
        EventHandlerBase
    implements
        CorbaAcceptor,
        SocketOrChannelAcceptor,
        Work,
        // BEGIN Legacy
        SocketInfo,
        LegacyServerSocketEndPointInfo
        // END Legacy
{
    protected ServerSocketChannel serverSocketChannel;
    protected ServerSocket serverSocket;
    protected int port;
    protected long enqueueTime;
    protected boolean initialized;
    protected ORBUtilSystemException wrapper ;
    protected InboundConnectionCache connectionCache;

    // BEGIN Legacy
    protected String type = "";
    protected String name = "";
    protected String hostname;
    protected int locatorPort;
    // END Legacy

    public SocketOrChannelAcceptorImpl(ORB orb)
    {
        this.orb = orb;
        wrapper = ORBUtilSystemException.get( orb,
            CORBALogDomains.RPC_TRANSPORT ) ;

        setWork(this);
        initialized = false;

        // BEGIN Legacy support.
        this.hostname = orb.getORBData().getORBServerHost();
        this.name = LegacyServerSocketEndPointInfo.NO_NAME;
        this.locatorPort = -1;
        // END Legacy support.
    }

    public SocketOrChannelAcceptorImpl(ORB orb, int port)
    {
        this(orb);
        this.port = port;
    }

    // BEGIN Legacy support.
    public SocketOrChannelAcceptorImpl(ORB orb, int port,
                                       String name, String type)
    {
        this(orb, port);
        this.name = name;
        this.type = type;
    }
    // END Legacy support.

    ////////////////////////////////////////////////////
    //
    // pept.transport.Acceptor
    //

    public boolean initialize()
    {
        if (initialized) {
            return false;
        }
        if (orb.transportDebugFlag) {
            dprint(".initialize: " + this);
        }
        InetSocketAddress inetSocketAddress = null;
        try {
            if (orb.getORBData().getListenOnAllInterfaces().equals(ORBConstants.LISTEN_ON_ALL_INTERFACES)) {
                inetSocketAddress = new InetSocketAddress(port);
            } else {
                String host = orb.getORBData().getORBServerHost();
                inetSocketAddress = new InetSocketAddress(host, port);
            }
            serverSocket = orb.getORBData().getSocketFactory()
                .createServerSocket(type, inetSocketAddress);
            internalInitialize();
        } catch (Throwable t) {
            throw wrapper.createListenerFailed( t, Integer.toString(port) ) ;
        }
        initialized = true;
        return true;
    }

    protected void internalInitialize()
        throws Exception
    {
        // Determine the listening port (for the IOR).
        // This is important when using emphemeral ports (i.e.,
        // when the port value to the constructor is 0).

        port = serverSocket.getLocalPort();

        // Register with transport (also sets up monitoring).

        orb.getCorbaTransportManager().getInboundConnectionCache(this);

        // Finish configuation.

        serverSocketChannel = serverSocket.getChannel();

        if (serverSocketChannel != null) {
            setUseSelectThreadToWait(
                orb.getORBData().acceptorSocketUseSelectThreadToWait());
            serverSocketChannel.configureBlocking(
                ! orb.getORBData().acceptorSocketUseSelectThreadToWait());
        } else {
            // Configure to use listener and reader threads.
            setUseSelectThreadToWait(false);
        }
        setUseWorkerThreadForEvent(
            orb.getORBData().acceptorSocketUseWorkerThreadForEvent());

    }

    public boolean initialized()
    {
        return initialized;
    }

    public String getConnectionCacheType()
    {
        return this.getClass().toString();
    }

    public void setConnectionCache(InboundConnectionCache connectionCache)
    {
        this.connectionCache = connectionCache;
    }

    public InboundConnectionCache getConnectionCache()
    {
        return connectionCache;
    }

    public boolean shouldRegisterAcceptEvent()
    {
        return true;
    }

    public void accept()
    {
        try {
            SocketChannel socketChannel = null;
            Socket socket = null;
            if (serverSocketChannel == null) {
                socket = serverSocket.accept();
            } else {
                socketChannel = serverSocketChannel.accept();
                socket = socketChannel.socket();
            }
            orb.getORBData().getSocketFactory()
                .setAcceptedSocketOptions(this, serverSocket, socket);
            if (orb.transportDebugFlag) {
                dprint(".accept: " +
                       (serverSocketChannel == null
                        ? serverSocket.toString()
                        : serverSocketChannel.toString()));
            }

            CorbaConnection connection =
                new SocketOrChannelConnectionImpl(orb, this, socket);
            if (orb.transportDebugFlag) {
                dprint(".accept: new: " + connection);
            }

            // NOTE: The connection MUST be put in the cache BEFORE being
            // registered with the selector.  Otherwise if the bytes
            // are read on the connection it will attempt a time stamp
            // but the cache will be null, resulting in NPE.
            getConnectionCache().put(this, connection);

            if (connection.shouldRegisterServerReadEvent()) {
                Selector selector = orb.getTransportManager().getSelector(0);
                selector.registerForEvent(connection.getEventHandler());
            }

            getConnectionCache().reclaim();

        } catch (IOException e) {
            if (orb.transportDebugFlag) {
                dprint(".accept:", e);
            }
            orb.getTransportManager().getSelector(0).unregisterForEvent(this);
            // REVISIT - need to close - recreate - then register new one.
            orb.getTransportManager().getSelector(0).registerForEvent(this);
            // NOTE: if register cycling we do not want to shut down ORB
            // since local beans will still work.  Instead one will see
            // a growing log file to alert admin of problem.
        }
    }

    public void close ()
    {
        try {
            if (orb.transportDebugFlag) {
                dprint(".close->:");
            }
            Selector selector = orb.getTransportManager().getSelector(0);
            selector.unregisterForEvent(this);
            if (serverSocketChannel != null) {
                serverSocketChannel.close();
            }
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            if (orb.transportDebugFlag) {
                dprint(".close:", e);
            }
        } finally {
            if (orb.transportDebugFlag) {
                dprint(".close<-:");
            }
        }
    }

    public EventHandler getEventHandler()
    {
        return this;
    }

    ////////////////////////////////////////////////////
    //
    // CorbaAcceptor
    //

    public String getObjectAdapterId()
    {
        return null;
    }

    public String getObjectAdapterManagerId()
    {
        return null;
    }

    public void addToIORTemplate(IORTemplate iorTemplate,
                                 Policies policies,
                                 String codebase)
    {
        Iterator iterator = iorTemplate.iteratorById(
            org.omg.IOP.TAG_INTERNET_IOP.value);

        String hostname = orb.getORBData().getORBServerHost();

        if (iterator.hasNext()) {
            // REVISIT - how does this play with legacy ORBD port exchange?
            IIOPAddress iiopAddress =
                IIOPFactories.makeIIOPAddress(orb, hostname, port);
            AlternateIIOPAddressComponent iiopAddressComponent =
                IIOPFactories.makeAlternateIIOPAddressComponent(iiopAddress);

            while (iterator.hasNext()) {
                TaggedProfileTemplate taggedProfileTemplate =
                    (TaggedProfileTemplate) iterator.next();
                taggedProfileTemplate.add(iiopAddressComponent);
            }
        } else {
            GIOPVersion version = orb.getORBData().getGIOPVersion();
            int templatePort;
            if (policies.forceZeroPort()) {
                templatePort = 0;
            } else if (policies.isTransient()) {
                templatePort = port;
            } else {
                templatePort = orb.getLegacyServerSocketManager()
                   .legacyGetPersistentServerPort(SocketInfo.IIOP_CLEAR_TEXT);
            }
            IIOPAddress addr =
                IIOPFactories.makeIIOPAddress(orb, hostname, templatePort);
            IIOPProfileTemplate iiopProfile =
                IIOPFactories.makeIIOPProfileTemplate(orb, version, addr);
            if (version.supportsIORIIOPProfileComponents()) {
                iiopProfile.add(IIOPFactories.makeCodeSetsComponent(orb));
                iiopProfile.add(IIOPFactories.makeMaxStreamFormatVersionComponent());
                RequestPartitioningPolicy rpPolicy = (RequestPartitioningPolicy)
                    policies.get_effective_policy(
                                      ORBConstants.REQUEST_PARTITIONING_POLICY);
                if (rpPolicy != null) {
                    iiopProfile.add(
                         IIOPFactories.makeRequestPartitioningComponent(
                             rpPolicy.getValue()));
                }
                if (codebase != null && codebase != "") {
                    iiopProfile.add(IIOPFactories. makeJavaCodebaseComponent(codebase));
                }
                if (orb.getORBData().isJavaSerializationEnabled()) {
                    iiopProfile.add(
                           IIOPFactories.makeJavaSerializationComponent());
                }
            }
            iorTemplate.add(iiopProfile);
        }
    }

    public String getMonitoringName()
    {
        return "AcceptedConnections";
    }

    ////////////////////////////////////////////////////
    //
    // EventHandler methods
    //

    public SelectableChannel getChannel()
    {
        return serverSocketChannel;
    }

    public int getInterestOps()
    {
        return SelectionKey.OP_ACCEPT;
    }

    public Acceptor getAcceptor()
    {
        return this;
    }

    public Connection getConnection()
    {
        throw new RuntimeException("Should not happen.");
    }

    ////////////////////////////////////////////////////
    //
    // Work methods.
    //

    /* CONFLICT: with legacy below.
    public String getName()
    {
        return this.toString();
    }
    */

    public void doWork()
    {
        try {
            if (orb.transportDebugFlag) {
                dprint(".doWork->: " + this);
            }
            if (selectionKey.isAcceptable()) {
                AccessController.doPrivileged(new PrivilegedAction() {
                    public java.lang.Object run() {
                        accept();
                        return null;
                    }
                });
            } else {
                if (orb.transportDebugFlag) {
                    dprint(".doWork: ! selectionKey.isAcceptable: " + this);
                }
            }
        } catch (SecurityException se) {
            if (orb.transportDebugFlag) {
                dprint(".doWork: ignoring SecurityException: "
                       + se
                       + " " + this);
            }
            String permissionStr = ORBUtility.getClassSecurityInfo(getClass());
            wrapper.securityExceptionInAccept(se, permissionStr);
        } catch (Exception ex) {
            if (orb.transportDebugFlag) {
                dprint(".doWork: ignoring Exception: "
                       + ex
                       + " " + this);
            }
            wrapper.exceptionInAccept(ex);
        } catch (Throwable t) {
            if (orb.transportDebugFlag) {
                dprint(".doWork: ignoring Throwable: "
                       + t
                       + " " + this);
            }
        } finally {

            // IMPORTANT: To avoid bug (4953599), we force the
            // Thread that does the NIO select to also do the
            // enable/disable of Ops using SelectionKey.interestOps().
            // Otherwise, the SelectionKey.interestOps() may block
            // indefinitely.
            // NOTE: If "acceptorSocketUseWorkerThreadForEvent" is
            // set to to false in ParserTable.java, then this method,
            // doWork(), will get executed by the same thread
            // (SelectorThread) that does the NIO select.
            // If "acceptorSocketUseWorkerThreadForEvent" is set
            // to true, a WorkerThread will execute this method,
            // doWork(). Hence, the registering of the enabling of
            // the SelectionKey's interestOps is done here instead
            // of calling SelectionKey.interestOps(<interest op>).

            Selector selector = orb.getTransportManager().getSelector(0);
            selector.registerInterestOps(this);

            if (orb.transportDebugFlag) {
                dprint(".doWork<-:" + this);
            }
        }
    }

    public void setEnqueueTime(long timeInMillis)
    {
        enqueueTime = timeInMillis;
    }

    public long getEnqueueTime()
    {
        return enqueueTime;
    }


    //
    // Factory methods.
    //

    // REVISIT: refactor into common base or delegate.
    public MessageMediator createMessageMediator(Broker broker,
                                                 Connection connection)
    {
        // REVISIT - no factoring so cheat to avoid code dup right now.
        // REVISIT **** COUPLING !!!!
        ContactInfo contactInfo = new SocketOrChannelContactInfoImpl();
        return contactInfo.createMessageMediator(broker, connection);
    }

    // REVISIT: refactor into common base or delegate.
    public MessageMediator finishCreatingMessageMediator(Broker broker,
                                                         Connection connection,
                                                         MessageMediator messageMediator)
    {
        // REVISIT - no factoring so cheat to avoid code dup right now.
        // REVISIT **** COUPLING !!!!
        ContactInfo contactInfo = new SocketOrChannelContactInfoImpl();
        return contactInfo.finishCreatingMessageMediator(broker,
                                          connection, messageMediator);
    }

    public InputObject createInputObject(Broker broker,
                                         MessageMediator messageMediator)
    {
        CorbaMessageMediator corbaMessageMediator = (CorbaMessageMediator)
            messageMediator;
        return new CDRInputObject((ORB)broker,
                                  (CorbaConnection)messageMediator.getConnection(),
                                  corbaMessageMediator.getDispatchBuffer(),
                                  corbaMessageMediator.getDispatchHeader());
    }

    public OutputObject createOutputObject(Broker broker,
                                           MessageMediator messageMediator)
    {
        CorbaMessageMediator corbaMessageMediator = (CorbaMessageMediator)
            messageMediator;
        return new CDROutputObject((ORB) broker, corbaMessageMediator,
                                   corbaMessageMediator.getReplyHeader(),
                                   corbaMessageMediator.getStreamFormatVersion());
    }

    ////////////////////////////////////////////////////
    //
    // SocketOrChannelAcceptor
    //

    public ServerSocket getServerSocket()
    {
        return serverSocket;
    }

    ////////////////////////////////////////////////////
    //
    // Implementation.
    //

    public String toString()
    {
        String sock;
        if (serverSocketChannel == null) {
            if (serverSocket == null) {
                sock = "(not initialized)";
            } else {
                sock = serverSocket.toString();
            }
        } else {
            sock = serverSocketChannel.toString();
        }

        return
            toStringName() +
            "["
            + sock + " "
            + type + " "
            + shouldUseSelectThreadToWait() + " "
            + shouldUseWorkerThreadForEvent()
            + "]" ;
    }

    protected String toStringName()
    {
        return "SocketOrChannelAcceptorImpl";
    }

    protected void dprint(String msg)
    {
        ORBUtility.dprint(toStringName(), msg);
    }

    protected void dprint(String msg, Throwable t)
    {
        dprint(msg);
        t.printStackTrace(System.out);
    }

    // BEGIN Legacy support
    ////////////////////////////////////////////////////
    //
    // LegacyServerSocketEndPointInfo and EndPointInfo
    //

    public String getType()
    {
        return type;
    }

    public String getHostName()
    {
        return hostname;
    }

    public String getHost()
    {
        return hostname;
    }

    public int getPort()
    {
        return port;
    }

    public int getLocatorPort()
    {
        return locatorPort;
    }

    public void setLocatorPort (int port)
    {
        locatorPort = port;
    }

    public String getName()
    {
        // Kluge alert:
        // Work and Legacy both define getName.
        // Try to make this behave best for most cases.
        String result =
            name.equals(LegacyServerSocketEndPointInfo.NO_NAME) ?
            this.toString() : name;
        return result;
    }
    // END Legacy support
}

// End of file.
