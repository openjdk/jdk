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

/**
 * The {@code ProtectionDomain} class encapsulates the characteristics of a
 * domain, which encloses a set of classes whose instances are granted a set
 * of permissions.
 * <p>
 * A static set of permissions can be bound to a {@code ProtectionDomain}
 * when it is constructed; such permissions are granted to the domain
 * regardless of the policy in force. However, to support dynamic security
 * policies, a {@code ProtectionDomain} can also be constructed such that it
 * is dynamically mapped to a set of permissions by the current policy.
 *
 * @apiNote Installing a system-wide {@link Policy} object is
 * no longer supported. The {@linkplain Policy#getPolicy current policy}
 * is always a {@code Policy} object that grants no permissions.
 *
 * @author Li Gong
 * @author Roland Schemers
 * @author Gary Ellison
 * @since 1.2
 */

public class ProtectionDomain {

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
     * @apiNote Installing a system-wide {@link Policy} object is
     * no longer supported. The {@linkplain Policy#getPolicy current policy}
     * is always a {@code Policy} object that grants no permissions.
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
     * and any permissions granted to this domain by the current policy.
     *
     * @apiNote Installing a system-wide {@link Policy} object is
     * no longer supported. The {@linkplain Policy#getPolicy current policy}
     * is always a {@code Policy} object that grants no permissions.
     *
     * @param codesource the {@code CodeSource} associated with this domain
     * @param permissions the permissions granted to this domain
     * @param classloader the {@code ClassLoader} associated with this domain
     * @param principals the array of {@code Principal} objects associated
     * with this domain. The contents of the array are copied to protect against
     * subsequent modification.
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
     * and does not check the current {@code Policy}.
     *
     * @apiNote Installing a system-wide {@link Policy} object is
     * no longer supported. The {@linkplain Policy#getPolicy current policy}
     * is always a {@code Policy} object that grants no permissions.
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
     * the current policy.
     *
     * @apiNote Installing a system-wide {@link Policy} object is
     * no longer supported. The {@linkplain Policy#getPolicy current policy}
     * is always a {@code Policy} object that grants no permissions.
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
            Policy.getPolicy().implies(this, perm)) {
            return true;
        }
        if (permissions != null) {
            return permissions.implies(perm);
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

        return "ProtectionDomain "+
            " "+codesource+"\n"+
            " "+classloader+"\n"+
            " "+pals+"\n"+
            " "+permissions+"\n";
    }
}
