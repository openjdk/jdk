/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * {@code DomainCombiner} was used to dynamically update
 * {@code ProtectionDomain}s for access control operations and decisions. This
 * feature no longer exists.
 *
 * @since 1.3
 * @deprecated This class was only useful in conjunction with {@linkplain
 *       SecurityManager the Security Manager}, which is no longer supported.
 *       There is no replacement for the Security Manager or this class.
 */
@Deprecated(since="17", forRemoval=true)
public interface DomainCombiner {

    /**
     * Modify or update the provided ProtectionDomains.
     * ProtectionDomains may be added to or removed from the given
     * ProtectionDomains.  The ProtectionDomains may be re-ordered.
     * Individual ProtectionDomains may be modified (with a new
     * set of Permissions, for example).
     *
     * @param currentDomains the ProtectionDomains associated with the
     *          current execution thread, up to the most recent
     *          privileged {@code ProtectionDomain}.
     *          The ProtectionDomains are listed in order of execution,
     *          with the most recently executing {@code ProtectionDomain}
     *          residing at the beginning of the array. This parameter may
     *          be {@code null} if the current execution thread
     *          has no associated ProtectionDomains.
     *
     * @param assignedDomains an array of inherited ProtectionDomains.
     *          ProtectionDomains may be inherited from a parent thread,
     *          or from a privileged {@code AccessControlContext}.
     *          This parameter may be {@code null}
     *          if there are no inherited ProtectionDomains.
     *
     * @return a new array consisting of the updated ProtectionDomains,
     *          or {@code null}.
     */
    ProtectionDomain[] combine(ProtectionDomain[] currentDomains,
                                ProtectionDomain[] assignedDomains);
}
