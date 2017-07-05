/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.security.jgss;

/**
 * Attribute types that can be specified as an argument of
 * {@link com.sun.security.jgss.ExtendedGSSContext#inquireSecContext}
 */
@jdk.Exported
public enum InquireType {
    /**
     * Attribute type for retrieving the session key of an
     * established Kerberos 5 security context.
     */
    KRB5_GET_SESSION_KEY,
    /**
     * Attribute type for retrieving the service ticket flags of an
     * established Kerberos 5 security context.
     */
    KRB5_GET_TKT_FLAGS,
    /**
     * Attribute type for retrieving the authorization data in the
     * service ticket of an established Kerberos 5 security context.
     * Only supported on the acceptor side.
     */
    KRB5_GET_AUTHZ_DATA,
    /**
     * Attribute type for retrieving the authtime in the service ticket
     * of an established Kerberos 5 security context.
     */
    KRB5_GET_AUTHTIME
}
