/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;

/**
 * {@code AccessControlContext} was used with the Security Manager for access
 * control decisions based on context that it encapsulated. This feature no
 * longer exists.
 *
 * @author Roland Schemers
 * @since 1.2
 * @deprecated This class was only useful in conjunction with the {@linkplain
 *       SecurityManager the Security Manager}, which is no longer supported.
 *       There is no replacement for the Security Manager or this class.
 */

@Deprecated(since="17", forRemoval=true)
public final class AccessControlContext {

    private ProtectionDomain[] context;
    // isPrivileged and isAuthorized are referenced by the VM - do not remove
    // or change their names
    private boolean isPrivileged;
    private boolean isAuthorized = false;

    // Note: This field is directly used by the virtual machine
    // native codes. Don't touch it.
    private AccessControlContext privilegedContext;

    @SuppressWarnings("removal")
    private DomainCombiner combiner = null;

    /**
     * Create an {@code AccessControlContext} with the given array of
     * {@code ProtectionDomain} objects.
     * Context must not be {@code null}. Duplicate domains will be removed
     * from the context.
     *
     * @param context the {@code ProtectionDomain} objects associated with this
     * context. The non-duplicate domains are copied from the array. Subsequent
     * changes to the array will not affect this {@code AccessControlContext}.
     * @throws NullPointerException if {@code context} is {@code null}
     */
    public AccessControlContext(ProtectionDomain[] context)
    {
        if (context.length == 0) {
            this.context = null;
        } else if (context.length == 1) {
            if (context[0] != null) {
                this.context = context.clone();
            } else {
                this.context = null;
            }
        } else {
            List<ProtectionDomain> v = new ArrayList<>(context.length);
            for (int i =0; i< context.length; i++) {
                if ((context[i] != null) && (!v.contains(context[i])))
                    v.add(context[i]);
            }
            if (!v.isEmpty()) {
                this.context = new ProtectionDomain[v.size()];
                this.context = v.toArray(this.context);
            }
        }
    }

    /**
     * Create a new {@code AccessControlContext} with the given
     * {@code AccessControlContext} and {@code DomainCombiner}.
     * This constructor associates the provided
     * {@code DomainCombiner} with the provided
     * {@code AccessControlContext}.
     *
     * @param acc the {@code AccessControlContext} associated
     *          with the provided {@code DomainCombiner}.
     *
     * @param combiner the {@code DomainCombiner} to be associated
     *          with the provided {@code AccessControlContext}.
     *
     * @throws    NullPointerException if the provided
     *          {@code context} is {@code null}.
     *
     * @since 1.3
     */
    public AccessControlContext(AccessControlContext acc,
                                @SuppressWarnings("removal") DomainCombiner combiner) {
        this.context = acc.context;
        this.combiner = combiner;
    }

    /**
     * Get the {@code DomainCombiner} associated with this
     * {@code AccessControlContext}.
     *
     * @return the {@code DomainCombiner} associated with this
     *          {@code AccessControlContext}, or {@code null}
     *          if there is none.
     *
     * @since 1.3
     */
    @SuppressWarnings("removal")
    public DomainCombiner getDomainCombiner() {
        return combiner;
    }

    /**
     * Throws {@code AccessControlException}.
     *
     * @param perm ignored
     * @throws    AccessControlException always
     */
    @SuppressWarnings("removal")
    public void checkPermission(Permission perm)
        throws AccessControlException
    {
        throw new AccessControlException("checking permissions is not supported");
    }

    /**
     * Checks two {@code AccessControlContext} objects for equality.
     * Checks that {@code obj} is
     * an {@code AccessControlContext} and has the same set of
     * {@code ProtectionDomain} objects as this context.
     *
     * @param obj the object we are testing for equality with this object.
     * @return {@code true} if {@code obj} is an {@code AccessControlContext},
     * and has the same set of {@code ProtectionDomain} objects as this context,
     * {@code false} otherwise.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;

        return obj instanceof AccessControlContext that
                && equalContext(that);
    }

    /*
     * Compare for equality based on state that is free of limited
     * privilege complications.
     */
    private boolean equalContext(AccessControlContext that) {
        if (!equalPDs(this.context, that.context))
            return false;

        if (this.combiner == null && that.combiner != null)
            return false;

        if (this.combiner != null && !this.combiner.equals(that.combiner))
            return false;

        return true;
    }

    private boolean equalPDs(ProtectionDomain[] a, ProtectionDomain[] b) {
        if (a == null) {
            return (b == null);
        }

        if (b == null)
            return false;

        if (!(containsAllPDs(a, b) && containsAllPDs(b, a)))
            return false;

        return true;
    }

    private static boolean containsAllPDs(ProtectionDomain[] thisContext,
        ProtectionDomain[] thatContext) {
        boolean match = false;

        //
        // ProtectionDomains within an ACC currently cannot be null
        // and this is enforced by the constructor and the various
        // optimize methods. However, historically this logic made attempts
        // to support the notion of a null PD and therefore this logic continues
        // to support that notion.
        ProtectionDomain thisPd;
        for (int i = 0; i < thisContext.length; i++) {
            match = false;
            if ((thisPd = thisContext[i]) == null) {
                for (int j = 0; (j < thatContext.length) && !match; j++) {
                    match = (thatContext[j] == null);
                }
            } else {
                Class<?> thisPdClass = thisPd.getClass();
                ProtectionDomain thatPd;
                for (int j = 0; (j < thatContext.length) && !match; j++) {
                    thatPd = thatContext[j];

                    // Class check required to avoid PD exposure (4285406)
                    match = (thatPd != null &&
                        thisPdClass == thatPd.getClass() && thisPd.equals(thatPd));
                }
            }
            if (!match) return false;
        }
        return match;
    }

    /**
     * {@return the hash code value for this context}
     * The hash code is computed by exclusive or-ing the hash code of all the
     * protection domains in the context together.
     */
    @Override
    public int hashCode() {
        int hashCode = 0;

        if (context == null)
            return hashCode;

        for (ProtectionDomain protectionDomain : context) {
            if (protectionDomain != null)
                hashCode ^= protectionDomain.hashCode();
        }

        return hashCode;
    }
}
