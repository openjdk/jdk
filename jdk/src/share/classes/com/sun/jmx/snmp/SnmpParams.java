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
package com.sun.jmx.snmp;

import com.sun.jmx.snmp.SnmpDefinitions;

/**
 * This class is the base class of all parameters that are used when making SNMP requests to an <CODE>SnmpPeer</CODE>.
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 * @since 1.5
 */
public abstract class SnmpParams implements SnmpDefinitions {
    private int protocolVersion = snmpVersionOne;
    SnmpParams(int version) {
        protocolVersion = version;
    }

    SnmpParams() {}
    /**
     * Checks whether parameters are in place for an SNMP <CODE>set</CODE> operation.
     * @return <CODE>true</CODE> if parameters are in place, <CODE>false</CODE> otherwise.
     */
    public abstract boolean allowSnmpSets();
    /**
     * Returns the version of the protocol to use.
     * The returned value is:
     * <UL>
     * <LI>{@link com.sun.jmx.snmp.SnmpDefinitions#snmpVersionOne snmpVersionOne} if the protocol is SNMPv1
     * <LI>{@link com.sun.jmx.snmp.SnmpDefinitions#snmpVersionTwo snmpVersionTwo} if the protocol is SNMPv2
     * <LI>{@link com.sun.jmx.snmp.SnmpDefinitions#snmpVersionThree snmpVersionThree} if the protocol is SNMPv3
     * </UL>
     * @return The version of the protocol to use.
     */
    public int getProtocolVersion() {
        return protocolVersion ;
    }

    /**
     * Sets the version of the protocol to be used.
     * The version should be identified using the definitions
     * contained in
     * {@link com.sun.jmx.snmp.SnmpDefinitions SnmpDefinitions}.
     * <BR>For instance if you wish to use SNMPv2, you can call the method as follows:
     * <BLOCKQUOTE><PRE>
     * setProtocolVersion(SnmpDefinitions.snmpVersionTwo);
     * </PRE></BLOCKQUOTE>
     * @param protocolversion The version of the protocol to be used.
     */

    public void setProtocolVersion(int protocolversion) {
        this.protocolVersion = protocolversion ;
    }
}
