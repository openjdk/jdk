/*
 * Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Enumeration;
import java.util.Vector;

import com.sun.jmx.snmp.SnmpVarBind;
import com.sun.jmx.snmp.SnmpPdu;
import com.sun.jmx.snmp.SnmpEngine;

/**
 * This interface models the part of a SNMP request that involves
 * a specific MIB. One object implementing this interface will be created
 * for every MIB involved in a SNMP request, and that object will be passed
 * to the SnmpMibAgent in charge of handling that MIB.
 *
 * Objects implementing this interface will be allocated by the SNMP engine.
 * You will never need to implement this interface. You will only use it.
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 */
public interface SnmpMibRequest {
    /**
     * Returns the list of varbind to be handled by the SNMP mib node.
     *
     * @return The element of the enumeration are instances of
     *         {@link com.sun.jmx.snmp.SnmpVarBind}
     */
    public Enumeration<SnmpVarBind> getElements();

    /**
     * Returns the vector of varbind to be handled by the SNMP mib node.
     * The caller shall not modify this vector.
     *
     * @return The element of the vector are instances of
     *         {@link com.sun.jmx.snmp.SnmpVarBind}
     */
    public Vector<SnmpVarBind> getSubList();

    /**
     * Returns the SNMP protocol version of the original request. If SNMP V1 request are received, the version is upgraded to SNMP V2.
     *
     * @return The SNMP protocol version of the original request.
     */
    public int getVersion();

    /**
     * Returns the SNMP protocol version of the original request. No translation is done on the version. The actual received request SNMP version is returned.
     *
     * @return The SNMP protocol version of the original request.
     *
     * @since 1.5
     */
    public int getRequestPduVersion();

    /**
     * Returns the local engine. This parameter is returned only if <CODE> SnmpV3AdaptorServer </CODE> is the adaptor receiving this request. Otherwise null is returned.
     * @return the local engine.
     *
     * @since 1.5
     */
    public SnmpEngine getEngine();
    /**
     * Gets the incoming request principal. This parameter is returned only if <CODE> SnmpV3AdaptorServer </CODE> is the adaptor receiving this request. Otherwise null is returned.
     * @return The request principal.
     *
     * @since 1.5
     **/
    public String getPrincipal();
    /**
     * Gets the incoming request security level. This level is defined in {@link com.sun.jmx.snmp.SnmpEngine SnmpEngine}. This parameter is returned only if <CODE> SnmpV3AdaptorServer </CODE> is the adaptor receiving this request. Otherwise -1 is returned.
     * @return The security level.
     *
     * @since 1.5
     */
    public int getSecurityLevel();
    /**
     * Gets the incoming request security model. This parameter is returned only if <CODE> SnmpV3AdaptorServer </CODE> is the adaptor receiving this request. Otherwise -1 is returned.
     * @return The security model.
     *
     * @since 1.5
     */
    public int getSecurityModel();
    /**
     * Gets the incoming request context name. This parameter is returned only if <CODE> SnmpV3AdaptorServer </CODE> is the adaptor receiving this request. Otherwise null is returned.
     * @return The context name.
     *
     * @since 1.5
     */
    public byte[] getContextName();
    /**
     * Gets the incoming request context name used by Access Control Model in order to allow or deny the access to OIDs. This parameter is returned only if <CODE> SnmpV3AdaptorServer </CODE> is the adaptor receiving this request. Otherwise null is returned.
     * @return The checked context name.
     *
     * @since 1.5
     */
    public byte[] getAccessContextName();

    /**
     * Returns a handle on a user allocated contextual object.
     * This contextual object is allocated through the SnmpUserDataFactory
     * on a per SNMP request basis, and is handed back to the user via
     * SnmpMibRequest (and derivative) objects. It is never accessed by
     * the system, but might be handed back in multiple threads. It is thus
     * the user responsibility to make sure he handles this object in a
     * thread safe manner.
     */
    public Object getUserData();

    /**
     * Returns the varbind index that should be embedded in an
     * SnmpStatusException for this particular varbind.
     * This does not necessarily correspond to the "real"
     * index value that will be returned in the result PDU.
     *
     * @param varbind The varbind for which the index value is
     *        querried. Note that this varbind <b>must</b> have
     *        been obtained from the enumeration returned by
     *        <CODE>getElements()</CODE>, or from the vector
     *        returned by <CODE>getSublist()</CODE>.
     *
     * @return The varbind index that should be embedded in an
     *         SnmpStatusException for this particular varbind.
     */
    public int getVarIndex(SnmpVarBind varbind);

    /**
     * Adds a varbind to this request sublist. This method is used for
     * internal purposes and you should never need to call it directly.
     *
     * @param varbind The varbind to be added in the sublist.
     *
     */
    public void addVarBind(SnmpVarBind varbind);


    /**
     * Returns the number of elements (varbinds) in this request sublist.
     *
     * @return The number of elements in the sublist.
     *
     **/
    public int getSize();
    /**
     * Returns the SNMP PDU attached to the request.
     * @return The SNMP PDU.
     *
     * @since 1.5
     **/
    public SnmpPdu getPdu();
}
