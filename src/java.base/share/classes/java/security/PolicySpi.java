/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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


package java.security;

/**
 * This class defines the <i>Service Provider Interface</i> (<b>SPI</b>)
 * for the {@code Policy} class. Installing a system-wide {@code Policy}
 * object is no longer supported.
 *
 * @since 1.6
 * @deprecated This class was only useful in conjunction with
 *       {@linkplain SecurityManager the Security Manager}, which is no
 *       longer supported. There is no replacement for
 *       the Security Manager or this class.
 */

@Deprecated(since="17", forRemoval=true)
public abstract class PolicySpi {

    /**
     * Constructor.
     */
    public PolicySpi() {}

    /**
     * Check whether the policy has granted a Permission to a ProtectionDomain.
     *
     * @param domain the ProtectionDomain to check
     *
     * @param permission check whether this permission is granted to the
     *          specified domain
     *
     * @return boolean {@code true} if the permission is granted to the domain
     */
    protected abstract boolean engineImplies
        (ProtectionDomain domain, Permission permission);

    /**
     * Refreshes/reloads the policy configuration.
     *
     * <p> The default implementation of this method does nothing.
     */
    protected void engineRefresh() { }

    /**
     * Return a PermissionCollection object containing the set of
     * permissions granted to the specified CodeSource.
     *
     * <p> The default implementation of this method returns
     * Policy.UNSUPPORTED_EMPTY_COLLECTION object.
     *
     * @param codesource the CodeSource to which the returned
     *          PermissionCollection has been granted
     *
     * @return a set of permissions granted to the specified CodeSource
     */
    @SuppressWarnings("removal")
    protected PermissionCollection engineGetPermissions
                                        (CodeSource codesource) {
        return Policy.UNSUPPORTED_EMPTY_COLLECTION;
    }

    /**
     * Return a PermissionCollection object containing the set of
     * permissions granted to the specified ProtectionDomain.
     *
     * <p> The default implementation of this method returns
     * Policy.UNSUPPORTED_EMPTY_COLLECTION object.
     *
     * @param domain the ProtectionDomain to which the returned
     *          PermissionCollection has been granted
     *
     * @return a set of permissions granted to the specified ProtectionDomain
     */
    @SuppressWarnings("removal")
    protected PermissionCollection engineGetPermissions
                                        (ProtectionDomain domain) {
        return Policy.UNSUPPORTED_EMPTY_COLLECTION;
    }
}
