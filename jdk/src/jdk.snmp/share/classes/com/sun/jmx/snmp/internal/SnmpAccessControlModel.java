/*
 * Copyright (c) 2001, 2003, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.jmx.snmp.internal;

import com.sun.jmx.snmp.SnmpStatusException;
import com.sun.jmx.snmp.SnmpOid;
import com.sun.jmx.snmp.SnmpPdu;
/**
 * Access Control Model interface. Every access control model must implement this interface in order to be integrated in the engine based framework.
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 * @since 1.5
 */
public interface SnmpAccessControlModel extends SnmpModel {
    /**
     * Method called by the dispatcher in order to control the access at an <CODE>SnmpOid</CODE> Level. If access is not allowed, an <CODE>SnmpStatusException</CODE> is thrown.
     * This method is called after the <CODE>checkPduAccess</CODE> pdu based method.
     * @param version The SNMP protocol version number.
     * @param principal The request principal.
     * @param securityLevel The request security level as defined in <CODE>SnmpEngine</CODE>.
     * @param pduType The pdu type (get, set, ...).
     * @param securityModel The security model ID.
     * @param contextName The access control context name.
     * @param oid The OID to check.
     */
    public void checkAccess(int version,
                            String principal,
                            int securityLevel,
                            int pduType,
                            int securityModel,
                            byte[] contextName,
                            SnmpOid oid)
        throws SnmpStatusException;
    /**
     * Method called by the dispatcher in order to control the access at an SNMP pdu Level. If access is not allowed, an <CODE>SnmpStatusException</CODE> is thrown. In case of exception, the access control is aborted. OIDs are not checked.
     * This method should be called prior to the <CODE>checkAccess</CODE> OID based method.
     * @param version The SNMP protocol version number.
     * @param principal The request principal.
     * @param securityLevel The request security level as defined in <CODE>SnmpEngine</CODE>.
     * @param pduType The pdu type (get, set, ...).
     * @param securityModel The security model ID.
     * @param contextName The access control context name.
     * @param pdu The pdu to check.
     */
    public void checkPduAccess(int version,
                               String principal,
                               int securityLevel,
                               int pduType,
                               int securityModel,
                               byte[] contextName,
                               SnmpPdu pdu)
        throws SnmpStatusException;

    /**
     * Enable SNMP V1 and V2 set requests. Be aware that can lead to a security hole in a context of SNMP V3 management. By default SNMP V1 and V2 set requests are not authorized.
     * @return boolean True the activation suceeded.
     */
    public boolean enableSnmpV1V2SetRequest();
    /**
     * Disable SNMP V1 and V2 set requests. By default SNMP V1 and V2 set requests are not authorized.
     * @return boolean True the deactivation suceeded.
     */
    public boolean disableSnmpV1V2SetRequest();

    /**
     * The SNMP V1 and V2 set requests authorization status. By default SNMP V1 and V2 set requests are not authorized.
     * @return boolean True SNMP V1 and V2 requests are authorized.
     */
    public boolean isSnmpV1V2SetRequestAuthorized();
}
