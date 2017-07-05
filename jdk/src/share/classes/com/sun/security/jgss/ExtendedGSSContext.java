/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.security.jgss;

import org.ietf.jgss.*;

/**
 * The extended GSSContext interface for supporting additional
 * functionalities not defined by {@code org.ietf.jgss.GSSContext},
 * such as querying context-specific attributes.
 */
public interface ExtendedGSSContext extends GSSContext {
    /**
     * Return the mechanism-specific attribute associated with {@code type}.
     * <br><br>
     * For each supported attribute type, the type for the output are
     * defined below.
     * <ol>
     * <li>{@code KRB5_GET_TKT_FLAGS}:
     * the returned object is a boolean array for the service ticket flags,
     * which is long enough to contain all true bits. This means if
     * the user wants to get the <em>n</em>'th bit but the length of the
     * returned array is less than <em>n</em>, it is regarded as false.
     * <li>{@code KRB5_GET_SESSION_KEY}:
     * the returned object is an instance of {@link java.security.Key},
     * which has the following properties:
     *    <ul>
     *    <li>Algorithm: enctype as a string, where
     *        enctype is defined in RFC 3961, section 8.
     *    <li>Format: "RAW"
     *    <li>Encoded form: the raw key bytes, not in any ASN.1 encoding
     *    </ul>
     * <li>{@code KRB5_GET_AUTHZ_DATA}:
     * the returned object is an array of
     * {@link com.sun.security.jgss.AuthorizationDataEntry}, or null if the
     * optional field is missing in the service ticket.
     * <li>{@code KRB5_GET_AUTHTIME}:
     * the returned object is a String object in the standard KerberosTime
     * format defined in RFC 4120 5.2.3
     * </ol>
     *
     * If there is a security manager, an {@link InquireSecContextPermission}
     * with the name {@code type.mech} must be granted. Otherwise, this could
     * result in a {@link SecurityException}.<p>
     *
     * Example:
     * <pre>
     *      GSSContext ctxt = m.createContext(...)
     *      // Establishing the context
     *      if (ctxt instanceof ExtendedGSSContext) {
     *          ExtendedGSSContext ex = (ExtendedGSSContext)ctxt;
     *          try {
     *              Key key = (key)ex.inquireSecContext(
     *                      InquireType.KRB5_GET_SESSION_KEY);
     *              // read key info
     *          } catch (GSSException gsse) {
     *              // deal with exception
     *          }
     *      }
     * </pre>
     * @param type the type of the attribute requested
     * @return the attribute, see the method documentation for details.
     * @throws GSSException containing  the following
     * major error codes:
     *   {@link GSSException#BAD_MECH GSSException.BAD_MECH} if the mechanism
     *   does not support this method,
     *   {@link GSSException#UNAVAILABLE GSSException.UNAVAILABLE} if the
     *   type specified is not supported,
     *   {@link GSSException#NO_CONTEXT GSSException.NO_CONTEXT} if the
     *   security context is invalid,
     *   {@link GSSException#FAILURE GSSException.FAILURE} for other
     *   unspecified failures.
     * @throws SecurityException if a security manager exists and a proper
     *   {@link InquireSecContextPermission} is not granted.
     * @see InquireSecContextPermission
     */
    public Object inquireSecContext(InquireType type)
            throws GSSException;
}
