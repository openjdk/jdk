/*
 * Copyright (c) 2001, 2006, Oracle and/or its affiliates. All rights reserved.
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

/**
 * This exception is thrown when an error occurs in an <CODE> SnmpSecurityModel </CODE>.
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 * @since 1.5
 */
public class SnmpSecurityException extends Exception {
    private static final long serialVersionUID = 5574448147432833480L;

    /**
     * The current request varbind list.
     */
    public SnmpVarBind[] list = null;
    /**
     * The status of the exception. See {@link com.sun.jmx.snmp.SnmpDefinitions} for possible values.
     */
    public int status = SnmpDefinitions.snmpReqUnknownError;
    /**
     * The current security model related security parameters.
     */
    public SnmpSecurityParameters params = null;
    /**
     * The current context engine Id.
     */
    public byte[] contextEngineId = null;
     /**
     * The current context name.
     */
    public byte[] contextName = null;
     /**
     * The current flags.
     */
    public byte flags = (byte) SnmpDefinitions.noAuthNoPriv;
    /**
     * Constructor.
     * @param msg The exception msg to display.
     */
    public SnmpSecurityException(String msg) {
        super(msg);
    }
}
