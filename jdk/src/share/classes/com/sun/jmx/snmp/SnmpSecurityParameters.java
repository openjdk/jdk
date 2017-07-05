/*
 * Copyright 2001-2003 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.jmx.snmp;
import com.sun.jmx.snmp.SnmpStatusException;
import com.sun.jmx.snmp.SnmpTooBigException;

/**
 * Security parameters are security model dependent. Every security parameters class wishing to be passed to a security model must implement this marker interface.
 * This interface has to be implemented when developing customized security models.
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 * @since 1.5
 */
public interface SnmpSecurityParameters {
    /**
     * BER encoding of security parameters.
     * @param outputBytes Array to fill.
     * @return Encoded parameters length.
     */
    int encode(byte[] outputBytes) throws SnmpTooBigException;
    /**
     * BER decoding of security parameters.
     * @param params Encoded parameters.
     */
    void decode(byte[] params) throws SnmpStatusException;

    /**
     * Principal coded inside the security parameters.
     * @return The security principal.
     */
    String getPrincipal();
}
