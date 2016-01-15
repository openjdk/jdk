/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.security.auth;

import java.security.CodeSource;
import java.security.PermissionCollection;
import javax.security.auth.Subject;

/**
 * This class represents a default implementation for
 * {@code javax.security.auth.Policy}.
 *
 * <p> This object stores the policy for entire Java runtime,
 * and is the amalgamation of multiple static policy
 * configurations that resides in files.
 * The algorithm for locating the policy file(s) and reading their
 * information into this {@code Policy} object is:
 *
 * <ol>
 * <li>
 *   Loop through the security properties,
 *   <i>auth.policy.url.1</i>, <i>auth.policy.url.2</i>, ...,
 *   <i>auth.policy.url.X</i>".
 *   Each property value specifies a {@code URL} pointing to a
 *   policy file to be loaded.  Read in and load each policy.
 *
 * <li>
 *   The {@code java.lang.System} property <i>java.security.auth.policy</i>
 *   may also be set to a {@code URL} pointing to another policy file
 *   (which is the case when a user uses the -D switch at runtime).
 *   If this property is defined, and its use is allowed by the
 *   security property file (the Security property,
 *   <i>policy.allowSystemProperty</i> is set to <i>true</i>),
 *   also load that policy.
 *
 * <li>
 *   If the <i>java.security.auth.policy</i> property is defined using
 *   "==" (rather than "="), then ignore all other specified
 *   policies and only load this policy.
 * </ol>
 *
 * Each policy file consists of one or more grant entries, each of
 * which consists of a number of permission entries.
 *
 * <pre>
 *   grant signedBy "<b>alias</b>", codeBase "<b>URL</b>",
 *         principal <b>principalClass</b> "<b>principalName</b>",
 *         principal <b>principalClass</b> "<b>principalName</b>",
 *         ... {
 *
 *     permission <b>Type</b> "<b>name</b> "<b>action</b>",
 *         signedBy "<b>alias</b>";
 *     permission <b>Type</b> "<b>name</b> "<b>action</b>",
 *         signedBy "<b>alias</b>";
 *     ....
 *   };
 * </pre>
 *
 * All non-bold items above must appear as is (although case
 * doesn't matter and some are optional, as noted below).
 * Italicized items represent variable values.
 *
 * <p> A grant entry must begin with the word {@code grant}.
 * The {@code signedBy} and {@code codeBase}
 * name/value pairs are optional.
 * If they are not present, then any signer (including unsigned code)
 * will match, and any codeBase will match.  Note that the
 * {@code principal} name/value pair is not optional.
 * This {@code Policy} implementation only permits
 * Principal-based grant entries.  Note that the <i>principalClass</i>
 * may be set to the wildcard value, *, which allows it to match
 * any {@code Principal} class.  In addition, the <i>principalName</i>
 * may also be set to the wildcard value, *, allowing it to match
 * any {@code Principal} name.  When setting the <i>principalName</i>
 * to the *, do not surround the * with quotes.
 *
 * <p> A permission entry must begin with the word {@code permission}.
 * The word <i>{@code Type}</i> in the template above is
 * a specific permission type, such as {@code java.io.FilePermission}
 * or {@code java.lang.RuntimePermission}.
 *
 * <p> The "<i>action</i>" is required for
 * many permission types, such as {@code java.io.FilePermission}
 * (where it specifies what type of file access that is permitted).
 * It is not required for categories such as
 * {@code java.lang.RuntimePermission}
 * where it is not necessary - you either have the
 * permission specified by the "<i>{@code name}</i>"
 * value following the type name or you don't.
 *
 * <p> The {@code signedBy} name/value pair for a permission entry
 * is optional. If present, it indicates a signed permission. That is,
 * the permission class itself must be signed by the given alias in
 * order for it to be granted. For example,
 * suppose you have the following grant entry:
 *
 * <pre>
 *   grant principal foo.com.Principal "Duke" {
 *     permission Foo "foobar", signedBy "FooSoft";
 *   }
 * </pre>
 *
 * <p> Then this permission of type <i>Foo</i> is granted if the
 * {@code Foo.class} permission has been signed by the
 * "FooSoft" alias, or if {@code Foo.class} is a
 * system class (i.e., is found on the CLASSPATH).
 *
 * <p> Items that appear in an entry must appear in the specified order
 * ({@code permission}, <i>Type</i>, "<i>name</i>", and
 * "<i>action</i>"). An entry is terminated with a semicolon.
 *
 * <p> Case is unimportant for the identifiers ({@code permission},
 * {@code signedBy}, {@code codeBase}, etc.) but is
 * significant for the <i>Type</i>
 * or for any string that is passed in as a value.
 *
 * <p> An example of two entries in a policy configuration file is
 * <pre>
 *   // if the code is comes from "foo.com" and is running as "Duke",
 *   // grant it read/write to all files in /tmp.
 *
 *   grant codeBase "foo.com", principal foo.com.Principal "Duke" {
 *              permission java.io.FilePermission "/tmp/*", "read,write";
 *   };
 *
 *   // grant any code running as "Duke" permission to read
 *   // the "java.vendor" Property.
 *
 *   grant principal foo.com.Principal "Duke" {
 *         permission java.util.PropertyPermission "java.vendor";
 * </pre>
 *
 * <p> This {@code Policy} implementation supports
 * special handling for PrivateCredentialPermissions.
 * If a grant entry is configured with a
 * {@code PrivateCredentialPermission},
 * and the "Principal Class/Principal Name" for that
 * {@code PrivateCredentialPermission} is "self",
 * then the entry grants the specified {@code Subject} permission to
 * access its own private Credential.  For example,
 * the following grants the {@code Subject} "Duke"
 * access to its own a.b.Credential.
 *
 * <pre>
 *   grant principal foo.com.Principal "Duke" {
 *      permission javax.security.auth.PrivateCredentialPermission
 *              "a.b.Credential self",
 *              "read";
 *    };
 * </pre>
 *
 * The following grants the {@code Subject} "Duke"
 * access to all of its own private Credentials:
 *
 * <pre>
 *   grant principal foo.com.Principal "Duke" {
 *      permission javax.security.auth.PrivateCredentialPermission
 *              "* self",
 *              "read";
 *    };
 * </pre>
 *
 * The following grants all Subjects authenticated as a
 * {@code SolarisPrincipal} (regardless of their respective names)
 * permission to access their own private Credentials:
 *
 * <pre>
 *   grant principal com.sun.security.auth.SolarisPrincipal * {
 *      permission javax.security.auth.PrivateCredentialPermission
 *              "* self",
 *              "read";
 *    };
 * </pre>
 *
 * The following grants all Subjects permission to access their own
 * private Credentials:
 *
 * <pre>
 *   grant principal * * {
 *      permission javax.security.auth.PrivateCredentialPermission
 *              "* self",
 *              "read";
 *    };
 * </pre>

 * @deprecated As of JDK&nbsp;1.4, replaced by
 *             {@code sun.security.provider.PolicyFile}.
 *             This class is entirely deprecated.
 *
 * @see java.security.CodeSource
 * @see java.security.Permissions
 * @see java.security.ProtectionDomain
 * @see java.security.Security security properties
 */
@Deprecated
public class PolicyFile extends javax.security.auth.Policy {

    private final sun.security.provider.AuthPolicyFile apf;

    /**
     * Initializes the Policy object and reads the default policy
     * configuration file(s) into the Policy object.
     */
    public PolicyFile() {
        apf = new sun.security.provider.AuthPolicyFile();
    }

    /**
     * Refreshes the policy object by re-reading all the policy files.
     *
     * @exception SecurityException if the caller doesn't have permission
     *          to refresh the {@code Policy}.
     */
    @Override
    public void refresh() {
        apf.refresh();
    }

    /**
     * Examines this {@code Policy} and returns the Permissions granted
     * to the specified {@code Subject} and {@code CodeSource}.
     *
     * <p> Permissions for a particular <i>grant</i> entry are returned
     * if the {@code CodeSource} constructed using the codebase and
     * signedby values specified in the entry {@code implies}
     * the {@code CodeSource} provided to this method, and if the
     * {@code Subject} provided to this method contains all of the
     * Principals specified in the entry.
     *
     * <p> The {@code Subject} provided to this method contains all
     * of the Principals specified in the entry if, for each
     * {@code Principal}, "P1", specified in the <i>grant</i> entry
     * one of the following two conditions is met:
     *
     * <ol>
     * <li> the {@code Subject} has a
     *      {@code Principal}, "P2", where
     *      {@code P2.getClass().getName()} equals the
     *      P1's class name, and where
     *      {@code P2.getName()} equals the P1's name.
     *
     * <li> P1 implements
     *      {@code com.sun.security.auth.PrincipalComparator},
     *      and {@code P1.implies} the provided {@code Subject}.
     * </ol>
     *
     * <p> Note that this {@code Policy} implementation has
     * special handling for PrivateCredentialPermissions.
     * When this method encounters a {@code PrivateCredentialPermission}
     * which specifies "self" as the {@code Principal} class and name,
     * it does not add that {@code Permission} to the returned
     * {@code PermissionCollection}.  Instead, it builds
     * a new {@code PrivateCredentialPermission}
     * for each {@code Principal} associated with the provided
     * {@code Subject}.  Each new {@code PrivateCredentialPermission}
     * contains the same Credential class as specified in the
     * originally granted permission, as well as the Class and name
     * for the respective {@code Principal}.
     *
     * @param subject the Permissions granted to this {@code Subject}
     *          and the additionally provided {@code CodeSource}
     *          are returned.
     *
     * @param codesource the Permissions granted to this {@code CodeSource}
     *          and the additionally provided {@code Subject}
     *          are returned.
     *
     * @return the Permissions granted to the provided {@code Subject}
     *          {@code CodeSource}.
     */
    @Override
    public PermissionCollection getPermissions(final Subject subject,
                                               final CodeSource codesource) {
        return apf.getPermissions(subject, codesource);
    }
}
