/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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

import org.ietf.jgss.*;

/**
 * The extended GSSContext interface for supporting additional
 * functionalities not defined by {@code org.ietf.jgss.GSSContext},
 * such as querying context-specific attributes.
 */
@jdk.Exported
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

    /**
     * Requests that the delegation policy be respected. When a true value is
     * requested, the underlying context would use the delegation policy
     * defined by the environment as a hint to determine whether credentials
     * delegation should be performed. This request can only be made on the
     * context initiator's side and it has to be done prior to the first
     * call to <code>initSecContext</code>.
     * <p>
     * When this flag is false, delegation will only be tried when the
     * {@link GSSContext#requestCredDeleg(boolean) credentials delegation flag}
     * is true.
     * <p>
     * When this flag is true but the
     * {@link GSSContext#requestCredDeleg(boolean) credentials delegation flag}
     * is false, delegation will be only tried if the delegation policy permits
     * delegation.
     * <p>
     * When both this flag and the
     * {@link GSSContext#requestCredDeleg(boolean) credentials delegation flag}
     * are true, delegation will be always tried. However, if the delegation
     * policy does not permit delegation, the value of
     * {@link #getDelegPolicyState} will be false, even
     * if delegation is performed successfully.
     * <p>
     * In any case, if the delegation is not successful, the value returned
     * by {@link GSSContext#getCredDelegState()} is false, and the value
     * returned by {@link #getDelegPolicyState()} is also false.
     * <p>
     * Not all mechanisms support delegation policy. Therefore, the
     * application should check to see if the request was honored with the
     * {@link #getDelegPolicyState() getDelegPolicyState} method. When
     * delegation policy is not supported, <code>requestDelegPolicy</code>
     * should return silently without throwing an exception.
     * <p>
     * Note: for the Kerberos 5 mechanism, the delegation policy is expressed
     * through the OK-AS-DELEGATE flag in the service ticket. When it's true,
     * the KDC permits delegation to the target server. In a cross-realm
     * environment, in order for delegation be permitted, all cross-realm TGTs
     * on the authentication path must also have the OK-AS-DELAGATE flags set.
     * @param state true if the policy should be respected
     * @throws GSSException containing the following
     * major error codes:
     *   {@link GSSException#FAILURE GSSException.FAILURE}
     */
    public void requestDelegPolicy(boolean state) throws GSSException;

    /**
     * Returns the delegation policy response. Called after a security context
     * is established. This method can be only called on the initiator's side.
     * See {@link ExtendedGSSContext#requestDelegPolicy}.
     * @return the delegation policy response
     */
    public boolean getDelegPolicyState();
}
