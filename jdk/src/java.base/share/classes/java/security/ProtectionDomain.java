/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jdk.internal.misc.JavaSecurityAccess;
import jdk.internal.misc.JavaSecurityProtectionDomainAccess;
import static jdk.internal.misc.JavaSecurityProtectionDomainAccess.ProtectionDomainCache;
import jdk.internal.misc.SharedSecrets;
import sun.security.util.Debug;
import sun.security.util.SecurityConstants;

/**
 * The ProtectionDomain class encapsulates the characteristics of a domain,
 * which encloses a set of classes whose instances are granted a set
 * of permissions when being executed on behalf of a given set of Principals.
 * <p>
 * A static set of permissions can be bound to a ProtectionDomain when it is
 * constructed; such permissions are granted to the domain regardless of the
 * Policy in force. However, to support dynamic security policies, a
 * ProtectionDomain can also be constructed such that it is dynamically
 * mapped to a set of permissions by the current Policy whenever a permission
 * is checked.
 *
 * @author Li Gong
 * @author Roland Schemers
 * @author Gary Ellison
 */

public class ProtectionDomain {

    private static class JavaSecurityAccessImpl implements JavaSecurityAccess {

        private JavaSecurityAccessImpl() {
        }

        @Override
        public <T> T doIntersectionPrivilege(
                PrivilegedAction<T> action,
                final AccessControlContext stack,
                final AccessControlContext context) {
            if (action == null) {
                throw new NullPointerException();
            }

            return AccessController.doPrivileged(
                action,
                getCombinedACC(context, stack)
            );
        }

        @Override
        public <T> T doIntersectionPrivilege(
                PrivilegedAction<T> action,
                AccessControlContext context) {
            return doIntersectionPrivilege(action,
                AccessController.getContext(), context);
        }

        private static AccessControlContext getCombinedACC(
            AccessControlContext context, AccessControlContext stack) {
            AccessControlContext acc =
                new AccessControlContext(context, stack.getCombiner(), true);

            return new AccessControlContext(stack.getContext(), acc).optimize();
        }
    }

    static {
        // setup SharedSecrets to allow access to doIntersectionPrivilege
        // methods and ProtectionDomain cache
        SharedSecrets.setJavaSecurityAccess(new JavaSecurityAccessImpl());
        SharedSecrets.setJavaSecurityProtectionDomainAccess(
            new JavaSecurityProtectionDomainAccess() {
                @Override
                public ProtectionDomainCache getProtectionDomainCache() {
                    return new PDCache();
                }
            });
    }

    /**
     * Used for storing ProtectionDomains as keys in a Map.
     */
    static final class Key {}

    /* CodeSource */
    private CodeSource codesource ;

    /* ClassLoader the protection domain was consed from */
    private ClassLoader classloader;

    /* Principals running-as within this protection domain */
    private Principal[] principals;

    /* the rights this protection domain is granted */
    private PermissionCollection permissions;

    /* if the permissions object has AllPermission */
    private boolean hasAllPerm = false;

    /* the PermissionCollection is static (pre 1.4 constructor)
       or dynamic (via a policy refresh) */
    private boolean staticPermissions;

    /*
     * An object used as a key when the ProtectionDomain is stored in a Map.
     */
    final Key key = new Key();

    private static final Debug debug = Debug.getInstance("domain");

    /**
     * Creates a new ProtectionDomain with the given CodeSource and
     * Permissions. If the permissions object is not null, then
     *  {@code setReadOnly())} will be called on the passed in
     * Permissions object. The only permissions granted to this domain
     * are the ones specified; the current Policy will not be consulted.
     *
     * @param codesource the codesource associated with this domain
     * @param permissions the permissions granted to this domain
     */
    public ProtectionDomain(CodeSource codesource,
                            PermissionCollection permissions) {
        this.codesource = codesource;
        if (permissions != null) {
            this.permissions = permissions;
            this.permissions.setReadOnly();
            if (permissions instanceof Permissions &&
                ((Permissions)permissions).allPermission != null) {
                hasAllPerm = true;
            }
        }
        this.classloader = null;
        this.principals = new Principal[0];
        staticPermissions = true;
    }

    /**
     * Creates a new ProtectionDomain qualified by the given CodeSource,
     * Permissions, ClassLoader and array of Principals. If the
     * permissions object is not null, then {@code setReadOnly()}
     * will be called on the passed in Permissions object.
     * The permissions granted to this domain are dynamic; they include
     * both the static permissions passed to this constructor, and any
     * permissions granted to this domain by the current Policy at the
     * time a permission is checked.
     * <p>
     * This constructor is typically used by
     * {@link SecureClassLoader ClassLoaders}
     * and {@link DomainCombiner DomainCombiners} which delegate to
     * {@code Policy} to actively associate the permissions granted to
     * this domain. This constructor affords the
     * Policy provider the opportunity to augment the supplied
     * PermissionCollection to reflect policy changes.
     *
     * @param codesource the CodeSource associated with this domain
     * @param permissions the permissions granted to this domain
     * @param classloader the ClassLoader associated with this domain
     * @param principals the array of Principals associated with this
     * domain. The contents of the array are copied to protect against
     * subsequent modification.
     * @see Policy#refresh
     * @see Policy#getPermissions(ProtectionDomain)
     * @since 1.4
     */
    public ProtectionDomain(CodeSource codesource,
                            PermissionCollection permissions,
                            ClassLoader classloader,
                            Principal[] principals) {
        this.codesource = codesource;
        if (permissions != null) {
            this.permissions = permissions;
            this.permissions.setReadOnly();
            if (permissions instanceof Permissions &&
                ((Permissions)permissions).allPermission != null) {
                hasAllPerm = true;
            }
        }
        this.classloader = classloader;
        this.principals = (principals != null ? principals.clone():
                           new Principal[0]);
        staticPermissions = false;
    }

    /**
     * Returns the CodeSource of this domain.
     * @return the CodeSource of this domain which may be null.
     * @since 1.2
     */
    public final CodeSource getCodeSource() {
        return this.codesource;
    }


    /**
     * Returns the ClassLoader of this domain.
     * @return the ClassLoader of this domain which may be null.
     *
     * @since 1.4
     */
    public final ClassLoader getClassLoader() {
        return this.classloader;
    }


    /**
     * Returns an array of principals for this domain.
     * @return a non-null array of principals for this domain.
     * Returns a new array each time this method is called.
     *
     * @since 1.4
     */
    public final Principal[] getPrincipals() {
        return this.principals.clone();
    }

    /**
     * Returns the static permissions granted to this domain.
     *
     * @return the static set of permissions for this domain which may be null.
     * @see Policy#refresh
     * @see Policy#getPermissions(ProtectionDomain)
     */
    public final PermissionCollection getPermissions() {
        return permissions;
    }

    /**
     * Check and see if this ProtectionDomain implies the permissions
     * expressed in the Permission object.
     * <p>
     * The set of permissions evaluated is a function of whether the
     * ProtectionDomain was constructed with a static set of permissions
     * or it was bound to a dynamically mapped set of permissions.
     * <p>
     * If the ProtectionDomain was constructed to a
     * {@link #ProtectionDomain(CodeSource, PermissionCollection)
     * statically bound} PermissionCollection then the permission will
     * only be checked against the PermissionCollection supplied at
     * construction.
     * <p>
     * However, if the ProtectionDomain was constructed with
     * the constructor variant which supports
     * {@link #ProtectionDomain(CodeSource, PermissionCollection,
     * ClassLoader, java.security.Principal[]) dynamically binding}
     * permissions, then the permission will be checked against the
     * combination of the PermissionCollection supplied at construction and
     * the current Policy binding.
     *
     * @param permission the Permission object to check.
     *
     * @return true if "permission" is implicit to this ProtectionDomain.
     */
    public boolean implies(Permission permission) {

        if (hasAllPerm) {
            // internal permission collection already has AllPermission -
            // no need to go to policy
            return true;
        }

        if (!staticPermissions &&
            Policy.getPolicyNoCheck().implies(this, permission))
            return true;
        if (permissions != null)
            return permissions.implies(permission);

        return false;
    }

    // called by the VM -- do not remove
    boolean impliesCreateAccessControlContext() {
        return implies(SecurityConstants.CREATE_ACC_PERMISSION);
    }

    /**
     * Convert a ProtectionDomain to a String.
     */
    @Override public String toString() {
        String pals = "<no principals>";
        if (principals != null && principals.length > 0) {
            StringBuilder palBuf = new StringBuilder("(principals ");

            for (int i = 0; i < principals.length; i++) {
                palBuf.append(principals[i].getClass().getName() +
                            " \"" + principals[i].getName() +
                            "\"");
                if (i < principals.length-1)
                    palBuf.append(",\n");
                else
                    palBuf.append(")\n");
            }
            pals = palBuf.toString();
        }

        // Check if policy is set; we don't want to load
        // the policy prematurely here
        PermissionCollection pc = Policy.isSet() && seeAllp() ?
                                      mergePermissions():
                                      getPermissions();

        return "ProtectionDomain "+
            " "+codesource+"\n"+
            " "+classloader+"\n"+
            " "+pals+"\n"+
            " "+pc+"\n";
    }

    /**
     * Return true (merge policy permissions) in the following cases:
     *
     * . SecurityManager is null
     *
     * . SecurityManager is not null,
     *          debug is not null,
     *          SecurityManager impelmentation is in bootclasspath,
     *          Policy implementation is in bootclasspath
     *          (the bootclasspath restrictions avoid recursion)
     *
     * . SecurityManager is not null,
     *          debug is null,
     *          caller has Policy.getPolicy permission
     */
    private static boolean seeAllp() {
        SecurityManager sm = System.getSecurityManager();

        if (sm == null) {
            return true;
        } else {
            if (debug != null) {
                if (sm.getClass().getClassLoader() == null &&
                    Policy.getPolicyNoCheck().getClass().getClassLoader()
                                                                == null) {
                    return true;
                }
            } else {
                try {
                    sm.checkPermission(SecurityConstants.GET_POLICY_PERMISSION);
                    return true;
                } catch (SecurityException se) {
                    // fall thru and return false
                }
            }
        }

        return false;
    }

    private PermissionCollection mergePermissions() {
        if (staticPermissions)
            return permissions;

        PermissionCollection perms =
            java.security.AccessController.doPrivileged
            (new java.security.PrivilegedAction<>() {
                    public PermissionCollection run() {
                        Policy p = Policy.getPolicyNoCheck();
                        return p.getPermissions(ProtectionDomain.this);
                    }
                });

        Permissions mergedPerms = new Permissions();
        int swag = 32;
        int vcap = 8;
        Enumeration<Permission> e;
        List<Permission> pdVector = new ArrayList<>(vcap);
        List<Permission> plVector = new ArrayList<>(swag);

        //
        // Build a vector of domain permissions for subsequent merge
        if (permissions != null) {
            synchronized (permissions) {
                e = permissions.elements();
                while (e.hasMoreElements()) {
                    pdVector.add(e.nextElement());
                }
            }
        }

        //
        // Build a vector of Policy permissions for subsequent merge
        if (perms != null) {
            synchronized (perms) {
                e = perms.elements();
                while (e.hasMoreElements()) {
                    plVector.add(e.nextElement());
                    vcap++;
                }
            }
        }

        if (perms != null && permissions != null) {
            //
            // Weed out the duplicates from the policy. Unless a refresh
            // has occurred since the pd was consed this should result in
            // an empty vector.
            synchronized (permissions) {
                e = permissions.elements();   // domain vs policy
                while (e.hasMoreElements()) {
                    Permission pdp = e.nextElement();
                    Class<?> pdpClass = pdp.getClass();
                    String pdpActions = pdp.getActions();
                    String pdpName = pdp.getName();
                    for (int i = 0; i < plVector.size(); i++) {
                        Permission pp = plVector.get(i);
                        if (pdpClass.isInstance(pp)) {
                            // The equals() method on some permissions
                            // have some side effects so this manual
                            // comparison is sufficient.
                            if (pdpName.equals(pp.getName()) &&
                                pdpActions.equals(pp.getActions())) {
                                plVector.remove(i);
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (perms !=null) {
            // the order of adding to merged perms and permissions
            // needs to preserve the bugfix 4301064

            for (int i = plVector.size()-1; i >= 0; i--) {
                mergedPerms.add(plVector.get(i));
            }
        }
        if (permissions != null) {
            for (int i = pdVector.size()-1; i >= 0; i--) {
                mergedPerms.add(pdVector.get(i));
            }
        }

        return mergedPerms;
    }

    /**
     * A cache of ProtectionDomains and their Permissions.
     *
     * This class stores ProtectionDomains as weak keys in a ConcurrentHashMap
     * with additional support for checking and removing weak keys that are no
     * longer in use. There can be cases where the permission collection may
     * have a chain of strong references back to the ProtectionDomain, which
     * ordinarily would prevent the entry from being removed from the map. To
     * address that, we wrap the permission collection in a SoftReference so
     * that it can be reclaimed by the garbage collector due to memory demand.
     */
    private static class PDCache implements ProtectionDomainCache {
        private final ConcurrentHashMap<WeakProtectionDomainKey,
                                        SoftReference<PermissionCollection>>
                                        pdMap = new ConcurrentHashMap<>();
        private final ReferenceQueue<Key> queue = new ReferenceQueue<>();

        @Override
        public void put(ProtectionDomain pd, PermissionCollection pc) {
            processQueue(queue, pdMap);
            WeakProtectionDomainKey weakPd =
                new WeakProtectionDomainKey(pd, queue);
            pdMap.put(weakPd, new SoftReference<>(pc));
        }

        @Override
        public PermissionCollection get(ProtectionDomain pd) {
            processQueue(queue, pdMap);
            WeakProtectionDomainKey weakPd = new WeakProtectionDomainKey(pd);
            SoftReference<PermissionCollection> sr = pdMap.get(weakPd);
            return (sr == null) ? null : sr.get();
        }

        /**
         * Removes weak keys from the map that have been enqueued
         * on the reference queue and are no longer in use.
         */
        private static void processQueue(ReferenceQueue<Key> queue,
                                         ConcurrentHashMap<? extends
                                         WeakReference<Key>, ?> pdMap) {
            Reference<? extends Key> ref;
            while ((ref = queue.poll()) != null) {
                pdMap.remove(ref);
            }
        }
    }

    /**
     * A weak key for a ProtectionDomain.
     */
    private static class WeakProtectionDomainKey extends WeakReference<Key> {
        /**
         * Saved value of the referent's identity hash code, to maintain
         * a consistent hash code after the referent has been cleared
         */
        private final int hash;

        /**
         * A key representing a null ProtectionDomain.
         */
        private static final Key NULL_KEY = new Key();

        /**
         * Create a new WeakProtectionDomain with the specified domain and
         * registered with a queue.
         */
        WeakProtectionDomainKey(ProtectionDomain pd, ReferenceQueue<Key> rq) {
            this((pd == null ? NULL_KEY : pd.key), rq);
        }

        WeakProtectionDomainKey(ProtectionDomain pd) {
            this(pd == null ? NULL_KEY : pd.key);
        }

        private WeakProtectionDomainKey(Key key, ReferenceQueue<Key> rq) {
            super(key, rq);
            hash = key.hashCode();
        }

        private WeakProtectionDomainKey(Key key) {
            super(key);
            hash = key.hashCode();
        }

        /**
         * Returns the identity hash code of the original referent.
         */
        @Override
        public int hashCode() {
            return hash;
        }

        /**
         * Returns true if the given object is an identical
         * WeakProtectionDomainKey instance, or, if this object's referent
         * has not been cleared and the given object is another
         * WeakProtectionDomainKey instance with an identical non-null
         * referent as this one.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }

            if (obj instanceof WeakProtectionDomainKey) {
                Object referent = get();
                return (referent != null) &&
                       (referent == ((WeakProtectionDomainKey)obj).get());
            } else {
                return false;
            }
        }
    }
}
