/*
 * Copyright (c) 2001, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Serializable;

import com.sun.jmx.snmp.SnmpSecurityParameters;

import com.sun.jmx.snmp.SnmpDefinitions;
/**
 * Is the fully decoded representation of an SNMP V3 packet.
 * <P>
 *
 * Classes are derived from <CODE>SnmpPdu</CODE> to
 * represent the different forms of SNMP pdu
 * ({@link com.sun.jmx.snmp.SnmpScopedPduRequest SnmpScopedPduRequest},
 * {@link com.sun.jmx.snmp.SnmpScopedPduBulk SnmpScopedPduBulk}).
 * <BR>The <CODE>SnmpScopedPduPacket</CODE> class defines the attributes
 * common to every scoped SNMP packets.
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 * @see SnmpV3Message
 *
 * @since 1.5
 */
@SuppressWarnings("serial") // JDK implementation class
public abstract class SnmpScopedPduPacket extends SnmpPdu
    implements Serializable {
    /**
     * Message max size the pdu sender can deal with.
     */
    public int msgMaxSize = 0;

    /**
     * Message identifier.
     */
    public int msgId = 0;

    /**
     * Message flags. Reportable flag  and security level.</P>
     *<PRE>
     * --  .... ...1   authFlag
     * --  .... ..1.   privFlag
     * --  .... .1..   reportableFlag
     * --              Please observe:
     * --  .... ..00   is OK, means noAuthNoPriv
     * --  .... ..01   is OK, means authNoPriv
     * --  .... ..10   reserved, must NOT be used.
     * --  .... ..11   is OK, means authPriv
     *</PRE>
     */
    public byte msgFlags = 0;

    /**
     * The security model the security sub system MUST use in order to deal with this pdu (eg: User based Security Model Id = 3).
     */
    public int msgSecurityModel = 0;

    /**
     * The context engine Id in which the pdu must be handled (Generaly the local engine Id).
     */
    public byte[] contextEngineId = null;

    /**
     * The context name in which the OID have to be interpreted.
     */
    public byte[] contextName = null;

    /**
     * The security parameters. This is an opaque member that is
     * interpreted by the concerned security model.
     */
    public SnmpSecurityParameters securityParameters = null;

    /**
     * Constructor. Is only called by a son. Set the version to <CODE>SnmpDefinitions.snmpVersionThree</CODE>.
     */
    protected SnmpScopedPduPacket() {
        version = SnmpDefinitions.snmpVersionThree;
    }
}
