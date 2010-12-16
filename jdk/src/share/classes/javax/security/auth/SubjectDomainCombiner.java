/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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

package javax.security.auth;

import java.security.AccessController;
import java.security.Permission;
import java.security.Permissions;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.Security;
import java.util.Set;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

/**
 * A <code>SubjectDomainCombiner</code> updates ProtectionDomains
 * with Principals from the <code>Subject</code> associated with this
 * <code>SubjectDomainCombiner</code>.
 *
 */
public class SubjectDomainCombiner implements java.security.DomainCombiner {

    private Subject subject;
    private WeakKeyValueMap<ProtectionDomain, ProtectionDomain> cachedPDs =
                new WeakKeyValueMap<ProtectionDomain, ProtectionDomain>();
    private Set<Principal> principalSet;
    private Principal[] principals;

    private static final sun.security.util.Debug debug =
        sun.security.util.Debug.getInstance("combiner",
                                        "\t[SubjectDomainCombiner]");

    // Note: check only at classloading time, not dynamically during combine()
    private static final boolean useJavaxPolicy =
        javax.security.auth.Policy.isCustomPolicySet(debug);

    // Relevant only when useJavaxPolicy is true
    private static final boolean allowCaching =
                                        (useJavaxPolicy && cachePolicy());

    /**
     * Associate the provided <code>Subject</code> with this
     * <code>SubjectDomainCombiner</code>.
     *
     * <p>
     *
     * @param subject the <code>Subject</code> to be associated with
     *          with this <code>SubjectDomainCombiner</code>.
     */
    public SubjectDomainCombiner(Subject subject) {
        this.subject = subject;

        if (subject.isReadOnly()) {
            principalSet = subject.getPrincipals();
            principals = principalSet.toArray
                        (new Principal[principalSet.size()]);
        }
    }

    /**
     * Get the <code>Subject</code> associated with this
     * <code>SubjectDomainCombiner</code>.
     *
     * <p>
     *
     * @return the <code>Subject</code> associated with this
     *          <code>SubjectDomainCombiner</code>, or <code>null</code>
     *          if no <code>Subject</code> is associated with this
     *          <code>SubjectDomainCombiner</code>.
     *
     * @exception SecurityException if the caller does not have permission
     *          to get the <code>Subject</code> associated with this
     *          <code>SubjectDomainCombiner</code>.
     */
    public Subject getSubject() {
        java.lang.SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new AuthPermission
                ("getSubjectFromDomainCombiner"));
        }
        return subject;
    }

    /**
     * Update the relevant ProtectionDomains with the Principals
     * from the <code>Subject</code> associated with this
     * <code>SubjectDomainCombiner</code>.
     *
     * <p> A new <code>ProtectionDomain</code> instance is created
     * for each <code>ProtectionDomain</code> in the
     * <i>currentDomains</i> array.  Each new <code>ProtectionDomain</code>
     * instance is created using the <code>CodeSource</code>,
     * <code>Permission</code>s and <code>ClassLoader</code>
     * from the corresponding <code>ProtectionDomain</code> in
     * <i>currentDomains</i>, as well as with the Principals from
     * the <code>Subject</code> associated with this
     * <code>SubjectDomainCombiner</code>.
     *
     * <p> All of the newly instantiated ProtectionDomains are
     * combined into a new array.  The ProtectionDomains from the
     * <i>assignedDomains</i> array are appended to this new array,
     * and the result is returned.
     *
     * <p> Note that optimizations such as the removal of duplicate
     * ProtectionDomains may have occurred.
     * In addition, caching of ProtectionDomains may be permitted.
     *
     * <p>
     *
     * @param currentDomains the ProtectionDomains associated with the
     *          current execution Thread, up to the most recent
     *          privileged <code>ProtectionDomain</code>.
     *          The ProtectionDomains are are listed in order of execution,
     *          with the most recently executing <code>ProtectionDomain</code>
     *          residing at the beginning of the array. This parameter may
     *          be <code>null</code> if the current execution Thread
     *          has no associated ProtectionDomains.<p>
     *
     * @param assignedDomains the ProtectionDomains inherited from the
     *          parent Thread, or the ProtectionDomains from the
     *          privileged <i>context</i>, if a call to
     *          AccessController.doPrivileged(..., <i>context</i>)
     *          had occurred  This parameter may be <code>null</code>
     *          if there were no ProtectionDomains inherited from the
     *          parent Thread, or from the privileged <i>context</i>.
     *
     * @return a new array consisting of the updated ProtectionDomains,
     *          or <code>null</code>.
     */
    public ProtectionDomain[] combine(ProtectionDomain[] currentDomains,
                                ProtectionDomain[] assignedDomains) {
        if (debug != null) {
            if (subject == null) {
                debug.println("null subject");
            } else {
                final Subject s = subject;
                AccessController.doPrivileged
                    (new java.security.PrivilegedAction<Void>() {
                    public Void run() {
                        debug.println(s.toString());
                        return null;
                    }
                });
            }
            printInputDomains(currentDomains, assignedDomains);
        }

        if (currentDomains == null || currentDomains.length == 0) {
            // No need to optimize assignedDomains because it should
            // have been previously optimized (when it was set).

            // Note that we are returning a direct reference
            // to the input array - since ACC does not clone
            // the arrays when it calls combiner.combine,
            // multiple ACC instances may share the same
            // array instance in this case

            return assignedDomains;
        }

        // optimize currentDomains
        //
        // No need to optimize assignedDomains because it should
        // have been previously optimized (when it was set).

        currentDomains = optimize(currentDomains);
        if (debug != null) {
            debug.println("after optimize");
            printInputDomains(currentDomains, assignedDomains);
        }

        if (currentDomains == null && assignedDomains == null) {
            return null;
        }

        // maintain backwards compatibility for developers who provide
        // their own custom javax.security.auth.Policy implementations
        if (useJavaxPolicy) {
            return combineJavaxPolicy(currentDomains, assignedDomains);
        }

        int cLen = (currentDomains == null ? 0 : currentDomains.length);
        int aLen = (assignedDomains == null ? 0 : assignedDomains.length);

        // the ProtectionDomains for the new AccessControlContext
        // that we will return
        ProtectionDomain[] newDomains = new ProtectionDomain[cLen + aLen];

        boolean allNew = true;
        synchronized(cachedPDs) {
            if (!subject.isReadOnly() &&
                !subject.getPrincipals().equals(principalSet)) {

                // if the Subject was mutated, clear the PD cache
                Set<Principal> newSet = subject.getPrincipals();
                synchronized(newSet) {
                    principalSet = new java.util.HashSet<Principal>(newSet);
                }
                principals = principalSet.toArray
                        (new Principal[principalSet.size()]);
                cachedPDs.clear();

                if (debug != null) {
                    debug.println("Subject mutated - clearing cache");
                }
            }

            ProtectionDomain subjectPd;
            for (int i = 0; i < cLen; i++) {
                ProtectionDomain pd = currentDomains[i];

                subjectPd = cachedPDs.getValue(pd);

                if (subjectPd == null) {
                    subjectPd = new ProtectionDomain(pd.getCodeSource(),
                                                pd.getPermissions(),
                                                pd.getClassLoader(),
                                                principals);
                    cachedPDs.putValue(pd, subjectPd);
                } else {
                    allNew = false;
                }
                newDomains[i] = subjectPd;
            }
        }

        if (debug != null) {
            debug.println("updated current: ");
            for (int i = 0; i < cLen; i++) {
                debug.println("\tupdated[" + i + "] = " +
                                printDomain(newDomains[i]));
            }
        }

        // now add on the assigned domains
        if (aLen > 0) {
            System.arraycopy(assignedDomains, 0, newDomains, cLen, aLen);

            // optimize the result (cached PDs might exist in assignedDomains)
            if (!allNew) {
                newDomains = optimize(newDomains);
            }
        }

        // if aLen == 0 || allNew, no need to further optimize newDomains

        if (debug != null) {
            if (newDomains == null || newDomains.length == 0) {
                debug.println("returning null");
            } else {
                debug.println("combinedDomains: ");
                for (int i = 0; i < newDomains.length; i++) {
                    debug.println("newDomain " + i + ": " +
                                  printDomain(newDomains[i]));
                }
            }
        }

        // return the new ProtectionDomains
        if (newDomains == null || newDomains.length == 0) {
            return null;
        } else {
            return newDomains;
        }
    }

    /**
     * Use the javax.security.auth.Policy implementation
     */
    private ProtectionDomain[] combineJavaxPolicy(
        ProtectionDomain[] currentDomains,
        ProtectionDomain[] assignedDomains) {

        if (!allowCaching) {
            java.security.AccessController.doPrivileged
                (new PrivilegedAction<Void>() {
                    public Void run() {
                        // Call refresh only caching is disallowed
                        javax.security.auth.Policy.getPolicy().refresh();
                        return null;
                    }
                });
        }

        int cLen = (currentDomains == null ? 0 : currentDomains.length);
        int aLen = (assignedDomains == null ? 0 : assignedDomains.length);

        // the ProtectionDomains for the new AccessControlContext
        // that we will return
        ProtectionDomain[] newDomains = new ProtectionDomain[cLen + aLen];

        synchronized(cachedPDs) {
            if (!subject.isReadOnly() &&
                !subject.getPrincipals().equals(principalSet)) {

                // if the Subject was mutated, clear the PD cache
                Set<Principal> newSet = subject.getPrincipals();
                synchronized(newSet) {
                    principalSet = new java.util.HashSet<Principal>(newSet);
                }
                principals = principalSet.toArray
                        (new Principal[principalSet.size()]);
                cachedPDs.clear();

                if (debug != null) {
                    debug.println("Subject mutated - clearing cache");
                }
            }

            for (int i = 0; i < cLen; i++) {
                ProtectionDomain pd = currentDomains[i];
                ProtectionDomain subjectPd = cachedPDs.getValue(pd);

                if (subjectPd == null) {

                    // XXX
                    // we must first add the original permissions.
                    // that way when we later add the new JAAS permissions,
                    // any unresolved JAAS-related permissions will
                    // automatically get resolved.

                    // get the original perms
                    Permissions perms = new Permissions();
                    PermissionCollection coll = pd.getPermissions();
                    java.util.Enumeration e;
                    if (coll != null) {
                        synchronized (coll) {
                            e = coll.elements();
                            while (e.hasMoreElements()) {
                                Permission newPerm =
                                        (Permission)e.nextElement();
                                 perms.add(newPerm);
                            }
                        }
                    }

                    // get perms from the policy

                    final java.security.CodeSource finalCs = pd.getCodeSource();
                    final Subject finalS = subject;
                    PermissionCollection newPerms =
                        java.security.AccessController.doPrivileged
                        (new PrivilegedAction<PermissionCollection>() {
                        public PermissionCollection run() {
                          return
                          javax.security.auth.Policy.getPolicy().getPermissions
                                (finalS, finalCs);
                        }
                    });

                    // add the newly granted perms,
                    // avoiding duplicates
                    synchronized (newPerms) {
                        e = newPerms.elements();
                        while (e.hasMoreElements()) {
                            Permission newPerm = (Permission)e.nextElement();
                            if (!perms.implies(newPerm)) {
                                perms.add(newPerm);
                                if (debug != null)
                                    debug.println (
                                        "Adding perm " + newPerm + "\n");
                            }
                        }
                    }
                    subjectPd = new ProtectionDomain
                        (finalCs, perms, pd.getClassLoader(), principals);

                    if (allowCaching)
                        cachedPDs.putValue(pd, subjectPd);
                }
                newDomains[i] = subjectPd;
            }
        }

        if (debug != null) {
            debug.println("updated current: ");
            for (int i = 0; i < cLen; i++) {
                debug.println("\tupdated[" + i + "] = " + newDomains[i]);
            }
        }

        // now add on the assigned domains
        if (aLen > 0) {
            System.arraycopy(assignedDomains, 0, newDomains, cLen, aLen);
        }

        if (debug != null) {
            if (newDomains == null || newDomains.length == 0) {
                debug.println("returning null");
            } else {
                debug.println("combinedDomains: ");
                for (int i = 0; i < newDomains.length; i++) {
                    debug.println("newDomain " + i + ": " +
                        newDomains[i].toString());
                }
            }
        }

        // return the new ProtectionDomains
        if (newDomains == null || newDomains.length == 0) {
            return null;
        } else {
            return newDomains;
        }
    }

    private static ProtectionDomain[] optimize(ProtectionDomain[] domains) {
        if (domains == null || domains.length == 0)
            return null;

        ProtectionDomain[] optimized = new ProtectionDomain[domains.length];
        ProtectionDomain pd;
        int num = 0;
        for (int i = 0; i < domains.length; i++) {

            // skip domains with AllPermission
            // XXX
            //
            //  if (domains[i].implies(ALL_PERMISSION))
            //  continue;

            // skip System Domains
            if ((pd = domains[i]) != null) {

                // remove duplicates
                boolean found = false;
                for (int j = 0; j < num && !found; j++) {
                    found = (optimized[j] == pd);
                }
                if (!found) {
                    optimized[num++] = pd;
                }
            }
        }

        // resize the array if necessary
        if (num > 0 && num < domains.length) {
            ProtectionDomain[] downSize = new ProtectionDomain[num];
            System.arraycopy(optimized, 0, downSize, 0, downSize.length);
            optimized = downSize;
        }

        return ((num == 0 || optimized.length == 0) ? null : optimized);
    }

    private static boolean cachePolicy() {
        String s = AccessController.doPrivileged
            (new PrivilegedAction<String>() {
            public String run() {
                return Security.getProperty("cache.auth.policy");
            }
        });
        if (s != null) {
            return Boolean.parseBoolean(s);
        }

        // cache by default
        return true;
    }

    private static void printInputDomains(ProtectionDomain[] currentDomains,
                                ProtectionDomain[] assignedDomains) {
        if (currentDomains == null || currentDomains.length == 0) {
            debug.println("currentDomains null or 0 length");
        } else {
            for (int i = 0; currentDomains != null &&
                        i < currentDomains.length; i++) {
                if (currentDomains[i] == null) {
                    debug.println("currentDomain " + i + ": SystemDomain");
                } else {
                    debug.println("currentDomain " + i + ": " +
                                printDomain(currentDomains[i]));
                }
            }
        }

        if (assignedDomains == null || assignedDomains.length == 0) {
            debug.println("assignedDomains null or 0 length");
        } else {
            debug.println("assignedDomains = ");
            for (int i = 0; assignedDomains != null &&
                        i < assignedDomains.length; i++) {
                if (assignedDomains[i] == null) {
                    debug.println("assignedDomain " + i + ": SystemDomain");
                } else {
                    debug.println("assignedDomain " + i + ": " +
                                printDomain(assignedDomains[i]));
                }
            }
        }
    }

    private static String printDomain(final ProtectionDomain pd) {
        if (pd == null) {
            return "null";
        }
        return AccessController.doPrivileged(new PrivilegedAction<String>() {
            public String run() {
                return pd.toString();
            }
        });
    }

    /**
     * A HashMap that has weak keys and values.
     *
     * Key objects in this map are the "current" ProtectionDomain instances
     * received via the combine method.  Each "current" PD is mapped to a
     * new PD instance that holds both the contents of the "current" PD,
     * as well as the principals from the Subject associated with this combiner.
     *
     * The newly created "principal-based" PD values must be stored as
     * WeakReferences since they contain strong references to the
     * corresponding key object (the "current" non-principal-based PD),
     * which will prevent the key from being GC'd.  Specifically,
     * a "principal-based" PD contains strong references to the CodeSource,
     * signer certs, PermissionCollection and ClassLoader objects
     * in the "current PD".
     */
    private static class WeakKeyValueMap<K,V> extends
                                        WeakHashMap<K,WeakReference<V>> {

        public V getValue(K key) {
            WeakReference<V> wr = super.get(key);
            if (wr != null) {
                return wr.get();
            }
            return null;
        }

        public V putValue(K key, V value) {
            WeakReference<V> wr = super.put(key, new WeakReference<V>(value));
            if (wr != null) {
                return wr.get();
            }
            return null;
        }
    }
}
