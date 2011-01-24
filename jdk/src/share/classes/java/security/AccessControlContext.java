/*
 * Copyright (c) 1997, 2008, Oracle and/or its affiliates. All rights reserved.
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
import sun.security.util.Debug;
import sun.security.util.SecurityConstants;

/**
 * An AccessControlContext is used to make system resource access decisions
 * based on the context it encapsulates.
 *
 * <p>More specifically, it encapsulates a context and
 * has a single method, <code>checkPermission</code>,
 * that is equivalent to the <code>checkPermission</code> method
 * in the AccessController class, with one difference: The AccessControlContext
 * <code>checkPermission</code> method makes access decisions based on the
 * context it encapsulates,
 * rather than that of the current execution thread.
 *
 * <p>Thus, the purpose of AccessControlContext is for those situations where
 * a security check that should be made within a given context
 * actually needs to be done from within a
 * <i>different</i> context (for example, from within a worker thread).
 *
 * <p> An AccessControlContext is created by calling the
 * <code>AccessController.getContext</code> method.
 * The <code>getContext</code> method takes a "snapshot"
 * of the current calling context, and places
 * it in an AccessControlContext object, which it returns. A sample call is
 * the following:
 *
 * <pre>
 *   AccessControlContext acc = AccessController.getContext()
 * </pre>
 *
 * <p>
 * Code within a different context can subsequently call the
 * <code>checkPermission</code> method on the
 * previously-saved AccessControlContext object. A sample call is the
 * following:
 *
 * <pre>
 *   acc.checkPermission(permission)
 * </pre>
 *
 * @see AccessController
 *
 * @author Roland Schemers
 */

public final class AccessControlContext {

    private ProtectionDomain context[];
    private boolean isPrivileged;

    // Note: This field is directly used by the virtual machine
    // native codes. Don't touch it.
    private AccessControlContext privilegedContext;

    private DomainCombiner combiner = null;

    private static boolean debugInit = false;
    private static Debug debug = null;

    static Debug getDebug()
    {
        if (debugInit)
            return debug;
        else {
            if (Policy.isSet()) {
                debug = Debug.getInstance("access");
                debugInit = true;
            }
            return debug;
        }
    }

    /**
     * Create an AccessControlContext with the given array of ProtectionDomains.
     * Context must not be null. Duplicate domains will be removed from the
     * context.
     *
     * @param context the ProtectionDomains associated with this context.
     * The non-duplicate domains are copied from the array. Subsequent
     * changes to the array will not affect this AccessControlContext.
     * @throws NullPointerException if <code>context</code> is <code>null</code>
     */
    public AccessControlContext(ProtectionDomain context[])
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
                if ((context[i] != null) &&  (!v.contains(context[i])))
                    v.add(context[i]);
            }
            if (!v.isEmpty()) {
                this.context = new ProtectionDomain[v.size()];
                this.context = v.toArray(this.context);
            }
        }
    }

    /**
     * Create a new <code>AccessControlContext</code> with the given
     * <code>AccessControlContext</code> and <code>DomainCombiner</code>.
     * This constructor associates the provided
     * <code>DomainCombiner</code> with the provided
     * <code>AccessControlContext</code>.
     *
     * <p>
     *
     * @param acc the <code>AccessControlContext</code> associated
     *          with the provided <code>DomainCombiner</code>.
     *
     * @param combiner the <code>DomainCombiner</code> to be associated
     *          with the provided <code>AccessControlContext</code>.
     *
     * @exception NullPointerException if the provided
     *          <code>context</code> is <code>null</code>.
     *
     * @exception SecurityException if a security manager is installed and the
     *          caller does not have the "createAccessControlContext"
     *          {@link SecurityPermission}
     * @since 1.3
     */
    public AccessControlContext(AccessControlContext acc,
                                DomainCombiner combiner) {

        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(SecurityConstants.CREATE_ACC_PERMISSION);
        }

        this.context = acc.context;

        // we do not need to run the combine method on the
        // provided ACC.  it was already "combined" when the
        // context was originally retrieved.
        //
        // at this point in time, we simply throw away the old
        // combiner and use the newly provided one.
        this.combiner = combiner;
    }

    /**
     * package private for AccessController
     */
    AccessControlContext(ProtectionDomain context[], DomainCombiner combiner) {
        if (context != null) {
            this.context = context.clone();
        }
        this.combiner = combiner;
    }

    /**
     * package private constructor for AccessController.getContext()
     */

    AccessControlContext(ProtectionDomain context[],
                                 boolean isPrivileged)
    {
        this.context = context;
        this.isPrivileged = isPrivileged;
    }

    /**
     * Returns true if this context is privileged.
     */
    boolean isPrivileged()
    {
        return isPrivileged;
    }

    /**
     * get the assigned combiner from the privileged or inherited context
     */
    DomainCombiner getAssignedCombiner() {
        AccessControlContext acc;
        if (isPrivileged) {
            acc = privilegedContext;
        } else {
            acc = AccessController.getInheritedAccessControlContext();
        }
        if (acc != null) {
            return acc.combiner;
        }
        return null;
    }

    /**
     * Get the <code>DomainCombiner</code> associated with this
     * <code>AccessControlContext</code>.
     *
     * <p>
     *
     * @return the <code>DomainCombiner</code> associated with this
     *          <code>AccessControlContext</code>, or <code>null</code>
     *          if there is none.
     *
     * @exception SecurityException if a security manager is installed and
     *          the caller does not have the "getDomainCombiner"
     *          {@link SecurityPermission}
     * @since 1.3
     */
    public DomainCombiner getDomainCombiner() {

        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(SecurityConstants.GET_COMBINER_PERMISSION);
        }
        return combiner;
    }

    /**
     * Determines whether the access request indicated by the
     * specified permission should be allowed or denied, based on
     * the security policy currently in effect, and the context in
     * this object. The request is allowed only if every ProtectionDomain
     * in the context implies the permission. Otherwise the request is
     * denied.
     *
     * <p>
     * This method quietly returns if the access request
     * is permitted, or throws a suitable AccessControlException otherwise.
     *
     * @param perm the requested permission.
     *
     * @exception AccessControlException if the specified permission
     * is not permitted, based on the current security policy and the
     * context encapsulated by this object.
     * @exception NullPointerException if the permission to check for is null.
     */
    public void checkPermission(Permission perm)
        throws AccessControlException
    {
        boolean dumpDebug = false;

        if (perm == null) {
            throw new NullPointerException("permission can't be null");
        }
        if (getDebug() != null) {
            // If "codebase" is not specified, we dump the info by default.
            dumpDebug = !Debug.isOn("codebase=");
            if (!dumpDebug) {
                // If "codebase" is specified, only dump if the specified code
                // value is in the stack.
                for (int i = 0; context != null && i < context.length; i++) {
                    if (context[i].getCodeSource() != null &&
                        context[i].getCodeSource().getLocation() != null &&
                        Debug.isOn("codebase=" + context[i].getCodeSource().getLocation().toString())) {
                        dumpDebug = true;
                        break;
                    }
                }
            }

            dumpDebug &= !Debug.isOn("permission=") ||
                Debug.isOn("permission=" + perm.getClass().getCanonicalName());

            if (dumpDebug && Debug.isOn("stack")) {
                Thread.currentThread().dumpStack();
            }

            if (dumpDebug && Debug.isOn("domain")) {
                if (context == null) {
                    debug.println("domain (context is null)");
                } else {
                    for (int i=0; i< context.length; i++) {
                        debug.println("domain "+i+" "+context[i]);
                    }
                }
            }
        }

        /*
         * iterate through the ProtectionDomains in the context.
         * Stop at the first one that doesn't allow the
         * requested permission (throwing an exception).
         *
         */

        /* if ctxt is null, all we had on the stack were system domains,
           or the first domain was a Privileged system domain. This
           is to make the common case for system code very fast */

        if (context == null)
            return;

        for (int i=0; i< context.length; i++) {
            if (context[i] != null &&  !context[i].implies(perm)) {
                if (dumpDebug) {
                    debug.println("access denied " + perm);
                }

                if (Debug.isOn("failure") && debug != null) {
                    // Want to make sure this is always displayed for failure,
                    // but do not want to display again if already displayed
                    // above.
                    if (!dumpDebug) {
                        debug.println("access denied " + perm);
                    }
                    Thread.currentThread().dumpStack();
                    final ProtectionDomain pd = context[i];
                    final Debug db = debug;
                    AccessController.doPrivileged (new PrivilegedAction<Void>() {
                        public Void run() {
                            db.println("domain that failed "+pd);
                            return null;
                        }
                    });
                }
                throw new AccessControlException("access denied "+perm, perm);
            }
        }

        // allow if all of them allowed access
        if (dumpDebug) {
            debug.println("access allowed "+perm);
        }

        return;
    }

    /**
     * Take the stack-based context (this) and combine it with the
     * privileged or inherited context, if need be.
     */
    AccessControlContext optimize() {
        // the assigned (privileged or inherited) context
        AccessControlContext acc;
        if (isPrivileged) {
            acc = privilegedContext;
        } else {
            acc = AccessController.getInheritedAccessControlContext();
        }

        // this.context could be null if only system code is on the stack;
        // in that case, ignore the stack context
        boolean skipStack = (context == null);

        // acc.context could be null if only system code was involved;
        // in that case, ignore the assigned context
        boolean skipAssigned = (acc == null || acc.context == null);

        if (acc != null && acc.combiner != null) {
            // let the assigned acc's combiner do its thing
            return goCombiner(context, acc);
        }

        // optimization: if neither have contexts; return acc if possible
        // rather than this, because acc might have a combiner
        if (skipAssigned && skipStack) {
            return this;
        }

        // optimization: if there is no stack context; there is no reason
        // to compress the assigned context, it already is compressed
        if (skipStack) {
            return acc;
        }

        int slen = context.length;

        // optimization: if there is no assigned context and the stack length
        // is less then or equal to two; there is no reason to compress the
        // stack context, it already is
        if (skipAssigned && slen <= 2) {
            return this;
        }

        // optimization: if there is a single stack domain and that domain
        // is already in the assigned context; no need to combine
        if ((slen == 1) && (context[0] == acc.context[0])) {
            return acc;
        }

        int n = (skipAssigned) ? 0 : acc.context.length;

        // now we combine both of them, and create a new context
        ProtectionDomain pd[] = new ProtectionDomain[slen + n];

        // first copy in the assigned context domains, no need to compress
        if (!skipAssigned) {
            System.arraycopy(acc.context, 0, pd, 0, n);
        }

        // now add the stack context domains, discarding nulls and duplicates
    outer:
        for (int i = 0; i < context.length; i++) {
            ProtectionDomain sd = context[i];
            if (sd != null) {
                for (int j = 0; j < n; j++) {
                    if (sd == pd[j]) {
                        continue outer;
                    }
                }
                pd[n++] = sd;
            }
        }

        // if length isn't equal, we need to shorten the array
        if (n != pd.length) {
            // optimization: if we didn't really combine anything
            if (!skipAssigned && n == acc.context.length) {
                return acc;
            } else if (skipAssigned && n == slen) {
                return this;
            }
            ProtectionDomain tmp[] = new ProtectionDomain[n];
            System.arraycopy(pd, 0, tmp, 0, n);
            pd = tmp;
        }

        //      return new AccessControlContext(pd, false);

        // Reuse existing ACC

        this.context = pd;
        this.combiner = null;
        this.isPrivileged = false;

        return this;
    }

    private AccessControlContext goCombiner(ProtectionDomain[] current,
                                        AccessControlContext assigned) {

        // the assigned ACC's combiner is not null --
        // let the combiner do its thing

        // XXX we could add optimizations to 'current' here ...

        if (getDebug() != null) {
            debug.println("AccessControlContext invoking the Combiner");
        }

        // No need to clone current and assigned.context
        // combine() will not update them
        ProtectionDomain[] combinedPds = assigned.combiner.combine(
            current, assigned.context);

        // return new AccessControlContext(combinedPds, assigned.combiner);

        // Reuse existing ACC
        this.context = combinedPds;
        this.combiner = assigned.combiner;
        this.isPrivileged = false;

        return this;
    }

    /**
     * Checks two AccessControlContext objects for equality.
     * Checks that <i>obj</i> is
     * an AccessControlContext and has the same set of ProtectionDomains
     * as this context.
     * <P>
     * @param obj the object we are testing for equality with this object.
     * @return true if <i>obj</i> is an AccessControlContext, and has the
     * same set of ProtectionDomains as this context, false otherwise.
     */
    public boolean equals(Object obj) {
        if (obj == this)
            return true;

        if (! (obj instanceof AccessControlContext))
            return false;

        AccessControlContext that = (AccessControlContext) obj;


        if (context == null) {
            return (that.context == null);
        }

        if (that.context == null)
            return false;

        if (!(this.containsAllPDs(that) && that.containsAllPDs(this)))
            return false;

        if (this.combiner == null)
            return (that.combiner == null);

        if (that.combiner == null)
            return false;

        if (!this.combiner.equals(that.combiner))
            return false;

        return true;
    }

    private boolean containsAllPDs(AccessControlContext that) {
        boolean match = false;
        //
        // ProtectionDomains within an ACC currently cannot be null
        // and this is enforced by the constructor and the various
        // optimize methods. However, historically this logic made attempts
        // to support the notion of a null PD and therefore this logic continues
        // to support that notion.
        ProtectionDomain thisPd;
        for (int i = 0; i < context.length; i++) {
            match = false;
            if ((thisPd = context[i]) == null) {
                for (int j = 0; (j < that.context.length) && !match; j++) {
                    match = (that.context[j] == null);
                }
            } else {
                Class thisPdClass = thisPd.getClass();
                ProtectionDomain thatPd;
                for (int j = 0; (j < that.context.length) && !match; j++) {
                    thatPd = that.context[j];

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
     * Returns the hash code value for this context. The hash code
     * is computed by exclusive or-ing the hash code of all the protection
     * domains in the context together.
     *
     * @return a hash code value for this context.
     */

    public int hashCode() {
        int hashCode = 0;

        if (context == null)
            return hashCode;

        for (int i =0; i < context.length; i++) {
            if (context[i] != null)
                hashCode ^= context[i].hashCode();
        }
        return hashCode;
    }
}
