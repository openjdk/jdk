/*
 * Copyright (c) 1998, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.security.Security;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.Objects;
import sun.security.util.Debug;

/**
 * <p> This is an abstract class for representing the system policy for
 * Subject-based authorization.  A subclass implementation
 * of this class provides a means to specify a Subject-based
 * access control {@code Policy}.
 *
 * <p> A {@code Policy} object can be queried for the set of
 * Permissions granted to code running as a
 * {@code Principal} in the following manner:
 *
 * <pre>
 *      policy = Policy.getPolicy();
 *      PermissionCollection perms = policy.getPermissions(subject,
 *                                                      codeSource);
 * </pre>
 *
 * The {@code Policy} object consults the local policy and returns
 * and appropriate {@code Permissions} object with the
 * Permissions granted to the Principals associated with the
 * provided {@code subject}, and granted to the code specified
 * by the provided {@code codeSource}.
 *
 * <p> A {@code Policy} contains the following information.
 * Note that this example only represents the syntax for the default
 * {@code Policy} implementation. Subclass implementations of this class
 * may implement alternative syntaxes and may retrieve the
 * {@code Policy} from any source such as files, databases,
 * or servers.
 *
 * <p> Each entry in the {@code Policy} is represented as
 * a <b><i>grant</i></b> entry.  Each <b><i>grant</i></b> entry
 * specifies a codebase, code signers, and Principals triplet,
 * as well as the Permissions granted to that triplet.
 *
 * <pre>
 *      grant CodeBase ["URL"], Signedby ["signers"],
 *            Principal [Principal_Class] "Principal_Name" {
 *          Permission Permission_Class ["Target_Name"]
 *                                      [, "Permission_Actions"]
 *                                      [, signedBy "SignerName"];
 *      };
 * </pre>
 *
 * The CodeBase and Signedby components of the triplet name/value pairs
 * are optional.  If they are not present, then any codebase will match,
 * and any signer (including unsigned code) will match.
 * For Example,
 *
 * <pre>
 *      grant CodeBase "foo.com", Signedby "foo",
 *            Principal com.sun.security.auth.UnixPrincipal "duke" {
 *          permission java.io.FilePermission "/home/duke", "read, write";
 *      };
 * </pre>
 *
 * This <b><i>grant</i></b> entry specifies that code from "foo.com",
 * signed by "foo', and running as a {@code UnixPrincipal} with the
 * name, duke, has one {@code Permission}.  This {@code Permission}
 * permits the executing code to read and write files in the directory,
 * "/home/duke".
 *
 * <p> To "run" as a particular {@code Principal},
 * code invokes the {@code Subject.doAs(subject, ...)} method.
 * After invoking that method, the code runs as all the Principals
 * associated with the specified {@code Subject}.
 * Note that this {@code Policy} (and the Permissions
 * granted in this {@code Policy}) only become effective
 * after the call to {@code Subject.doAs} has occurred.
 *
 * <p> Multiple Principals may be listed within one <b><i>grant</i></b> entry.
 * All the Principals in the grant entry must be associated with
 * the {@code Subject} provided to {@code Subject.doAs}
 * for that {@code Subject} to be granted the specified Permissions.
 *
 * <pre>
 *      grant Principal com.sun.security.auth.UnixPrincipal "duke",
 *            Principal com.sun.security.auth.UnixNumericUserPrincipal "0" {
 *          permission java.io.FilePermission "/home/duke", "read, write";
 *          permission java.net.SocketPermission "duke.com", "connect";
 *      };
 * </pre>
 *
 * This entry grants any code running as both "duke" and "0"
 * permission to read and write files in duke's home directory,
 * as well as permission to make socket connections to "duke.com".
 *
 * <p> Note that non Principal-based grant entries are not permitted
 * in this {@code Policy}.  Therefore, grant entries such as:
 *
 * <pre>
 *      grant CodeBase "foo.com", Signedby "foo" {
 *          permission java.io.FilePermission "/tmp/scratch", "read, write";
 *      };
 * </pre>
 *
 * are rejected.  Such permission must be listed in the
 * {@code java.security.Policy}.
 *
 * <p> The default {@code Policy} implementation can be changed by
 * setting the value of the {@code auth.policy.provider} security property to
 * the fully qualified name of the desired {@code Policy} implementation class.
 *
 * @deprecated  Replaced by java.security.Policy.
 *              java.security.Policy has a method:
 * <pre>
 *      public PermissionCollection getPermissions
 *          (java.security.ProtectionDomain pd)
 *
 * </pre>
 * and ProtectionDomain has a constructor:
 * <pre>
 *      public ProtectionDomain
 *          (CodeSource cs,
 *           PermissionCollection permissions,
 *           ClassLoader loader,
 *           Principal[] principals)
 * </pre>
 *
 * These two APIs provide callers the means to query the
 * Policy for Principal-based Permission entries.
 * This class is subject to removal in a future version of Java SE.
 *
 * @since 1.4
 * @see java.security.Security security properties
 */
@Deprecated(since="1.4", forRemoval=true)
public abstract class Policy {

    private static Policy policy;
    private static final String AUTH_POLICY =
        "sun.security.provider.AuthPolicyFile";

    private final java.security.AccessControlContext acc =
            java.security.AccessController.getContext();

    // true if a custom (not AUTH_POLICY) system-wide policy object is set
    private static boolean isCustomPolicy;

    /**
     * Sole constructor.  (For invocation by subclass constructors, typically
     * implicit.)
     */
    protected Policy() { }

    /**
     * Returns the installed Policy object.
     * This method first calls
     * {@code SecurityManager.checkPermission} with the
     * {@code AuthPermission("getPolicy")} permission
     * to ensure the caller has permission to get the Policy object.
     *
     * @return the installed Policy.  The return value cannot be
     *          {@code null}.
     *
     * @exception java.lang.SecurityException if the current thread does not
     *      have permission to get the Policy object.
     *
     * @see #setPolicy
     */
    public static Policy getPolicy() {
        java.lang.SecurityManager sm = System.getSecurityManager();
        if (sm != null) sm.checkPermission(new AuthPermission("getPolicy"));
        return getPolicyNoCheck();
    }

    /**
     * Returns the installed Policy object, skipping the security check.
     *
     * @return the installed Policy.
     *
     */
    static Policy getPolicyNoCheck() {
        if (policy == null) {

            synchronized(Policy.class) {

                if (policy == null) {
                    String policy_class = null;
                    policy_class = AccessController.doPrivileged
                        (new PrivilegedAction<String>() {
                        public String run() {
                            return java.security.Security.getProperty
                                ("auth.policy.provider");
                        }
                    });
                    if (policy_class == null) {
                        policy_class = AUTH_POLICY;
                    }

                    try {
                        final String finalClass = policy_class;

                        Policy untrustedImpl = AccessController.doPrivileged(
                                new PrivilegedExceptionAction<Policy>() {
                                    public Policy run() throws ClassNotFoundException,
                                            InstantiationException,
                                            IllegalAccessException {
                                        Class<? extends Policy> implClass = Class.forName(
                                                finalClass, false,
                                                Thread.currentThread().getContextClassLoader()
                                        ).asSubclass(Policy.class);
                                        return implClass.newInstance();
                                    }
                                });
                        AccessController.doPrivileged(
                                new PrivilegedExceptionAction<Void>() {
                                    public Void run() {
                                        setPolicy(untrustedImpl);
                                        isCustomPolicy = !finalClass.equals(AUTH_POLICY);
                                        return null;
                                    }
                                }, Objects.requireNonNull(untrustedImpl.acc)
                        );
                    } catch (Exception e) {
                        throw new SecurityException
                                (sun.security.util.ResourcesMgr.getString
                                ("unable.to.instantiate.Subject.based.policy"));
                    }
                }
            }
        }
        return policy;
    }


    /**
     * Sets the system-wide Policy object. This method first calls
     * {@code SecurityManager.checkPermission} with the
     * {@code AuthPermission("setPolicy")}
     * permission to ensure the caller has permission to set the Policy.
     *
     * @param policy the new system Policy object.
     *
     * @exception java.lang.SecurityException if the current thread does not
     *          have permission to set the Policy.
     *
     * @see #getPolicy
     */
    public static void setPolicy(Policy policy) {
        java.lang.SecurityManager sm = System.getSecurityManager();
        if (sm != null) sm.checkPermission(new AuthPermission("setPolicy"));
        Policy.policy = policy;
        // all non-null policy objects are assumed to be custom
        isCustomPolicy = policy != null ? true : false;
    }

    /**
     * Returns true if a custom (not AUTH_POLICY) system-wide policy object
     * has been set or installed. This method is called by
     * SubjectDomainCombiner to provide backwards compatibility for
     * developers that provide their own javax.security.auth.Policy
     * implementations.
     *
     * @return true if a custom (not AUTH_POLICY) system-wide policy object
     * has been set; false otherwise
     */
    static boolean isCustomPolicySet(Debug debug) {
        if (policy != null) {
            if (debug != null && isCustomPolicy) {
                debug.println("Providing backwards compatibility for " +
                              "javax.security.auth.policy implementation: " +
                              policy.toString());
            }
            return isCustomPolicy;
        }
        // check if custom policy has been set using auth.policy.provider prop
        String policyClass = java.security.AccessController.doPrivileged
            (new java.security.PrivilegedAction<String>() {
                public String run() {
                    return Security.getProperty("auth.policy.provider");
                }
        });
        if (policyClass != null && !policyClass.equals(AUTH_POLICY)) {
            if (debug != null) {
                debug.println("Providing backwards compatibility for " +
                              "javax.security.auth.policy implementation: " +
                              policyClass);
            }
            return true;
        }
        return false;
    }

    /**
     * Retrieve the Permissions granted to the Principals associated with
     * the specified {@code CodeSource}.
     *
     * @param subject the {@code Subject}
     *                  whose associated Principals,
     *                  in conjunction with the provided
     *                  {@code CodeSource}, determines the Permissions
     *                  returned by this method.  This parameter
     *                  may be {@code null}.
     *
     * @param cs the code specified by its {@code CodeSource}
     *                  that determines, in conjunction with the provided
     *                  {@code Subject}, the Permissions
     *                  returned by this method.  This parameter may be
     *                  {@code null}.
     *
     * @return the Collection of Permissions granted to all the
     *                  {@code Subject} and code specified in
     *                  the provided <i>subject</i> and <i>cs</i>
     *                  parameters.
     */
    public abstract java.security.PermissionCollection getPermissions
                                        (Subject subject,
                                        java.security.CodeSource cs);

    /**
     * Refresh and reload the Policy.
     *
     * <p>This method causes this object to refresh/reload its current
     * Policy. This is implementation-dependent.
     * For example, if the Policy object is stored in
     * a file, calling {@code refresh} will cause the file to be re-read.
     *
     * @exception SecurityException if the caller does not have permission
     *                          to refresh the Policy.
     */
    public abstract void refresh();
}
