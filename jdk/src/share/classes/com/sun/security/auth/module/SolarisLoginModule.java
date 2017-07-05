/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.security.auth.module;

import java.util.*;
import java.io.IOException;
import javax.security.auth.*;
import javax.security.auth.callback.*;
import javax.security.auth.login.*;
import javax.security.auth.spi.*;
import com.sun.security.auth.SolarisPrincipal;
import com.sun.security.auth.SolarisNumericUserPrincipal;
import com.sun.security.auth.SolarisNumericGroupPrincipal;

/**
 * <p> This <code>LoginModule</code> imports a user's Solaris
 * <code>Principal</code> information (<code>SolarisPrincipal</code>,
 * <code>SolarisNumericUserPrincipal</code>,
 * and <code>SolarisNumericGroupPrincipal</code>)
 * and associates them with the current <code>Subject</code>.
 *
 * <p> This LoginModule recognizes the debug option.
 * If set to true in the login Configuration,
 * debug messages will be output to the output stream, System.out.
 * @deprecated  As of JDK1.4, replaced by
 * <code>com.sun.security.auth.module.UnixLoginModule</code>.
 *             This LoginModule is entirely deprecated and
 *             is here to allow for a smooth transition to the new
 *             UnixLoginModule.
 *
 */
@jdk.Exported(false)
@Deprecated
public class SolarisLoginModule implements LoginModule {

    // initial state
    private Subject subject;
    private CallbackHandler callbackHandler;
    private Map<String, ?> sharedState;
    private Map<String, ?> options;

    // configurable option
    private boolean debug = true;

    // SolarisSystem to retrieve underlying system info
    private SolarisSystem ss;

    // the authentication status
    private boolean succeeded = false;
    private boolean commitSucceeded = false;

    // Underlying system info
    private SolarisPrincipal userPrincipal;
    private SolarisNumericUserPrincipal UIDPrincipal;
    private SolarisNumericGroupPrincipal GIDPrincipal;
    private LinkedList<SolarisNumericGroupPrincipal> supplementaryGroups =
                new LinkedList<>();

    /**
     * Initialize this <code>LoginModule</code>.
     *
     * <p>
     *
     * @param subject the <code>Subject</code> to be authenticated. <p>
     *
     * @param callbackHandler a <code>CallbackHandler</code> for communicating
     *                  with the end user (prompting for usernames and
     *                  passwords, for example). <p>
     *
     * @param sharedState shared <code>LoginModule</code> state. <p>
     *
     * @param options options specified in the login
     *                  <code>Configuration</code> for this particular
     *                  <code>LoginModule</code>.
     */
    public void initialize(Subject subject, CallbackHandler callbackHandler,
                           Map<String,?> sharedState,
                           Map<String,?> options)
    {

        this.subject = subject;
        this.callbackHandler = callbackHandler;
        this.sharedState = sharedState;
        this.options = options;

        // initialize any configured options
        debug = "true".equalsIgnoreCase((String)options.get("debug"));
    }

    /**
     * Authenticate the user (first phase).
     *
     * <p> The implementation of this method attempts to retrieve the user's
     * Solaris <code>Subject</code> information by making a native Solaris
     * system call.
     *
     * <p>
     *
     * @exception FailedLoginException if attempts to retrieve the underlying
     *          system information fail.
     *
     * @return true in all cases (this <code>LoginModule</code>
     *          should not be ignored).
     */
    public boolean login() throws LoginException {

        long[] solarisGroups = null;

        try {
            ss = new SolarisSystem();
        } catch (UnsatisfiedLinkError ule) {
            succeeded = false;
            throw new FailedLoginException
                                ("Failed in attempt to import " +
                                "the underlying system identity information" +
                                " on " + System.getProperty("os.name"));
        }
        userPrincipal = new SolarisPrincipal(ss.getUsername());
        UIDPrincipal = new SolarisNumericUserPrincipal(ss.getUid());
        GIDPrincipal = new SolarisNumericGroupPrincipal(ss.getGid(), true);
        if (ss.getGroups() != null && ss.getGroups().length > 0)
            solarisGroups = ss.getGroups();
            for (int i = 0; i < solarisGroups.length; i++) {
                SolarisNumericGroupPrincipal ngp =
                    new SolarisNumericGroupPrincipal
                    (solarisGroups[i], false);
                if (!ngp.getName().equals(GIDPrincipal.getName()))
                    supplementaryGroups.add(ngp);
            }
        if (debug) {
            System.out.println("\t\t[SolarisLoginModule]: " +
                    "succeeded importing info: ");
            System.out.println("\t\t\tuid = " + ss.getUid());
            System.out.println("\t\t\tgid = " + ss.getGid());
            solarisGroups = ss.getGroups();
            for (int i = 0; i < solarisGroups.length; i++) {
                System.out.println("\t\t\tsupp gid = " + solarisGroups[i]);
            }
        }
        succeeded = true;
        return true;
    }

    /**
     * Commit the authentication (second phase).
     *
     * <p> This method is called if the LoginContext's
     * overall authentication succeeded
     * (the relevant REQUIRED, REQUISITE, SUFFICIENT and OPTIONAL LoginModules
     * succeeded).
     *
     * <p> If this LoginModule's own authentication attempt
     * succeeded (the importing of the Solaris authentication information
     * succeeded), then this method associates the Solaris Principals
     * with the <code>Subject</code> currently tied to the
     * <code>LoginModule</code>.  If this LoginModule's
     * authentication attempted failed, then this method removes
     * any state that was originally saved.
     *
     * <p>
     *
     * @exception LoginException if the commit fails
     *
     * @return true if this LoginModule's own login and commit attempts
     *          succeeded, or false otherwise.
     */
    public boolean commit() throws LoginException {
        if (succeeded == false) {
            if (debug) {
                System.out.println("\t\t[SolarisLoginModule]: " +
                    "did not add any Principals to Subject " +
                    "because own authentication failed.");
            }
            return false;
        }
        if (subject.isReadOnly()) {
            throw new LoginException ("Subject is Readonly");
        }
        if (!subject.getPrincipals().contains(userPrincipal))
            subject.getPrincipals().add(userPrincipal);
        if (!subject.getPrincipals().contains(UIDPrincipal))
            subject.getPrincipals().add(UIDPrincipal);
        if (!subject.getPrincipals().contains(GIDPrincipal))
            subject.getPrincipals().add(GIDPrincipal);
        for (int i = 0; i < supplementaryGroups.size(); i++) {
            if (!subject.getPrincipals().contains(supplementaryGroups.get(i)))
                subject.getPrincipals().add(supplementaryGroups.get(i));
        }

        if (debug) {
            System.out.println("\t\t[SolarisLoginModule]: " +
                               "added SolarisPrincipal,");
            System.out.println("\t\t\t\tSolarisNumericUserPrincipal,");
            System.out.println("\t\t\t\tSolarisNumericGroupPrincipal(s),");
            System.out.println("\t\t\t to Subject");
        }

        commitSucceeded = true;
        return true;
    }


    /**
     * Abort the authentication (second phase).
     *
     * <p> This method is called if the LoginContext's
     * overall authentication failed.
     * (the relevant REQUIRED, REQUISITE, SUFFICIENT and OPTIONAL LoginModules
     * did not succeed).
     *
     * <p> This method cleans up any state that was originally saved
     * as part of the authentication attempt from the <code>login</code>
     * and <code>commit</code> methods.
     *
     * <p>
     *
     * @exception LoginException if the abort fails
     *
     * @return false if this LoginModule's own login and/or commit attempts
     *          failed, and true otherwise.
     */
    public boolean abort() throws LoginException {
        if (debug) {
            System.out.println("\t\t[SolarisLoginModule]: " +
                "aborted authentication attempt");
        }

        if (succeeded == false) {
            return false;
        } else if (succeeded == true && commitSucceeded == false) {

            // Clean out state
            succeeded = false;
            ss = null;
            userPrincipal = null;
            UIDPrincipal = null;
            GIDPrincipal = null;
            supplementaryGroups =
                        new LinkedList<SolarisNumericGroupPrincipal>();
        } else {
            // overall authentication succeeded and commit succeeded,
            // but someone else's commit failed
            logout();
        }
        return true;
    }

    /**
     * Logout the user
     *
     * <p> This method removes the Principals associated
     * with the <code>Subject</code>.
     *
     * <p>
     *
     * @exception LoginException if the logout fails
     *
     * @return true in all cases (this <code>LoginModule</code>
     *          should not be ignored).
     */
    public boolean logout() throws LoginException {
        if (debug) {
            System.out.println("\t\t[SolarisLoginModule]: " +
                "Entering logout");
        }
        if (subject.isReadOnly()) {
            throw new LoginException ("Subject is Readonly");
        }
        // remove the added Principals from the Subject
        subject.getPrincipals().remove(userPrincipal);
        subject.getPrincipals().remove(UIDPrincipal);
        subject.getPrincipals().remove(GIDPrincipal);
        for (int i = 0; i < supplementaryGroups.size(); i++) {
            subject.getPrincipals().remove(supplementaryGroups.get(i));
        }

        // clean out state
        ss = null;
        succeeded = false;
        commitSucceeded = false;
        userPrincipal = null;
        UIDPrincipal = null;
        GIDPrincipal = null;
        supplementaryGroups = new LinkedList<SolarisNumericGroupPrincipal>();

        if (debug) {
            System.out.println("\t\t[SolarisLoginModule]: " +
                "logged out Subject");
        }
        return true;
    }
}
