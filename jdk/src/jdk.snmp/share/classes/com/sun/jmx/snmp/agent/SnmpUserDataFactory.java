/*
 * Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.jmx.snmp.agent;

import com.sun.jmx.snmp.SnmpPduPacket;
import com.sun.jmx.snmp.SnmpPdu;
import com.sun.jmx.snmp.SnmpStatusException;

/**
 * This interface is provided to enable fine customization of the SNMP
 * agent behaviour.
 *
 * <p>You will not need to implement this interface except if your agent
 * needs extra customization requiring some contextual information.</p>
 *
 * <p>If an SnmpUserDataFactory is set on the SnmpAdaptorServer, then a new
 * object containing user-data will be allocated through this factory
 * for each incoming request. This object will be passed along to
 * the SnmpMibAgent within SnmpMibRequest objects. By default, no
 * SnmpUserDataFactory is set on the SnmpAdaptorServer, and the contextual
 * object passed to SnmpMibAgent is null.</p>
 *
 * <p>You can use this feature to obtain on contextual information
 * (such as community string etc...) or to implement a caching
 * mechanism, or for whatever purpose might be required by your specific
 * agent implementation.</p>
 *
 * <p>The sequence <code>allocateUserData() / releaseUserData()</code> can
 * also be used to implement a caching mechanism:
 * <ul>
 * <li><code>allocateUserData()</code> could be used to allocate
 *         some cache space,</li>
 * <li>and <code>releaseUserData()</code> could be used to flush it.</li>
 * </ul></p>
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 * @see com.sun.jmx.snmp.agent.SnmpMibRequest
 * @see com.sun.jmx.snmp.agent.SnmpMibAgent
 * @see com.sun.jmx.snmp.daemon.SnmpAdaptorServer
 *
 **/
public interface SnmpUserDataFactory {
    /**
     * Called by the <CODE>SnmpAdaptorServer</CODE> adaptor.
     * Allocate a contextual object containing some user data. This method
     * is called once for each incoming SNMP request. The scope
     * of this object will be the whole request. Since the request can be
     * handled in several threads, the user should make sure that this
     * object can be accessed in a thread-safe manner. The SNMP framework
     * will never access this object directly - it will simply pass
     * it to the <code>SnmpMibAgent</code> within
     * <code>SnmpMibRequest</code> objects - from where it can be retrieved
     * through the {@link com.sun.jmx.snmp.agent.SnmpMibRequest#getUserData() getUserData()} accessor.
     * <code>null</code> is considered to be a valid return value.
     *
     * This method is called just after the SnmpPduPacket has been
     * decoded.
     *
     * @param requestPdu The SnmpPduPacket received from the SNMP manager.
     *        <b>This parameter is owned by the SNMP framework and must be
     *        considered as transient.</b> If you wish to keep some of its
     *        content after this method returns (by storing it in the
     *        returned object for instance) you should clone that
     *        information.
     *
     * @return A newly allocated user-data contextual object, or
     *         <code>null</code>
     * @exception SnmpStatusException If an SnmpStatusException is thrown,
     *            the request will be aborted.
     *
     * @since 1.5
     **/
    public Object allocateUserData(SnmpPdu requestPdu)
        throws SnmpStatusException;

    /**
     * Called by the <CODE>SnmpAdaptorServer</CODE> adaptor.
     * Release a previously allocated contextual object containing user-data.
     * This method is called just before the responsePdu is sent back to the
     * manager. It gives the user a chance to alter the responsePdu packet
     * before it is encoded, and to free any resources that might have
     * been allocated when creating the contextual object.
     *
     * @param userData The contextual object being released.
     * @param responsePdu The SnmpPduPacket that will be sent back to the
     *        SNMP manager.
     *        <b>This parameter is owned by the SNMP framework and must be
     *        considered as transient.</b> If you wish to keep some of its
     *        content after this method returns you should clone that
     *        information.
     *
     * @exception SnmpStatusException If an SnmpStatusException is thrown,
     *            the responsePdu is dropped and nothing is returned to
     *            to the manager.
     *
     * @since 1.5
     **/
    public void releaseUserData(Object userData, SnmpPdu responsePdu)
        throws SnmpStatusException;
}
