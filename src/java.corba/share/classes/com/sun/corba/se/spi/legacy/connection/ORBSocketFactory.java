/*
 * Copyright (c) 2000, 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.spi.legacy.connection;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;

import com.sun.corba.se.spi.ior.IOR;
import com.sun.corba.se.spi.transport.SocketInfo;

/**
 *
 * DEPRECATED.  DEPRECATED. DEPRECATED. DEPRECATED. <p>
 * DEPRECATED.  DEPRECATED. DEPRECATED. DEPRECATED. <p>
 *
 * This interface gives one the ability to plug in their own socket
 * factory class to an ORB. <p>
 *
 * Usage: <p>
 *
 * One specifies a class which implements this interface via the
 *
 *     <code>ORBConstants.SOCKET_FACTORY_CLASS_PROPERTY</code>
 *
 * property. <p>
 *
 * Example:

 * <pre>
 *   -Dcom.sun.CORBA.connection.ORBSocketFactoryClass=MySocketFactory
 * </pre> <p>
 *
 * Typically one would use the same socket factory class on both the
 * server side and the client side (but this is not required). <p>
 *
 * A <code>ORBSocketFactory</code> class should have a public default
 * constructor which is called once per instantiating ORB.init call.
 * That ORB then calls the methods of that <code>ORBSocketFactory</code>
 * to obtain client and server sockets. <p>
 *
 * This interface also supports multiple server end points.  See the
 * documentation on <code>createServerSocket</code> below.
 *
 */

public interface ORBSocketFactory
{
    /**
     * DEPRECATED.  DEPRECATED. DEPRECATED. DEPRECATED. <p>
     *
     * A server ORB always creates an "IIOP_CLEAR_TEXT" listening port.
     * That port is put into IOP profiles of object references exported
     * by an ORB. <p>
     *
     * If
     *
     *     <code>createServerSocket(String type, int port)</code>
     *
     * is passed <code>IIOP_CLEAR_TEXT</code> as a <code>type</code>
     * argument it should then call and return
     *
     *     <code>new java.net.ServerSocket(int port)</code> <p>
     *
     * If
     *
     *     <code>createSocket(SocketInfo socketInfo)</code>
     *
     * is passed <code>IIOP_CLEAR_TEXT</code> in
     * <code>socketInfo.getType()</code> it should
     * then call and return
     *
     * <pre>
     *     new java.net.Socket(socketInfo.getHost(),
     *                         socketInfo.getPort())
     * </pre>
     *
     */
    public static final String IIOP_CLEAR_TEXT = "IIOP_CLEAR_TEXT";


    /**
     * DEPRECATED.  DEPRECATED. DEPRECATED. DEPRECATED. <p>
     *
     * This method is used by a server side ORB. <p>
     *
     * When an ORB needs to create a listen socket on which connection
     * requests are accepted it calls
     *
     *     <code>createServerSocket(String type, int port)</code>.
     *
     * The type argument says which type of socket should be created. <p>
     *
     * The interpretation of the type argument is the responsibility of
     * an instance of <code>ORBSocketFactory</code>, except in the case
     * of <code>IIOP_CLEAR_TEXT</code>, in which case a standard server
     * socket should be created. <p>
     *
     *
     * Multiple Server Port API: <p>
     *
     * In addition to the IIOP_CLEAR_TEXT listening port, it is possible
     * to specify that an ORB listen on additional port of specific types. <p>
     *
     * This API allows one to specify that an ORB should create an X,
     * or an X and a Y listen socket. <p>
     *
     * If X, to the user, means SSL, then one just plugs in an SSL
     * socket factory. <p>
     *
     * Or, another example, if X and Y, to the user, means SSL without
     * authentication and SSL with authentication respectively, then they
     * plug in a factory which will either create an X or a Y socket
     * depending on the type given to
     *
     *     <code>createServerSocket(String type, int port)</code>. <p>
     *
     * One specifies multiple listening ports (in addition to the
     * default IIOP_CLEAR_TEXT port) using the
     *
     *     <code>ORBConstants.LISTEN_SOCKET_PROPERTY</code>
     *
     * property. <p>
     *
     * Example usage:
     *
     * <pre>
     *    ... \
     *    -Dcom.sun.CORBA.connection.ORBSocketFactoryClass=com.my.MySockFact \
     *    -Dcom.sun.CORBA.connection.ORBListenSocket=SSL:0,foo:1 \
     *    ...
     * </pre>
     *
     * The meaning of the "type" (SSL and foo above) is controlled
     * by the user. <p>
     *
     * ORBListenSocket is only meaningful for servers. <p>
     *
     * The property value is interpreted as follows.  For each
     * type/number pair: <p>
     *
     * If number is 0 then use an emphemeral port for the listener of
     * the associated type. <p>
     *
     * If number is greater than 0 use that port number. <p>
     *
     * An ORB creates a listener socket for each type
     * specified by the user by calling
     *
     *    <code>createServerSocket(String type, int port)</code>
     *
     * with the type specified by the user. <p>
     *
     * After an ORB is initialized and the RootPOA has been resolved,
     * it is then listening on
     * all the end points which were specified.  It may be necessary
     * to add this additional end point information to object references
     * exported by this ORB.  <p>
     *
     * Each object reference will contain the ORB's default IIOP_CLEAR_TEXT
     * end point in its IOP profile.  To add additional end point information
     * (i.e., an SSL port) to an IOR (i.e., an object reference) one needs
     * to intercept IOR creation using
     * an <code>PortableInterceptor::IORInterceptor</code>. <p>
     *
     * Using PortableInterceptors (with a non-standard extension): <p>
     *
     * Register an <code>IORInterceptor</code>.  Inside its
     * <code>establish_components</code> operation:
     *
     * <pre>
     *
     * com.sun.corba.se.spi.legacy.interceptor.IORInfoExt ext;
     * ext = (com.sun.corba.se.spi.legacy.interceptor.IORInfoExt)info;
     *
     * int port = ext.getServerPort("myType");
     *
     * </pre>
     *
     * Once you have the port you may add information to references
     * created by the associated adapter by calling
     *
     *    <code>IORInfo::add_ior_component</code><p>
     *
     *
     * Note: if one is using a POA and the lifespan policy of that
     * POA is persistent then the port number returned
     * by <code>getServerPort</code> <em>may</em>
     * be the corresponding ORBD port, depending on whether the POA/ORBD
     * protocol is the present port exchange or if, in the future,
     * the protocol is based on object reference template exchange.
     * In either
     * case, the port returned will be correct for the protocol.
     * (In more detail, if the port exchange protocol is used then
     * getServerPort will return the ORBD's port since the port
     * exchange happens before, at ORB initialization.
     * If object reference
     * exchange is used then the server's transient port will be returned
     * since the templates are exchanged after adding components.) <p>
     *
     *
     * Persistent object reference support: <p>
     *
     * When creating persistent object references with alternate
     * type/port info, ones needs to configure the ORBD to also support
     * this alternate info.  This is done as follows: <p>
     *
     * - Give the ORBD the same socket factory you gave to the client
     * and server. <p>
     *
     * - specify ORBListenSocket ports of the same types that your
     * servers support.  You should probably specify explicit port
     * numbers for ORBD if you embed these numbers inside IORs. <p>
     *
     * Note: when using the port exchange protocol
     * the ORBD and servers will exchange port
     * numbers for each given type so they know about each other.
     * When using object reference template exchange the server's
     * transient ports are contained in the template. <p>
     *
     *
     * - specify your <code>BadServerIdHandler</code> (discussed below)
     * using the
     *
     *    <code>ORBConstants.BAD_SERVER_ID_HANDLER_CLASS_PROPERTY</code> <p>
     *
     * Example:
     *
     * <pre>
     *
     * -Dcom.sun.CORBA.POA.ORBBadServerIdHandlerClass=corba.socketPersistent.MyBadServerIdHandler
     *
     * </pre>
     *
     * The <code>BadServerIdHandler</code> ...<p>
     *
     * See <code>com.sun.corba.se.impl.activation.ServerManagerImpl.handle</code>
     * for example code on writing a bad server id handler.  NOTE:  This
     * is an unsupported internal API.  It will not exist in future releases.
     * <p>
     *
     *
     * Secure connections to other services: <p>
     *
     * If one wants secure connections to other services such as
     * Naming then one should configure them with the same
     *
     *     <code>SOCKET_FACTORY_CLASS_PROPERTY</code> and
     *     <code>LISTEN_SOCKET_PROPERTY</code>
     *
     * as used by other clients and servers in your distributed system.
     *
     */
    public ServerSocket createServerSocket(String type, int port)
        throws
            IOException;



    /**
     * DEPRECATED.  DEPRECATED. DEPRECATED. DEPRECATED. <p>
     *
     * This method is used by a client side ORB. <p>
     *
     * Each time a client invokes on an object reference, the reference's
     * associated ORB will call
     *
     * <pre>
     *    getEndPointInfo(ORB orb,
     *                    IOR ior,
     *                    SocketInfo socketInfo)
     * </pre>
     *
     * NOTE: The type of the <code>ior</code> argument is an internal
     * representation for efficiency.  If the <code>ORBSocketFactory</code>
     * interface ever becomes standardized then the <code>ior</code> will
     * most likely change to a standard type (e.g., a stringified ior,
     * an <code>org.omg.IOP.IOR</code>, or ...). <p>
     *
     * Typically, this method will look at tagged components in the
     * given <code>ior</code> to determine what type of socket to create. <p>
     *
     * Typically, the <code>ior</code> will contain a tagged component
     * specifying an alternate port type and number.  <p>
     *
     * This method should return an <code>SocketInfo</code> object
     * containing the type/host/port to be used for the connection.
     *
     * If there are no appropriate tagged components then this method
     * should return an <code>SocketInfo</code> object with the type
     * <code>IIOP_CLEAR_TEXT</code> and host/port from the ior's IOP
     * profile. <p>
     *
     * If the ORB already has an existing connection to the returned
     * type/host/port, then that connection is used.  Otherwise the ORB calls
     *
     *    <code>createSocket(SocketInfo socketInfo)</code> <p>
     *
     * The <code>orb</code> argument is useful for handling
     * the <code>ior</code> argument. <p>
     *
     * The <code>SocketInfo</code> given to <code>getEndPointInfo</code>
     * is either null or an object obtained
     * from <code>GetEndPointInfoAgainException</code>
     *
     */
    public SocketInfo getEndPointInfo(org.omg.CORBA.ORB orb,
                                        IOR ior,
                                        SocketInfo socketInfo);


    /**
     * DEPRECATED.  DEPRECATED. DEPRECATED. DEPRECATED. <p>
     *
     * This method is used by a client side ORB. <p>
     *
     * This method should return a client socket of the given
     * type/host/port. <p>
     *
     * Note: the <code>SocketInfo</code> is the same instance as was
     * returned by <code>getSocketInfo</code> so extra cookie info may
     * be attached. <p>
     *
     * If this method throws GetEndPointInfoAgainException then the
     * ORB calls <code>getEndPointInfo</code> again, passing it the
     * <code>SocketInfo</code> object contained in the exception.
     *
     */
    public Socket createSocket(SocketInfo socketInfo)
        throws
            IOException,
            GetEndPointInfoAgainException;
}

// End of file.
