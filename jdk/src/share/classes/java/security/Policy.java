/*
 * Copyright (c) 1997, 2009, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.lang.RuntimePermission;
import java.lang.reflect.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.PropertyPermission;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.WeakHashMap;
import sun.security.jca.GetInstance;
import sun.security.util.Debug;
import sun.security.util.SecurityConstants;


/**
 * A Policy object is responsible for determining whether code executing
 * in the Java runtime environment has permission to perform a
 * security-sensitive operation.
 *
 * <p> There is only one Policy object installed in the runtime at any
 * given time.  A Policy object can be installed by calling the
 * <code>setPolicy</code> method.  The installed Policy object can be
 * obtained by calling the <code>getPolicy</code> method.
 *
 * <p> If no Policy object has been installed in the runtime, a call to
 * <code>getPolicy</code> installs an instance of the default Policy
 * implementation (a default subclass implementation of this abstract class).
 * The default Policy implementation can be changed by setting the value
 * of the "policy.provider" security property (in the Java security properties
 * file) to the fully qualified name of the desired Policy subclass
 * implementation.  The Java security properties file is located in the
 * file named &lt;JAVA_HOME&gt;/lib/security/java.security.
 * &lt;JAVA_HOME&gt; refers to the value of the java.home system property,
 * and specifies the directory where the JRE is installed.
 *
 * <p> Application code can directly subclass Policy to provide a custom
 * implementation.  In addition, an instance of a Policy object can be
 * constructed by invoking one of the <code>getInstance</code> factory methods
 * with a standard type.  The default policy type is "JavaPolicy".
 * See Appendix A in the <a href="../../../technotes/guides/security/crypto/CryptoSpec.html#AppA">
 * Java Cryptography Architecture API Specification &amp; Reference </a>
 * for a list of standard Policy types.
 *
 * <p> Once a Policy instance has been installed (either by default, or by
 * calling <code>setPolicy</code>),
 * the Java runtime invokes its <code>implies</code> when it needs to
 * determine whether executing code (encapsulated in a ProtectionDomain)
 * can perform SecurityManager-protected operations.  How a Policy object
 * retrieves its policy data is up to the Policy implementation itself.
 * The policy data may be stored, for example, in a flat ASCII file,
 * in a serialized binary file of the Policy class, or in a database.
 *
 * <p> The <code>refresh</code> method causes the policy object to
 * refresh/reload its data.  This operation is implementation-dependent.
 * For example, if the policy object stores its data in configuration files,
 * calling <code>refresh</code> will cause it to re-read the configuration
 * policy files.  If a refresh operation is not supported, this method does
 * nothing.  Note that refreshed policy may not have an effect on classes
 * in a particular ProtectionDomain. This is dependent on the Policy
 * provider's implementation of the <code>implies</code>
 * method and its PermissionCollection caching strategy.
 *
 * @author Roland Schemers
 * @author Gary Ellison
 * @see java.security.Provider
 * @see java.security.ProtectionDomain
 * @see java.security.Permission
 */

public abstract class Policy {

    /**
     * A read-only empty PermissionCollection instance.
     * @since 1.6
     */
    public static final PermissionCollection UNSUPPORTED_EMPTY_COLLECTION =
                        new UnsupportedEmptyCollection();

    /** the system-wide policy. */
    private static Policy policy; // package private for AccessControlContext

    private static final Debug debug = Debug.getInstance("policy");

    // Cache mapping ProtectionDomain.Key to PermissionCollection
    private WeakHashMap<ProtectionDomain.Key, PermissionCollection> pdMapping;

    /** package private for AccessControlContext */
    static boolean isSet()
    {
        return policy != null;
    }

    private static void checkPermission(String type) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new SecurityPermission("createPolicy." + type));
        }
    }

    /**
     * Returns the installed Policy object. This value should not be cached,
     * as it may be changed by a call to <code>setPolicy</code>.
     * This method first calls
     * <code>SecurityManager.checkPermission</code> with a
     * <code>SecurityPermission("getPolicy")</code> permission
     * to ensure it's ok to get the Policy object..
     *
     * @return the installed Policy.
     *
     * @throws SecurityException
     *        if a security manager exists and its
     *        <code>checkPermission</code> method doesn't allow
     *        getting the Policy object.
     *
     * @see SecurityManager#checkPermission(Permission)
     * @see #setPolicy(java.security.Policy)
     */
    public static Policy getPolicy()
    {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null)
            sm.checkPermission(SecurityConstants.GET_POLICY_PERMISSION);
        return getPolicyNoCheck();
    }

    /**
     * Returns the installed Policy object, skipping the security check.
     * Used by SecureClassLoader and getPolicy.
     *
     * @return the installed Policy.
     *
     */
    static synchronized Policy getPolicyNoCheck()
    {
        if (policy == null) {
            String policy_class = null;
            policy_class = AccessController.doPrivileged(
                new PrivilegedAction<String>() {
                    public String run() {
                        return Security.getProperty("policy.provider");
                    }
                });
            if (policy_class == null) {
                policy_class = "sun.security.provider.PolicyFile";
            }

            try {
                policy = (Policy)
                    Class.forName(policy_class).newInstance();
            } catch (Exception e) {
                /*
                 * The policy_class seems to be an extension
                 * so we have to bootstrap loading it via a policy
                 * provider that is on the bootclasspath
                 * If it loads then shift gears to using the configured
                 * provider.
                 */

                // install the bootstrap provider to avoid recursion
                policy = new sun.security.provider.PolicyFile();

                final String pc = policy_class;
                Policy p = AccessController.doPrivileged(
                    new PrivilegedAction<Policy>() {
                        public Policy run() {
                            try {
                                ClassLoader cl =
                                        ClassLoader.getSystemClassLoader();
                                // we want the extension loader
                                ClassLoader extcl = null;
                                while (cl != null) {
                                    extcl = cl;
                                    cl = cl.getParent();
                                }
                                return (extcl != null ? (Policy)Class.forName(
                                        pc, true, extcl).newInstance() : null);
                            } catch (Exception e) {
                                if (debug != null) {
                                    debug.println("policy provider " +
                                                pc +
                                                " not available");
                                    e.printStackTrace();
                                }
                                return null;
                            }
                        }
                    });
                /*
                 * if it loaded install it as the policy provider. Otherwise
                 * continue to use the system default implementation
                 */
                if (p != null) {
                    policy = p;
                } else {
                    if (debug != null) {
                        debug.println("using sun.security.provider.PolicyFile");
                    }
                }
            }
        }
        return policy;
    }

    /**
     * Sets the system-wide Policy object. This method first calls
     * <code>SecurityManager.checkPermission</code> with a
     * <code>SecurityPermission("setPolicy")</code>
     * permission to ensure it's ok to set the Policy.
     *
     * @param p the new system Policy object.
     *
     * @throws SecurityException
     *        if a security manager exists and its
     *        <code>checkPermission</code> method doesn't allow
     *        setting the Policy.
     *
     * @see SecurityManager#checkPermission(Permission)
     * @see #getPolicy()
     *
     */
    public static void setPolicy(Policy p)
    {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) sm.checkPermission(
                                 new SecurityPermission("setPolicy"));
        if (p != null) {
            initPolicy(p);
        }
        synchronized (Policy.class) {
            Policy.policy = p;
        }
    }

    /**
     * Initialize superclass state such that a legacy provider can
     * handle queries for itself.
     *
     * @since 1.4
     */
    private static void initPolicy (final Policy p) {
        /*
         * A policy provider not on the bootclasspath could trigger
         * security checks fulfilling a call to either Policy.implies
         * or Policy.getPermissions. If this does occur the provider
         * must be able to answer for it's own ProtectionDomain
         * without triggering additional security checks, otherwise
         * the policy implementation will end up in an infinite
         * recursion.
         *
         * To mitigate this, the provider can collect it's own
         * ProtectionDomain and associate a PermissionCollection while
         * it is being installed. The currently installed policy
         * provider (if there is one) will handle calls to
         * Policy.implies or Policy.getPermissions during this
         * process.
         *
         * This Policy superclass caches away the ProtectionDomain and
         * statically binds permissions so that legacy Policy
         * implementations will continue to function.
         */

        ProtectionDomain policyDomain =
        AccessController.doPrivileged(new PrivilegedAction<ProtectionDomain>() {
            public ProtectionDomain run() {
                return p.getClass().getProtectionDomain();
            }
        });

        /*
         * Collect the permissions granted to this protection domain
         * so that the provider can be security checked while processing
         * calls to Policy.implies or Policy.getPermissions.
         */
        PermissionCollection policyPerms = null;
        synchronized (p) {
            if (p.pdMapping == null) {
                p.pdMapping =
                    new WeakHashMap<ProtectionDomain.Key, PermissionCollection>();
           }
        }

        if (policyDomain.getCodeSource() != null) {
            if (Policy.isSet()) {
                policyPerms = policy.getPermissions(policyDomain);
            }

            if (policyPerms == null) { // assume it has all
                policyPerms = new Permissions();
                policyPerms.add(SecurityConstants.ALL_PERMISSION);
            }

            synchronized (p.pdMapping) {
                // cache of pd to permissions
                p.pdMapping.put(policyDomain.key, policyPerms);
            }
        }
        return;
    }


    /**
     * Returns a Policy object of the specified type.
     *
     * <p> This method traverses the list of registered security providers,
     * starting with the most preferred Provider.
     * A new Policy object encapsulating the
     * PolicySpi implementation from the first
     * Provider that supports the specified type is returned.
     *
     * <p> Note that the list of registered providers may be retrieved via
     * the {@link Security#getProviders() Security.getProviders()} method.
     *
     * @param type the specified Policy type.  See Appendix A in the
     *    <a href="../../../technotes/guides/security/crypto/CryptoSpec.html#AppA">
     *    Java Cryptography Architecture API Specification &amp; Reference </a>
     *    for a list of standard Policy types.
     *
     * @param params parameters for the Policy, which may be null.
     *
     * @return the new Policy object.
     *
     * @exception SecurityException if the caller does not have permission
     *          to get a Policy instance for the specified type.
     *
     * @exception NullPointerException if the specified type is null.
     *
     * @exception IllegalArgumentException if the specified parameters
     *          are not understood by the PolicySpi implementation
     *          from the selected Provider.
     *
     * @exception NoSuchAlgorithmException if no Provider supports a PolicySpi
     *          implementation for the specified type.
     *
     * @see Provider
     * @since 1.6
     */
    public static Policy getInstance(String type, Policy.Parameters params)
                throws NoSuchAlgorithmException {

        checkPermission(type);
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
     * Returns a Policy object of the specified type.
     *
     * <p> A new Policy object encapsulating the
     * PolicySpi implementation from the specified provider
     * is returned.   The specified provider must be registered
     * in the provider list.
     *
     * <p> Note that the list of registered providers may be retrieved via
     * the {@link Security#getProviders() Security.getProviders()} method.
     *
     * @param type the specified Policy type.  See Appendix A in the
     *    <a href="../../../technotes/guides/security/crypto/CryptoSpec.html#AppA">
     *    Java Cryptography Architecture API Specification &amp; Reference </a>
     *    for a list of standard Policy types.
     *
     * @param params parameters for the Policy, which may be null.
     *
     * @param provider the provider.
     *
     * @return the new Policy object.
     *
     * @exception SecurityException if the caller does not have permission
     *          to get a Policy instance for the specified type.
     *
     * @exception NullPointerException if the specified type is null.
     *
     * @exception IllegalArgumentException if the specified provider
     *          is null or empty,
     *          or if the specified parameters are not understood by
     *          the PolicySpi implementation from the specified provider.
     *
     * @exception NoSuchProviderException if the specified provider is not
     *          registered in the security provider list.
     *
     * @exception NoSuchAlgorithmException if the specified provider does not
     *          support a PolicySpi implementation for the specified type.
     *
     * @see Provider
     * @since 1.6
     */
    public static Policy getInstance(String type,
                                Policy.Parameters params,
                                String provider)
                throws NoSuchProviderException, NoSuchAlgorithmException {

        if (provider == null || provider.length() == 0) {
            throw new IllegalArgumentException("missing provider");
        }

        checkPermission(type);
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
            return handleException (nsae);
        }
    }

    /**
     * Returns a Policy object of the specified type.
     *
     * <p> A new Policy object encapsulating the
     * PolicySpi implementation from the specified Provider
     * object is returned.  Note that the specified Provider object
     * does not have to be registered in the provider list.
     *
     * @param type the specified Policy type.  See Appendix A in the
     *    <a href="../../../technotes/guides/security/crypto/CryptoSpec.html#AppA">
     *    Java Cryptography Architecture API Specification &amp; Reference </a>
     *    for a list of standard Policy types.
     *
     * @param params parameters for the Policy, which may be null.
     *
     * @param provider the Provider.
     *
     * @return the new Policy object.
     *
     * @exception SecurityException if the caller does not have permission
     *          to get a Policy instance for the specified type.
     *
     * @exception NullPointerException if the specified type is null.
     *
     * @exception IllegalArgumentException if the specified Provider is null,
     *          or if the specified parameters are not understood by
     *          the PolicySpi implementation from the specified Provider.
     *
     * @exception NoSuchAlgorithmException if the specified Provider does not
     *          support a PolicySpi implementation for the specified type.
     *
     * @see Provider
     * @since 1.6
     */
    public static Policy getInstance(String type,
                                Policy.Parameters params,
                                Provider provider)
                throws NoSuchAlgorithmException {

        if (provider == null) {
            throw new IllegalArgumentException("missing provider");
        }

        checkPermission(type);
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
            return handleException (nsae);
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
     * Return the Provider of this Policy.
     *
     * <p> This Policy instance will only have a Provider if it
     * was obtained via a call to <code>Policy.getInstance</code>.
     * Otherwise this method returns null.
     *
     * @return the Provider of this Policy, or null.
     *
     * @since 1.6
     */
    public Provider getProvider() {
        return null;
    }

    /**
     * Return the type of this Policy.
     *
     * <p> This Policy instance will only have a type if it
     * was obtained via a call to <code>Policy.getInstance</code>.
     * Otherwise this method returns null.
     *
     * @return the type of this Policy, or null.
     *
     * @since 1.6
     */
    public String getType() {
        return null;
    }

    /**
     * Return Policy parameters.
     *
     * <p> This Policy instance will only have parameters if it
     * was obtained via a call to <code>Policy.getInstance</code>.
     * Otherwise this method returns null.
     *
     * @return Policy parameters, or null.
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
     * <p> Applications are discouraged from calling this method
     * since this operation may not be supported by all policy implementations.
     * Applications should solely rely on the <code>implies</code> method
     * to perform policy checks.  If an application absolutely must call
     * a getPermissions method, it should call
     * <code>getPermissions(ProtectionDomain)</code>.
     *
     * <p> The default implementation of this method returns
     * Policy.UNSUPPORTED_EMPTY_COLLECTION.  This method can be
     * overridden if the policy implementation can return a set of
     * permissions granted to a CodeSource.
     *
     * @param codesource the CodeSource to which the returned
     *          PermissionCollection has been granted.
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
     * <p> Applications are discouraged from calling this method
     * since this operation may not be supported by all policy implementations.
     * Applications should rely on the <code>implies</code> method
     * to perform policy checks.
     *
     * <p> The default implementation of this method first retrieves
     * the permissions returned via <code>getPermissions(CodeSource)</code>
     * (the CodeSource is taken from the specified ProtectionDomain),
     * as well as the permissions located inside the specified ProtectionDomain.
     * All of these permissions are then combined and returned in a new
     * PermissionCollection object.  If <code>getPermissions(CodeSource)</code>
     * returns Policy.UNSUPPORTED_EMPTY_COLLECTION, then this method
     * returns the permissions contained inside the specified ProtectionDomain
     * in a new PermissionCollection object.
     *
     * <p> This method can be overridden if the policy implementation
     * supports returning a set of permissions granted to a ProtectionDomain.
     *
     * @param domain the ProtectionDomain to which the returned
     *          PermissionCollection has been granted.
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
        PermissionCollection pc = null;

        if (domain == null)
            return new Permissions();

        if (pdMapping == null) {
            initPolicy(this);
        }

        synchronized (pdMapping) {
            pc = pdMapping.get(domain.key);
        }

        if (pc != null) {
            Permissions perms = new Permissions();
            synchronized (pc) {
                for (Enumeration<Permission> e = pc.elements() ; e.hasMoreElements() ;) {
                    perms.add(e.nextElement());
                }
            }
            return perms;
        }

        pc = getPermissions(domain.getCodeSource());
        if (pc == null || pc == UNSUPPORTED_EMPTY_COLLECTION) {
            pc = new Permissions();
        }

        addStaticPerms(pc, domain.getPermissions());
        return pc;
    }

    /**
     * add static permissions to provided permission collection
     */
    private void addStaticPerms(PermissionCollection perms,
                                PermissionCollection statics) {
        if (statics != null) {
            synchronized (statics) {
                Enumeration<Permission> e = statics.elements();
                while (e.hasMoreElements()) {
                    perms.add(e.nextElement());
                }
            }
        }
    }

    /**
     * Evaluates the global policy for the permissions granted to
     * the ProtectionDomain and tests whether the permission is
     * granted.
     *
     * @param domain the ProtectionDomain to test
     * @param permission the Permission object to be tested for implication.
     *
     * @return true if "permission" is a proper subset of a permission
     * granted to this ProtectionDomain.
     *
     * @see java.security.ProtectionDomain
     * @since 1.4
     */
    public boolean implies(ProtectionDomain domain, Permission permission) {
        PermissionCollection pc;

        if (pdMapping == null) {
            initPolicy(this);
        }

        synchronized (pdMapping) {
            pc = pdMapping.get(domain.key);
        }

        if (pc != null) {
            return pc.implies(permission);
        }

        pc = getPermissions(domain);
        if (pc == null) {
            return false;
        }

        synchronized (pdMapping) {
            // cache it
            pdMapping.put(domain.key, pc);
        }

        return pc.implies(permission);
    }

    /**
     * Refreshes/reloads the policy configuration. The behavior of this method
     * depends on the implementation. For example, calling <code>refresh</code>
     * on a file-based policy will cause the file to be re-read.
     *
     * <p> The default implementation of this method does nothing.
     * This method should be overridden if a refresh operation is supported
     * by the policy implementation.
     */
    public void refresh() { }

    /**
     * This subclass is returned by the getInstance calls.  All Policy calls
     * are delegated to the underlying PolicySpi.
     */
    private static class PolicyDelegate extends Policy {

        private PolicySpi spi;
        private Provider p;
        private String type;
        private Policy.Parameters params;

        private PolicyDelegate(PolicySpi spi, Provider p,
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
     */
    public static interface Parameters { }

    /**
     * This class represents a read-only empty PermissionCollection object that
     * is returned from the <code>getPermissions(CodeSource)</code> and
     * <code>getPermissions(ProtectionDomain)</code>
     * methods in the Policy class when those operations are not
     * supported by the Policy implementation.
     */
    private static class UnsupportedEmptyCollection
        extends PermissionCollection {

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
         * @exception SecurityException - if this PermissionCollection object
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
         * @return true if "permission" is implied by the  permissions in
         * the collection, false if not.
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
    }
}
