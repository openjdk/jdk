/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.spi.logging ;

/** Defines constants for all of the logging domains used in the ORB.
 * Note that this is the suffix to the log domain.  The full domain is given by
 * <code>javax.enterprise.resource.corba.{ORBId}.{Log domain}</code>
 * where {ORBId} is the ORB ID of the ORB instance doing the logging.
 * <P>
 * The ORB implementation packages are mapped into these domains as follows:
 * <ul>
 * <li>activation: orbd.*
 * <li>corba: rpc.presentation (CORBA API, typecode/any), oa.invocation (DII)
 * <li>core: service context code in rpc.protocol (will eventually move to its own package)
 * <li>dynamicany: rpc.presentation
 * <li>encoding: rpc.encoding
 * <li>iiop: rmiiop.delegate (ShutdownUtilDelegate needs to move somewhere)
 * <li>interceptors: rpc.protocol
 * <li>io: rpc.encoding
 * <li>ior: oa.ior
 * <li>javax: rmiiiop.delegate
 * <li>logging: logging does not have a domain
 * <li>naming: naming
 * <li>oa: oa
 * <li>orb: orb.lifecycle
 * <li>orbutil: util
 * <li>protocol: rpc.protocol
 * <li>resolver: orb.resolver
 * <li>transport: rpc.transport
 * <li>txpoa: this will be removed in the future.
 * <li>util: util
 * </ul>
 */
public abstract class CORBALogDomains {
    private CORBALogDomains() {}

    // Top level log domain for CORBA
    public static String TOP_LEVEL_DOMAIN  = "javax.enterprise.resource.corba";

    public static final String RPC              = "rpc" ;

    /** Log domain for code directly implementing the CORBA API and
     * the typecode/any machinery.
     */
    public static final String RPC_PRESENTATION = "rpc.presentation" ;

    /** Log domain for any sort of wire encoding used in marshalling
     */
    public static final String RPC_ENCODING = "rpc.encoding" ;

    /** Log domain for the code used to handle any kind of invocation
     * protocol.  This includes client and server delegates, client and
     * server request dispatchers, service contexts, portable interceptors,
     * and the GIOP protocol (but not CDR representation of data).
     */
    public static final String RPC_PROTOCOL = "rpc.protocol" ;

    /** Log domain for low-level transport details, which are
     * independent of encoding and presentation details.  This
     * includes selectors, acceptors, connections, connection management,
     * and any other transport management functions.
     */
    public static final String RPC_TRANSPORT = "rpc.transport" ;

    public static final String NAMING          = "naming" ;

    /** Log domain for naming context creation and destruction.
     */
    public static final String NAMING_LIFECYCLE = "naming.lifecycle" ;

    /** Log domain for name service lookup.
     */
    public static final String NAMING_READ = "naming.read" ;

    /** Log domain for name service bind, rebind, destroy, and other state
     * change operations.
     */
    public static final String NAMING_UPDATE = "naming.update" ;

    public static final String ORBD         = "orbd" ;

    /** Log domain for the ORBD locator function, which forwards
     * client requests to their current server incarnation.
     */
    public static final String ORBD_LOCATOR = "orbd.locator" ;

    /** Log domain for the ORBD activator function, which starts
     * server instances on demand.
     */
    public static final String ORBD_ACTIVATOR = "orbd.activator" ;

    /** Log domain for the Implementation Repository.
     */
    public static final String ORBD_REPOSITORY = "orbd.repository" ;

    /** Log domain for the servertool utilitiy used to update the
     * implementation repository.
     */
    public static final String ORBD_SERVERTOOL = "orbd.servertool" ;

    public static final String ORB          = "orb" ;

    /** Log domain for ORB initialization, configuration, startup,
     * and shutdown.
     */
    public static final String ORB_LIFECYCLE = "orb.lifecycle" ;

    /** Log domain for ORB client side name resolution and supporting
     * functions such as INS.
     */
    public static final String ORB_RESOLVER = "orb.resolver" ;

    public static final String OA           = "oa" ;

    /** Log domain for creation, destruction, and state change of
     * Object Adapters and related classes (e.g. POAManager).
     */
    public static final String OA_LIFECYCLE = "oa.lifecycle" ;

    /** Log domain for all IOR related code.
     */
    public static final String OA_IOR = "oa.ior" ;

    /** Log domain for object adapter request dispatch.
     */
    public static final String OA_INVOCATION = "oa.invocation" ;

    public static final String RMIIIOP          = "rmiiiop" ;

    /** Log domain for the RMI-IIOP implementation in the Stub, Util, and
     * PortableRemoteObject delegates.
     */
    public static final String RMIIIOP_DELEGATE = "rmiiiop.delegate" ;

    /** Log domain for utility classes.
     */
    public static final String UTIL = "util" ;
}
