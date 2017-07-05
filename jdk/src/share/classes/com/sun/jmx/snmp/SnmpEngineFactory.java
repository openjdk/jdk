/*
 * Copyright 2002-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

/**
 * This <CODE>SnmpEngineFactory</CODE> is instantiating an <CODE>SnmpEngine</CODE> containing :
 * <ul>
 * <li> Message Processing Sub System + V1, V2 et V3 Message Processing Models</li>
 * <li> Security Sub System + User based Security Model (Id 3)</li>
 * <li> Access Control Sub System + Ip Acl + User based Access Control Model. See <CODE> IpAcl </CODE> and <CODE> UserAcl </CODE>.</li>
 * </ul>
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 * @since 1.5
 */
public interface SnmpEngineFactory {
    /**
     * The engine instantiation method.
     * @param p The parameters used to instantiate a new engine.
     * @throws IllegalArgumentException Throwed if one of the configuration file file doesn't exist (Acl files, security file).
     * @return The newly created SnmpEngine.
     */
    public SnmpEngine createEngine(SnmpEngineParameters p);

    /**
     * The engine instantiation method.
     * @param p The parameters used to instantiate a new engine.
     * @param ipacl The Ip ACL to pass to the Access Control Model.
     * @throws IllegalArgumentException Throwed if one of the configuration
     *         file file doesn't exist (Acl files, security file).
     * @return The newly created SnmpEngine.
     */
    public SnmpEngine createEngine(SnmpEngineParameters p,
                                   InetAddressAcl ipacl);
}
