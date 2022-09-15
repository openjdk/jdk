/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import jdk.internal.access.JavaSecurityAccess;
import jdk.internal.access.SharedSecrets;
import sun.security.action.GetPropertyAction;
import sun.security.provider.PolicyFile;
import sun.security.util.Debug;
import sun.security.util.FilePermCompat;
import sun.security.util.SecurityConstants;

/**
 * The {@code ProtectionDomain} class encapsulates the characteristics of a
 * domain, which encloses a set of classes whose instances are granted a set
 * of permissions when being executed on behalf of a given set of Principals.
 * <p>
 * A static set of permissions can be bound to a {@code ProtectionDomain}
 * when it is constructed; such permissions are granted to the domain
 * regardless of the policy in force. However, to support dynamic security
 * policies, a {@code ProtectionDomain} can also be constructed such that it
 * is dynamically mapped to a set of permissions by the current policy whenever
 * a permission is checked.
 *
 * @author Li Gong
 * @author Roland Schemers
 * @author Gary Ellison
 * @since 1.2
 */

public class ProtectionDomain {

    /**
     * If {@code true}, {@link #impliesWithAltFilePerm} will try to be
     * compatible on FilePermission checking even if a 3rd-party Policy
     * implementation is set.
     */
    private static final boolean filePermCompatInPD =
            "true".equals(GetPropertyAction.privilegedGetProperty(
                "jdk.security.filePermCompat"));

    private static class JavaSecurityAccessImpl implements JavaSecurityAccess {

        private JavaSecurityAccessImpl() {
        }

        @SuppressWarnings("removal")
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

        @SuppressWarnings("removal")
        @Override
        public <T> T doIntersectionPrivilege(
                PrivilegedAction<T> action,
                AccessControlContext context) {
            return doIntersectionPrivilege(action,
                AccessController.getContext(), context);
        }

        @Override
        public ProtectionDomain[] getProtectDomains(@SuppressWarnings("removal") AccessControlContext context) {
            return context.getContext();
        }

        @SuppressWarnings("removal")
        private static AccessControlContext getCombinedACC(
            AccessControlContext context, AccessControlContext stack) {
            AccessControlContext acc =
                new AccessControlContext(context, stack.getCombiner(), true);

            return new AccessControlContext(stack.getContext(), acc).optimize();
        }

        @Override
        public ProtectionDomainCache getProtectionDomainCache() {
            return new ProtectionDomainCache() {
                private final Map<Key, PermissionCollection> map =
                        Collections.synchronizedMap(new WeakHashMap<>());
                public void put(ProtectionDomain pd,
                                PermissionCollection pc) {
                    map.put((pd == null ? null : pd.key), pc);
                }
                public PermissionCollection get(ProtectionDomain pd) {
                    return pd == null ? map.get(null) : map.get(pd.key);
                }
            };
        }
    }

    static {
        // Set up JavaSecurityAccess in SharedSecrets
        SharedSecrets.setJavaSecurityAccess(new JavaSecurityAccessImpl());
    }

    /* CodeSource */
    private final CodeSource codesource ;

    /* ClassLoader the protection domain was consed from */
    private final ClassLoader classloader;

    /* Principals running-as within this protection domain */
    private final Principal[] principals;

    /* the rights this protection domain is granted */
    private PermissionCollection permissions;

    /* if the permissions object has AllPermission */
    private boolean hasAllPerm = false;

    /* the PermissionCollection is static (pre 1.4 constructor)
       or dynamic (via a policy refresh) */
    private final boolean staticPermissions;

    /*
     * An object used as a key when the ProtectionDomain is stored in a Map.
     */
    final Key key = new Key();

    /**
     * Creates a new {@code ProtectionDomain} with the given {@code CodeSource}
     * and permissions. If permissions is not {@code null}, then
     * {@code setReadOnly()} will be called on the passed in
     * permissions.
     * <p>
     * The permissions granted to this domain are static, i.e.
     * invoking the {@link #staticPermissionsOnly()} method returns
     * {@code true}.
     * They contain only the ones passed to this constructor and
     * the current policy will not be consulted.
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
     * Creates a new {@code ProtectionDomain} qualified by the given
     * {@code CodeSource}, permissions, {@code ClassLoader} and array
     * of principals. If permissions is not {@code null}, then
     * {@code setReadOnly()} will be called on the passed in permissions.
     * <p>
     * The permissions granted to this domain are dynamic, i.e.
     * invoking the {@link #staticPermissionsOnly()} method returns
     * {@code false}.
     * They include both the static permissions passed to this constructor,
     * and any permissions granted to this domain by the current policy at the
     * time a permission is checked.
     * <p>
     * This constructor is typically used by
     * {@link SecureClassLoader ClassLoaders}
     * and {@link DomainCombiner DomainCombiners} which delegate to the
     * {@code Policy} object to actively associate the permissions granted to
     * this domain. This constructor affords the
     * policy provider the opportunity to augment the supplied
     * {@code PermissionCollection} to reflect policy changes.
     *
     * @param codesource the {@code CodeSource} associated with this domain
     * @param permissions the permissions granted to this domain
     * @param classloader the {@code ClassLoader} associated with this domain
     * @param principals the array of {@code Principal} objects associated
     * with this domain. The contents of the array are copied to protect against
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
     * Returns the {@code CodeSource} of this domain.
     * @return the {@code CodeSource} of this domain which may be {@code null}.
     * @since 1.2
     */
    public final CodeSource getCodeSource() {
        return this.codesource;
    }


    /**
     * Returns the {@code ClassLoader} of this domain.
     * @return the {@code ClassLoader} of this domain which may be {@code null}.
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
     * @return the static set of permissions for this domain which may be
     * {@code null}.
     * @see Policy#refresh
     * @see Policy#getPermissions(ProtectionDomain)
     */
    public final PermissionCollection getPermissions() {
        return permissions;
    }

    /**
     * Returns {@code true} if this domain contains only static permissions
     * and does not check the current {@code Policy} at the time of
     * permission checking.
     *
     * @return {@code true} if this domain contains only static permissions.
     *
     * @since 9
     */
    public final boolean staticPermissionsOnly() {
        return this.staticPermissions;
    }

    /**
     * Check and see if this {@code ProtectionDomain} implies the permissions
     * expressed in the {@code Permission} object.
     * <p>
     * The set of permissions evaluated is a function of whether the
     * {@code ProtectionDomain} was constructed with a static set of permissions
     * or it was bound to a dynamically mapped set of permissions.
     * <p>
     * If the {@link #staticPermissionsOnly()} method returns
     * {@code true}, then the permission will only be checked against the
     * {@code PermissionCollection} supplied at construction.
     * <p>
     * Otherwise, the permission will be checked against the combination
     * of the {@code PermissionCollection} supplied at construction and
     * the current policy binding.
     *
     * @param perm the {code Permission} object to check.
     *
     * @return {@code true} if {@code perm} is implied by this
     * {@code ProtectionDomain}.
     */
    @SuppressWarnings("removal")
    public boolean implies(Permission perm) {

        if (hasAllPerm) {
            // internal permission collection already has AllPermission -
            // no need to go to policy
            return true;
        }

        if (!staticPermissions &&
            Policy.getPolicyNoCheck().implies(this, perm)) {
            return true;
        }
        if (permissions != null) {
            return permissions.implies(perm);
        }

        return false;
    }

    /**
     * This method has almost the same logic flow as {@link #implies} but
     * it ensures some level of FilePermission compatibility after JDK-8164705.
     *
     * This method is called by {@link AccessControlContext#checkPermission}
     * and not intended to be called by an application.
     */
    boolean impliesWithAltFilePerm(Permission perm) {

        // If FilePermCompat.compat is set (default value), FilePermission
        // checking compatibility should be considered.

        // If filePermCompatInPD is set, this method checks for alternative
        // FilePermission to keep compatibility for any Policy implementation.
        // When set to false (default value), implies() is called since
        // the PolicyFile implementation already supports compatibility.

        // If this is a subclass of ProtectionDomain, call implies()
        // because most likely user has overridden it.

        if (!filePermCompatInPD || !FilePermCompat.compat ||
                getClass() != ProtectionDomain.class) {
            return implies(perm);
        }

        if (hasAllPerm) {
            // internal permission collection already has AllPermission -
            // no need to go to policy
            return true;
        }

        Permission p2 = null;
        boolean p2Calculated = false;

        if (!staticPermissions) {
            @SuppressWarnings("removal")
            Policy policy = Policy.getPolicyNoCheck();
            if (policy instanceof PolicyFile) {
                // The PolicyFile implementation supports compatibility
                // inside, and it also covers the static permissions.
                return policy.implies(this, perm);
            } else {
                if (policy.implies(this, perm)) {
                    return true;
                }
                p2 = FilePermCompat.newPermUsingAltPath(perm);
                p2Calculated = true;
                if (p2 != null && policy.implies(this, p2)) {
                    return true;
                }
            }
        }
        if (permissions != null) {
            if (permissions.implies(perm)) {
                return true;
            } else {
                if (!p2Calculated) {
                    p2 = FilePermCompat.newPermUsingAltPath(perm);
                }
                if (p2 != null) {
                    return permissions.implies(p2);
                }
            }
        }
        return false;
    }

    /**
     * Convert a {@code ProtectionDomain} to a {@code String}.
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
        @SuppressWarnings("removal")
        PermissionCollection pc = Policy.isSet() && seeAllp() ?
                                      mergePermissions():
                                      getPermissions();

        return "ProtectionDomain "+
            " "+codesource+"\n"+
            " "+classloader+"\n"+
            " "+pals+"\n"+
            " "+pc+"\n";
    }

    /*
     * holder class for the static field "debug" to delay its initialization
     */
    private static class DebugHolder {
        private static final Debug debug = Debug.getInstance("domain");
    }

    /**
     * Return {@code true} (merge policy permissions) in the following cases:
     *
     * . SecurityManager is {@code null}
     *
     * . SecurityManager is not {@code null},
     *          debug is not {@code null},
     *          SecurityManager implementation is in bootclasspath,
     *          Policy implementation is in bootclasspath
     *          (the bootclasspath restrictions avoid recursion)
     *
     * . SecurityManager is not {@code null},
     *          debug is {@code null},
     *          caller has Policy.getPolicy permission
     */
    @SuppressWarnings("removal")
    private static boolean seeAllp() {
        SecurityManager sm = System.getSecurityManager();

        if (sm == null) {
            return true;
        } else {
            if (DebugHolder.debug != null) {
                return sm.getClass().getClassLoader() == null &&
                        Policy.getPolicyNoCheck().getClass().getClassLoader()
                                == null;
            } else {
                try {
                    sm.checkPermission(SecurityConstants.GET_POLICY_PERMISSION);
                    return true;
                } catch (SecurityException se) {
                    return false;
                }
            }
        }
    }

    private PermissionCollection mergePermissions() {
        if (staticPermissions)
            return permissions;

        @SuppressWarnings("removal")
        PermissionCollection perms =
            java.security.AccessController.doPrivileged
            ((PrivilegedAction<PermissionCollection>) () ->
                Policy.getPolicyNoCheck().getPermissions(ProtectionDomain.this));

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
                                Objects.equals(pdpActions, pp.getActions())) {
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
     * Used for storing ProtectionDomains as keys in a Map.
     */
    static final class Key {}

}
