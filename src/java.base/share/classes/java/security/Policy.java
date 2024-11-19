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

import java.util.Enumeration;
import java.util.Objects;
import sun.security.jca.GetInstance;

/**
 * A {@code Policy} object was responsible for determining whether code
 * executing in the Java runtime environment had permission to perform a
 * security-sensitive operation. This feature no longer exists.
 *
 * @author Roland Schemers
 * @author Gary Ellison
 * @since 1.2
 * @deprecated This class was only useful in conjunction with
 *       {@linkplain SecurityManager the Security Manager}, which is no longer
 *       supported. Installing a system-wide {@code Policy} object is no longer
 *       supported. The {@linkplain #setPolicy setPolicy} method has been
 *       changed to always throw {@code UnsupportedOperationException}. The
 *       {@linkplain getPolicy getPolicy} method has been changed to always
 *       return a {@code Policy} object that grants no permissions. There is no
 *       replacement for the Security Manager or this class.
 */

@Deprecated(since="17", forRemoval=true)
public abstract class Policy {

    private static Policy NO_PERMISSIONS_POLICY = new Policy() {};

    /**
     * Constructor for subclasses to call.
     */
    public Policy() {}

    /**
     * A read-only empty PermissionCollection instance.
     * @since 1.6
     */
    public static final PermissionCollection UNSUPPORTED_EMPTY_COLLECTION =
                        new UnsupportedEmptyCollection();

    /**
     * Returns a {@code Policy} object that grants no permissions.
     * Specifically:
     *
     * <ul>
     *     <li> The {@code getParameters} method returns {@code null}. </li>
     *     <li> The {@code getPermissions(CodeSource)} and
     *     {@code getPermissions(ProtectionDomain)} methods return a read-only
     *     empty {@code PermissionCollection}. </li>
     *     <li> The {@code implies} method always returns {@code false}. </li>
     * </ul>
     *
     * @return a {@code Policy} object that grants no permissions
     *
     * @apiNote This method originally returned the installed {@code Policy}
     *    object, or if no {@code Policy} object had been installed, a default
     *    {@code Policy} implementation. Installing a system-wide {@code Policy}
     *    object is no longer supported. This method always returns a
     *    default {@code Policy} object that grants no permissions. A
     *    {@code Policy} object was only useful in conjunction with
     *    {@linkplain SecurityManager the Security Manager}, which is no
     *    longer supported. There is no replacement for this method.
     *
     * @see #setPolicy(java.security.Policy)
     */
    public static Policy getPolicy()
    {
        return NO_PERMISSIONS_POLICY;
    }

    /**
     * Throws {@code UnsupportedOperationException}. Setting a system-wide
     * {@code Policy} object is not supported.
     *
     * @param p ignored
     * @throws UnsupportedOperationException always
     * @apiNote This method originally installed the system-wide
     *    {@code Policy} object. Installing a system-wide {@code Policy} object
     *    is no longer supported. A {@code Policy} object was only useful in
     *    conjunction with {@linkplain SecurityManager the Security Manager},
     *    which is no longer supported. There is no replacement for this method.
     *
     * @see #getPolicy()
     */
    public static void setPolicy(Policy p)
    {
        throw new UnsupportedOperationException(
                "Setting a system-wide Policy object is not supported");
    }

    /**
     * Returns a Policy object of the specified type.
     *
     * <p> This method traverses the list of registered security providers,
     * starting with the most preferred provider.
     * A new {@code Policy} object encapsulating the
     * {@code PolicySpi} implementation from the first
     * provider that supports the specified type is returned.
     *
     * <p> Note that the list of registered providers may be retrieved via
     * the {@link Security#getProviders() Security.getProviders()} method.
     *
     * @implNote
     * The JDK Reference Implementation additionally uses the
     * {@code jdk.security.provider.preferred}
     * {@link Security#getProperty(String) Security} property to determine
     * the preferred provider order for the specified algorithm. This
     * may be different than the order of providers returned by
     * {@link Security#getProviders() Security.getProviders()}.
     *
     * @param type the specified Policy type
     *
     * @param params parameters for the {@code Policy}, which may be
     * {@code null}.
     *
     * @return the new {@code Policy} object
     *
     * @throws IllegalArgumentException if the specified parameters
     *         are not understood by the {@code PolicySpi} implementation
     *         from the selected {@code Provider}
     *
     * @throws NoSuchAlgorithmException if no {@code Provider} supports
     *         a {@code PolicySpi} implementation for the specified type
     *
     * @throws NullPointerException if {@code type} is {@code null}
     *
     * @see Provider
     * @since 1.6
     */
    @SuppressWarnings("removal")
    public static Policy getInstance(String type, Policy.Parameters params)
                throws NoSuchAlgorithmException {
        Objects.requireNonNull(type, "null type name");
        try {
            GetInstance.Instance instance = GetInstance.getInstance("Policy",
                                                        PolicySpi.class,
                                                        type,
                                                        params);
            return new PolicyDelegate((PolicySpi)instance.impl,
                                                        instance.provider,
                                                        type,
                                                        params);
        } catch (NoSuchAlgorithmException nsae) {
            return handleException(nsae);
        }
    }

    /**
     * Returns a {@code Policy} object of the specified type.
     *
     * <p> A new {@code Policy} object encapsulating the
     * {@code PolicySpi} implementation from the specified provider
     * is returned.   The specified provider must be registered
     * in the provider list.
     *
     * <p> Note that the list of registered providers may be retrieved via
     * the {@link Security#getProviders() Security.getProviders()} method.
     *
     * @param type the specified Policy type
     *
     * @param params parameters for the {@code Policy}, which may be
     * {@code null}.
     *
     * @param provider the provider.
     *
     * @return the new {@code Policy} object
     *
     * @throws IllegalArgumentException if the specified provider
     *         is {@code null} or empty, or if the specified parameters are
     *         not understood by the {@code PolicySpi} implementation from
     *         the specified provider
     *
     * @throws NoSuchAlgorithmException if the specified provider does not
     *         support a {@code PolicySpi} implementation for the specified
     *         type
     *
     * @throws NoSuchProviderException if the specified provider is not
     *         registered in the security provider list
     *
     * @throws NullPointerException if {@code type} is {@code null}
     *
     * @see Provider
     * @since 1.6
     */
    @SuppressWarnings("removal")
    public static Policy getInstance(String type,
                                Policy.Parameters params,
                                String provider)
                throws NoSuchProviderException, NoSuchAlgorithmException {

        Objects.requireNonNull(type, "null type name");
        if (provider == null || provider.isEmpty()) {
            throw new IllegalArgumentException("missing provider");
        }

        try {
            GetInstance.Instance instance = GetInstance.getInstance("Policy",
                                                        PolicySpi.class,
                                                        type,
                                                        params,
                                                        provider);
            return new PolicyDelegate((PolicySpi)instance.impl,
                                                        instance.provider,
                                                        type,
                                                        params);
        } catch (NoSuchAlgorithmException nsae) {
            return handleException(nsae);
        }
    }

    /**
     * Returns a {@code Policy} object of the specified type.
     *
     * <p> A new {@code Policy} object encapsulating the
     * {@code PolicySpi} implementation from the specified provider
     * is returned.  Note that the specified provider does not
     * have to be registered in the provider list.
     *
     * @param type the specified Policy type
     *
     * @param params parameters for the {@code Policy}, which may be
     * {@code null}.
     *
     * @param provider the {@code Provider}.
     *
     * @return the new {@code Policy} object
     *
     * @throws IllegalArgumentException if the specified {@code Provider}
     *         is {@code null}, or if the specified parameters are not
     *         understood by the {@code PolicySpi} implementation from the
     *         specified {@code Provider}
     *
     * @throws NoSuchAlgorithmException if the specified {@code Provider}
     *         does not support a {@code PolicySpi} implementation for
     *         the specified type
     *
     * @throws NullPointerException if {@code type} is {@code null}
     *
     * @see Provider
     * @since 1.6
     */
    @SuppressWarnings("removal")
    public static Policy getInstance(String type,
                                Policy.Parameters params,
                                Provider provider)
                throws NoSuchAlgorithmException {

        Objects.requireNonNull(type, "null type name");
        if (provider == null) {
            throw new IllegalArgumentException("missing provider");
        }

        try {
            GetInstance.Instance instance = GetInstance.getInstance("Policy",
                                                        PolicySpi.class,
                                                        type,
                                                        params,
                                                        provider);
            return new PolicyDelegate((PolicySpi)instance.impl,
                                                        instance.provider,
                                                        type,
                                                        params);
        } catch (NoSuchAlgorithmException nsae) {
            return handleException(nsae);
        }
    }

    private static Policy handleException(NoSuchAlgorithmException nsae)
                throws NoSuchAlgorithmException {
        Throwable cause = nsae.getCause();
        if (cause instanceof IllegalArgumentException) {
            throw (IllegalArgumentException)cause;
        }
        throw nsae;
    }

    /**
     * Return the {@code Provider} of this policy.
     *
     * <p> This {@code Policy} instance will only have a provider if it
     * was obtained via a call to {@code Policy.getInstance}.
     * Otherwise this method returns {@code null}.
     *
     * @return the {@code Provider} of this policy, or {@code null}.
     *
     * @since 1.6
     */
    public Provider getProvider() {
        return null;
    }

    /**
     * Return the type of this {@code Policy}.
     *
     * <p> This {@code Policy} instance will only have a type if it
     * was obtained via a call to {@code Policy.getInstance}.
     * Otherwise this method returns {@code null}.
     *
     * @return the type of this {@code Policy}, or {@code null}.
     *
     * @since 1.6
     */
    public String getType() {
        return null;
    }

    /**
     * Return {@code Policy} parameters.
     *
     * <p> This {@code Policy} instance will only have parameters if it
     * was obtained via a call to {@code Policy.getInstance}.
     * Otherwise this method returns {@code null}.
     *
     * @return {@code Policy} parameters, or {@code null}.
     *
     * @since 1.6
     */
    public Policy.Parameters getParameters() {
        return null;
    }

    /**
     * Return a PermissionCollection object containing the set of
     * permissions granted to the specified CodeSource.
     *
     * <p> The default implementation of this method ignores the
     * CodeSource and returns Policy.UNSUPPORTED_EMPTY_COLLECTION.
     *
     * @param codesource ignored
     *
     * @return a set of permissions granted to the specified CodeSource.
     *          If this operation is supported, the returned
     *          set of permissions must be a new mutable instance
     *          and it must support heterogeneous Permission types.
     *          If this operation is not supported,
     *          Policy.UNSUPPORTED_EMPTY_COLLECTION is returned.
     */
    public PermissionCollection getPermissions(CodeSource codesource) {
        return Policy.UNSUPPORTED_EMPTY_COLLECTION;
    }

    /**
     * Return a PermissionCollection object containing the set of
     * permissions granted to the specified ProtectionDomain.
     *
     * <p> The default implementation of this method ignores the
     * ProtectionDomain and returns Policy.UNSUPPORTED_EMPTY_COLLECTION.
     *
     * @param domain ignored
     *
     * @return a set of permissions granted to the specified ProtectionDomain.
     *          If this operation is supported, the returned
     *          set of permissions must be a new mutable instance
     *          and it must support heterogeneous Permission types.
     *          If this operation is not supported,
     *          Policy.UNSUPPORTED_EMPTY_COLLECTION is returned.
     *
     * @since 1.4
     */
    public PermissionCollection getPermissions(ProtectionDomain domain) {
        return Policy.UNSUPPORTED_EMPTY_COLLECTION;
    }

    /**
     * Evaluates the permissions granted to the ProtectionDomain and tests
     * whether the permission is granted.
     *
     * <p> The default implementation of this method ignores the
     * ProtectionDomain and Permission parameters and always returns false.
     *
     * @param domain ignored
     * @param permission ignored
     *
     * @return {@code false} always
     *
     * @see java.security.ProtectionDomain
     * @since 1.4
     */
    public boolean implies(ProtectionDomain domain, Permission permission) {
        return false;
    }

    /**
     * Refreshes/reloads the policy configuration.
     *
     * <p> The default implementation of this method does nothing.
     */
    public void refresh() { }

    /**
     * This subclass is returned by the getInstance calls.  All {@code Policy}
     * calls are delegated to the underlying {@code PolicySpi}.
     */
    private static class PolicyDelegate extends Policy {

        @SuppressWarnings("removal")
        private PolicySpi spi;
        private Provider p;
        private String type;
        private Policy.Parameters params;

        private PolicyDelegate(@SuppressWarnings("removal") PolicySpi spi, Provider p,
                        String type, Policy.Parameters params) {
            this.spi = spi;
            this.p = p;
            this.type = type;
            this.params = params;
        }

        @Override public String getType() { return type; }

        @Override public Policy.Parameters getParameters() { return params; }

        @Override public Provider getProvider() { return p; }

        @Override
        public PermissionCollection getPermissions(CodeSource codesource) {
            return spi.engineGetPermissions(codesource);
        }
        @Override
        public PermissionCollection getPermissions(ProtectionDomain domain) {
            return spi.engineGetPermissions(domain);
        }
        @Override
        public boolean implies(ProtectionDomain domain, Permission perm) {
            return spi.engineImplies(domain, perm);
        }
        @Override
        public void refresh() {
            spi.engineRefresh();
        }
    }

    /**
     * This represents a marker interface for Policy parameters.
     *
     * @since 1.6
     * @deprecated This class was only useful in conjunction with
     *       {@linkplain SecurityManager the Security Manager}, which is
     *       no longer supported. There is no replacement for the Security
     *       Manager or this class.
     */
    @Deprecated(since="17", forRemoval=true)
    public static interface Parameters { }

    /**
     * This class represents a read-only empty PermissionCollection object that
     * is returned from the {@code getPermissions(CodeSource)} and
     * {@code getPermissions(ProtectionDomain)}
     * methods in the {@code Policy} class when those operations are not
     * supported by the Policy implementation.
     */
    private static class UnsupportedEmptyCollection
        extends PermissionCollection {

        @java.io.Serial
        private static final long serialVersionUID = -8492269157353014774L;

        private Permissions perms;

        /**
         * Create a read-only empty PermissionCollection object.
         */
        public UnsupportedEmptyCollection() {
            this.perms = new Permissions();
            perms.setReadOnly();
        }

        /**
         * Adds a permission object to the current collection of permission
         * objects.
         *
         * @param permission the Permission object to add.
         *
         * @throws    SecurityException   if this PermissionCollection object
         *                                has been marked readonly
         */
        @Override public void add(Permission permission) {
            perms.add(permission);
        }

        /**
         * Checks to see if the specified permission is implied by the
         * collection of Permission objects held in this PermissionCollection.
         *
         * @param permission the Permission object to compare.
         *
         * @return {@code true} if "permission" is implied by the permissions in
         * the collection, {@code false} if not.
         */
        @Override public boolean implies(Permission permission) {
            return perms.implies(permission);
        }

        /**
         * Returns an enumeration of all the Permission objects in the
         * collection.
         *
         * @return an enumeration of all the Permissions.
         */
        @Override public Enumeration<Permission> elements() {
            return perms.elements();
        }

        /**
         * If this object is readonly, no new objects can be added to it using {@code add}.
         *
         * @return {@code true} if this object is marked as readonly, {@code false} otherwise.
         */
        @Override
        public boolean isReadOnly() {
            return perms.isReadOnly();
        }
    }
}
