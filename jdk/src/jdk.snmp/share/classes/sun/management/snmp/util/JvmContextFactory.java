/*
 * Copyright (c) 2003, 2006, Oracle and/or its affiliates. All rights reserved.
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
package sun.management.snmp.util;

import com.sun.jmx.mbeanserver.Util;
import com.sun.jmx.snmp.agent.SnmpUserDataFactory;
import com.sun.jmx.snmp.SnmpPdu;
import com.sun.jmx.snmp.SnmpStatusException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


public class JvmContextFactory implements SnmpUserDataFactory {

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
     * @since Java DMK 5.0
     **/
    public Object allocateUserData(SnmpPdu requestPdu)
        throws SnmpStatusException {
        return Collections.synchronizedMap(new HashMap<Object, Object>());
    }

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
     * @since Java DMK 5.0
     **/
    public void releaseUserData(Object userData, SnmpPdu responsePdu)
        throws SnmpStatusException {
        ((Map<?, ?>)userData).clear();
    }


    public static Map<Object, Object> getUserData() {
        final Object userData =
            com.sun.jmx.snmp.ThreadContext.get("SnmpUserData");

        if (userData instanceof Map<?, ?>) return Util.cast(userData);
        else return null;
    }

}
