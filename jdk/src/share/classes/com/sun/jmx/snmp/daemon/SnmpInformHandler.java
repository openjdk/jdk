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

package com.sun.jmx.snmp.daemon ;

// JMX imports
//
import com.sun.jmx.snmp.SnmpDefinitions;
import com.sun.jmx.snmp.SnmpVarBindList;

/**
 * Provides the callback methods that are required to be implemented by the application
 * when an inform response is received by the agent.
 * <P>
 * Each inform request can be provided with an object that implements this callback
 * interface. An application then uses the SNMP adaptor to start an SNMP inform request,
 * which marks the request as active. The methods in this callback interface
 * get invoked when any of the following happens:
 * <P>
 * <UL>
 * <LI> The agent receives the SNMP inform response.
 * <LI> The agent does not receive any response within a specified time and the number of tries
 * have exceeded the limit (timeout condition).
 * <LI> An internal error occurs while processing or parsing the inform request.
 * </UL>
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 */

public interface SnmpInformHandler extends SnmpDefinitions {

    /**
     * This callback is invoked when a manager responds to an SNMP inform request.
     * The callback should check the error status of the inform request to determine
     * the kind of response.
     *
     * @param request The <CODE>SnmpInformRequest</CODE> associated with this callback.
     * @param errStatus The status of the request.
     * @param errIndex The index in the list that caused the error.
     * @param vblist The <CODE>Response varBind</CODE> list for the successful request.
     */
    public abstract void processSnmpPollData(SnmpInformRequest request, int errStatus, int errIndex, SnmpVarBindList vblist);

    /**
     * This callback is invoked when a manager does not respond within the
     * specified timeout value to the SNMP inform request. The number of tries have also
     * been exhausted.
     * @param request The <CODE>SnmpInformRequest</CODE> associated with this callback.
     */
    public abstract void processSnmpPollTimeout(SnmpInformRequest request);

    /**
     * This callback is invoked when any form of internal error occurs.
     * @param request The <CODE>SnmpInformRequest</CODE> associated with this callback.
     * @param errmsg The <CODE>String</CODE> describing the internal error.
     */
    public abstract void processSnmpInternalError(SnmpInformRequest request, String errmsg);
}
